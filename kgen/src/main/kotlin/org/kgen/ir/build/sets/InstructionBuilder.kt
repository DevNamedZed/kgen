package org.kgen.ir.build.sets

import org.kgen.ir.build.InstructionSink
import java.lang.reflect.Proxy

/**
 * Factory for creating typed instruction builders from scope interfaces.
 *
 * A scope interface declares which instruction set categories are available by extending
 * them. The factory reflects on the scope's super-interfaces, creates a backing
 * implementation for each one via [InstructionSetRegistry], and returns a
 * [java.lang.reflect.Proxy] that implements the scope interface directly.
 *
 * Built-in scopes: [ManagedScope], [NativeScope], [FullScope], [ComputeScope].
 * Users define their own for any combination:
 *
 * ```kotlin
 * interface MyScope : ArithmeticInstructionSet, MemoryInstructionSet, TerminatorInstructionSet
 *
 * val b = ir.createInstructionBuilder<MyScope>()
 * b.add(x, y)        // ArithmeticInstructionSet
 * b.load(ptr)         // MemoryInstructionSet
 * b.ret(result)       // TerminatorInstructionSet
 * b.newObject("Foo")  // compile error — ObjectInstructionSet not in MyScope
 * ```
 *
 * Java usage:
 * ```java
 * NativeScope b = ir.createInstructionBuilder(NativeScope.class);
 * b.add(x, y);
 * ```
 */
object InstructionBuilder {

    /**
     * Creates a proxy implementing the given scope interface (Kotlin reified overload).
     *
     * @param T the scope interface (must extend one or more [InstructionSet] interfaces)
     * @param sink the shared [InstructionSink] for all emitted instructions
     * @return a proxy implementing [T]
     * @throws IllegalArgumentException if [T] does not extend any [InstructionSet] interfaces
     * @throws IllegalStateException if any constituent interface is not registered
     */
    inline fun <reified T : InstructionSet> create(sink: InstructionSink): T {
        return create(T::class.java, sink)
    }

    /**
     * Creates a proxy implementing the given scope interface.
     *
     * Reflects on the scope's super-interfaces to discover which [InstructionSet] types
     * it extends, creates an implementation for each via [InstructionSetRegistry],
     * and returns a [java.lang.reflect.Proxy] that implements the scope directly.
     *
     * @param T the scope interface type
     * @param scope the scope interface class
     * @param sink the shared [InstructionSink] for all emitted instructions
     * @return a proxy implementing the scope interface
     * @throws IllegalArgumentException if the scope does not extend any [InstructionSet] interfaces
     * @throws IllegalStateException if any constituent interface is not registered
     */
    @JvmStatic
    fun <T : InstructionSet> create(scope: Class<T>, sink: InstructionSink): T {
        val instructionSetInterfaces = scope.interfaces
            .filter { InstructionSet::class.java.isAssignableFrom(it) && it != InstructionSet::class.java }
            .toList()

        require(instructionSetInterfaces.isNotEmpty()) {
            "${scope.simpleName} does not extend any InstructionSet interfaces"
        }

        val allInterfaces = (instructionSetInterfaces + scope).toTypedArray()

        val implementations = instructionSetInterfaces
            .filter { InstructionSetRegistry.hasImplementation(it) }
            .associate { iface ->
                iface to InstructionSetRegistry.createForInterface(iface, sink)
            }

        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            InstructionSet::class.java.classLoader,
            allInterfaces,
        ) { proxy, method, args ->
            // Try registered implementations first
            val target = implementations[method.declaringClass]
                ?: implementations.values.firstOrNull { method.declaringClass.isInstance(it) }

            if (target != null) {
                if (args != null) { method.invoke(target, *args) } else { method.invoke(target) }
            } else if (method.isDefault) {
                // Extension interfaces have default methods — invoke them on the proxy
                java.lang.invoke.MethodHandles.privateLookupIn(method.declaringClass, java.lang.invoke.MethodHandles.lookup())
                    .findSpecial(method.declaringClass, method.name, java.lang.invoke.MethodType.methodType(method.returnType, method.parameterTypes), method.declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(*(args ?: emptyArray()))
            } else {
                error("No implementation found for ${method.declaringClass.simpleName}.${method.name}")
            }
        } as T
    }
}

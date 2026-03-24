// Generated — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.build.InstructionSink
import kotlin.reflect.KClass

/**
 * Maps [InstructionSet] interfaces to their backing implementation factories.
 *
 * Built-in instruction sets (arithmetic, memory, terminators, etc.) are registered
 * at class-load time. Third-party extensions register via [register].
 *
 * The registry is used by [ProxyFactory] to resolve each interface to a concrete
 * implementation when building proxies.
 *
 * ```kotlin
 * // Register a third-party instruction set
 * InstructionSetRegistry.register(MyDSLInstructionSet::class) { sink ->
 *     MyDSLInstructionSetProvider(sink)
 * }
 * ```
 */
object InstructionSetRegistry {

    private val factories = mutableMapOf<Class<*>, (InstructionSink) -> InstructionSet>()

    init {
        factories[AggregateInstructionSet::class.java] = { sink -> AggregateInstructionSetImpl(sink) }
        factories[ArithmeticInstructionSet::class.java] = { sink -> ArithmeticInstructionSetImpl(sink) }
        factories[AtomicInstructionSet::class.java] = { sink -> AtomicInstructionSetImpl(sink) }
        factories[BitwiseInstructionSet::class.java] = { sink -> BitwiseInstructionSetImpl(sink) }
        factories[CallInstructionSet::class.java] = { sink -> CallInstructionSetImpl(sink) }
        factories[ComparisonInstructionSet::class.java] = { sink -> ComparisonInstructionSetImpl(sink) }
        factories[ComputeInstructionSet::class.java] = { sink -> ComputeInstructionSetImpl(sink) }
        factories[ConversionInstructionSet::class.java] = { sink -> ConversionInstructionSetImpl(sink) }
        factories[DebugInstructionSet::class.java] = { sink -> DebugInstructionSetImpl(sink) }
        factories[DeoptimizationInstructionSet::class.java] = { sink -> DeoptimizationInstructionSetImpl(sink) }
        factories[ExceptionInstructionSet::class.java] = { sink -> ExceptionInstructionSetImpl(sink) }
        factories[InteropInstructionSet::class.java] = { sink -> InteropInstructionSetImpl(sink) }
        factories[IntrinsicInstructionSet::class.java] = { sink -> IntrinsicInstructionSetImpl(sink) }
        factories[MemoryInstructionSet::class.java] = { sink -> MemoryInstructionSetImpl(sink) }
        factories[ObjectInstructionSet::class.java] = { sink -> ObjectInstructionSetImpl(sink) }
        factories[RuntimeInstructionSet::class.java] = { sink -> RuntimeInstructionSetImpl(sink) }
        factories[SsaInstructionSet::class.java] = { sink -> SsaInstructionSetImpl(sink) }
        factories[TerminatorInstructionSet::class.java] = { sink -> TerminatorInstructionSetImpl(sink) }
        factories[VectorInstructionSet::class.java] = { sink -> VectorInstructionSetImpl(sink) }
    }

    /**
     * Registers a factory for a third-party [InstructionSet] interface.
     *
     * @param T the instruction set interface type
     * @param iface the KClass of the interface to register
     * @param factory a function that creates the implementation given an [InstructionSink]
     */
    fun <T : InstructionSet> register(iface: KClass<T>, factory: (InstructionSink) -> T) {
        factories[iface.java] = factory
    }

    /**
     * Registers a factory for a third-party [InstructionSet] interface (Java overload).
     *
     * @param T the instruction set interface type
     * @param iface the Class of the interface to register
     * @param factory a function that creates the implementation given an [InstructionSink]
     */
    @JvmStatic
    fun <T : InstructionSet> register(iface: Class<T>, factory: (InstructionSink) -> T) {
        factories[iface] = factory
    }

    /**
     * Creates an implementation for the given instruction set interface.
     *
     * @param iface the instruction set interface class
     * @param sink the shared instruction sink
     * @return the implementation instance
     * @throws IllegalStateException if no factory is registered for the interface
     */
    @JvmStatic
    fun createForInterface(iface: Class<*>, sink: InstructionSink): InstructionSet {
        return factories[iface]?.invoke(sink)
            ?: error("No implementation registered for ${iface.simpleName}")
    }

    /**
     * Returns true if an implementation factory is registered for the given interface.
     * Extension interfaces (which have default methods only) are typically not registered.
     */
    @JvmStatic
    fun hasImplementation(iface: Class<*>): Boolean = iface in factories
}

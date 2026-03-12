package org.kgen.reflect.emit

import org.kgen.ir.target.Target
import org.kgen.reflect.Signature

/**
 * Generate a single method at runtime and call it directly.
 *
 * This is the core runtime code generation API. Define a method body,
 * then invoke it — no files, no libraries, no intermediate objects.
 *
 * ```java
 * // Native: generate machine code, call it
 * var add = DynamicMethod.native_("add",
 *     Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64));
 * add.body((ir, params) -> {
 *     ir.ret(ir.add(params.get(0), params.get(1)));
 * });
 * long result = add.invoke(3, 4);  // 7
 * add.close();
 *
 * // JVM: generate bytecode, call it
 * var mul = DynamicMethod.jvm("mul",
 *     Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32));
 * mul.jvmBody(code -> {
 *     code.iload(0);
 *     code.iload(1);
 *     code.imul();
 *     code.ireturn();
 * });
 * int result = (int) mul.invoke(6, 7);  // 42
 * ```
 */
abstract class DynamicMethod(
    /** The method name. */
    val name: String,
    /** The method signature. */
    val signature: Signature,
) : AutoCloseable {

    /** Call the method. Compiles on first invocation, cached after that. */
    abstract fun invoke(vararg args: Any?): Any?

    /** Release any resources (executable memory, loaded classes, etc.). */
    override fun close() {}

    companion object {
        /** Create a native dynamic method (auto-detects host platform). */
        @JvmStatic
        fun native_(name: String, signature: Signature): NativeDynamicMethod =
            NativeDynamicMethod(name, signature, NativeModuleBuilder.hostTarget())

        /** Create a native dynamic method for a specific target. */
        @JvmStatic
        fun native_(name: String, signature: Signature, target: Target): NativeDynamicMethod =
            NativeDynamicMethod(name, signature, target)

        /** Create a JVM dynamic method. */
        @JvmStatic
        fun jvm(name: String, signature: Signature): JvmDynamicMethod =
            JvmDynamicMethod(name, signature)

        /** Create a CLR dynamic method (CIL bytecode only, no execution on JVM). */
        @JvmStatic
        fun clr(name: String, signature: Signature): ClrDynamicMethod =
            ClrDynamicMethod(name, signature)
    }
}

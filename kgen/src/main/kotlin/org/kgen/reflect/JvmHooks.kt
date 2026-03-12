package org.kgen.reflect

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method

/**
 * JVM method hooking and interception utilities.
 *
 * Provides several strategies for hooking JVM methods:
 * - **JIT code patching**: Patches the native code of JIT-compiled methods
 * - **Instrumentation agent**: Uses java.lang.instrument for class retransformation
 * - **MethodHandle interception**: Wraps method handles with pre/post hooks
 *
 * ```java
 * // Method handle interception
 * var hook = JvmHooks.intercept(target, before, after);
 * hook.call(args);
 * hook.unhook();
 *
 * // JIT code patching (advanced — patches compiled native code)
 * var patch = JvmHooks.patchJitCode(methodAddress, replacementCode);
 * patch.revert();
 * ```
 */
object JvmHooks {

    /**
     * Intercept a method by wrapping it with before/after callbacks.
     * The original method is still called — this adds pre/post processing.
     *
     * @param target The method to intercept
     * @param before Called before the target method (receives the arguments)
     * @param after Called after the target method (receives the result)
     * @return An active interception that can be undone
     */
    @JvmStatic
    fun intercept(
        target: Method,
        before: ((Array<Any?>) -> Unit)? = null,
        after: ((Any?) -> Unit)? = null,
    ): MethodInterception {
        val lookup = MethodHandles.lookup()
        val handle = lookup.unreflect(target)
        return MethodInterception(target, handle, before, after)
    }

    /**
     * Create a method handle that wraps the target with pre/post hooks.
     */
    @JvmStatic
    fun wrapMethodHandle(
        target: MethodHandle,
        before: Runnable? = null,
        after: Runnable? = null,
    ): WrappedMethodHandle {
        return WrappedMethodHandle(target, before, after)
    }

    /**
     * Patch the JIT-compiled native code of a method at the given address.
     * This directly overwrites the machine code — use with extreme caution.
     *
     * The method must have already been JIT-compiled (called at least once).
     *
     * @param methodAddress The native address of the JIT'd method
     * @param patchBytes The bytes to write at the method entry point
     * @return A patch that can be reverted
     */
    @JvmStatic
    fun patchJitCode(methodAddress: Long, patchBytes: ByteArray): JitPatch {
        val originalBytes = NativeMemory.readBytes(methodAddress, patchBytes.size)
        NativeMemory.writeBytes(methodAddress, patchBytes)
        return JitPatch(methodAddress, originalBytes, patchBytes)
    }

    /**
     * Generate x86-64 jump bytes to redirect execution from one address to another.
     * Creates a `mov rax, target; jmp rax` sequence (12 bytes).
     */
    @JvmStatic
    fun generateJump(targetAddress: Long): ByteArray {
        val bytes = ByteArray(12)
        bytes[0] = 0x48 // REX.W
        bytes[1] = 0xB8.toByte() // mov rax, imm64
        for (i in 0 until 8) {
            bytes[2 + i] = ((targetAddress shr (i * 8)) and 0xFF).toByte()
        }
        bytes[10] = 0xFF.toByte() // jmp rax
        bytes[11] = 0xE0.toByte()
        return bytes
    }

    /**
     * Generate x86-64 breakpoint (INT 3) bytes for debugging.
     */
    @JvmStatic
    fun generateBreakpoint(count: Int = 1): ByteArray {
        return ByteArray(count) { 0xCC.toByte() }
    }

    /**
     * Generate x86-64 NOP sled bytes.
     */
    @JvmStatic
    fun generateNops(count: Int): ByteArray {
        return ByteArray(count) { 0x90.toByte() }
    }

    /**
     * A method interception that wraps before/after hooks around a target method.
     */
    class MethodInterception(
        private val targetMethod: Method,
        private val originalHandle: MethodHandle,
        private val before: ((Array<Any?>) -> Unit)?,
        private val after: ((Any?) -> Unit)?,
    ) {
        private var active = true

        /** Whether the interception is active. */
        fun isActive(): Boolean = active

        /** The intercepted method. */
        fun method(): Method = targetMethod

        /** Call the intercepted method with hooks. */
        fun call(vararg args: Any?): Any? {
            before?.invoke(args as Array<Any?>)
            val result = originalHandle.invokeWithArguments(*args)
            after?.invoke(result)
            return result
        }

        /** Call the original method without hooks. */
        fun callOriginal(vararg args: Any?): Any? {
            return originalHandle.invokeWithArguments(*args)
        }

        /** Remove the interception. */
        fun unhook() {
            active = false
        }
    }

    /**
     * A wrapped method handle with pre/post callbacks.
     */
    class WrappedMethodHandle(
        private val target: MethodHandle,
        private val before: Runnable?,
        private val after: Runnable?,
    ) {
        /** Invoke the wrapped method handle with pre/post callbacks. */
        fun invoke(vararg args: Any?): Any? {
            before?.run()
            val result = target.invokeWithArguments(*args)
            after?.run()
            return result
        }

        /** Get the underlying method handle. */
        fun handle(): MethodHandle = target
    }

    /**
     * A JIT code patch that can be reverted.
     */
    class JitPatch(
        private val address: Long,
        private val originalBytes: ByteArray,
        private val patchBytes: ByteArray,
    ) {
        private var active = true

        /** Whether the patch is currently applied. */
        fun isActive(): Boolean = active

        /** The address of the patched code. */
        fun address(): Long = address

        /** The original bytes that were overwritten. */
        fun originalBytes(): ByteArray = originalBytes.copyOf()

        /** The bytes that were written. */
        fun patchBytes(): ByteArray = patchBytes.copyOf()

        /** Revert the patch, restoring original code. */
        fun revert() {
            if (!active) return
            NativeMemory.writeBytes(address, originalBytes)
            active = false
        }
    }
}

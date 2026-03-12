package org.kgen.runtime.exec

/**
 * Per-thread execution context for compiled code. Tracks the call stack,
 * exception state, and safepoint status.
 *
 * Each thread running compiled code gets its own context, stored in thread-local storage.
 *
 * ```java
 * ExecutionContext ctx = runtime.currentContext();
 * ctx.pushFrame("fibonacci", stackMap);
 * // ... execute code ...
 * ctx.popFrame();
 * ```
 */
interface ExecutionContext {

    /** Push a new frame onto the call stack. */
    fun pushFrame(functionName: String, returnAddress: Long)

    /**
     * Push a frame with native frame info for GC root scanning.
     * @param basePointer The frame's base pointer (RBP on x86, FP on ARM64/RISC-V). 0 if unknown.
     * @param registerSaveArea Address of saved register buffer (from safepoint stub). 0 if unknown.
     */
    fun pushFrame(functionName: String, returnAddress: Long, basePointer: Long, registerSaveArea: Long) {
        pushFrame(functionName, returnAddress) // default: delegate to simple overload
    }

    /** Pop the top frame from the call stack. */
    fun popFrame()

    /** Current stack depth. */
    fun depth(): Int

    /** Walk the stack, visiting each frame from top to bottom. */
    fun walkStack(visitor: FrameVisitor)

    /** Get the current exception, or null if none is active. */
    fun currentException(): Throwable?

    /** Set an exception to be propagated. */
    fun setException(exception: Throwable)

    /** Clear the current exception. */
    fun clearException()

    /** Whether this context is at a safepoint (safe for GC). */
    fun isAtSafepoint(): Boolean

    /** Enter a safepoint — signal that the thread is safe for GC. */
    fun enterSafepoint()

    /** Leave a safepoint — signal that the thread is running mutator code. */
    fun leaveSafepoint()

    /** Whether this context is currently in managed (vs native) mode. */
    fun isInManagedCode(): Boolean

    /** Transition from managed code to native code. */
    fun enterNative()

    /** Transition from native code back to managed code. */
    fun enterManaged()
}

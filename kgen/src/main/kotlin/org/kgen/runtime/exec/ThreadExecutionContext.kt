package org.kgen.runtime.exec

/**
 * Per-thread execution context implementation. Tracks the managed call stack,
 * exception state, safepoint state, and managed/native transitions.
 *
 * ```java
 * var ctx = new ThreadExecutionContext();
 * ctx.pushFrame("main", 0);
 * ctx.pushFrame("fibonacci", returnAddr);
 * ctx.walkStack(frame -> System.out.println(frame.functionName()));
 * ctx.popFrame();
 * ```
 */
class ThreadExecutionContext : ExecutionContext {

    private val frames = ArrayDeque<StackFrame>()
    private var exception: Throwable? = null
    @Volatile private var atSafepoint = false
    @Volatile private var inManaged = true

    override fun pushFrame(functionName: String, returnAddress: Long) {
        frames.addLast(StackFrame(functionName, returnAddress, frames.size))
    }

    override fun pushFrame(functionName: String, returnAddress: Long, basePointer: Long, registerSaveArea: Long) {
        frames.addLast(StackFrame(functionName, returnAddress, frames.size, basePointer, registerSaveArea))
    }

    override fun popFrame() {
        if (frames.isEmpty()) throw IllegalStateException("Cannot pop from empty call stack")
        frames.removeLast()
    }

    override fun depth(): Int = frames.size

    override fun walkStack(visitor: FrameVisitor) {
        for (i in frames.indices.reversed()) {
            visitor.visitFrame(frames[i])
        }
    }

    override fun currentException(): Throwable? = exception

    override fun setException(exception: Throwable) {
        this.exception = exception
    }

    override fun clearException() {
        exception = null
    }

    override fun isAtSafepoint(): Boolean = atSafepoint

    override fun enterSafepoint() {
        atSafepoint = true
    }

    override fun leaveSafepoint() {
        atSafepoint = false
    }

    override fun isInManagedCode(): Boolean = inManaged

    override fun enterNative() {
        inManaged = false
    }

    override fun enterManaged() {
        inManaged = true
    }

    /** Get the top frame, or null if the stack is empty. */
    fun topFrame(): StackFrame? = frames.lastOrNull()

    /** Collect all frames as a list (top first). */
    fun allFrames(): List<StackFrame> = frames.reversed()
}

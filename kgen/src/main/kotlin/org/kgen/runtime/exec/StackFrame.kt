package org.kgen.runtime.exec

/**
 * A single frame on the call stack.
 */
data class StackFrame(
    val functionName: String,
    val returnAddress: Long,
    val depth: Int,
    /** Native frame base pointer (RBP/FP). 0 if not captured. */
    val basePointer: Long = 0,
    /** Address of saved register buffer from safepoint stub. 0 if not captured. */
    val registerSaveArea: Long = 0,
)

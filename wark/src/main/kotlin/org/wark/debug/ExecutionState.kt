package org.wark.debug

/**
 * Snapshot of the interpreter's execution state.
 */
data class ExecutionState(
    val totalInstructions: Long,
    val callDepth: Int,
    val functionsCalled: Int,
)

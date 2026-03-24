package org.wark.debug

/**
 * Result of comparing interpreter vs JIT execution of the same function.
 */
data class CompareResult(
    val match: Boolean,
    val interpreterResult: CallResult,
    val jitResult: CallResult,
)

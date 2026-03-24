package org.wark.debug

/**
 * Result of calling a WASM function via interpreter or JIT.
 */
data class CallResult(
    val result: LongArray,
    val completed: Boolean,
    val paused: Boolean,
    val trap: String?,
)

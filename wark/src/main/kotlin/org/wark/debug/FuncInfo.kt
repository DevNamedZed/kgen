package org.wark.debug

/**
 * Metadata for a single WASM function.
 */
data class FuncInfo(
    val localIndex: Int,
    val globalIndex: Int,
    val name: String,
    val params: List<String>,
    val results: List<String>,
    val localCount: Int,
    val bodySize: Int,
    val instructionCount: Int,
)

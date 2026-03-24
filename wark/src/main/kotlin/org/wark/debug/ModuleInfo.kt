package org.wark.debug

/**
 * Summary of a loaded WASM module.
 */
data class ModuleInfo(
    val functionCount: Int,
    val importCount: Int,
    val totalFunctionCount: Int,
    val typeCount: Int,
    val globalCount: Int,
    val memoryPages: Int,
    val memoryBytes: Int,
    val exports: List<String>,
    val totalInstructions: Long,
)

package org.kgen.wasm.asm

class WasmFunction internal constructor(
    val name: String,
    internal val typeIndex: Int,
    internal val funcIndex: Int,
    private val params: List<WasmLocal>,
) {
    fun getParameter(index: Int): WasmLocal = params[index]
    val paramCount: Int get() = params.size
}

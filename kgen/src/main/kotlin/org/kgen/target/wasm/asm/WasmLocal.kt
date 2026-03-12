package org.kgen.target.wasm.asm

import org.kgen.target.wasm.WasmValueType

class WasmLocal internal constructor(
    val name: String?,
    val type: WasmValueType,
    internal val index: Int,
)

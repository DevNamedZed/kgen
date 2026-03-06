package org.kgen.wasm.asm

import org.kgen.wasm.WasmValueType

class WasmLocal internal constructor(
    val name: String?,
    val type: WasmValueType,
    internal val index: Int,
)

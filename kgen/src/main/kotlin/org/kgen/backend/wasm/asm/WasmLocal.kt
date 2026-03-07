package org.kgen.backend.wasm.asm

import org.kgen.backend.wasm.WasmValueType

class WasmLocal internal constructor(
    val name: String?,
    val type: WasmValueType,
    internal val index: Int,
)

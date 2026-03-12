package org.kgen.target.wasm

sealed interface WasmBlockType {
    val code: Int

    data object Void : WasmBlockType { override val code: Int get() = 0x40 }
    data object I32 : WasmBlockType { override val code: Int get() = 0x7F }
    data object I64 : WasmBlockType { override val code: Int get() = 0x7E }
    data object F32 : WasmBlockType { override val code: Int get() = 0x7D }
    data object F64 : WasmBlockType { override val code: Int get() = 0x7C }
    data class TypeIndex(val index: Int) : WasmBlockType { override val code: Int get() = index }
}

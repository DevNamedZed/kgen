package org.kgen.backend.wasm

enum class WasmValueType(val code: Int) {
    I32(0x7F),
    I64(0x7E),
    F32(0x7D),
    F64(0x7C),
    V128(0x7B),
    FUNCREF(0x70),
    EXTERNREF(0x6F),
}

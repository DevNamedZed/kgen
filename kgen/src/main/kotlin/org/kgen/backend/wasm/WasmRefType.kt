package org.kgen.backend.wasm

enum class WasmRefType(val code: Int) {
    FUNCREF(0x70),
    EXTERNREF(0x6F),
}

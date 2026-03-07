package org.kgen.backend.wasm

enum class WasmOpGroup {
    CONTROL, CALL, PARAMETRIC, VARIABLE, TABLE, MEMORY, NUMERIC, CONVERSION,
    REFERENCE, SIMD, ATOMIC, GC,
}

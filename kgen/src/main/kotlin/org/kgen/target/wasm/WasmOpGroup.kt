package org.kgen.target.wasm

enum class WasmOpGroup {
    CONTROL, CALL, PARAMETRIC, VARIABLE, TABLE, MEMORY, NUMERIC, CONVERSION,
    REFERENCE, SIMD, ATOMIC, GC,
}

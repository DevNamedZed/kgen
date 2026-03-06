package org.kgen.wasm

enum class WasmFeatureFlag(val specName: String) {
    SIGN_EXT("sign-ext"),
    NONTRAPPING_FPTOINT("nontrapping-fptoint"),
    BULK_MEMORY("bulk-memory"),
    REFERENCE_TYPES("reference-types"),
    SIMD128("simd128"),
    RELAXED_SIMD("relaxed-simd"),
    TAIL_CALL("tail-call"),
    EXCEPTION_HANDLING("exception-handling"),
    ATOMICS("atomics"),
    GC("gc"),
    MULTI_MEMORY("multi-memory"),
    MEMORY64("memory64"),
}

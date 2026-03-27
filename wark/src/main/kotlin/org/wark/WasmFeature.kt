package org.wark

/**
 * WASM post-MVP feature extensions.
 *
 * Each feature corresponds to a finalized or in-progress WASM proposal.
 * Use [WasmFeatureSet] to compose a set of enabled features, or use
 * [WasmTarget] presets for common combinations.
 *
 * ```java
 * var features = WasmFeatureSet.of(WasmFeature.BULK_MEMORY, WasmFeature.SIMD);
 * var runtime = WarkRuntime.create(features);
 * ```
 */
enum class WasmFeature(val specName: String) {
    SIGN_EXTEND("sign-extend"),
    SAT_TRUNC("sat-trunc"),
    BULK_MEMORY("bulk-memory"),
    REFERENCE_TYPES("reference-types"),
    SIMD("simd"),
    MULTI_VALUE("multi-value"),
    MUTABLE_GLOBALS("mutable-globals"),
    TAIL_CALL("tail-call"),
    EXTENDED_CONST("extended-const"),
    THREADS("threads"),
    EXCEPTION_HANDLING("exception-handling"),
    MULTI_MEMORY("multi-memory"),
    MULTI_TABLE("multi-table"),
    RELAXED_SIMD("relaxed-simd"),
    MEMORY64("memory64"),
    GC("gc");

    companion object {
        @JvmStatic
        fun fromSpecName(name: String): WasmFeature? = entries.firstOrNull { it.specName == name }
    }
}

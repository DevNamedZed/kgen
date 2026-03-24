package org.wark

/**
 * WASM version targets. Each target enables a standard set of features.
 *
 * ```java
 * var target = WasmTarget.V2_0;
 * var features = target.features();
 * assertTrue(features.isEnabled(WasmFeature.BULK_MEMORY));
 * ```
 */
enum class WasmTarget {
    /**
     * WASM 1.0 MVP — the original spec, no extensions.
     */
    MVP {
        override fun features(): WasmFeatureSet = WasmFeatureSet.none()
    },

    /**
     * WASM 2.0 — first major revision. Standardized in 2024.
     * Adds: sign-extend, sat-trunc, bulk-memory, reference-types, multi-value, mutable-globals.
     */
    V2_0 {
        override fun features(): WasmFeatureSet = WasmFeatureSet.of(
            WasmFeature.SIGN_EXTEND,
            WasmFeature.SAT_TRUNC,
            WasmFeature.BULK_MEMORY,
            WasmFeature.REFERENCE_TYPES,
            WasmFeature.MULTI_VALUE,
            WasmFeature.MUTABLE_GLOBALS,
        )
    },

    /**
     * WASM 3.0 — next revision (in progress).
     * Adds: simd, exception-handling, tail-call, extended-const, multi-table.
     */
    V3_0 {
        override fun features(): WasmFeatureSet = V2_0.features().merge(WasmFeatureSet.of(
            WasmFeature.SIMD,
            WasmFeature.EXCEPTION_HANDLING,
            WasmFeature.TAIL_CALL,
            WasmFeature.EXTENDED_CONST,
            WasmFeature.MULTI_TABLE,
        ))
    },

    /**
     * All features enabled. Tracks the latest proposals.
     */
    LATEST {
        override fun features(): WasmFeatureSet = WasmFeatureSet.all()
    };

    abstract fun features(): WasmFeatureSet
}

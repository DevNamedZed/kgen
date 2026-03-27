package org.wark

/**
 * An immutable set of enabled WASM features.
 *
 * Controls which post-MVP proposals are available during validation and execution.
 * Modules using instructions from disabled features will fail validation.
 *
 * ```java
 * var features = WasmFeatureSet.of(WasmFeature.BULK_MEMORY, WasmFeature.SIMD);
 * assertTrue(features.isEnabled(WasmFeature.BULK_MEMORY));
 * assertFalse(features.isEnabled(WasmFeature.THREADS));
 * ```
 */
class WasmFeatureSet private constructor(
    private val enabled: Set<WasmFeature>,
) {
    fun isEnabled(feature: WasmFeature): Boolean = feature in enabled

    fun enabledFeatures(): Set<WasmFeature> = enabled.toSet()

    fun with(feature: WasmFeature): WasmFeatureSet = WasmFeatureSet(enabled + feature)

    fun without(feature: WasmFeature): WasmFeatureSet = WasmFeatureSet(enabled - feature)

    fun merge(other: WasmFeatureSet): WasmFeatureSet = WasmFeatureSet(enabled + other.enabled)

    fun size(): Int = enabled.size

    override fun equals(other: Any?): Boolean {
        if (other !is WasmFeatureSet) { return false }
        return enabled == other.enabled
    }

    override fun hashCode(): Int = enabled.hashCode()

    override fun toString(): String = "WasmFeatureSet(${enabled.joinToString(", ") { it.specName }})"

    companion object {
        @JvmStatic
        fun none(): WasmFeatureSet = WasmFeatureSet(emptySet())

        @JvmStatic
        fun of(vararg features: WasmFeature): WasmFeatureSet = WasmFeatureSet(features.toSet())

        @JvmStatic
        fun all(): WasmFeatureSet = WasmFeatureSet(WasmFeature.entries.toSet())
    }
}

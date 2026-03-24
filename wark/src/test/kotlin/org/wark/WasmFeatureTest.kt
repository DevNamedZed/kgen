package org.wark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WasmFeatureTest {

    @Test
    fun featureFromSpecName() {
        assertEquals(WasmFeature.SIMD, WasmFeature.fromSpecName("simd"))
        assertEquals(WasmFeature.BULK_MEMORY, WasmFeature.fromSpecName("bulk-memory"))
        assertEquals(WasmFeature.REFERENCE_TYPES, WasmFeature.fromSpecName("reference-types"))
        assertNull(WasmFeature.fromSpecName("nonexistent"))
    }

    @Test
    fun featureSetOf() {
        val features = WasmFeatureSet.of(WasmFeature.SIMD, WasmFeature.BULK_MEMORY)
        assertTrue(features.isEnabled(WasmFeature.SIMD))
        assertTrue(features.isEnabled(WasmFeature.BULK_MEMORY))
        assertFalse(features.isEnabled(WasmFeature.THREADS))
        assertEquals(2, features.size())
    }

    @Test
    fun featureSetNone() {
        val features = WasmFeatureSet.none()
        assertEquals(0, features.size())
        assertFalse(features.isEnabled(WasmFeature.SIMD))
    }

    @Test
    fun featureSetAll() {
        val features = WasmFeatureSet.all()
        assertEquals(WasmFeature.entries.size, features.size())
        for (feature in WasmFeature.entries) {
            assertTrue(features.isEnabled(feature))
        }
    }

    @Test
    fun featureSetWith() {
        val base = WasmFeatureSet.of(WasmFeature.SIMD)
        val extended = base.with(WasmFeature.THREADS)
        assertTrue(extended.isEnabled(WasmFeature.SIMD))
        assertTrue(extended.isEnabled(WasmFeature.THREADS))
        assertFalse(base.isEnabled(WasmFeature.THREADS))
    }

    @Test
    fun featureSetWithout() {
        val base = WasmFeatureSet.of(WasmFeature.SIMD, WasmFeature.THREADS)
        val reduced = base.without(WasmFeature.THREADS)
        assertTrue(reduced.isEnabled(WasmFeature.SIMD))
        assertFalse(reduced.isEnabled(WasmFeature.THREADS))
    }

    @Test
    fun featureSetMerge() {
        val first = WasmFeatureSet.of(WasmFeature.SIMD)
        val second = WasmFeatureSet.of(WasmFeature.THREADS)
        val merged = first.merge(second)
        assertTrue(merged.isEnabled(WasmFeature.SIMD))
        assertTrue(merged.isEnabled(WasmFeature.THREADS))
    }

    @Test
    fun targetMvp() {
        val features = WasmTarget.MVP.features()
        assertEquals(0, features.size())
    }

    @Test
    fun targetV2() {
        val features = WasmTarget.V2_0.features()
        assertTrue(features.isEnabled(WasmFeature.BULK_MEMORY))
        assertTrue(features.isEnabled(WasmFeature.REFERENCE_TYPES))
        assertTrue(features.isEnabled(WasmFeature.MULTI_VALUE))
        assertTrue(features.isEnabled(WasmFeature.SIGN_EXTEND))
        assertTrue(features.isEnabled(WasmFeature.SAT_TRUNC))
        assertTrue(features.isEnabled(WasmFeature.MUTABLE_GLOBALS))
        assertFalse(features.isEnabled(WasmFeature.SIMD))
    }

    @Test
    fun targetV3() {
        val features = WasmTarget.V3_0.features()
        assertTrue(features.isEnabled(WasmFeature.BULK_MEMORY))
        assertTrue(features.isEnabled(WasmFeature.SIMD))
        assertTrue(features.isEnabled(WasmFeature.EXCEPTION_HANDLING))
        assertTrue(features.isEnabled(WasmFeature.TAIL_CALL))
        assertFalse(features.isEnabled(WasmFeature.THREADS))
    }

    @Test
    fun targetLatest() {
        val features = WasmTarget.LATEST.features()
        assertEquals(WasmFeature.entries.size, features.size())
    }

    @Test
    fun v3SupersetOfV2() {
        val v2 = WasmTarget.V2_0.features()
        val v3 = WasmTarget.V3_0.features()
        for (feature in v2.enabledFeatures()) {
            assertTrue(v3.isEnabled(feature), "$feature in V2_0 but not in V3_0")
        }
    }
}

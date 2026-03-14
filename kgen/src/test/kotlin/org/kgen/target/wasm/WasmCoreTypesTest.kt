package org.kgen.target.wasm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmCoreTypesTest {

    @Nested
    inner class ValueTypes {

        @Test
        fun i32HasCorrectCode() {
            assertEquals(0x7F, WasmValueType.I32.code)
        }

        @Test
        fun i64HasCorrectCode() {
            assertEquals(0x7E, WasmValueType.I64.code)
        }

        @Test
        fun f32HasCorrectCode() {
            assertEquals(0x7D, WasmValueType.F32.code)
        }

        @Test
        fun f64HasCorrectCode() {
            assertEquals(0x7C, WasmValueType.F64.code)
        }

        @Test
        fun v128HasCorrectCode() {
            assertEquals(0x7B, WasmValueType.V128.code)
        }

        @Test
        fun funcrefHasCorrectCode() {
            assertEquals(0x70, WasmValueType.FUNCREF.code)
        }

        @Test
        fun externrefHasCorrectCode() {
            assertEquals(0x6F, WasmValueType.EXTERNREF.code)
        }

        @Test
        fun allValueTypesAreDefined() {
            assertEquals(7, WasmValueType.entries.size)
        }

        @Test
        fun valueTypesHaveUniqueCodes() {
            val codes = WasmValueType.entries.map { it.code }
            assertEquals(codes.size, codes.toSet().size)
        }

        @Test
        fun valueOfByName() {
            assertEquals(WasmValueType.I32, WasmValueType.valueOf("I32"))
            assertEquals(WasmValueType.F64, WasmValueType.valueOf("F64"))
        }
    }

    @Nested
    inner class BlockTypes {

        @Test
        fun voidHasCorrectCode() {
            assertEquals(0x40, WasmBlockType.Void.code)
        }

        @Test
        fun i32BlockHasCorrectCode() {
            assertEquals(0x7F, WasmBlockType.I32.code)
        }

        @Test
        fun i64BlockHasCorrectCode() {
            assertEquals(0x7E, WasmBlockType.I64.code)
        }

        @Test
        fun f32BlockHasCorrectCode() {
            assertEquals(0x7D, WasmBlockType.F32.code)
        }

        @Test
        fun f64BlockHasCorrectCode() {
            assertEquals(0x7C, WasmBlockType.F64.code)
        }

        @Test
        fun typeIndexReturnsIndexAsCode() {
            val typeIdx = WasmBlockType.TypeIndex(5)
            assertEquals(5, typeIdx.code)
        }

        @Test
        fun typeIndexWithZero() {
            val typeIdx = WasmBlockType.TypeIndex(0)
            assertEquals(0, typeIdx.code)
        }

        @Test
        fun typeIndexWithLargeValue() {
            val typeIdx = WasmBlockType.TypeIndex(255)
            assertEquals(255, typeIdx.code)
        }

        @Test
        fun blockTypeIsSealedInterface() {
            val blockType: WasmBlockType = WasmBlockType.Void
            assertNotNull(blockType)
        }

        @Test
        fun typeIndexEquality() {
            val idx1 = WasmBlockType.TypeIndex(3)
            val idx2 = WasmBlockType.TypeIndex(3)
            assertEquals(idx1, idx2)
            assertEquals(idx1.hashCode(), idx2.hashCode())
        }

        @Test
        fun typeIndexDifferentValuesNotEqual() {
            val idx1 = WasmBlockType.TypeIndex(1)
            val idx2 = WasmBlockType.TypeIndex(2)
            assertNotEquals(idx1, idx2)
        }

        @Test
        fun dataObjectsAreSingletons() {
            assertSame(WasmBlockType.Void, WasmBlockType.Void)
            assertSame(WasmBlockType.I32, WasmBlockType.I32)
            assertSame(WasmBlockType.I64, WasmBlockType.I64)
            assertSame(WasmBlockType.F32, WasmBlockType.F32)
            assertSame(WasmBlockType.F64, WasmBlockType.F64)
        }
    }

    @Nested
    inner class RefTypes {

        @Test
        fun funcrefHasCorrectCode() {
            assertEquals(0x70, WasmRefType.FUNCREF.code)
        }

        @Test
        fun externrefHasCorrectCode() {
            assertEquals(0x6F, WasmRefType.EXTERNREF.code)
        }

        @Test
        fun allRefTypesAreDefined() {
            assertEquals(2, WasmRefType.entries.size)
        }

        @Test
        fun refTypesHaveUniqueCodes() {
            val codes = WasmRefType.entries.map { it.code }
            assertEquals(codes.size, codes.toSet().size)
        }

        @Test
        fun funcrefMatchesValueTypeFuncref() {
            assertEquals(WasmValueType.FUNCREF.code, WasmRefType.FUNCREF.code)
        }

        @Test
        fun externrefMatchesValueTypeExternref() {
            assertEquals(WasmValueType.EXTERNREF.code, WasmRefType.EXTERNREF.code)
        }
    }

    @Nested
    inner class OpGroups {

        @Test
        fun allExpectedGroupsExist() {
            val groups = WasmOpGroup.entries
            assertTrue(groups.contains(WasmOpGroup.CONTROL))
            assertTrue(groups.contains(WasmOpGroup.CALL))
            assertTrue(groups.contains(WasmOpGroup.PARAMETRIC))
            assertTrue(groups.contains(WasmOpGroup.VARIABLE))
            assertTrue(groups.contains(WasmOpGroup.TABLE))
            assertTrue(groups.contains(WasmOpGroup.MEMORY))
            assertTrue(groups.contains(WasmOpGroup.NUMERIC))
            assertTrue(groups.contains(WasmOpGroup.CONVERSION))
            assertTrue(groups.contains(WasmOpGroup.REFERENCE))
            assertTrue(groups.contains(WasmOpGroup.SIMD))
            assertTrue(groups.contains(WasmOpGroup.ATOMIC))
            assertTrue(groups.contains(WasmOpGroup.GC))
        }

        @Test
        fun groupCount() {
            assertEquals(12, WasmOpGroup.entries.size)
        }

        @Test
        fun valueOfByName() {
            assertEquals(WasmOpGroup.CONTROL, WasmOpGroup.valueOf("CONTROL"))
            assertEquals(WasmOpGroup.SIMD, WasmOpGroup.valueOf("SIMD"))
        }
    }

    @Nested
    inner class FeatureFlags {

        @Test
        fun signExtHasCorrectSpecName() {
            assertEquals("sign-ext", WasmFeatureFlag.SIGN_EXT.specName)
        }

        @Test
        fun simd128HasCorrectSpecName() {
            assertEquals("simd128", WasmFeatureFlag.SIMD128.specName)
        }

        @Test
        fun tailCallHasCorrectSpecName() {
            assertEquals("tail-call", WasmFeatureFlag.TAIL_CALL.specName)
        }

        @Test
        fun atomicsHasCorrectSpecName() {
            assertEquals("atomics", WasmFeatureFlag.ATOMICS.specName)
        }

        @Test
        fun gcHasCorrectSpecName() {
            assertEquals("gc", WasmFeatureFlag.GC.specName)
        }

        @Test
        fun bulkMemoryHasCorrectSpecName() {
            assertEquals("bulk-memory", WasmFeatureFlag.BULK_MEMORY.specName)
        }

        @Test
        fun referenceTypesHasCorrectSpecName() {
            assertEquals("reference-types", WasmFeatureFlag.REFERENCE_TYPES.specName)
        }

        @Test
        fun allFlagsHaveNonEmptySpecNames() {
            for (flag in WasmFeatureFlag.entries) {
                assertTrue(flag.specName.isNotEmpty(), "${flag.name} should have non-empty specName")
            }
        }

        @Test
        fun featureFlagCount() {
            assertEquals(12, WasmFeatureFlag.entries.size)
        }

        @Test
        fun specNamesAreUnique() {
            val specNames = WasmFeatureFlag.entries.map { it.specName }
            assertEquals(specNames.size, specNames.toSet().size)
        }
    }

    @Nested
    inner class Immediates {

        @Test
        fun allExpectedImmediatesExist() {
            val immediates = WasmImmediate.entries
            assertTrue(immediates.contains(WasmImmediate.BlockType))
            assertTrue(immediates.contains(WasmImmediate.LabelIdx))
            assertTrue(immediates.contains(WasmImmediate.FuncIdx))
            assertTrue(immediates.contains(WasmImmediate.LocalIdx))
            assertTrue(immediates.contains(WasmImmediate.GlobalIdx))
            assertTrue(immediates.contains(WasmImmediate.TableIdx))
            assertTrue(immediates.contains(WasmImmediate.MemIdx))
            assertTrue(immediates.contains(WasmImmediate.I32))
            assertTrue(immediates.contains(WasmImmediate.I64))
            assertTrue(immediates.contains(WasmImmediate.F32))
            assertTrue(immediates.contains(WasmImmediate.F64))
            assertTrue(immediates.contains(WasmImmediate.V128))
            assertTrue(immediates.contains(WasmImmediate.MemArg))
            assertTrue(immediates.contains(WasmImmediate.BrTable))
        }

        @Test
        fun valueOfByName() {
            assertEquals(WasmImmediate.BlockType, WasmImmediate.valueOf("BlockType"))
            assertEquals(WasmImmediate.MemArg, WasmImmediate.valueOf("MemArg"))
        }
    }
}

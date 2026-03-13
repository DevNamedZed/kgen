package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.Type

class LiveIntervalTest {

    @Nested
    inner class Construction {

        @Test
        fun basicProperties() {
            val iv = LiveInterval("x", 0, 5, Type.I64, 3, false)
            assertEquals("x", iv.name)
            assertEquals(0, iv.start)
            assertEquals(5, iv.end)
            assertEquals(Type.I64, iv.type)
            assertEquals(3, iv.useCount)
            assertFalse(iv.acrossCall)
        }

        @Test
        fun defaultValues() {
            val iv = LiveInterval("y", 1, 4, Type.I32)
            assertEquals(1, iv.useCount)
            assertFalse(iv.acrossCall)
        }

        @Test
        fun acrossCallTrue() {
            val iv = LiveInterval("z", 0, 10, Type.I64, 2, true)
            assertTrue(iv.acrossCall)
        }

        @Test
        fun zeroLengthInterval() {
            val iv = LiveInterval("dead", 3, 3, Type.I64, 0)
            assertEquals(iv.start, iv.end)
        }

        @Test
        fun floatingPointType() {
            val ivF32 = LiveInterval("f", 0, 2, Type.F32)
            val ivF64 = LiveInterval("d", 0, 2, Type.F64)
            assertEquals(Type.F32, ivF32.type)
            assertEquals(Type.F64, ivF64.type)
        }

        @Test
        fun pointerType() {
            val iv = LiveInterval("ptr", 0, 3, Type.OpaquePointer)
            assertEquals(Type.OpaquePointer, iv.type)
        }
    }

    @Nested
    inner class Equality {

        @Test
        fun equalIntervalsAreEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64, 2, false)
            val b = LiveInterval("x", 0, 5, Type.I64, 2, false)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun differentNameNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64)
            val b = LiveInterval("y", 0, 5, Type.I64)
            assertNotEquals(a, b)
        }

        @Test
        fun differentStartNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64)
            val b = LiveInterval("x", 1, 5, Type.I64)
            assertNotEquals(a, b)
        }

        @Test
        fun differentEndNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64)
            val b = LiveInterval("x", 0, 6, Type.I64)
            assertNotEquals(a, b)
        }

        @Test
        fun differentTypeNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64)
            val b = LiveInterval("x", 0, 5, Type.I32)
            assertNotEquals(a, b)
        }

        @Test
        fun differentUseCountNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64, 1)
            val b = LiveInterval("x", 0, 5, Type.I64, 3)
            assertNotEquals(a, b)
        }

        @Test
        fun differentAcrossCallNotEqual() {
            val a = LiveInterval("x", 0, 5, Type.I64, 1, false)
            val b = LiveInterval("x", 0, 5, Type.I64, 1, true)
            assertNotEquals(a, b)
        }
    }

    @Nested
    inner class OverlapSemantics {

        @Test
        fun overlappingIntervalsShareRange() {
            val a = LiveInterval("a", 0, 5, Type.I64)
            val b = LiveInterval("b", 3, 8, Type.I64)
            assertTrue(a.end >= b.start && b.end >= a.start)
        }

        @Test
        fun nonOverlappingIntervalsDisjoint() {
            val a = LiveInterval("a", 0, 3, Type.I64)
            val b = LiveInterval("b", 5, 8, Type.I64)
            assertFalse(a.end >= b.start && b.end >= a.start)
        }

        @Test
        fun adjacentIntervalsDoNotOverlap() {
            val a = LiveInterval("a", 0, 3, Type.I64)
            val b = LiveInterval("b", 4, 7, Type.I64)
            assertTrue(a.end < b.start)
        }

        @Test
        fun touchingEndpointsOverlap() {
            val a = LiveInterval("a", 0, 5, Type.I64)
            val b = LiveInterval("b", 5, 10, Type.I64)
            assertTrue(a.end >= b.start)
        }

        @Test
        fun containedIntervalOverlaps() {
            val outer = LiveInterval("outer", 0, 10, Type.I64)
            val inner = LiveInterval("inner", 3, 7, Type.I64)
            assertTrue(outer.end >= inner.start && inner.end >= outer.start)
        }

        @Test
        fun sameRangeOverlaps() {
            val a = LiveInterval("a", 2, 6, Type.I64)
            val b = LiveInterval("b", 2, 6, Type.I64)
            assertTrue(a.end >= b.start && b.end >= a.start)
        }

        @Test
        fun sortingByStartForLinearScan() {
            val intervals = listOf(
                LiveInterval("c", 5, 10, Type.I64),
                LiveInterval("a", 0, 3, Type.I64),
                LiveInterval("b", 2, 8, Type.I64),
            )
            val sorted = intervals.sortedBy { it.start }
            assertEquals("a", sorted[0].name)
            assertEquals("b", sorted[1].name)
            assertEquals("c", sorted[2].name)
        }
    }

    @Nested
    inner class Copy {

        @Test
        fun copyWithModifiedEnd() {
            val original = LiveInterval("x", 0, 5, Type.I64, 2, false)
            val extended = original.copy(end = 10)
            assertEquals(10, extended.end)
            assertEquals(original.name, extended.name)
            assertEquals(original.start, extended.start)
            assertEquals(original.type, extended.type)
        }

        @Test
        fun copyPreservesAllFields() {
            val original = LiveInterval("x", 1, 7, Type.F64, 4, true)
            val copy = original.copy()
            assertEquals(original, copy)
        }
    }
}

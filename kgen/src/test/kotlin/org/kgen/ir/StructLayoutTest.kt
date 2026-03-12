package org.kgen.ir

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.target.Target

class StructLayoutTest {

    private val x64 = Target.x86_64()
    private val arm64 = Target.arm64()

    @Test
    fun singleFieldStruct() {
        val struct = Type.Struct(null, listOf(Type.I32), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(4, layout.sizeInBytes())
        assertEquals(4, layout.alignmentInBytes())
        assertEquals(0, layout.fieldOffset(0))
    }

    @Test
    fun twoFieldsNoPadding() {
        // {i32, i32} — no padding needed
        val struct = Type.Struct(null, listOf(Type.I32, Type.I32), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(8, layout.sizeInBytes())
        assertEquals(4, layout.alignmentInBytes())
        assertEquals(0, layout.fieldOffset(0))
        assertEquals(4, layout.fieldOffset(1))
    }

    @Test
    fun paddingBetweenFields() {
        // {i32, i8, i64} — padding between i8 and i64
        val struct = Type.Struct(null, listOf(Type.I32, Type.I8, Type.I64), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(0, layout.fieldOffset(0))   // i32 at 0
        assertEquals(4, layout.fieldOffset(1))   // i8 at 4
        assertEquals(8, layout.fieldOffset(2))   // i64 at 8 (aligned)
        assertEquals(16, layout.sizeInBytes())   // total with trailing padding
        assertEquals(8, layout.alignmentInBytes())
    }

    @Test
    fun trailingPadding() {
        // {i64, i8} — trailing padding to 16
        val struct = Type.Struct(null, listOf(Type.I64, Type.I8), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(0, layout.fieldOffset(0))
        assertEquals(8, layout.fieldOffset(1))
        assertEquals(16, layout.sizeInBytes()) // padded to align=8
    }

    @Test
    fun packedStruct() {
        // packed {i32, i8, i64} — no padding
        val struct = Type.Struct(null, listOf(Type.I32, Type.I8, Type.I64), true)
        val layout = StructLayout.of(struct, x64)
        assertEquals(0, layout.fieldOffset(0))
        assertEquals(4, layout.fieldOffset(1))
        assertEquals(5, layout.fieldOffset(2))
        assertEquals(13, layout.sizeInBytes())
        assertEquals(1, layout.alignmentInBytes())
    }

    @Test
    fun pointerField() {
        // {ptr, i32} on x86_64 — ptr is 8 bytes
        val struct = Type.Struct(null, listOf(Type.OpaquePointer, Type.I32), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(0, layout.fieldOffset(0))
        assertEquals(8, layout.fieldOffset(1))
        assertEquals(16, layout.sizeInBytes()) // trailing padding
        assertEquals(8, layout.alignmentInBytes())
    }

    @Test
    fun nestedStruct() {
        val inner = Type.Struct(null, listOf(Type.I32, Type.I32), false)
        val outer = Type.Struct(null, listOf(Type.I8, inner), false)
        val layout = StructLayout.of(outer, x64)
        assertEquals(0, layout.fieldOffset(0))   // i8 at 0
        assertEquals(4, layout.fieldOffset(1))   // inner at 4 (aligned to inner's align=4)
        assertEquals(12, layout.sizeInBytes())
    }

    @Test
    fun unionLayout() {
        val union = Type.Union(null, listOf(Type.I32, Type.I64, Type.I8))
        val layout = StructLayout.ofUnion(union, x64)
        assertEquals(8, layout.sizeInBytes())    // max of {4, 8, 1}
        assertEquals(8, layout.alignmentInBytes())
        // All fields at offset 0
        assertEquals(0, layout.fieldOffset(0))
        assertEquals(0, layout.fieldOffset(1))
        assertEquals(0, layout.fieldOffset(2))
    }

    @Test
    fun sizeOfPrimitives() {
        assertEquals(1, StructLayout.sizeOf(Type.I8, x64))
        assertEquals(2, StructLayout.sizeOf(Type.I16, x64))
        assertEquals(4, StructLayout.sizeOf(Type.I32, x64))
        assertEquals(8, StructLayout.sizeOf(Type.I64, x64))
        assertEquals(4, StructLayout.sizeOf(Type.F32, x64))
        assertEquals(8, StructLayout.sizeOf(Type.F64, x64))
        assertEquals(0, StructLayout.sizeOf(Type.Void, x64))
    }

    @Test
    fun sizeOfPointers() {
        assertEquals(8, StructLayout.sizeOf(Type.OpaquePointer, x64))
        assertEquals(8, StructLayout.sizeOf(Type.Pointer(Type.I32), x64))
    }

    @Test
    fun sizeOfArray() {
        val arr = Type.Array(Type.I32, 10)
        assertEquals(40, StructLayout.sizeOf(arr, x64))
    }

    @Test
    fun alignOfPrimitives() {
        assertEquals(1, StructLayout.alignOf(Type.I8, x64))
        assertEquals(2, StructLayout.alignOf(Type.I16, x64))
        assertEquals(4, StructLayout.alignOf(Type.I32, x64))
        assertEquals(8, StructLayout.alignOf(Type.I64, x64))
        assertEquals(4, StructLayout.alignOf(Type.F32, x64))
        assertEquals(8, StructLayout.alignOf(Type.F64, x64))
    }

    @Test
    fun emptyStruct() {
        val struct = Type.Struct(null, emptyList(), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(0, layout.sizeInBytes())
        assertEquals(0, layout.fieldCount())
    }

    @Test
    fun fieldCount() {
        val struct = Type.Struct(null, listOf(Type.I32, Type.I64, Type.I8), false)
        val layout = StructLayout.of(struct, x64)
        assertEquals(3, layout.fieldCount())
    }

    @Test
    fun toStringDescriptive() {
        val struct = Type.Struct(null, listOf(Type.I32, Type.I64), false)
        val layout = StructLayout.of(struct, x64)
        val str = layout.toString()
        assertTrue(str.contains("size=16"))
        assertTrue(str.contains("align=8"))
    }
}

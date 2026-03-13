package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConstantTest {

    @Nested
    inner class I1Tests {

        @Test
        fun trueHasTypeI1() {
            val c = Constant.I1(true)
            assertEquals(Type.I1, c.type)
        }

        @Test
        fun trueNameIs1() {
            assertEquals("1", Constant.I1(true).name)
        }

        @Test
        fun falseNameIs0() {
            assertEquals("0", Constant.I1(false).name)
        }

        @Test
        fun falseHasTypeI1() {
            assertEquals(Type.I1, Constant.I1(false).type)
        }

        @Test
        fun equalityForSameValue() {
            assertEquals(Constant.I1(true), Constant.I1(true))
            assertEquals(Constant.I1(false), Constant.I1(false))
        }

        @Test
        fun inequalityForDifferentValues() {
            assertNotEquals(Constant.I1(true), Constant.I1(false))
        }

        @Test
        fun hashCodeConsistentWithEquals() {
            assertEquals(Constant.I1(true).hashCode(), Constant.I1(true).hashCode())
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.I1(true)
            assertEquals(Type.I1, v.type)
            assertEquals("1", v.name)
        }
    }

    @Nested
    inner class I8Tests {

        @Test
        fun typeIsI8() {
            assertEquals(Type.I8, Constant.I8(42.toByte()).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("42", Constant.I8(42.toByte()).name)
        }

        @Test
        fun negativeValue() {
            assertEquals("-1", Constant.I8((-1).toByte()).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.I8(10.toByte()), Constant.I8(10.toByte()))
            assertNotEquals(Constant.I8(10.toByte()), Constant.I8(20.toByte()))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.I8(10.toByte()).hashCode(), Constant.I8(10.toByte()).hashCode())
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.I8(0.toByte())
            assertNotNull(v.type)
            assertNotNull(v.name)
        }
    }

    @Nested
    inner class I16Tests {

        @Test
        fun typeIsI16() {
            assertEquals(Type.I16, Constant.I16(1000.toShort()).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("1000", Constant.I16(1000.toShort()).name)
        }

        @Test
        fun negativeValue() {
            assertEquals("-500", Constant.I16((-500).toShort()).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.I16(100.toShort()), Constant.I16(100.toShort()))
            assertNotEquals(Constant.I16(100.toShort()), Constant.I16(200.toShort()))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.I16(100.toShort()).hashCode(), Constant.I16(100.toShort()).hashCode())
        }
    }

    @Nested
    inner class I32Tests {

        @Test
        fun typeIsI32() {
            assertEquals(Type.I32, Constant.I32(42).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("42", Constant.I32(42).name)
        }

        @Test
        fun zeroValue() {
            assertEquals("0", Constant.I32(0).name)
        }

        @Test
        fun negativeValue() {
            assertEquals("-1", Constant.I32(-1).name)
        }

        @Test
        fun maxValue() {
            assertEquals(Int.MAX_VALUE.toString(), Constant.I32(Int.MAX_VALUE).name)
        }

        @Test
        fun minValue() {
            assertEquals(Int.MIN_VALUE.toString(), Constant.I32(Int.MIN_VALUE).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.I32(42), Constant.I32(42))
            assertNotEquals(Constant.I32(42), Constant.I32(43))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.I32(42).hashCode(), Constant.I32(42).hashCode())
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.I32(99)
            assertEquals(Type.I32, v.type)
            assertEquals("99", v.name)
        }
    }

    @Nested
    inner class I64Tests {

        @Test
        fun typeIsI64() {
            assertEquals(Type.I64, Constant.I64(100L).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("100", Constant.I64(100L).name)
        }

        @Test
        fun largeValue() {
            val large = Long.MAX_VALUE
            assertEquals(large.toString(), Constant.I64(large).name)
        }

        @Test
        fun negativeValue() {
            assertEquals("-9999", Constant.I64(-9999L).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.I64(7L), Constant.I64(7L))
            assertNotEquals(Constant.I64(7L), Constant.I64(8L))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.I64(7L).hashCode(), Constant.I64(7L).hashCode())
        }
    }

    @Nested
    inner class I128Tests {

        @Test
        fun typeIsI128() {
            assertEquals(Type.I128, Constant.I128(0L).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("12345", Constant.I128(12345L).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.I128(1L), Constant.I128(1L))
            assertNotEquals(Constant.I128(1L), Constant.I128(2L))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.I128(1L).hashCode(), Constant.I128(1L).hashCode())
        }
    }

    @Nested
    inner class IntNTests {

        @Test
        fun typeIsIntNWithCorrectBits() {
            val c = Constant.IntN(42L, 24)
            assertEquals(Type.IntN(24), c.type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("42", Constant.IntN(42L, 24).name)
        }

        @Test
        fun differentBitWidths() {
            val c7 = Constant.IntN(5L, 7)
            val c24 = Constant.IntN(5L, 24)
            assertEquals(Type.IntN(7), c7.type)
            assertEquals(Type.IntN(24), c24.type)
        }

        @Test
        fun equalitySameValueAndBits() {
            assertEquals(Constant.IntN(10L, 24), Constant.IntN(10L, 24))
        }

        @Test
        fun inequalityDifferentBits() {
            assertNotEquals(Constant.IntN(10L, 24), Constant.IntN(10L, 32))
        }

        @Test
        fun inequalityDifferentValues() {
            assertNotEquals(Constant.IntN(10L, 24), Constant.IntN(20L, 24))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.IntN(10L, 24).hashCode(), Constant.IntN(10L, 24).hashCode())
        }
    }

    @Nested
    inner class F16Tests {

        @Test
        fun typeIsF16() {
            assertEquals(Type.F16, Constant.F16(1.5f).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("1.5", Constant.F16(1.5f).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.F16(1.5f), Constant.F16(1.5f))
            assertNotEquals(Constant.F16(1.5f), Constant.F16(2.5f))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.F16(1.5f).hashCode(), Constant.F16(1.5f).hashCode())
        }
    }

    @Nested
    inner class BF16Tests {

        @Test
        fun typeIsBF16() {
            assertEquals(Type.BF16, Constant.BF16(3.0f).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("3.0", Constant.BF16(3.0f).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.BF16(3.0f), Constant.BF16(3.0f))
            assertNotEquals(Constant.BF16(3.0f), Constant.BF16(4.0f))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.BF16(3.0f).hashCode(), Constant.BF16(3.0f).hashCode())
        }
    }

    @Nested
    inner class F32Tests {

        @Test
        fun typeIsF32() {
            assertEquals(Type.F32, Constant.F32(3.14f).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("3.14", Constant.F32(3.14f).name)
        }

        @Test
        fun zeroValue() {
            assertEquals("0.0", Constant.F32(0.0f).name)
        }

        @Test
        fun negativeValue() {
            assertEquals("-1.0", Constant.F32(-1.0f).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.F32(1.0f), Constant.F32(1.0f))
            assertNotEquals(Constant.F32(1.0f), Constant.F32(2.0f))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.F32(1.0f).hashCode(), Constant.F32(1.0f).hashCode())
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.F32(2.0f)
            assertEquals(Type.F32, v.type)
            assertEquals("2.0", v.name)
        }
    }

    @Nested
    inner class F64Tests {

        @Test
        fun typeIsF64() {
            assertEquals(Type.F64, Constant.F64(2.718).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("2.718", Constant.F64(2.718).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.F64(1.0), Constant.F64(1.0))
            assertNotEquals(Constant.F64(1.0), Constant.F64(2.0))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.F64(1.0).hashCode(), Constant.F64(1.0).hashCode())
        }
    }

    @Nested
    inner class F80Tests {

        @Test
        fun typeIsF80() {
            assertEquals(Type.F80, Constant.F80(1.0).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("1.0", Constant.F80(1.0).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.F80(1.0), Constant.F80(1.0))
            assertNotEquals(Constant.F80(1.0), Constant.F80(2.0))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.F80(1.0).hashCode(), Constant.F80(1.0).hashCode())
        }
    }

    @Nested
    inner class F128Tests {

        @Test
        fun typeIsF128() {
            assertEquals(Type.F128, Constant.F128(1.0).type)
        }

        @Test
        fun nameIsValueString() {
            assertEquals("1.0", Constant.F128(1.0).name)
        }

        @Test
        fun equality() {
            assertEquals(Constant.F128(1.0), Constant.F128(1.0))
            assertNotEquals(Constant.F128(1.0), Constant.F128(2.0))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.F128(1.0).hashCode(), Constant.F128(1.0).hashCode())
        }
    }

    @Nested
    inner class NullPtrTests {

        @Test
        fun typeIsOpaquePointer() {
            assertEquals(Type.OpaquePointer, Constant.NullPtr.type)
        }

        @Test
        fun nameIsNull() {
            assertEquals("null", Constant.NullPtr.name)
        }

        @Test
        fun isSingleton() {
            assertSame(Constant.NullPtr, Constant.NullPtr)
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.NullPtr
            assertEquals(Type.OpaquePointer, v.type)
            assertEquals("null", v.name)
        }

        @Test
        fun equalityAsSingleton() {
            assertEquals(Constant.NullPtr, Constant.NullPtr)
            assertEquals(Constant.NullPtr.hashCode(), Constant.NullPtr.hashCode())
        }
    }

    @Nested
    inner class NullRefTests {

        @Test
        fun typeIsNullableReferenceToVoid() {
            val expected = Type.Reference(Type.Void, nullable = true)
            assertEquals(expected, Constant.NullRef.type)
        }

        @Test
        fun nameIsNull() {
            assertEquals("null", Constant.NullRef.name)
        }

        @Test
        fun isSingleton() {
            assertSame(Constant.NullRef, Constant.NullRef)
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.NullRef
            assertEquals("null", v.name)
        }

        @Test
        fun notEqualToNullPtr() {
            assertNotEquals(Constant.NullPtr as Constant, Constant.NullRef as Constant)
        }
    }

    @Nested
    inner class UndefTests {

        @Test
        fun typeIsWhatWasProvided() {
            val c = Constant.Undef(Type.I32)
            assertEquals(Type.I32, c.type)
        }

        @Test
        fun nameIsUndef() {
            assertEquals("undef", Constant.Undef(Type.F64).name)
        }

        @Test
        fun equalityWithSameType() {
            assertEquals(Constant.Undef(Type.I32), Constant.Undef(Type.I32))
        }

        @Test
        fun inequalityWithDifferentType() {
            assertNotEquals(Constant.Undef(Type.I32), Constant.Undef(Type.I64))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.Undef(Type.I32).hashCode(), Constant.Undef(Type.I32).hashCode())
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.Undef(Type.I8)
            assertEquals(Type.I8, v.type)
            assertEquals("undef", v.name)
        }
    }

    @Nested
    inner class PoisonTests {

        @Test
        fun typeIsWhatWasProvided() {
            assertEquals(Type.F32, Constant.Poison(Type.F32).type)
        }

        @Test
        fun nameIsPoison() {
            assertEquals("poison", Constant.Poison(Type.I64).name)
        }

        @Test
        fun equalityWithSameType() {
            assertEquals(Constant.Poison(Type.I32), Constant.Poison(Type.I32))
        }

        @Test
        fun inequalityWithDifferentType() {
            assertNotEquals(Constant.Poison(Type.I32), Constant.Poison(Type.I64))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(Constant.Poison(Type.I32).hashCode(), Constant.Poison(Type.I32).hashCode())
        }

        @Test
        fun notEqualToUndefOfSameType() {
            assertNotEquals(Constant.Poison(Type.I32) as Constant, Constant.Undef(Type.I32) as Constant)
        }
    }

    @Nested
    inner class ZeroInitializerTests {

        @Test
        fun typeIsWhatWasProvided() {
            val structType = Type.Struct("test", listOf(Type.I32, Type.F64))
            assertEquals(structType, Constant.ZeroInitializer(structType).type)
        }

        @Test
        fun nameIsZeroinitializer() {
            assertEquals("zeroinitializer", Constant.ZeroInitializer(Type.I32).name)
        }

        @Test
        fun equalityWithSameType() {
            assertEquals(Constant.ZeroInitializer(Type.I32), Constant.ZeroInitializer(Type.I32))
        }

        @Test
        fun inequalityWithDifferentType() {
            assertNotEquals(Constant.ZeroInitializer(Type.I32), Constant.ZeroInitializer(Type.I64))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(
                Constant.ZeroInitializer(Type.I32).hashCode(),
                Constant.ZeroInitializer(Type.I32).hashCode()
            )
        }

        @Test
        fun worksWithArrayType() {
            val arrayType = Type.Array(Type.I8, 16)
            val c = Constant.ZeroInitializer(arrayType)
            assertEquals(arrayType, c.type)
            assertEquals("zeroinitializer", c.name)
        }
    }

    @Nested
    inner class ArrayConstTests {

        @Test
        fun typeIsWhatWasProvided() {
            val arrayType = Type.Array(Type.I32, 3)
            val c = Constant.ArrayConst(arrayType, listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3)))
            assertEquals(arrayType, c.type)
        }

        @Test
        fun nameRendersBrackets() {
            val c = Constant.ArrayConst(
                Type.Array(Type.I32, 3),
                listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3))
            )
            assertEquals("[1, 2, 3]", c.name)
        }

        @Test
        fun emptyArray() {
            val c = Constant.ArrayConst(Type.Array(Type.I32, 0), emptyList())
            assertEquals("[]", c.name)
        }

        @Test
        fun singleElement() {
            val c = Constant.ArrayConst(Type.Array(Type.I32, 1), listOf(Constant.I32(42)))
            assertEquals("[42]", c.name)
        }

        @Test
        fun equalityWithSameElements() {
            val elems = listOf(Constant.I32(1), Constant.I32(2))
            val type = Type.Array(Type.I32, 2)
            assertEquals(Constant.ArrayConst(type, elems), Constant.ArrayConst(type, elems))
        }

        @Test
        fun inequalityWithDifferentElements() {
            val type = Type.Array(Type.I32, 2)
            assertNotEquals(
                Constant.ArrayConst(type, listOf(Constant.I32(1), Constant.I32(2))),
                Constant.ArrayConst(type, listOf(Constant.I32(3), Constant.I32(4)))
            )
        }

        @Test
        fun hashCodeConsistent() {
            val elems = listOf(Constant.I32(1))
            val type = Type.Array(Type.I32, 1)
            assertEquals(
                Constant.ArrayConst(type, elems).hashCode(),
                Constant.ArrayConst(type, elems).hashCode()
            )
        }

        @Test
        fun implementsValue() {
            val type = Type.Array(Type.I32, 1)
            val v: Value = Constant.ArrayConst(type, listOf(Constant.I32(5)))
            assertEquals(type, v.type)
            assertEquals("[5]", v.name)
        }
    }

    @Nested
    inner class VectorConstTests {

        @Test
        fun typeIsWhatWasProvided() {
            val vecType = Type.Vector(Type.F32, 4)
            val c = Constant.VectorConst(vecType, listOf(Constant.F32(1.0f), Constant.F32(2.0f)))
            assertEquals(vecType, c.type)
        }

        @Test
        fun nameRendersAngleBrackets() {
            val c = Constant.VectorConst(
                Type.Vector(Type.I32, 2),
                listOf(Constant.I32(10), Constant.I32(20))
            )
            assertEquals("<10, 20>", c.name)
        }

        @Test
        fun emptyVector() {
            val c = Constant.VectorConst(Type.Vector(Type.I32, 0), emptyList())
            assertEquals("<>", c.name)
        }

        @Test
        fun singleElement() {
            val c = Constant.VectorConst(Type.Vector(Type.I32, 1), listOf(Constant.I32(7)))
            assertEquals("<7>", c.name)
        }

        @Test
        fun equality() {
            val type = Type.Vector(Type.I32, 2)
            val elems = listOf(Constant.I32(1), Constant.I32(2))
            assertEquals(Constant.VectorConst(type, elems), Constant.VectorConst(type, elems))
        }

        @Test
        fun hashCodeConsistent() {
            val type = Type.Vector(Type.I32, 2)
            val elems = listOf(Constant.I32(1), Constant.I32(2))
            assertEquals(
                Constant.VectorConst(type, elems).hashCode(),
                Constant.VectorConst(type, elems).hashCode()
            )
        }
    }

    @Nested
    inner class StructConstTests {

        @Test
        fun typeIsWhatWasProvided() {
            val structType = Type.Struct("point", listOf(Type.I32, Type.I32))
            val c = Constant.StructConst(structType, listOf(Constant.I32(1), Constant.I32(2)))
            assertEquals(structType, c.type)
        }

        @Test
        fun nameRendersCurlyBraces() {
            val c = Constant.StructConst(
                Type.Struct("test", listOf(Type.I32, Type.F64)),
                listOf(Constant.I32(10), Constant.F64(3.14))
            )
            assertEquals("{10, 3.14}", c.name)
        }

        @Test
        fun emptyStruct() {
            val c = Constant.StructConst(Type.Struct("empty", emptyList()), emptyList())
            assertEquals("{}", c.name)
        }

        @Test
        fun singleField() {
            val c = Constant.StructConst(
                Type.Struct("wrapper", listOf(Type.I64)),
                listOf(Constant.I64(99L))
            )
            assertEquals("{99}", c.name)
        }

        @Test
        fun equality() {
            val type = Type.Struct("s", listOf(Type.I32))
            val fields = listOf(Constant.I32(1))
            assertEquals(Constant.StructConst(type, fields), Constant.StructConst(type, fields))
        }

        @Test
        fun inequalityDifferentFields() {
            val type = Type.Struct("s", listOf(Type.I32))
            assertNotEquals(
                Constant.StructConst(type, listOf(Constant.I32(1))),
                Constant.StructConst(type, listOf(Constant.I32(2)))
            )
        }

        @Test
        fun hashCodeConsistent() {
            val type = Type.Struct("s", listOf(Type.I32))
            val fields = listOf(Constant.I32(1))
            assertEquals(
                Constant.StructConst(type, fields).hashCode(),
                Constant.StructConst(type, fields).hashCode()
            )
        }
    }

    @Nested
    inner class StringConstTests {

        @Test
        fun typeIsArrayOfI8WithNullTerminator() {
            val c = Constant.StringConst("hello")
            val expected = Type.Array(Type.I8, 6) // 5 chars + 1 null
            assertEquals(expected, c.type)
        }

        @Test
        fun typeWithoutNullTerminator() {
            val c = Constant.StringConst("hello", nullTerminated = false)
            val expected = Type.Array(Type.I8, 5)
            assertEquals(expected, c.type)
        }

        @Test
        fun nameIsQuotedString() {
            assertEquals("\"hello\"", Constant.StringConst("hello").name)
        }

        @Test
        fun emptyString() {
            val c = Constant.StringConst("")
            assertEquals(Type.Array(Type.I8, 1), c.type) // just the null terminator
            assertEquals("\"\"", c.name)
        }

        @Test
        fun emptyStringWithoutNullTerminator() {
            val c = Constant.StringConst("", nullTerminated = false)
            assertEquals(Type.Array(Type.I8, 0), c.type)
        }

        @Test
        fun nullTerminatedDefaultTrue() {
            val c = Constant.StringConst("abc")
            assertTrue(c.nullTerminated)
        }

        @Test
        fun equality() {
            assertEquals(Constant.StringConst("test"), Constant.StringConst("test"))
            assertNotEquals(Constant.StringConst("test"), Constant.StringConst("other"))
        }

        @Test
        fun inequalityNullTerminatedDiffers() {
            assertNotEquals(
                Constant.StringConst("test", true),
                Constant.StringConst("test", false)
            )
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(
                Constant.StringConst("test").hashCode(),
                Constant.StringConst("test").hashCode()
            )
        }

        @Test
        fun implementsValue() {
            val v: Value = Constant.StringConst("hi")
            assertEquals(Type.Array(Type.I8, 3), v.type)
            assertEquals("\"hi\"", v.name)
        }
    }

    @Nested
    inner class GetElementPtrTests {

        @Test
        fun typeIsWhatWasProvided() {
            val c = Constant.GetElementPtr(
                type = Type.OpaquePointer,
                base = Constant.NullPtr,
                indices = listOf(Constant.I32(0)),
                inBounds = true
            )
            assertEquals(Type.OpaquePointer, c.type)
        }

        @Test
        fun nameIsGetelementptr() {
            val c = Constant.GetElementPtr(
                type = Type.OpaquePointer,
                base = Constant.NullPtr,
                indices = listOf(Constant.I32(0))
            )
            assertEquals("getelementptr", c.name)
        }

        @Test
        fun inBoundsDefaultTrue() {
            val c = Constant.GetElementPtr(
                type = Type.OpaquePointer,
                base = Constant.NullPtr,
                indices = listOf(Constant.I32(0))
            )
            assertTrue(c.inBounds)
        }

        @Test
        fun inBoundsCanBeFalse() {
            val c = Constant.GetElementPtr(
                type = Type.OpaquePointer,
                base = Constant.NullPtr,
                indices = listOf(Constant.I32(0)),
                inBounds = false
            )
            assertFalse(c.inBounds)
        }

        @Test
        fun multipleIndices() {
            val indices = listOf(Constant.I32(0), Constant.I32(1), Constant.I32(2))
            val c = Constant.GetElementPtr(
                type = Type.OpaquePointer,
                base = Constant.NullPtr,
                indices = indices
            )
            assertEquals(3, c.indices.size)
        }

        @Test
        fun equality() {
            val a = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0)))
            val b = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0)))
            assertEquals(a, b)
        }

        @Test
        fun inequalityDifferentIndices() {
            val a = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0)))
            val b = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(1)))
            assertNotEquals(a, b)
        }

        @Test
        fun hashCodeConsistent() {
            val a = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0)))
            val b = Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0)))
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class BitCastTests {

        @Test
        fun typeIsWhatWasProvided() {
            val c = Constant.BitCast(Type.OpaquePointer, Constant.I64(0L))
            assertEquals(Type.OpaquePointer, c.type)
        }

        @Test
        fun nameIsBitcast() {
            assertEquals("bitcast", Constant.BitCast(Type.I32, Constant.F32(1.0f)).name)
        }

        @Test
        fun storesSourceValue() {
            val source = Constant.I64(42L)
            val c = Constant.BitCast(Type.F64, source)
            assertEquals(source, c.value)
        }

        @Test
        fun equality() {
            val a = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            val b = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            assertEquals(a, b)
        }

        @Test
        fun inequalityDifferentTargetType() {
            val a = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            val b = Constant.BitCast(Type.I64, Constant.F32(1.0f))
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityDifferentSourceValue() {
            val a = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            val b = Constant.BitCast(Type.I32, Constant.F32(2.0f))
            assertNotEquals(a, b)
        }

        @Test
        fun hashCodeConsistent() {
            val a = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            val b = Constant.BitCast(Type.I32, Constant.F32(1.0f))
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class IntToPtrTests {

        @Test
        fun typeIsWhatWasProvided() {
            val c = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(0xDEADBEEFL))
            assertEquals(Type.OpaquePointer, c.type)
        }

        @Test
        fun nameIsInttoptr() {
            assertEquals("inttoptr", Constant.IntToPtr(Type.OpaquePointer, Constant.I64(0L)).name)
        }

        @Test
        fun storesSourceValue() {
            val source = Constant.I64(12345L)
            val c = Constant.IntToPtr(Type.OpaquePointer, source)
            assertEquals(source, c.value)
        }

        @Test
        fun equality() {
            val a = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(100L))
            val b = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(100L))
            assertEquals(a, b)
        }

        @Test
        fun inequalityDifferentValue() {
            val a = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(100L))
            val b = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(200L))
            assertNotEquals(a, b)
        }

        @Test
        fun hashCodeConsistent() {
            val a = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(100L))
            val b = Constant.IntToPtr(Type.OpaquePointer, Constant.I64(100L))
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class PtrToIntTests {

        @Test
        fun typeIsWhatWasProvided() {
            val c = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            assertEquals(Type.I64, c.type)
        }

        @Test
        fun nameIsPtrtoint() {
            assertEquals("ptrtoint", Constant.PtrToInt(Type.I64, Constant.NullPtr).name)
        }

        @Test
        fun storesSourceValue() {
            val c = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            assertEquals(Constant.NullPtr, c.value)
        }

        @Test
        fun equality() {
            val a = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            val b = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            assertEquals(a, b)
        }

        @Test
        fun inequalityDifferentType() {
            val a = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            val b = Constant.PtrToInt(Type.I32, Constant.NullPtr)
            assertNotEquals(a, b)
        }

        @Test
        fun hashCodeConsistent() {
            val a = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            val b = Constant.PtrToInt(Type.I64, Constant.NullPtr)
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class SealedInterfaceTests {

        @Test
        fun allSubtypesImplementConstant() {
            val constants: List<Constant> = listOf(
                Constant.I1(true),
                Constant.I8(1.toByte()),
                Constant.I16(1.toShort()),
                Constant.I32(1),
                Constant.I64(1L),
                Constant.I128(1L),
                Constant.IntN(1L, 24),
                Constant.F16(1.0f),
                Constant.BF16(1.0f),
                Constant.F32(1.0f),
                Constant.F64(1.0),
                Constant.F80(1.0),
                Constant.F128(1.0),
                Constant.NullPtr,
                Constant.NullRef,
                Constant.Undef(Type.I32),
                Constant.Poison(Type.I32),
                Constant.ZeroInitializer(Type.I32),
                Constant.ArrayConst(Type.Array(Type.I32, 1), listOf(Constant.I32(1))),
                Constant.VectorConst(Type.Vector(Type.I32, 1), listOf(Constant.I32(1))),
                Constant.StructConst(Type.Struct("s", listOf(Type.I32)), listOf(Constant.I32(1))),
                Constant.StringConst("test"),
                Constant.GetElementPtr(Type.OpaquePointer, Constant.NullPtr, listOf(Constant.I32(0))),
                Constant.BitCast(Type.I32, Constant.F32(1.0f)),
                Constant.IntToPtr(Type.OpaquePointer, Constant.I64(0L)),
                Constant.PtrToInt(Type.I64, Constant.NullPtr)
            )

            for (c in constants) {
                assertNotNull(c.type, "${c::class.simpleName} should have a type")
                assertNotNull(c.name, "${c::class.simpleName} should have a name")
            }
        }

        @Test
        fun allSubtypesImplementValue() {
            val values: List<Value> = listOf(
                Constant.I1(false),
                Constant.I32(42),
                Constant.F64(3.14),
                Constant.NullPtr,
                Constant.NullRef,
                Constant.Undef(Type.I32),
                Constant.Poison(Type.I32),
                Constant.ZeroInitializer(Type.I32),
                Constant.StringConst("hi")
            )

            for (v in values) {
                assertNotNull(v.type)
                assertNotNull(v.name)
            }
        }
    }

    @Nested
    inner class TypeFactoryTests {

        @Test
        fun i1Factory() {
            assertEquals(Constant.I1(true), Type.i1(true))
            assertEquals(Constant.I1(false), Type.i1(false))
        }

        @Test
        fun i8Factory() {
            assertEquals(Constant.I8(42.toByte()), Type.i8(42))
        }

        @Test
        fun i16Factory() {
            assertEquals(Constant.I16(1000.toShort()), Type.i16(1000))
        }

        @Test
        fun i32Factory() {
            assertEquals(Constant.I32(42), Type.i32(42))
        }

        @Test
        fun i64Factory() {
            assertEquals(Constant.I64(42L), Type.i64(42L))
        }

        @Test
        fun i128Factory() {
            assertEquals(Constant.I128(42L), Type.i128(42L))
        }

        @Test
        fun f16Factory() {
            assertEquals(Constant.F16(1.5f), Type.f16(1.5f))
        }

        @Test
        fun bf16Factory() {
            assertEquals(Constant.BF16(1.5f), Type.bf16(1.5f))
        }

        @Test
        fun f32Factory() {
            assertEquals(Constant.F32(3.14f), Type.f32(3.14f))
        }

        @Test
        fun f64Factory() {
            assertEquals(Constant.F64(3.14), Type.f64(3.14))
        }

        @Test
        fun stringFactory() {
            assertEquals(Constant.StringConst("hello"), Type.string("hello"))
        }

        @Test
        fun stringFactoryWithNullTerminatedFalse() {
            assertEquals(Constant.StringConst("hello", false), Type.string("hello", false))
        }

        @Test
        fun nullPtrFactory() {
            assertSame(Constant.NullPtr, Type.nullPtr())
        }

        @Test
        fun nullRefFactory() {
            assertSame(Constant.NullRef, Type.nullRef())
        }

        @Test
        fun zeroFactory() {
            assertEquals(Constant.ZeroInitializer(Type.I32), Type.zero(Type.I32))
        }

        @Test
        fun undefFactory() {
            assertEquals(Constant.Undef(Type.I32), Type.undef(Type.I32))
        }

        @Test
        fun poisonFactory() {
            assertEquals(Constant.Poison(Type.I32), Type.poison(Type.I32))
        }
    }

    @Nested
    inner class NestedConstantTests {

        @Test
        fun arrayOfStructs() {
            val structType = Type.Struct("point", listOf(Type.I32, Type.I32))
            val arrayType = Type.Array(structType, 2)
            val s1 = Constant.StructConst(structType, listOf(Constant.I32(1), Constant.I32(2)))
            val s2 = Constant.StructConst(structType, listOf(Constant.I32(3), Constant.I32(4)))
            val array = Constant.ArrayConst(arrayType, listOf(s1, s2))

            assertEquals(arrayType, array.type)
            assertEquals("[{1, 2}, {3, 4}]", array.name)
        }

        @Test
        fun structOfArrays() {
            val arrayType = Type.Array(Type.I32, 2)
            val structType = Type.Struct("nested", listOf(arrayType, arrayType))
            val a1 = Constant.ArrayConst(arrayType, listOf(Constant.I32(10), Constant.I32(20)))
            val a2 = Constant.ArrayConst(arrayType, listOf(Constant.I32(30), Constant.I32(40)))
            val s = Constant.StructConst(structType, listOf(a1, a2))

            assertEquals("{[10, 20], [30, 40]}", s.name)
        }

        @Test
        fun vectorOfNestedNames() {
            val vecType = Type.Vector(Type.I32, 3)
            val vec = Constant.VectorConst(vecType, listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3)))
            assertEquals("<1, 2, 3>", vec.name)
        }
    }
}

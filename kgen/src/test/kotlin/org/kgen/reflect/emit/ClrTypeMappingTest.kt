package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.FieldFlag
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.target.clr.CilFieldFlags
import org.kgen.target.clr.CilMethodFlags
import org.kgen.target.clr.CilSigType

class ClrTypeMappingTest {

    @Nested
    inner class MapTypeRef {

        @Test
        fun voidMapsToVoid() {
            assertEquals(CilSigType.VOID, mapTypeRef(TypeRef.VOID))
        }

        @Test
        fun boolMapsToBoolean() {
            assertEquals(CilSigType.BOOLEAN, mapTypeRef(TypeRef.BOOL))
        }

        @Test
        fun i8MapsToI1() {
            assertEquals(CilSigType.I1, mapTypeRef(TypeRef.I8))
        }

        @Test
        fun i16MapsToI2() {
            assertEquals(CilSigType.I2, mapTypeRef(TypeRef.I16))
        }

        @Test
        fun i32MapsToI4() {
            assertEquals(CilSigType.I4, mapTypeRef(TypeRef.I32))
        }

        @Test
        fun i64MapsToI8() {
            assertEquals(CilSigType.I8, mapTypeRef(TypeRef.I64))
        }

        @Test
        fun u8MapsToU1() {
            assertEquals(CilSigType.U1, mapTypeRef(TypeRef.U8))
        }

        @Test
        fun u16MapsToU2() {
            assertEquals(CilSigType.U2, mapTypeRef(TypeRef.U16))
        }

        @Test
        fun u32MapsToU4() {
            assertEquals(CilSigType.U4, mapTypeRef(TypeRef.U32))
        }

        @Test
        fun u64MapsToU8() {
            assertEquals(CilSigType.U8, mapTypeRef(TypeRef.U64))
        }

        @Test
        fun f32MapsToR4() {
            assertEquals(CilSigType.R4, mapTypeRef(TypeRef.F32))
        }

        @Test
        fun f64MapsToR8() {
            assertEquals(CilSigType.R8, mapTypeRef(TypeRef.F64))
        }

        @Test
        fun pointerMapsToI() {
            assertEquals(CilSigType.I, mapTypeRef(TypeRef.POINTER))
        }

        @Test
        fun stringNameMapsToString() {
            assertEquals(CilSigType.STRING, mapTypeRef(TypeRef.of("string")))
        }

        @Test
        fun systemStringMapsToString() {
            assertEquals(CilSigType.STRING, mapTypeRef(TypeRef.of("System.String")))
        }

        @Test
        fun objectNameMapsToObject() {
            assertEquals(CilSigType.OBJECT, mapTypeRef(TypeRef.of("object")))
        }

        @Test
        fun systemObjectMapsToObject() {
            assertEquals(CilSigType.OBJECT, mapTypeRef(TypeRef.of("System.Object")))
        }

        @Test
        fun unsupportedTypeThrows() {
            assertThrows(IllegalStateException::class.java) {
                mapTypeRef(TypeRef.of("SomeRandomType"))
            }
        }
    }

    @Nested
    inner class MapSignatureTest {

        @Test
        fun voidNoParams() {
            val sig = Signature.VOID
            val bytes = mapSignature(sig)
            assertNotNull(bytes)
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun staticSignatureIsNotNull() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
            val bytes = mapSignature(sig)
            assertNotNull(bytes)
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun instanceSignatureIsNotNull() {
            val sig = Signature.of(TypeRef.I32)
            val bytes = mapSignature(sig, instance = true)
            assertNotNull(bytes)
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun staticVsInstanceDiffer() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32)
            val staticBytes = mapSignature(sig, instance = false)
            val instanceBytes = mapSignature(sig, instance = true)
            assertFalse(staticBytes.contentEquals(instanceBytes))
        }
    }

    @Nested
    inner class MapMethodFlagsTest {

        @Test
        fun publicFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.PUBLIC))
            assertTrue(flags and CilMethodFlags.PUBLIC != 0)
        }

        @Test
        fun privateFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.PRIVATE))
            assertTrue(flags and CilMethodFlags.PRIVATE != 0)
        }

        @Test
        fun protectedFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.PROTECTED))
            assertTrue(flags and CilMethodFlags.FAMILY != 0)
        }

        @Test
        fun staticFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.STATIC))
            assertTrue(flags and CilMethodFlags.STATIC != 0)
        }

        @Test
        fun virtualFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.VIRTUAL))
            assertTrue(flags and CilMethodFlags.VIRTUAL != 0)
        }

        @Test
        fun abstractFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.ABSTRACT))
            assertTrue(flags and CilMethodFlags.ABSTRACT != 0)
        }

        @Test
        fun finalFlag() {
            val flags = mapMethodFlags(setOf(MethodFlag.FINAL))
            assertTrue(flags and CilMethodFlags.FINAL != 0)
        }

        @Test
        fun constructorFlagSetsSpecialName() {
            val flags = mapMethodFlags(setOf(MethodFlag.CONSTRUCTOR))
            assertTrue(flags and CilMethodFlags.SPECIAL_NAME != 0)
            assertTrue(flags and CilMethodFlags.RT_SPECIAL_NAME != 0)
        }

        @Test
        fun alwaysIncludesHideBySig() {
            val flags = mapMethodFlags(emptySet())
            assertTrue(flags and CilMethodFlags.HIDE_BY_SIG != 0)
        }

        @Test
        fun multipleFlagsCombined() {
            val flags = mapMethodFlags(setOf(MethodFlag.PUBLIC, MethodFlag.STATIC, MethodFlag.FINAL))
            assertTrue(flags and CilMethodFlags.PUBLIC != 0)
            assertTrue(flags and CilMethodFlags.STATIC != 0)
            assertTrue(flags and CilMethodFlags.FINAL != 0)
        }
    }

    @Nested
    inner class MapFieldFlagsTest {

        @Test
        fun publicFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.PUBLIC))
            assertTrue(flags and CilFieldFlags.PUBLIC != 0)
        }

        @Test
        fun privateFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.PRIVATE))
            assertTrue(flags and CilFieldFlags.PRIVATE != 0)
        }

        @Test
        fun protectedFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.PROTECTED))
            assertTrue(flags and CilFieldFlags.FAMILY != 0)
        }

        @Test
        fun staticFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.STATIC))
            assertTrue(flags and CilFieldFlags.STATIC != 0)
        }

        @Test
        fun readonlyFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.READONLY))
            assertTrue(flags and CilFieldFlags.INIT_ONLY != 0)
        }

        @Test
        fun constFlag() {
            val flags = mapFieldFlags(setOf(FieldFlag.CONST))
            assertTrue(flags and CilFieldFlags.LITERAL != 0)
        }

        @Test
        fun emptyFlagsReturnsZero() {
            assertEquals(0, mapFieldFlags(emptySet()))
        }

        @Test
        fun multipleFlagsCombined() {
            val flags = mapFieldFlags(setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.READONLY))
            assertTrue(flags and CilFieldFlags.PUBLIC != 0)
            assertTrue(flags and CilFieldFlags.STATIC != 0)
            assertTrue(flags and CilFieldFlags.INIT_ONLY != 0)
        }
    }
}

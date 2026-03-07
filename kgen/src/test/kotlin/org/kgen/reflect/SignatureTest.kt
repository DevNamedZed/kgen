package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SignatureTest {

    @Test
    fun voidSignature() {
        val sig = Signature.VOID
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(0, sig.parameterCount())
        assertTrue(sig.parameters().isEmpty())
        assertTrue(sig.parameterTypes().isEmpty())
    }

    @Test
    fun longToLong() {
        val sig = Signature.LONG_TO_LONG
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(1, sig.parameterCount())
        assertEquals(TypeRef.I64, sig.parameterTypes()[0])
    }

    @Test
    fun longLongToLong() {
        val sig = Signature.LONG_LONG_TO_LONG
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals(listOf(TypeRef.I64, TypeRef.I64), sig.parameterTypes())
    }

    @Test
    fun builderUnnamed() {
        val sig = Signature.returning(TypeRef.I64)
            .param(TypeRef.POINTER)
            .param(TypeRef.I32)
            .build()

        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals(TypeRef.POINTER, sig.parameterTypes()[0])
        assertEquals(TypeRef.I32, sig.parameterTypes()[1])
        assertNull(sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
    }

    @Test
    fun builderNamed() {
        val sig = Signature.returning(TypeRef.F64)
            .param("x", TypeRef.F64)
            .param("y", TypeRef.F64)
            .build()

        assertEquals(TypeRef.F64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals("x", sig.parameters()[0].name)
        assertEquals("y", sig.parameters()[1].name)
        assertEquals(TypeRef.F64, sig.parameters()[0].type)
    }

    @Test
    fun builderMixed() {
        val sig = Signature.returning(TypeRef.I32)
            .param("buf", TypeRef.POINTER)
            .param(TypeRef.I64)
            .build()

        assertEquals("buf", sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
    }

    @Test
    fun builderVoid() {
        val sig = Signature.returningVoid()
            .param(TypeRef.POINTER)
            .build()

        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(1, sig.parameterCount())
    }

    @Test
    fun factoryOf() {
        val sig = Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64)
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals(listOf(TypeRef.I64, TypeRef.I64), sig.parameterTypes())
    }

    @Test
    fun factoryOfVoid() {
        val sig = Signature.ofVoid(TypeRef.POINTER, TypeRef.I64)
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }

    @Test
    fun equality() {
        val a = Signature.of(TypeRef.I64, TypeRef.I64)
        val b = Signature.of(TypeRef.I64, TypeRef.I64)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = Signature.of(TypeRef.I32, TypeRef.I64)
        assertNotEquals(a, c)
    }

    @Test
    fun toStringFormat() {
        val sig = Signature.returning(TypeRef.I64)
            .param("a", TypeRef.I64)
            .param("b", TypeRef.I64)
            .build()
        assertEquals("(a: i64, b: i64) -> i64", sig.toString())
    }

    @Test
    fun toStringNoParams() {
        assertEquals("() -> void", Signature.VOID.toString())
    }

    @Test
    fun toStringUnnamed() {
        val sig = Signature.of(TypeRef.I64, TypeRef.POINTER, TypeRef.I32)
        assertEquals("(ptr, i32) -> i64", sig.toString())
    }

    @Test
    fun signatureParamToString() {
        assertEquals("i64", SignatureParam(null, TypeRef.I64).toString())
        assertEquals("buf: ptr", SignatureParam("buf", TypeRef.POINTER).toString())
    }
}

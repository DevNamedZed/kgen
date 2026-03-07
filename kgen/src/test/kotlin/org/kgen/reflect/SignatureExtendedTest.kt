package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SignatureExtendedTest {

    // -- Static constants --

    @Test
    fun voidSignatureReturnType() {
        assertEquals(TypeRef.VOID, Signature.VOID.returnType())
    }

    @Test
    fun voidSignatureNoParams() {
        assertEquals(0, Signature.VOID.parameterCount())
        assertTrue(Signature.VOID.parameters().isEmpty())
        assertTrue(Signature.VOID.parameterTypes().isEmpty())
    }

    @Test
    fun longToLongReturnType() {
        assertEquals(TypeRef.I64, Signature.LONG_TO_LONG.returnType())
    }

    @Test
    fun longToLongParamCount() {
        assertEquals(1, Signature.LONG_TO_LONG.parameterCount())
        assertEquals(TypeRef.I64, Signature.LONG_TO_LONG.parameterTypes()[0])
    }

    @Test
    fun longLongToLongReturnType() {
        assertEquals(TypeRef.I64, Signature.LONG_LONG_TO_LONG.returnType())
    }

    @Test
    fun longLongToLongParamCount() {
        assertEquals(2, Signature.LONG_LONG_TO_LONG.parameterCount())
    }

    // -- Builder with named params --

    @Test
    fun builderSingleNamedParam() {
        val sig = Signature.returning(TypeRef.I32)
            .param("x", TypeRef.I32)
            .build()
        assertEquals(1, sig.parameterCount())
        assertEquals("x", sig.parameters()[0].name)
        assertEquals(TypeRef.I32, sig.parameters()[0].type)
    }

    @Test
    fun builderMultipleNamedParams() {
        val sig = Signature.returning(TypeRef.F64)
            .param("a", TypeRef.F64)
            .param("b", TypeRef.F64)
            .param("c", TypeRef.F64)
            .build()
        assertEquals(3, sig.parameterCount())
        assertEquals("a", sig.parameters()[0].name)
        assertEquals("c", sig.parameters()[2].name)
    }

    @Test
    fun builderUnnamedParams() {
        val sig = Signature.returning(TypeRef.I64)
            .param(TypeRef.POINTER)
            .param(TypeRef.I32)
            .build()
        assertNull(sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
    }

    @Test
    fun builderMixedNamedAndUnnamed() {
        val sig = Signature.returning(TypeRef.I32)
            .param("buf", TypeRef.POINTER)
            .param(TypeRef.I64)
            .param("flags", TypeRef.I32)
            .build()
        assertEquals("buf", sig.parameters()[0].name)
        assertNull(sig.parameters()[1].name)
        assertEquals("flags", sig.parameters()[2].name)
    }

    @Test
    fun builderReturningVoid() {
        val sig = Signature.returningVoid()
            .param(TypeRef.POINTER)
            .build()
        assertEquals(TypeRef.VOID, sig.returnType())
    }

    @Test
    fun builderNoParams() {
        val sig = Signature.returning(TypeRef.I32).build()
        assertEquals(0, sig.parameterCount())
        assertTrue(sig.parameters().isEmpty())
    }

    // -- Factory methods --

    @Test
    fun factoryOfSingleParam() {
        val sig = Signature.of(TypeRef.I32, TypeRef.I32)
        assertEquals(TypeRef.I32, sig.returnType())
        assertEquals(1, sig.parameterCount())
    }

    @Test
    fun factoryOfNoParams() {
        val sig = Signature.of(TypeRef.I64)
        assertEquals(0, sig.parameterCount())
        assertEquals(TypeRef.I64, sig.returnType())
    }

    @Test
    fun factoryOfVoidSingleParam() {
        val sig = Signature.ofVoid(TypeRef.POINTER)
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(1, sig.parameterCount())
    }

    @Test
    fun factoryOfVoidNoParams() {
        val sig = Signature.ofVoid()
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(0, sig.parameterCount())
    }

    @Test
    fun factoryOfManyParams() {
        val sig = Signature.of(TypeRef.I32, TypeRef.POINTER, TypeRef.I64, TypeRef.F64, TypeRef.BOOL)
        assertEquals(4, sig.parameterCount())
    }

    // -- Equality --

    @Test
    fun equalSignaturesAreEqual() {
        val a = Signature.of(TypeRef.I64, TypeRef.I64)
        val b = Signature.of(TypeRef.I64, TypeRef.I64)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentReturnTypeNotEqual() {
        val a = Signature.of(TypeRef.I64, TypeRef.I64)
        val b = Signature.of(TypeRef.I32, TypeRef.I64)
        assertNotEquals(a, b)
    }

    @Test
    fun differentParamsNotEqual() {
        val a = Signature.of(TypeRef.I64, TypeRef.I32)
        val b = Signature.of(TypeRef.I64, TypeRef.F64)
        assertNotEquals(a, b)
    }

    @Test
    fun differentParamCountNotEqual() {
        val a = Signature.of(TypeRef.I64, TypeRef.I32)
        val b = Signature.of(TypeRef.I64, TypeRef.I32, TypeRef.I32)
        assertNotEquals(a, b)
    }

    @Test
    fun signatureNotEqualToNull() {
        assertNotEquals(Signature.VOID, null)
    }

    @Test
    fun signatureNotEqualToOtherType() {
        assertNotEquals(Signature.VOID, "not a signature")
    }

    // -- toString --

    @Test
    fun toStringVoidNoParams() {
        assertEquals("() -> void", Signature.VOID.toString())
    }

    @Test
    fun toStringWithNamedParams() {
        val sig = Signature.returning(TypeRef.I64)
            .param("a", TypeRef.I64)
            .param("b", TypeRef.I64)
            .build()
        assertEquals("(a: i64, b: i64) -> i64", sig.toString())
    }

    @Test
    fun toStringWithUnnamedParams() {
        val sig = Signature.of(TypeRef.I64, TypeRef.POINTER, TypeRef.I32)
        assertEquals("(ptr, i32) -> i64", sig.toString())
    }

    @Test
    fun toStringSingleParam() {
        val sig = Signature.of(TypeRef.I32, TypeRef.BOOL)
        assertEquals("(bool) -> i32", sig.toString())
    }

    // -- SignatureParam --

    @Test
    fun signatureParamNamedToString() {
        val p = SignatureParam("x", TypeRef.F64)
        assertEquals("x: f64", p.toString())
    }

    @Test
    fun signatureParamUnnamedToString() {
        val p = SignatureParam(null, TypeRef.I32)
        assertEquals("i32", p.toString())
    }

    @Test
    fun signatureParamEquality() {
        val a = SignatureParam("x", TypeRef.I32)
        val b = SignatureParam("x", TypeRef.I32)
        assertEquals(a, b)
    }

    @Test
    fun signatureParamInequality() {
        val a = SignatureParam("x", TypeRef.I32)
        val b = SignatureParam("y", TypeRef.I32)
        assertNotEquals(a, b)
    }

    // -- parameterTypes() --

    @Test
    fun parameterTypesReturnsList() {
        val sig = Signature.returning(TypeRef.I64)
            .param("a", TypeRef.I32)
            .param("b", TypeRef.F64)
            .build()
        assertEquals(listOf(TypeRef.I32, TypeRef.F64), sig.parameterTypes())
    }
}

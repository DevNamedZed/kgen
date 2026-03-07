package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class FunctionExtendedTest {

    private fun func(
        name: String = "test",
        kind: SymbolKind = SymbolKind.FUNCTION,
        sig: Signature? = null,
        value: Long = 0,
        size: Long = 0,
        binding: SymbolBinding = SymbolBinding.GLOBAL,
    ): Function {
        val raw = org.kgen.binary.Symbol(name, value, size, ".text", binding, kind)
        return Function(Symbol(raw, null), sig)
    }

    // -- Basic accessors --

    @Test
    fun nameReturnsSymbolName() {
        assertEquals("myFunc", func(name = "myFunc").name())
    }

    @Test
    fun qualifiedNameSimple() {
        val f = func(name = "strlen")
        assertEquals("strlen", f.qualifiedName().name())
    }

    @Test
    fun qualifiedNameNamespaced() {
        val f = func(name = "std::vector::push_back")
        assertEquals("push_back", f.qualifiedName().name())
        assertEquals("std::vector", f.qualifiedName().namespace())
    }

    @Test
    fun offsetValue() {
        assertEquals(0x2000L, func(value = 0x2000).offset())
    }

    @Test
    fun sizeValue() {
        assertEquals(64L, func(size = 64).size())
    }

    @Test
    fun zeroOffset() {
        assertEquals(0L, func(value = 0).offset())
    }

    @Test
    fun zeroSize() {
        assertEquals(0L, func(size = 0).size())
    }

    // -- Code classification --

    @Test
    fun nativeFunctionIsNative() {
        assertTrue(func(kind = SymbolKind.FUNCTION).isNative())
    }

    @Test
    fun nativeFunctionIsNotBytecode() {
        assertFalse(func(kind = SymbolKind.FUNCTION).isBytecode())
    }

    @Test
    fun bytecodeIsBytecode() {
        assertTrue(func(kind = SymbolKind.METHOD).isBytecode())
    }

    @Test
    fun dataSymbolIsNative() {
        // DATA symbols aren't METHOD, so isNative returns true
        assertTrue(func(kind = SymbolKind.DATA).isNative())
    }

    // -- Module --

    @Test
    fun moduleIsNullForStandalone() {
        assertNull(func().module())
    }

    // -- Signature --

    @Test
    fun noSignatureByDefault() {
        val f = func()
        assertFalse(f.hasSignature())
        assertNull(f.signature())
    }

    @Test
    fun noSignatureReturnType() {
        assertNull(func().returnType())
    }

    @Test
    fun noSignatureParameterTypesEmpty() {
        assertTrue(func().parameterTypes().isEmpty())
    }

    @Test
    fun noSignatureParametersEmpty() {
        assertTrue(func().parameters().isEmpty())
    }

    @Test
    fun withSignatureAttaches() {
        val sig = Signature.of(TypeRef.I64, TypeRef.I32)
        val f = func().withSignature(sig)
        assertTrue(f.hasSignature())
        assertEquals(sig, f.signature())
    }

    @Test
    fun withSignatureReturnType() {
        val sig = Signature.of(TypeRef.F64, TypeRef.F64)
        val f = func().withSignature(sig)
        assertEquals(TypeRef.F64, f.returnType())
    }

    @Test
    fun withSignatureParameterTypes() {
        val sig = Signature.of(TypeRef.I64, TypeRef.POINTER, TypeRef.I32)
        val f = func().withSignature(sig)
        assertEquals(2, f.parameterTypes().size)
        assertEquals(TypeRef.POINTER, f.parameterTypes()[0])
        assertEquals(TypeRef.I32, f.parameterTypes()[1])
    }

    @Test
    fun withSignatureParameters() {
        val sig = Signature.returning(TypeRef.I64)
            .param("s", TypeRef.POINTER)
            .build()
        val f = func().withSignature(sig)
        assertEquals(1, f.parameters().size)
        assertEquals("s", f.parameters()[0].name)
    }

    @Test
    fun withSignaturePreservesName() {
        val f = func(name = "strlen").withSignature(Signature.VOID)
        assertEquals("strlen", f.name())
    }

    @Test
    fun withSignaturePreservesOffset() {
        val f = func(value = 0x1000).withSignature(Signature.VOID)
        assertEquals(0x1000L, f.offset())
    }

    // -- Symbol navigation --

    @Test
    fun symbolNotNull() {
        assertNotNull(func().symbol())
    }

    @Test
    fun symbolNameMatches() {
        assertEquals("main", func(name = "main").symbol().name())
    }

    @Test
    fun symbolIsFunction() {
        assertTrue(func().symbol().isFunction())
    }

    // -- toString --

    @Test
    fun toStringNoSignature() {
        assertEquals("myFunc", func(name = "myFunc").toString())
    }

    @Test
    fun toStringWithSignature() {
        val sig = Signature.returning(TypeRef.I32)
            .param("n", TypeRef.I32)
            .build()
        val f = func(name = "factorial").withSignature(sig)
        assertEquals("factorial: (n: i32) -> i32", f.toString())
    }

    @Test
    fun toStringWithVoidSignature() {
        val f = func(name = "init").withSignature(Signature.VOID)
        assertEquals("init: () -> void", f.toString())
    }

    // -- Equality --

    @Test
    fun equalFunctions() {
        val raw = org.kgen.binary.Symbol("test", 0x1000, 10)
        val sym = Symbol(raw, null)
        val a = Function(sym)
        val b = Function(sym)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun signatureDoesNotAffectEquality() {
        val raw = org.kgen.binary.Symbol("test", 0x1000, 10)
        val sym = Symbol(raw, null)
        val a = Function(sym, Signature.VOID)
        val b = Function(sym, Signature.LONG_TO_LONG)
        assertEquals(a, b)
    }

    @Test
    fun differentSymbolsNotEqual() {
        val rawA = org.kgen.binary.Symbol("func1", 0, 0)
        val rawB = org.kgen.binary.Symbol("func2", 0, 0)
        val a = Function(Symbol(rawA, null))
        val b = Function(Symbol(rawB, null))
        assertNotEquals(a, b)
    }

    @Test
    fun notEqualToNull() {
        assertNotEquals(func(), null)
    }

    @Test
    fun notEqualToOtherType() {
        assertNotEquals(func(), "not a function")
    }
}

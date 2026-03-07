package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class FunctionTest {

    private fun func(
        name: String = "test",
        kind: SymbolKind = SymbolKind.FUNCTION,
        sig: Signature? = null,
        value: Long = 0,
        size: Long = 0,
    ): Function {
        val raw = org.kgen.binary.Symbol(name, value, size, ".text", SymbolBinding.GLOBAL, kind)
        return Function(Symbol(raw, null), sig)
    }

    @Test
    fun nameAndQualifiedName() {
        val f = func(name = "std::vector::push_back")
        assertEquals("std::vector::push_back", f.name())
        assertEquals("push_back", f.qualifiedName().name())
    }

    @Test
    fun offsetAndSize() {
        val f = func(value = 0x1000, size = 128)
        assertEquals(0x1000L, f.offset())
        assertEquals(128L, f.size())
    }

    @Test
    fun noSignature() {
        val f = func()
        assertFalse(f.hasSignature())
        assertNull(f.signature())
        assertNull(f.returnType())
        assertTrue(f.parameterTypes().isEmpty())
        assertTrue(f.parameters().isEmpty())
    }

    @Test
    fun withSignature() {
        val sig = Signature.returning(TypeRef.I64)
            .param("n", TypeRef.I64)
            .build()
        val f = func().withSignature(sig)

        assertTrue(f.hasSignature())
        assertEquals(sig, f.signature())
        assertEquals(TypeRef.I64, f.returnType())
        assertEquals(1, f.parameterTypes().size)
        assertEquals(TypeRef.I64, f.parameterTypes()[0])
        assertEquals("n", f.parameters()[0].name)
    }

    @Test
    fun withSignaturePreservesName() {
        val sig = Signature.LONG_TO_LONG
        val f = func(name = "strlen").withSignature(sig)
        assertEquals("strlen", f.name())
    }

    @Test
    fun symbolNavigation() {
        val f = func(name = "main")
        val sym = f.symbol()
        assertEquals("main", sym.name())
        assertTrue(sym.isFunction())
    }

    @Test
    fun nativeFunctionDefault() {
        val f = func(kind = SymbolKind.FUNCTION)
        assertTrue(f.isNative())
        assertFalse(f.isBytecode())
    }

    @Test
    fun bytecodeFunction() {
        val f = func(kind = SymbolKind.METHOD)
        assertTrue(f.isBytecode())
    }

    @Test
    fun toStringNoSignature() {
        val f = func(name = "strlen")
        assertEquals("strlen", f.toString())
    }

    @Test
    fun toStringWithSignature() {
        val sig = Signature.returning(TypeRef.I64)
            .param("s", TypeRef.POINTER)
            .build()
        val f = func(name = "strlen").withSignature(sig)
        assertEquals("strlen: (s: ptr) -> i64", f.toString())
    }

    @Test
    fun equality() {
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
}

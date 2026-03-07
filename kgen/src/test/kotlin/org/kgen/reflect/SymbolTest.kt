package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class SymbolTest {

    private fun sym(
        name: String = "test",
        kind: SymbolKind = SymbolKind.FUNCTION,
        binding: SymbolBinding = SymbolBinding.GLOBAL,
        visibility: SymbolVisibility = SymbolVisibility.DEFAULT,
        flags: Set<SymbolFlag> = emptySet(),
        value: Long = 0,
        size: Long = 0,
        section: String? = ".text",
    ): Symbol = Symbol(
        org.kgen.binary.Symbol(name, value, size, section, binding, kind, visibility, flags),
        null,
    )

    @Test
    fun nameAndQualifiedName() {
        val s = sym(name = "System.String.Concat")
        assertEquals("System.String.Concat", s.name())
        assertEquals("Concat", s.qualifiedName().name())
        assertEquals("System.String", s.qualifiedName().namespace())
    }

    @Test
    fun functionSymbol() {
        val s = sym(kind = SymbolKind.FUNCTION)
        assertTrue(s.isFunction())
        assertFalse(s.isData())
        assertNotNull(s.function())
    }

    @Test
    fun methodSymbol() {
        val s = sym(kind = SymbolKind.METHOD)
        assertTrue(s.isFunction())
        assertNotNull(s.function())
    }

    @Test
    fun dataSymbol() {
        val s = sym(kind = SymbolKind.DATA)
        assertTrue(s.isData())
        assertFalse(s.isFunction())
        assertNull(s.function())
    }

    @Test
    fun undefinedSymbol() {
        val s = sym(kind = SymbolKind.UNDEFINED)
        assertTrue(s.isUndefined())
    }

    @Test
    fun undefinedFlag() {
        val s = sym(flags = setOf(SymbolFlag.UNDEFINED))
        assertTrue(s.isUndefined())
    }

    @Test
    fun exportedSymbol() {
        val s = sym(flags = setOf(SymbolFlag.EXPORTED))
        assertTrue(s.isExported())
        assertFalse(s.isImported())
    }

    @Test
    fun exportedByBindingAndVisibility() {
        val s = sym(binding = SymbolBinding.GLOBAL, visibility = SymbolVisibility.DEFAULT)
        assertTrue(s.isExported())
    }

    @Test
    fun importedSymbol() {
        val s = sym(flags = setOf(SymbolFlag.IMPORTED))
        assertTrue(s.isImported())
    }

    @Test
    fun dllImportExport() {
        val imp = sym(flags = setOf(SymbolFlag.DLL_IMPORT))
        assertTrue(imp.isImported())

        val exp = sym(flags = setOf(SymbolFlag.DLL_EXPORT))
        assertTrue(exp.isExported())
    }

    @Test
    fun wasmImportExport() {
        val imp = sym(flags = setOf(SymbolFlag.WASM_IMPORT))
        assertTrue(imp.isImported())

        val exp = sym(flags = setOf(SymbolFlag.WASM_EXPORT))
        assertTrue(exp.isExported())
    }

    @Test
    fun weakSymbol() {
        val s = sym(binding = SymbolBinding.WEAK)
        assertTrue(s.isWeak())
        assertFalse(s.isLocal())
        assertFalse(s.isGlobal())
    }

    @Test
    fun localSymbol() {
        val s = sym(binding = SymbolBinding.LOCAL)
        assertTrue(s.isLocal())
        assertFalse(s.isExported())
    }

    @Test
    fun tlsSymbol() {
        val s = sym(kind = SymbolKind.TLS)
        assertTrue(s.isTls())
    }

    @Test
    fun absoluteSymbol() {
        val s = sym(kind = SymbolKind.ABSOLUTE)
        assertTrue(s.isAbsolute())
    }

    @Test
    fun commonSymbol() {
        val s = sym(kind = SymbolKind.COMMON)
        assertTrue(s.isCommon())
    }

    @Test
    fun jvmAccessFlags() {
        val s = sym(flags = setOf(
            SymbolFlag.ACC_PUBLIC, SymbolFlag.ACC_STATIC,
            SymbolFlag.ACC_FINAL, SymbolFlag.DEPRECATED,
        ))
        assertTrue(s.isPublic())
        assertTrue(s.isStatic())
        assertTrue(s.isFinal())
        assertTrue(s.isDeprecated())
        assertFalse(s.isPrivate())
        assertFalse(s.isProtected())
        assertFalse(s.isAbstract())
    }

    @Test
    fun offsetAndSize() {
        val s = sym(value = 0x1000, size = 64)
        assertEquals(0x1000L, s.offset())
        assertEquals(64L, s.size())
    }

    @Test
    fun sectionName() {
        val s = sym(section = ".text")
        assertEquals(".text", s.sectionName())
    }

    @Test
    fun nullSection() {
        val s = sym(section = null)
        assertNull(s.sectionName())
    }

    @Test
    fun importDetails() {
        val raw = org.kgen.binary.Symbol(
            name = "MessageBoxA",
            importModule = "user32.dll",
            importName = "MessageBoxA",
            ordinal = 42,
        )
        val s = Symbol(raw, null)
        assertEquals("user32.dll", s.importModule())
        assertEquals("MessageBoxA", s.importName())
        assertEquals(42, s.ordinal())
    }

    @Test
    fun mangledCppName() {
        val s = sym(name = "_ZN3std6vectorIiE9push_backERKi")
        assertTrue(s.isMangled())
    }

    @Test
    fun notMangled() {
        val s = sym(name = "strlen")
        assertFalse(s.isMangled())
        assertNull(s.demangledName())
    }

    @Test
    fun rawAccess() {
        val raw = org.kgen.binary.Symbol("test", 42, 10)
        val s = Symbol(raw, null)
        assertSame(raw, s.raw())
    }

    @Test
    fun toStringIncludesKind() {
        val s = sym(name = "main", kind = SymbolKind.FUNCTION, flags = setOf(SymbolFlag.EXPORTED))
        val str = s.toString()
        assertTrue(str.contains("main"))
        assertTrue(str.contains("function"))
        assertTrue(str.contains("exported"))
    }

    @Test
    fun equality() {
        val raw = org.kgen.binary.Symbol("test", 0x1000, 10)
        val a = Symbol(raw, null)
        val b = Symbol(raw, null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun functionNavigationCreatesFunction() {
        val s = sym(name = "strlen", kind = SymbolKind.FUNCTION)
        val func = s.function()
        assertNotNull(func)
        assertEquals("strlen", func!!.name())
        assertSame(s, func.symbol())
    }
}

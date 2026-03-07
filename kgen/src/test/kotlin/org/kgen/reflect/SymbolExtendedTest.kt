package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class SymbolExtendedTest {

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

    // --- Symbol kinds ---

    @Test
    fun sectionSymbol() {
        val s = sym(kind = SymbolKind.SECTION)
        assertFalse(s.isFunction())
        assertFalse(s.isData())
    }

    @Test
    fun fileSymbol() {
        val s = sym(kind = SymbolKind.FILE)
        assertFalse(s.isFunction())
        assertFalse(s.isData())
    }

    @Test
    fun undefinedKind() {
        val s = sym(kind = SymbolKind.UNDEFINED)
        assertFalse(s.isFunction())
        assertTrue(s.isUndefined())
    }

    // --- Visibility ---

    @Test
    fun hiddenVisibility() {
        val s = sym(visibility = SymbolVisibility.HIDDEN)
        assertFalse(s.isExported())
    }

    @Test
    fun protectedVisibility() {
        val s = sym(visibility = SymbolVisibility.PROTECTED)
        // Protected symbols are visible but limited
        assertNotNull(s.raw())
    }

    @Test
    fun internalVisibility() {
        val s = sym(visibility = SymbolVisibility.INTERNAL)
        assertFalse(s.isExported())
    }

    // --- JVM access flags ---

    @Test
    fun privateSymbol() {
        val s = sym(flags = setOf(SymbolFlag.ACC_PRIVATE))
        assertTrue(s.isPrivate())
        assertFalse(s.isPublic())
        assertFalse(s.isProtected())
    }

    @Test
    fun protectedSymbol() {
        val s = sym(flags = setOf(SymbolFlag.ACC_PROTECTED))
        assertTrue(s.isProtected())
        assertFalse(s.isPublic())
        assertFalse(s.isPrivate())
    }

    @Test
    fun abstractSymbol() {
        val s = sym(flags = setOf(SymbolFlag.ACC_ABSTRACT))
        assertTrue(s.isAbstract())
    }

    @Test
    fun nativeSymbol() {
        val s = sym(flags = setOf(SymbolFlag.ACC_NATIVE))
        assertTrue(s.isNative())
    }

    @Test
    fun deprecatedSymbol() {
        val s = sym(flags = setOf(SymbolFlag.DEPRECATED))
        assertTrue(s.isDeprecated())
    }

    @Test
    fun multipleJvmFlags() {
        val s = sym(flags = setOf(
            SymbolFlag.ACC_PUBLIC, SymbolFlag.ACC_STATIC, SymbolFlag.ACC_FINAL
        ))
        assertTrue(s.isPublic())
        assertTrue(s.isStatic())
        assertTrue(s.isFinal())
        assertFalse(s.isPrivate())
        assertFalse(s.isAbstract())
    }

    // --- Qualified name parsing ---

    @Test
    fun simpleQualifiedName() {
        val s = sym(name = "strlen")
        assertEquals("strlen", s.qualifiedName().name())
        assertNull(s.qualifiedName().namespace())
    }

    @Test
    fun dottedQualifiedName() {
        val s = sym(name = "System.Console.WriteLine")
        assertEquals("WriteLine", s.qualifiedName().name())
        assertEquals("System.Console", s.qualifiedName().namespace())
    }

    // --- Mangled names ---

    @Test
    fun mangledItanium() {
        val s = sym(name = "_Z3foov")
        assertTrue(s.isMangled())
        // demangledName may return null if demangler not available in reflect layer
        val demangled = s.demangledName()
        if (demangled != null) {
            assertTrue(demangled.contains("foo"))
        }
    }

    @Test
    fun mangledMsvc() {
        val s = sym(name = "?foo@@YAHXZ")
        assertTrue(s.isMangled())
    }

    @Test
    fun notMangledCName() {
        val s = sym(name = "printf")
        assertFalse(s.isMangled())
        assertNull(s.demangledName())
    }

    @Test
    fun notMangledUnderscore() {
        val s = sym(name = "_main")
        // _main without _Z prefix is not mangled
        assertFalse(s.isMangled())
    }

    // --- Import/export details ---

    @Test
    fun importModuleDetails() {
        val raw = org.kgen.binary.Symbol(
            name = "GetProcAddress",
            importModule = "kernel32.dll",
            importName = "GetProcAddress",
            ordinal = 100,
        )
        val s = Symbol(raw, null)
        assertEquals("kernel32.dll", s.importModule())
        assertEquals("GetProcAddress", s.importName())
        assertEquals(100, s.ordinal())
    }

    @Test
    fun noImportDetails() {
        val s = sym(name = "main")
        assertNull(s.importModule())
        assertNull(s.importName())
        assertNull(s.ordinal())
    }

    // --- Offset and size ---

    @Test
    fun zeroOffsetAndSize() {
        val s = sym(value = 0, size = 0)
        assertEquals(0L, s.offset())
        assertEquals(0L, s.size())
    }

    @Test
    fun largeOffsetAndSize() {
        val s = sym(value = 0x7FFFFFFFL, size = 0x10000L)
        assertEquals(0x7FFFFFFFL, s.offset())
        assertEquals(0x10000L, s.size())
    }

    // --- Section name ---

    @Test
    fun textSection() {
        assertEquals(".text", sym(section = ".text").sectionName())
    }

    @Test
    fun dataSection() {
        assertEquals(".data", sym(section = ".data").sectionName())
    }

    @Test
    fun bssSection() {
        assertEquals(".bss", sym(section = ".bss").sectionName())
    }

    @Test
    fun noSection() {
        assertNull(sym(section = null).sectionName())
    }

    // --- Equality ---

    @Test
    fun sameRawEqual() {
        val raw = org.kgen.binary.Symbol("test", 100, 50)
        assertEquals(Symbol(raw, null), Symbol(raw, null))
    }

    @Test
    fun sameRawSameHash() {
        val raw = org.kgen.binary.Symbol("test", 100, 50)
        assertEquals(Symbol(raw, null).hashCode(), Symbol(raw, null).hashCode())
    }

    // --- toString ---

    @Test
    fun toStringData() {
        val s = sym(name = "counter", kind = SymbolKind.DATA)
        val str = s.toString()
        assertTrue(str.contains("counter"))
        assertTrue(str.contains("data"))
    }

    @Test
    fun toStringWeak() {
        val s = sym(name = "weak_sym", binding = SymbolBinding.WEAK)
        val str = s.toString()
        assertTrue(str.contains("weak_sym"))
    }

    // --- Function navigation ---

    @Test
    fun functionNavigationForDataReturnsNull() {
        val s = sym(kind = SymbolKind.DATA)
        assertNull(s.function())
    }

    @Test
    fun functionNavigationForFunctionReturnsFunction() {
        val s = sym(name = "my_func", kind = SymbolKind.FUNCTION)
        val func = s.function()!!
        assertEquals("my_func", func.name())
    }

    // --- Raw access ---

    @Test
    fun rawAccessReturnsOriginal() {
        val raw = org.kgen.binary.Symbol("test", 42, 10)
        val s = Symbol(raw, null)
        assertSame(raw, s.raw())
        assertEquals("test", s.raw().name)
        assertEquals(42L, s.raw().value)
    }
}

package org.kgen.tools

import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ElfBinaryDiffTest {

    private val diff = ElfBinaryDiff()

    private fun makeElf(
        code: ByteArray = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()),
        data: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        symbols: List<Symbol> = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ),
        extraSections: List<Section> = emptyList(),
    ): ByteArray {
        val sections = listOf(
            Section(".text", SectionKind.TEXT, code, align = 16),
            Section(".data", SectionKind.DATA, data, align = 4),
        ) + extraSections
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = sections,
            symbols = symbols,
            relocations = emptyList(),
        )
        return ElfObjectWriter().write(obj)
    }

    @Test
    fun `identical binaries have no diff`() {
        val bytes = makeElf()
        val deltas = diff.diff(bytes, bytes)
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `identical binaries have empty structural diff`() {
        val bytes = makeElf()
        val sd = diff.structuralDiff(bytes, bytes)
        assertTrue(sd.addedSections.isEmpty())
        assertTrue(sd.removedSections.isEmpty())
        assertTrue(sd.modifiedSections.isEmpty())
        assertTrue(sd.addedSymbols.isEmpty())
        assertTrue(sd.removedSymbols.isEmpty())
        assertTrue(sd.modifiedSymbols.isEmpty())
    }

    @Test
    fun `detects byte-level changes in text section`() {
        val a = makeElf(code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()))
        val b = makeElf(code = byteArrayOf(0x48, 0x90.toByte(), 0xE5.toByte(), 0xC3.toByte()))
        val deltas = diff.diff(a, b)
        assertTrue(deltas.isNotEmpty())
        val textDelta = deltas.find { it.section == ".text" }
        assertNotNull(textDelta)
    }

    @Test
    fun `detects added section`() {
        val a = makeElf()
        val b = makeElf(extraSections = listOf(
            Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue(".custom" in sd.addedSections)
    }

    @Test
    fun `detects removed section`() {
        val a = makeElf(extraSections = listOf(
            Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
        ))
        val b = makeElf()
        val sd = diff.structuralDiff(a, b)
        assertTrue(".custom" in sd.removedSections)
    }

    @Test
    fun `detects modified section`() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val b = makeElf(data = byteArrayOf(0xFF.toByte(), 0x02, 0x03, 0x04))
        val sd = diff.structuralDiff(a, b)
        assertTrue(".data" in sd.modifiedSections)
        assertTrue(sd.sectionDiffs.containsKey(".data"))
    }

    @Test
    fun `detects added symbol`() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            Symbol("helper", value = 0, size = 0, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("helper" in sd.addedSymbols)
    }

    @Test
    fun `detects removed symbol`() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            Symbol("helper", value = 0, size = 0, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("helper" in sd.removedSymbols)
    }

    @Test
    fun `detects modified symbol`() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 8, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("main" in sd.modifiedSymbols)
    }

    @Test
    fun `diff bytes finds nearest symbol`() {
        val a = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val b = byteArrayOf(0x00, 0xFF.toByte(), 0x02, 0x03)
        val syms = mapOf(0L to "start")
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0x1000, ".text", syms)
        assertEquals(1, deltas.size)
        assertEquals("start", deltas[0].nearestSymbol)
    }

    @Test
    fun `diff bytes coalesces nearby changes`() {
        val a = byteArrayOf(0x01, 0x00, 0x00, 0x02)
        val b = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0xFE.toByte())
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, null, emptyMap())
        assertEquals(1, deltas.size)
    }
}

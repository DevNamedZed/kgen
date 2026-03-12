package org.kgen.binary.diff

import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ElfBinaryDiffExtendedTest {

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

    // -- diff() tests --

    @Test
    fun identicalBinariesNoDiff() {
        val bytes = makeElf()
        assertTrue(diff.diff(bytes, bytes).isEmpty())
    }

    @Test
    fun textSectionByteChange() {
        val a = makeElf(code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()))
        val b = makeElf(code = byteArrayOf(0x48, 0x90.toByte(), 0xE5.toByte(), 0xC3.toByte()))
        val deltas = diff.diff(a, b)
        assertTrue(deltas.isNotEmpty())
        assertTrue(deltas.any { it.section == ".text" })
    }

    @Test
    fun dataSectionByteChange() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val b = makeElf(data = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x04))
        val deltas = diff.diff(a, b)
        assertTrue(deltas.any { it.section == ".data" })
    }

    @Test
    fun noDiffWhenDataIdentical() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02))
        val b = makeElf(data = byteArrayOf(0x01, 0x02))
        val deltas = diff.diff(a, b)
        val dataDeltas = deltas.filter { it.section == ".data" }
        assertTrue(dataDeltas.isEmpty())
    }

    // -- structuralDiff() tests --

    @Test
    fun identicalStructuralDiffEmpty() {
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
    fun addedSectionDetected() {
        val a = makeElf()
        val b = makeElf(extraSections = listOf(
            Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue(".custom" in sd.addedSections)
    }

    @Test
    fun removedSectionDetected() {
        val a = makeElf(extraSections = listOf(
            Section(".custom", SectionKind.RODATA, byteArrayOf(0xAA.toByte()), align = 1)
        ))
        val b = makeElf()
        val sd = diff.structuralDiff(a, b)
        assertTrue(".custom" in sd.removedSections)
    }

    @Test
    fun modifiedSectionDetected() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02))
        val b = makeElf(data = byteArrayOf(0xFF.toByte(), 0x02))
        val sd = diff.structuralDiff(a, b)
        assertTrue(".data" in sd.modifiedSections)
    }

    @Test
    fun modifiedSectionHasDiffDetails() {
        val a = makeElf(data = byteArrayOf(0x01, 0x02))
        val b = makeElf(data = byteArrayOf(0xFF.toByte(), 0x02))
        val sd = diff.structuralDiff(a, b)
        assertTrue(sd.sectionDiffs.containsKey(".data"))
    }

    @Test
    fun addedSymbolDetected() {
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
    fun removedSymbolDetected() {
        val syms = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            Symbol("helper", value = 0, size = 0, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
        )
        val a = makeElf(symbols = syms)
        val b = makeElf(symbols = listOf(syms[0]))
        val sd = diff.structuralDiff(a, b)
        assertTrue("helper" in sd.removedSymbols)
    }

    @Test
    fun modifiedSymbolSizeChange() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 16, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("main" in sd.modifiedSymbols)
    }

    @Test
    fun modifiedSymbolBindingChange() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("main" in sd.modifiedSymbols)
    }

    @Test
    fun modifiedSymbolKindChange() {
        val a = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        ))
        val b = makeElf(symbols = listOf(
            Symbol("main", value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
        ))
        val sd = diff.structuralDiff(a, b)
        assertTrue("main" in sd.modifiedSymbols)
    }

    // -- diffBytes() static utility --

    @Test
    fun diffBytesIdentical() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val deltas = ElfBinaryDiff.diffBytes(data, data, 0, ".text", emptyMap())
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun diffBytesSingleChange() {
        val a = byteArrayOf(0x01, 0x02, 0x03)
        val b = byteArrayOf(0x01, 0xFF.toByte(), 0x03)
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0x1000, ".text", emptyMap())
        assertEquals(1, deltas.size)
        assertEquals(0x1001L, deltas[0].offset)
    }

    @Test
    fun diffBytesNearestSymbol() {
        val a = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val b = byteArrayOf(0x00, 0xFF.toByte(), 0x02, 0x03)
        val syms = mapOf(0L to "start", 2L to "middle")
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, ".text", syms)
        assertEquals(1, deltas.size)
        assertEquals("start", deltas[0].nearestSymbol)
    }

    @Test
    fun diffBytesCoalescesNearbyChanges() {
        val a = byteArrayOf(0x01, 0x00, 0x00, 0x02)
        val b = byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0xFE.toByte())
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, null, emptyMap())
        assertEquals(1, deltas.size)
    }

    @Test
    fun diffBytesSectionName() {
        val a = byteArrayOf(0x01)
        val b = byteArrayOf(0xFF.toByte())
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, ".data", emptyMap())
        assertEquals(".data", deltas[0].section)
    }

    @Test
    fun diffBytesNoSymbol() {
        val a = byteArrayOf(0x01)
        val b = byteArrayOf(0xFF.toByte())
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, ".text", emptyMap())
        assertNull(deltas[0].nearestSymbol)
    }

    @Test
    fun diffBytesDifferentLengths() {
        val a = byteArrayOf(0x01, 0x02)
        val b = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val deltas = ElfBinaryDiff.diffBytes(a, b, 0, ".text", emptyMap())
        assertTrue(deltas.isNotEmpty())
    }

    // -- BinaryDelta data class --

    @Test
    fun binaryDeltaEquality() {
        val a = BinaryDelta(0x100, byteArrayOf(0x01), byteArrayOf(0x02), ".text", "main")
        val b = BinaryDelta(0x100, byteArrayOf(0x01), byteArrayOf(0x02), ".text", "main")
        assertEquals(a, b)
    }

    @Test
    fun binaryDeltaInequalityOffset() {
        val a = BinaryDelta(0x100, byteArrayOf(0x01), byteArrayOf(0x02), ".text", null)
        val b = BinaryDelta(0x200, byteArrayOf(0x01), byteArrayOf(0x02), ".text", null)
        assertNotEquals(a, b)
    }

    // -- Multiple sections diff --

    @Test
    fun multiSectionChangesDetected() {
        val a = makeElf(
            code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()),
            data = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        )
        val b = makeElf(
            code = byteArrayOf(0x48, 0x90.toByte(), 0xE5.toByte(), 0xC3.toByte()),
            data = byteArrayOf(0x01, 0x02, 0xFF.toByte(), 0x04),
        )
        val deltas = diff.diff(a, b)
        val sections = deltas.mapNotNull { it.section }.toSet()
        assertTrue(sections.contains(".text"))
        assertTrue(sections.contains(".data"))
    }
}

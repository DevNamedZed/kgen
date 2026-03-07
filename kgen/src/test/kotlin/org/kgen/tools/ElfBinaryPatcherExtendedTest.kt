package org.kgen.tools

import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ElfBinaryPatcherExtendedTest {

    private val patcher = ElfBinaryPatcher()

    private fun makeElf(
        code: ByteArray = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte()),
        data: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        extraSections: List<Section> = emptyList(),
        extraSymbols: List<Symbol> = emptyList(),
    ): ByteArray {
        val sections = mutableListOf(
            Section(".text", SectionKind.TEXT, code, align = 16),
            Section(".data", SectionKind.DATA, data, align = 4),
        )
        sections.addAll(extraSections)
        val symbols = mutableListOf(
            Symbol("main", value = 0, size = code.size.toLong(), section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            Symbol("myvar", value = 0, size = data.size.toLong(), section = ".data",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
        )
        symbols.addAll(extraSymbols)
        return ElfObjectWriter().write(ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = sections, symbols = symbols, relocations = emptyList(),
        ))
    }

    // --- Section operations ---

    @Test
    fun readSectionReturnsCorrectSize() {
        val bin = patcher.load(makeElf())
        val text = bin.readSection(".text")!!
        assertEquals(4, text.size)
    }

    @Test
    fun readDataSection() {
        val bin = patcher.load(makeElf())
        val data = bin.readSection(".data")!!
        assertEquals(4, data.size)
        assertEquals(0x01.toByte(), data[0])
        assertEquals(0x04.toByte(), data[3])
    }

    @Test
    fun writeSectionChangesSize() {
        val bin = patcher.load(makeElf())
        val newCode = byteArrayOf(0x90.toByte(), 0x90.toByte())
        bin.writeSection(".text", newCode)
        val text = bin.readSection(".text")!!
        assertEquals(2, text.size)
    }

    @Test
    fun addMultipleSections() {
        val bin = patcher.load(makeElf())
        bin.addSection(".rodata", SectionKind.RODATA, byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F))
        bin.addSection(".bss", SectionKind.BSS, ByteArray(16))
        assertNotNull(bin.readSection(".rodata"))
        assertNotNull(bin.readSection(".bss"))
    }

    @Test
    fun removeAndReAddSection() {
        val bin = patcher.load(makeElf())
        bin.removeSection(".data")
        assertNull(bin.readSection(".data"))
        bin.addSection(".data", SectionKind.DATA, byteArrayOf(0xFF.toByte()))
        val data = bin.readSection(".data")!!
        assertEquals(1, data.size)
        assertEquals(0xFF.toByte(), data[0])
    }

    @Test
    fun renameSectionPreservesData() {
        val bin = patcher.load(makeElf())
        val origData = bin.readSection(".data")!!.clone()
        bin.renameSection(".data", ".mydata")
        val renamed = bin.readSection(".mydata")!!
        assertArrayEquals(origData, renamed)
    }

    @Test
    fun addSectionWithLargeData() {
        val bin = patcher.load(makeElf())
        val large = ByteArray(4096) { (it % 256).toByte() }
        bin.addSection(".big", SectionKind.DATA, large)
        val read = bin.readSection(".big")!!
        assertEquals(4096, read.size)
        assertArrayEquals(large, read)
    }

    // --- Symbol operations ---

    @Test
    fun findSymbolReturnsCorrectKind() {
        val bin = patcher.load(makeElf())
        assertEquals(SymbolKind.FUNCTION, bin.findSymbol("main")!!.kind)
        assertEquals(SymbolKind.DATA, bin.findSymbol("myvar")!!.kind)
    }

    @Test
    fun addMultipleSymbols() {
        val bin = patcher.load(makeElf())
        bin.addSymbol(Symbol("sym1", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
        bin.addSymbol(Symbol("sym2", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA))
        bin.addSymbol(Symbol("sym3", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION))
        assertNotNull(bin.findSymbol("sym1"))
        assertNotNull(bin.findSymbol("sym2"))
        assertNotNull(bin.findSymbol("sym3"))
    }

    @Test
    fun removeAndVerifySymbolGone() {
        val bin = patcher.load(makeElf())
        val origCount = bin.symbols().size
        bin.removeSymbol("myvar")
        assertEquals(origCount - 1, bin.symbols().size)
        assertNull(bin.findSymbol("myvar"))
        assertNotNull(bin.findSymbol("main"))
    }

    @Test
    fun renameSymbolPreservesProperties() {
        val bin = patcher.load(makeElf())
        val orig = bin.findSymbol("main")!!
        val origKind = orig.kind
        val origBinding = orig.binding
        bin.renameSymbol("main", "entry")
        val renamed = bin.findSymbol("entry")!!
        assertEquals(origKind, renamed.kind)
        assertEquals(origBinding, renamed.binding)
    }

    @Test
    fun setSymbolVisibilityHidden() {
        val bin = patcher.load(makeElf())
        bin.setSymbolVisibility("main", SymbolVisibility.HIDDEN)
        assertEquals(SymbolVisibility.HIDDEN, bin.findSymbol("main")!!.visibility)
    }

    @Test
    fun setSymbolVisibilityProtected() {
        val bin = patcher.load(makeElf())
        bin.setSymbolVisibility("main", SymbolVisibility.PROTECTED)
        assertEquals(SymbolVisibility.PROTECTED, bin.findSymbol("main")!!.visibility)
    }

    @Test
    fun symbolsCountCorrect() {
        val bin = patcher.load(makeElf())
        val count = bin.symbols().size
        assertTrue(count >= 2, "Should have at least main and myvar")
    }

    // --- Byte-level patching ---

    @Test
    fun readBytesAtOffset() {
        val bin = patcher.load(makeElf())
        val bytes = bin.readBytes(".text", 1, 2)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0xE5.toByte(), bytes[1])
    }

    @Test
    fun writeBytesAtMiddle() {
        val bin = patcher.load(makeElf())
        bin.writeBytes(".text", 1, byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val result = bin.readBytes(".text", 0, 4)
        assertEquals(0x48.toByte(), result[0])
        assertEquals(0xAA.toByte(), result[1])
        assertEquals(0xBB.toByte(), result[2])
        assertEquals(0xC3.toByte(), result[3])
    }

    @Test
    fun patchInstructionMultipleBytes() {
        val bin = patcher.load(makeElf())
        bin.patchInstruction(".text", 0, byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte()))
        val text = bin.readSection(".text")!!
        assertEquals(0x55.toByte(), text[0])
        assertEquals(0x48.toByte(), text[1])
    }

    @Test
    fun nopOutPartialRange() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val bin = patcher.load(makeElf(code = code))
        bin.nopOut(".text", 1, 3)
        val text = bin.readSection(".text")!!
        assertEquals(0x55.toByte(), text[0])
        assertEquals(0x90.toByte(), text[1])
        assertEquals(0x90.toByte(), text[2])
        assertEquals(0x90.toByte(), text[3])
        assertEquals(0x5D.toByte(), text[4])
        assertEquals(0xC3.toByte(), text[5])
    }

    // --- Entry point ---

    @Test
    fun entryPointSet() {
        val bin = patcher.load(makeElf())
        bin.setEntryPoint(0x400000)
        assertEquals(0x400000L, bin.entryPoint())
    }

    @Test
    fun entryPointDefaultNull() {
        val bin = patcher.load(makeElf())
        // Object files have entry point 0, which returns null
        assertNull(bin.entryPoint())
    }

    // --- Assemble round-trip ---

    @Test
    fun assembleRoundTripPreservesMultipleSections() {
        val bin = patcher.load(makeElf())
        bin.addSection(".rodata", SectionKind.RODATA, byteArrayOf(0x42))
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertNotNull(bin2.readSection(".text"))
        assertNotNull(bin2.readSection(".data"))
        assertNotNull(bin2.readSection(".rodata"))
    }

    @Test
    fun assembleRoundTripProducesValidElf() {
        val bin = patcher.load(makeElf())
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertEquals(ObjectFormat.ELF, bin2.format)
    }

    @Test
    fun assembleRoundTripAfterNop() {
        val bin = patcher.load(makeElf())
        bin.nopOut(".text", 0, 4)
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        val text = bin2.readSection(".text")!!
        for (b in text) assertEquals(0x90.toByte(), b)
    }

    @Test
    fun assembleAfterAddSymbol() {
        val bin = patcher.load(makeElf())
        bin.addSymbol(Symbol("new_func", value = 0, size = 0,
            binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertNotNull(bin2.findSymbol("new_func"))
    }

    @Test
    fun assembleAfterRemoveSymbol() {
        val bin = patcher.load(makeElf())
        bin.removeSymbol("myvar")
        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertNull(bin2.findSymbol("myvar"))
        assertNotNull(bin2.findSymbol("main"))
    }

    // --- Error cases ---

    @Test
    fun writeSectionNonexistentThrows() {
        val bin = patcher.load(makeElf())
        assertThrows(IllegalArgumentException::class.java) {
            bin.writeSection(".ghost", byteArrayOf(0))
        }
    }

    @Test
    fun removeSectionTwice() {
        val bin = patcher.load(makeElf())
        bin.removeSection(".data")
        // Removing again should not crash
        bin.removeSection(".data")
        assertNull(bin.readSection(".data"))
    }

    // --- Format detection ---

    @Test
    fun loadedBinaryIsElf() {
        val bin = patcher.load(makeElf())
        assertEquals(ObjectFormat.ELF, bin.format)
    }

    @Test
    fun loadedBinaryIsX86_64() {
        val bin = patcher.load(makeElf())
        assertEquals(ArchType.X86_64, bin.arch.arch)
    }

    // --- Large code section ---

    @Test
    fun largeCodeSection() {
        val code = ByteArray(8192) { 0x90.toByte() }
        val bin = patcher.load(makeElf(code = code))
        val text = bin.readSection(".text")!!
        assertEquals(8192, text.size)
    }

    // --- Multiple modifications before assemble ---

    @Test
    fun multipleModificationsBeforeAssemble() {
        val bin = patcher.load(makeElf())
        bin.writeSection(".text", byteArrayOf(0xCC.toByte(), 0xCC.toByte()))
        bin.writeSection(".data", byteArrayOf(0xFF.toByte()))
        bin.renameSymbol("main", "_start")
        bin.setSymbolVisibility("myvar", SymbolVisibility.HIDDEN)
        bin.addSection(".note", SectionKind.NOTE, byteArrayOf(0x01, 0x02))

        val assembled = bin.assemble()
        val bin2 = patcher.load(assembled)
        assertEquals(2, bin2.readSection(".text")!!.size)
        assertEquals(1, bin2.readSection(".data")!!.size)
        assertNotNull(bin2.findSymbol("_start"))
        assertNull(bin2.findSymbol("main"))
    }
}

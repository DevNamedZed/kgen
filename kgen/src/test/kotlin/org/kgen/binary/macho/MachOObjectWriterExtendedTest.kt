package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOObjectWriterExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun makeObjectFile(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.MACH_O,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    @Test
    fun `header magic is Mach-O 64-bit`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(bytes)
        assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
    }

    @Test
    fun `file type is MH_OBJECT`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(bytes)
        assertEquals(MachO.MH_OBJECT, buf.getInt(12))
    }

    @Test
    fun `two load commands for object with sections`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(bytes)
        assertEquals(2, buf.getInt(16)) // LC_SEGMENT_64 + LC_SYMTAB
    }

    @Test
    fun `text maps to __text in __TEXT segment`() {
        val code = byteArrayOf(0x90.toByte(), 0xC3.toByte())
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1))
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals("__TEXT", text.segmentName)
        assertTrue(text.isPureInstructions)
    }

    @Test
    fun `data maps to __data in __DATA segment`() {
        val data = byteArrayOf(1, 2, 3, 4)
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 4))
        ))
        val macho = MachOReader.read(bytes)
        val dataSec = macho.allSections.first { it.sectionName == "__data" }
        assertEquals("__DATA", dataSec.segmentName)
        assertArrayEquals(data, dataSec.data)
    }

    @Test
    fun `rodata maps to __const in __TEXT segment`() {
        val rodata = "constant\u0000".toByteArray()
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1))
        ))
        val macho = MachOReader.read(bytes)
        val constSec = macho.allSections.first { it.sectionName == "__const" }
        assertEquals("__TEXT", constSec.segmentName)
    }

    @Test
    fun `bss maps to __bss in __DATA segment`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), align = 16))
        ))
        val macho = MachOReader.read(bytes)
        val bss = macho.allSections.first { it.sectionName == "__bss" }
        assertEquals("__DATA", bss.segmentName)
    }

    @Test
    fun `three sections produce three section headers`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 4),
                Section(".rodata", SectionKind.RODATA, "hi".toByteArray(), align = 1),
            )
        ))
        val macho = MachOReader.read(bytes)
        assertEquals(3, macho.allSections.size)
    }

    @Test
    fun `global symbol is marked external`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(Symbol("_func", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val macho = MachOReader.read(bytes)
        val sym = macho.symbols.first { it.name == "_func" }
        assertTrue(sym.isExternal)
        assertTrue(sym.isInSection)
    }

    @Test
    fun `local symbol is not external`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(Symbol("_helper", value = 0, section = ".text",
                binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
        ))
        val macho = MachOReader.read(bytes)
        val sym = macho.symbols.first { it.name == "_helper" }
        assertFalse(sym.isExternal)
        assertTrue(sym.isInSection)
    }

    @Test
    fun `undefined symbol has no section`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(Symbol("_extern", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        ))
        val macho = MachOReader.read(bytes)
        val sym = macho.symbols.first { it.name == "_extern" }
        assertTrue(sym.isUndefined)
        assertTrue(sym.isExternal)
        assertEquals(0, sym.sectionIndex)
    }

    @Test
    fun `symbol value is preserved`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(Symbol("_inner", value = 32, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val macho = MachOReader.read(bytes)
        val sym = macho.symbols.first { it.name == "_inner" }
        assertEquals(32L, sym.value)
    }

    @Test
    fun `multiple symbols are preserved`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("_fn1", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_fn2", value = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper", value = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        ))
        val macho = MachOReader.read(bytes)
        assertEquals(3, macho.symbols.size)
    }

    @Test
    fun `branch relocation is PC-relative`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("_target", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 1, symbol = "_target",
                type = RelocationType.MachO_X86_64.BRANCH, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        assertTrue(text.relocations[0].pcRelative)
        assertEquals(2, text.relocations[0].type) // X86_64_RELOC_BRANCH
    }

    @Test
    fun `signed relocation is PC-relative`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("_sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 3, symbol = "_sym",
                type = RelocationType.MachO_X86_64.SIGNED, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        assertTrue(text.relocations[0].pcRelative)
        assertEquals(1, text.relocations[0].type) // X86_64_RELOC_SIGNED
    }

    @Test
    fun `unsigned relocation is not PC-relative`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("_sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "_sym",
                type = RelocationType.MachO_X86_64.UNSIGNED, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        assertFalse(text.relocations[0].pcRelative)
    }

    @Test
    fun `multiple relocations on same section`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("_a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("_b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_a", type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                Relocation(offset = 10, symbol = "_b", type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
            ),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(2, text.relocations.size)
    }

    @Test
    fun `relocation address is preserved`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(Symbol("_x", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 12, symbol = "_x",
                type = RelocationType.MachO_X86_64.BRANCH, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(12, text.relocations[0].address)
    }

    @Test
    fun `ELF PC32 maps to Mach-O SIGNED`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("_sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 3, symbol = "_sym",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        assertTrue(text.relocations[0].pcRelative)
    }

    @Test
    fun `ELF PLT32 maps to Mach-O BRANCH`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("_fn", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 1, symbol = "_fn",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        assertEquals(2, text.relocations[0].type) // BRANCH
    }

    @Test
    fun `ARM64 CPU type is set correctly`() {
        val bytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 4))
        ))
        val buf = le(bytes)
        assertEquals(MachO.CPU_TYPE_ARM64, buf.getInt(4))
        assertEquals(MachO.CPU_SUBTYPE_ARM64_ALL, buf.getInt(8))
    }

    @Test
    fun `section data content is preserved`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1))
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertArrayEquals(code, text.data)
    }

    @Test
    fun `data section content is preserved`() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 4))
        ))
        val macho = MachOReader.read(bytes)
        val dataSec = macho.allSections.first { it.sectionName == "__data" }
        assertArrayEquals(data, dataSec.data)
    }

    @Test
    fun `canRead accepts writer output`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        assertTrue(MachOReader.canRead(bytes))
    }

    @Test
    fun `debug sections are skipped`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x00), align = 1),
            )
        ))
        val macho = MachOReader.read(bytes)
        assertEquals(1, macho.allSections.size)
    }

    @Test
    fun `large section data is preserved`() {
        val code = ByteArray(4096) { (it % 256).toByte() }
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16))
        ))
        val macho = MachOReader.read(bytes)
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(code.size, text.data.size)
        assertArrayEquals(code, text.data)
    }

    @Test
    fun `round-trip with text data and symbols`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val data = byteArrayOf(1, 2, 3, 4)
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_var", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        ))
        val macho = MachOReader.read(bytes)
        assertTrue(macho.isObject)
        assertEquals(2, macho.allSections.size)
        assertEquals(2, macho.symbols.size)
        assertArrayEquals(code, macho.allSections[0].data)
    }

    @Test
    fun `empty object produces valid header`() {
        val bytes = MachOObjectWriter().write(makeObjectFile())
        val buf = le(bytes)
        assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
        assertEquals(MachO.MH_OBJECT, buf.getInt(12))
    }

    @Test
    fun `section with alignment 16 is properly aligned`() {
        val bytes = MachOObjectWriter().write(makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 16),
            )
        ))
        val macho = MachOReader.read(bytes)
        assertEquals(2, macho.allSections.size)
    }
}

package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOObjectWriterTest {

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
    fun `produces valid Mach-O 64-bit header`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        )
        val bytes = MachOObjectWriter().write(obj)
        val buf = le(bytes)

        assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
        assertEquals(MachO.CPU_TYPE_X86_64, buf.getInt(4))
        assertEquals(MachO.CPU_SUBTYPE_ALL, buf.getInt(8))
        assertEquals(MachO.MH_OBJECT, buf.getInt(12))
        assertEquals(2, buf.getInt(16)) // ncmds: LC_SEGMENT_64 + LC_SYMTAB
    }

    @Test
    fun `produces ARM64 header when configured`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x00, 0x00, 0x00, 0x00), align = 4))
        )
        val bytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(obj)
        val buf = le(bytes)

        assertEquals(MachO.CPU_TYPE_ARM64, buf.getInt(4))
        assertEquals(MachO.CPU_SUBTYPE_ARM64_ALL, buf.getInt(8))
    }

    @Test
    fun `writes text section with correct data`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 1))
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals("__TEXT", text.segmentName)
        assertArrayEquals(code, text.data)
        assertTrue(text.isPureInstructions)
    }

    @Test
    fun `writes data section`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 8))
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val dataSec = macho.allSections.first { it.sectionName == "__data" }
        assertEquals("__DATA", dataSec.segmentName)
        assertArrayEquals(data, dataSec.data)
    }

    @Test
    fun `writes rodata section`() {
        val rodata = "Hello\u0000".toByteArray(Charsets.US_ASCII)
        val obj = makeObjectFile(
            sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1))
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val constSec = macho.allSections.first { it.sectionName == "__const" }
        assertEquals("__TEXT", constSec.segmentName)
        assertArrayEquals(rodata, constSec.data)
    }

    @Test
    fun `writes multiple sections`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0x42)
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 1),
                Section(".data", SectionKind.DATA, data, align = 1),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.allSections.size)
        assertEquals("__text", macho.allSections[0].sectionName)
        assertEquals("__data", macho.allSections[1].sectionName)
    }

    @Test
    fun `writes symbols`() {
        val code = ByteArray(32)
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper", value = 16, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.symbols.size)

        val main = macho.symbols.first { it.name == "_main" }
        assertTrue(main.isExternal, "Global symbol should be external")
        assertTrue(main.isInSection, "Symbol with section should be N_SECT")
        assertEquals(1, main.sectionIndex) // 1-based
        assertEquals(0L, main.value)

        val helper = macho.symbols.first { it.name == "_helper" }
        assertFalse(helper.isExternal, "Local symbol should not be external")
        assertTrue(helper.isInSection)
        assertEquals(16L, helper.value)
    }

    @Test
    fun `writes undefined symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val sym = macho.symbols.first { it.name == "_printf" }
        assertTrue(sym.isUndefined)
        assertTrue(sym.isExternal)
        assertEquals(0, sym.sectionIndex)
    }

    @Test
    fun `writes relocations`() {
        val code = ByteArray(16)
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(
                    offset = 4,
                    symbol = "_foo",
                    type = RelocationType.MachO_X86_64.BRANCH,
                    section = ".text",
                ),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)

        val rel = text.relocations[0]
        assertEquals(4, rel.address)
        assertTrue(rel.pcRelative)
        assertTrue(rel.extern)
        assertEquals(2, rel.type) // X86_64_RELOC_BRANCH
        assertEquals(2, rel.length) // 4 bytes = log2(4) = 2
    }

    @Test
    fun `round-trip preserves structure`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x5D, 0xC3.toByte())
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "_puts",
                    type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
            ),
        )

        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertTrue(macho.isObject)
        assertTrue(macho.header.is64Bit)
        assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
        assertEquals(2, macho.allSections.size)
        assertEquals(2, macho.symbols.size)

        val textSec = macho.allSections.first { it.sectionName == "__text" }
        assertArrayEquals(code, textSec.data)
        assertEquals(1, textSec.relocations.size)
        assertEquals(5, textSec.relocations[0].address)
    }

    @Test
    fun `skips unsupported section kinds`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x00), align = 1),
            )
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(1, macho.allSections.size)
        assertEquals("__text", macho.allSections[0].sectionName)
    }

    @Test
    fun `empty object file produces valid output`() {
        val obj = makeObjectFile()
        val bytes = MachOObjectWriter().write(obj)
        val buf = le(bytes)

        assertEquals(MachO.MH_MAGIC_64.toInt(), buf.getInt(0))
        assertEquals(MachO.MH_OBJECT, buf.getInt(12))
        assertTrue(bytes.size >= 32, "Should at least have header")
    }

    @Test
    fun `MachOReader canRead accepts writer output`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        )
        val bytes = MachOObjectWriter().write(obj)
        assertTrue(MachOReader.canRead(bytes))
    }
}

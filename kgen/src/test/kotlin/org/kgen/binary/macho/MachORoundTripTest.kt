package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MachORoundTripTest {

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
    fun `round-trip preserves text section data exactly`() {
        val code = byteArrayOf(
            0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0x48, 0x83.toByte(), 0xEC.toByte(), 0x10,
            0xC3.toByte(),
        )
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertArrayEquals(code, text.data)
    }

    @Test
    fun `round-trip preserves data section data exactly`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 8)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val dataSec = macho.allSections.first { it.sectionName == "__data" }
        assertArrayEquals(data, dataSec.data)
    }

    @Test
    fun `round-trip preserves rodata as const in TEXT segment`() {
        val rodata = "Constant string\u0000".toByteArray(Charsets.US_ASCII)
        val obj = makeObjectFile(
            sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val constSec = macho.allSections.first { it.sectionName == "__const" }
        assertEquals("__TEXT", constSec.segmentName)
        assertArrayEquals(rodata, constSec.data)
    }

    @Test
    fun `round-trip preserves symbol names and values`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("_func1", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_func2", value = 32, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.symbols.size)
        val f1 = macho.symbols.first { it.name == "_func1" }
        val f2 = macho.symbols.first { it.name == "_func2" }
        assertEquals(0L, f1.value)
        assertEquals(32L, f2.value)
    }

    @Test
    fun `round-trip global symbol is external`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_globalFn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val sym = macho.symbols.first { it.name == "_globalFn" }
        assertTrue(sym.isExternal)
        assertTrue(sym.isInSection)
    }

    @Test
    fun `round-trip local symbol is not external`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_localFn", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val sym = macho.symbols.first { it.name == "_localFn" }
        assertFalse(sym.isExternal)
        assertTrue(sym.isInSection)
    }

    @Test
    fun `round-trip undefined symbol`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("_puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val sym = macho.symbols.first { it.name == "_puts" }
        assertTrue(sym.isUndefined)
        assertTrue(sym.isExternal)
        assertEquals(0, sym.sectionIndex)
    }

    @Test
    fun `round-trip relocations`() {
        val code = ByteArray(16)
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(
                    offset = 8, symbol = "_bar",
                    type = RelocationType.MachO_X86_64.BRANCH, section = ".text",
                ),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.relocations.size)
        val rel = text.relocations[0]
        assertEquals(8, rel.address)
        assertTrue(rel.pcRelative)
        assertTrue(rel.extern)
    }

    @Test
    fun `round-trip multiple relocations`() {
        val code = ByteArray(32)
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("_bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 4, symbol = "_foo",
                    type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
                Relocation(offset = 12, symbol = "_bar",
                    type = RelocationType.MachO_X86_64.BRANCH, section = ".text"),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(2, text.relocations.size)
    }

    @Test
    fun `round-trip text section has pure instructions attribute`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertTrue(text.isPureInstructions)
    }

    @Test
    fun `round-trip data section does not have pure instructions`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val data = macho.allSections.first { it.sectionName == "__data" }
        assertFalse(data.isPureInstructions)
    }

    @Test
    fun `round-trip ARM64 object`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ret
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
        )
        val bytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(MachO.CPU_TYPE_ARM64, macho.header.cpuType)
        assertEquals(MachO.CPU_SUBTYPE_ARM64_ALL, macho.header.cpuSubtype)
        assertTrue(macho.isObject)
    }

    @Test
    fun `round-trip ObjectFile projection preserves format`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)
        val projected = MachOReader.toObjectFile(macho)

        assertEquals(ObjectFormat.MACH_O, projected.format)
        assertEquals(ArchType.X86_64, projected.arch.arch)
        assertTrue(ObjectFlag.RELOCATABLE in projected.metadata.flags)
    }

    @Test
    fun `round-trip ObjectFile projection preserves symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("_main", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)
        val projected = MachOReader.toObjectFile(macho)

        assertTrue(projected.symbols.any { it.name == "_main" })
    }

    @Test
    fun `round-trip ObjectFile projection section kinds`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)
        val projected = MachOReader.toObjectFile(macho)

        val textSec = projected.sections.first { it.name == "__text" }
        assertEquals(SectionKind.TEXT, textSec.kind)
        val dataSec = projected.sections.first { it.name == "__data" }
        assertEquals(SectionKind.DATA, dataSec.kind)
    }

    @Test
    fun `round-trip with large section`() {
        val bigCode = ByteArray(8192) { (it % 256).toByte() }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, bigCode, align = 16)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        val text = macho.allSections.first { it.sectionName == "__text" }
        assertArrayEquals(bigCode, text.data)
    }

    @Test
    fun `round-trip with many symbols`() {
        val symbols = (0 until 50).map { i ->
            Symbol("_sym_$i", value = i.toLong() * 4, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(200), align = 16)),
            symbols = symbols,
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertEquals(50, macho.symbols.size)
        for (i in 0 until 50) {
            assertNotNull(
                macho.symbols.firstOrNull { it.name == "_sym_$i" },
                "Missing symbol _sym_$i"
            )
        }
    }

    @Test
    fun `MachOObjectFileReader implements interface correctly`() {
        val reader = MachOObjectFileReader()
        assertEquals(ObjectFormat.MACH_O, reader.format)

        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        assertTrue(reader.canRead(bytes))

        val result = reader.read(bytes)
        assertEquals(ObjectFormat.MACH_O, result.format)
    }

    @Test
    fun `MachOObjectFileReader rejects non-MachO`() {
        val reader = MachOObjectFileReader()
        assertFalse(reader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) // ELF magic
        assertFalse(reader.canRead(byteArrayOf(0x4d, 0x5a, 0x00, 0x00))) // PE/MZ magic
    }

    @Test
    fun `Mach-O file type is MH_OBJECT`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        assertTrue(macho.isObject)
        assertFalse(macho.isExecutable)
        assertFalse(macho.isDylib)
    }

    @Test
    fun `segment name conventions`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 1),
                Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
            ),
        )
        val bytes = MachOObjectWriter().write(obj)
        val macho = MachOReader.read(bytes)

        // Text goes in __TEXT, data goes in __DATA, rodata (__const) goes in __TEXT
        for (sec in macho.allSections) {
            when (sec.sectionName) {
                "__text" -> assertEquals("__TEXT", sec.segmentName)
                "__data" -> assertEquals("__DATA", sec.segmentName)
                "__const" -> assertEquals("__TEXT", sec.segmentName)
            }
        }
    }
}

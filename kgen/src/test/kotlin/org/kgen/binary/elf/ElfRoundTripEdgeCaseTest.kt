package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfRoundTripEdgeCaseTest {

    private val reader = ElfObjectFileReader()

    private fun makeObjectFile(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ) = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    @Test
    fun `round-trip with only empty sections`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(0), align = 1),
                Section(".data", SectionKind.DATA, ByteArray(0), align = 1),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val text = parsed.sections.first { it.name == ".text" }
        assertEquals(0, text.data.size)
        val data = parsed.sections.first { it.name == ".data" }
        assertEquals(0, data.data.size)
    }

    @Test
    fun `round-trip with single byte section`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val text = parsed.sections.first { it.name == ".text" }
        assertEquals(1, text.data.size)
        assertEquals(0x90.toByte(), text.data[0])
    }

    @Test
    fun `round-trip preserves large rodata`() {
        val bigRodata = ByteArray(16384) { (it % 251).toByte() } // prime pattern
        val obj = makeObjectFile(
            sections = listOf(Section(".rodata", SectionKind.RODATA, bigRodata, align = 16)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sec = parsed.sections.first { it.name == ".rodata" }
        assertArrayEquals(bigRodata, sec.data)
    }

    @Test
    fun `round-trip preserves symbol with zero size`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("label", value = 8, size = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sym = parsed.symbols.first { it.name == "label" }
        assertEquals(0L, sym.size)
        assertEquals(8L, sym.value)
    }

    @Test
    fun `round-trip preserves weak symbol binding`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("weak_default", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sym = parsed.symbols.first { it.name == "weak_default" }
        assertEquals(SymbolBinding.WEAK, sym.binding)
    }

    @Test
    fun `round-trip preserves data symbol kind`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(8), align = 8)),
            symbols = listOf(
                Symbol("global_var", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sym = parsed.symbols.first { it.name == "global_var" }
        assertEquals(SymbolKind.DATA, sym.kind)
    }

    @Test
    fun `round-trip with multiple relocation types`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("extern_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("data_sym", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "extern_fn",
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 12, symbol = "data_sym",
                    type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "data_sym",
                    type = RelocationType.X86_64.R_64, addend = 0, section = ".data"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(3, parsed.relocations.size)

        val plt32 = parsed.relocations.first { it.type == RelocationType.X86_64.PLT32 }
        assertEquals("extern_fn", plt32.symbol)
        assertEquals(5L, plt32.offset)
        assertEquals(-4L, plt32.addend)

        val pc32 = parsed.relocations.first { it.type == RelocationType.X86_64.PC32 }
        assertEquals("data_sym", pc32.symbol)

        val r64 = parsed.relocations.first { it.type == RelocationType.X86_64.R_64 }
        assertEquals("data_sym", r64.symbol)
        assertEquals(0L, r64.addend)
    }

    @Test
    fun `round-trip with all four section kinds`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".rodata", SectionKind.RODATA, "hello\u0000".toByteArray(), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42, 43), align = 8),
                Section(".bss", SectionKind.BSS, ByteArray(128), align = 16),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val text = parsed.sections.first { it.name == ".text" }
        assertEquals(SectionKind.TEXT, text.kind)
        assertTrue(SectionFlag.EXEC in text.flags)

        val rodata = parsed.sections.first { it.name == ".rodata" }
        assertEquals(SectionKind.RODATA, rodata.kind)

        val data = parsed.sections.first { it.name == ".data" }
        assertEquals(SectionKind.DATA, data.kind)
        assertTrue(SectionFlag.WRITE in data.flags)

        val bss = parsed.sections.first { it.name == ".bss" }
        assertEquals(SectionKind.BSS, bss.kind)
        assertEquals(128, bss.data.size)
    }

    @Test
    fun `round-trip mixed local and global and weak symbols`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("local_fn", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("global_fn", value = 16, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("weak_fn", value = 32, size = 16, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
                Symbol("extern_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val local = parsed.symbols.first { it.name == "local_fn" }
        assertEquals(SymbolBinding.LOCAL, local.binding)

        val global = parsed.symbols.first { it.name == "global_fn" }
        assertEquals(SymbolBinding.GLOBAL, global.binding)

        val weak = parsed.symbols.first { it.name == "weak_fn" }
        assertEquals(SymbolBinding.WEAK, weak.binding)

        val extern = parsed.symbols.first { it.name == "extern_fn" }
        assertEquals(SymbolKind.UNDEFINED, extern.kind)
        assertTrue(SymbolFlag.UNDEFINED in extern.flags)
    }

    @Test
    fun `round-trip large addend values`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("sym", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "sym",
                    type = RelocationType.X86_64.R_64, addend = 0x7FFFFFFF, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(0x7FFFFFFFL, parsed.relocations[0].addend)
    }

    @Test
    fun `round-trip large negative addend`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("sym", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "sym",
                    type = RelocationType.X86_64.PLT32, addend = -128, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(-128L, parsed.relocations[0].addend)
    }

    @Test
    fun `ElfReader rich model contains raw header info`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertEquals(ElfClass.ELF64, elf.header.elfClass)
        assertEquals(ElfData.LSB, elf.header.dataEncoding)
        assertEquals(ElfObjectType.REL, elf.header.type)
        assertEquals(ElfMachine.X86_64, elf.header.machine)
    }

    @Test
    fun `ElfReader sections contain raw header data`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val text = elf.sections.first { it.name == ".text" }
        assertEquals(ElfSectionType.PROGBITS, text.type)
        assertTrue(text.flags and ElfSectionFlags.EXECINSTR != 0L)
        assertTrue(text.flags and ElfSectionFlags.ALLOC != 0L)
    }

    @Test
    fun `ElfReader toObjectFile produces correct projection`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(
                Symbol("main", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)
        val projected = ElfReader.toObjectFile(elf)

        assertEquals(ObjectFormat.ELF, projected.format)
        assertEquals(ArchType.X86_64, projected.arch.arch)
        assertTrue(ObjectFlag.RELOCATABLE in projected.metadata.flags)
        assertTrue(projected.symbols.any { it.name == "main" })
    }

    @Test
    fun `round-trip with 100 symbols`() {
        val symbols = (0 until 100).map { i ->
            Symbol("sym_$i", value = i.toLong() * 8, size = 8, section = ".text",
                binding = if (i % 3 == 0) SymbolBinding.LOCAL else SymbolBinding.GLOBAL,
                kind = SymbolKind.FUNCTION)
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(800), align = 16)),
            symbols = symbols,
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        for (i in 0 until 100) {
            val sym = parsed.symbols.firstOrNull { it.name == "sym_$i" }
            assertNotNull(sym, "Missing symbol sym_$i")
            assertEquals(i.toLong() * 8, sym!!.value)
        }
    }

    @Test
    fun `round-trip with relocations on different sections`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("ext1", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("ext2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 4, symbol = "ext1",
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "ext2",
                    type = RelocationType.X86_64.R_64, addend = 0, section = ".data"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(2, parsed.relocations.size)
        val textRel = parsed.relocations.first { it.section == ".text" }
        assertEquals("ext1", textRel.symbol)
        val dataRel = parsed.relocations.first { it.section == ".data" }
        assertEquals("ext2", dataRel.symbol)
    }

    @Test
    fun `round-trip AARCH64 preserves architecture`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ret
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = ElfObjectWriter(ElfMachine.AARCH64.code).write(obj)
        val parsed = reader.read(bytes)

        assertEquals(ArchType.AARCH64, parsed.arch.arch)
    }

    @Test
    fun `symbols in different sections have correct section references`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
                Section(".rodata", SectionKind.RODATA, ByteArray(8), align = 1),
            ),
            symbols = listOf(
                Symbol("code_sym", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("data_sym", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                Symbol("ro_sym", value = 0, size = 4, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(".text", parsed.symbols.first { it.name == "code_sym" }.section)
        assertEquals(".data", parsed.symbols.first { it.name == "data_sym" }.section)
        assertEquals(".rodata", parsed.symbols.first { it.name == "ro_sym" }.section)
    }

    @Test
    fun `ElfObjectFileReader implements interface`() {
        val r = ElfObjectFileReader()
        assertEquals(ObjectFormat.ELF, r.format)

        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        assertTrue(r.canRead(bytes))

        val result = r.read(bytes)
        assertEquals(ObjectFormat.ELF, result.format)
    }

    @Test
    fun `ElfObjectFileReader rejects non-ELF`() {
        val r = ElfObjectFileReader()
        assertFalse(r.canRead(byteArrayOf(0x4d, 0x5a, 0x00, 0x00))) // MZ/PE
        assertFalse(r.canRead(byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte()))) // Mach-O
    }

    @Test
    fun `large BSS section preserves size without bloating file`() {
        val bssSize = 1024 * 1024 // 1MB BSS
        val obj = makeObjectFile(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(bssSize), align = 16)),
        )
        val bytes = ElfObjectWriter().write(obj)

        // The file should be much smaller than 1MB since BSS is NOBITS
        assertTrue(bytes.size < bssSize / 2, "BSS should not bloat file: file=${bytes.size}")

        val parsed = reader.read(bytes)
        val bss = parsed.sections.first { it.name == ".bss" }
        assertEquals(bssSize, bss.data.size)
    }
}

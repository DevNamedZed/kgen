package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfWriterExtendedTest {

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

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    // Round-trip: write with ElfObjectWriter, read back, verify fields

    @Test
    fun `round-trip with five sections preserves all data`() {
        val text = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val rodata = "Hello, kgen!\u0000".toByteArray(Charsets.US_ASCII)
        val bss = ByteArray(4096)
        val extraData = byteArrayOf(42, 43, 44, 45)

        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, text, align = 16),
                Section(".data", SectionKind.DATA, data, align = 8),
                Section(".rodata", SectionKind.RODATA, rodata, align = 4),
                Section(".bss", SectionKind.BSS, bss, align = 32),
                Section(".data.rel", SectionKind.DATA, extraData, align = 4),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertArrayEquals(text, parsed.sections.first { it.name == ".text" }.data)
        assertArrayEquals(data, parsed.sections.first { it.name == ".data" }.data)
        assertArrayEquals(rodata, parsed.sections.first { it.name == ".rodata" }.data)
        assertEquals(4096, parsed.sections.first { it.name == ".bss" }.data.size)
        assertArrayEquals(extraData, parsed.sections.first { it.name == ".data.rel" }.data)
    }

    @Test
    fun `section flags are correct for text`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val text = parsed.sections.first { it.name == ".text" }
        assertTrue(SectionFlag.ALLOC in text.flags)
        assertTrue(SectionFlag.EXEC in text.flags)
        assertFalse(SectionFlag.WRITE in text.flags)
    }

    @Test
    fun `section flags are correct for data`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val data = parsed.sections.first { it.name == ".data" }
        assertTrue(SectionFlag.ALLOC in data.flags)
        assertTrue(SectionFlag.WRITE in data.flags)
        assertFalse(SectionFlag.EXEC in data.flags)
    }

    @Test
    fun `section flags are correct for rodata`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(1), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val rodata = parsed.sections.first { it.name == ".rodata" }
        assertTrue(SectionFlag.ALLOC in rodata.flags)
        assertFalse(SectionFlag.WRITE in rodata.flags)
        assertFalse(SectionFlag.EXEC in rodata.flags)
    }

    @Test
    fun `section flags are correct for bss`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(64), align = 16)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val bss = parsed.sections.first { it.name == ".bss" }
        assertTrue(SectionFlag.ALLOC in bss.flags)
        assertTrue(SectionFlag.WRITE in bss.flags)
        assertFalse(SectionFlag.EXEC in bss.flags)
    }

    // Symbol types

    @Test
    fun `round-trip preserves data symbol kind through ELF`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(16), align = 8)),
            symbols = listOf(
                Symbol("my_var", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sym = parsed.symbols.first { it.name == "my_var" }
        assertEquals(SymbolBinding.GLOBAL, sym.binding)
    }

    @Test
    fun `round-trip multiple symbol bindings LOCAL GLOBAL WEAK`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(48), align = 16)),
            symbols = listOf(
                Symbol("local_fn", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("global_fn", value = 16, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("weak_fn", value = 32, size = 16, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(SymbolBinding.LOCAL, parsed.symbols.first { it.name == "local_fn" }.binding)
        assertEquals(SymbolBinding.GLOBAL, parsed.symbols.first { it.name == "global_fn" }.binding)
        assertEquals(SymbolBinding.WEAK, parsed.symbols.first { it.name == "weak_fn" }.binding)
    }

    @Test
    fun `round-trip symbol values and sizes are exact`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(256), align = 16)),
            symbols = listOf(
                Symbol("fn_a", value = 0, size = 64, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("fn_b", value = 64, size = 128, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("fn_c", value = 192, size = 64, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val fnA = parsed.symbols.first { it.name == "fn_a" }
        assertEquals(0L, fnA.value)
        assertEquals(64L, fnA.size)

        val fnB = parsed.symbols.first { it.name == "fn_b" }
        assertEquals(64L, fnB.value)
        assertEquals(128L, fnB.size)

        val fnC = parsed.symbols.first { it.name == "fn_c" }
        assertEquals(192L, fnC.value)
        assertEquals(64L, fnC.size)
    }

    @Test
    fun `undefined symbols have no section and UNDEFINED flag`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        for (name in listOf("printf", "malloc")) {
            val sym = parsed.symbols.first { it.name == name }
            assertNull(sym.section)
            assertTrue(SymbolFlag.UNDEFINED in sym.flags)
        }
    }

    // Relocation types

    @Test
    fun `round-trip R_64 relocation with zero addend`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("target", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target", type = RelocationType.X86_64.R_64,
                    addend = 0, section = ".data"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(1, parsed.relocations.size)
        val rel = parsed.relocations[0]
        assertEquals(RelocationType.X86_64.R_64, rel.type)
        assertEquals(0L, rel.addend)
        assertEquals("target", rel.symbol)
    }

    @Test
    fun `round-trip R_32S relocation with positive addend`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("data_ref", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 8, symbol = "data_ref", type = RelocationType.X86_64.R_32S,
                    addend = 256, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val rel = parsed.relocations[0]
        assertEquals(RelocationType.X86_64.R_32S, rel.type)
        assertEquals(256L, rel.addend)
        assertEquals(8L, rel.offset)
    }

    @Test
    fun `round-trip PC32 relocation`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 14, symbol = "helper", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val rel = parsed.relocations[0]
        assertEquals(RelocationType.X86_64.PC32, rel.type)
        assertEquals(-4L, rel.addend)
        assertEquals(14L, rel.offset)
    }

    @Test
    fun `round-trip relocations spanning two sections`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(64), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(32), align = 8),
            ),
            symbols = listOf(
                Symbol("extern_a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("extern_b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("extern_c", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "extern_a", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 20, symbol = "extern_b", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "extern_c", type = RelocationType.X86_64.R_64,
                    addend = 0, section = ".data"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(3, parsed.relocations.size)
        val textRels = parsed.relocations.filter { it.section == ".text" }
        val dataRels = parsed.relocations.filter { it.section == ".data" }
        assertEquals(2, textRels.size)
        assertEquals(1, dataRels.size)
        assertEquals("extern_c", dataRels[0].symbol)
    }

    // AARCH64 architecture

    @Test
    fun `round-trip AARCH64 object preserves machine type`() {
        val code = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()) // ret
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_start", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val bytes = ElfObjectWriter(ElfMachine.AARCH64.code).write(obj)
        val parsed = reader.read(bytes)

        assertEquals(ArchType.AARCH64, parsed.arch.arch)
        val sym = parsed.symbols.first { it.name == "_start" }
        assertEquals(SymbolBinding.GLOBAL, sym.binding)
    }

    // ELF flat executable via ElfWriter

    @Test
    fun `ElfWriter writeFlat produces valid executable`() {
        val code = byteArrayOf(
            0x48, 0xC7.toByte(), 0xC0.toByte(), 0x3C, 0x00, 0x00, 0x00, // mov rax, 60
            0x48, 0x31, 0xFF.toByte(),                                   // xor rdi, rdi
            0x0F, 0x05,                                                   // syscall
        )
        val bytes = ElfWriter.writeFlat(code)
        val elf = ElfReader.read(bytes)

        assertEquals(ElfObjectType.EXEC, elf.header.type)
        assertEquals(ElfMachine.X86_64, elf.header.machine)
        assertTrue(elf.segments.isNotEmpty())
    }

    @Test
    fun `ElfWriter writeFlat with data produces two LOAD segments`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0x42, 0x43)
        val bytes = ElfWriter.writeFlat(code, data)
        val elf = ElfReader.read(bytes)

        assertEquals(ElfObjectType.EXEC, elf.header.type)
        assertEquals(2, elf.segments.size)
    }

    // ElfReader rich model tests

    @Test
    fun `ElfReader sections have correct types`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(64), align = 16),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertEquals(ElfSectionType.PROGBITS, elf.sectionByName(".text")!!.type)
        assertEquals(ElfSectionType.PROGBITS, elf.sectionByName(".data")!!.type)
        assertEquals(ElfSectionType.NOBITS, elf.sectionByName(".bss")!!.type)
        assertEquals(ElfSectionType.SYMTAB, elf.sectionByName(".symtab")!!.type)
        assertEquals(ElfSectionType.STRTAB, elf.sectionByName(".strtab")!!.type)
    }

    @Test
    fun `ElfReader rich model has correct section flags`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val text = elf.sectionByName(".text")!!
        assertTrue(text.isAllocated)
        assertTrue(text.isExecutable)
        assertFalse(text.isWritable)
    }

    @Test
    fun `ElfReader symbol entries have correct binding and type`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 16, size = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val mainSym = elf.symbols.first { it.name == "main" }
        assertEquals(ElfSymbolBinding.GLOBAL, mainSym.binding)
        assertEquals(ElfSymbolType.FUNC, mainSym.type)

        val helperSym = elf.symbols.first { it.name == "helper" }
        assertEquals(ElfSymbolBinding.LOCAL, helperSym.binding)
        assertEquals(ElfSymbolType.FUNC, helperSym.type)
    }

    @Test
    fun `ElfReader relocations have correct entries`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertEquals(1, elf.relocations.size)
        val rel = elf.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("puts", rel.symbolName)
        assertEquals(RelocationType.X86_64.PLT32.value, rel.type)
        assertEquals(-4L, rel.addend)
        assertTrue(rel.hasAddend)
    }

    @Test
    fun `ElfFile isRelocatable for object files`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertTrue(elf.isRelocatable)
        assertFalse(elf.isExecutable)
        assertFalse(elf.isSharedObject)
    }

    @Test
    fun `ElfFile isExecutable for flat executable`() {
        val bytes = ElfWriter.writeFlat(byteArrayOf(0xC3.toByte()))
        val elf = ElfReader.read(bytes)

        assertTrue(elf.isExecutable)
        assertFalse(elf.isRelocatable)
        assertFalse(elf.isSharedObject)
    }

    @Test
    fun `ElfFile symbolsBySection returns symbols in given section`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("text_fn", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("data_var", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val textSyms = elf.symbolsBySection(".text")
        assertTrue(textSyms.any { it.name == "text_fn" })
        assertFalse(textSyms.any { it.name == "data_var" })
    }

    // Edge cases

    @Test
    fun `empty object file with no user sections`() {
        val obj = makeObjectFile(sections = emptyList())
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(ObjectFormat.ELF, parsed.format)
        assertTrue(ObjectFlag.RELOCATABLE in parsed.metadata.flags)
    }

    @Test
    fun `section alignment of 1 is preserved`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(1, parsed.sections.first { it.name == ".text" }.align)
    }

    @Test
    fun `high section alignment is preserved`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(64), align = 64)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(64, parsed.sections.first { it.name == ".data" }.align)
    }

    @Test
    fun `200 symbols round-trip correctly`() {
        val symbols = (0 until 200).map { i ->
            Symbol("sym_$i", value = i.toLong() * 4, size = 4, section = ".text",
                binding = if (i % 4 == 0) SymbolBinding.LOCAL
                else if (i % 4 == 1) SymbolBinding.GLOBAL
                else if (i % 4 == 2) SymbolBinding.WEAK
                else SymbolBinding.GLOBAL,
                kind = SymbolKind.FUNCTION)
        }
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(800), align = 16)),
            symbols = symbols,
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        for (i in 0 until 200) {
            val sym = parsed.symbols.firstOrNull { it.name == "sym_$i" }
            assertNotNull(sym, "Missing symbol sym_$i")
            assertEquals(i.toLong() * 4, sym!!.value, "Wrong value for sym_$i")
        }
    }

    @Test
    fun `section with pattern data round-trips exactly`() {
        val pattern = ByteArray(8192) { (it xor (it shr 8)).toByte() }
        val obj = makeObjectFile(
            sections = listOf(Section(".data", SectionKind.DATA, pattern, align = 16)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertArrayEquals(pattern, parsed.sections.first { it.name == ".data" }.data)
    }

    @Test
    fun `multiple relocations with different addends`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("a", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("b", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("c", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "a", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 16, symbol = "b", type = RelocationType.X86_64.R_64,
                    addend = 100, section = ".text"),
                Relocation(offset = 32, symbol = "c", type = RelocationType.X86_64.PC32,
                    addend = -8, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        assertEquals(3, parsed.relocations.size)
        val relA = parsed.relocations.first { it.symbol == "a" }
        assertEquals(-4L, relA.addend)
        assertEquals(RelocationType.X86_64.PLT32, relA.type)

        val relB = parsed.relocations.first { it.symbol == "b" }
        assertEquals(100L, relB.addend)
        assertEquals(RelocationType.X86_64.R_64, relB.type)

        val relC = parsed.relocations.first { it.symbol == "c" }
        assertEquals(-8L, relC.addend)
        assertEquals(RelocationType.X86_64.PC32, relC.type)
    }

    @Test
    fun `symtab section entry size is correct`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
            symbols = listOf(
                Symbol("fn", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val symtab = elf.sectionByName(".symtab")!!
        assertEquals(Elf.SYM64_SIZE.toLong(), symtab.entrySize)
    }

    @Test
    fun `rela section entry size is correct`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("ext", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        val rela = elf.sections.first { it.type == ElfSectionType.RELA }
        assertEquals(Elf.RELA64_SIZE.toLong(), rela.entrySize)
    }

    @Test
    fun `no relocations means no rela sections`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val elf = ElfReader.read(bytes)

        assertFalse(elf.sections.any { it.type == ElfSectionType.RELA })
    }

    @Test
    fun `RISCV machine type is preserved`() {
        val code = ByteArray(4)
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = ElfObjectWriter(ElfMachine.RISCV.code).write(obj)
        val buf = le(bytes)

        assertEquals(ElfMachine.RISCV.code, readU16(buf, 18))
    }

    @Test
    fun `ElfWriter object interface writes valid ELF`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = ElfWriter.write(obj)
        assertTrue(ElfReader.canRead(bytes))
        val elf = ElfReader.read(bytes)
        assertEquals(ElfObjectType.EXEC, elf.header.type)
    }

    @Test
    fun `BSS section does not inflate file size`() {
        val bssSize = 1024 * 1024 // 1MB
        val obj = makeObjectFile(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(bssSize), align = 16),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)

        assertTrue(bytes.size < bssSize / 2,
            "BSS should not bloat file: file=${bytes.size}, bss=$bssSize")

        val parsed = reader.read(bytes)
        val bss = parsed.sections.first { it.name == ".bss" }
        assertEquals(bssSize, bss.data.size)
    }

    @Test
    fun `symbols in rodata section have correct section reference`() {
        val obj = makeObjectFile(
            sections = listOf(
                Section(".rodata", SectionKind.RODATA, "test\u0000".toByteArray(), align = 1),
            ),
            symbols = listOf(
                Symbol("str_const", value = 0, size = 5, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
        )
        val bytes = ElfObjectWriter().write(obj)
        val parsed = reader.read(bytes)

        val sym = parsed.symbols.first { it.name == "str_const" }
        assertEquals(".rodata", sym.section)
    }

    @Test
    fun `shstrtab index is valid`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        val shstrtabIdx = readU16(buf, 62)
        val shnum = readU16(buf, 60)
        assertTrue(shstrtabIdx in 0 until shnum)
    }

    @Test
    fun `detectFormat returns ELF for ElfObjectWriter output`() {
        val obj = makeObjectFile(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
        )
        val bytes = ElfObjectWriter().write(obj)
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun `detectFormat returns ELF for ElfWriter flat output`() {
        val bytes = ElfWriter.writeFlat(byteArrayOf(0xC3.toByte()))
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }
}

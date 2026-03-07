package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfReaderExtendedTest {

    private val reader = ElfObjectFileReader()

    private fun makeObj(
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

    private fun writeAndRead(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ObjectFile {
        val bytes = ElfObjectWriter(machine).write(makeObj(sections, symbols, relocations, machine))
        return reader.read(bytes)
    }

    private fun writeAndReadElf(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ElfFile {
        val bytes = ElfObjectWriter(machine).write(makeObj(sections, symbols, relocations, machine))
        return ElfReader.read(bytes)
    }

    // Section type variety

    @Test
    fun `reads symtab section with correct type`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(Symbol("fn", value = 0, size = 1, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val symtab = elf.sections.first { it.type == ElfSectionType.SYMTAB }
        assertEquals(ElfSectionType.SYMTAB, symtab.type)
        assertTrue(symtab.entrySize > 0)
    }

    @Test
    fun `reads strtab section with correct type`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val strtabs = elf.sections.filter { it.type == ElfSectionType.STRTAB }
        assertTrue(strtabs.isNotEmpty())
    }

    @Test
    fun `reads rela section with correct entry size`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("ext", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = elf.sections.first { it.type == ElfSectionType.RELA }
        assertEquals(Elf.RELA64_SIZE.toLong(), rela.entrySize)
    }

    @Test
    fun `reads nobits section for bss`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(256), align = 16)),
        )
        val bss = elf.sections.first { it.name == ".bss" }
        assertEquals(ElfSectionType.NOBITS, bss.type)
        assertEquals(256L, bss.size)
    }

    // Symbol tables with mixed bindings

    @Test
    fun `reads all three binding types from symbol table`() {
        val obj = writeAndRead(
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
        assertEquals(SymbolBinding.LOCAL, obj.symbols.first { it.name == "local_fn" }.binding)
        assertEquals(SymbolBinding.GLOBAL, obj.symbols.first { it.name == "global_fn" }.binding)
        assertEquals(SymbolBinding.WEAK, obj.symbols.first { it.name == "weak_fn" }.binding)
    }

    @Test
    fun `reads global data symbol`() {
        val obj = writeAndRead(
            sections = listOf(Section(".data", SectionKind.DATA, ByteArray(8), align = 8)),
            symbols = listOf(Symbol("var1", value = 0, size = 8, section = ".data",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
        )
        val sym = obj.symbols.first { it.name == "var1" }
        assertEquals(SymbolBinding.GLOBAL, sym.binding)
        assertEquals(".data", sym.section)
    }

    @Test
    fun `reads symbols from multiple sections`() {
        val obj = writeAndRead(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
                Section(".rodata", SectionKind.RODATA, ByteArray(4), align = 1),
            ),
            symbols = listOf(
                Symbol("fn", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("var1", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                Symbol("const1", value = 0, size = 4, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
        )
        assertEquals(".text", obj.symbols.first { it.name == "fn" }.section)
        assertEquals(".data", obj.symbols.first { it.name == "var1" }.section)
        assertEquals(".rodata", obj.symbols.first { it.name == "const1" }.section)
    }

    @Test
    fun `reads multiple undefined symbols`() {
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("ext1", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("ext2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("ext3", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        for (name in listOf("ext1", "ext2", "ext3")) {
            val sym = obj.symbols.first { it.name == name }
            assertEquals(SymbolKind.UNDEFINED, sym.kind)
            assertTrue(SymbolFlag.UNDEFINED in sym.flags)
            assertNull(sym.section)
        }
    }

    // Relocation varieties

    @Test
    fun `reads R_32 relocation type`() {
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("abs_sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 4, symbol = "abs_sym",
                type = RelocationType.X86_64.R_32, addend = 0, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.R_32, obj.relocations[0].type)
        assertEquals(4L, obj.relocations[0].offset)
    }

    @Test
    fun `reads relocations with zero addend`() {
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "sym",
                type = RelocationType.X86_64.R_64, addend = 0, section = ".text")),
        )
        assertEquals(0L, obj.relocations[0].addend)
    }

    @Test
    fun `reads relocations targeting data section`() {
        val obj = writeAndRead(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(Symbol("fn_ptr", value = 0, size = 16, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = listOf(Relocation(offset = 0, symbol = "fn_ptr",
                type = RelocationType.X86_64.R_64, addend = 0, section = ".data")),
        )
        assertEquals(".data", obj.relocations[0].section)
        assertEquals("fn_ptr", obj.relocations[0].symbol)
    }

    @Test
    fun `reads many relocations in same section`() {
        val symbols = (0 until 10).map { i ->
            Symbol("sym_$i", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
        }
        val relocs = (0 until 10).map { i ->
            Relocation(offset = i.toLong() * 8, symbol = "sym_$i",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")
        }
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(128), align = 16)),
            symbols = symbols,
            relocations = relocs,
        )
        assertEquals(10, obj.relocations.size)
        for (i in 0 until 10) {
            assertEquals("sym_$i", obj.relocations[i].symbol)
            assertEquals(i.toLong() * 8, obj.relocations[i].offset)
        }
    }

    // String table

    @Test
    fun `reads long symbol names correctly`() {
        val longName = "very_long_function_name_that_tests_string_table_handling"
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol(longName, value = 0, size = 16, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertNotNull(obj.symbols.firstOrNull { it.name == longName })
    }

    @Test
    fun `reads symbols with special characters in names`() {
        val names = listOf("_ZN4main", ".L0", "__cxa_atexit")
        val symbols = names.map { name ->
            Symbol(name, value = 0, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = symbols,
        )
        for (name in names) {
            assertNotNull(obj.symbols.firstOrNull { it.name == name }, "Missing symbol: $name")
        }
    }

    // ELF header for different architectures

    @Test
    fun `reads RISCV architecture from header`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 4)),
            machine = ElfMachine.RISCV.code,
        )
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `header has correct ELF class and encoding`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        assertEquals(ElfClass.ELF64, elf.header.elfClass)
        assertEquals(ElfData.LSB, elf.header.dataEncoding)
    }

    @Test
    fun `header section count matches actual sections`() {
        val elf = writeAndReadElf(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
            ),
        )
        assertEquals(elf.header.sectionHeaderCount, elf.sections.size)
    }

    @Test
    fun `header shstrtab index is valid`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val idx = elf.header.sectionNameStringTableIndex
        assertTrue(idx > 0 && idx < elf.sections.size)
        assertEquals(ElfSectionType.STRTAB, elf.sections[idx].type)
    }

    // Section flags parsing

    @Test
    fun `text section has ALLOC and EXECINSTR flags`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val text = elf.sections.first { it.name == ".text" }
        assertTrue(text.flags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(text.flags and ElfSectionFlags.EXECINSTR != 0L)
        assertFalse(text.flags and ElfSectionFlags.WRITE != 0L)
    }

    @Test
    fun `data section has ALLOC and WRITE flags`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1), align = 1)),
        )
        val data = elf.sections.first { it.name == ".data" }
        assertTrue(data.flags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(data.flags and ElfSectionFlags.WRITE != 0L)
        assertFalse(data.flags and ElfSectionFlags.EXECINSTR != 0L)
    }

    @Test
    fun `rodata section has ALLOC but not WRITE or EXEC`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".rodata", SectionKind.RODATA, byteArrayOf(1), align = 1)),
        )
        val rodata = elf.sections.first { it.name == ".rodata" }
        assertTrue(rodata.flags and ElfSectionFlags.ALLOC != 0L)
        assertFalse(rodata.flags and ElfSectionFlags.WRITE != 0L)
        assertFalse(rodata.flags and ElfSectionFlags.EXECINSTR != 0L)
    }

    @Test
    fun `rela section has INFO_LINK flag`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("ext", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = elf.sections.first { it.type == ElfSectionType.RELA }
        assertTrue(rela.flags and ElfSectionFlags.INFO_LINK != 0L)
    }

    // Round-trip verification of all fields

    @Test
    fun `round-trip preserves section alignment values`() {
        val alignments = listOf(1, 4, 8, 16, 32)
        for (alignment in alignments) {
            val obj = writeAndRead(
                sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = alignment)),
            )
            val text = obj.sections.first { it.name == ".text" }
            assertEquals(alignment, text.align, "Alignment $alignment not preserved")
        }
    }

    @Test
    fun `round-trip preserves symbol value at various offsets`() {
        val offsets = listOf(0L, 8L, 128L, 512L)
        val symbols = offsets.mapIndexed { i, off ->
            Symbol("sym_$i", value = off, size = 4, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(1024), align = 16)),
            symbols = symbols,
        )
        for ((i, off) in offsets.withIndex()) {
            val sym = obj.symbols.first { it.name == "sym_$i" }
            assertEquals(off, sym.value, "Symbol sym_$i value not preserved")
        }
    }

    @Test
    fun `round-trip preserves relocation offsets and addends`() {
        val relocs = listOf(
            Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
            Relocation(offset = 16, symbol = "s", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
            Relocation(offset = 24, symbol = "s", type = RelocationType.X86_64.R_64, addend = 100, section = ".text"),
        )
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(Symbol("s", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = relocs,
        )
        assertEquals(3, obj.relocations.size)
        assertEquals(0L, obj.relocations[0].offset)
        assertEquals(16L, obj.relocations[1].offset)
        assertEquals(24L, obj.relocations[2].offset)
        assertEquals(-4L, obj.relocations[0].addend)
        assertEquals(100L, obj.relocations[2].addend)
    }

    // Edge cases

    @Test
    fun `handles object with no user sections`() {
        val bytes = ElfObjectWriter().write(makeObj())
        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.ELF, obj.format)
    }

    @Test
    fun `handles many sections`() {
        val sections = (0 until 20).map { i ->
            Section(".data.$i", SectionKind.DATA, byteArrayOf((i % 256).toByte()), align = 1)
        }
        val obj = writeAndRead(sections = sections)
        for (i in 0 until 20) {
            val sec = obj.sections.firstOrNull { it.name == ".data.$i" }
            assertNotNull(sec, "Missing section .data.$i")
            assertArrayEquals(byteArrayOf((i % 256).toByte()), sec!!.data)
        }
    }

    @Test
    fun `handles large symbol table with 200 symbols`() {
        val symbols = (0 until 200).map { i ->
            Symbol("func_$i", value = i.toLong() * 4, size = 4, section = ".text",
                binding = if (i % 2 == 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                kind = SymbolKind.FUNCTION)
        }
        val obj = writeAndRead(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(800), align = 16)),
            symbols = symbols,
        )
        for (i in 0 until 200) {
            assertNotNull(obj.symbols.firstOrNull { it.name == "func_$i" }, "Missing func_$i")
        }
    }

    @Test
    fun `reads section data pattern correctly for all byte values`() {
        val data = ByteArray(256) { it.toByte() }
        val obj = writeAndRead(
            sections = listOf(Section(".data", SectionKind.DATA, data, align = 1)),
        )
        val sec = obj.sections.first { it.name == ".data" }
        assertArrayEquals(data, sec.data)
    }

    @Test
    fun `ElfFile sectionByName finds sections`() {
        val elf = writeAndReadElf(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
            ),
        )
        assertNotNull(elf.sectionByName(".text"))
        assertNotNull(elf.sectionByName(".data"))
        assertNull(elf.sectionByName(".nonexistent"))
    }

    @Test
    fun `ElfFile symbolsBySection returns correct symbols`() {
        val elf = writeAndReadElf(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("fn1", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("fn2", value = 16, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("var1", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val textSyms = elf.symbolsBySection(".text")
        assertTrue(textSyms.any { it.name == "fn1" })
        assertTrue(textSyms.any { it.name == "fn2" })
        assertFalse(textSyms.any { it.name == "var1" })
    }

    @Test
    fun `ElfFile isRelocatable returns true for object file`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        assertTrue(elf.isRelocatable)
        assertFalse(elf.isExecutable)
        assertFalse(elf.isSharedObject)
    }

    @Test
    fun `ElfReader rich model symbol entries have correct fields`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("weak_fn", value = 16, size = 8, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
            ),
        )
        val main = elf.symbols.first { it.name == "main" }
        assertTrue(main.isGlobal)
        assertTrue(main.isFunction)
        assertFalse(main.isUndefined)

        val weak = elf.symbols.first { it.name == "weak_fn" }
        assertTrue(weak.isWeak)
    }

    @Test
    fun `ElfReader relocation entries have correct fields`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(Symbol("ext", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 5, symbol = "ext",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(1, elf.relocations.size)
        val rel = elf.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("ext", rel.symbolName)
        assertEquals(RelocationType.X86_64.PLT32.value, rel.type)
        assertEquals(-4L, rel.addend)
        assertTrue(rel.hasAddend)
        assertEquals(".text", rel.sectionName)
    }

    @Test
    fun `section convenience properties work correctly`() {
        val elf = writeAndReadElf(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
            ),
        )
        val text = elf.sections.first { it.name == ".text" }
        assertTrue(text.isAllocated)
        assertTrue(text.isExecutable)
        assertFalse(text.isWritable)

        val data = elf.sections.first { it.name == ".data" }
        assertTrue(data.isAllocated)
        assertTrue(data.isWritable)
        assertFalse(data.isExecutable)
    }

    @Test
    fun `null section at index 0 has correct properties`() {
        val elf = writeAndReadElf(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
        )
        val null0 = elf.sections[0]
        assertEquals(0, null0.index)
        assertEquals(ElfSectionType.NULL, null0.type)
        assertEquals(0L, null0.size)
    }

    @Test
    fun `round-trip with relocations on both text and data`() {
        val obj = writeAndRead(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("fn", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ext", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "fn", type = RelocationType.X86_64.R_64, addend = 0, section = ".data"),
            ),
        )
        assertEquals(2, obj.relocations.size)
        val textRel = obj.relocations.first { it.section == ".text" }
        assertEquals("ext", textRel.symbol)
        val dataRel = obj.relocations.first { it.section == ".data" }
        assertEquals("fn", dataRel.symbol)
    }
}

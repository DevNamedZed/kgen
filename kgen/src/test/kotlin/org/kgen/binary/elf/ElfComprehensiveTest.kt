package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfComprehensiveTest {

    private val writer = ElfObjectWriter()
    private val reader = ElfObjectFileReader()

    private fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun writeObj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ByteArray = ElfObjectWriter(machine).write(ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    ))

    private fun roundTrip(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ObjectFile {
        val bytes = writeObj(sections, symbols, relocations, machine)
        return reader.read(bytes)
    }

    private fun writeAndParseElf(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ElfFile {
        val bytes = writeObj(sections, symbols, relocations, machine)
        return ElfReader.read(bytes)
    }

    private fun textSection(size: Int = 16, align: Int = 16) =
        Section(".text", SectionKind.TEXT, ByteArray(size), align = align)

    private fun dataSection(size: Int = 8, align: Int = 8) =
        Section(".data", SectionKind.DATA, ByteArray(size), align = align)

    private fun rodataSection(content: String = "hello\u0000", align: Int = 1) =
        Section(".rodata", SectionKind.RODATA, content.toByteArray(Charsets.US_ASCII), align = align)

    private fun bssSection(size: Int = 256, align: Int = 16) =
        Section(".bss", SectionKind.BSS, ByteArray(size), align = align)

    // --- ELF Header: Magic bytes ---

    @Test
    fun `header magic byte 0 is 0x7f`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals(0x7f, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `header magic byte 1 is E`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals('E'.code, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `header magic byte 2 is L`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals('L'.code, bytes[2].toInt() and 0xFF)
    }

    @Test
    fun `header magic byte 3 is F`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals('F'.code, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun `header class is ELF64`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals(ElfClass.ELF64.code, bytes[4].toInt() and 0xFF)
    }

    @Test
    fun `header data encoding is LSB`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals(ElfData.LSB.code, bytes[5].toInt() and 0xFF)
    }

    @Test
    fun `header ELF version is 1`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals(Elf.VERSION, bytes[6].toInt() and 0xFF)
    }

    @Test
    fun `header type is REL for relocatable`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(ElfObjectType.REL.code, readU16(buf, 16))
    }

    @Test
    fun `header machine is X86_64`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))
    }

    @Test
    fun `header machine is AARCH64 when specified`() {
        val bytes = writeObj(sections = listOf(textSection()), machine = ElfMachine.AARCH64.code)
        val buf = le(bytes)
        assertEquals(ElfMachine.AARCH64.code, readU16(buf, 18))
    }

    @Test
    fun `header machine is RISCV when specified`() {
        val bytes = writeObj(sections = listOf(textSection()), machine = ElfMachine.RISCV.code)
        val buf = le(bytes)
        assertEquals(ElfMachine.RISCV.code, readU16(buf, 18))
    }

    @Test
    fun `header version field is 1`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(Elf.VERSION, readU32(buf, 20))
    }

    @Test
    fun `header entry point is zero for relocatable`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(0L, readU64(buf, 24))
    }

    @Test
    fun `header program header offset is zero for relocatable`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(0L, readU64(buf, 32))
    }

    @Test
    fun `header section header offset is nonzero`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertTrue(readU64(buf, 40) > 0)
    }

    @Test
    fun `header flags is zero for x86_64`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(0, readU32(buf, 48))
    }

    @Test
    fun `header ehdr size is 64`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(Elf.EHDR64_SIZE, readU16(buf, 52))
    }

    @Test
    fun `header shdr size is 64`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(Elf.SHDR64_SIZE, readU16(buf, 58))
    }

    @Test
    fun `header phdr count is zero for relocatable`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        assertEquals(0, readU16(buf, 56))
    }

    @Test
    fun `header shstrtab index is valid`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        val shnum = readU16(buf, 60)
        val shstrtabIdx = readU16(buf, 62)
        assertTrue(shstrtabIdx in 0 until shnum)
    }

    // --- ELF Header: parsed via ElfReader ---

    @Test
    fun `ElfReader parses elfClass as ELF64`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(ElfClass.ELF64, elf.header.elfClass)
    }

    @Test
    fun `ElfReader parses dataEncoding as LSB`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(ElfData.LSB, elf.header.dataEncoding)
    }

    @Test
    fun `ElfReader parses type as REL`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(ElfObjectType.REL, elf.header.type)
    }

    @Test
    fun `ElfReader parses machine as X86_64`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(ElfMachine.X86_64, elf.header.machine)
    }

    @Test
    fun `ElfReader parses machine as AARCH64`() {
        val elf = writeAndParseElf(sections = listOf(textSection()), machine = ElfMachine.AARCH64.code)
        assertEquals(ElfMachine.AARCH64, elf.header.machine)
    }

    @Test
    fun `ElfReader parses machine as RISCV`() {
        val elf = writeAndParseElf(sections = listOf(textSection()), machine = ElfMachine.RISCV.code)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `ElfReader parses entryPoint as zero for REL`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(0L, elf.header.entryPoint)
    }

    @Test
    fun `ElfReader parses sectionHeaderCount correctly`() {
        val elf = writeAndParseElf(sections = listOf(textSection(), dataSection()))
        assertTrue(elf.header.sectionHeaderCount > 0)
        assertEquals(elf.sections.size, elf.header.sectionHeaderCount)
    }

    @Test
    fun `ElfReader parses programHeaderCount as zero`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(0, elf.header.programHeaderCount)
    }

    @Test
    fun `ElfReader isRelocatable returns true`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertTrue(elf.isRelocatable)
        assertFalse(elf.isExecutable)
        assertFalse(elf.isSharedObject)
    }

    // --- Section header parsing ---

    @Test
    fun `first section header is SHT_NULL`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertEquals(ElfSectionType.NULL, elf.sections[0].type)
    }

    @Test
    fun `text section has type PROGBITS`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val text = elf.sectionByName(".text")!!
        assertEquals(ElfSectionType.PROGBITS, text.type)
    }

    @Test
    fun `text section has ALLOC and EXECINSTR flags`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val text = elf.sectionByName(".text")!!
        assertTrue(text.isAllocated)
        assertTrue(text.isExecutable)
        assertFalse(text.isWritable)
    }

    @Test
    fun `data section has ALLOC and WRITE flags`() {
        val elf = writeAndParseElf(sections = listOf(dataSection()))
        val data = elf.sectionByName(".data")!!
        assertTrue(data.isAllocated)
        assertTrue(data.isWritable)
        assertFalse(data.isExecutable)
    }

    @Test
    fun `rodata section has ALLOC but not WRITE or EXECINSTR`() {
        val elf = writeAndParseElf(sections = listOf(rodataSection()))
        val rodata = elf.sectionByName(".rodata")!!
        assertTrue(rodata.isAllocated)
        assertFalse(rodata.isWritable)
        assertFalse(rodata.isExecutable)
    }

    @Test
    fun `bss section has type NOBITS`() {
        val elf = writeAndParseElf(sections = listOf(bssSection()))
        val bss = elf.sectionByName(".bss")!!
        assertEquals(ElfSectionType.NOBITS, bss.type)
    }

    @Test
    fun `bss section has ALLOC and WRITE flags`() {
        val elf = writeAndParseElf(sections = listOf(bssSection()))
        val bss = elf.sectionByName(".bss")!!
        assertTrue(bss.isAllocated)
        assertTrue(bss.isWritable)
    }

    @Test
    fun `symtab section has type SYMTAB`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val symtab = elf.sectionByName(".symtab")!!
        assertEquals(ElfSectionType.SYMTAB, symtab.type)
    }

    @Test
    fun `symtab entrySize is SYM64_SIZE`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val symtab = elf.sectionByName(".symtab")!!
        assertEquals(Elf.SYM64_SIZE.toLong(), symtab.entrySize)
    }

    @Test
    fun `strtab section has type STRTAB`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val strtab = elf.sectionByName(".strtab")!!
        assertEquals(ElfSectionType.STRTAB, strtab.type)
    }

    @Test
    fun `shstrtab section has type STRTAB`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val shstrtab = elf.sectionByName(".shstrtab")!!
        assertEquals(ElfSectionType.STRTAB, shstrtab.type)
    }

    @Test
    fun `rela section has type RELA`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = elf.sectionByName(".rela.text")!!
        assertEquals(ElfSectionType.RELA, rela.type)
    }

    @Test
    fun `rela section entrySize is RELA64_SIZE`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = elf.sectionByName(".rela.text")!!
        assertEquals(Elf.RELA64_SIZE.toLong(), rela.entrySize)
    }

    @Test
    fun `section alignment is preserved for text`() {
        val elf = writeAndParseElf(sections = listOf(textSection(align = 32)))
        val text = elf.sectionByName(".text")!!
        assertEquals(32L, text.alignment)
    }

    @Test
    fun `section alignment is preserved for data`() {
        val elf = writeAndParseElf(sections = listOf(dataSection(align = 64)))
        val data = elf.sectionByName(".data")!!
        assertEquals(64L, data.alignment)
    }

    @Test
    fun `section size matches data length`() {
        val code = ByteArray(42)
        val elf = writeAndParseElf(sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)))
        val text = elf.sectionByName(".text")!!
        assertEquals(42L, text.size)
    }

    @Test
    fun `section data content is preserved`() {
        val code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val elf = writeAndParseElf(sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)))
        val text = elf.sectionByName(".text")!!
        assertArrayEquals(code, text.data)
    }

    // --- Section types: .text, .data, .rodata, .bss, .symtab, .strtab, .shstrtab, .rela ---

    @Test
    fun `all standard sections present in multi-section object`() {
        val elf = writeAndParseElf(sections = listOf(textSection(), dataSection(), rodataSection(), bssSection()))
        assertNotNull(elf.sectionByName(".text"))
        assertNotNull(elf.sectionByName(".data"))
        assertNotNull(elf.sectionByName(".rodata"))
        assertNotNull(elf.sectionByName(".bss"))
        assertNotNull(elf.sectionByName(".symtab"))
        assertNotNull(elf.sectionByName(".strtab"))
        assertNotNull(elf.sectionByName(".shstrtab"))
    }

    @Test
    fun `section count for single text section`() {
        val bytes = writeObj(sections = listOf(textSection()))
        val buf = le(bytes)
        val shnum = readU16(buf, 60)
        // null + .text + .symtab + .strtab + .shstrtab = 5
        assertEquals(5, shnum)
    }

    @Test
    fun `section count for text and data`() {
        val bytes = writeObj(sections = listOf(textSection(), dataSection()))
        val buf = le(bytes)
        val shnum = readU16(buf, 60)
        // null + .text + .data + .symtab + .strtab + .shstrtab = 6
        assertEquals(6, shnum)
    }

    @Test
    fun `section count with relocations`() {
        val bytes = writeObj(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val buf = le(bytes)
        val shnum = readU16(buf, 60)
        // null + .text + .rela.text + .symtab + .strtab + .shstrtab = 6
        assertEquals(6, shnum)
    }

    // --- Symbol table: local/global ordering, STB/STT/STV encoding ---

    @Test
    fun `locals come before globals in symtab`() {
        val bytes = writeObj(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(
                Symbol("global_fn", value = 0, size = 32, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("local_fn", value = 32, size = 16, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val buf = le(bytes)
        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)

        var symtabOff = -1
        var symtabSize = 0L
        var symtabInfo = 0
        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.SYMTAB.code) {
                symtabOff = readU64(buf, sh + 24).toInt()
                symtabSize = readU64(buf, sh + 32)
                symtabInfo = readU32(buf, sh + 44)
                break
            }
        }
        assertTrue(symtabOff > 0)
        val numSyms = (symtabSize / Elf.SYM64_SIZE).toInt()

        // All symbols before symtabInfo should be LOCAL
        for (s in 1 until symtabInfo) {
            val off = symtabOff + s * Elf.SYM64_SIZE
            val info = bytes[off + 4].toInt() and 0xFF
            val bind = info shr 4
            assertEquals(ElfSymbolBinding.LOCAL.code, bind, "Symbol $s should be LOCAL")
        }
        // All symbols from symtabInfo onward should be GLOBAL or WEAK
        for (s in symtabInfo until numSyms) {
            val off = symtabOff + s * Elf.SYM64_SIZE
            val info = bytes[off + 4].toInt() and 0xFF
            val bind = info shr 4
            assertTrue(bind == ElfSymbolBinding.GLOBAL.code || bind == ElfSymbolBinding.WEAK.code,
                "Symbol $s should be GLOBAL or WEAK, got $bind")
        }
    }

    @Test
    fun `symtab info field equals first global index`() {
        val bytes = writeObj(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(
                Symbol("g1", value = 0, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("l1", value = 16, size = 8, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("l2", value = 24, size = 8, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("g2", value = 32, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val buf = le(bytes)
        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)

        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.SYMTAB.code) {
                val info = readU32(buf, sh + 44)
                // null sym + 1 section sym + 2 local user syms = 4
                assertEquals(4, info)
                return
            }
        }
        fail<Nothing>("No .symtab found")
    }

    @Test
    fun `STT_FUNC encoded for function symbols`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("myfn", value = 0, size = 8, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "myfn" }
        assertEquals(ElfSymbolType.FUNC, sym.type)
    }

    @Test
    fun `STT_NOTYPE encoded for undefined symbols`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        )
        val sym = elf.symbols.first { it.name == "ext" }
        assertEquals(ElfSymbolType.NOTYPE, sym.type)
    }

    @Test
    fun `STB_GLOBAL encoded for global symbols`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("g", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "g" }
        assertEquals(ElfSymbolBinding.GLOBAL, sym.binding)
    }

    @Test
    fun `STB_LOCAL encoded for local symbols`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("l", value = 0, size = 4, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "l" }
        assertEquals(ElfSymbolBinding.LOCAL, sym.binding)
    }

    @Test
    fun `STB_WEAK encoded for weak symbols`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("w", value = 0, size = 4, section = ".text", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "w" }
        assertEquals(ElfSymbolBinding.WEAK, sym.binding)
    }

    @Test
    fun `STV_HIDDEN encoded correctly`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("h", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.HIDDEN)),
        )
        val sym = elf.symbols.first { it.name == "h" }
        assertEquals(ElfSymbolVisibility.HIDDEN, sym.visibility)
    }

    @Test
    fun `STV_PROTECTED encoded correctly`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("p", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.PROTECTED)),
        )
        val sym = elf.symbols.first { it.name == "p" }
        assertEquals(ElfSymbolVisibility.PROTECTED, sym.visibility)
    }

    @Test
    fun `STV_DEFAULT encoded correctly`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("d", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.DEFAULT)),
        )
        val sym = elf.symbols.first { it.name == "d" }
        assertEquals(ElfSymbolVisibility.DEFAULT, sym.visibility)
    }

    @Test
    fun `undefined symbol has SHN_UNDEF section index`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("undef", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        )
        val sym = elf.symbols.first { it.name == "undef" }
        assertTrue(sym.isUndefined)
        assertEquals(Elf.SHN_UNDEF, sym.sectionIndex)
    }

    @Test
    fun `symbol value is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 128)),
            symbols = listOf(Symbol("fn", value = 64, size = 32, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "fn" }
        assertEquals(64L, sym.value)
    }

    @Test
    fun `symbol size is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 128)),
            symbols = listOf(Symbol("fn", value = 0, size = 99, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "fn" }
        assertEquals(99L, sym.size)
    }

    @Test
    fun `symbol section name is resolved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(), dataSection()),
            symbols = listOf(Symbol("in_data", value = 0, size = 4, section = ".data", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val sym = elf.symbols.first { it.name == "in_data" }
        assertEquals(".data", sym.sectionName)
    }

    @Test
    fun `section symbols are generated for user sections`() {
        val elf = writeAndParseElf(sections = listOf(textSection(), dataSection()))
        val sectionSyms = elf.symbols.filter { it.type == ElfSymbolType.SECTION }
        assertTrue(sectionSyms.size >= 2)
    }

    // --- String table construction and lookup ---

    @Test
    fun `strtab starts with null byte`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("test", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val strtab = elf.sectionByName(".strtab")!!
        assertEquals(0, strtab.data[0].toInt())
    }

    @Test
    fun `shstrtab contains section names`() {
        val elf = writeAndParseElf(sections = listOf(textSection(), dataSection()))
        val shstrtab = elf.sectionByName(".shstrtab")!!
        val content = String(shstrtab.data, Charsets.US_ASCII)
        assertTrue(".text" in content)
        assertTrue(".data" in content)
        assertTrue(".symtab" in content)
        assertTrue(".strtab" in content)
        assertTrue(".shstrtab" in content)
    }

    @Test
    fun `strtab contains symbol names`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(
                Symbol("alpha", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("beta", value = 4, size = 4, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val strtab = elf.sectionByName(".strtab")!!
        val content = String(strtab.data, Charsets.US_ASCII)
        assertTrue("alpha" in content)
        assertTrue("beta" in content)
    }

    @Test
    fun `symbol names are correctly resolved from strtab`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(
                Symbol("foo_bar", value = 0, size = 8, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("baz_qux", value = 8, size = 8, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        assertNotNull(elf.symbols.firstOrNull { it.name == "foo_bar" })
        assertNotNull(elf.symbols.firstOrNull { it.name == "baz_qux" })
    }

    // --- Relocation entries ---

    @Test
    fun `R_X86_64_64 relocation type value`() {
        assertEquals(1, RelocationType.X86_64.R_64.value)
    }

    @Test
    fun `R_X86_64_PC32 relocation type value`() {
        assertEquals(2, RelocationType.X86_64.PC32.value)
    }

    @Test
    fun `R_X86_64_PLT32 relocation type value`() {
        assertEquals(4, RelocationType.X86_64.PLT32.value)
    }

    @Test
    fun `R_X86_64_32S relocation type value`() {
        assertEquals(11, RelocationType.X86_64.R_32S.value)
    }

    @Test
    fun `R_X86_64_GOTPCREL relocation type value`() {
        assertEquals(9, RelocationType.X86_64.GOTPCREL.value)
    }

    @Test
    fun `relocation offset is written correctly`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(Symbol("sym", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 42, symbol = "sym", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(42L, elf.relocations[0].offset)
    }

    @Test
    fun `relocation symbol name is resolved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("target_fn", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "target_fn", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals("target_fn", elf.relocations[0].symbolName)
    }

    @Test
    fun `relocation type PLT32 is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.PLT32.value, elf.relocations[0].type)
    }

    @Test
    fun `relocation type PC32 is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.PC32.value, elf.relocations[0].type)
    }

    @Test
    fun `relocation type R_64 is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.R_64, addend = 0, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.R_64.value, elf.relocations[0].type)
    }

    @Test
    fun `relocation type R_32S is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.R_32S, addend = 0, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.R_32S.value, elf.relocations[0].type)
    }

    @Test
    fun `relocation addend is preserved positive`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.R_32S, addend = 100, section = ".text")),
        )
        assertEquals(100L, elf.relocations[0].addend)
    }

    @Test
    fun `relocation addend is preserved negative`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(-4L, elf.relocations[0].addend)
    }

    @Test
    fun `relocation addend zero`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.R_64, addend = 0, section = ".text")),
        )
        assertEquals(0L, elf.relocations[0].addend)
    }

    @Test
    fun `relocation section name is preserved`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(".text", elf.relocations[0].sectionName)
    }

    @Test
    fun `relocation hasAddend is true for RELA`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertTrue(elf.relocations[0].hasAddend)
    }

    @Test
    fun `AArch64 relocation type CALL26`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.AArch64.CALL26, addend = 0, section = ".text")),
            machine = ElfMachine.AARCH64.code,
        )
        assertEquals(RelocationType.AArch64.CALL26.value, elf.relocations[0].type)
    }

    @Test
    fun `RiscV relocation type CALL_PLT`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.RiscV.CALL_PLT, addend = 0, section = ".text")),
            machine = ElfMachine.RISCV.code,
        )
        assertEquals(RelocationType.RiscV.CALL_PLT.value, elf.relocations[0].type)
    }

    // --- Round-trip: ObjectFile -> ElfObjectWriter -> ElfReader -> ObjectFile ---

    @Test
    fun `round-trip preserves format`() {
        val obj = roundTrip(sections = listOf(textSection()))
        assertEquals(ObjectFormat.ELF, obj.format)
    }

    @Test
    fun `round-trip preserves arch x86_64`() {
        val obj = roundTrip(sections = listOf(textSection()))
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun `round-trip preserves arch aarch64`() {
        val obj = roundTrip(sections = listOf(textSection()), machine = ElfMachine.AARCH64.code)
        assertEquals(ArchType.AARCH64, obj.arch.arch)
    }

    @Test
    fun `round-trip preserves arch riscv`() {
        val obj = roundTrip(sections = listOf(textSection()), machine = ElfMachine.RISCV.code)
        assertEquals(ArchType.RISCV64, obj.arch.arch)
    }

    @Test
    fun `round-trip preserves text section data`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val obj = roundTrip(sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)))
        val text = obj.sections.first { it.name == ".text" }
        assertArrayEquals(code, text.data)
    }

    @Test
    fun `round-trip preserves data section data`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val obj = roundTrip(sections = listOf(Section(".data", SectionKind.DATA, data, align = 8)))
        val sec = obj.sections.first { it.name == ".data" }
        assertArrayEquals(data, sec.data)
    }

    @Test
    fun `round-trip preserves rodata section data`() {
        val rodata = "test data\u0000".toByteArray(Charsets.US_ASCII)
        val obj = roundTrip(sections = listOf(Section(".rodata", SectionKind.RODATA, rodata, align = 1)))
        val sec = obj.sections.first { it.name == ".rodata" }
        assertArrayEquals(rodata, sec.data)
    }

    @Test
    fun `round-trip preserves bss section size`() {
        val obj = roundTrip(sections = listOf(bssSection(size = 4096)))
        val bss = obj.sections.first { it.name == ".bss" }
        assertEquals(4096, bss.data.size)
    }

    @Test
    fun `round-trip preserves section alignment`() {
        val obj = roundTrip(sections = listOf(textSection(align = 32)))
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(32, text.align)
    }

    @Test
    fun `round-trip preserves section kind text`() {
        val obj = roundTrip(sections = listOf(textSection()))
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(SectionKind.TEXT, text.kind)
    }

    @Test
    fun `round-trip preserves section kind data`() {
        val obj = roundTrip(sections = listOf(dataSection()))
        val sec = obj.sections.first { it.name == ".data" }
        assertEquals(SectionKind.DATA, sec.kind)
    }

    @Test
    fun `round-trip preserves section kind rodata`() {
        val obj = roundTrip(sections = listOf(rodataSection()))
        val sec = obj.sections.first { it.name == ".rodata" }
        assertEquals(SectionKind.RODATA, sec.kind)
    }

    @Test
    fun `round-trip preserves section kind bss`() {
        val obj = roundTrip(sections = listOf(bssSection()))
        val sec = obj.sections.first { it.name == ".bss" }
        assertEquals(SectionKind.BSS, sec.kind)
    }

    @Test
    fun `round-trip preserves symbol name`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("main", value = 0, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertNotNull(obj.symbols.firstOrNull { it.name == "main" })
    }

    @Test
    fun `round-trip preserves symbol value`() {
        val obj = roundTrip(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(Symbol("fn", value = 32, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(32L, obj.symbols.first { it.name == "fn" }.value)
    }

    @Test
    fun `round-trip preserves symbol size`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("fn", value = 0, size = 12, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(12L, obj.symbols.first { it.name == "fn" }.size)
    }

    @Test
    fun `round-trip preserves symbol binding global`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(SymbolBinding.GLOBAL, obj.symbols.first { it.name == "fn" }.binding)
    }

    @Test
    fun `round-trip preserves symbol binding local`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(SymbolBinding.LOCAL, obj.symbols.first { it.name == "fn" }.binding)
    }

    @Test
    fun `round-trip preserves symbol binding weak`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(SymbolBinding.WEAK, obj.symbols.first { it.name == "fn" }.binding)
    }

    @Test
    fun `round-trip preserves symbol kind function`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(SymbolKind.FUNCTION, obj.symbols.first { it.name == "fn" }.kind)
    }

    @Test
    fun `round-trip preserves symbol kind undefined`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        )
        assertEquals(SymbolKind.UNDEFINED, obj.symbols.first { it.name == "ext" }.kind)
    }

    @Test
    fun `round-trip preserves symbol section`() {
        val obj = roundTrip(
            sections = listOf(textSection(), dataSection()),
            symbols = listOf(Symbol("d", value = 0, size = 4, section = ".data", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertEquals(".data", obj.symbols.first { it.name == "d" }.section)
    }

    @Test
    fun `round-trip preserves undefined symbol null section`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        )
        assertNull(obj.symbols.first { it.name == "ext" }.section)
    }

    @Test
    fun `round-trip preserves symbol visibility hidden`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("h", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.HIDDEN)),
        )
        assertEquals(SymbolVisibility.HIDDEN, obj.symbols.first { it.name == "h" }.visibility)
    }

    @Test
    fun `round-trip preserves symbol visibility protected`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("p", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.PROTECTED)),
        )
        assertEquals(SymbolVisibility.PROTECTED, obj.symbols.first { it.name == "p" }.visibility)
    }

    @Test
    fun `round-trip preserves relocation offset`() {
        val obj = roundTrip(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 17, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(17L, obj.relocations[0].offset)
    }

    @Test
    fun `round-trip preserves relocation symbol`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("my_sym", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "my_sym", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals("my_sym", obj.relocations[0].symbol)
    }

    @Test
    fun `round-trip preserves relocation type PLT32`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.PLT32, obj.relocations[0].type)
    }

    @Test
    fun `round-trip preserves relocation type PC32`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.PC32, obj.relocations[0].type)
    }

    @Test
    fun `round-trip preserves relocation type R_64`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.R_64, addend = 0, section = ".text")),
        )
        assertEquals(RelocationType.X86_64.R_64, obj.relocations[0].type)
    }

    @Test
    fun `round-trip preserves relocation addend`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(-4L, obj.relocations[0].addend)
    }

    @Test
    fun `round-trip preserves relocation section`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("s", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "s", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        assertEquals(".text", obj.relocations[0].section)
    }

    @Test
    fun `round-trip preserves RELOCATABLE flag`() {
        val obj = roundTrip(sections = listOf(textSection()))
        assertTrue(ObjectFlag.RELOCATABLE in obj.metadata.flags)
    }

    // --- Multiple sections with different alignments ---

    @Test
    fun `multiple sections with varying alignment`() {
        val obj = roundTrip(sections = listOf(
            Section(".text", SectionKind.TEXT, ByteArray(100), align = 16),
            Section(".data", SectionKind.DATA, ByteArray(50), align = 8),
            Section(".rodata", SectionKind.RODATA, ByteArray(30), align = 4),
            Section(".bss", SectionKind.BSS, ByteArray(200), align = 32),
        ))
        assertEquals(16, obj.sections.first { it.name == ".text" }.align)
        assertEquals(8, obj.sections.first { it.name == ".data" }.align)
        assertEquals(4, obj.sections.first { it.name == ".rodata" }.align)
        assertEquals(32, obj.sections.first { it.name == ".bss" }.align)
    }

    @Test
    fun `alignment 1 is valid`() {
        val obj = roundTrip(sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 1)))
        assertTrue(obj.sections.first { it.name == ".text" }.align >= 1)
    }

    @Test
    fun `large alignment 4096`() {
        val obj = roundTrip(sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 4096)))
        assertEquals(4096, obj.sections.first { it.name == ".text" }.align)
    }

    // --- Empty sections ---

    @Test
    fun `empty text section round-trips`() {
        val obj = roundTrip(sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)))
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(0, text.data.size)
    }

    @Test
    fun `empty data section round-trips`() {
        val obj = roundTrip(sections = listOf(Section(".data", SectionKind.DATA, ByteArray(0), align = 1)))
        val sec = obj.sections.first { it.name == ".data" }
        assertEquals(0, sec.data.size)
    }

    @Test
    fun `empty rodata section round-trips`() {
        val obj = roundTrip(sections = listOf(Section(".rodata", SectionKind.RODATA, ByteArray(0), align = 1)))
        val sec = obj.sections.first { it.name == ".rodata" }
        assertEquals(0, sec.data.size)
    }

    // --- BSS sections ---

    @Test
    fun `bss section does not increase file size proportionally`() {
        val smallBytes = writeObj(sections = listOf(bssSection(size = 64)))
        val largeBytes = writeObj(sections = listOf(bssSection(size = 1048576)))
        // Large BSS should not be much bigger than small BSS in file
        assertTrue(largeBytes.size < smallBytes.size + 1024,
            "BSS should not store data in file: small=${smallBytes.size}, large=${largeBytes.size}")
    }

    @Test
    fun `bss section data is all zeros after round-trip`() {
        val obj = roundTrip(sections = listOf(bssSection(size = 512)))
        val bss = obj.sections.first { it.name == ".bss" }
        assertTrue(bss.data.all { it == 0.toByte() })
    }

    @Test
    fun `bss section NOBITS in raw binary`() {
        val bytes = writeObj(sections = listOf(bssSection()))
        val buf = le(bytes)
        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)
        var foundNobits = false
        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.NOBITS.code) {
                foundNobits = true
                break
            }
        }
        assertTrue(foundNobits)
    }

    // --- Debug sections ---

    @Test
    fun `debug_info section round-trips`() {
        val debugData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_info", SectionKind.DEBUG_INFO, debugData, align = 1),
        ))
        val sec = obj.sections.first { it.name == ".debug_info" }
        assertEquals(SectionKind.DEBUG_INFO, sec.kind)
        assertArrayEquals(debugData, sec.data)
    }

    @Test
    fun `debug_abbrev section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, byteArrayOf(0x01), align = 1),
        ))
        assertNotNull(obj.sections.firstOrNull { it.name == ".debug_abbrev" })
    }

    @Test
    fun `debug_line section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_line", SectionKind.DEBUG_LINE, byteArrayOf(0x01), align = 1),
        ))
        val sec = obj.sections.first { it.name == ".debug_line" }
        assertEquals(SectionKind.DEBUG_LINE, sec.kind)
    }

    @Test
    fun `debug_str section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_str", SectionKind.DEBUG_STR, "hello\u0000".toByteArray(), align = 1),
        ))
        val sec = obj.sections.first { it.name == ".debug_str" }
        assertEquals(SectionKind.DEBUG_STR, sec.kind)
    }

    @Test
    fun `debug_ranges section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_ranges", SectionKind.DEBUG_RANGES, byteArrayOf(0x00), align = 1),
        ))
        assertNotNull(obj.sections.firstOrNull { it.name == ".debug_ranges" })
    }

    @Test
    fun `debug_loc section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_loc", SectionKind.DEBUG_LOC, byteArrayOf(0x00), align = 1),
        ))
        assertNotNull(obj.sections.firstOrNull { it.name == ".debug_loc" })
    }

    @Test
    fun `debug_frame section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_frame", SectionKind.DEBUG_FRAME, byteArrayOf(0x00), align = 1),
        ))
        assertNotNull(obj.sections.firstOrNull { it.name == ".debug_frame" })
    }

    @Test
    fun `debug_aranges section round-trips`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_aranges", SectionKind.DEBUG_ARANGES, byteArrayOf(0x00), align = 1),
        ))
        assertNotNull(obj.sections.firstOrNull { it.name == ".debug_aranges" })
    }

    @Test
    fun `debug section has no ALLOC flag`() {
        val elf = writeAndParseElf(sections = listOf(
            textSection(),
            Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x01), align = 1),
        ))
        val sec = elf.sectionByName(".debug_info")!!
        assertFalse(sec.isAllocated)
        assertFalse(sec.isWritable)
        assertFalse(sec.isExecutable)
    }

    @Test
    fun `multiple debug sections in one object`() {
        val obj = roundTrip(sections = listOf(
            textSection(),
            Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x01), align = 1),
            Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, byteArrayOf(0x02), align = 1),
            Section(".debug_line", SectionKind.DEBUG_LINE, byteArrayOf(0x03), align = 1),
            Section(".debug_str", SectionKind.DEBUG_STR, byteArrayOf(0x04), align = 1),
        ))
        assertEquals(4, obj.sections.count { it.kind.isDebug })
    }

    // --- Weak symbols, hidden/protected visibility ---

    @Test
    fun `weak global function symbol`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("weak_fn", value = 0, size = 8, section = ".text", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )
        val sym = obj.symbols.first { it.name == "weak_fn" }
        assertEquals(SymbolBinding.WEAK, sym.binding)
        assertEquals(SymbolKind.FUNCTION, sym.kind)
    }

    @Test
    fun `weak undefined symbol`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("weak_ext", value = 0, section = null, binding = SymbolBinding.WEAK, kind = SymbolKind.UNDEFINED)),
        )
        val sym = obj.symbols.first { it.name == "weak_ext" }
        assertEquals(SymbolBinding.WEAK, sym.binding)
    }

    @Test
    fun `hidden symbol not in dynamic table`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("hidden_fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.HIDDEN)),
        )
        val sym = obj.symbols.first { it.name == "hidden_fn" }
        assertEquals(SymbolVisibility.HIDDEN, sym.visibility)
    }

    @Test
    fun `protected symbol visibility preserved`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("prot_fn", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.PROTECTED)),
        )
        val sym = obj.symbols.first { it.name == "prot_fn" }
        assertEquals(SymbolVisibility.PROTECTED, sym.visibility)
    }

    @Test
    fun `mix of visibilities in same object`() {
        val obj = roundTrip(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(
                Symbol("default_fn", value = 0, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.DEFAULT),
                Symbol("hidden_fn", value = 16, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.HIDDEN),
                Symbol("protected_fn", value = 32, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.PROTECTED),
            ),
        )
        assertEquals(SymbolVisibility.DEFAULT, obj.symbols.first { it.name == "default_fn" }.visibility)
        assertEquals(SymbolVisibility.HIDDEN, obj.symbols.first { it.name == "hidden_fn" }.visibility)
        assertEquals(SymbolVisibility.PROTECTED, obj.symbols.first { it.name == "protected_fn" }.visibility)
    }

    // --- Multiple relocation sections ---

    @Test
    fun `relocations for text and data sections`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 32), dataSection(size = 32)),
            symbols = listOf(
                Symbol("ext1", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("ext2", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "ext1", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "ext2", type = RelocationType.X86_64.R_64, addend = 0, section = ".data"),
            ),
        )
        assertNotNull(elf.sectionByName(".rela.text"))
        assertNotNull(elf.sectionByName(".rela.data"))
        assertEquals(2, elf.relocations.size)
    }

    @Test
    fun `multiple relocations in same section`() {
        val obj = roundTrip(
            sections = listOf(textSection(size = 64)),
            symbols = listOf(
                Symbol("a", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("b", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("c", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "a", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 10, symbol = "b", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
                Relocation(offset = 20, symbol = "c", type = RelocationType.X86_64.R_64, addend = 0, section = ".text"),
            ),
        )
        assertEquals(3, obj.relocations.size)
        assertEquals("a", obj.relocations[0].symbol)
        assertEquals("b", obj.relocations[1].symbol)
        assertEquals("c", obj.relocations[2].symbol)
    }

    @Test
    fun `relocations across text and data with different types`() {
        val obj = roundTrip(
            sections = listOf(textSection(size = 32), dataSection(size = 32)),
            symbols = listOf(
                Symbol("fn", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("var", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "fn", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
                Relocation(offset = 0, symbol = "var", type = RelocationType.X86_64.R_64, addend = 0, section = ".data"),
            ),
        )
        val textRel = obj.relocations.first { it.section == ".text" }
        val dataRel = obj.relocations.first { it.section == ".data" }
        assertEquals(RelocationType.X86_64.PLT32, textRel.type)
        assertEquals(RelocationType.X86_64.R_64, dataRel.type)
    }

    // --- Large symbol tables ---

    @Test
    fun `100 symbols round-trip correctly`() {
        val symbols = (0 until 100).map { i ->
            Symbol("sym_$i", value = i.toLong() * 4, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = roundTrip(
            sections = listOf(textSection(size = 400)),
            symbols = symbols,
        )
        for (i in 0 until 100) {
            val sym = obj.symbols.firstOrNull { it.name == "sym_$i" }
            assertNotNull(sym, "Missing symbol sym_$i")
            assertEquals(i.toLong() * 4, sym!!.value)
        }
    }

    @Test
    fun `150 symbols round-trip correctly`() {
        val symbols = (0 until 150).map { i ->
            Symbol("func_$i", value = i.toLong() * 8, size = 8, section = ".text", binding = if (i % 3 == 0) SymbolBinding.LOCAL else SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val obj = roundTrip(
            sections = listOf(textSection(size = 1200)),
            symbols = symbols,
        )
        for (i in 0 until 150) {
            assertNotNull(obj.symbols.firstOrNull { it.name == "func_$i" }, "Missing func_$i")
        }
    }

    @Test
    fun `200 symbols with mixed bindings`() {
        val symbols = (0 until 200).map { i ->
            val binding = when (i % 4) {
                0 -> SymbolBinding.LOCAL
                1 -> SymbolBinding.GLOBAL
                2 -> SymbolBinding.WEAK
                else -> SymbolBinding.GLOBAL
            }
            Symbol("s_$i", value = i.toLong() * 2, size = 2, section = ".text", binding = binding, kind = SymbolKind.FUNCTION)
        }
        val obj = roundTrip(
            sections = listOf(textSection(size = 400)),
            symbols = symbols,
        )
        assertEquals(200, obj.symbols.count { it.name.startsWith("s_") && it.kind != SymbolKind.SECTION })
    }

    // --- Section flags combinations ---

    @Test
    fun `text section has ALLOC and EXEC flags in ObjectFile`() {
        val obj = roundTrip(sections = listOf(textSection()))
        val text = obj.sections.first { it.name == ".text" }
        assertTrue(SectionFlag.ALLOC in text.flags)
        assertTrue(SectionFlag.EXEC in text.flags)
    }

    @Test
    fun `data section has ALLOC and WRITE flags in ObjectFile`() {
        val obj = roundTrip(sections = listOf(dataSection()))
        val data = obj.sections.first { it.name == ".data" }
        assertTrue(SectionFlag.ALLOC in data.flags)
        assertTrue(SectionFlag.WRITE in data.flags)
    }

    @Test
    fun `rodata section has ALLOC but not WRITE or EXEC in ObjectFile`() {
        val obj = roundTrip(sections = listOf(rodataSection()))
        val rodata = obj.sections.first { it.name == ".rodata" }
        assertTrue(SectionFlag.ALLOC in rodata.flags)
        assertFalse(SectionFlag.WRITE in rodata.flags)
        assertFalse(SectionFlag.EXEC in rodata.flags)
    }

    @Test
    fun `bss section has ALLOC and WRITE flags in ObjectFile`() {
        val obj = roundTrip(sections = listOf(bssSection()))
        val bss = obj.sections.first { it.name == ".bss" }
        assertTrue(SectionFlag.ALLOC in bss.flags)
        assertTrue(SectionFlag.WRITE in bss.flags)
    }

    @Test
    fun `rela section has INFO_LINK flag`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = elf.sectionByName(".rela.text")!!
        assertTrue(rela.flags and ElfSectionFlags.INFO_LINK != 0L)
    }

    // --- Architecture ---

    @Test
    fun `x86_64 object file architecture`() {
        val obj = roundTrip(sections = listOf(textSection()))
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun `aarch64 object file architecture`() {
        val obj = roundTrip(sections = listOf(textSection()), machine = ElfMachine.AARCH64.code)
        assertEquals(ArchType.AARCH64, obj.arch.arch)
    }

    @Test
    fun `riscv object file architecture`() {
        val obj = roundTrip(sections = listOf(textSection()), machine = ElfMachine.RISCV.code)
        assertEquals(ArchType.RISCV64, obj.arch.arch)
    }

    // --- ELF enums ---

    @Test
    fun `ElfClass values`() {
        assertEquals(1, ElfClass.ELF32.code)
        assertEquals(2, ElfClass.ELF64.code)
    }

    @Test
    fun `ElfClass fromCode`() {
        assertEquals(ElfClass.ELF32, ElfClass.fromCode(1))
        assertEquals(ElfClass.ELF64, ElfClass.fromCode(2))
        assertNull(ElfClass.fromCode(99))
    }

    @Test
    fun `ElfData values`() {
        assertEquals(1, ElfData.LSB.code)
        assertEquals(2, ElfData.MSB.code)
    }

    @Test
    fun `ElfData fromCode`() {
        assertEquals(ElfData.LSB, ElfData.fromCode(1))
        assertEquals(ElfData.MSB, ElfData.fromCode(2))
        assertNull(ElfData.fromCode(99))
    }

    @Test
    fun `ElfObjectType values`() {
        assertEquals(1, ElfObjectType.REL.code)
        assertEquals(2, ElfObjectType.EXEC.code)
        assertEquals(3, ElfObjectType.DYN.code)
    }

    @Test
    fun `ElfObjectType fromCode`() {
        assertEquals(ElfObjectType.REL, ElfObjectType.fromCode(1))
        assertEquals(ElfObjectType.EXEC, ElfObjectType.fromCode(2))
        assertEquals(ElfObjectType.DYN, ElfObjectType.fromCode(3))
        assertNull(ElfObjectType.fromCode(99))
    }

    @Test
    fun `ElfMachine values`() {
        assertEquals(0x3E, ElfMachine.X86_64.code)
        assertEquals(0xB7, ElfMachine.AARCH64.code)
        assertEquals(0xF3, ElfMachine.RISCV.code)
    }

    @Test
    fun `ElfMachine fromCode`() {
        assertEquals(ElfMachine.X86_64, ElfMachine.fromCode(0x3E))
        assertEquals(ElfMachine.AARCH64, ElfMachine.fromCode(0xB7))
        assertEquals(ElfMachine.RISCV, ElfMachine.fromCode(0xF3))
        assertNull(ElfMachine.fromCode(0))
    }

    @Test
    fun `ElfSectionType values`() {
        assertEquals(0, ElfSectionType.NULL.code)
        assertEquals(1, ElfSectionType.PROGBITS.code)
        assertEquals(2, ElfSectionType.SYMTAB.code)
        assertEquals(3, ElfSectionType.STRTAB.code)
        assertEquals(4, ElfSectionType.RELA.code)
        assertEquals(8, ElfSectionType.NOBITS.code)
        assertEquals(9, ElfSectionType.REL.code)
        assertEquals(11, ElfSectionType.DYNSYM.code)
    }

    @Test
    fun `ElfSectionType fromCode`() {
        assertEquals(ElfSectionType.NULL, ElfSectionType.fromCode(0))
        assertEquals(ElfSectionType.PROGBITS, ElfSectionType.fromCode(1))
        assertEquals(ElfSectionType.SYMTAB, ElfSectionType.fromCode(2))
        assertEquals(ElfSectionType.STRTAB, ElfSectionType.fromCode(3))
        assertEquals(ElfSectionType.RELA, ElfSectionType.fromCode(4))
        assertEquals(ElfSectionType.NOBITS, ElfSectionType.fromCode(8))
        assertNull(ElfSectionType.fromCode(999))
    }

    @Test
    fun `ElfSectionFlags constants`() {
        assertEquals(0x1L, ElfSectionFlags.WRITE)
        assertEquals(0x2L, ElfSectionFlags.ALLOC)
        assertEquals(0x4L, ElfSectionFlags.EXECINSTR)
        assertEquals(0x40L, ElfSectionFlags.INFO_LINK)
    }

    @Test
    fun `ElfSymbolBinding values`() {
        assertEquals(0, ElfSymbolBinding.LOCAL.code)
        assertEquals(1, ElfSymbolBinding.GLOBAL.code)
        assertEquals(2, ElfSymbolBinding.WEAK.code)
    }

    @Test
    fun `ElfSymbolBinding fromCode`() {
        assertEquals(ElfSymbolBinding.LOCAL, ElfSymbolBinding.fromCode(0))
        assertEquals(ElfSymbolBinding.GLOBAL, ElfSymbolBinding.fromCode(1))
        assertEquals(ElfSymbolBinding.WEAK, ElfSymbolBinding.fromCode(2))
        assertNull(ElfSymbolBinding.fromCode(99))
    }

    @Test
    fun `ElfSymbolType values`() {
        assertEquals(0, ElfSymbolType.NOTYPE.code)
        assertEquals(1, ElfSymbolType.OBJECT.code)
        assertEquals(2, ElfSymbolType.FUNC.code)
        assertEquals(3, ElfSymbolType.SECTION.code)
        assertEquals(4, ElfSymbolType.FILE.code)
        assertEquals(5, ElfSymbolType.COMMON.code)
        assertEquals(6, ElfSymbolType.TLS.code)
        assertEquals(10, ElfSymbolType.GNU_IFUNC.code)
    }

    @Test
    fun `ElfSymbolType fromCode`() {
        assertEquals(ElfSymbolType.NOTYPE, ElfSymbolType.fromCode(0))
        assertEquals(ElfSymbolType.FUNC, ElfSymbolType.fromCode(2))
        assertEquals(ElfSymbolType.SECTION, ElfSymbolType.fromCode(3))
        assertNull(ElfSymbolType.fromCode(99))
    }

    @Test
    fun `ElfSymbolVisibility values`() {
        assertEquals(0, ElfSymbolVisibility.DEFAULT.code)
        assertEquals(2, ElfSymbolVisibility.HIDDEN.code)
        assertEquals(3, ElfSymbolVisibility.PROTECTED.code)
    }

    @Test
    fun `ElfSymbolVisibility fromCode`() {
        assertEquals(ElfSymbolVisibility.DEFAULT, ElfSymbolVisibility.fromCode(0))
        assertEquals(ElfSymbolVisibility.HIDDEN, ElfSymbolVisibility.fromCode(2))
        assertEquals(ElfSymbolVisibility.PROTECTED, ElfSymbolVisibility.fromCode(3))
        assertNull(ElfSymbolVisibility.fromCode(99))
    }

    @Test
    fun `ElfSegmentType values`() {
        assertEquals(1, ElfSegmentType.LOAD.code)
        assertEquals(2, ElfSegmentType.DYNAMIC.code)
        assertEquals(3, ElfSegmentType.INTERP.code)
        assertEquals(4, ElfSegmentType.NOTE.code)
        assertEquals(6, ElfSegmentType.PHDR.code)
    }

    @Test
    fun `ElfSegmentFlags constants`() {
        assertEquals(1, ElfSegmentFlags.X)
        assertEquals(2, ElfSegmentFlags.W)
        assertEquals(4, ElfSegmentFlags.R)
    }

    @Test
    fun `ElfDynamicTag values`() {
        assertEquals(0L, ElfDynamicTag.NULL.code)
        assertEquals(1L, ElfDynamicTag.NEEDED.code)
        assertEquals(14L, ElfDynamicTag.SONAME.code)
        assertEquals(15L, ElfDynamicTag.RPATH.code)
        assertEquals(29L, ElfDynamicTag.RUNPATH.code)
    }

    @Test
    fun `Elf stInfo encodes binding and type`() {
        val info = Elf.stInfo(ElfSymbolBinding.GLOBAL, ElfSymbolType.FUNC)
        assertEquals((1 shl 4) or 2, info)
    }

    @Test
    fun `Elf stInfo local notype`() {
        val info = Elf.stInfo(ElfSymbolBinding.LOCAL, ElfSymbolType.NOTYPE)
        assertEquals(0, info)
    }

    @Test
    fun `Elf stInfo weak func`() {
        val info = Elf.stInfo(ElfSymbolBinding.WEAK, ElfSymbolType.FUNC)
        assertEquals((2 shl 4) or 2, info)
    }

    @Test
    fun `Elf constants`() {
        assertEquals(64, Elf.EHDR64_SIZE)
        assertEquals(64, Elf.SHDR64_SIZE)
        assertEquals(56, Elf.PHDR64_SIZE)
        assertEquals(24, Elf.SYM64_SIZE)
        assertEquals(24, Elf.RELA64_SIZE)
        assertEquals(0, Elf.SHN_UNDEF)
        assertEquals(0xFFF1, Elf.SHN_ABS)
    }

    // --- canRead / detectFormat ---

    @Test
    fun `canRead returns true for valid ELF`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertTrue(reader.canRead(bytes))
    }

    @Test
    fun `canRead returns false for empty array`() {
        assertFalse(reader.canRead(ByteArray(0)))
    }

    @Test
    fun `canRead returns false for short array`() {
        assertFalse(reader.canRead(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte())))
    }

    @Test
    fun `canRead returns false for wrong magic`() {
        assertFalse(reader.canRead(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun `canRead returns true for ELF32`() {
        val bytes = writeObj(sections = listOf(textSection()))
        bytes[4] = ElfClass.ELF32.code.toByte()
        assertTrue(reader.canRead(bytes))
    }

    @Test
    fun `ElfReader canRead static method`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertTrue(ElfReader.canRead(bytes))
    }

    @Test
    fun `detectFormat identifies ELF`() {
        val bytes = writeObj(sections = listOf(textSection()))
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    // --- ElfFile query methods ---

    @Test
    fun `ElfFile sectionByName finds section`() {
        val elf = writeAndParseElf(sections = listOf(textSection(), dataSection()))
        assertNotNull(elf.sectionByName(".text"))
        assertNotNull(elf.sectionByName(".data"))
        assertNull(elf.sectionByName(".nonexistent"))
    }

    @Test
    fun `ElfFile sectionByIndex finds section`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        val text = elf.sectionByName(".text")!!
        val byIdx = elf.sectionByIndex(text.index)
        assertNotNull(byIdx)
        assertEquals(".text", byIdx!!.name)
    }

    @Test
    fun `ElfFile symbolsBySection filters correctly`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection(size = 64), dataSection()),
            symbols = listOf(
                Symbol("text_fn", value = 0, size = 16, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("data_fn", value = 0, size = 8, section = ".data", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val textSyms = elf.symbolsBySection(".text")
        assertTrue(textSyms.any { it.name == "text_fn" })
        assertFalse(textSyms.any { it.name == "data_fn" })
    }

    // --- ElfSymbolEntry query methods ---

    @Test
    fun `ElfSymbolEntry isGlobal`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("g", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertTrue(elf.symbols.first { it.name == "g" }.isGlobal)
    }

    @Test
    fun `ElfSymbolEntry isLocal`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("l", value = 0, size = 4, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)),
        )
        assertTrue(elf.symbols.first { it.name == "l" }.isLocal)
    }

    @Test
    fun `ElfSymbolEntry isWeak`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("w", value = 0, size = 4, section = ".text", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )
        assertTrue(elf.symbols.first { it.name == "w" }.isWeak)
    }

    @Test
    fun `ElfSymbolEntry isUndefined`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("u", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
        )
        assertTrue(elf.symbols.first { it.name == "u" }.isUndefined)
    }

    @Test
    fun `ElfSymbolEntry isFunction`() {
        val elf = writeAndParseElf(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("f", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertTrue(elf.symbols.first { it.name == "f" }.isFunction)
    }

    // --- ElfSectionEntry query methods ---

    @Test
    fun `ElfSectionEntry isAllocated for text`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertTrue(elf.sectionByName(".text")!!.isAllocated)
    }

    @Test
    fun `ElfSectionEntry isWritable for data`() {
        val elf = writeAndParseElf(sections = listOf(dataSection()))
        assertTrue(elf.sectionByName(".data")!!.isWritable)
    }

    @Test
    fun `ElfSectionEntry isExecutable for text`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertTrue(elf.sectionByName(".text")!!.isExecutable)
    }

    @Test
    fun `ElfSectionEntry not writable for rodata`() {
        val elf = writeAndParseElf(sections = listOf(rodataSection()))
        assertFalse(elf.sectionByName(".rodata")!!.isWritable)
    }

    // --- ObjectFile metadata ---

    @Test
    fun `ObjectFile has no dynamicInfo for relocatable`() {
        val obj = roundTrip(sections = listOf(textSection()))
        assertNull(obj.dynamicInfo)
    }

    @Test
    fun `ObjectFile reader format is ELF`() {
        assertEquals(ObjectFormat.ELF, reader.format)
    }

    // --- Complex round-trip ---

    @Test
    fun `full complex object round-trip`() {
        val textCode = byteArrayOf(
            0x55,
            0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x5D,
            0xC3.toByte(),
        )
        val rodata = "Hello, World!\u0000".toByteArray(Charsets.US_ASCII)
        val data = ByteArray(16) { it.toByte() }

        val obj = roundTrip(
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                Section(".data", SectionKind.DATA, data, align = 8),
                Section(".bss", SectionKind.BSS, ByteArray(512), align = 32),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = textCode.size.toLong(), section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("local_helper", value = 0, size = 4, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("weak_sym", value = 0, size = 4, section = ".text", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION, visibility = SymbolVisibility.HIDDEN),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
            ),
        )

        assertEquals(ObjectFormat.ELF, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
        assertTrue(ObjectFlag.RELOCATABLE in obj.metadata.flags)

        assertArrayEquals(textCode, obj.sections.first { it.name == ".text" }.data)
        assertArrayEquals(rodata, obj.sections.first { it.name == ".rodata" }.data)
        assertArrayEquals(data, obj.sections.first { it.name == ".data" }.data)
        assertEquals(512, obj.sections.first { it.name == ".bss" }.data.size)

        val main = obj.symbols.first { it.name == "main" }
        assertEquals(SymbolBinding.GLOBAL, main.binding)
        assertEquals(SymbolKind.FUNCTION, main.kind)
        assertEquals(textCode.size.toLong(), main.size)

        val puts = obj.symbols.first { it.name == "puts" }
        assertEquals(SymbolKind.UNDEFINED, puts.kind)
        assertNull(puts.section)

        val local = obj.symbols.first { it.name == "local_helper" }
        assertEquals(SymbolBinding.LOCAL, local.binding)

        val weak = obj.symbols.first { it.name == "weak_sym" }
        assertEquals(SymbolBinding.WEAK, weak.binding)
        assertEquals(SymbolVisibility.HIDDEN, weak.visibility)

        assertEquals(1, obj.relocations.size)
        val rel = obj.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("puts", rel.symbol)
        assertEquals(RelocationType.X86_64.PLT32, rel.type)
        assertEquals(-4L, rel.addend)
        assertEquals(".text", rel.section)
    }

    // --- RelocationType enum coverage ---

    @Test
    fun `x86_64 relocation type names`() {
        assertEquals("R_X86_64_R_64", RelocationType.X86_64.R_64.relocName)
        assertEquals("R_X86_64_PC32", RelocationType.X86_64.PC32.relocName)
        assertEquals("R_X86_64_PLT32", RelocationType.X86_64.PLT32.relocName)
    }

    @Test
    fun `aarch64 relocation type names`() {
        assertEquals("R_AARCH64_CALL26", RelocationType.AArch64.CALL26.relocName)
        assertEquals("R_AARCH64_ABS64", RelocationType.AArch64.ABS64.relocName)
    }

    @Test
    fun `riscv relocation type names`() {
        assertEquals("R_RISCV_CALL_PLT", RelocationType.RiscV.CALL_PLT.relocName)
        assertEquals("R_RISCV_PCREL_HI20", RelocationType.RiscV.PCREL_HI20.relocName)
    }

    @Test
    fun `x86_64 relocation type values comprehensive`() {
        assertEquals(0, RelocationType.X86_64.NONE.value)
        assertEquals(1, RelocationType.X86_64.R_64.value)
        assertEquals(2, RelocationType.X86_64.PC32.value)
        assertEquals(3, RelocationType.X86_64.GOT32.value)
        assertEquals(4, RelocationType.X86_64.PLT32.value)
        assertEquals(5, RelocationType.X86_64.COPY.value)
        assertEquals(6, RelocationType.X86_64.GLOB_DAT.value)
        assertEquals(7, RelocationType.X86_64.JUMP_SLOT.value)
        assertEquals(8, RelocationType.X86_64.RELATIVE.value)
        assertEquals(9, RelocationType.X86_64.GOTPCREL.value)
        assertEquals(10, RelocationType.X86_64.R_32.value)
        assertEquals(11, RelocationType.X86_64.R_32S.value)
        assertEquals(41, RelocationType.X86_64.GOTPCRELX.value)
        assertEquals(42, RelocationType.X86_64.REX_GOTPCRELX.value)
    }

    @Test
    fun `aarch64 relocation type values`() {
        assertEquals(257, RelocationType.AArch64.ABS64.value)
        assertEquals(258, RelocationType.AArch64.ABS32.value)
        assertEquals(275, RelocationType.AArch64.ADR_PREL_PG_HI21.value)
        assertEquals(277, RelocationType.AArch64.ADD_ABS_LO12_NC.value)
        assertEquals(282, RelocationType.AArch64.JUMP26.value)
        assertEquals(283, RelocationType.AArch64.CALL26.value)
    }

    @Test
    fun `riscv relocation type values`() {
        assertEquals(0, RelocationType.RiscV.NONE.value)
        assertEquals(1, RelocationType.RiscV.R_32.value)
        assertEquals(2, RelocationType.RiscV.R_64.value)
        assertEquals(16, RelocationType.RiscV.BRANCH.value)
        assertEquals(17, RelocationType.RiscV.JAL.value)
        assertEquals(18, RelocationType.RiscV.CALL.value)
        assertEquals(19, RelocationType.RiscV.CALL_PLT.value)
        assertEquals(23, RelocationType.RiscV.PCREL_HI20.value)
        assertEquals(24, RelocationType.RiscV.PCREL_LO12_I.value)
        assertEquals(51, RelocationType.RiscV.RELAX.value)
    }

    // --- SectionKind.isDebug ---

    @Test
    fun `SectionKind isDebug true for debug sections`() {
        assertTrue(SectionKind.DEBUG_INFO.isDebug)
        assertTrue(SectionKind.DEBUG_ABBREV.isDebug)
        assertTrue(SectionKind.DEBUG_LINE.isDebug)
        assertTrue(SectionKind.DEBUG_STR.isDebug)
        assertTrue(SectionKind.DEBUG_RANGES.isDebug)
        assertTrue(SectionKind.DEBUG_LOC.isDebug)
        assertTrue(SectionKind.DEBUG_FRAME.isDebug)
        assertTrue(SectionKind.DEBUG_ARANGES.isDebug)
    }

    @Test
    fun `SectionKind isDebug false for non-debug sections`() {
        assertFalse(SectionKind.TEXT.isDebug)
        assertFalse(SectionKind.DATA.isDebug)
        assertFalse(SectionKind.BSS.isDebug)
        assertFalse(SectionKind.RODATA.isDebug)
        assertFalse(SectionKind.SYMTAB.isDebug)
    }

    // --- Symtab link to strtab ---

    @Test
    fun `symtab link field points to strtab section`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("test", value = 0, size = 4, section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val symtab = obj.sections.first { it.kind == SectionKind.SYMTAB }
        assertEquals(".strtab", symtab.link)
    }

    @Test
    fun `rela section info points to target section`() {
        val obj = roundTrip(
            sections = listOf(textSection()),
            symbols = listOf(Symbol("ext", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val rela = obj.sections.first { it.name.startsWith(".rela") }
        assertEquals(".text", rela.info)
    }

    // --- Large data ---

    @Test
    fun `large section data 64KB round-trips`() {
        val bigData = ByteArray(65536) { (it % 256).toByte() }
        val obj = roundTrip(sections = listOf(Section(".data", SectionKind.DATA, bigData, align = 16)))
        assertArrayEquals(bigData, obj.sections.first { it.name == ".data" }.data)
    }

    @Test
    fun `many relocations 50 entries`() {
        val symbols = (0 until 50).map { i ->
            Symbol("ext_$i", value = 0, section = null, binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
        }
        val relocs = (0 until 50).map { i ->
            Relocation(offset = i.toLong() * 8, symbol = "ext_$i", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")
        }
        val obj = roundTrip(
            sections = listOf(textSection(size = 400)),
            symbols = symbols,
            relocations = relocs,
        )
        assertEquals(50, obj.relocations.size)
    }

    // --- No symbols ---

    @Test
    fun `object with no user symbols`() {
        val obj = roundTrip(sections = listOf(textSection()))
        // Should still have section symbols but no user-defined named symbols
        val userSyms = obj.symbols.filter { it.name.isNotEmpty() && it.kind != SymbolKind.SECTION }
        assertEquals(0, userSyms.size)
    }

    // --- No relocations ---

    @Test
    fun `object with no relocations has no rela sections`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertNull(elf.sectionByName(".rela.text"))
    }

    // --- No dynamic info ---

    @Test
    fun `ElfFile dynamicInfo is null for relocatable`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertNull(elf.dynamicInfo)
    }

    @Test
    fun `ElfFile segments empty for relocatable`() {
        val elf = writeAndParseElf(sections = listOf(textSection()))
        assertTrue(elf.segments.isEmpty())
    }
}

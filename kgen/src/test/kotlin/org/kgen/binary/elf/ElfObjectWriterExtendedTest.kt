package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfObjectWriterExtendedTest {

    private val writer = ElfObjectWriter()

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun obj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    private fun findSectionByType(bytes: ByteArray, type: Int): Pair<Int, Int>? {
        val buf = le(bytes)
        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)
        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == type) return sh to i
        }
        return null
    }

    @Test
    fun `empty sections list produces minimal output`() {
        val result = writer.write(obj())
        val buf = le(result)
        assertEquals(0x7f, result[0].toInt() and 0xFF)
        assertEquals(ElfObjectType.REL.code, readU16(buf, 16))
    }

    @Test
    fun `text only object has correct section count`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(result)
        val shnum = readU16(buf, 60)
        // null + .text + .symtab + .strtab + .shstrtab = 5
        assertEquals(5, shnum)
    }

    @Test
    fun `multiple sections have correct section count`() {
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 4),
                Section(".rodata", SectionKind.RODATA, "hello".toByteArray(), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(128), align = 16),
            )
        ))
        val buf = le(result)
        val shnum = readU16(buf, 60)
        // null + 4 user sections + .symtab + .strtab + .shstrtab = 8
        assertEquals(8, shnum)
    }

    @Test
    fun `rodata section has ALLOC but not WRITE or EXECINSTR`() {
        val result = writer.write(obj(
            sections = listOf(Section(".rodata", SectionKind.RODATA, "data".toByteArray(), align = 1))
        ))
        val buf = le(result)
        val shoff = readU64(buf, 40).toInt()
        val shRodata = shoff + Elf.SHDR64_SIZE // section 1
        val flags = readU64(buf, shRodata + 8)
        assertTrue(flags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(flags and ElfSectionFlags.WRITE == 0L)
        assertTrue(flags and ElfSectionFlags.EXECINSTR == 0L)
    }

    @Test
    fun `data section has ALLOC and WRITE flags`() {
        val result = writer.write(obj(
            sections = listOf(Section(".data", SectionKind.DATA, byteArrayOf(1, 2, 3, 4), align = 4))
        ))
        val buf = le(result)
        val shoff = readU64(buf, 40).toInt()
        val shData = shoff + Elf.SHDR64_SIZE
        val flags = readU64(buf, shData + 8)
        assertTrue(flags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(flags and ElfSectionFlags.WRITE != 0L)
    }

    @Test
    fun `bss section has NOBITS type and correct size`() {
        val bssSize = 2048
        val result = writer.write(obj(
            sections = listOf(Section(".bss", SectionKind.BSS, ByteArray(bssSize), align = 16))
        ))
        val buf = le(result)
        val shoff = readU64(buf, 40).toInt()
        val shBss = shoff + Elf.SHDR64_SIZE
        assertEquals(ElfSectionType.NOBITS.code, readU32(buf, shBss + 4))
        assertEquals(bssSize.toLong(), readU64(buf, shBss + 32))
    }

    @Test
    fun `bss section does not bloat file size`() {
        val smallObj = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val bssObj = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".bss", SectionKind.BSS, ByteArray(1024 * 1024), align = 16),
            )
        ))
        // BSS should not add 1MB to file; just one more section header
        assertTrue(bssObj.size < smallObj.size + 1024, "BSS should not bloat file: ${bssObj.size} vs ${smallObj.size}")
    }

    @Test
    fun `symtab section has correct entry size`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(Symbol("main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        ))
        val (sh, _) = findSectionByType(result, ElfSectionType.SYMTAB.code)!!
        val buf = le(result)
        assertEquals(Elf.SYM64_SIZE.toLong(), readU64(buf, sh + 56)) // sh_entsize
    }

    @Test
    fun `strtab contains symbol names`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(
                Symbol("alpha", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("beta", value = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        ))
        val str = String(result, Charsets.ISO_8859_1)
        assertTrue(str.contains("alpha"))
        assertTrue(str.contains("beta"))
    }

    @Test
    fun `shstrtab contains section names`() {
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 1),
            )
        ))
        val str = String(result, Charsets.ISO_8859_1)
        assertTrue(str.contains(".text"))
        assertTrue(str.contains(".data"))
        assertTrue(str.contains(".symtab"))
        assertTrue(str.contains(".strtab"))
        assertTrue(str.contains(".shstrtab"))
    }

    @Test
    fun `weak symbol produces valid output`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = listOf(Symbol("weakfn", value = 0, section = ".text",
                binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        ))
        assertTrue(result.size > Elf.EHDR64_SIZE)
    }

    @Test
    fun `undefined symbol has SHN_UNDEF index`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.SYMTAB.code)!!
        val symtabOff = readU64(buf, sh + 24).toInt()
        val symtabSize = readU64(buf, sh + 32)
        val numSyms = (symtabSize / Elf.SYM64_SIZE).toInt()
        // Last symbol should be "puts" with SHN_UNDEF
        val lastSym = symtabOff + (numSyms - 1) * Elf.SYM64_SIZE
        val shndx = readU16(buf, lastSym + 6)
        assertEquals(Elf.SHN_UNDEF, shndx)
    }

    @Test
    fun `multiple relocations on same section produce one rela section`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "foo", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
                Relocation(offset = 10, symbol = "bar", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        val relaSize = readU64(buf, sh + 32)
        assertEquals(2 * Elf.RELA64_SIZE.toLong(), relaSize, "Should have 2 relocation entries")
    }

    @Test
    fun `rela section entry size is correct`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(Symbol("x", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "x",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        assertEquals(Elf.RELA64_SIZE.toLong(), readU64(buf, sh + 56))
    }

    @Test
    fun `section header table offset is within file bounds`() {
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(256), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(64), align = 8),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        ))
        val buf = le(result)
        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)
        assertTrue(shoff > 0)
        assertTrue(shoff + shnum * Elf.SHDR64_SIZE <= result.size)
    }

    @Test
    fun `large text section data is preserved`() {
        val code = ByteArray(4096) { (it % 256).toByte() }
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16))
        ))
        val textOffset = Elf.EHDR64_SIZE
        for (i in code.indices) {
            assertEquals(code[i], result[textOffset + i], "Byte $i mismatch")
        }
    }

    @Test
    fun `mixed local and global symbols are ordered correctly`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("global1", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("local1", value = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("global2", value = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("local2", value = 24, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.SYMTAB.code)!!
        val info = readU32(buf, sh + 44) // sh_info = first global index
        val symtabSize = readU64(buf, sh + 32)
        val numSyms = (symtabSize / Elf.SYM64_SIZE).toInt()
        // All locals (including null + section syms + 2 user locals) before globals
        assertTrue(info > 0)
        assertTrue(info < numSyms)
    }

    @Test
    fun `output can be parsed by ElfReader`() {
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte(), 0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 4),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        ))
        assertTrue(ElfReader.canRead(result))
        val elf = ElfReader.read(result)
        assertEquals(ElfObjectType.REL, elf.header.type)
        assertEquals(ElfMachine.X86_64, elf.header.machine)
    }

    @Test
    fun `custom machine code is respected`() {
        val customWriter = ElfObjectWriter(machine = ElfMachine.AARCH64.code)
        val result = customWriter.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(4), align = 4))
        ))
        val buf = le(result)
        assertEquals(ElfMachine.AARCH64.code, readU16(buf, 18))
    }

    @Test
    fun `ELF version field is set correctly`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(result)
        assertEquals(Elf.VERSION, readU32(buf, 20)) // e_version
    }

    @Test
    fun `header size fields are correct`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(result)
        assertEquals(Elf.EHDR64_SIZE, readU16(buf, 52)) // e_ehsize
        assertEquals(Elf.SHDR64_SIZE, readU16(buf, 58)) // e_shentsize
    }

    @Test
    fun `no program headers for relocatable object`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1))
        ))
        val buf = le(result)
        assertEquals(0, readU16(buf, 56)) // e_phnum
        assertEquals(0L, readU64(buf, 32)) // e_phoff
    }

    @Test
    fun `relocation types are preserved`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(Symbol("target", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "target",
                type = RelocationType.X86_64.R_64, addend = 0, section = ".text")),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        val relaOff = readU64(buf, sh + 24).toInt()
        val relaInfo = readU64(buf, relaOff + 8)
        val relaType = (relaInfo and 0xFFFFFFFFL).toInt()
        assertEquals(RelocationType.X86_64.R_64.value, relaType)
    }

    @Test
    fun `PLT32 relocation type is preserved`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(Symbol("fn", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 5, symbol = "fn",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        val relaOff = readU64(buf, sh + 24).toInt()
        val relaInfo = readU64(buf, relaOff + 8)
        val relaType = (relaInfo and 0xFFFFFFFFL).toInt()
        assertEquals(RelocationType.X86_64.PLT32.value, relaType)
    }

    @Test
    fun `relocation addend is preserved`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 1)),
            symbols = listOf(Symbol("sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 0, symbol = "sym",
                type = RelocationType.X86_64.PC32, addend = -8, section = ".text")),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        val relaOff = readU64(buf, sh + 24).toInt()
        val addend = readU64(buf, relaOff + 16)
        assertEquals(-8L, addend)
    }

    @Test
    fun `relocation offset is preserved`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 1)),
            symbols = listOf(Symbol("sym", value = 0, section = null,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)),
            relocations = listOf(Relocation(offset = 17, symbol = "sym",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        ))
        val buf = le(result)
        val (sh, _) = findSectionByType(result, ElfSectionType.RELA.code)!!
        val relaOff = readU64(buf, sh + 24).toInt()
        assertEquals(17L, readU64(buf, relaOff))
    }

    @Test
    fun `unsupported section kinds are skipped`() {
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0x00), align = 1),
                Section(".note", SectionKind.NOTE, byteArrayOf(0x01), align = 1),
            )
        ))
        val buf = le(result)
        val shnum = readU16(buf, 60)
        // null + .text + .debug_info + .symtab + .strtab + .shstrtab = 6 (note skipped, debug preserved)
        assertEquals(6, shnum)
    }

    @Test
    fun `symbol with zero size is valid`() {
        val result = writer.write(obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(8), align = 1)),
            symbols = listOf(Symbol("label", value = 4, size = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
        ))
        assertTrue(result.size > Elf.EHDR64_SIZE)
    }

    @Test
    fun `data section content is preserved exactly`() {
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val result = writer.write(obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, data, align = 8),
            )
        ))
        // Find .data section and verify content
        val buf = le(result)
        val shoff = readU64(buf, 40).toInt()
        val shData = shoff + 2 * Elf.SHDR64_SIZE // section 2 (.data)
        val dataOff = readU64(buf, shData + 24).toInt()
        for (i in data.indices) {
            assertEquals(data[i], result[dataOff + i], "Data byte $i mismatch")
        }
    }
}

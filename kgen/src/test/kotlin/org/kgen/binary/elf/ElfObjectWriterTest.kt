package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfObjectWriterTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    @Test
    fun `produces valid ELF64 header`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 16)
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        assertEquals(0x7f, bytes[0].toInt() and 0xFF)
        assertEquals('E'.code, bytes[1].toInt() and 0xFF)
        assertEquals('L'.code, bytes[2].toInt() and 0xFF)
        assertEquals('F'.code, bytes[3].toInt() and 0xFF)

        assertEquals(ElfClass.ELF64.code, bytes[4].toInt() and 0xFF)
        assertEquals(ElfData.LSB.code, bytes[5].toInt() and 0xFF)
        assertEquals(ElfObjectType.REL.code, readU16(buf, 16))
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))
    }

    @Test
    fun `writes text section with correct data`() {
        val code = byteArrayOf(0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16)
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)

        val textOffset = Elf.EHDR64_SIZE
        for (i in code.indices) {
            assertEquals(code[i], bytes[textOffset + i], "Byte $i of .text mismatch")
        }
    }

    @Test
    fun `section headers are valid`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)
        assertEquals(6, shnum)

        val shNull = shoff
        assertEquals(0, readU32(buf, shNull + 4))

        val shText = shoff + Elf.SHDR64_SIZE
        assertEquals(ElfSectionType.PROGBITS.code, readU32(buf, shText + 4))
        val textFlags = readU64(buf, shText + 8)
        assertTrue(textFlags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(textFlags and ElfSectionFlags.EXECINSTR != 0L)

        val shData = shoff + 2 * Elf.SHDR64_SIZE
        assertEquals(ElfSectionType.PROGBITS.code, readU32(buf, shData + 4))
        val dataFlags = readU64(buf, shData + 8)
        assertTrue(dataFlags and ElfSectionFlags.ALLOC != 0L)
        assertTrue(dataFlags and ElfSectionFlags.WRITE != 0L)
    }

    @Test
    fun `symbol table contains local and global symbols`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 16, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)
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

        assertTrue(symtabOff > 0, "Should have a .symtab section")
        val numSyms = (symtabSize / Elf.SYM64_SIZE).toInt()
        assertEquals(4, numSyms)
        assertEquals(3, symtabInfo)
    }

    @Test
    fun `relocations produce rela section`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
            ),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(
                    offset = 5,
                    symbol = "puts",
                    type = RelocationType.X86_64.PLT32,
                    addend = -4,
                    section = ".text",
                ),
            ),
        )

        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)

        var relaOff = -1
        var relaSize = 0L

        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.RELA.code) {
                relaOff = readU64(buf, sh + 24).toInt()
                relaSize = readU64(buf, sh + 32)
                break
            }
        }

        assertTrue(relaOff > 0, "Should have a .rela.text section")
        assertEquals(Elf.RELA64_SIZE.toLong(), relaSize, "One relocation entry")

        val relaOffset = readU64(buf, relaOff)
        assertEquals(5L, relaOffset)

        val relaInfo = readU64(buf, relaOff + 8)
        val relaSym = (relaInfo shr 32).toInt()
        val relaType = (relaInfo and 0xFFFFFFFFL).toInt()
        assertEquals(RelocationType.X86_64.PLT32.value, relaType)
        assertTrue(relaSym > 0, "Symbol index should be positive")

        val relaAddend = readU64(buf, relaOff + 16)
        assertEquals(-4L, relaAddend)
    }

    @Test
    fun `bss section has no data but records size`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".bss", SectionKind.BSS, ByteArray(1024), align = 16),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)

        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.NOBITS.code) {
                val size = readU64(buf, sh + 32)
                assertEquals(1024L, size)
                return
            }
        }
        fail<Nothing>("Should have a BSS section with SHT_NOBITS")
    }

    @Test
    fun `round-trip structure is self-consistent`() {
        val textCode = byteArrayOf(
            0x55,
            0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x5D,
            0xC3.toByte(),
        )
        val rodata = "Hello, World!\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
                Section(".bss", SectionKind.BSS, ByteArray(256), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = textCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("msg", value = 0, size = rodata.size.toLong(), section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val bytes = ElfObjectWriter().write(obj)
        assertTrue(bytes.size > Elf.EHDR64_SIZE, "Output should be larger than just header")

        val buf = le(bytes)

        assertEquals(ElfObjectType.REL.code, readU16(buf, 16))
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))

        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)
        assertEquals(9, shnum)

        val shEnd = shoff + shnum * Elf.SHDR64_SIZE
        assertTrue(shEnd <= bytes.size, "Section headers should fit within file")

        val shstrtabIdx = readU16(buf, 62)
        assertTrue(shstrtabIdx in 0 until shnum)
    }

    @Test
    fun `writes symbol visibility`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
            ),
            symbols = listOf(
                Symbol("hiddenFunc", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION,
                    visibility = SymbolVisibility.HIDDEN),
                Symbol("protectedFunc", value = 16, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION,
                    visibility = SymbolVisibility.PROTECTED),
                Symbol("defaultFunc", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION,
                    visibility = SymbolVisibility.DEFAULT),
            ),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(obj)
        val buf = le(bytes)

        val shoff = readU64(buf, 40).toInt()
        val shnum = readU16(buf, 60)

        var symtabOff = -1
        var symtabSize = 0L

        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.SYMTAB.code) {
                symtabOff = readU64(buf, sh + 24).toInt()
                symtabSize = readU64(buf, sh + 32)
                break
            }
        }

        assertTrue(symtabOff > 0, "Should have a .symtab section")
        val numSyms = (symtabSize / Elf.SYM64_SIZE).toInt()

        // Read the strtab to find symbol names and match visibility
        var strtabOff = -1
        for (i in 0 until shnum) {
            val sh = shoff + i * Elf.SHDR64_SIZE
            if (readU32(buf, sh + 4) == ElfSectionType.STRTAB.code) {
                // Find the strtab that is linked from symtab (not shstrtab)
                val candidate = readU64(buf, sh + 24).toInt()
                // The first STRTAB we find that isn't the shstrtab is the symbol strtab
                if (i != readU16(buf, 62)) {
                    strtabOff = candidate
                    break
                }
            }
        }
        assertTrue(strtabOff > 0, "Should have a .strtab section")

        // Collect symbol name -> st_other mapping
        val visMap = mutableMapOf<String, Int>()
        for (s in 0 until numSyms) {
            val symOff = symtabOff + s * Elf.SYM64_SIZE
            val nameIdx = readU32(buf, symOff)  // st_name
            val stOther = bytes[symOff + 5].toInt() and 0xFF  // st_other

            // Read name from strtab
            if (nameIdx > 0) {
                val nameStart = strtabOff + nameIdx
                val sb = StringBuilder()
                var pos = nameStart
                while (pos < bytes.size && bytes[pos] != 0.toByte()) {
                    sb.append(bytes[pos].toInt().toChar())
                    pos++
                }
                visMap[sb.toString()] = stOther and 0x3
            }
        }

        assertEquals(ElfSymbolVisibility.HIDDEN.code, visMap["hiddenFunc"],
            "hiddenFunc should have HIDDEN visibility")
        assertEquals(ElfSymbolVisibility.PROTECTED.code, visMap["protectedFunc"],
            "protectedFunc should have PROTECTED visibility")
        assertEquals(ElfSymbolVisibility.DEFAULT.code, visMap["defaultFunc"],
            "defaultFunc should have DEFAULT visibility")
    }
}

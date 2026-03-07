package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    @Test
    fun `links hello world with puts`() {
        val textCode = byteArrayOf(
            0x48, 0x8d.toByte(), 0x3d,
            0x00, 0x00, 0x00, 0x00,
            0xe8.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x31, 0xc0.toByte(),
            0xc3.toByte(),
        )
        val rodataStr = "Hello, World!\n\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rodata", SectionKind.RODATA, rodataStr, align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = textCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, size = rodataStr.size.toLong(), section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "msg", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
                Relocation(offset = 8, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfLinker().link(listOf(obj))
        val buf = le(binary)

        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals('E'.code, binary[1].toInt() and 0xFF)
        assertEquals('L'.code, binary[2].toInt() and 0xFF)
        assertEquals('F'.code, binary[3].toInt() and 0xFF)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))

        val entryPoint = readU64(buf, 24)
        assertTrue(entryPoint > 0x400000, "Entry point should be in valid range")

        val phoff = readU64(buf, 32).toInt()
        assertEquals(Elf.EHDR64_SIZE, phoff)
        val phnum = readU16(buf, 56)
        assertEquals(6, phnum)

        val interpPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.INTERP.code, readU32(buf, interpPhdr))

        val loadRxPhdr = phoff + 2 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, loadRxPhdr))
        val loadRxFlags = readU32(buf, loadRxPhdr + 4)
        assertTrue(loadRxFlags and ElfSegmentFlags.R != 0)
        assertTrue(loadRxFlags and ElfSegmentFlags.X != 0)

        val loadRwPhdr = phoff + 3 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, loadRwPhdr))
        val loadRwFlags = readU32(buf, loadRwPhdr + 4)
        assertTrue(loadRwFlags and ElfSegmentFlags.R != 0)
        assertTrue(loadRwFlags and ElfSegmentFlags.W != 0)

        val dynPhdr = phoff + 4 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.DYNAMIC.code, readU32(buf, dynPhdr))

        assertTrue(binary.size > 1000, "Binary should be non-trivial size")
        assertTrue(binary.size < 100000, "Binary should not be unreasonably large")
    }

    @Test
    fun `links multiple object files`() {
        val mainCode = byteArrayOf(
            0xe8.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0x31, 0xc0.toByte(),
            0xc3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, mainCode, align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = mainCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "helper", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val helperCode = byteArrayOf(
            0xb8.toByte(), 0x2a, 0x00, 0x00, 0x00,
            0xc3.toByte(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, helperCode, align = 16),
            ),
            symbols = listOf(
                Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfLinker(sharedLibs = emptyList()).link(listOf(obj1, obj2))
        val buf = le(binary)

        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))

        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000)
    }

    @Test
    fun `resolves cross-object symbol references`() {
        val mainCode = byteArrayOf(
            0xe8.toByte(), 0x00, 0x00, 0x00, 0x00,
            0xc3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, mainCode, align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = mainCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfLinker().link(listOf(obj))
        assertTrue(binary.size > 0)

        val buf = le(binary)
        val entry = readU64(buf, 24).toInt()
        val fileOffset = entry - 0x400000
        val patchedDisp = readU32(buf, fileOffset + 1)
        assertNotEquals(0, patchedDisp, "Call displacement should be patched")
    }
}

package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfTlsSegmentTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun findPhdr(buf: ByteBuffer, phoff: Int, phnum: Int, type: Int): Int {
        for (i in 0 until phnum) {
            val off = phoff + i * Elf.PHDR64_SIZE
            if (readU32(buf, off) == type) return off
        }
        return -1
    }

    private fun makeObjWithTdata(tdataBytes: ByteArray, tdataAlign: Int = 4): ObjectFile {
        val code = byteArrayOf(0xC3.toByte()) // ret
        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".tdata", SectionKind.TDATA, tdataBytes, align = tdataAlign),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    private fun makeObjWithoutTdata(): ObjectFile {
        val code = byteArrayOf(0xC3.toByte())
        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    @Test
    fun ptTlsPresentWhenTdataExists() {
        val obj = makeObjWithTdata(ByteArray(4) { 0x42 })
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0, "PT_TLS program header should be present when .tdata exists")
    }

    @Test
    fun ptTlsAbsentWhenNoTdata() {
        val obj = makeObjWithoutTdata()
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertEquals(-1, tlsOff, "PT_TLS should not be present when there is no .tdata")
    }

    @Test
    fun ptTlsHasCorrectFileSize() {
        val tdataContent = ByteArray(32) { i -> i.toByte() }
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val filesz = readU64(buf, tlsOff + 32)
        assertEquals(32L, filesz, "PT_TLS filesz should match .tdata size")
    }

    @Test
    fun ptTlsHasCorrectMemSize() {
        val tdataContent = ByteArray(16) { 0xFF.toByte() }
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val memsz = readU64(buf, tlsOff + 40)
        assertTrue(memsz >= 16, "PT_TLS memsz should be >= tdata size")
    }

    @Test
    fun ptTlsHasReadableFlag() {
        val obj = makeObjWithTdata(ByteArray(8))
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val flags = readU32(buf, tlsOff + 4)
        assertTrue(flags and ElfSegmentFlags.R != 0, "PT_TLS should have R flag set")
    }

    @Test
    fun ptTlsHasCorrectAlignment() {
        val obj = makeObjWithTdata(ByteArray(8), tdataAlign = 8)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val align = readU64(buf, tlsOff + 48)
        assertEquals(8L, align, "PT_TLS alignment should match tdata alignment")
    }

    @Test
    fun phdrCountIncreasesWithTdata() {
        val objNo = makeObjWithoutTdata()
        val objYes = makeObjWithTdata(ByteArray(4))

        val binNo = ElfStaticLinker().link(listOf(objNo))
        val binYes = ElfStaticLinker().link(listOf(objYes))

        val phNumNo = readU16(le(binNo), 56)
        val phNumYes = readU16(le(binYes), 56)
        assertEquals(phNumNo + 1, phNumYes, "Should have one extra phdr for PT_TLS")
    }

    @Test
    fun ptTlsVaddrIsNonZero() {
        val obj = makeObjWithTdata(ByteArray(4) { 0x11 })
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val vaddr = readU64(buf, tlsOff + 16)
        assertTrue(vaddr > 0, "PT_TLS vaddr should be non-zero")
    }

    @Test
    fun ptTlsOffsetPointsToTdataContent() {
        val tdataContent = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val fileOff = readU64(buf, tlsOff + 8).toInt()
        assertTrue(fileOff > 0 && fileOff + 4 <= binary.size, "PT_TLS offset should be valid")
        assertEquals(0x11.toByte(), binary[fileOff])
        assertEquals(0x22.toByte(), binary[fileOff + 1])
        assertEquals(0x33.toByte(), binary[fileOff + 2])
        assertEquals(0x44.toByte(), binary[fileOff + 3])
    }

    @Test
    fun elfSegmentTypeFromCode() {
        assertEquals(ElfSegmentType.TLS, ElfSegmentType.fromCode(7))
    }

    @Test
    fun multipleTdataSectionsMerge() {
        val code1 = byteArrayOf(0xC3.toByte())
        val code2 = byteArrayOf(0xC3.toByte())
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code1, align = 16),
                Section(".tdata", SectionKind.TDATA, ByteArray(4) { 0x11 }, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code2, align = 16),
                Section(".tdata", SectionKind.TDATA, ByteArray(8) { 0x22 }, align = 8),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0, "PT_TLS should be present for merged tdata")

        val filesz = readU64(buf, tlsOff + 32)
        assertTrue(filesz >= 12, "Merged tdata should be at least 12 bytes (4 + padding + 8)")
    }

    @Test
    fun ptTlsTypeCodeIs7() {
        assertEquals(7, ElfSegmentType.TLS.code)
    }
}

package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOFatWriterTest {

    private fun buildMiniMachO64(cpuType: Int, cpuSubtype: Int = 0): ByteArray {
        val buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0, MachO.MH_MAGIC_64.toInt())
        buf.putInt(4, cpuType)
        buf.putInt(8, cpuSubtype)
        buf.putInt(12, MachO.MH_OBJECT)
        buf.putInt(16, 0) // ncmds
        buf.putInt(20, 0) // sizeofcmds
        buf.putInt(24, 0) // flags
        buf.putInt(28, 0) // reserved
        return buf.array()
    }

    @Test
    fun writeSingleArch() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64, MachO.CPU_SUBTYPE_ALL)
        val fat = MachOFatWriter.write(listOf(x86))

        assertTrue(MachOFatBinary.isFat(fat))
        val parsed = MachOFatBinary.read(fat)
        assertEquals(1, parsed.arches.size)
        assertEquals(MachO.CPU_TYPE_X86_64, parsed.arches[0].cpuType)
        assertEquals(MachO.CPU_SUBTYPE_ALL, parsed.arches[0].cpuSubtype)
    }

    @Test
    fun writeDualArch() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64, MachO.CPU_SUBTYPE_ALL)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64, MachO.CPU_SUBTYPE_ARM64_ALL)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        assertTrue(MachOFatBinary.isFat(fat))
        val parsed = MachOFatBinary.read(fat)
        assertEquals(2, parsed.arches.size)
        assertEquals("x86_64", parsed.arches[0].cpuTypeName)
        assertEquals("arm64", parsed.arches[1].cpuTypeName)
    }

    @Test
    fun roundTripPreservesSliceContent() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64, MachO.CPU_SUBTYPE_ALL)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64, MachO.CPU_SUBTYPE_ARM64_ALL)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        val parsed = MachOFatBinary.read(fat)

        val x86Slice = parsed.sliceFor(MachO.CPU_TYPE_X86_64)!!
        val armSlice = parsed.sliceFor(MachO.CPU_TYPE_ARM64)!!

        assertArrayEquals(x86, x86Slice)
        assertArrayEquals(arm, armSlice)
    }

    @Test
    fun slicesArePageAligned() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        val parsed = MachOFatBinary.read(fat)
        for (arch in parsed.arches) {
            assertEquals(0L, arch.offset % 4096, "Slice offset should be page-aligned")
        }
    }

    @Test
    fun sliceSizesMatchOriginals() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        val parsed = MachOFatBinary.read(fat)
        assertEquals(x86.size.toLong(), parsed.arches[0].size)
        assertEquals(arm.size.toLong(), parsed.arches[1].size)
    }

    @Test
    fun extractedSlicesAreValidMachO() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        val parsed = MachOFatBinary.read(fat)
        val x86File = parsed.readSlice(0)
        assertEquals(MachO.CPU_TYPE_X86_64, x86File.header.cpuType)

        val armFile = parsed.readSlice(1)
        assertEquals(MachO.CPU_TYPE_ARM64, armFile.header.cpuType)
    }

    @Test
    fun rejectsDuplicateCpuTypes() {
        val x86a = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val x86b = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        assertThrows<IllegalArgumentException> {
            MachOFatWriter.write(listOf(x86a, x86b))
        }
    }

    @Test
    fun rejectsEmptySliceList() {
        assertThrows<IllegalArgumentException> {
            MachOFatWriter.write(emptyList())
        }
    }

    @Test
    fun rejectsInvalidMachO() {
        assertThrows<IllegalArgumentException> {
            MachOFatWriter.write(listOf(ByteArray(256)))
        }
    }

    @Test
    fun fatMagicIsBigEndian() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val fat = MachOFatWriter.write(listOf(x86))

        // FAT_MAGIC = 0xCAFEBABE, big-endian → bytes CA FE BA BE
        assertEquals(0xCA.toByte(), fat[0])
        assertEquals(0xFE.toByte(), fat[1])
        assertEquals(0xBA.toByte(), fat[2])
        assertEquals(0xBE.toByte(), fat[3])
    }

    @Test
    fun archCountIsCorrectInHeader() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val arm = buildMiniMachO64(MachO.CPU_TYPE_ARM64)
        val fat = MachOFatWriter.write(listOf(x86, arm))

        val buf = ByteBuffer.wrap(fat).order(ByteOrder.BIG_ENDIAN)
        assertEquals(2, buf.getInt(4))
    }

    @Test
    fun isMachOOrFatAcceptsWrittenBinary() {
        val x86 = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val fat = MachOFatWriter.write(listOf(x86))
        assertTrue(MachO.isMachOOrFat(fat))
    }
}

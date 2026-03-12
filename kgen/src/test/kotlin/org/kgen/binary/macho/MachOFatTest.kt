package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOFatTest {

    private fun buildMiniMachO64(cpuType: Int, cpuSubtype: Int = 0): ByteArray {
        val buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        // Mach-O 64-bit little-endian: on disk bytes are CF FA ED FE
        // Writing MH_MAGIC_64 via LE putInt gives correct on-disk bytes
        buf.putInt(0, MachO.MH_MAGIC_64.toInt())
        buf.putInt(4, cpuType)
        buf.putInt(8, cpuSubtype)
        buf.putInt(12, MachO.MH_OBJECT) // file type
        buf.putInt(16, 0) // ncmds
        buf.putInt(20, 0) // sizeofcmds
        buf.putInt(24, 0) // flags
        buf.putInt(28, 0) // reserved
        return buf.array()
    }

    private fun buildFat32(arches: List<Triple<Int, Int, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        // FAT header (big-endian)
        val headerBuf = ByteBuffer.allocate(8 + arches.size * 20).order(ByteOrder.BIG_ENDIAN)
        headerBuf.putInt(0, MachOFatBinary.FAT_MAGIC.toInt())
        headerBuf.putInt(4, arches.size)

        // Calculate offsets: header + arch entries, aligned
        var dataOffset = 8 + arches.size * 20
        dataOffset = (dataOffset + 0xFFF) and 0xFFFFF000.toInt() // page-align

        val offsets = mutableListOf<Int>()
        for ((_, _, data) in arches) {
            offsets.add(dataOffset)
            dataOffset += data.size
            dataOffset = (dataOffset + 0xFFF) and 0xFFFFF000.toInt()
        }

        for ((i, entry) in arches.withIndex()) {
            val (cpuType, cpuSubtype, data) = entry
            val base = 8 + i * 20
            headerBuf.putInt(base, cpuType)
            headerBuf.putInt(base + 4, cpuSubtype)
            headerBuf.putInt(base + 8, offsets[i])
            headerBuf.putInt(base + 12, data.size)
            headerBuf.putInt(base + 16, 12) // align = 2^12 = 4096
        }

        baos.write(headerBuf.array())
        // Pad and write each slice
        for ((i, entry) in arches.withIndex()) {
            val padSize = offsets[i] - baos.size()
            if (padSize > 0) baos.write(ByteArray(padSize))
            baos.write(entry.third)
        }

        return baos.toByteArray()
    }

    @Test
    fun `isFat detects FAT magic`() {
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, buildMiniMachO64(MachO.CPU_TYPE_X86_64)),
        ))
        assertTrue(MachOFatBinary.isFat(fat))
    }

    @Test
    fun `isFat rejects non-FAT`() {
        assertFalse(MachOFatBinary.isFat(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
        assertFalse(MachOFatBinary.isFat(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `isFat rejects regular Mach-O`() {
        val macho = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        assertFalse(MachOFatBinary.isFat(macho))
    }

    @Test
    fun `reads single-arch FAT binary`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
        ))

        val fatBinary = MachOFatBinary.read(fat)
        assertEquals(1, fatBinary.arches.size)
        assertEquals(MachO.CPU_TYPE_X86_64, fatBinary.arches[0].cpuType)
        assertEquals("x86_64", fatBinary.arches[0].cpuTypeName)
    }

    @Test
    fun `reads dual-arch FAT binary`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val armSlice = buildMiniMachO64(MachO.CPU_TYPE_ARM64, 0)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
            Triple(MachO.CPU_TYPE_ARM64, 0, armSlice),
        ))

        val fatBinary = MachOFatBinary.read(fat)
        assertEquals(2, fatBinary.arches.size)
        assertEquals("x86_64", fatBinary.arches[0].cpuTypeName)
        assertEquals("arm64", fatBinary.arches[1].cpuTypeName)
    }

    @Test
    fun `extracts correct slice by index`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val armSlice = buildMiniMachO64(MachO.CPU_TYPE_ARM64, 0)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
            Triple(MachO.CPU_TYPE_ARM64, 0, armSlice),
        ))

        val fatBinary = MachOFatBinary.read(fat)
        val slice0 = fatBinary.slice(0)
        assertTrue(MachO.isMachO(slice0))

        val buf = ByteBuffer.wrap(slice0).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(MachO.CPU_TYPE_X86_64, buf.getInt(4))
    }

    @Test
    fun `extracts slice by CPU type`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val armSlice = buildMiniMachO64(MachO.CPU_TYPE_ARM64, 0)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
            Triple(MachO.CPU_TYPE_ARM64, 0, armSlice),
        ))

        val fatBinary = MachOFatBinary.read(fat)

        val armBytes = fatBinary.sliceFor(MachO.CPU_TYPE_ARM64)
        assertNotNull(armBytes)
        val buf = ByteBuffer.wrap(armBytes!!).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(MachO.CPU_TYPE_ARM64, buf.getInt(4))

        assertNull(fatBinary.sliceFor(0x00000012)) // ppc — not present
    }

    @Test
    fun `readSlice parses embedded Mach-O`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        assertTrue(MachO.isMachO(x86Slice))
        // Verify standalone parsing works
        val standalone = MachOReader.read(x86Slice)
        assertEquals(MachO.CPU_TYPE_X86_64, standalone.header.cpuType)

        // Build FAT with this slice
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
        ))
        val fatBinary = MachOFatBinary.read(fat)
        val macho = fatBinary.readSlice(0)
        assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
        assertTrue(macho.header.is64Bit)
    }

    @Test
    fun `readSliceFor returns null for missing arch`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
        ))

        val fatBinary = MachOFatBinary.read(fat)
        assertNull(fatBinary.readSliceFor(MachO.CPU_TYPE_ARM64))
    }

    @Test
    fun `isMachOOrFat accepts both formats`() {
        val macho = buildMiniMachO64(MachO.CPU_TYPE_X86_64)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, buildMiniMachO64(MachO.CPU_TYPE_X86_64)),
        ))

        assertTrue(MachO.isMachOOrFat(macho))
        assertTrue(MachO.isMachOOrFat(fat))
        assertFalse(MachO.isMachOOrFat(byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun `arch size and offset are correct`() {
        val x86Slice = buildMiniMachO64(MachO.CPU_TYPE_X86_64, 3)
        val fat = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, x86Slice),
        ))

        val fatBinary = MachOFatBinary.read(fat)
        val arch = fatBinary.arches[0]
        assertEquals(x86Slice.size.toLong(), arch.size)
        assertTrue(arch.offset >= 28) // at least past the header
    }

    @Test
    fun `is64Bit flag is correct`() {
        val fat32 = buildFat32(listOf(
            Triple(MachO.CPU_TYPE_X86_64, 3, buildMiniMachO64(MachO.CPU_TYPE_X86_64)),
        ))
        val fatBinary = MachOFatBinary.read(fat32)
        assertFalse(fatBinary.is64Bit) // 32-bit FAT header
    }
}

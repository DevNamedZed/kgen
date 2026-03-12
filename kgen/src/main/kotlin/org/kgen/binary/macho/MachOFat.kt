package org.kgen.binary.macho

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FAT/universal Mach-O binary — contains multiple architecture slices.
 *
 * Format:
 * - Magic: 0xCAFEBABE (32-bit FAT) or 0xCAFEBABF (64-bit FAT)
 * - nfat_arch: number of architecture slices
 * - fat_arch entries: cpuType, cpuSubtype, offset, size, align
 * - Each slice is a complete Mach-O binary at the given offset
 */
data class MachOFatBinary(
    val magic: UInt,
    val arches: List<MachOFatArch>,
    val raw: ByteArray,
) {
    val is64Bit: Boolean get() = magic == FAT_MAGIC_64 || magic == FAT_CIGAM_64

    fun slice(index: Int): ByteArray {
        val arch = arches[index]
        return raw.copyOfRange(arch.offset.toInt(), (arch.offset + arch.size).toInt())
    }

    fun sliceFor(cpuType: Int): ByteArray? {
        val arch = arches.firstOrNull { it.cpuType == cpuType } ?: return null
        return raw.copyOfRange(arch.offset.toInt(), (arch.offset + arch.size).toInt())
    }

    fun readSlice(index: Int): MachOFile = MachOReader.read(slice(index))

    fun readSliceFor(cpuType: Int): MachOFile? {
        val bytes = sliceFor(cpuType) ?: return null
        return MachOReader.read(bytes)
    }

    companion object {
        const val FAT_MAGIC: UInt = 0xCAFEBABEu
        const val FAT_CIGAM: UInt = 0xBEBAFECAu
        const val FAT_MAGIC_64: UInt = 0xCAFEBABFu
        const val FAT_CIGAM_64: UInt = 0xBFBAFECAu

        @JvmStatic
        fun isFat(bytes: ByteArray): Boolean {
            if (bytes.size < 8) return false
            val magic = readMagic(bytes)
            return magic == FAT_MAGIC || magic == FAT_CIGAM ||
                   magic == FAT_MAGIC_64 || magic == FAT_CIGAM_64
        }

        @JvmStatic
        fun read(bytes: ByteArray): MachOFatBinary {
            val magic = readMagic(bytes)
            require(isFat(bytes)) { "Not a FAT Mach-O binary" }

            val order = if (magic == FAT_CIGAM || magic == FAT_CIGAM_64)
                ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            val buf = ByteBuffer.wrap(bytes).order(order)

            val nfat = buf.getInt(4)
            require(nfat in 1..255) { "Unreasonable number of FAT arches: $nfat" }

            val is64 = magic == FAT_MAGIC_64 || magic == FAT_CIGAM_64
            val archEntrySize = if (is64) 32 else 20
            val arches = mutableListOf<MachOFatArch>()

            for (i in 0 until nfat) {
                val off = 8 + i * archEntrySize
                if (is64) {
                    arches.add(MachOFatArch(
                        cpuType = buf.getInt(off),
                        cpuSubtype = buf.getInt(off + 4),
                        offset = buf.getLong(off + 8),
                        size = buf.getLong(off + 16),
                        align = buf.getInt(off + 24),
                    ))
                } else {
                    arches.add(MachOFatArch(
                        cpuType = buf.getInt(off),
                        cpuSubtype = buf.getInt(off + 4),
                        offset = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL,
                        size = buf.getInt(off + 12).toLong() and 0xFFFFFFFFL,
                        align = buf.getInt(off + 16),
                    ))
                }
            }

            return MachOFatBinary(magic, arches, bytes)
        }

        private fun readMagic(bytes: ByteArray): UInt =
            ((bytes[0].toInt() and 0xFF).toUInt() shl 24) or
            ((bytes[1].toInt() and 0xFF).toUInt() shl 16) or
            ((bytes[2].toInt() and 0xFF).toUInt() shl 8) or
            (bytes[3].toInt() and 0xFF).toUInt()
    }
}

data class MachOFatArch(
    val cpuType: Int,
    val cpuSubtype: Int,
    val offset: Long,
    val size: Long,
    val align: Int,
) {
    val cpuTypeName: String get() = when (cpuType) {
        MachO.CPU_TYPE_X86_64 -> "x86_64"
        MachO.CPU_TYPE_ARM64 -> "arm64"
        MachO.CPU_TYPE_ARM -> "arm"
        0x00000007 -> "i386"
        0x00000012 -> "ppc"
        0x01000012 -> "ppc64"
        else -> "unknown(0x${cpuType.toString(16)})"
    }
}

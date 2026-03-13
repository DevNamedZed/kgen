package org.kgen.binary.macho

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Combines multiple single-architecture Mach-O binaries into a universal (FAT) binary.
 *
 * Each input slice must be a valid Mach-O binary. The writer reads the CPU type and
 * subtype from each slice's Mach-O header. Slices are page-aligned (4096 bytes) in the
 * output, matching Apple's `lipo` behavior.
 *
 * ```java
 * byte[] x86 = MachOLinker.link(x86Objects);
 * byte[] arm = MachOLinker.link(armObjects);
 * byte[] fat = MachOFatWriter.write(List.of(x86, arm));
 * ```
 */
class MachOFatWriter private constructor(private val slices: List<ByteArray>) {

    /**
     * Describes a single architecture slice for the FAT header.
     */
    private data class SliceInfo(
        val cpuType: Int,
        val cpuSubtype: Int,
        val offset: Long,
        val size: Long,
        val alignPower: Int,
    )

    private fun write(): ByteArray {
        require(slices.isNotEmpty()) { "At least one Mach-O slice is required" }
        require(slices.size <= 255) { "Too many slices: ${slices.size} (max 255)" }

        val infos = mutableListOf<SliceInfo>()

        // Read CPU type/subtype from each slice's Mach-O header
        for (slice in slices) {
            require(slice.size >= 16) { "Slice too small to be a valid Mach-O" }
            val (cpuType, cpuSubtype) = readCpuInfo(slice)
            infos.add(SliceInfo(cpuType, cpuSubtype, 0, slice.size.toLong(), PAGE_ALIGN_POWER))
        }

        // Check for duplicate CPU types
        val types = infos.map { it.cpuType }
        require(types.size == types.toSet().size) { "Duplicate CPU types in slices" }

        // Compute offsets: header + arch entries, then each slice at page boundary
        val headerSize = 8 + slices.size * FAT_ARCH_SIZE
        var offset = alignUp(headerSize.toLong(), PAGE_SIZE)

        val finalInfos = infos.mapIndexed { i, info ->
            val sliceOffset = offset
            offset = alignUp(offset + slices[i].size, PAGE_SIZE)
            info.copy(offset = sliceOffset)
        }

        // Write the FAT binary
        val out = ByteArrayOutputStream()
        val headerBuf = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)

        // FAT magic (always big-endian)
        headerBuf.putInt(MachOFatBinary.FAT_MAGIC.toInt())
        headerBuf.putInt(slices.size)

        // Fat arch entries (32-bit format)
        for (info in finalInfos) {
            headerBuf.putInt(info.cpuType)
            headerBuf.putInt(info.cpuSubtype)
            headerBuf.putInt(info.offset.toInt())
            headerBuf.putInt(info.size.toInt())
            headerBuf.putInt(info.alignPower)
        }

        out.write(headerBuf.array())

        // Pad and write each slice
        for ((i, slice) in slices.withIndex()) {
            val targetOffset = finalInfos[i].offset.toInt()
            val padding = targetOffset - out.size()
            if (padding > 0) {
                out.write(ByteArray(padding))
            }
            out.write(slice)
        }

        return out.toByteArray()
    }

    companion object {
        private const val PAGE_ALIGN_POWER = 12 // 2^12 = 4096
        private const val PAGE_SIZE = 4096L
        private const val FAT_ARCH_SIZE = 20 // 32-bit fat_arch entry size

        /**
         * Combines multiple Mach-O binaries into a universal FAT binary.
         *
         * @param slices List of single-architecture Mach-O binaries.
         * @return The combined FAT Mach-O binary.
         * @throws IllegalArgumentException if slices is empty, contains duplicates, or has invalid Mach-Os.
         */
        @JvmStatic
        fun write(slices: List<ByteArray>): ByteArray = MachOFatWriter(slices).write()

        private fun readCpuInfo(machO: ByteArray): Pair<Int, Int> {
            // Read magic as big-endian (ByteBuffer default) to determine file endianness
            val buf = ByteBuffer.wrap(machO)
            val magic = buf.int
            // CIGAM variants mean the file is byte-swapped relative to our read order
            val order = when (magic.toUInt()) {
                MachO.MH_MAGIC_64, MachO.MH_MAGIC_32 -> ByteOrder.BIG_ENDIAN
                MachO.MH_CIGAM_64, MachO.MH_CIGAM_32 -> ByteOrder.LITTLE_ENDIAN
                else -> throw IllegalArgumentException(
                    "Not a valid Mach-O binary (magic: 0x${magic.toUInt().toString(16)})")
            }
            buf.order(order)
            val cpuType = buf.getInt(4)
            val cpuSubtype = buf.getInt(8)
            return cpuType to cpuSubtype
        }

        private fun alignUp(value: Long, alignment: Long): Long {
            val mask = alignment - 1
            return (value + mask) and mask.inv()
        }
    }
}

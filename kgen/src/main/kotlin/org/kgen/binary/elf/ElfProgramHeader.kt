package org.kgen.binary.elf

data class ElfProgramHeader(
    val type: ElfSegmentType?,
    val flags: Int,
    val offset: Long,
    val virtualAddress: Long,
    val physicalAddress: Long,
    val fileSize: Long,
    val memorySize: Long,
    val alignment: Long,
) {
    val isReadable: Boolean get() = flags and ElfSegmentFlags.R != 0
    val isWritable: Boolean get() = flags and ElfSegmentFlags.W != 0
    val isExecutable: Boolean get() = flags and ElfSegmentFlags.X != 0
}

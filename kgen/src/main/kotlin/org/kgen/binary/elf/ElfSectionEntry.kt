package org.kgen.binary.elf

data class ElfSectionEntry(
    val index: Int,
    val name: String,
    val type: ElfSectionType?,
    val flags: Long,
    val address: Long,
    val offset: Long,
    val size: Long,
    val link: Int,
    val info: Int,
    val alignment: Long,
    val entrySize: Long,
    val data: ByteArray,
) {
    val isAllocated: Boolean get() = flags and ElfSectionFlags.ALLOC != 0L
    val isWritable: Boolean get() = flags and ElfSectionFlags.WRITE != 0L
    val isExecutable: Boolean get() = flags and ElfSectionFlags.EXECINSTR != 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ElfSectionEntry) return false
        return index == other.index && name == other.name && type == other.type &&
            flags == other.flags && address == other.address && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = index * 31 + name.hashCode()
}

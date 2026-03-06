package org.kgen.binary.elf

data class ElfRelocationEntry(
    val offset: Long,
    val symbolIndex: Int,
    val symbolName: String,
    val type: Int,
    val addend: Long,
    val sectionName: String?,
    val hasAddend: Boolean,
)

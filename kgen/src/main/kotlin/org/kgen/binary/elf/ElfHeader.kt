package org.kgen.binary.elf

data class ElfHeader(
    val elfClass: ElfClass,
    val dataEncoding: ElfData,
    val osAbi: Int,
    val type: ElfObjectType?,
    val machine: ElfMachine?,
    val entryPoint: Long,
    val programHeaderOffset: Long,
    val sectionHeaderOffset: Long,
    val flags: Int,
    val programHeaderCount: Int,
    val sectionHeaderCount: Int,
    val sectionNameStringTableIndex: Int,
)

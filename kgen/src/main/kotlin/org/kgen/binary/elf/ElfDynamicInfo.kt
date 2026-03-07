package org.kgen.binary.elf

data class ElfDynamicInfo(
    val neededLibraries: List<String>,
    val soName: String?,
    val rpath: List<String>,
    val runpath: List<String>,
    val entries: List<ElfDynamicEntry>,
) {
    val hasBindNow: Boolean get() = entries.any { it.tag == ElfDynamicTag.BIND_NOW }
}

data class ElfDynamicEntry(
    val tag: ElfDynamicTag?,
    val tagCode: Long,
    val value: Long,
)

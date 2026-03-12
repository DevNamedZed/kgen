package org.kgen.binary.elf

enum class ElfSegmentType(val code: Int) {
    LOAD(1),
    DYNAMIC(2),
    INTERP(3),
    NOTE(4),
    PHDR(6),
    TLS(7),
    GNU_STACK(0x6474e551),
    GNU_RELRO(0x6474e552);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

object ElfSegmentFlags {
    const val X = 1
    const val W = 2
    const val R = 4
}

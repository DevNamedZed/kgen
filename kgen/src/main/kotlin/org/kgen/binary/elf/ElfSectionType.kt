package org.kgen.binary.elf

enum class ElfSectionType(val code: Int) {
    NULL(0),
    PROGBITS(1),
    SYMTAB(2),
    STRTAB(3),
    RELA(4),
    HASH(5),
    DYNAMIC(6),
    NOTE(7),
    NOBITS(8),
    REL(9),
    DYNSYM(11),
    INIT_ARRAY(14),
    FINI_ARRAY(15),
    PREINIT_ARRAY(16),
    GROUP(17),
    GNU_HASH(0x6ffffff6);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

object ElfSectionFlags {
    const val WRITE: Long = 0x1
    const val ALLOC: Long = 0x2
    const val EXECINSTR: Long = 0x4
    const val INFO_LINK: Long = 0x40
}

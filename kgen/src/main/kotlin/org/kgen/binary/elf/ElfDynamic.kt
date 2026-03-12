package org.kgen.binary.elf

enum class ElfDynamicTag(val code: Long) {
    NULL(0),
    NEEDED(1),
    PLTRELSZ(2),
    PLTGOT(3),
    STRTAB(5),
    SYMTAB(6),
    RELA(7),
    RELASZ(8),
    RELAENT(9),
    STRSZ(10),
    SYMENT(11),
    INIT(12),
    FINI(13),
    SONAME(14),
    RPATH(15),
    SYMBOLIC(16),
    PLTREL(20),
    JMPREL(23),
    BIND_NOW(24),
    INIT_ARRAY(25),
    FINI_ARRAY(26),
    INIT_ARRAYSZ(27),
    FINI_ARRAYSZ(28),
    RUNPATH(29),
    FLAGS(30),
    FLAGS_1(0x6ffffffb);

    companion object {
        @JvmStatic fun fromCode(code: Long) = entries.firstOrNull { it.code == code }
    }
}

object ElfDynFlags {
    const val SYMBOLIC: Long = 0x2
    const val TEXTREL: Long = 0x4
    const val BIND_NOW: Long = 0x8
    const val STATIC_TLS: Long = 0x10
}

object ElfDynFlags1 {
    const val NOW: Long = 0x1
    const val NODELETE: Long = 0x8
    const val PIE: Long = 0x08000000
}

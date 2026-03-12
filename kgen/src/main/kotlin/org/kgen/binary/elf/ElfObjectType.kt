package org.kgen.binary.elf

enum class ElfObjectType(val code: Int) {
    REL(1),
    EXEC(2),
    DYN(3);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

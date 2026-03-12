package org.kgen.binary.elf

enum class ElfClass(val code: Int) {
    ELF32(1),
    ELF64(2);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

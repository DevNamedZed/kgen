package org.kgen.binary.elf

enum class ElfMachine(val code: Int) {
    X86_64(0x3E),
    AARCH64(0xB7),
    RISCV(0xF3);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

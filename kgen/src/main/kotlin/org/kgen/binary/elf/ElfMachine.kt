package org.kgen.binary.elf

enum class ElfMachine(val code: Int) {
    SPARC(0x02),
    I386(0x03),
    MIPS(0x08),
    PPC(0x14),
    PPC64(0x15),
    S390(0x16),
    ARM(0x28),
    SPARC64(0x2B),
    X86_64(0x3E),
    AARCH64(0xB7),
    RISCV(0xF3);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

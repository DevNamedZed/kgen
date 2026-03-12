package org.kgen.binary.elf

enum class ElfData(val code: Int) {
    LSB(1),
    MSB(2);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

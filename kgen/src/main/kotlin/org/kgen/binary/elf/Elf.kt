package org.kgen.binary.elf

object Elf {
    val MAGIC = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    const val VERSION = 1

    const val EHDR64_SIZE = 64
    const val SHDR64_SIZE = 64
    const val PHDR64_SIZE = 56
    const val SYM64_SIZE = 24
    const val RELA64_SIZE = 24
    const val REL64_SIZE = 16

    const val EHDR32_SIZE = 52
    const val SHDR32_SIZE = 40
    const val PHDR32_SIZE = 32
    const val SYM32_SIZE = 16
    const val RELA32_SIZE = 12
    const val REL32_SIZE = 8

    const val SHN_UNDEF = 0
    const val SHN_ABS = 0xFFF1

    fun stInfo(binding: ElfSymbolBinding, type: ElfSymbolType): Int =
        (binding.code shl 4) or (type.code and 0xf)
}

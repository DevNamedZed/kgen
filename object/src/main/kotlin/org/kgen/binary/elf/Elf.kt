package org.kgen.binary.elf

object Elf {
    val MAGIC = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    const val VERSION = 1

    const val EHDR64_SIZE = 64
    const val SHDR64_SIZE = 64
    const val PHDR64_SIZE = 56
    const val SYM64_SIZE = 24
    const val RELA64_SIZE = 24

    const val SHN_UNDEF = 0
    const val SHN_ABS = 0xFFF1

    fun stInfo(binding: ElfSymbolBinding, type: ElfSymbolType): Int =
        (binding.code shl 4) or (type.code and 0xf)
}

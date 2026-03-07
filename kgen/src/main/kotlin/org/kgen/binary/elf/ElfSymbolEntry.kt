package org.kgen.binary.elf

data class ElfSymbolEntry(
    val name: String,
    val value: Long,
    val size: Long,
    val binding: ElfSymbolBinding?,
    val type: ElfSymbolType?,
    val visibility: ElfSymbolVisibility?,
    val sectionIndex: Int,
    val sectionName: String?,
) {
    val isGlobal: Boolean get() = binding == ElfSymbolBinding.GLOBAL
    val isLocal: Boolean get() = binding == ElfSymbolBinding.LOCAL
    val isWeak: Boolean get() = binding == ElfSymbolBinding.WEAK
    val isUndefined: Boolean get() = sectionIndex == Elf.SHN_UNDEF
    val isAbsolute: Boolean get() = sectionIndex == Elf.SHN_ABS
    val isFunction: Boolean get() = type == ElfSymbolType.FUNC
    val isObject: Boolean get() = type == ElfSymbolType.OBJECT
}

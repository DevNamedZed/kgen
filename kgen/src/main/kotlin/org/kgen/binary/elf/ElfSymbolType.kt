package org.kgen.binary.elf

enum class ElfSymbolBinding(val code: Int) {
    LOCAL(0),
    GLOBAL(1),
    WEAK(2);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class ElfSymbolType(val code: Int) {
    NOTYPE(0),
    OBJECT(1),
    FUNC(2),
    SECTION(3),
    FILE(4),
    COMMON(5),
    TLS(6),
    GNU_IFUNC(10);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

enum class ElfSymbolVisibility(val code: Int) {
    DEFAULT(0),
    HIDDEN(2),
    PROTECTED(3);

    companion object {
        @JvmStatic fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

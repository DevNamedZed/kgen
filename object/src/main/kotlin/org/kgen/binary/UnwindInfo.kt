package org.kgen.binary

// Unwind / exception handling frame info

data class UnwindEntry(
    val functionSymbol: String,
    val startOffset: Long,
    val endOffset: Long,
    val kind: UnwindKind,
    val data: ByteArray,                 // raw unwind data
    val personalityFunction: String? = null,
    val lsdaPointer: Long? = null,       // language-specific data area
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnwindEntry) return false
        return functionSymbol == other.functionSymbol && startOffset == other.startOffset && kind == other.kind
    }
    override fun hashCode(): Int = functionSymbol.hashCode() * 31 + startOffset.hashCode()
}

enum class UnwindKind {
    // ELF / DWARF
    DWARF_CFI,             // .eh_frame / .debug_frame — Call Frame Information
    DWARF_FDE,             // Frame Description Entry
    DWARF_CIE,             // Common Information Entry
    GCC_EXCEPT_TABLE,      // .gcc_except_table — LSDA

    // PE / Windows
    WIN_XDATA,             // .xdata — unwind data (x86-64)
    WIN_PDATA,             // .pdata — function table entry
    WIN_SEH,               // Structured Exception Handling info

    // Mach-O
    MACHO_COMPACT,         // __compact_unwind
    MACHO_DWARF,           // __eh_frame (DWARF in Mach-O)

    // ARM
    ARM_EXIDX,             // .ARM.exidx — exception index
    ARM_EXTAB,             // .ARM.extab — exception table
    AARCH64_DWARF,         // AArch64 uses DWARF
}

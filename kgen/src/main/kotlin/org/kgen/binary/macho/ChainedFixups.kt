package org.kgen.binary.macho

/**
 * Parsed chained fixups from LC_DYLD_CHAINED_FIXUPS.
 * Contains imports (bind targets) and per-segment chain start info.
 */
data class ChainedFixups(
    val fixupsVersion: Int,
    val importsFormat: ChainedImportFormat,
    val symbolsFormat: Int,
    val imports: List<ChainedFixupImport>,
    val segments: List<ChainedFixupSegment>,
)

enum class ChainedImportFormat(val code: Int) {
    DYLD_CHAINED_IMPORT(1),
    DYLD_CHAINED_IMPORT_ADDEND(2),
    DYLD_CHAINED_IMPORT_ADDEND64(3);

    companion object {
        fun fromCode(code: Int): ChainedImportFormat =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown chained import format: $code")
    }
}

enum class ChainedPointerFormat(val code: Int) {
    DYLD_CHAINED_PTR_ARM64E(1),
    DYLD_CHAINED_PTR_64(2),
    DYLD_CHAINED_PTR_32(3),
    DYLD_CHAINED_PTR_32_CACHE(4),
    DYLD_CHAINED_PTR_32_FIRMWARE(5),
    DYLD_CHAINED_PTR_64_OFFSET(6),
    DYLD_CHAINED_PTR_ARM64E_KERNEL(7),
    DYLD_CHAINED_PTR_64_KERNEL_CACHE(8),
    DYLD_CHAINED_PTR_ARM64E_USERLAND(9),
    DYLD_CHAINED_PTR_ARM64E_FIRMWARE(10),
    DYLD_CHAINED_PTR_X86_64_KERNEL_CACHE(11),
    DYLD_CHAINED_PTR_ARM64E_USERLAND24(12);

    companion object {
        fun fromCode(code: Int): ChainedPointerFormat =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown chained pointer format: $code")
    }
}

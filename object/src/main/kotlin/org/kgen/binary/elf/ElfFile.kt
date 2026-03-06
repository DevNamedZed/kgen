package org.kgen.binary.elf

/**
 * Structured representation of an ELF64 binary.
 *
 * This is the primary output of [ElfReader]. All headers, sections, segments,
 * symbols, and relocations are accessible as structured data.
 *
 * ```kotlin
 * val elf = ElfReader.read(bytes)
 *
 * // Inspect header
 * println("Type: ${elf.header.type}")
 * println("Machine: ${elf.header.machine}")
 *
 * // Enumerate sections
 * for (section in elf.sections) {
 *     println("${section.name}: ${section.size} bytes at 0x${section.address.toString(16)}")
 * }
 *
 * // Enumerate symbols
 * for (sym in elf.symbols) {
 *     println("${sym.name}: ${sym.binding} ${sym.type}")
 * }
 *
 * // Find dynamic dependencies
 * for (lib in elf.dynamicInfo?.neededLibraries.orEmpty()) {
 *     println("needs: $lib")
 * }
 * ```
 */
data class ElfFile(
    val header: ElfHeader,
    val sections: List<ElfSectionEntry>,
    val segments: List<ElfProgramHeader>,
    val symbols: List<ElfSymbolEntry>,
    val dynamicSymbols: List<ElfSymbolEntry>,
    val relocations: List<ElfRelocationEntry>,
    val dynamicInfo: ElfDynamicInfo?,
) {
    val isExecutable: Boolean get() = header.type == ElfObjectType.EXEC
    val isRelocatable: Boolean get() = header.type == ElfObjectType.REL
    val isSharedObject: Boolean get() = header.type == ElfObjectType.DYN

    fun sectionByName(name: String): ElfSectionEntry? = sections.firstOrNull { it.name == name }
    fun sectionByIndex(index: Int): ElfSectionEntry? = sections.firstOrNull { it.index == index }

    fun symbolsBySection(sectionName: String): List<ElfSymbolEntry> =
        symbols.filter { it.sectionName == sectionName }
}

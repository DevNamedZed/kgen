package org.kgen.binary.pe

import org.kgen.binary.TLSDirectory
import org.kgen.binary.pe.clr.ClrMetadata

/**
 * Structured representation of a PE/COFF binary.
 *
 * This is the primary output of [PeReader]. All headers, sections, directories,
 * and CLR metadata are accessible as structured data for inspection and enumeration.
 *
 * ```kotlin
 * val pe = PeReader.read(bytes)
 *
 * // Inspect COFF header
 * println("Machine: 0x${pe.coffHeader.machine.toString(16)}")
 * println("Sections: ${pe.coffHeader.numberOfSections}")
 *
 * // Enumerate sections
 * for (section in pe.sections) {
 *     println("${section.name}: ${section.virtualSize} bytes at RVA 0x${section.virtualAddress.toString(16)}")
 * }
 *
 * // Enumerate imports
 * for (dir in pe.importDirectories) {
 *     println("DLL: ${dir.name}")
 *     for (entry in dir.entries) {
 *         println("  ${entry.name ?: "ordinal#${entry.ordinal}"}")
 *     }
 * }
 *
 * // Check for CLR metadata
 * if (pe.clrMetadata != null) {
 *     val clr = pe.clrMetadata!!
 *     for (td in clr.tables.typeDefs) {
 *         println("Type: ${clr.strings.get(td.namespace)}.${clr.strings.get(td.name)}")
 *     }
 * }
 * ```
 */
data class PeFile(
    val isPe: Boolean,
    val coffHeader: CoffHeader,
    val optionalHeader: PeOptionalHeader?,
    val sections: List<PeSection>,
    val symbols: List<CoffSymbol>,
    val importDirectories: List<PeImportDirectory>,
    val delayImportDirectories: List<PeImportDirectory>,
    val exportDirectory: PeExportDirectory?,
    val baseRelocations: List<PeBaseRelocation>,
    val clrMetadata: ClrMetadata?,
    val dataDirectories: List<PeDataDirectory>,
    val resources: PeResourceDirectory? = null,
    val tlsDirectory: TLSDirectory? = null,
    val exceptionEntries: List<PeExceptionParser.ParsedEntry> = emptyList(),
    val debugEntries: List<PeDebugEntry> = emptyList(),
    val loadConfig: PeLoadConfig? = null,
) {
    val isExecutable: Boolean get() = coffHeader.characteristics and PeConstants.IMAGE_FILE_EXECUTABLE_IMAGE != 0
    val isDll: Boolean get() = coffHeader.characteristics and PeConstants.IMAGE_FILE_DLL != 0
    val isPe32Plus: Boolean get() = optionalHeader?.magic == PeConstants.PE32PLUS_MAGIC
    val isManagedAssembly: Boolean get() = clrMetadata != null
    val isILOnly: Boolean get() = clrMetadata?.isILOnly == true
    val isMixedMode: Boolean get() = clrMetadata != null && !clrMetadata.isILOnly

    val imageBase: Long get() = optionalHeader?.imageBase ?: 0L
    val entryPointRVA: Int get() = optionalHeader?.entryPointRVA ?: 0

    fun sectionByName(name: String): PeSection? = sections.firstOrNull { it.name == name }
    fun sectionByRVA(rva: Int): PeSection? = sections.firstOrNull {
        rva >= it.virtualAddress && rva < it.virtualAddress + maxOf(it.virtualSize, it.rawDataSize)
    }
}

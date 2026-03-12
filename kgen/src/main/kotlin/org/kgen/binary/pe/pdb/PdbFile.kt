package org.kgen.binary.pe.pdb

import java.util.UUID

/**
 * Structured representation of a PDB (Program Database) file.
 *
 * A PDB file contains debug information for Windows PE/COFF binaries.
 * It uses an MSF (Multi-Stream File) container to organize data into streams:
 * - Stream 1: PDB Info (version, GUID, age, named stream map)
 * - Stream 2: TPI (Type Program Information — all type records)
 * - Stream 3: DBI (Debug Information — modules, section contributions, file info)
 * - Stream 4: IPI (ID Program Information — function IDs, build info)
 * - Additional streams: module symbol/line streams, global/public symbol hash, string table
 *
 * ```kotlin
 * val pdb = PdbReader.read(pdbBytes)
 *
 * // Basic info
 * println("GUID: ${pdb.guid}")
 * println("Age: ${pdb.age}")
 *
 * // Enumerate types
 * for (type in pdb.types) {
 *     println("Type 0x${(0x1000 + pdb.types.indexOf(type)).toString(16)}: ${type.typeKind}")
 * }
 *
 * // Enumerate symbols
 * for (sym in pdb.globalSymbols) {
 *     println("${sym.symbolKind}: offset=${sym.data.size} bytes")
 * }
 *
 * // Module info
 * for (mod in pdb.modules) {
 *     println("Module: ${mod.moduleName}")
 * }
 * ```
 */
data class PdbFile(
    val guid: UUID,
    val age: Int,
    val version: PdbInfoStream.PdbStreamVersion?,
    val info: PdbInfoStream,
    val tpiHeader: TpiStreamHeader?,
    val types: List<CvTypeRecord>,
    val ipiHeader: TpiStreamHeader?,
    val idTypes: List<CvTypeRecord>,
    val dbiHeader: DbiStreamHeader?,
    val modules: List<DbiModuleInfo>,
    val sectionContributions: List<DbiSectionContribution>,
    val globalSymbols: List<CvSymbolRecord>,
    val publicSymbols: List<CvSymbolRecord>,
    val moduleSymbols: List<List<CvSymbolRecord>>,
    val namedStreams: Map<String, Int>,
    val msf: MsfFile,
) {
    /** Machine type from DBI header. */
    val machine: Short get() = dbiHeader?.machine ?: 0

    /** Check if this PDB matches a PE file's debug directory entry. */
    fun matchesGuid(peGuid: UUID, peAge: Int): Boolean =
        guid == peGuid && age == peAge
}

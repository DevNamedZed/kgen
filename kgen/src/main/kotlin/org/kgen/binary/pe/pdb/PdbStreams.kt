package org.kgen.binary.pe.pdb

import java.util.UUID

/**
 * Stream indices within a PDB file.
 */
object PdbStreamIndex {
    const val OLD_DIRECTORY = 0  // Empty in modern PDBs
    const val PDB_INFO = 1      // PDB Info (version, GUID, age, named streams)
    const val TPI = 2            // Type Program Information
    const val DBI = 3            // Debug Information
    const val IPI = 4            // ID Program Information
}

/**
 * PDB Info stream (stream 1 in older PDBs, stream 0 in the MSF directory).
 * Contains version info, GUID, age, and named stream map.
 */
data class PdbInfoStream(
    val version: PdbStreamVersion,
    val signature: Int,
    val age: Int,
    val guid: UUID,
    val namedStreams: Map<String, Int>,
) {
    enum class PdbStreamVersion(val code: Int) {
        VC2(19941610),
        VC4(19950623),
        VC41(19950814),
        VC50(19960307),
        VC98(19970604),
        VC70DEP(19990604),
        VC70(20000404),
        VC80(20030901),
        VC110(20091201),
        VC140(20140508),
        ;

        companion object {
            @JvmStatic
            fun fromCode(code: Int): PdbStreamVersion? = entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * TPI (Type Info) stream header.
 */
data class TpiStreamHeader(
    val version: Int,
    val headerSize: Int,
    val typeIndexBegin: Int,
    val typeIndexEnd: Int,
    val typeRecordBytes: Int,
    val hashStreamIndex: Int,
    val hashAuxStreamIndex: Int,
    val hashKeySize: Int,
    val numHashBuckets: Int,
    val hashValueBufferOffset: Int,
    val hashValueBufferLength: Int,
    val indexOffsetBufferOffset: Int,
    val indexOffsetBufferLength: Int,
    val hashAdjBufferOffset: Int,
    val hashAdjBufferLength: Int,
) {
    companion object {
        const val SIZE = 56
        const val VERSION_V80 = 20040203
    }
}

/**
 * A parsed CodeView type record from the TPI or IPI stream.
 */
data class CvTypeRecord(
    val kind: Int,
    val data: ByteArray,
) {
    val typeKind: CvTypeKind? get() = CvTypeKind.fromCode(kind)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CvTypeRecord) return false
        return kind == other.kind && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * kind + data.contentHashCode()
}

/**
 * A parsed CodeView symbol record.
 */
data class CvSymbolRecord(
    val kind: Int,
    val data: ByteArray,
) {
    val symbolKind: CvSymbolKind? get() = CvSymbolKind.fromCode(kind)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CvSymbolRecord) return false
        return kind == other.kind && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * kind + data.contentHashCode()
}

/**
 * DBI (Debug Information) stream header.
 */
data class DbiStreamHeader(
    val versionSignature: Int,
    val versionHeader: Int,
    val age: Int,
    val globalStreamIndex: Short,
    val buildNumber: Short,
    val publicStreamIndex: Short,
    val pdbDllVersion: Short,
    val symRecordStreamIndex: Short,
    val pdbDllRbld: Short,
    val modInfoSize: Int,
    val sectionContributionSize: Int,
    val sectionMapSize: Int,
    val sourceInfoSize: Int,
    val typeServerMapSize: Int,
    val mfcTypeServerIndex: Int,
    val optionalDbgHeaderSize: Int,
    val ecSubstreamSize: Int,
    val flags: Short,
    val machine: Short,
) {
    companion object {
        const val SIZE = 64
        const val VERSION_V70 = 19990903
    }
}

/**
 * A module info entry from the DBI module info substream.
 */
data class DbiModuleInfo(
    val moduleName: String,
    val objectFileName: String,
    val moduleSymbolStreamIndex: Int,
    val symbolSize: Int,
    val linesSize: Int,
    val c13LinesSize: Int,
    val sourceFileCount: Int,
    val sectionContributionOffset: Int,
    val sectionContributionSize: Int,
    val sectionIndex: Int,
)

/**
 * Section contribution entry from DBI stream.
 */
data class DbiSectionContribution(
    val section: Int,
    val offset: Int,
    val size: Int,
    val characteristics: Int,
    val moduleIndex: Int,
    val dataCrc: Int,
    val relocCrc: Int,
)

/**
 * C13 line information subsection types.
 */
enum class DebugSubsectionKind(val code: Int) {
    SYMBOLS(0xF1),
    LINES(0xF2),
    STRING_TABLE(0xF3),
    FILE_CHECKSUMS(0xF4),
    FRAME_DATA(0xF5),
    INLINEE_LINES(0xF6),
    CROSS_SCOPE_IMPORTS(0xF7),
    CROSS_SCOPE_EXPORTS(0xF8),
    IL_LINES(0xF9),
    FUNC_MDTOKEN_MAP(0xFA),
    TYPE_MDTOKEN_MAP(0xFB),
    MERGED_ASSEMBLY_INPUT(0xFC),
    COFF_SYMBOL_RVA(0xFD),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DebugSubsectionKind? = byCode[code]
    }
}

/**
 * A line info block within DEBUG_S_LINES.
 */
data class CvLineBlock(
    val fileIndex: Int,
    val lines: List<CvLineEntry>,
)

data class CvLineEntry(
    val offset: Int,
    val lineStart: Int,
    val deltaLineEnd: Int,
    val isStatement: Boolean,
)

/**
 * File checksum entry from DEBUG_S_FILECHKSMS.
 */
data class CvFileChecksum(
    val nameOffset: Int,
    val hashLength: Int,
    val hashType: Int,
    val hashBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CvFileChecksum) return false
        return nameOffset == other.nameOffset && hashType == other.hashType && hashBytes.contentEquals(other.hashBytes)
    }

    override fun hashCode(): Int = 31 * nameOffset + hashBytes.contentHashCode()
}

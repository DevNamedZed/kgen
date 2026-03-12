package org.kgen.binary.pe

import org.kgen.binary.pe.pdb.RsdsEntry

/**
 * IMAGE_DEBUG_DIRECTORY entry parsed from PE data directory 6.
 */
data class PeDebugEntry(
    val characteristics: Int,
    val timeDateStamp: Int,
    val majorVersion: Int,
    val minorVersion: Int,
    val type: Int,
    val sizeOfData: Int,
    val addressOfRawData: Int,
    val pointerToRawData: Int,
    val data: ByteArray,
) {
    val typeName: String get() = when (type) {
        TYPE_CODEVIEW -> "CodeView"
        TYPE_MISC -> "Misc"
        TYPE_FPO -> "FPO"
        TYPE_EXCEPTION -> "Exception"
        TYPE_FIXUP -> "Fixup"
        TYPE_OMAP_TO_SRC -> "OMAP_TO_SRC"
        TYPE_OMAP_FROM_SRC -> "OMAP_FROM_SRC"
        TYPE_BORLAND -> "Borland"
        TYPE_REPRO -> "Repro"
        TYPE_POGO -> "POGO"
        TYPE_ILTCG -> "ILTCG"
        TYPE_MPX -> "MPX"
        TYPE_VC_FEATURE -> "VCFeature"
        TYPE_EX_DLLCHARACTERISTICS -> "ExDllCharacteristics"
        else -> "Unknown($type)"
    }

    fun asRsds(): RsdsEntry? =
        if (type == TYPE_CODEVIEW && RsdsEntry.isRsds(data)) RsdsEntry.parse(data) else null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeDebugEntry) return false
        return type == other.type && timeDateStamp == other.timeDateStamp &&
                addressOfRawData == other.addressOfRawData && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = type * 31 + data.contentHashCode()

    companion object {
        const val TYPE_UNKNOWN = 0
        const val TYPE_COFF = 1
        const val TYPE_CODEVIEW = 2
        const val TYPE_FPO = 3
        const val TYPE_MISC = 4
        const val TYPE_EXCEPTION = 5
        const val TYPE_FIXUP = 6
        const val TYPE_OMAP_TO_SRC = 7
        const val TYPE_OMAP_FROM_SRC = 8
        const val TYPE_BORLAND = 9
        const val TYPE_CLSID = 11
        const val TYPE_VC_FEATURE = 12
        const val TYPE_POGO = 13
        const val TYPE_ILTCG = 14
        const val TYPE_MPX = 15
        const val TYPE_REPRO = 16
        const val TYPE_EX_DLLCHARACTERISTICS = 20

        const val ENTRY_SIZE = 28
    }
}

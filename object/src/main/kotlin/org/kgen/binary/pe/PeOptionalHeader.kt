package org.kgen.binary.pe

data class PeOptionalHeader(
    val magic: Int,
    val linkerVersionMajor: Int,
    val linkerVersionMinor: Int,
    val sizeOfCode: Int,
    val sizeOfInitializedData: Int,
    val sizeOfUninitializedData: Int,
    val entryPointRVA: Int,
    val baseOfCode: Int,
    val imageBase: Long,
    val sectionAlignment: Int,
    val fileAlignment: Int,
    val osVersionMajor: Int,
    val osVersionMinor: Int,
    val imageVersionMajor: Int,
    val imageVersionMinor: Int,
    val subsystemVersionMajor: Int,
    val subsystemVersionMinor: Int,
    val sizeOfImage: Int,
    val sizeOfHeaders: Int,
    val checksum: Int,
    val subsystem: Int,
    val dllCharacteristics: Int,
    val sizeOfStackReserve: Long,
    val sizeOfStackCommit: Long,
    val sizeOfHeapReserve: Long,
    val sizeOfHeapCommit: Long,
    val numberOfDataDirectories: Int,
)

data class PeDataDirectory(val rva: Int, val size: Int) {
    val isEmpty: Boolean get() = rva == 0 && size == 0
    companion object {
        const val EXPORT = 0
        const val IMPORT = 1
        const val RESOURCE = 2
        const val EXCEPTION = 3
        const val CERTIFICATE = 4
        const val BASE_RELOCATION = 5
        const val DEBUG = 6
        const val ARCHITECTURE = 7
        const val GLOBAL_PTR = 8
        const val TLS = 9
        const val LOAD_CONFIG = 10
        const val BOUND_IMPORT = 11
        const val IAT = 12
        const val DELAY_IMPORT = 13
        const val CLR_RUNTIME = 14
    }
}

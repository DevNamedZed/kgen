package org.kgen.binary.pe

data class PeExportDirectory(
    val name: String,
    val ordinalBase: Int,
    val timestamp: Int,
    val majorVersion: Int,
    val minorVersion: Int,
    val entries: List<PeExportEntry>,
)

data class PeExportEntry(
    val name: String?,
    val ordinal: Int,
    val rva: Int,
    val forwarderName: String?,
) {
    val isForwarder: Boolean get() = forwarderName != null
}

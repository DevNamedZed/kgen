package org.kgen.binary.pe

data class PeImportDirectory(
    val name: String,
    val entries: List<PeImportEntry>,
    val importLookupTableRVA: Int,
    val importAddressTableRVA: Int,
    val timestamp: Int,
    val forwarderChain: Int,
)

data class PeImportEntry(
    val name: String?,
    val ordinal: Int?,
    val hint: Int?,
    val isOrdinal: Boolean,
)

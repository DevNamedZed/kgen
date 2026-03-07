package org.kgen.binary.pe

data class PeBaseRelocation(
    val pageRVA: Int,
    val entries: List<PeBaseRelocationEntry>,
)

data class PeBaseRelocationEntry(
    val type: Int,
    val offset: Int,
) {
    companion object {
        const val IMAGE_REL_BASED_ABSOLUTE = 0
        const val IMAGE_REL_BASED_HIGH = 1
        const val IMAGE_REL_BASED_LOW = 2
        const val IMAGE_REL_BASED_HIGHLOW = 3
        const val IMAGE_REL_BASED_DIR64 = 10
    }
}

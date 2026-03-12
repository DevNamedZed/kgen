package org.kgen.binary.macho

/**
 * Per-segment chain start information.
 * Describes how fixup chains are laid out within a segment's pages.
 */
data class ChainedFixupSegment(
    val segmentIndex: Int,
    val pointerFormat: ChainedPointerFormat,
    val pageSize: Int,
    val segmentOffset: Long,
    val maxValidPointer: Long,
    val pageStarts: List<Int>,
) {
    companion object {
        const val DYLD_CHAINED_PTR_START_NONE = 0xFFFF
    }

    fun hasFixupsOnPage(pageIndex: Int): Boolean =
        pageIndex in pageStarts.indices && pageStarts[pageIndex] != DYLD_CHAINED_PTR_START_NONE
}

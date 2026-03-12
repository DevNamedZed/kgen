package org.kgen.binary.diff

// Binary diff and comparison

interface BinaryDiff {

    fun diff(a: ByteArray, b: ByteArray): List<BinaryDelta>

    fun structuralDiff(a: ByteArray, b: ByteArray): StructuralDelta
}

data class BinaryDelta(
    val offset: Long,
    val oldBytes: ByteArray,
    val newBytes: ByteArray,
    val section: String?,
    val nearestSymbol: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryDelta) return false
        return offset == other.offset && oldBytes.contentEquals(other.oldBytes) && newBytes.contentEquals(other.newBytes)
    }
    override fun hashCode(): Int = offset.hashCode()
}

data class StructuralDelta(
    val addedSections: List<String>,
    val removedSections: List<String>,
    val modifiedSections: List<String>,
    val addedSymbols: List<String>,
    val removedSymbols: List<String>,
    val modifiedSymbols: List<String>,
    val addedImports: List<String>,
    val removedImports: List<String>,
    val addedExports: List<String>,
    val removedExports: List<String>,
    val sectionDiffs: Map<String, List<BinaryDelta>>,
)

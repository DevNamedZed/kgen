package org.kgen.runtime.exec

/**
 * Virtual method table — maps slot indices to function entry points.
 */
data class VTable(
    val typeId: Int,
    val slots: LongArray,
) {
    override fun equals(other: Any?) = other is VTable && typeId == other.typeId
    override fun hashCode() = typeId
}

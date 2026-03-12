package org.kgen.runtime.exec

/**
 * Interface method table — maps slot indices to function entry points
 * for a specific type's implementation of an interface.
 */
data class ITable(
    val interfaceId: Int,
    val typeId: Int,
    val slots: LongArray,
) {
    override fun equals(other: Any?) = other is ITable && interfaceId == other.interfaceId && typeId == other.typeId
    override fun hashCode() = interfaceId * 31 + typeId
}

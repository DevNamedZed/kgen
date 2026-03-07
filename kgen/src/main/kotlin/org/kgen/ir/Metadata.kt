package org.kgen.ir

/** A type alias: maps [name] to [type]. */
data class TypeAlias(
    val name: String,
    val type: Type,
)

/** Metadata values attached to modules, functions, or instructions. */
sealed interface MetadataValue {
    data class StringMD(val value: String) : MetadataValue
    data class IntMD(val value: Long) : MetadataValue
    data class NodeMD(val values: List<MetadataValue>) : MetadataValue
    data class RefMD(val name: String) : MetadataValue
}

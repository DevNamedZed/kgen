package org.kgen.binary.macho

/**
 * A single chained fixup import entry.
 * Represents a symbol that needs to be bound at load time.
 */
data class ChainedFixupImport(
    val name: String,
    val libOrdinal: Int,
    val weakImport: Boolean,
    val addend: Long,
)

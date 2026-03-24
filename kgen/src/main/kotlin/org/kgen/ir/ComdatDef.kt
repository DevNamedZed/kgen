package org.kgen.ir

/** COMDAT group definition. Controls linker deduplication. */
data class ComdatDefinition(
    val name: String,
    val selectionKind: ComdatSelectionKind,
)

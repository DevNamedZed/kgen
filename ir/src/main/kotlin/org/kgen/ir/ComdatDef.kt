package org.kgen.ir

/** COMDAT group definition. Controls linker deduplication. */
data class ComdatDef(
    val name: String,
    val selectionKind: ComdatSelectionKind,
)

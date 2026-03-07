package org.kgen.ir.types

import org.kgen.ir.*

/** Low-level struct definition for native backends. */
data class StructDef(
    val name: String,
    val fields: List<Param>,
    val packed: Boolean = false,
    val align: Int? = null,
)

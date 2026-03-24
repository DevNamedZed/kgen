package org.kgen.ir.types

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/** Low-level struct definition for native backends. */
data class StructDefinition(
    val name: String,
    val fields: List<Param>,
    val packed: Boolean = false,
    val align: Int? = null,
)

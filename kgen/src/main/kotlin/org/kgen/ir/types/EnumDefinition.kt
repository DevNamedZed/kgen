package org.kgen.ir.types

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/** Enum definition with variants. Variants can have fields (algebraic enums). */
data class EnumDefinition(
    val name: String,
    val variants: List<EnumVariant>,
    val methods: List<MethodDefinition> = emptyList(),
    val interfaces: List<String> = emptyList(),
    val visibility: ClassVisibility = ClassVisibility.PUBLIC,
)

/** A single variant in an [EnumDefinition]. */
data class EnumVariant(
    val name: String,
    val ordinal: Int,
    val fields: List<Param> = emptyList(),
    val constructorArgs: List<Constant> = emptyList(),
)

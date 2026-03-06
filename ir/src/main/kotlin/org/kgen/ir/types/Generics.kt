package org.kgen.ir.types

import org.kgen.ir.Type

/** Generic type parameter definition. */
data class TypeParamDef(
    val name: String,
    val index: Int,
    val upperBounds: List<Type> = emptyList(),
    val lowerBounds: List<Type> = emptyList(),
    val variance: TypeVariance = TypeVariance.INVARIANT,
)

enum class TypeVariance { INVARIANT, COVARIANT, CONTRAVARIANT }

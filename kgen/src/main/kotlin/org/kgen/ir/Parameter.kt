package org.kgen.ir

/**
 * A function parameter. Accessible by [index] (0-based) within the function body.
 * May carry [attributes] like `zeroext`, `signext`, `noalias`, etc.
 */
data class Parameter(
    override val name: String,
    override val type: Type,
    val index: Int,
    val attributes: Set<ParamAttribute> = emptySet(),
) : Value

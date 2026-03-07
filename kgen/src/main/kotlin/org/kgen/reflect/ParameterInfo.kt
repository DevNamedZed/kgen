package org.kgen.reflect

/**
 * Reflection info for a method parameter.
 */
class ParameterInfo(
    private val name: String?,
    private val type: TypeRef,
    private val position: Int = 0,
    private val flags: Set<ParameterFlag> = emptySet(),
    private val defaultVal: Any? = null,
    private val attrs: List<AttributeInfo> = emptyList(),
) {
    fun name(): String? = name
    fun type(): TypeRef = type
    fun position(): Int = position
    fun isOptional(): Boolean = ParameterFlag.OPTIONAL in flags
    fun isOut(): Boolean = ParameterFlag.OUT in flags
    fun isRef(): Boolean = ParameterFlag.REF in flags
    fun isParams(): Boolean = ParameterFlag.PARAMS in flags
    fun defaultValue(): Any? = defaultVal
    fun attributes(): List<AttributeInfo> = attrs

    override fun toString(): String = buildString {
        if (name != null) {
            append(name)
            append(": ")
        }
        append(type)
    }
}

enum class ParameterFlag {
    OPTIONAL, OUT, REF, PARAMS,
}

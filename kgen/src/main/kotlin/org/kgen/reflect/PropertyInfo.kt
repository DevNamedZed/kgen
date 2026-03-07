package org.kgen.reflect

/**
 * Reflection info for a property (CLR, Kotlin).
 */
class PropertyInfo(
    private val name: String,
    private val propertyType: TypeRef,
    private val declaringType: TypeInfo? = null,
    private val getter: MethodInfo? = null,
    private val setter: MethodInfo? = null,
    private val attrs: List<AttributeInfo> = emptyList(),
) {
    fun name(): String = name
    fun propertyType(): TypeRef = propertyType
    fun declaringType(): TypeInfo? = declaringType
    fun getter(): MethodInfo? = getter
    fun setter(): MethodInfo? = setter
    fun isReadOnly(): Boolean = setter == null
    fun attributes(): List<AttributeInfo> = attrs

    override fun toString(): String = "$name: $propertyType"
}

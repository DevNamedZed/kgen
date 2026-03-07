package org.kgen.reflect

/**
 * Reflection info for a field within a type.
 *
 * ```java
 * var field = type.field("Length");
 * field.name();                // "Length"
 * field.fieldType();           // TypeRef.I32
 * field.isPublic();            // true
 * field.isReadOnly();          // true
 * ```
 */
class FieldInfo(
    private val name: String,
    private val fieldType: TypeRef,
    private val declaringType: TypeInfo? = null,
    private val flags: Set<FieldFlag> = emptySet(),
    private val constantVal: Any? = null,
    private val byteOffset: Int = -1,
    private val attrs: List<AttributeInfo> = emptyList(),
) {
    fun name(): String = name
    fun fieldType(): TypeRef = fieldType
    fun declaringType(): TypeInfo? = declaringType

    fun isPublic(): Boolean = FieldFlag.PUBLIC in flags
    fun isPrivate(): Boolean = FieldFlag.PRIVATE in flags
    fun isProtected(): Boolean = FieldFlag.PROTECTED in flags
    fun isStatic(): Boolean = FieldFlag.STATIC in flags
    fun isReadOnly(): Boolean = FieldFlag.READONLY in flags
    fun isConst(): Boolean = FieldFlag.CONST in flags
    fun isVolatile(): Boolean = FieldFlag.VOLATILE in flags
    fun isTransient(): Boolean = FieldFlag.TRANSIENT in flags

    fun constantValue(): Any? = constantVal
    fun offset(): Int = byteOffset
    fun attributes(): List<AttributeInfo> = attrs

    override fun toString(): String = "$name: $fieldType"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FieldInfo) return false
        return name == other.name && fieldType == other.fieldType
                && declaringType?.qualifiedName() == other.declaringType?.qualifiedName()
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + fieldType.hashCode()
        return result
    }
}

enum class FieldFlag {
    PUBLIC, PRIVATE, PROTECTED, INTERNAL,
    STATIC, READONLY, CONST, VOLATILE, TRANSIENT,
}

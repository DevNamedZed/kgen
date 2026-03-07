package org.kgen.reflect

/**
 * Reflection info for a method within a type.
 *
 * ```java
 * var method = type.method("Contains");
 * method.name();               // "Contains"
 * method.returnType();         // TypeRef
 * method.parameters();         // List<ParameterInfo>
 * method.isPublic();           // true
 * method.isVirtual();          // true
 * ```
 */
class MethodInfo(
    private val name: String,
    private val declaringType: TypeInfo? = null,
    private val returnType: TypeRef = TypeRef.VOID,
    private val params: List<ParameterInfo> = emptyList(),
    private val flags: Set<MethodFlag> = emptySet(),
    private val genericArgs: List<TypeRef> = emptyList(),
    private val attrs: List<AttributeInfo> = emptyList(),
    private val ilBytes: ByteArray? = null,
    private val bytecodeBytes: ByteArray? = null,
) {
    fun name(): String = name
    fun declaringType(): TypeInfo? = declaringType
    fun returnType(): TypeRef = returnType
    fun parameters(): List<ParameterInfo> = params
    fun parameterCount(): Int = params.size

    fun signature(): Signature {
        val builder = Signature.returning(returnType)
        for (p in params) {
            if (p.name() != null) builder.param(p.name()!!, p.type())
            else builder.param(p.type())
        }
        return builder.build()
    }

    // -- Classification --

    fun isPublic(): Boolean = MethodFlag.PUBLIC in flags
    fun isPrivate(): Boolean = MethodFlag.PRIVATE in flags
    fun isProtected(): Boolean = MethodFlag.PROTECTED in flags
    fun isStatic(): Boolean = MethodFlag.STATIC in flags
    fun isVirtual(): Boolean = MethodFlag.VIRTUAL in flags
    fun isAbstract(): Boolean = MethodFlag.ABSTRACT in flags
    fun isFinal(): Boolean = MethodFlag.FINAL in flags
    fun isNative(): Boolean = MethodFlag.NATIVE in flags
    fun isSynchronized(): Boolean = MethodFlag.SYNCHRONIZED in flags
    fun isConstructor(): Boolean = MethodFlag.CONSTRUCTOR in flags
    fun isGeneric(): Boolean = genericArgs.isNotEmpty()

    fun genericArguments(): List<TypeRef> = genericArgs
    fun attributes(): List<AttributeInfo> = attrs

    // -- Body --

    fun hasBody(): Boolean = ilBytes != null || bytecodeBytes != null
    fun il(): ByteArray? = ilBytes?.copyOf()
    fun bytecode(): ByteArray? = bytecodeBytes?.copyOf()

    override fun toString(): String = buildString {
        append(name)
        append("(")
        append(params.joinToString(", "))
        append("): ")
        append(returnType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodInfo) return false
        return name == other.name && params.map { it.type() } == other.params.map { it.type() }
                && declaringType?.qualifiedName() == other.declaringType?.qualifiedName()
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + params.map { it.type() }.hashCode()
        return result
    }
}

enum class MethodFlag {
    PUBLIC, PRIVATE, PROTECTED, INTERNAL,
    STATIC, VIRTUAL, ABSTRACT, FINAL,
    NATIVE, SYNCHRONIZED, CONSTRUCTOR,
}

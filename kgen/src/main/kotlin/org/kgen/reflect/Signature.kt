package org.kgen.reflect

/**
 * Describes a function's parameter types and return type.
 *
 * Used for marshaling when calling native functions via FFM, and for
 * inspection of managed function signatures.
 *
 * ```java
 * var sig = Signature.returning(TypeRef.I64)
 *     .param("a", TypeRef.I64)
 *     .param("b", TypeRef.I64);
 *
 * var sig = Signature.returningVoid()
 *     .param(TypeRef.POINTER);
 * ```
 */
class Signature private constructor(
    private val retType: TypeRef,
    private val params: List<SignatureParam>,
) {
    fun returnType(): TypeRef = retType
    fun parameters(): List<SignatureParam> = params
    fun parameterTypes(): List<TypeRef> = params.map { it.type }
    fun parameterCount(): Int = params.size

    override fun toString(): String = buildString {
        append("(")
        append(params.joinToString(", ") { it.toString() })
        append(") -> ")
        append(retType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false
        return retType == other.retType && params == other.params
    }

    override fun hashCode(): Int = 31 * retType.hashCode() + params.hashCode()

    companion object {
        @JvmField val VOID = Signature(TypeRef.VOID, emptyList())
        @JvmField val LONG_TO_LONG = returning(TypeRef.I64).param(TypeRef.I64).build()
        @JvmField val LONG_LONG_TO_LONG = returning(TypeRef.I64).param(TypeRef.I64).param(TypeRef.I64).build()

        @JvmStatic
        fun returning(returnType: TypeRef): Builder = Builder(returnType)

        @JvmStatic
        fun returningVoid(): Builder = Builder(TypeRef.VOID)

        @JvmStatic
        fun of(returnType: TypeRef, vararg paramTypes: TypeRef): Signature =
            Signature(returnType, paramTypes.map { SignatureParam(null, it) })

        @JvmStatic
        fun ofVoid(vararg paramTypes: TypeRef): Signature =
            Signature(TypeRef.VOID, paramTypes.map { SignatureParam(null, it) })
    }

    class Builder(private val returnType: TypeRef) {
        private val params = mutableListOf<SignatureParam>()

        fun param(type: TypeRef): Builder {
            params.add(SignatureParam(null, type))
            return this
        }

        fun param(name: String, type: TypeRef): Builder {
            params.add(SignatureParam(name, type))
            return this
        }

        fun build(): Signature = Signature(returnType, params.toList())
    }
}

data class SignatureParam(
    val name: String?,
    val type: TypeRef,
) {
    override fun toString(): String = if (name != null) "$name: $type" else type.toString()
}

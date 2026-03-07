package org.kgen.reflect

/**
 * A reference to a type. May or may not be resolvable to a [TypeInfo].
 *
 * Used in signatures, field types, return types, etc.
 */
class TypeRef private constructor(
    private val qname: QualifiedName?,
    private val kind: Kind,
    private val element: TypeRef? = null,
) {
    enum class Kind {
        NAMED, VOID, PRIMITIVE, ARRAY, POINTER, BY_REF, GENERIC_PARAM
    }

    fun name(): String = qname?.name() ?: kind.name.lowercase()
    fun namespace(): String? = qname?.namespace()
    fun fullName(): String = when (kind) {
        Kind.ARRAY -> "${element!!.fullName()}[]"
        Kind.POINTER -> "${element!!.fullName()}*"
        Kind.BY_REF -> "${element!!.fullName()}&"
        else -> qname?.fullName() ?: kind.name.lowercase()
    }

    fun isResolved(): Boolean = false // TODO: resolve against a Module's type table
    fun resolve(): Any? = null        // TODO: returns TypeInfo when implemented

    fun isArray(): Boolean = kind == Kind.ARRAY
    fun isPointer(): Boolean = kind == Kind.POINTER
    fun isByRef(): Boolean = kind == Kind.BY_REF
    fun isGenericParameter(): Boolean = kind == Kind.GENERIC_PARAM
    fun isVoid(): Boolean = this === VOID
    fun isPrimitive(): Boolean = kind == Kind.PRIMITIVE

    fun elementType(): TypeRef? = element

    override fun toString(): String = fullName()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeRef) return false
        return kind == other.kind && qname == other.qname && element == other.element
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (qname?.hashCode() ?: 0)
        result = 31 * result + (element?.hashCode() ?: 0)
        return result
    }

    companion object {
        @JvmField val VOID = TypeRef(null, Kind.VOID)
        @JvmField val BOOL = primitive("bool")
        @JvmField val I8 = primitive("i8")
        @JvmField val I16 = primitive("i16")
        @JvmField val I32 = primitive("i32")
        @JvmField val I64 = primitive("i64")
        @JvmField val U8 = primitive("u8")
        @JvmField val U16 = primitive("u16")
        @JvmField val U32 = primitive("u32")
        @JvmField val U64 = primitive("u64")
        @JvmField val F32 = primitive("f32")
        @JvmField val F64 = primitive("f64")
        @JvmField val POINTER = primitive("ptr")
        @JvmField val LONG = I64

        private fun primitive(name: String): TypeRef =
            TypeRef(QualifiedName.of(name), Kind.PRIMITIVE)

        @JvmStatic
        fun of(name: String): TypeRef = TypeRef(QualifiedName.parse(name), Kind.NAMED)

        @JvmStatic
        fun of(name: QualifiedName): TypeRef = TypeRef(name, Kind.NAMED)

        @JvmStatic
        fun arrayOf(element: TypeRef): TypeRef = TypeRef(null, Kind.ARRAY, element)

        @JvmStatic
        fun pointerTo(element: TypeRef): TypeRef = TypeRef(null, Kind.POINTER, element)

        @JvmStatic
        fun byRef(element: TypeRef): TypeRef = TypeRef(null, Kind.BY_REF, element)

        @JvmStatic
        fun genericParam(name: String): TypeRef =
            TypeRef(QualifiedName.of(name), Kind.GENERIC_PARAM)
    }
}

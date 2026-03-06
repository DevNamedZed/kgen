package org.kgen.ir

/**
 * Compile-time constants. These are values known at IR construction time
 * and can appear as instruction operands, global initializers, and annotation values.
 */
sealed interface Constant : Value {

    data class I1(val value: Boolean) : Constant {
        override val type: Type get() = Type.I1
        override val name: String get() = if (value) "1" else "0"
    }

    data class I8(val value: Byte) : Constant {
        override val type: Type get() = Type.I8
        override val name: String get() = value.toString()
    }

    data class I16(val value: Short) : Constant {
        override val type: Type get() = Type.I16
        override val name: String get() = value.toString()
    }

    data class I32(val value: Int) : Constant {
        override val type: Type get() = Type.I32
        override val name: String get() = value.toString()
    }

    data class I64(val value: Long) : Constant {
        override val type: Type get() = Type.I64
        override val name: String get() = value.toString()
    }

    data class I128(val value: Long) : Constant {
        override val type: Type get() = Type.I128
        override val name: String get() = value.toString()
    }

    data class IntN(val value: Long, val bits: Int) : Constant {
        override val type: Type get() = Type.IntN(bits)
        override val name: String get() = value.toString()
    }

    data class F16(val value: Float) : Constant {
        override val type: Type get() = Type.F16
        override val name: String get() = value.toString()
    }

    data class BF16(val value: Float) : Constant {
        override val type: Type get() = Type.BF16
        override val name: String get() = value.toString()
    }

    data class F32(val value: Float) : Constant {
        override val type: Type get() = Type.F32
        override val name: String get() = value.toString()
    }

    data class F64(val value: Double) : Constant {
        override val type: Type get() = Type.F64
        override val name: String get() = value.toString()
    }

    data class F80(val value: Double) : Constant {
        override val type: Type get() = Type.F80
        override val name: String get() = value.toString()
    }

    data class F128(val value: Double) : Constant {
        override val type: Type get() = Type.F128
        override val name: String get() = value.toString()
    }

    data object NullPtr : Constant {
        override val type: Type get() = Type.OpaquePointer
        override val name: String get() = "null"
    }

    data object NullRef : Constant {
        override val type: Type get() = Type.Reference(Type.Void, nullable = true)
        override val name: String get() = "null"
    }

    data class Undef(override val type: Type) : Constant {
        override val name: String get() = "undef"
    }

    data class Poison(override val type: Type) : Constant {
        override val name: String get() = "poison"
    }

    data class ZeroInitializer(override val type: Type) : Constant {
        override val name: String get() = "zeroinitializer"
    }

    data class ArrayConst(override val type: Type, val elements: List<Constant>) : Constant {
        override val name: String get() = "[${elements.joinToString(", ") { it.name }}]"
    }

    data class VectorConst(override val type: Type, val elements: List<Constant>) : Constant {
        override val name: String get() = "<${elements.joinToString(", ") { it.name }}>"
    }

    data class StructConst(override val type: Type, val fields: List<Constant>) : Constant {
        override val name: String get() = "{${fields.joinToString(", ") { it.name }}}"
    }

    data class StringConst(val value: String, val nullTerminated: Boolean = true) : Constant {
        override val type: Type get() = Type.Array(Type.I8, (value.length + if (nullTerminated) 1 else 0).toLong())
        override val name: String get() = "\"$value\""
    }

    data class GetElementPtr(override val type: Type, val base: Constant, val indices: List<Constant>, val inBounds: Boolean = true) : Constant {
        override val name: String get() = "getelementptr"
    }

    data class BitCast(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "bitcast"
    }

    data class IntToPtr(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "inttoptr"
    }

    data class PtrToInt(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "ptrtoint"
    }
}

package org.kgen.ir

/**
 * Compile-time constants. These are values known at IR construction time
 * and can appear as instruction operands, global initializers, and annotation values.
 *
 * Constants implement [Value], so they can be passed directly wherever a [Value] operand
 * is expected (e.g., to `ModuleBuilder.add`, `ModuleBuilder.store`, etc.). The [Type] companions
 * provide convenient factory functions for the most common constant types:
 *
 * ```java
 * // Java — via Type companion factories
 * Constant.I32 forty two = Type.i32(42);
 * Constant.F64 pi        = Type.f64(3.14159);
 * Constant.StringConst s = Type.string("hello");
 * Constant.NullPtr nul   = Type.nullPtr();
 * ```
 *
 * ```kotlin
 * // Kotlin
 * val fortyTwo = Type.i32(42)
 * val pi       = Type.f64(3.14159)
 * val hello    = Type.string("hello")
 * ```
 *
 * @see Type
 * @see Value
 */
sealed interface Constant : Value {

    /**
     * A 1-bit integer constant, representing a boolean value.
     * Printed as `1` (true) or `0` (false) in IR text.
     *
     * @param value the boolean value this constant represents
     */
    data class I1(val value: Boolean) : Constant {
        override val type: Type get() = Type.I1
        override val name: String get() = if (value) "1" else "0"
    }

    /**
     * An 8-bit integer constant.
     *
     * @param value the byte value of this constant
     */
    data class I8(val value: Byte) : Constant {
        override val type: Type get() = Type.I8
        override val name: String get() = value.toString()
    }

    /**
     * A 16-bit integer constant.
     *
     * @param value the short value of this constant
     */
    data class I16(val value: Short) : Constant {
        override val type: Type get() = Type.I16
        override val name: String get() = value.toString()
    }

    /**
     * A 32-bit integer constant. The most commonly used integer constant.
     *
     * @param value the int value of this constant
     */
    data class I32(val value: Int) : Constant {
        override val type: Type get() = Type.I32
        override val name: String get() = value.toString()
    }

    /**
     * A 64-bit integer constant.
     *
     * @param value the long value of this constant
     */
    data class I64(val value: Long) : Constant {
        override val type: Type get() = Type.I64
        override val name: String get() = value.toString()
    }

    /**
     * A 128-bit integer constant. The value is stored as a [Long] (lower 64 bits).
     * For values that exceed 64-bit range, use [ArrayConst] of two [I64]s or a custom encoding.
     *
     * @param value the lower 64 bits of the 128-bit constant
     */
    data class I128(val value: Long) : Constant {
        override val type: Type get() = Type.I128
        override val name: String get() = value.toString()
    }

    /**
     * An arbitrary-width integer constant. Used for bit widths not covered by the standard
     * integer types (e.g., i24, i48, i256).
     *
     * @param value the constant's value (truncated to [bits] bits)
     * @param bits the bit width of the integer type
     */
    data class IntN(val value: Long, val bits: Int) : Constant {
        override val type: Type get() = Type.IntN(bits)
        override val name: String get() = value.toString()
    }

    /**
     * An IEEE 754 half-precision (16-bit) floating-point constant.
     * Stored as a [Float] (widened); precision is limited to the f16 range.
     *
     * @param value the floating-point value
     */
    data class F16(val value: Float) : Constant {
        override val type: Type get() = Type.F16
        override val name: String get() = value.toString()
    }

    /**
     * A Brain Float 16 (bfloat16) constant. Same exponent range as [F32] but with
     * reduced mantissa precision. Used in ML/AI workloads.
     * Stored as a [Float] (widened).
     *
     * @param value the floating-point value
     */
    data class BF16(val value: Float) : Constant {
        override val type: Type get() = Type.BF16
        override val name: String get() = value.toString()
    }

    /**
     * An IEEE 754 single-precision (32-bit) floating-point constant.
     *
     * @param value the floating-point value
     */
    data class F32(val value: Float) : Constant {
        override val type: Type get() = Type.F32
        override val name: String get() = value.toString()
    }

    /**
     * An IEEE 754 double-precision (64-bit) floating-point constant.
     *
     * @param value the double-precision floating-point value
     */
    data class F64(val value: Double) : Constant {
        override val type: Type get() = Type.F64
        override val name: String get() = value.toString()
    }

    /**
     * An x87 extended-precision (80-bit) floating-point constant. x86-specific.
     * Stored as a [Double] (nearest representable value).
     *
     * @param value the floating-point value
     */
    data class F80(val value: Double) : Constant {
        override val type: Type get() = Type.F80
        override val name: String get() = value.toString()
    }

    /**
     * An IEEE 754 quad-precision (128-bit) floating-point constant.
     * Stored as a [Double] (nearest representable value).
     *
     * @param value the floating-point value
     */
    data class F128(val value: Double) : Constant {
        override val type: Type get() = Type.F128
        override val name: String get() = value.toString()
    }

    /**
     * A null pointer constant of type [Type.OpaquePointer].
     * Use this as the operand to pointer-consuming instructions when you need a literal `null`.
     *
     * @see Type.nullPtr
     */
    data object NullPtr : Constant {
        override val type: Type get() = Type.OpaquePointer
        override val name: String get() = "null"
    }

    /**
     * A null GC-managed reference constant. Its type is a nullable [Type.Reference] to [Type.Void].
     * Use this when initializing reference-typed fields or locals to null.
     *
     * @see Type.nullRef
     */
    data object NullRef : Constant {
        override val type: Type get() = Type.Reference(Type.Void, nullable = true)
        override val name: String get() = "null"
    }

    /**
     * An undefined value constant. Reading an `undef` value produces an arbitrary but legal
     * value of [type] — similar to LLVM's `undef`. The optimizer may assume any value was read.
     * Use this for uninitialized variables or unreachable paths where the value does not matter.
     *
     * @param type the type of the undefined value
     * @see Poison
     */
    data class Undef(override val type: Type) : Constant {
        override val name: String get() = "undef"
    }

    /**
     * A poison value constant. Stronger than [Undef]: any instruction that observes a `poison`
     * value has undefined behavior, allowing the optimizer to make more aggressive assumptions.
     * Analogous to LLVM's `poison`. Use when the value is logically unreachable or when
     * expressing contract violations to the optimizer.
     *
     * @param type the type of the poison value
     * @see Undef
     */
    data class Poison(override val type: Type) : Constant {
        override val name: String get() = "poison"
    }

    /**
     * A zero-initializer constant for any type. Aggregates (structs, arrays, vectors) are
     * recursively zero-filled; integer types become `0`; float types become `+0.0`;
     * pointer types become `null`. Analogous to LLVM's `zeroinitializer`.
     *
     * ```java
     * // Initialize a 4-element i32 array to all zeros
     * Constant zeros = Type.zero(Type.array(Type.I32, 4));
     * ```
     *
     * @param type the type to zero-initialize
     */
    data class ZeroInitializer(override val type: Type) : Constant {
        override val name: String get() = "zeroinitializer"
    }

    /**
     * A constant aggregate array. All [elements] must have types consistent with [type].
     *
     * ```java
     * // [1, 2, 3] : [3 x i32]
     * var elements = List.of(Type.i32(1), Type.i32(2), Type.i32(3));
     * var arr = new Constant.ArrayConst(Type.array(Type.I32, 3), elements);
     * ```
     *
     * @param type the array type ([Type.Array]) describing the element type and count
     * @param elements the constant element values, in order
     */
    data class ArrayConst(override val type: Type, val elements: List<Constant>) : Constant {
        override val name: String get() = "[${elements.joinToString(", ") { it.name }}]"
    }

    /**
     * A constant SIMD vector. All [elements] must have the scalar type matching [type]'s element type.
     *
     * ```java
     * // <4 x i32> splat of 1
     * var ones = Collections.nCopies(4, Type.i32(1));
     * var vec = new Constant.VectorConst(Type.vector(Type.I32, 4), ones);
     * ```
     *
     * @param type the vector type ([Type.Vector]) describing the element type and lane count
     * @param elements the constant lane values, in order
     */
    data class VectorConst(override val type: Type, val elements: List<Constant>) : Constant {
        override val name: String get() = "<${elements.joinToString(", ") { it.name }}>"
    }

    /**
     * A constant struct aggregate. [fields] must match the field types declared in [type].
     *
     * @param type the struct type ([Type.Struct]) this constant inhabits
     * @param fields the constant values for each field, in declaration order
     */
    data class StructConst(override val type: Type, val fields: List<Constant>) : Constant {
        override val name: String get() = "{${fields.joinToString(", ") { it.name }}}"
    }

    /**
     * A compile-time string constant, stored as an array of [Type.I8] bytes.
     * If [nullTerminated] is true (the default), a trailing `\0` byte is appended and
     * the resulting type is `[length+1 x i8]`. Otherwise the type is `[length x i8]`.
     *
     * The backend emits this in a read-only data section (e.g., `.rodata` on ELF).
     *
     * ```java
     * // "hello\0" : [6 x i8]
     * Constant.StringConst s = Type.string("hello");
     *
     * // "hello" : [5 x i8]  (no null terminator)
     * Constant.StringConst s2 = Type.string("hello", false);
     * ```
     *
     * @param value the string contents (Java/Kotlin UTF-16 string; encoded as UTF-8 by the backend)
     * @param nullTerminated whether to append a null terminator byte
     */
    data class StringConst(val value: String, val nullTerminated: Boolean = true) : Constant {
        override val type: Type get() = Type.Array(Type.I8, (value.length + if (nullTerminated) 1 else 0).toLong())
        override val name: String get() = "\"$value\""
    }

    /**
     * A constant GEP (GetElementPtr) expression. Computes a pointer to a sub-element of
     * a constant aggregate [base] without performing a load. [indices] navigate into the
     * aggregate in the same way as the GEP instruction.
     *
     * Primarily used to embed pointers-into-globals in other global initializers.
     *
     * @param type the resulting pointer type after applying the index path
     * @param base the constant aggregate or pointer to index into
     * @param indices the sequence of constant indices, starting with the outer array index
     * @param inBounds if true, the optimizer may assume the result does not overflow the object bounds
     */
    data class GetElementPtr(override val type: Type, val base: Constant, val indices: List<Constant>, val inBounds: Boolean = true) : Constant {
        override val name: String get() = "getelementptr"
    }

    /**
     * A constant bitcast expression. Reinterprets [value] as [type] without changing the
     * underlying bit pattern. Both types must have the same bit width.
     *
     * @param type the target type after the cast
     * @param value the source constant to reinterpret
     */
    data class BitCast(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "bitcast"
    }

    /**
     * A constant integer-to-pointer cast. Converts an integer constant [value] to a
     * pointer of [type]. The integer is interpreted as a flat memory address.
     *
     * @param type the resulting pointer type
     * @param value an integer constant representing the target address
     */
    data class IntToPtr(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "inttoptr"
    }

    /**
     * A constant pointer-to-integer cast. Converts a pointer constant [value] to an
     * integer of [type], capturing the pointer's numeric address.
     *
     * @param type the resulting integer type (typically [Type.I64] on 64-bit targets)
     * @param value a pointer constant to convert
     */
    data class PtrToInt(override val type: Type, val value: Constant) : Constant {
        override val name: String get() = "ptrtoint"
    }
}

package org.kgen.ir

/**
 * The kgen type system.
 *
 * Types are immutable, sealed, and structural — two types are equal if their structure matches.
 * The system spans both low-level (native backend) and high-level (JVM/WASM) needs:
 *
 * - **Primitive integers**: [I1], [I8], [I16], [I32], [I64], [I128], [IntN] (arbitrary width)
 * - **Floating point**: [F16], [BF16], [F32], [F64], [F80], [F128]
 * - **Pointers**: [Pointer] (typed), [OpaquePointer] (untyped, like LLVM's `ptr`)
 * - **References**: [Reference] (GC-managed), [WeakReference] (weak GC ref)
 * - **Aggregates**: [Array], [Vector], [Struct], [Union], [TaggedUnion]
 * - **Functions**: [Function] (param types + return type + vararg)
 * - **OOP**: [ClassRef], [InterfaceRef] (references to class/interface definitions)
 * - **Generics**: [TypeParam], [Parameterized]
 * - **Special**: [Void], [Label], [Metadata], [Token], [Nullable], [PlatformType]
 */
sealed interface Type {

    /** 1-bit integer. Used as the boolean type (result of comparisons, branch conditions). */
    data object I1 : Type

    /** 8-bit integer. Byte type. */
    data object I8 : Type

    /** 16-bit integer. Short/char type. */
    data object I16 : Type

    /** 32-bit integer. Standard int type. */
    data object I32 : Type

    /** 64-bit integer. Long type. */
    data object I64 : Type

    /** 128-bit integer. Used for large integer arithmetic and crypto. */
    data object I128 : Type

    /** Arbitrary-width integer. For bit widths not covered by the standard types (e.g., i24, i256). */
    data class IntN(val bits: Int) : Type

    /** IEEE 754 half-precision (16-bit) floating point. */
    data object F16 : Type

    /** Brain floating point (16-bit). Used in ML workloads — same exponent range as f32 but reduced mantissa. */
    data object BF16 : Type

    /** IEEE 754 single-precision (32-bit) floating point. */
    data object F32 : Type

    /** IEEE 754 double-precision (64-bit) floating point. */
    data object F64 : Type

    /** x87 extended precision (80-bit) floating point. x86-specific. */
    data object F80 : Type

    /** IEEE 754 quad-precision (128-bit) floating point. */
    data object F128 : Type

    /** The void type. Used as the return type for functions that return nothing. Not a valid value type. */
    data object Void : Type

    /** The label type. Represents basic block labels in control flow instructions. */
    data object Label : Type

    /** The metadata type. Used for debug info and optimization hints, not for real values. */
    data object Metadata : Type

    /** The token type. Used for exception handling pads (catchpad, cleanuppad) that cannot be used as real values. */
    data object Token : Type

    /**
     * Typed pointer. Points to a value of type [pointee] in address space [addressSpace].
     * Address space 0 is the default (flat/generic). Non-zero address spaces are target-specific
     * (e.g., GPU shared memory, x86 segment registers).
     */
    data class Pointer(val pointee: Type, val addressSpace: Int = 0) : Type

    /**
     * Opaque pointer (like LLVM's `ptr`). No pointee type — the load/store instructions
     * specify the accessed type. Preferred for low-level backends.
     */
    data object OpaquePointer : Type

    /**
     * GC-managed reference. Used by high-level backends (JVM, WASM-GC).
     * [nullable] controls whether the reference can be null — enables nullability checking in the verifier.
     */
    data class Reference(val referent: Type, val nullable: Boolean = true) : Type

    /** Weak GC reference. May be collected even while this reference exists. */
    data class WeakReference(val referent: Type) : Type

    /** Fixed-size array. [size] elements of type [element], laid out contiguously in memory. */
    data class Array(val element: Type, val size: Long) : Type

    /**
     * SIMD vector type. [lanes] elements of type [element] (must be integer or float).
     * If [scalable] is true, the actual lane count is a runtime multiple of [lanes] (like ARM SVE).
     */
    data class Vector(val element: Type, val lanes: Int, val scalable: Boolean = false) : Type

    /**
     * Struct type. An ordered collection of [fields] at sequential offsets.
     * [name] is optional — anonymous structs are identified structurally.
     * [packed] disables padding between fields (like `__attribute__((packed))`).
     */
    data class Struct(val name: String?, val fields: List<Type>, val packed: Boolean = false) : Type

    /** Opaque struct. Declared by name but with no visible field layout (forward declaration). */
    data class OpaqueStruct(val name: String) : Type

    /** Untagged union. Overlapping [variants] share the same memory. Size = max variant size. */
    data class Union(val name: String?, val variants: List<Type>) : Type

    /**
     * Tagged union (algebraic data type / sum type). A discriminant of type [tagType]
     * selects the active [variant][TaggedVariant]. Used with [Instruction.ConstructVariant],
     * [Instruction.GetTag], [Instruction.GetVariantField], and [Instruction.TagSwitch].
     */
    data class TaggedUnion(
        val name: String,
        val tagType: Type,
        val variants: List<TaggedVariant>,
    ) : Type

    /**
     * Function type. Describes the signature: [params] → [ret].
     * [vararg] indicates a C-style variadic function (extra args beyond the declared params).
     */
    data class Function(val params: List<Type>, val ret: Type, val vararg: Boolean = false) : Type

    /** Reference to a class definition by name. Resolved against the module's [Module.classes]. */
    data class ClassRef(val name: String) : Type

    /** Reference to an interface definition by name. Resolved against the module's [Module.interfaces]. */
    data class InterfaceRef(val name: String) : Type

    /**
     * Generic type parameter. [name] is the source-level name (e.g., "T"), [index] is the
     * positional index in the type parameter list, and [bounds] are upper-bound constraints.
     */
    data class TypeParam(val name: String, val index: Int, val bounds: List<Type> = emptyList()) : Type

    /**
     * Parameterized (generic) type. [base] is the raw type (e.g., `ClassRef("List")`)
     * and [typeArgs] are the concrete type arguments (e.g., `[Type.I32]` for `List<Int>`).
     */
    data class Parameterized(val base: Type, val typeArgs: List<Type>) : Type

    /** Nullable wrapper for non-reference types. For references, use [Reference] with `nullable = true` instead. */
    data class Nullable(val inner: Type) : Type

    /**
     * Escape hatch for platform-specific types that don't fit the standard type system.
     * [name] identifies the platform type; [properties] carry target-specific metadata.
     */
    data class PlatformType(val name: String, val properties: Map<String, String> = emptyMap()) : Type

    companion object {
        // --- Type constructors (convenience aliases for Java discoverability) ---

        @JvmStatic @JvmOverloads fun pointer(pointee: Type, addressSpace: Int = 0): Pointer = Pointer(pointee, addressSpace)
        @JvmStatic fun opaquePointer(): OpaquePointer = OpaquePointer
        @JvmStatic @JvmOverloads fun reference(referent: Type, nullable: Boolean = true): Reference = Reference(referent, nullable)
        @JvmStatic fun weakReference(referent: Type): WeakReference = WeakReference(referent)
        @JvmStatic fun array(element: Type, size: Long): Array = Array(element, size)
        @JvmStatic @JvmOverloads fun vector(element: Type, lanes: Int, scalable: Boolean = false): Vector = Vector(element, lanes, scalable)
        @JvmStatic @JvmOverloads fun struct(name: String?, fields: List<Type>, packed: Boolean = false): Struct = Struct(name, fields, packed)
        @JvmStatic fun union(name: String?, variants: List<Type>): Union = Union(name, variants)
        @JvmStatic @JvmOverloads fun function(params: List<Type>, ret: Type, vararg: Boolean = false): Function = Function(params, ret, vararg)
        @JvmStatic fun classRef(name: String): ClassRef = ClassRef(name)
        @JvmStatic fun interfaceRef(name: String): InterfaceRef = InterfaceRef(name)
        @JvmStatic fun nullable(inner: Type): Nullable = Nullable(inner)

        // --- Constant factories ---

        @JvmStatic fun i1(value: Boolean): Constant.I1 = Constant.I1(value)
        @JvmStatic fun i8(value: Int): Constant.I8 = Constant.I8(value.toByte())
        @JvmStatic fun i16(value: Int): Constant.I16 = Constant.I16(value.toShort())
        @JvmStatic fun i32(value: Int): Constant.I32 = Constant.I32(value)
        @JvmStatic fun i64(value: Long): Constant.I64 = Constant.I64(value)
        @JvmStatic fun i128(value: Long): Constant.I128 = Constant.I128(value)
        @JvmStatic fun f16(value: Float): Constant.F16 = Constant.F16(value)
        @JvmStatic fun bf16(value: Float): Constant.BF16 = Constant.BF16(value)
        @JvmStatic fun f32(value: Float): Constant.F32 = Constant.F32(value)
        @JvmStatic fun f64(value: Double): Constant.F64 = Constant.F64(value)
        @JvmStatic @JvmOverloads fun string(value: String, nullTerminated: Boolean = true): Constant.StringConst = Constant.StringConst(value, nullTerminated)
        @JvmStatic fun nullPtr(): Constant.NullPtr = Constant.NullPtr
        @JvmStatic fun nullRef(): Constant.NullRef = Constant.NullRef
        @JvmStatic fun zero(type: Type): Constant.ZeroInitializer = Constant.ZeroInitializer(type)
        @JvmStatic fun undef(type: Type): Constant.Undef = Constant.Undef(type)
        @JvmStatic fun poison(type: Type): Constant.Poison = Constant.Poison(type)

        // --- Param factory ---

        @JvmStatic fun param(name: String, type: Type): Param = Param(name, type)
    }
}

/** A variant in a [Type.TaggedUnion]. Each variant has a unique [tag] value and zero or more [fields]. */
data class TaggedVariant(
    val name: String,
    val tag: Long,
    val fields: List<Type>,
)

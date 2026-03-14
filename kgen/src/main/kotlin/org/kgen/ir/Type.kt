package org.kgen.ir

import org.kgen.ir.instructions.*

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

    /**
     * Interior pointer into a GC-managed object (e.g., a field or array element).
     * The GC tracks these and updates them when the containing object moves.
     * Unlike raw pointers, interior refs are safe across GC compaction.
     */
    data class InteriorRef(val pointee: Type) : Type

    /**
     * Pinned reference to a GC-managed object. The GC will not move the object
     * while a pinned ref to it exists. Can be converted to a raw native pointer.
     * Inspired by C++/CLI `pin_ptr<T>`.
     */
    data class PinnedRef(val referent: Type) : Type

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
     * selects the active [variant][TaggedVariant]. Used with [ConstructVariant],
     * [GetTag], [GetVariantField], and [TagSwitch].
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

        /**
         * Creates a typed pointer to [pointee] in the given [addressSpace].
         * Address space 0 is the default flat/generic space.
         *
         * @param pointee the type of the value being pointed to
         * @param addressSpace target-specific address space index (default `0`)
         */
        @JvmStatic @JvmOverloads fun pointer(pointee: Type, addressSpace: Int = 0): Pointer = Pointer(pointee, addressSpace)

        /**
         * Returns the singleton opaque pointer type (`ptr` in LLVM terms).
         * No pointee type is encoded; the accessing instruction specifies the loaded type.
         */
        @JvmStatic fun opaquePointer(): OpaquePointer = OpaquePointer

        /**
         * Creates a GC-managed reference to [referent].
         *
         * @param referent the type of the heap object being referred to
         * @param nullable whether this reference can hold `null` (default `true`)
         */
        @JvmStatic @JvmOverloads fun reference(referent: Type, nullable: Boolean = true): Reference = Reference(referent, nullable)

        /**
         * Creates a weak GC-managed reference to [referent].
         * The referenced object may be collected even while this reference exists.
         *
         * @param referent the type of the heap object being weakly referenced
         */
        @JvmStatic fun weakReference(referent: Type): WeakReference = WeakReference(referent)

        /**
         * Creates an interior pointer into a GC-managed object of type [pointee].
         * The GC tracks and updates interior refs during compaction.
         *
         * @param pointee the type of the sub-element being pointed to
         */
        @JvmStatic fun interiorRef(pointee: Type): InteriorRef = InteriorRef(pointee)

        /**
         * Creates a pinned GC reference to [referent]. The GC will not move the object
         * while a pinned ref exists, allowing conversion to a raw native pointer.
         *
         * @param referent the type of the pinned heap object
         */
        @JvmStatic fun pinnedRef(referent: Type): PinnedRef = PinnedRef(referent)

        /**
         * Creates a fixed-size array type with [size] elements of type [element].
         *
         * @param element the type of each array element
         * @param size the number of elements (must be > 0)
         */
        @JvmStatic fun array(element: Type, size: Long): Array = Array(element, size)

        /**
         * Creates a SIMD vector type with [lanes] elements of type [element].
         *
         * @param element the scalar element type (must be integer or float)
         * @param lanes the number of vector lanes
         * @param scalable if `true`, the actual lane count is a runtime multiple of [lanes] (ARM SVE style)
         */
        @JvmStatic @JvmOverloads fun vector(element: Type, lanes: Int, scalable: Boolean = false): Vector = Vector(element, lanes, scalable)

        /**
         * Creates a struct type with the given [fields].
         *
         * @param name optional name for the struct (anonymous if `null`)
         * @param fields the ordered list of field types
         * @param packed if `true`, fields are packed with no alignment padding
         */
        @JvmStatic @JvmOverloads fun struct(name: String?, fields: List<Type>, packed: Boolean = false): Struct = Struct(name, fields, packed)

        /**
         * Creates an untagged union type whose [variants] share the same memory.
         * The size of the union equals the size of its largest variant.
         *
         * @param name optional name for the union (anonymous if `null`)
         * @param variants the set of overlapping field types
         */
        @JvmStatic fun union(name: String?, variants: List<Type>): Union = Union(name, variants)

        /**
         * Creates a function type with the given parameter and return types.
         *
         * @param params the ordered list of parameter types
         * @param ret the return type ([Void] for functions that return nothing)
         * @param vararg `true` for C-style variadic functions (callers may pass extra untyped arguments)
         */
        @JvmStatic @JvmOverloads fun function(params: List<Type>, ret: Type, vararg: Boolean = false): Function = Function(params, ret, vararg)

        /**
         * Creates a [ClassRef] to the class named [name].
         * The name is resolved against [Module.classes] at verification time.
         *
         * @param name the fully-qualified or module-local class name
         */
        @JvmStatic fun classRef(name: String): ClassRef = ClassRef(name)

        /**
         * Creates an [InterfaceRef] to the interface named [name].
         * The name is resolved against [Module.interfaces] at verification time.
         *
         * @param name the fully-qualified or module-local interface name
         */
        @JvmStatic fun interfaceRef(name: String): InterfaceRef = InterfaceRef(name)

        /**
         * Wraps [inner] in a [Nullable] type. Prefer [reference] with `nullable = true`
         * for GC-managed reference types. Use [Nullable] for wrapping primitive or struct types.
         *
         * @param inner the non-nullable base type to wrap
         */
        @JvmStatic fun nullable(inner: Type): Nullable = Nullable(inner)

        // --- Constant factories ---

        /**
         * Creates a [Constant.I1] boolean constant.
         *
         * @param value the boolean value
         */
        @JvmStatic fun i1(value: Boolean): Constant.I1 = Constant.I1(value)

        /**
         * Creates a [Constant.I8] byte constant from an [Int] (truncated to 8 bits).
         *
         * @param value the integer value, truncated to a byte
         */
        @JvmStatic fun i8(value: Int): Constant.I8 = Constant.I8(value.toByte())

        /**
         * Creates a [Constant.I16] short constant from an [Int] (truncated to 16 bits).
         *
         * @param value the integer value, truncated to a short
         */
        @JvmStatic fun i16(value: Int): Constant.I16 = Constant.I16(value.toShort())

        /**
         * Creates a [Constant.I32] 32-bit integer constant.
         *
         * @param value the integer value
         */
        @JvmStatic fun i32(value: Int): Constant.I32 = Constant.I32(value)

        /**
         * Creates a [Constant.I64] 64-bit integer constant.
         *
         * @param value the long value
         */
        @JvmStatic fun i64(value: Long): Constant.I64 = Constant.I64(value)

        /**
         * Creates a [Constant.I128] 128-bit integer constant (lower 64 bits stored as [Long]).
         *
         * @param value the lower 64 bits of the 128-bit constant
         */
        @JvmStatic fun i128(value: Long): Constant.I128 = Constant.I128(value)

        /**
         * Creates a [Constant.F16] half-precision floating-point constant.
         *
         * @param value the floating-point value (stored widened as [Float])
         */
        @JvmStatic fun f16(value: Float): Constant.F16 = Constant.F16(value)

        /**
         * Creates a [Constant.BF16] bfloat16 constant.
         *
         * @param value the floating-point value (stored widened as [Float])
         */
        @JvmStatic fun bf16(value: Float): Constant.BF16 = Constant.BF16(value)

        /**
         * Creates a [Constant.F32] single-precision floating-point constant.
         *
         * @param value the float value
         */
        @JvmStatic fun f32(value: Float): Constant.F32 = Constant.F32(value)

        /**
         * Creates a [Constant.F64] double-precision floating-point constant.
         *
         * @param value the double value
         */
        @JvmStatic fun f64(value: Double): Constant.F64 = Constant.F64(value)

        /**
         * Creates a [Constant.StringConst] representing a string literal.
         * By default the string is null-terminated (C string style).
         *
         * @param value the string contents
         * @param nullTerminated whether to append a `\0` terminator byte (default `true`)
         */
        @JvmStatic @JvmOverloads fun string(value: String, nullTerminated: Boolean = true): Constant.StringConst = Constant.StringConst(value, nullTerminated)

        /**
         * Returns the singleton [Constant.NullPtr] (a null opaque pointer constant).
         */
        @JvmStatic fun nullPtr(): Constant.NullPtr = Constant.NullPtr

        /**
         * Returns the singleton [Constant.NullRef] (a null GC reference constant).
         */
        @JvmStatic fun nullRef(): Constant.NullRef = Constant.NullRef

        /**
         * Creates a [Constant.ZeroInitializer] for [type].
         * All bytes in the resulting value are zero. Works for any type, including aggregates.
         *
         * @param type the type to zero-initialize
         */
        @JvmStatic fun zero(type: Type): Constant.ZeroInitializer = Constant.ZeroInitializer(type)

        /**
         * Creates a [Constant.Undef] for [type]. The value is unspecified but safe to read.
         * The optimizer may substitute any legal value of [type].
         *
         * @param type the type of the undefined value
         */
        @JvmStatic fun undef(type: Type): Constant.Undef = Constant.Undef(type)

        /**
         * Creates a [Constant.Poison] for [type]. Any instruction that observes this value
         * has undefined behavior, enabling aggressive optimizer transforms.
         *
         * @param type the type of the poison value
         */
        @JvmStatic fun poison(type: Type): Constant.Poison = Constant.Poison(type)

        // --- Param factory ---

        /**
         * Creates a [Param] with the given [name] and [type].
         * Convenience alias that allows `Type.param("x", Type.I32)` at call sites.
         *
         * @param name the parameter's source-level name
         * @param type the parameter's IR type
         */
        @JvmStatic fun param(name: String, type: Type): Param = Param(name, type)
    }
}

/**
 * A variant in a [Type.TaggedUnion]. Each variant has a unique [tag] value and zero or more [fields].
 *
 * At runtime, the active variant is identified by the tagged union's discriminant field (whose type
 * is [Type.TaggedUnion.tagType]). Use [org.kgen.ir.instructions.ConstructVariant] to build a value
 * of a specific variant, [org.kgen.ir.instructions.GetTag] to read the active discriminant, and
 * [org.kgen.ir.instructions.TagSwitch] to branch on it.
 *
 * @param name the variant's source-level name (e.g., `"Some"`, `"Err"`)
 * @param tag the unique discriminant value that identifies this variant at runtime
 * @param fields the payload field types for this variant (may be empty for unit-like variants)
 */
data class TaggedVariant(
    val name: String,
    val tag: Long,
    val fields: List<Type>,
)

package org.kgen.ir

/**
 * A reference to a global variable. Printed as `@name` in IR text.
 *
 * A [GlobalRef] is the SSA [Value] form of a [Global] — it carries the global's name and
 * the pointer type used to access it. Pass a [GlobalRef] wherever a pointer operand is
 * expected (e.g., as the address operand of a `load` or `store` instruction).
 *
 * The [type] field should be the pointer type that points to the global's storage type
 * (e.g., `Type.Pointer(Type.I32)` for a global of type `i32`). On opaque-pointer targets
 * (LLVM-style), use `Type.OpaquePointer` instead.
 *
 * ```java
 * // Define a global in the module
 * module.addGlobal(new Global("counter", Type.I32));
 *
 * // Reference it in a function — type is a pointer to the global's type
 * var ref = new GlobalRef("counter", Type.pointer(Type.I32));
 * Value loaded = builder.load(Type.I32, ref);
 * ```
 *
 * @param name the global variable's symbol name (matches [Global.name])
 * @param type the type of the reference — typically a [Type.Pointer] to the global's value type,
 *   or [Type.OpaquePointer] on opaque-pointer targets
 *
 * @see Global
 * @see Value
 */
data class GlobalRef(override val name: String, override val type: Type) : Value

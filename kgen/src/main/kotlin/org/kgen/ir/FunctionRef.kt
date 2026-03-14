package org.kgen.ir

/**
 * A reference to a function. Carries the full [Type.Function] signature for call verification.
 *
 * A [FunctionRef] is the [Value] form of a function symbol — it can be passed as the callee
 * operand to a `call` or `invoke` instruction, stored in a function-pointer global, or used
 * as an initializer. The [type] is always a [Type.Function] so the verifier can confirm that
 * the call site's argument types and return type match the declared signature.
 *
 * Printed as `@name` in IR text, identical to [GlobalRef], but typed as a function.
 *
 * ```java
 * // Resolve a function reference for a direct call
 * var printRef = new FunctionRef(
 *     "printf",
 *     Type.function(List.of(Type.OpaquePointer), Type.I32, /* vararg */ true)
 * );
 * builder.call(printRef, List.of(formatStrPtr, argValue));
 *
 * // Pass a function pointer as an argument
 * builder.call(callbackInvoker, List.of(printRef, userData));
 * ```
 *
 * @param name the function's symbol name (matches [IrFunction.name])
 * @param type the complete function signature including parameter types, return type, and vararg flag
 *
 * @see IrFunction
 * @see Type.Function
 * @see Value
 */
data class FunctionRef(override val name: String, override val type: Type.Function) : Value

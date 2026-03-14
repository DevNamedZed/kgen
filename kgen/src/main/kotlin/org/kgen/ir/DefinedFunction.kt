package org.kgen.ir

import org.kgen.ir.build.IrBuilder

/**
 * A handle to a function currently being defined via [IrBuilder].
 *
 * Returned by [IrBuilder.defineFunction]. Provides access to the function's
 * parameters and acts as a [Value] that can be passed directly to `call`
 * instructions (no manual [FunctionRef] construction needed).
 *
 * Implements [AutoCloseable] for try-with-resources scoping — closing the
 * handle finalizes the function.
 *
 * ```java
 * try (DefinedFunction fn = ir.defineFunction("add",
 *         List.of(Param.of("a", Type.I32), Param.of("b", Type.I32)), Type.I32)) {
 *     ir.appendBlock("entry");
 *     Value sum = b.add(fn.param("a"), fn.param("b"));
 *     b.ret(sum);
 * }
 * ```
 *
 * @see IrBuilder.defineFunction
 */
class DefinedFunction internal constructor(
    private val builder: IrBuilder,
    private val parameters: List<Parameter>,
    private val ref: FunctionRef,
) : Value, AutoCloseable {

    override val name: String get() = ref.name
    override val type: Type get() = ref.type

    /**
     * Returns the parameter at the given index.
     *
     * @throws IndexOutOfBoundsException if [index] is out of range
     */
    fun param(index: Int): Value = parameters[index]

    /**
     * Returns the parameter with the given name.
     *
     * @throws NoSuchElementException if no parameter with [paramName] exists
     */
    fun param(paramName: String): Value =
        parameters.first { it.name == paramName }

    /**
     * Returns the [FunctionRef] for this function, for use in call instructions.
     */
    fun ref(): FunctionRef = ref

    /**
     * Finalizes the function. Called automatically when used with
     * try-with-resources or Kotlin's `use {}`.
     */
    override fun close() {
        builder.finalizeFunction()
    }
}

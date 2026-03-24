package org.kgen.ir

/**
 * A function's formal parameter, usable as a [Value] operand within the function body.
 *
 * Parameters are created by [org.kgen.ir.build.ModuleBuilder.createFunction] and returned
 * as a list. They can be used directly as operands to instructions:
 *
 * ```java
 * List<Parameter> params = ir.createFunction("add",
 *     List.of(new Param("a", Type.I32), new Param("b", Type.I32)), Type.I32);
 * ir.appendBlock("entry");
 * Value sum = ir.add(params.get(0), params.get(1));
 * ir.ret(sum);
 * ```
 *
 * @param name the parameter name (e.g., `"argc"`, `"ptr"`) — used in IR text output
 * @param type the parameter's IR type
 * @param index 0-based position in the function's parameter list
 * @param attributes parameter-level attributes (e.g., `zeroext`, `signext`, `noalias`, `nonnull`)
 */
data class Parameter(
    override val name: String,
    override val type: Type,
    val index: Int,
    val attributes: Set<ParamAttribute> = emptySet(),
) : Value

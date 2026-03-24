// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.Type

/**
 * Emission interface for ssa instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface SsaInstructionSet : InstructionSet {

    /**
     * Phi node: merges values from predecessor basic blocks at a control flow join point.
     *
     * Emits a [Phi] instruction into the current block.
     *
     * @param resultType the type of the merged value
     * @param incoming source operand map
     * @return the SSA value produced by this instruction
     */
    fun phi(resultType: Type, incoming: List<Pair<Value, BlockRef>>): Value

    fun phiByName(resultType: Type, incoming: List<Pair<Value, String>>): Value =
        phi(resultType, incoming.map { it.first to BlockRef(it.second) })

    /**
     * Conditional value selection: `dest = condition ? trueValue : falseValue`.
     *
     * Emits a [Select] instruction into the current block.
     *
     * @param condition source operand
     * @param trueValue source operand
     * @param falseValue source operand
     * @return the SSA value produced by this instruction
     */
    fun select(condition: Value, trueValue: Value, falseValue: Value): Value

    /**
     * Freeze a potentially poison or undef value to an arbitrary but fixed value.
     *
     * Emits a [Freeze] instruction into the current block.
     *
     * @param value source operand
     * @return the SSA value produced by this instruction
     */
    fun freeze(value: Value): Value

    /**
     * Type-refinement pseudo-instruction.

Produces a new SSA value with a narrowed type for a value that has been
proven to be of that type at a given program point (e.g., after a guard).
Emits no machine code — eliminated before instruction selection.
     *
     * Emits a [PiNode] instruction into the current block.
     *
     * @param base source operand
     * @param refinedType the type
     * @return the SSA value produced by this instruction
     */
    fun piNode(base: Value, refinedType: Type): Value
}

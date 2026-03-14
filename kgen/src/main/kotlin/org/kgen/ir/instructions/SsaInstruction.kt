// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.Type

/**
 * SSA form management instructions.
 *
 * Handles value selection and merging at control flow join points.
 */
sealed interface SsaInstruction : Instruction {
    override val category get() = IrCategory.SSA
}

// --- SSA instructions ---

/**
 * Phi node: merges values from predecessor basic blocks at a control flow join point.
 *
 * @param dest the SSA result reference
 * @param incoming incoming values
 */
data class Phi(
    val dest: InstructionRef,
    val incoming: List<Pair<Value, BlockRef>>,
) : SsaInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = incoming.map { it.first }
}

/**
 * Conditional value selection: `dest = condition ? trueValue : falseValue`.
 *
 * @param dest the SSA result reference
 * @param condition operand value
 * @param trueValue operand value
 * @param falseValue operand value
 */
data class Select(
    val dest: InstructionRef,
    val condition: Value,
    val trueValue: Value,
    val falseValue: Value,
) : SsaInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(condition, trueValue, falseValue)
}

/**
 * Freeze a potentially poison or undef value to an arbitrary but fixed value.
 *
 * @param dest the SSA result reference
 * @param value operand value
 */
data class Freeze(
    val dest: InstructionRef,
    val value: Value,
) : SsaInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Type-refinement pseudo-instruction.
 *
 * Produces a new SSA value with a narrowed type for a value that has been
 * proven to be of that type at a given program point (e.g., after a guard).
 * Emits no machine code — eliminated before instruction selection.
 *
 * @param dest the SSA result reference
 * @param base operand value
 * @param refinedType configuration
 */
data class PiNode(
    val dest: InstructionRef,
    val base: Value,
    val refinedType: Type,
) : SsaInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(base)
}


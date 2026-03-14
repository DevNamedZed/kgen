// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.Type
import org.kgen.ir.VectorReduceOp

/**
 * SIMD vector manipulation instructions.
 *
 * Vector instructions operate on fixed-width vector types, supporting
 * element extraction, insertion, lane shuffling, scalar broadcasting,
 * and horizontal reductions.
 */
sealed interface VectorInstruction : Instruction {
    override val category get() = IrCategory.VECTOR
}

// --- Vector instructions ---

/**
 * Extracts a single scalar element from a vector at a dynamic index.
 *
 * @param dest the SSA result reference
 * @param vector operand value
 * @param index operand value
 */
data class ExtractElement(
    val dest: InstructionRef,
    val vector: Value,
    val index: Value,
) : VectorInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(vector, index)
}

/**
 * Inserts a scalar element into a vector at a dynamic index.
 *
 * @param dest the SSA result reference
 * @param vector operand value
 * @param element operand value
 * @param index operand value
 */
data class InsertElement(
    val dest: InstructionRef,
    val vector: Value,
    val element: Value,
    val index: Value,
) : VectorInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(vector, element, index)
}

/**
 * Shuffles lanes from two vectors according to a compile-time mask.
 *
 * @param dest the SSA result reference
 * @param v1 operand value
 * @param v2 operand value
 * @param mask configuration
 */
data class ShuffleVector(
    val dest: InstructionRef,
    val v1: Value,
    val v2: Value,
    val mask: List<Int>,
) : VectorInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(v1, v2)
}

/**
 * Broadcasts a scalar value into every lane of a vector.
 *
 * @param dest the SSA result reference
 * @param scalar operand value
 * @param vectorType configuration
 */
data class Splat(
    val dest: InstructionRef,
    val scalar: Value,
    val vectorType: Type.Vector,
) : VectorInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(scalar)
}

/**
 * Reduces all lanes of a vector into a single scalar using a reduction operator.
 *
 * @param dest the SSA result reference
 * @param op configuration
 * @param vector operand value
 */
data class VectorReduce(
    val dest: InstructionRef,
    val op: VectorReduceOp,
    val vector: Value,
) : VectorInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(vector)
}


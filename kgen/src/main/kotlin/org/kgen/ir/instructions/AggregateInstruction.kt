// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.Type

/**
 * Instructions that operate on aggregate types (structs and arrays).
 */
sealed interface AggregateInstruction : Instruction {
    override val category get() = IrCategory.AGGREGATE
}

// --- Aggregate instructions ---

/**
 * Extracts a value from a nested aggregate at compile-time indices.
 *
 * @param dest the SSA result reference
 * @param aggregate operand value
 * @param indices configuration
 */
data class ExtractValue(
    val dest: InstructionRef,
    val aggregate: Value,
    val indices: List<Int>,
) : AggregateInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(aggregate)
}

/**
 * Inserts a value into a nested aggregate at compile-time indices.
 *
 * @param dest the SSA result reference
 * @param aggregate operand value
 * @param element operand value
 * @param indices configuration
 */
data class InsertValue(
    val dest: InstructionRef,
    val aggregate: Value,
    val element: Value,
    val indices: List<Int>,
) : AggregateInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(aggregate, element)
}


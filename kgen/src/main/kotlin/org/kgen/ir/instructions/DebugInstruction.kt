// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.Constant

/**
 * Debug metadata and optimizer hint instructions.
 *
 * Debug instructions carry source-level information without affecting program semantics.
 */
sealed interface DebugInstruction : Instruction {
    override val category get() = IrCategory.DEBUG
}

// --- Debug instructions ---

/**
 * Records source location metadata.
 *
 * @param line configuration
 * @param col configuration
 * @param scope configuration
 * @param inlinedAt configuration
 */
data class DebugLoc(
    val line: Int,
    val col: Int,
    val scope: String,
    val inlinedAt: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.NONE
    override val operands get() = emptyList<Value>()
}

/**
 * Associates a source-level variable with an SSA value.
 *
 * @param variable configuration
 * @param value operand value
 * @param expression configuration
 */
data class DebugValue(
    val variable: String,
    val value: Value,
    val expression: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.NONE
    override val operands get() = listOf(value)
}

/**
 * Associates a source-level variable with a memory address.
 *
 * @param variable configuration
 * @param address operand value
 * @param expression configuration
 */
data class DebugDeclare(
    val variable: String,
    val address: Value,
    val expression: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.NONE
    override val operands get() = listOf(address)
}

/**
 * Optimizer hint asserting that a condition is always true.
 *
 * @param condition operand value
 */
data class Assume(
    val condition: Value,
) : DebugInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.NONE
    override val operands get() = listOf(condition)
}

/**
 * Branch prediction hint: tells the optimizer that value is likely equal to expected.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param expected operand value
 */
data class Expect(
    val dest: InstructionRef,
    val value: Value,
    val expected: Constant,
) : DebugInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.NONE
    override val operands get() = listOf(value, expected)
}


// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.FastMathFlags

/**
 * Integer and floating-point comparison instructions.
 *
 * Comparison instructions compare two operands and produce a boolean (`i1`) result.
 */
sealed interface ComparisonInstruction : Instruction {
    override val category get() = IrCategory.COMPARISON
}

// --- Integer comparison ---

/**
 * Integer comparison: `dest = lhs <predicate> rhs` (result is `i1`).
 *
 * @param dest the SSA result reference
 * @param predicate configuration
 * @param lhs operand value
 * @param rhs operand value
 */
data class ICmp(
    val dest: InstructionRef,
    val predicate: ICmpPredicate,
    val lhs: Value,
    val rhs: Value,
) : ComparisonInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

// --- Floating-point comparison ---

/**
 * Floating-point comparison: `dest = lhs <predicate> rhs` (result is `i1`).
 *
 * @param dest the SSA result reference
 * @param predicate configuration
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FCmp(
    val dest: InstructionRef,
    val predicate: FCmpPredicate,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ComparisonInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}


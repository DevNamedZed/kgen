// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

/**
 * Bitwise logic and shift instructions.
 *
 * All bitwise instructions operate on integer types and produce a single result
 * of the same type as their operands.
 */
sealed interface BitwiseInstruction : Instruction {
    override val category get() = IrCategory.BITWISE
}

// --- Logical operations ---

/**
 * Bitwise AND: `dest = lhs & rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class And(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Bitwise OR: `dest = lhs | rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class Or(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Bitwise XOR: `dest = lhs ^ rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class Xor(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Bitwise NOT (complement): `dest = ~operand`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Not(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

// --- Shift operations ---

/**
 * Shift left: `dest = lhs << rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param nuw flag (default: false)
 * @param nsw flag (default: false)
 */
data class Shl(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Logical shift right: `dest = lhs >>> rhs` (unsigned).
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param exact flag (default: false)
 */
data class LShr(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Arithmetic shift right: `dest = lhs >> rhs` (signed).
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param exact flag (default: false)
 */
data class AShr(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

// --- Rotate operations ---

/**
 * Rotate left (hardware instruction form): maps to target `rol`.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param amount operand value
 */
data class Rotl(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, amount)
}

/**
 * Rotate right (hardware instruction form): maps to target `ror`.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param amount operand value
 */
data class Rotr(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, amount)
}

// --- Bit counting and manipulation ---

/**
 * Count leading zeros: `dest = ctlz(operand)`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 * @param isZeroPoison flag (default: false)
 */
data class Ctlz(
    val dest: InstructionRef,
    val operand: Value,
    val isZeroPoison: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Count trailing zeros: `dest = cttz(operand)`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 * @param isZeroPoison flag (default: false)
 */
data class Cttz(
    val dest: InstructionRef,
    val operand: Value,
    val isZeroPoison: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Population count: `dest = ctpop(operand)`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Ctpop(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Byte swap: reverses the byte order of `operand`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class BSwap(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Bit reverse: reverses the order of all bits in `operand`.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class BitReverse(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}


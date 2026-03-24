// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Emission interface for bitwise instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface BitwiseInstructionSet : InstructionSet {

    /**
     * Bitwise AND: `dest = lhs & rhs`.
     *
     * Emits a [And] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun and(lhs: Value, rhs: Value): Value

    /**
     * Bitwise OR: `dest = lhs | rhs`.
     *
     * Emits a [Or] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun or(lhs: Value, rhs: Value): Value

    /**
     * Bitwise XOR: `dest = lhs ^ rhs`.
     *
     * Emits a [Xor] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun xor(lhs: Value, rhs: Value): Value

    /**
     * Bitwise NOT (complement): `dest = ~operand`.
     *
     * Emits a [Not] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun not(operand: Value): Value

    /**
     * Shift left: `dest = lhs << rhs`.
     *
     * Emits a [Shl] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param nuw Boolean
     * @param nsw Boolean
     * @return the SSA value produced by this instruction
     */
    fun shl(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value

    /**
     * Logical shift right: `dest = lhs >>> rhs` (unsigned).
     *
     * Emits a [LShr] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param exact Boolean
     * @return the SSA value produced by this instruction
     */
    fun lshr(lhs: Value, rhs: Value, exact: Boolean = false): Value

    /**
     * Arithmetic shift right: `dest = lhs >> rhs` (signed).
     *
     * Emits a [AShr] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param exact Boolean
     * @return the SSA value produced by this instruction
     */
    fun ashr(lhs: Value, rhs: Value, exact: Boolean = false): Value

    /**
     * Rotate left (hardware instruction form): maps to target `rol`.
     *
     * Emits a [Rotl] instruction into the current block.
     *
     * @param value source operand
     * @param amount source operand
     * @return the SSA value produced by this instruction
     */
    fun rotl(value: Value, amount: Value): Value

    /**
     * Rotate right (hardware instruction form): maps to target `ror`.
     *
     * Emits a [Rotr] instruction into the current block.
     *
     * @param value source operand
     * @param amount source operand
     * @return the SSA value produced by this instruction
     */
    fun rotr(value: Value, amount: Value): Value

    /**
     * Count leading zeros: `dest = ctlz(operand)`.
     *
     * Emits a [Ctlz] instruction into the current block.
     *
     * @param operand source operand
     * @param isZeroPoison Boolean
     * @return the SSA value produced by this instruction
     */
    fun ctlz(operand: Value, isZeroPoison: Boolean = false): Value

    /**
     * Count trailing zeros: `dest = cttz(operand)`.
     *
     * Emits a [Cttz] instruction into the current block.
     *
     * @param operand source operand
     * @param isZeroPoison Boolean
     * @return the SSA value produced by this instruction
     */
    fun cttz(operand: Value, isZeroPoison: Boolean = false): Value

    /**
     * Population count: `dest = ctpop(operand)`.
     *
     * Emits a [Ctpop] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun ctpop(operand: Value): Value

    /**
     * Byte swap: reverses the byte order of `operand`.
     *
     * Emits a [BSwap] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun bswap(operand: Value): Value

    /**
     * Bit reverse: reverses the order of all bits in `operand`.
     *
     * Emits a [BitReverse] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun bitReverse(operand: Value): Value
}

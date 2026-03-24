// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.FastMathFlags

/**
 * Emission interface for comparison instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ComparisonInstructionSet : InstructionSet {

    /**
     * Integer comparison: `dest = lhs <predicate> rhs` (result is `i1`).
     *
     * Emits a [ICmp] instruction into the current block.
     *
     * @param predicate ICmpPredicate
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun icmp(predicate: ICmpPredicate, lhs: Value, rhs: Value): Value

    /**
     * Floating-point comparison: `dest = lhs <predicate> rhs` (result is `i1`).
     *
     * Emits a [FCmp] instruction into the current block.
     *
     * @param predicate FCmpPredicate
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fcmp(predicate: FCmpPredicate, lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value
}

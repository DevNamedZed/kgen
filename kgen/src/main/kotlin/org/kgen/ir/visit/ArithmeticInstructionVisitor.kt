// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for arithmetic instructions.
 *
 * Implement this interface to receive callbacks for arithmetic instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ArithmeticInstructionVisitor : InstructionVisitor {

    fun visitAdd(instruction: Add) {}

    fun visitSub(instruction: Sub) {}

    fun visitMul(instruction: Mul) {}

    fun visitUDiv(instruction: UDiv) {}

    fun visitSDiv(instruction: SDiv) {}

    fun visitURem(instruction: URem) {}

    fun visitSRem(instruction: SRem) {}

    fun visitNeg(instruction: Neg) {}

    fun visitSAddOverflow(instruction: SAddOverflow) {}

    fun visitUAddOverflow(instruction: UAddOverflow) {}

    fun visitSSubOverflow(instruction: SSubOverflow) {}

    fun visitUSubOverflow(instruction: USubOverflow) {}

    fun visitSMulOverflow(instruction: SMulOverflow) {}

    fun visitUMulOverflow(instruction: UMulOverflow) {}

    fun visitSAddSat(instruction: SAddSat) {}

    fun visitUAddSat(instruction: UAddSat) {}

    fun visitSSubSat(instruction: SSubSat) {}

    fun visitUSubSat(instruction: USubSat) {}

    fun visitSMin(instruction: SMin) {}

    fun visitSMax(instruction: SMax) {}

    fun visitUMin(instruction: UMin) {}

    fun visitUMax(instruction: UMax) {}

    fun visitAbs(instruction: Abs) {}

    fun visitFAdd(instruction: FAdd) {}

    fun visitFSub(instruction: FSub) {}

    fun visitFMul(instruction: FMul) {}

    fun visitFDiv(instruction: FDiv) {}

    fun visitFRem(instruction: FRem) {}

    fun visitFNeg(instruction: FNeg) {}

    fun visitFAbs(instruction: FAbs) {}

    fun visitFMA(instruction: FMA) {}

    fun visitFMin(instruction: FMin) {}

    fun visitFMax(instruction: FMax) {}

    fun visitSqrt(instruction: Sqrt) {}

    fun visitCeil(instruction: Ceil) {}

    fun visitFloor(instruction: Floor) {}

    fun visitRound(instruction: Round) {}

    fun visitFTrunc(instruction: FTrunc) {}

    fun visitCopySign(instruction: CopySign) {}
}

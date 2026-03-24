// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.FastMathFlags

/**
 * Default implementation of [ArithmeticInstructionSet] backed by an [InstructionSink].
 */
internal class ArithmeticInstructionSetImpl(private val sink: InstructionSink) : ArithmeticInstructionSet {

    override fun add(lhs: Value, rhs: Value, nuw: Boolean, nsw: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(Add(it, lhs, rhs, nuw, nsw)) }

    override fun sub(lhs: Value, rhs: Value, nuw: Boolean, nsw: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(Sub(it, lhs, rhs, nuw, nsw)) }

    override fun mul(lhs: Value, rhs: Value, nuw: Boolean, nsw: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(Mul(it, lhs, rhs, nuw, nsw)) }

    override fun udiv(lhs: Value, rhs: Value, exact: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(UDiv(it, lhs, rhs, exact)) }

    override fun sdiv(lhs: Value, rhs: Value, exact: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(SDiv(it, lhs, rhs, exact)) }

    override fun urem(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(URem(it, lhs, rhs)) }

    override fun srem(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SRem(it, lhs, rhs)) }

    override fun neg(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Neg(it, operand)) }

    override fun saddOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SAddOverflow(it, lhs, rhs)) }

    override fun uaddOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(UAddOverflow(it, lhs, rhs)) }

    override fun ssubOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SSubOverflow(it, lhs, rhs)) }

    override fun usubOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(USubOverflow(it, lhs, rhs)) }

    override fun smulOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SMulOverflow(it, lhs, rhs)) }

    override fun umulOverflow(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(UMulOverflow(it, lhs, rhs)) }

    override fun saddSat(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SAddSat(it, lhs, rhs)) }

    override fun uaddSat(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(UAddSat(it, lhs, rhs)) }

    override fun ssubSat(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SSubSat(it, lhs, rhs)) }

    override fun usubSat(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(USubSat(it, lhs, rhs)) }

    override fun smin(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SMin(it, lhs, rhs)) }

    override fun smax(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(SMax(it, lhs, rhs)) }

    override fun umin(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(UMin(it, lhs, rhs)) }

    override fun umax(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(UMax(it, lhs, rhs)) }

    override fun abs(operand: Value, isIntMin: Boolean): Value =
        sink.nextRef(operand.type).also { sink.emit(Abs(it, operand, isIntMin)) }

    override fun fadd(lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(lhs.type).also { sink.emit(FAdd(it, lhs, rhs, fastMath)) }

    override fun fsub(lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(lhs.type).also { sink.emit(FSub(it, lhs, rhs, fastMath)) }

    override fun fmul(lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(lhs.type).also { sink.emit(FMul(it, lhs, rhs, fastMath)) }

    override fun fdiv(lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(lhs.type).also { sink.emit(FDiv(it, lhs, rhs, fastMath)) }

    override fun frem(lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(lhs.type).also { sink.emit(FRem(it, lhs, rhs, fastMath)) }

    override fun fneg(operand: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(operand.type).also { sink.emit(FNeg(it, operand, fastMath)) }

    override fun fabs(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(FAbs(it, operand)) }

    override fun fma(a: Value, b: Value, c: Value): Value =
        sink.nextRef(a.type).also { sink.emit(FMA(it, a, b, c)) }

    override fun fmin(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(FMin(it, lhs, rhs)) }

    override fun fmax(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(FMax(it, lhs, rhs)) }

    override fun sqrt(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Sqrt(it, operand)) }

    override fun ceil(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Ceil(it, operand)) }

    override fun floor(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Floor(it, operand)) }

    override fun round(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Round(it, operand)) }

    override fun ftrunc(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(FTrunc(it, operand)) }

    override fun copySign(magnitude: Value, sign: Value): Value =
        sink.nextRef(magnitude.type).also { sink.emit(CopySign(it, magnitude, sign)) }
}

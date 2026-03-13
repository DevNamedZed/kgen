package org.kgen.ir.instructions

import org.kgen.ir.FastMathFlags
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface ArithmeticInstruction : Instruction {
    override val category get() = IrCategory.ARITHMETIC
}

data class Add(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Sub(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Mul(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class URem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SRem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Neg(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SAddOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UAddOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SSubOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class USubOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SMulOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UMulOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SAddSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UAddSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SSubSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class USubSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class SMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class UMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Abs(
    val dest: InstructionRef,
    val operand: Value,
    val isIntMin: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FAdd(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FSub(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FMul(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FRem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FNeg(
    val dest: InstructionRef,
    val operand: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FAbs(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FMA(
    val dest: InstructionRef,
    val a: Value,
    val b: Value,
    val c: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class FMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Sqrt(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Ceil(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Floor(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Round(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class Trunc(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

data class CopySign(
    val dest: InstructionRef,
    val magnitude: Value,
    val sign: Value,
) : ArithmeticInstruction {
    override val result get() = dest
}

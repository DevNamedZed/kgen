// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.FastMathFlags

/**
 * Integer and floating-point computation instructions.
 *
 * All arithmetic instructions produce a single result value and consume one or more
 * operands of matching type. Integer operations support optional overflow flags
 * (`nuw`/`nsw`) that cause the result to be poison on overflow. Floating-point
 * operations support fast-math flags for aggressive optimization.
 *
 * Arithmetic instructions are always legal in native compilation contexts.
 */
sealed interface ArithmeticInstruction : Instruction {
    override val category get() = IrCategory.ARITHMETIC
}

// --- Integer arithmetic ---

/**
 * Integer addition: `dest = lhs + rhs`.
 *
 * Result type matches the operand type. If `nuw` (no unsigned wrap) is set,
 * unsigned overflow produces poison. If `nsw` (no signed wrap) is set,
 * signed overflow produces poison. Both flags default to false (wrapping two's complement).
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param nuw flag (default: false)
 * @param nsw flag (default: false)
 */
data class Add(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Integer subtraction: `dest = lhs - rhs`.
 *
 * Overflow semantics match Add: `nuw` and `nsw` flags control poison on wrap.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param nuw flag (default: false)
 * @param nsw flag (default: false)
 */
data class Sub(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Integer multiplication: `dest = lhs * rhs`.
 *
 * Overflow semantics match Add: `nuw` and `nsw` flags control poison on wrap.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param nuw flag (default: false)
 * @param nsw flag (default: false)
 */
data class Mul(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned integer division: `dest = lhs / rhs` (unsigned).
 *
 * Division by zero is undefined behavior. If `exact` is true and the result
 * is not exact (i.e., `lhs` is not evenly divisible by `rhs`), the result is poison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param exact flag (default: false)
 */
data class UDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.MAY_TRAP
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed integer division: `dest = lhs / rhs` (signed).
 *
 * Division by zero and `INT_MIN / -1` are undefined behavior. If `exact` is true
 * and the result is not exact, the result is poison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param exact flag (default: false)
 */
data class SDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.MAY_TRAP
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned integer remainder: `dest = lhs % rhs` (unsigned).
 *
 * Division by zero is undefined behavior.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class URem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.MAY_TRAP
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed integer remainder: `dest = lhs % rhs` (signed).
 *
 * The result has the same sign as the dividend. Division by zero is undefined behavior.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SRem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.MAY_TRAP
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Integer negation: `dest = -operand` (equivalent to `0 - operand`).
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Neg(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

// --- Overflow-checked arithmetic ---

/**
 * Signed addition with overflow detection: `dest = {lhs + rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SAddOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned addition with overflow detection: `dest = {lhs + rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if unsigned overflow (carry) occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class UAddOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed subtraction with overflow detection: `dest = {lhs - rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SSubOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned subtraction with overflow detection: `dest = {lhs - rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if unsigned underflow (borrow) occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class USubOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed multiplication with overflow detection: `dest = {lhs * rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SMulOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned multiplication with overflow detection: `dest = {lhs * rhs, overflow_flag}`.
 *
 * Returns a struct `{T, i1}` where the second element is true if unsigned overflow occurred.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class UMulOverflow(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

// --- Saturating arithmetic ---

/**
 * Signed saturating addition: `dest = clamp(lhs + rhs, INT_MIN, INT_MAX)`.
 *
 * On overflow, the result clamps to the minimum or maximum representable value.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SAddSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned saturating addition: `dest = min(lhs + rhs, UINT_MAX)`.
 *
 * On overflow, the result clamps to the maximum representable value.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class UAddSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed saturating subtraction: `dest = clamp(lhs - rhs, INT_MIN, INT_MAX)`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SSubSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned saturating subtraction: `dest = max(lhs - rhs, 0)`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class USubSat(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

// --- Min / Max / Abs ---

/**
 * Signed minimum: `dest = min(lhs, rhs)` using signed comparison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Signed maximum: `dest = max(lhs, rhs)` using signed comparison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class SMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned minimum: `dest = min(lhs, rhs)` using unsigned comparison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class UMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Unsigned maximum: `dest = max(lhs, rhs)` using unsigned comparison.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class UMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Integer absolute value: `dest = |operand|`.
 *
 * If `isIntMin` is true and the operand is `INT_MIN` (which has no positive representation),
 * the result is poison. If false, `INT_MIN` returns `INT_MIN` (wrapping behavior).
 *
 * @param dest the SSA result reference
 * @param operand operand value
 * @param isIntMin flag (default: false)
 */
data class Abs(
    val dest: InstructionRef,
    val operand: Value,
    val isIntMin: Boolean = false,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

// --- Floating-point arithmetic ---

/**
 * Floating-point addition: `dest = lhs + rhs`.
 *
 * Follows IEEE 754 semantics unless fast-math flags permit reassociation or other transformations.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FAdd(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point subtraction: `dest = lhs - rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FSub(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point multiplication: `dest = lhs * rhs`.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FMul(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point division: `dest = lhs / rhs`.
 *
 * Division by zero follows IEEE 754 rules (produces infinity or NaN) unless
 * fast-math flags alter behavior.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FDiv(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point remainder: `dest = lhs % rhs` (IEEE 754 remainder).
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FRem(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point negation: `dest = -operand`.
 *
 * Flips the sign bit. NaN propagation follows IEEE 754.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 * @param fastMath flag (default: FastMathFlags.NONE)
 */
data class FNeg(
    val dest: InstructionRef,
    val operand: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Floating-point absolute value: `dest = |operand|`.
 *
 * Clears the sign bit. Works correctly with NaN and infinity.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class FAbs(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Fused multiply-add: `dest = a * b + c` with a single rounding step.
 *
 * More precise than separate multiply and add because the intermediate product
 * is not rounded. Maps to hardware FMA instructions where available.
 *
 * @param dest the SSA result reference
 * @param a operand value
 * @param b operand value
 * @param c operand value
 */
data class FMA(
    val dest: InstructionRef,
    val a: Value,
    val b: Value,
    val c: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(a, b, c)
}

/**
 * Floating-point minimum: `dest = min(lhs, rhs)`.
 *
 * If either operand is NaN, the result is NaN. Follows IEEE 754-2008 `minNum` semantics.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class FMin(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

/**
 * Floating-point maximum: `dest = max(lhs, rhs)`.
 *
 * If either operand is NaN, the result is NaN. Follows IEEE 754-2008 `maxNum` semantics.
 *
 * @param dest the SSA result reference
 * @param lhs operand value
 * @param rhs operand value
 */
data class FMax(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE_COMMUTATIVE
    override val operands get() = listOf(lhs, rhs)
}

// --- Floating-point math functions ---

/**
 * Square root: `dest = sqrt(operand)`.
 *
 * For negative inputs (except -0), the result is NaN per IEEE 754.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Sqrt(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Ceiling: `dest = ceil(operand)` — smallest integer >= operand.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Ceil(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Floor: `dest = floor(operand)` — largest integer <= operand.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Floor(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Round to nearest integer: `dest = round(operand)`.
 *
 * Ties round to the nearest even integer (banker's rounding).
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class Round(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Floating-point truncation toward zero: `dest = trunc(operand)`.
 *
 * Rounds toward zero (drops the fractional part). Not to be confused with
 * IntTrunc which narrows integer bit width.
 *
 * @param dest the SSA result reference
 * @param operand operand value
 */
data class FTrunc(
    val dest: InstructionRef,
    val operand: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(operand)
}

/**
 * Copy sign: `dest` has the magnitude of `magnitude` and the sign of `sign`.
 *
 * Equivalent to `copysign(magnitude, sign)` in C. Works correctly with NaN and zero.
 *
 * @param dest the SSA result reference
 * @param magnitude operand value
 * @param sign operand value
 */
data class CopySign(
    val dest: InstructionRef,
    val magnitude: Value,
    val sign: Value,
) : ArithmeticInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(magnitude, sign)
}


// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.FastMathFlags

/**
 * Emission interface for arithmetic instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ArithmeticInstructionSet : InstructionSet {

    /**
     * Integer addition: `dest = lhs + rhs`.

Result type matches the operand type. If `nuw` (no unsigned wrap) is set,
unsigned overflow produces poison. If `nsw` (no signed wrap) is set,
signed overflow produces poison. Both flags default to false (wrapping two's complement).
     *
     * Emits a [Add] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param nuw Boolean
     * @param nsw Boolean
     * @return the SSA value produced by this instruction
     */
    fun add(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value

    /**
     * Integer subtraction: `dest = lhs - rhs`.

Overflow semantics match Add: `nuw` and `nsw` flags control poison on wrap.
     *
     * Emits a [Sub] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param nuw Boolean
     * @param nsw Boolean
     * @return the SSA value produced by this instruction
     */
    fun sub(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value

    /**
     * Integer multiplication: `dest = lhs * rhs`.

Overflow semantics match Add: `nuw` and `nsw` flags control poison on wrap.
     *
     * Emits a [Mul] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param nuw Boolean
     * @param nsw Boolean
     * @return the SSA value produced by this instruction
     */
    fun mul(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value

    /**
     * Unsigned integer division: `dest = lhs / rhs` (unsigned).

Division by zero is undefined behavior. If `exact` is true and the result
is not exact (i.e., `lhs` is not evenly divisible by `rhs`), the result is poison.
     *
     * Emits a [UDiv] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param exact Boolean
     * @return the SSA value produced by this instruction
     */
    fun udiv(lhs: Value, rhs: Value, exact: Boolean = false): Value

    /**
     * Signed integer division: `dest = lhs / rhs` (signed).

Division by zero and `INT_MIN / -1` are undefined behavior. If `exact` is true
and the result is not exact, the result is poison.
     *
     * Emits a [SDiv] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param exact Boolean
     * @return the SSA value produced by this instruction
     */
    fun sdiv(lhs: Value, rhs: Value, exact: Boolean = false): Value

    /**
     * Unsigned integer remainder: `dest = lhs % rhs` (unsigned).

Division by zero is undefined behavior.
     *
     * Emits a [URem] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun urem(lhs: Value, rhs: Value): Value

    /**
     * Signed integer remainder: `dest = lhs % rhs` (signed).

The result has the same sign as the dividend. Division by zero is undefined behavior.
     *
     * Emits a [SRem] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun srem(lhs: Value, rhs: Value): Value

    /**
     * Integer negation: `dest = -operand` (equivalent to `0 - operand`).
     *
     * Emits a [Neg] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun neg(operand: Value): Value

    /**
     * Signed addition with overflow detection: `dest = {lhs + rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
     *
     * Emits a [SAddOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun saddOverflow(lhs: Value, rhs: Value): Value

    /**
     * Unsigned addition with overflow detection: `dest = {lhs + rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if unsigned overflow (carry) occurred.
     *
     * Emits a [UAddOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun uaddOverflow(lhs: Value, rhs: Value): Value

    /**
     * Signed subtraction with overflow detection: `dest = {lhs - rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
     *
     * Emits a [SSubOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun ssubOverflow(lhs: Value, rhs: Value): Value

    /**
     * Unsigned subtraction with overflow detection: `dest = {lhs - rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if unsigned underflow (borrow) occurred.
     *
     * Emits a [USubOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun usubOverflow(lhs: Value, rhs: Value): Value

    /**
     * Signed multiplication with overflow detection: `dest = {lhs * rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if signed overflow occurred.
     *
     * Emits a [SMulOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun smulOverflow(lhs: Value, rhs: Value): Value

    /**
     * Unsigned multiplication with overflow detection: `dest = {lhs * rhs, overflow_flag}`.

Returns a struct `{T, i1}` where the second element is true if unsigned overflow occurred.
     *
     * Emits a [UMulOverflow] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun umulOverflow(lhs: Value, rhs: Value): Value

    /**
     * Signed saturating addition: `dest = clamp(lhs + rhs, INT_MIN, INT_MAX)`.

On overflow, the result clamps to the minimum or maximum representable value.
     *
     * Emits a [SAddSat] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun saddSat(lhs: Value, rhs: Value): Value

    /**
     * Unsigned saturating addition: `dest = min(lhs + rhs, UINT_MAX)`.

On overflow, the result clamps to the maximum representable value.
     *
     * Emits a [UAddSat] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun uaddSat(lhs: Value, rhs: Value): Value

    /**
     * Signed saturating subtraction: `dest = clamp(lhs - rhs, INT_MIN, INT_MAX)`.
     *
     * Emits a [SSubSat] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun ssubSat(lhs: Value, rhs: Value): Value

    /**
     * Unsigned saturating subtraction: `dest = max(lhs - rhs, 0)`.
     *
     * Emits a [USubSat] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun usubSat(lhs: Value, rhs: Value): Value

    /**
     * Signed minimum: `dest = min(lhs, rhs)` using signed comparison.
     *
     * Emits a [SMin] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun smin(lhs: Value, rhs: Value): Value

    /**
     * Signed maximum: `dest = max(lhs, rhs)` using signed comparison.
     *
     * Emits a [SMax] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun smax(lhs: Value, rhs: Value): Value

    /**
     * Unsigned minimum: `dest = min(lhs, rhs)` using unsigned comparison.
     *
     * Emits a [UMin] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun umin(lhs: Value, rhs: Value): Value

    /**
     * Unsigned maximum: `dest = max(lhs, rhs)` using unsigned comparison.
     *
     * Emits a [UMax] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun umax(lhs: Value, rhs: Value): Value

    /**
     * Integer absolute value: `dest = |operand|`.

If `isIntMin` is true and the operand is `INT_MIN` (which has no positive representation),
the result is poison. If false, `INT_MIN` returns `INT_MIN` (wrapping behavior).
     *
     * Emits a [Abs] instruction into the current block.
     *
     * @param operand source operand
     * @param isIntMin Boolean
     * @return the SSA value produced by this instruction
     */
    fun abs(operand: Value, isIntMin: Boolean = false): Value

    /**
     * Floating-point addition: `dest = lhs + rhs`.

Follows IEEE 754 semantics unless fast-math flags permit reassociation or other transformations.
     *
     * Emits a [FAdd] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fadd(lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point subtraction: `dest = lhs - rhs`.
     *
     * Emits a [FSub] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fsub(lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point multiplication: `dest = lhs * rhs`.
     *
     * Emits a [FMul] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fmul(lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point division: `dest = lhs / rhs`.

Division by zero follows IEEE 754 rules (produces infinity or NaN) unless
fast-math flags alter behavior.
     *
     * Emits a [FDiv] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fdiv(lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point remainder: `dest = lhs % rhs` (IEEE 754 remainder).
     *
     * Emits a [FRem] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun frem(lhs: Value, rhs: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point negation: `dest = -operand`.

Flips the sign bit. NaN propagation follows IEEE 754.
     *
     * Emits a [FNeg] instruction into the current block.
     *
     * @param operand source operand
     * @param fastMath FastMathFlags
     * @return the SSA value produced by this instruction
     */
    fun fneg(operand: Value, fastMath: FastMathFlags = FastMathFlags.NONE): Value

    /**
     * Floating-point absolute value: `dest = |operand|`.

Clears the sign bit. Works correctly with NaN and infinity.
     *
     * Emits a [FAbs] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun fabs(operand: Value): Value

    /**
     * Fused multiply-add: `dest = a * b + c` with a single rounding step.

More precise than separate multiply and add because the intermediate product
is not rounded. Maps to hardware FMA instructions where available.
     *
     * Emits a [FMA] instruction into the current block.
     *
     * @param a source operand
     * @param b source operand
     * @param c source operand
     * @return the SSA value produced by this instruction
     */
    fun fma(a: Value, b: Value, c: Value): Value

    /**
     * Floating-point minimum: `dest = min(lhs, rhs)`.

If either operand is NaN, the result is NaN. Follows IEEE 754-2008 `minNum` semantics.
     *
     * Emits a [FMin] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun fmin(lhs: Value, rhs: Value): Value

    /**
     * Floating-point maximum: `dest = max(lhs, rhs)`.

If either operand is NaN, the result is NaN. Follows IEEE 754-2008 `maxNum` semantics.
     *
     * Emits a [FMax] instruction into the current block.
     *
     * @param lhs source operand
     * @param rhs source operand
     * @return the SSA value produced by this instruction
     */
    fun fmax(lhs: Value, rhs: Value): Value

    /**
     * Square root: `dest = sqrt(operand)`.

For negative inputs (except -0), the result is NaN per IEEE 754.
     *
     * Emits a [Sqrt] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun sqrt(operand: Value): Value

    /**
     * Ceiling: `dest = ceil(operand)` — smallest integer >= operand.
     *
     * Emits a [Ceil] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun ceil(operand: Value): Value

    /**
     * Floor: `dest = floor(operand)` — largest integer <= operand.
     *
     * Emits a [Floor] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun floor(operand: Value): Value

    /**
     * Round to nearest integer: `dest = round(operand)`.

Ties round to the nearest even integer (banker's rounding).
     *
     * Emits a [Round] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun round(operand: Value): Value

    /**
     * Floating-point truncation toward zero: `dest = trunc(operand)`.

Rounds toward zero (drops the fractional part). Not to be confused with
IntTrunc which narrows integer bit width.
     *
     * Emits a [FTrunc] instruction into the current block.
     *
     * @param operand source operand
     * @return the SSA value produced by this instruction
     */
    fun ftrunc(operand: Value): Value

    /**
     * Copy sign: `dest` has the magnitude of `magnitude` and the sign of `sign`.

Equivalent to `copysign(magnitude, sign)` in C. Works correctly with NaN and zero.
     *
     * Emits a [CopySign] instruction into the current block.
     *
     * @param magnitude source operand
     * @param sign source operand
     * @return the SSA value produced by this instruction
     */
    fun copySign(magnitude: Value, sign: Value): Value
}

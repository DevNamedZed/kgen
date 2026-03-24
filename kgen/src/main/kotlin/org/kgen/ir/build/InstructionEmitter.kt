package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Abstract base class for IR instruction emission, shared by [BlockBuilder] (DSL-style) and
 * [ModuleBuilder] (imperative-style).
 *
 * Each method in this class corresponds to one IR instruction. Calling a method allocates a
 * fresh SSA [InstructionRef] via [nextRef], constructs the matching [Instruction] node, passes
 * it to [emit], and returns the ref as a [Value] that subsequent instructions can use as an
 * operand.
 *
 * Void-producing instructions (stores, branches, monitors, etc.) return [Unit] and do not
 * allocate a ref.
 *
 * Subclasses must implement:
 * - [emit] — appends the instruction to the current basic block.
 * - [nextRef] — allocates the next SSA name for a result of the given [Type].
 *
 * All instruction families are defined here exactly once so that both builder styles stay in
 * sync automatically.
 */
@Suppress("TooManyFunctions")
abstract class InstructionEmitter : InstructionSink {
    abstract override fun emit(instruction: Instruction)
    abstract override fun nextRef(type: Type): InstructionRef

    // Integer arithmetic

    /**
     * Emits an integer addition instruction (`add`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @param nuw if `true`, the result is poison on unsigned overflow (No Unsigned Wrap).
     * @param nsw if `true`, the result is poison on signed overflow (No Signed Wrap).
     * @return the SSA value representing the sum, with the same type as [lhs].
     */
    @JvmOverloads fun add(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value =
        nextRef(lhs.type).also { emit(Add(it, lhs, rhs, nuw, nsw)) }

    /**
     * Emits an integer subtraction instruction (`sub`).
     *
     * @param lhs the minuend.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @param nuw if `true`, the result is poison on unsigned overflow (No Unsigned Wrap).
     * @param nsw if `true`, the result is poison on signed overflow (No Signed Wrap).
     * @return the SSA value representing the difference, with the same type as [lhs].
     */
    @JvmOverloads fun sub(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value =
        nextRef(lhs.type).also { emit(Sub(it, lhs, rhs, nuw, nsw)) }

    /**
     * Emits an integer multiplication instruction (`mul`).
     *
     * @param lhs the left factor.
     * @param rhs the right factor; must have the same type as [lhs].
     * @param nuw if `true`, the result is poison on unsigned overflow (No Unsigned Wrap).
     * @param nsw if `true`, the result is poison on signed overflow (No Signed Wrap).
     * @return the SSA value representing the product, with the same type as [lhs].
     */
    @JvmOverloads fun mul(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value =
        nextRef(lhs.type).also { emit(Mul(it, lhs, rhs, nuw, nsw)) }

    /**
     * Emits an unsigned integer division instruction (`udiv`).
     *
     * Division by zero produces a poison value. Both operands are treated as unsigned.
     *
     * @param lhs the dividend.
     * @param rhs the divisor; must have the same type as [lhs].
     * @param exact if `true`, the result is poison when [lhs] is not an exact multiple of [rhs].
     * @return the SSA value representing the unsigned quotient.
     */
    @JvmOverloads fun udiv(lhs: Value, rhs: Value, exact: Boolean = false): Value =
        nextRef(lhs.type).also { emit(UDiv(it, lhs, rhs, exact)) }

    /**
     * Emits a signed integer division instruction (`sdiv`).
     *
     * Division by zero and `INT_MIN / -1` produce poison values. Both operands are treated
     * as two's-complement signed integers.
     *
     * @param lhs the dividend.
     * @param rhs the divisor; must have the same type as [lhs].
     * @param exact if `true`, the result is poison when the division is not exact.
     * @return the SSA value representing the signed quotient.
     */
    @JvmOverloads fun sdiv(lhs: Value, rhs: Value, exact: Boolean = false): Value =
        nextRef(lhs.type).also { emit(SDiv(it, lhs, rhs, exact)) }

    /**
     * Emits an unsigned integer remainder instruction (`urem`).
     *
     * The result satisfies `lhs == (lhs udiv rhs) * rhs + result`. Division by zero produces
     * a poison value.
     *
     * @param lhs the dividend.
     * @param rhs the divisor; must have the same type as [lhs].
     * @return the SSA value representing the unsigned remainder.
     */
    fun urem(lhs: Value, rhs: Value): Value =
        nextRef(lhs.type).also { emit(URem(it, lhs, rhs)) }

    /**
     * Emits a signed integer remainder instruction (`srem`).
     *
     * The result has the same sign as [lhs]. Division by zero and `INT_MIN % -1` produce
     * poison values.
     *
     * @param lhs the dividend.
     * @param rhs the divisor; must have the same type as [lhs].
     * @return the SSA value representing the signed remainder.
     */
    fun srem(lhs: Value, rhs: Value): Value =
        nextRef(lhs.type).also { emit(SRem(it, lhs, rhs)) }

    /**
     * Emits an integer negation instruction (`neg`), equivalent to `0 - operand`.
     *
     * @param operand the value to negate.
     * @return the SSA value representing the two's-complement negation of [operand].
     */
    fun neg(operand: Value): Value =
        nextRef(operand.type).also { emit(Neg(it, operand)) }

    // Overflow-checked arithmetic

    /**
     * Emits a signed addition with overflow detection (`sadd.with.overflow`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the sum with [extractValue] at
     *   index 0 and the overflow flag (i1) at index 1.
     */
    fun saddOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(SAddOverflow(it, lhs, rhs)) }

    /**
     * Emits an unsigned addition with overflow detection (`uadd.with.overflow`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the sum with [extractValue] at
     *   index 0 and the overflow flag (i1) at index 1.
     */
    fun uaddOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(UAddOverflow(it, lhs, rhs)) }

    /**
     * Emits a signed subtraction with overflow detection (`ssub.with.overflow`).
     *
     * @param lhs the minuend.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the difference with [extractValue]
     *   at index 0 and the overflow flag (i1) at index 1.
     */
    fun ssubOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(SSubOverflow(it, lhs, rhs)) }

    /**
     * Emits an unsigned subtraction with overflow detection (`usub.with.overflow`).
     *
     * @param lhs the minuend.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the difference with [extractValue]
     *   at index 0 and the overflow flag (i1) at index 1.
     */
    fun usubOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(USubOverflow(it, lhs, rhs)) }

    /**
     * Emits a signed multiplication with overflow detection (`smul.with.overflow`).
     *
     * @param lhs the left factor.
     * @param rhs the right factor; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the product with [extractValue]
     *   at index 0 and the overflow flag (i1) at index 1.
     */
    fun smulOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(SMulOverflow(it, lhs, rhs)) }

    /**
     * Emits an unsigned multiplication with overflow detection (`umul.with.overflow`).
     *
     * @param lhs the left factor.
     * @param rhs the right factor; must have the same type as [lhs].
     * @return a `{ result, i1 overflow }` struct value. Extract the product with [extractValue]
     *   at index 0 and the overflow flag (i1) at index 1.
     */
    fun umulOverflow(lhs: Value, rhs: Value): Value =
        nextRef(Type.Struct(null, listOf(lhs.type, Type.I1))).also { emit(UMulOverflow(it, lhs, rhs)) }

    // Saturating arithmetic

    /**
     * Emits a signed saturating addition (`sadd.sat`).
     *
     * Instead of wrapping or producing poison on overflow, the result is clamped to the
     * signed minimum or maximum representable value for the type.
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value representing the saturated signed sum.
     */
    fun saddSat(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(SAddSat(it, lhs, rhs)) }

    /**
     * Emits an unsigned saturating addition (`uadd.sat`).
     *
     * Instead of wrapping on overflow, the result is clamped to the unsigned maximum
     * representable value for the type.
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value representing the saturated unsigned sum.
     */
    fun uaddSat(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(UAddSat(it, lhs, rhs)) }

    /**
     * Emits a signed saturating subtraction (`ssub.sat`).
     *
     * On underflow the result is clamped to the signed minimum for the type; on overflow it
     * is clamped to the signed maximum.
     *
     * @param lhs the minuend.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @return the SSA value representing the saturated signed difference.
     */
    fun ssubSat(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(SSubSat(it, lhs, rhs)) }

    /**
     * Emits an unsigned saturating subtraction (`usub.sat`).
     *
     * On underflow (result would go below zero) the result is clamped to zero.
     *
     * @param lhs the minuend.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @return the SSA value representing the saturated unsigned difference.
     */
    fun usubSat(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(USubSat(it, lhs, rhs)) }

    // Min/max/abs

    /**
     * Emits a signed integer minimum instruction (`smin`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `min(lhs, rhs)` under signed comparison.
     */
    fun smin(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(SMin(it, lhs, rhs)) }

    /**
     * Emits a signed integer maximum instruction (`smax`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `max(lhs, rhs)` under signed comparison.
     */
    fun smax(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(SMax(it, lhs, rhs)) }

    /**
     * Emits an unsigned integer minimum instruction (`umin`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `min(lhs, rhs)` under unsigned comparison.
     */
    fun umin(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(UMin(it, lhs, rhs)) }

    /**
     * Emits an unsigned integer maximum instruction (`umax`).
     *
     * @param lhs the left operand.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `max(lhs, rhs)` under unsigned comparison.
     */
    fun umax(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(UMax(it, lhs, rhs)) }

    /**
     * Emits an integer absolute value instruction (`abs`).
     *
     * @param operand the value whose absolute value is computed.
     * @param isIntMin if `true`, the result is poison when [operand] equals `INT_MIN` (the
     *   most-negative two's-complement value), allowing the optimizer to assume it never occurs.
     * @return the SSA value representing `|operand|`.
     */
    @JvmOverloads fun abs(operand: Value, isIntMin: Boolean = false): Value =
        nextRef(operand.type).also { emit(Abs(it, operand, isIntMin)) }

    // Float arithmetic

    /**
     * Emits a floating-point addition instruction (`fadd`).
     *
     * @param lhs the left operand; must be a floating-point scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @param fm fast-math flags that permit IEEE-754 relaxations (e.g. reassociation, NaN
     *   assumptions). Defaults to [FastMathFlags.NONE] (strict IEEE semantics).
     * @return the SSA value representing the floating-point sum.
     */
    @JvmOverloads fun fadd(lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(lhs.type).also { emit(FAdd(it, lhs, rhs, fm)) }

    /**
     * Emits a floating-point subtraction instruction (`fsub`).
     *
     * @param lhs the minuend; must be a floating-point scalar or vector type.
     * @param rhs the subtrahend; must have the same type as [lhs].
     * @param fm fast-math flags permitting IEEE-754 relaxations.
     * @return the SSA value representing the floating-point difference.
     */
    @JvmOverloads fun fsub(lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(lhs.type).also { emit(FSub(it, lhs, rhs, fm)) }

    /**
     * Emits a floating-point multiplication instruction (`fmul`).
     *
     * @param lhs the left factor; must be a floating-point scalar or vector type.
     * @param rhs the right factor; must have the same type as [lhs].
     * @param fm fast-math flags permitting IEEE-754 relaxations.
     * @return the SSA value representing the floating-point product.
     */
    @JvmOverloads fun fmul(lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(lhs.type).also { emit(FMul(it, lhs, rhs, fm)) }

    /**
     * Emits a floating-point division instruction (`fdiv`).
     *
     * Division by zero yields ±infinity or NaN according to IEEE-754. Fast-math flags may
     * allow the optimizer to assume a finite non-zero divisor.
     *
     * @param lhs the dividend; must be a floating-point scalar or vector type.
     * @param rhs the divisor; must have the same type as [lhs].
     * @param fm fast-math flags permitting IEEE-754 relaxations.
     * @return the SSA value representing the floating-point quotient.
     */
    @JvmOverloads fun fdiv(lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(lhs.type).also { emit(FDiv(it, lhs, rhs, fm)) }

    /**
     * Emits a floating-point remainder instruction (`frem`).
     *
     * The result is equivalent to the C `fmod` function. The result has the same sign as
     * [lhs] and a magnitude smaller than that of [rhs].
     *
     * @param lhs the dividend; must be a floating-point scalar or vector type.
     * @param rhs the divisor; must have the same type as [lhs].
     * @param fm fast-math flags permitting IEEE-754 relaxations.
     * @return the SSA value representing the floating-point remainder.
     */
    @JvmOverloads fun frem(lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(lhs.type).also { emit(FRem(it, lhs, rhs, fm)) }

    /**
     * Emits a floating-point negation instruction (`fneg`).
     *
     * Flips the sign bit; equivalent to `-0.0 - operand` but more explicit for optimizers.
     *
     * @param operand the value to negate; must be a floating-point scalar or vector type.
     * @param fm fast-math flags permitting IEEE-754 relaxations.
     * @return the SSA value representing the negated floating-point value.
     */
    @JvmOverloads fun fneg(operand: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(operand.type).also { emit(FNeg(it, operand, fm)) }

    /**
     * Emits a floating-point absolute value instruction (`fabs`).
     *
     * Clears the sign bit, returning a non-negative value with the same magnitude.
     *
     * @param operand the value; must be a floating-point scalar or vector type.
     * @return the SSA value representing `|operand|`.
     */
    fun fabs(operand: Value): Value = nextRef(operand.type).also { emit(FAbs(it, operand)) }

    /**
     * Emits a fused multiply-add instruction (`fma`).
     *
     * Computes `(a * b) + c` as a single operation with only one rounding step, giving higher
     * precision than separate [fmul] + [fadd].
     *
     * @param a the first factor; must be a floating-point scalar or vector type.
     * @param b the second factor; must have the same type as [a].
     * @param c the addend; must have the same type as [a].
     * @return the SSA value representing the fused multiply-add result.
     */
    fun fma(a: Value, b: Value, c: Value): Value = nextRef(a.type).also { emit(FMA(it, a, b, c)) }

    /**
     * Emits a floating-point minimum instruction (`fmin`).
     *
     * Returns the smaller of the two operands. NaN propagation follows platform conventions
     * (typically returns NaN if either operand is NaN).
     *
     * @param lhs the left operand; must be a floating-point scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `min(lhs, rhs)`.
     */
    fun fmin(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(FMin(it, lhs, rhs)) }

    /**
     * Emits a floating-point maximum instruction (`fmax`).
     *
     * Returns the larger of the two operands. NaN propagation follows platform conventions.
     *
     * @param lhs the left operand; must be a floating-point scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value equal to `max(lhs, rhs)`.
     */
    fun fmax(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(FMax(it, lhs, rhs)) }

    /**
     * Emits a floating-point square root instruction (`sqrt`).
     *
     * The result is the positive square root. Passing a negative value produces a NaN.
     *
     * @param operand the value; must be a floating-point scalar or vector type.
     * @return the SSA value representing `sqrt(operand)`.
     */
    fun sqrt(operand: Value): Value = nextRef(operand.type).also { emit(Sqrt(it, operand)) }

    /**
     * Emits a floating-point ceiling instruction (`ceil`).
     *
     * Rounds toward positive infinity to the nearest integer value representable in the
     * same floating-point type.
     *
     * @param operand the value to round; must be a floating-point scalar or vector type.
     * @return the SSA value representing `⌈operand⌉`.
     */
    fun ceil(operand: Value): Value = nextRef(operand.type).also { emit(Ceil(it, operand)) }

    /**
     * Emits a floating-point floor instruction (`floor`).
     *
     * Rounds toward negative infinity to the nearest integer value representable in the
     * same floating-point type.
     *
     * @param operand the value to round; must be a floating-point scalar or vector type.
     * @return the SSA value representing `⌊operand⌋`.
     */
    fun floor(operand: Value): Value = nextRef(operand.type).also { emit(Floor(it, operand)) }

    /**
     * Emits a floating-point round-to-nearest instruction (`round`).
     *
     * Rounds to the nearest integer value, with ties going away from zero (i.e. `0.5` rounds
     * to `1.0`, `-0.5` rounds to `-1.0`).
     *
     * @param operand the value to round; must be a floating-point scalar or vector type.
     * @return the SSA value representing `round(operand)`.
     */
    fun round(operand: Value): Value = nextRef(operand.type).also { emit(Round(it, operand)) }

    /**
     * Emits a floating-point truncation-to-integer instruction (`ftrunc`).
     *
     * Rounds toward zero, discarding the fractional part. The result is still a
     * floating-point value (not an integer type) — use [fptosi] / [fptoui] to convert to
     * an integer type.
     *
     * @param operand the value to truncate; must be a floating-point scalar or vector type.
     * @return the SSA value representing `trunc(operand)`.
     */
    fun ftrunc(operand: Value): Value = nextRef(operand.type).also { emit(FTrunc(it, operand)) }

    /**
     * Emits a copy-sign instruction (`copysign`).
     *
     * Returns a value with the magnitude of [magnitude] and the sign bit of [sign]. Both
     * operands must share the same floating-point type.
     *
     * @param magnitude the value whose magnitude is used.
     * @param sign the value whose sign bit is copied.
     * @return the SSA value with the magnitude of [magnitude] and the sign of [sign].
     */
    fun copySign(magnitude: Value, sign: Value): Value =
        nextRef(magnitude.type).also { emit(CopySign(it, magnitude, sign)) }

    // Bitwise

    /**
     * Emits a bitwise AND instruction (`and`).
     *
     * @param lhs the left operand; must be an integer scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value representing `lhs & rhs`.
     */
    fun and(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(And(it, lhs, rhs)) }

    /**
     * Emits a bitwise OR instruction (`or`).
     *
     * @param lhs the left operand; must be an integer scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value representing `lhs | rhs`.
     */
    fun or(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(Or(it, lhs, rhs)) }

    /**
     * Emits a bitwise XOR instruction (`xor`).
     *
     * @param lhs the left operand; must be an integer scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA value representing `lhs ^ rhs`.
     */
    fun xor(lhs: Value, rhs: Value): Value = nextRef(lhs.type).also { emit(Xor(it, lhs, rhs)) }

    /**
     * Emits a bitwise NOT instruction (equivalent to `xor operand, -1`).
     *
     * @param operand the value to complement; must be an integer scalar or vector type.
     * @return the SSA value representing `~operand`.
     */
    fun not(operand: Value): Value = nextRef(operand.type).also { emit(Not(it, operand)) }

    /**
     * Emits a left-shift instruction (`shl`).
     *
     * Shifts [lhs] left by [rhs] bit positions. If [rhs] is greater than or equal to the
     * bit width of the type, the result is poison.
     *
     * @param lhs the value to shift.
     * @param rhs the shift amount; must have the same type as [lhs].
     * @param nuw if `true`, the result is poison on unsigned overflow (No Unsigned Wrap).
     * @param nsw if `true`, the result is poison on signed overflow (No Signed Wrap).
     * @return the SSA value representing `lhs << rhs`.
     */
    @JvmOverloads fun shl(lhs: Value, rhs: Value, nuw: Boolean = false, nsw: Boolean = false): Value =
        nextRef(lhs.type).also { emit(Shl(it, lhs, rhs, nuw, nsw)) }

    /**
     * Emits a logical right-shift instruction (`lshr`).
     *
     * Shifts [lhs] right by [rhs] bit positions, filling the vacated high bits with zeros.
     * If [rhs] is greater than or equal to the bit width, the result is poison.
     *
     * @param lhs the value to shift.
     * @param rhs the shift amount; must have the same type as [lhs].
     * @param exact if `true`, the result is poison when any of the bits shifted out are
     *   non-zero (i.e. [lhs] must be exactly divisible by `2^rhs`).
     * @return the SSA value representing `lhs >>> rhs` (unsigned shift right).
     */
    @JvmOverloads fun lshr(lhs: Value, rhs: Value, exact: Boolean = false): Value =
        nextRef(lhs.type).also { emit(LShr(it, lhs, rhs, exact)) }

    /**
     * Emits an arithmetic right-shift instruction (`ashr`).
     *
     * Shifts [lhs] right by [rhs] bit positions, sign-extending the vacated high bits.
     * If [rhs] is greater than or equal to the bit width, the result is poison.
     *
     * @param lhs the value to shift (treated as signed).
     * @param rhs the shift amount; must have the same type as [lhs].
     * @param exact if `true`, the result is poison when any of the bits shifted out are
     *   non-zero.
     * @return the SSA value representing `lhs >> rhs` (signed shift right).
     */
    @JvmOverloads fun ashr(lhs: Value, rhs: Value, exact: Boolean = false): Value =
        nextRef(lhs.type).also { emit(AShr(it, lhs, rhs, exact)) }

    /**
     * Emits a left-rotation instruction (`rotl`).
     *
     * Rotates the bits of [value] left by [amount] positions. Bits shifted out of the
     * most-significant end are reintroduced at the least-significant end.
     *
     * @param value the value to rotate.
     * @param amount the number of bit positions to rotate; must have the same type as [value].
     * @return the SSA value representing `rotl(value, amount)`.
     */
    fun rotateLeft(value: Value, amount: Value): Value =
        nextRef(value.type).also { emit(Rotl(it, value, amount)) }

    /**
     * Emits a right-rotation instruction (`rotr`).
     *
     * Rotates the bits of [value] right by [amount] positions. Bits shifted out of the
     * least-significant end are reintroduced at the most-significant end.
     *
     * @param value the value to rotate.
     * @param amount the number of bit positions to rotate; must have the same type as [value].
     * @return the SSA value representing `rotr(value, amount)`.
     */
    fun rotateRight(value: Value, amount: Value): Value =
        nextRef(value.type).also { emit(Rotr(it, value, amount)) }

    // Bit manipulation

    /**
     * Emits a count-leading-zeros instruction (`ctlz`).
     *
     * Counts the number of zero bits preceding the most-significant set bit. For a zero
     * input the result equals the bit width of the type unless [isZeroPoison] is set.
     *
     * @param operand the value to inspect; must be an integer scalar or vector type.
     * @param isZeroPoison if `true`, passing a zero [operand] produces a poison value,
     *   enabling the optimizer to assume the input is non-zero.
     * @return the SSA value representing the leading zero count.
     */
    @JvmOverloads fun ctlz(operand: Value, isZeroPoison: Boolean = false): Value =
        nextRef(operand.type).also { emit(Ctlz(it, operand, isZeroPoison)) }

    /**
     * Emits a count-trailing-zeros instruction (`cttz`).
     *
     * Counts the number of zero bits after the least-significant set bit. For a zero input
     * the result equals the bit width of the type unless [isZeroPoison] is set.
     *
     * @param operand the value to inspect; must be an integer scalar or vector type.
     * @param isZeroPoison if `true`, passing a zero [operand] produces a poison value,
     *   enabling the optimizer to assume the input is non-zero.
     * @return the SSA value representing the trailing zero count.
     */
    @JvmOverloads fun cttz(operand: Value, isZeroPoison: Boolean = false): Value =
        nextRef(operand.type).also { emit(Cttz(it, operand, isZeroPoison)) }

    /**
     * Emits a population-count instruction (`ctpop`).
     *
     * Counts the number of bits that are set to 1 (the Hamming weight).
     *
     * @param operand the value to inspect; must be an integer scalar or vector type.
     * @return the SSA value representing the number of set bits in [operand].
     */
    fun ctpop(operand: Value): Value = nextRef(operand.type).also { emit(Ctpop(it, operand)) }

    /**
     * Emits a byte-swap instruction (`bswap`).
     *
     * Reverses the byte order of the operand. The operand must be an integer type with a
     * bit width that is a multiple of 16.
     *
     * @param operand the value to byte-swap; must be an integer scalar type (e.g. i16, i32, i64).
     * @return the SSA value with the bytes of [operand] in reversed order.
     */
    fun bswap(operand: Value): Value = nextRef(operand.type).also { emit(BSwap(it, operand)) }

    /**
     * Emits a bit-reverse instruction (`bitreverse`).
     *
     * Reverses the order of all bits in the operand (most-significant bit becomes
     * least-significant, and vice versa).
     *
     * @param operand the value to reverse; must be an integer scalar or vector type.
     * @return the SSA value with all bits of [operand] in reversed order.
     */
    fun bitReverse(operand: Value): Value = nextRef(operand.type).also { emit(BitReverse(it, operand)) }

    // Comparison

    /**
     * Emits an integer comparison instruction (`icmp`).
     *
     * Compares [lhs] and [rhs] using the given predicate and produces an `i1` result (or a
     * vector of `i1` when the operands are vectors).
     *
     * @param pred the comparison predicate (e.g. [ICmpPredicate.EQ], [ICmpPredicate.SLT]).
     * @param lhs the left operand; must be an integer scalar, vector, or pointer type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @return the SSA `i1` value that is `1` when the predicate holds, `0` otherwise.
     */
    fun icmp(pred: ICmpPredicate, lhs: Value, rhs: Value): Value =
        nextRef(Type.I1).also { emit(ICmp(it, pred, lhs, rhs)) }

    /**
     * Emits a floating-point comparison instruction (`fcmp`).
     *
     * Compares [lhs] and [rhs] using the given predicate. Ordered predicates return `false`
     * when either operand is NaN; unordered predicates return `true`.
     *
     * @param pred the comparison predicate (e.g. [FCmpPredicate.OEQ], [FCmpPredicate.ULT]).
     * @param lhs the left operand; must be a floating-point scalar or vector type.
     * @param rhs the right operand; must have the same type as [lhs].
     * @param fm fast-math flags permitting IEEE-754 relaxations (e.g. assuming no NaNs).
     * @return the SSA `i1` value that is `1` when the predicate holds, `0` otherwise.
     */
    @JvmOverloads fun fcmp(pred: FCmpPredicate, lhs: Value, rhs: Value, fm: FastMathFlags = FastMathFlags.NONE): Value =
        nextRef(Type.I1).also { emit(FCmp(it, pred, lhs, rhs, fm)) }

    // Memory

    /**
     * Emits a stack allocation instruction (`alloca`).
     *
     * Reserves space on the current function's stack frame for one or more values of [type].
     * The allocation is released automatically when the function returns.
     *
     * @param type the type of each element to allocate.
     * @param numElements the number of elements to allocate; `null` means one element.
     * @param align the required byte alignment of the allocation; `null` uses the default
     *   alignment of [type].
     * @return a pointer value (of type `Pointer(type)`) pointing to the allocated memory.
     */
    @JvmOverloads fun alloca(type: Type, numElements: Value? = null, align: Int? = null): Value =
        nextRef(Type.Pointer(type)).also { emit(Alloca(it, type, numElements, align)) }

    /**
     * Emits a memory load instruction (`load`).
     *
     * Reads a value of [type] from the address held in [ptr].
     *
     * @param type the type of value to read; must match the pointee type of [ptr].
     * @param ptr the pointer to read from.
     * @param align the expected alignment of the access in bytes; `null` uses the default
     *   alignment of [type].
     * @param volatile if `true`, the load is treated as a volatile access (not reordered or
     *   eliminated by the optimizer).
     * @param ordering an optional atomic memory ordering for this load; `null` means
     *   non-atomic.
     * @return the SSA value that was loaded from memory.
     */
    @JvmOverloads fun load(type: Type, ptr: Value, align: Int? = null, volatile: Boolean = false, ordering: AtomicOrdering? = null): Value =
        nextRef(type).also { emit(Load(it, ptr, type, align, volatile, ordering)) }

    /**
     * Emits a memory store instruction (`store`).
     *
     * Writes [value] to the address held in [ptr]. This instruction has no SSA result.
     *
     * @param value the value to write.
     * @param ptr the pointer to write to; its pointee type must match the type of [value].
     * @param align the expected alignment of the access in bytes; `null` uses the default.
     * @param volatile if `true`, the store is treated as a volatile access.
     * @param ordering an optional atomic memory ordering; `null` means non-atomic.
     */
    @JvmOverloads fun store(value: Value, ptr: Value, align: Int? = null, volatile: Boolean = false, ordering: AtomicOrdering? = null) {
        emit(Store(value, ptr, align, volatile, ordering))
    }

    /**
     * Emits a GetElementPtr instruction (`gep`).
     *
     * Computes the address of a sub-element within an aggregate or array without reading
     * from memory. This is the primary way to index into structs and arrays.
     *
     * @param baseType the base element type that the initial pointer addresses.
     * @param ptr the base pointer.
     * @param indices one or more index values to traverse into the aggregate. The first index
     *   steps through an array of [baseType]; subsequent indices step into struct fields or
     *   nested array elements.
     * @param inBounds if `true`, the result is poison when the access would fall outside the
     *   bounds of the allocated object, allowing the optimizer to assume in-bounds accesses.
     * @return a pointer value pointing to the addressed sub-element.
     */
    @JvmOverloads fun gep(baseType: Type, ptr: Value, vararg indices: Value, inBounds: Boolean = true): Value =
        nextRef(Type.Pointer(baseType)).also { emit(GetElementPtr(it, baseType, ptr, indices.toList(), inBounds)) }

    /**
     * Emits a memory fence instruction (`fence`).
     *
     * Establishes a happens-before ordering between memory operations in the same or other
     * threads without performing a read-modify-write. This instruction has no SSA result.
     *
     * @param ordering the memory ordering semantics (e.g. [AtomicOrdering.SEQ_CST]).
     * @param syncScope an optional synchronization scope name; `null` means system-wide.
     */
    @JvmOverloads fun fence(ordering: AtomicOrdering, syncScope: String? = null) {
        emit(Fence(ordering, syncScope))
    }

    /**
     * Emits a compare-and-exchange instruction (`cmpxchg`).
     *
     * Atomically compares the value at [ptr] with [cmp]; if they are equal, writes [new].
     * The operation returns both the original value and a success flag.
     *
     * @param ptr the pointer to the memory location.
     * @param cmp the expected value to compare against.
     * @param new the value to write if the comparison succeeds.
     * @param successOrdering the memory ordering for the successful exchange.
     * @param failureOrdering the memory ordering for the failed comparison (must be at most
     *   as strong as [successOrdering]).
     * @param weak if `true`, a weak CAS that may spuriously fail even when the comparison
     *   would succeed (useful in retry loops).
     * @param volatile if `true`, the operation is treated as volatile.
     * @return a `{ oldValue, i1 succeeded }` struct. Extract fields with [extractValue].
     */
    @JvmOverloads fun cmpxchg(ptr: Value, cmp: Value, new: Value, successOrdering: AtomicOrdering, failureOrdering: AtomicOrdering, weak: Boolean = false, volatile: Boolean = false): Value =
        nextRef(Type.Struct(null, listOf(cmp.type, Type.I1))).also { emit(CmpXchg(it, ptr, cmp, new, successOrdering, failureOrdering, weak, volatile)) }

    /**
     * Emits an atomic read-modify-write instruction (`atomicrmw`).
     *
     * Atomically applies [op] to the value at [ptr] with [value] as the operand, and returns
     * the original value before the modification.
     *
     * @param op the operation to perform (e.g. [AtomicRMWOp.ADD], [AtomicRMWOp.XCHG]).
     * @param ptr the pointer to the memory location.
     * @param value the operand for the operation.
     * @param ordering the memory ordering for the operation.
     * @param volatile if `true`, the operation is treated as volatile.
     * @return the SSA value that was at [ptr] before the atomic operation.
     */
    @JvmOverloads fun atomicRMW(op: AtomicRMWOp, ptr: Value, value: Value, ordering: AtomicOrdering, volatile: Boolean = false): Value =
        nextRef(value.type).also { emit(AtomicRMW(it, op, ptr, value, ordering, volatile)) }

    /**
     * Emits a memory copy instruction (`memcpy`).
     *
     * Copies [len] bytes from the non-overlapping source address [src] to [dst]. This
     * instruction has no SSA result.
     *
     * @param dst the destination pointer.
     * @param src the source pointer; must not overlap with [dst].
     * @param len the number of bytes to copy (an integer value).
     * @param volatile if `true`, both the load from [src] and the store to [dst] are volatile.
     */
    @JvmOverloads fun memcpy(dst: Value, src: Value, len: Value, volatile: Boolean = false) { emit(MemCpy(dst, src, len, volatile)) }

    /**
     * Emits a memory set instruction (`memset`).
     *
     * Fills [len] bytes starting at [dst] with the byte value [value]. This instruction has
     * no SSA result.
     *
     * @param dst the destination pointer.
     * @param value the byte value to fill with (an `i8` integer value).
     * @param len the number of bytes to fill (an integer value).
     * @param volatile if `true`, the stores are volatile.
     */
    @JvmOverloads fun memset(dst: Value, value: Value, len: Value, volatile: Boolean = false) { emit(MemSet(dst, value, len, volatile)) }

    /**
     * Emits a memory move instruction (`memmove`).
     *
     * Copies [len] bytes from [src] to [dst]. Unlike [memcpy], the source and destination
     * ranges may overlap. This instruction has no SSA result.
     *
     * @param dst the destination pointer.
     * @param src the source pointer; may overlap with [dst].
     * @param len the number of bytes to copy (an integer value).
     * @param volatile if `true`, both the load from [src] and the store to [dst] are volatile.
     */
    @JvmOverloads fun memmove(dst: Value, src: Value, len: Value, volatile: Boolean = false) { emit(MemMove(dst, src, len, volatile)) }

    /**
     * Emits a cache prefetch hint instruction (`prefetch`).
     *
     * Hints to the hardware that data at [address] will be accessed soon. This is a
     * performance hint; it has no effect on program correctness and produces no SSA result.
     *
     * @param address the address to prefetch.
     * @param rw `0` for a read prefetch, `1` for a write prefetch.
     * @param locality the temporal locality hint from `0` (no locality) to `3` (high locality).
     * @param cacheType `1` for data cache, `2` for instruction cache.
     */
    fun prefetch(address: Value, rw: Int, locality: Int, cacheType: Int) { emit(Prefetch(address, rw, locality, cacheType)) }

    // Stack / lifetime

    /**
     * Emits a stack-save instruction (`stacksave`).
     *
     * Captures the current stack pointer so that it can be restored later with [stackRestore].
     * Typically used in pairs around dynamic `alloca` regions to free the dynamic allocation.
     *
     * @return an opaque pointer representing the saved stack pointer state.
     */
    fun stackSave(): Value = nextRef(Type.OpaquePointer).also { emit(StackSave(it)) }

    /**
     * Emits a stack-restore instruction (`stackrestore`).
     *
     * Restores the stack pointer to the state captured by a previous [stackSave] call,
     * effectively freeing all dynamic allocations made since that save. This instruction
     * has no SSA result.
     *
     * @param ptr the opaque pointer value returned by a prior [stackSave].
     */
    fun stackRestore(ptr: Value) { emit(StackRestore(ptr)) }

    /**
     * Emits a lifetime-start marker for an `alloca` slot.
     *
     * Informs the optimizer and memory analysis tools that the memory at [ptr] begins its
     * live range here. Accesses before this marker are undefined behavior. This instruction
     * has no SSA result.
     *
     * @param ptr the pointer to the alloca whose lifetime begins.
     * @param size the size in bytes of the alloca. Pass `-1` when the size is unknown.
     */
    fun lifetimeStart(ptr: Value, size: Long) { emit(LifetimeStart(ptr, size)) }

    /**
     * Emits a lifetime-end marker for an `alloca` slot.
     *
     * Informs the optimizer and memory analysis tools that the memory at [ptr] ends its
     * live range here. Accesses after this marker are undefined behavior. This instruction
     * has no SSA result.
     *
     * @param ptr the pointer to the alloca whose lifetime ends.
     * @param size the size in bytes of the alloca. Pass `-1` when the size is unknown.
     */
    fun lifetimeEnd(ptr: Value, size: Long) { emit(LifetimeEnd(ptr, size)) }

    // Conversions

    /**
     * Emits an integer truncation instruction (`trunc`).
     *
     * Discards high-order bits, narrowing [value] to [toType]. The source type must be
     * wider than [toType].
     *
     * @param value the integer value to narrow.
     * @param toType the narrower integer type to truncate to.
     * @return the SSA value holding the low-order bits of [value] in [toType].
     */
    fun trunc(value: Value, toType: Type): Value = nextRef(toType).also { emit(IntTrunc(it, value, toType)) }

    /**
     * Emits a zero-extension instruction (`zext`).
     *
     * Widens [value] to [toType] by prepending zero bits. The source type must be narrower
     * than [toType]. Use for unsigned integer widening.
     *
     * @param value the integer value to widen.
     * @param toType the wider integer type to extend to.
     * @return the SSA value holding [value] zero-extended to [toType].
     */
    fun zext(value: Value, toType: Type): Value = nextRef(toType).also { emit(ZExt(it, value, toType)) }

    /**
     * Emits a sign-extension instruction (`sext`).
     *
     * Widens [value] to [toType] by replicating the sign bit. The source type must be
     * narrower than [toType]. Use for signed integer widening.
     *
     * @param value the signed integer value to widen.
     * @param toType the wider integer type to extend to.
     * @return the SSA value holding [value] sign-extended to [toType].
     */
    fun sext(value: Value, toType: Type): Value = nextRef(toType).also { emit(SExt(it, value, toType)) }

    /**
     * Emits a floating-point truncation instruction (`fptrunc`).
     *
     * Converts a floating-point value to a narrower floating-point type, rounding if
     * necessary (e.g. `f64` → `f32`).
     *
     * @param value the floating-point value to narrow.
     * @param toType the narrower floating-point type to truncate to.
     * @return the SSA value in [toType].
     */
    fun fptrunc(value: Value, toType: Type): Value = nextRef(toType).also { emit(FPTrunc(it, value, toType)) }

    /**
     * Emits a floating-point extension instruction (`fpext`).
     *
     * Converts a floating-point value to a wider floating-point type without loss of
     * precision (e.g. `f32` → `f64`).
     *
     * @param value the floating-point value to widen.
     * @param toType the wider floating-point type to extend to.
     * @return the SSA value in [toType].
     */
    fun fpext(value: Value, toType: Type): Value = nextRef(toType).also { emit(FPExt(it, value, toType)) }

    /**
     * Emits a floating-point to unsigned integer conversion (`fptoui`).
     *
     * Truncates the floating-point [value] toward zero and produces an unsigned integer.
     * Results are poison when [value] is NaN or out of range for [toType].
     *
     * @param value the floating-point value to convert.
     * @param toType the target unsigned integer type.
     * @return the SSA integer value in [toType].
     */
    fun fptoui(value: Value, toType: Type): Value = nextRef(toType).also { emit(FPToUI(it, value, toType)) }

    /**
     * Emits a floating-point to signed integer conversion (`fptosi`).
     *
     * Truncates the floating-point [value] toward zero and produces a signed integer.
     * Results are poison when [value] is NaN or out of range for [toType].
     *
     * @param value the floating-point value to convert.
     * @param toType the target signed integer type.
     * @return the SSA integer value in [toType].
     */
    fun fptosi(value: Value, toType: Type): Value = nextRef(toType).also { emit(FPToSI(it, value, toType)) }

    /**
     * Emits an unsigned integer to floating-point conversion (`uitofp`).
     *
     * Converts an unsigned integer to a floating-point value, rounding if [toType] cannot
     * represent the value exactly.
     *
     * @param value the unsigned integer value to convert.
     * @param toType the target floating-point type.
     * @return the SSA floating-point value in [toType].
     */
    fun uitofp(value: Value, toType: Type): Value = nextRef(toType).also { emit(UIToFP(it, value, toType)) }

    /**
     * Emits a signed integer to floating-point conversion (`sitofp`).
     *
     * Converts a signed integer to a floating-point value, rounding if [toType] cannot
     * represent the value exactly.
     *
     * @param value the signed integer value to convert.
     * @param toType the target floating-point type.
     * @return the SSA floating-point value in [toType].
     */
    fun sitofp(value: Value, toType: Type): Value = nextRef(toType).also { emit(SIToFP(it, value, toType)) }

    /**
     * Emits a pointer-to-integer conversion (`ptrtoint`).
     *
     * Reinterprets the raw pointer address as an integer. The integer width must be large
     * enough to hold a pointer on the target platform.
     *
     * @param value the pointer value to convert.
     * @param toType the integer type to convert to (e.g. `Type.I64` on 64-bit targets).
     * @return the SSA integer value holding the raw address.
     */
    fun ptrtoint(value: Value, toType: Type): Value = nextRef(toType).also { emit(PtrToInt(it, value, toType)) }

    /**
     * Emits an integer-to-pointer conversion (`inttoptr`).
     *
     * Reinterprets an integer as a pointer address. Dereferencing the result without an
     * appropriate allocation is undefined behavior.
     *
     * @param value the integer value to convert.
     * @param toType the pointer type to produce.
     * @return the SSA pointer value at the address represented by [value].
     */
    fun inttoptr(value: Value, toType: Type): Value = nextRef(toType).also { emit(IntToPtr(it, value, toType)) }

    /**
     * Emits a bitcast instruction (`bitcast`).
     *
     * Reinterprets the bit pattern of [value] as [toType] without changing any bits. The
     * source and destination types must have the same bit width. No data conversion occurs.
     *
     * @param value the value to reinterpret.
     * @param toType the target type; must be the same size as `value.type`.
     * @return the SSA value with the same bits as [value] but typed as [toType].
     */
    fun bitcast(value: Value, toType: Type): Value = nextRef(toType).also { emit(BitCast(it, value, toType)) }

    /**
     * Emits an address-space cast instruction (`addrspacecast`).
     *
     * Converts a pointer from one address space to another. The numeric value of the pointer
     * may change if the target platform uses different pointer representations per address
     * space.
     *
     * @param value the pointer value to cast.
     * @param toType the pointer type in the target address space.
     * @return the SSA pointer value in the new address space.
     */
    fun addrspacecast(value: Value, toType: Type): Value = nextRef(toType).also { emit(AddrSpaceCast(it, value, toType)) }

    // Control flow

    /**
     * Emits a function return instruction (`ret`).
     *
     * Terminates the current basic block and returns control to the caller. This is a
     * terminator instruction with no SSA result.
     *
     * @param value the value to return, or `null` to return `void`.
     */
    @JvmOverloads fun ret(value: Value? = null) { emit(Ret(value)) }

    /**
     * Emits an unconditional branch instruction (`br`).
     *
     * Terminates the current basic block and transfers control to [target]. This is a
     * terminator instruction with no SSA result.
     *
     * @param target the label name of the destination basic block.
     */
    fun br(target: BlockRef) { emit(Br(target)) }
    fun br(target: String) { br(BlockRef(target)) }

    /**
     * Emits a conditional branch instruction (`br cond, true, false`).
     *
     * Terminates the current basic block and transfers control to [trueTarget] if [condition]
     * is non-zero, or to [falseTarget] otherwise. This is a terminator instruction with no
     * SSA result.
     *
     * @param condition an `i1` value that selects the branch direction.
     * @param trueTarget the label name of the block to jump to when [condition] is `1`.
     * @param falseTarget the label name of the block to jump to when [condition] is `0`.
     */
    fun condBr(condition: Value, trueTarget: BlockRef, falseTarget: BlockRef) { emit(CondBr(condition, trueTarget, falseTarget)) }
    fun condBr(condition: Value, trueTarget: String, falseTarget: String) { condBr(condition, BlockRef(trueTarget), BlockRef(falseTarget)) }

    /**
     * Emits a switch instruction (`switch`).
     *
     * Terminates the current basic block and transfers control to one of the [cases] whose
     * constant matches [value], or to [defaultTarget] if no case matches. This is a
     * terminator instruction with no SSA result.
     *
     * @param value the integer selector value.
     * @param defaultTarget the label name of the block reached when no case matches.
     * @param cases a list of `(constant, label)` pairs; each constant is compared to [value].
     */
    fun switch(value: Value, defaultTarget: BlockRef, cases: List<Pair<Constant, BlockRef>>) { emit(Switch(value, defaultTarget, cases)) }
    fun switch(value: Value, defaultTarget: String, cases: List<Pair<Constant, String>>) { switch(value, BlockRef(defaultTarget), cases.map { it.first to BlockRef(it.second) }) }

    /**
     * Emits an indirect branch instruction (`indirectbr`).
     *
     * Terminates the current basic block and jumps to the address stored in [address]. The
     * [targets] list must enumerate all possible destination labels so that control-flow
     * analysis remains sound. This is a terminator instruction with no SSA result.
     *
     * @param address a pointer-sized value holding the jump target address (typically
     *   obtained from a `blockaddress` constant).
     * @param targets the complete set of possible destination label names.
     */
    fun indirectBr(address: Value, targets: List<BlockRef>) { emit(IndirectBr(address, targets)) }
    @JvmName("indirectBrByName") fun indirectBr(address: Value, targets: List<String>) { indirectBr(address, targets.map { BlockRef(it) }) }

    /**
     * Emits an unreachable instruction (`unreachable`).
     *
     * Marks the current position as unreachable. Executing an unreachable is undefined
     * behavior; the optimizer may use it to prune dead code. This is a terminator
     * instruction with no SSA result.
     */
    fun unreachable() { emit(Unreachable()) }

    /**
     * Emits a hardware trap instruction.
     *
     * Generates an illegal instruction or software breakpoint that unconditionally aborts
     * the process (analogous to `__builtin_trap()`). This is a terminator instruction with
     * no SSA result.
     */
    fun trap() { emit(Trap()) }

    /**
     * Emits a debug trap instruction.
     *
     * Generates a debugger breakpoint (e.g. `int3` on x86). Unlike [trap], a debug trap
     * may have a [successor] block when control is expected to resume after the breakpoint
     * is handled by a debugger.
     *
     * @param successor the label name of the block to resume execution at after the
     *   breakpoint, or `null` if execution should not resume (acts like [trap]).
     */
    @JvmOverloads fun debugTrap(successor: BlockRef? = null) { emit(DebugTrap(successor)) }
    fun debugTrap(successor: String) { debugTrap(BlockRef(successor)) }

    // Calls

    /**
     * Emits a direct or indirect function call instruction (`call`).
     *
     * When [returnType] is [Type.Void], the instruction has no SSA result and `null` is
     * returned.
     *
     * @param function a [Value] representing the callee — either a [GlobalRef] for a known
     *   symbol or any pointer-typed value for an indirect call.
     * @param args the argument values to pass; must match the callee's parameter types.
     * @param returnType the type of the value the callee returns.
     * @param callingConv the calling convention to use (defaults to [CallingConvention.C]).
     * @param tailCall the tail-call hint (defaults to [TailCallKind.NONE]; use
     *   [TailCallKind.TAIL] or [TailCallKind.MUST_TAIL] for optimized returns).
     * @return the SSA return value, or `null` when [returnType] is [Type.Void].
     */
    @JvmOverloads fun call(function: Value, args: List<Value>, returnType: Type, callingConv: CallingConvention = CallingConvention.C, tailCall: TailCallKind = TailCallKind.NONE): Value? {
        if (returnType == Type.Void) { emit(Call(null, function, args, returnType, callingConv, tailCall)); return null }
        return nextRef(returnType).also { emit(Call(it, function, args, returnType, callingConv, tailCall)) }
    }

    /**
     * Emits a direct function call by name.
     *
     * Convenience overload that resolves the callee as a [GlobalRef] using [functionName].
     * The function type is inferred from [args] and [returnType].
     *
     * @param functionName the name of the global function symbol to call.
     * @param args the argument values to pass.
     * @param returnType the type of the value the callee returns.
     * @param callingConv the calling convention to use (defaults to [CallingConvention.C]).
     * @return the SSA return value, or `null` when [returnType] is [Type.Void].
     */
    @JvmOverloads fun call(functionName: String, args: List<Value>, returnType: Type, callingConv: CallingConvention = CallingConvention.C): Value? =
        call(GlobalRef(functionName, Type.Function(args.map { it.type }, returnType)), args, returnType, callingConv)

    /**
     * Emits an invoke instruction for exception-handling call sites (`invoke`).
     *
     * Like [call], but with two successor basic blocks: [normalDest] is reached when the
     * callee returns normally, and [unwindDest] is reached (via a landing pad) when an
     * exception is thrown. This is a terminator instruction.
     *
     * When [returnType] is [Type.Void], no SSA result is allocated and `null` is returned.
     *
     * @param function the callee value (global or indirect pointer).
     * @param args the argument values to pass.
     * @param returnType the type of the value returned on the normal path.
     * @param normalDest the label name of the block to enter on a normal return.
     * @param unwindDest the label name of the landing-pad block to enter on an exception.
     * @param callingConv the calling convention to use.
     * @return the SSA return value on the normal path, or `null` for void-returning callees.
     */
    @JvmOverloads fun invoke(function: Value, args: List<Value>, returnType: Type, normalDest: BlockRef, unwindDest: BlockRef, callingConv: CallingConvention = CallingConvention.C): Value? {
        if (returnType == Type.Void) { emit(Invoke(null, function, args, returnType, normalDest, unwindDest, callingConv)); return null }
        return nextRef(returnType).also { emit(Invoke(it, function, args, returnType, normalDest, unwindDest, callingConv)) }
    }
    @JvmOverloads fun invoke(function: Value, args: List<Value>, returnType: Type, normalDest: String, unwindDest: String, callingConv: CallingConvention = CallingConvention.C): Value? =
        invoke(function, args, returnType, BlockRef(normalDest), BlockRef(unwindDest), callingConv)

    /**
     * Emits a callbr instruction for inline-assembly with label outputs (`callbr`).
     *
     * Used for inline assembly that can branch to one of several label destinations in
     * addition to falling through. The [fallthrough] block is always a successor; the
     * [indirectDests] are additional successors reachable via inline-asm `goto` labels.
     * This is a terminator instruction.
     *
     * When [returnType] is [Type.Void], no SSA result is allocated and `null` is returned.
     *
     * @param function the callee or inline-asm value.
     * @param args the argument values.
     * @param returnType the type of the value produced on the fall-through path.
     * @param fallthrough the label name of the default fall-through successor block.
     * @param indirectDests additional successor label names reachable from inline-asm gotos.
     * @return the SSA result value on the fall-through path, or `null` for void callees.
     */
    fun callBr(function: Value, args: List<Value>, returnType: Type, fallthrough: BlockRef, indirectDests: List<BlockRef>): Value? {
        if (returnType == Type.Void) { emit(CallBr(null, function, args, returnType, fallthrough, indirectDests)); return null }
        return nextRef(returnType).also { emit(CallBr(it, function, args, returnType, fallthrough, indirectDests)) }
    }
    @JvmName("callBrByName") fun callBr(function: Value, args: List<Value>, returnType: Type, fallthrough: String, indirectDests: List<String>): Value? =
        callBr(function, args, returnType, BlockRef(fallthrough), indirectDests.map { BlockRef(it) })

    // Varargs

    /**
     * Emits a `va_start` instruction to initialize a variadic argument list.
     *
     * Must be called at the beginning of a variadic function before the first [vaArg].
     * This instruction has no SSA result.
     *
     * @param argList a pointer to the `va_list` object to initialize (typically an
     *   `alloca` of the platform's `va_list` type).
     */
    fun vaStart(argList: Value) { emit(VAStart(argList)) }

    /**
     * Emits a `va_end` instruction to clean up a variadic argument list.
     *
     * Must be called after the last [vaArg] and before the function returns. Matches a
     * corresponding [vaStart] or [vaCopy]. This instruction has no SSA result.
     *
     * @param argList the `va_list` pointer previously initialized by [vaStart] or [vaCopy].
     */
    fun vaEnd(argList: Value) { emit(VAEnd(argList)) }

    /**
     * Emits a `va_copy` instruction to duplicate a variadic argument list.
     *
     * Copies the state of [src] into [dst] so that [dst] can be independently iterated.
     * [dst] must be cleaned up with a corresponding [vaEnd]. This instruction has no SSA
     * result.
     *
     * @param dst a pointer to the destination `va_list` to initialize.
     * @param src the source `va_list` pointer to copy from.
     */
    fun vaCopy(dst: Value, src: Value) { emit(VACopy(dst, src)) }

    /**
     * Emits a `va_arg` instruction to read the next argument from a variadic argument list.
     *
     * Advances the `va_list` state and returns the next argument interpreted as [argType].
     *
     * @param argList the `va_list` pointer previously initialized by [vaStart] or [vaCopy].
     * @param argType the type of the next variadic argument to retrieve.
     * @return the SSA value holding the next argument as [argType].
     */
    fun vaArg(argList: Value, argType: Type): Value = nextRef(argType).also { emit(VAArg(it, argList, argType)) }

    // Exception handling (native)

    /**
     * Emits a landing pad instruction (`landingpad`).
     *
     * Must be the first non-phi instruction in an unwind destination block (the block named
     * in an [invoke]'s `unwindDest`). Describes the exception types handled here and
     * produces a struct value holding the exception pointer and selector.
     *
     * @param resultType the type of the landing pad result — typically a `{ ptr, i32 }` struct
     *   holding the exception object pointer and the exception selector integer.
     * @param clauses the list of catch / filter clauses describing which exceptions are handled.
     * @param cleanup if `true`, this landing pad is entered for cleanup (e.g. destructor calls)
     *   even when no clause matches, before re-throwing the exception.
     * @return the SSA struct value containing the exception pointer and selector.
     */
    @JvmOverloads fun landingPad(resultType: Type, clauses: List<LandingPadClause>, cleanup: Boolean = false): Value =
        nextRef(resultType).also { emit(LandingPad(it, resultType, clauses, cleanup)) }

    /**
     * Emits a resume instruction (`resume`).
     *
     * Re-throws the exception captured by a [landingPad] instruction, propagating it to the
     * next enclosing exception handler in the call stack. This is a terminator instruction
     * with no SSA result.
     *
     * @param value the landing pad result value (as returned by [landingPad]) to re-throw.
     */
    fun resume(value: Value) { emit(Resume(value)) }

    /**
     * Emits a catchswitch instruction (`catchswitch`) for Windows-style SEH exception handling.
     *
     * Introduces a set of catch handler labels for an exception personality function. The
     * result token is used by [catchPad] instructions in the handler blocks.
     *
     * @param parentPad the enclosing pad token (from a [cleanupPad] or another [catchSwitch]),
     *   or `null` for the outermost exception region.
     * @param handlers the label names of basic blocks that begin with a [catchPad].
     * @param unwindDest the label of the block to unwind to if no handler matches, or `null`
     *   to unwind to the caller.
     * @return a `token` value consumed by [catchPad] and [catchRet] instructions.
     */
    fun catchSwitch(parentPad: Value?, handlers: List<BlockRef>, unwindDest: BlockRef?): Value =
        nextRef(Type.Token).also { emit(CatchSwitch(it, parentPad, handlers, unwindDest)) }
    @JvmName("catchSwitchByName") fun catchSwitch(parentPad: Value?, handlers: List<String>, unwindDest: String?): Value =
        catchSwitch(parentPad, handlers.map { BlockRef(it) }, unwindDest?.let { BlockRef(it) })

    /**
     * Emits a catchpad instruction (`catchpad`) for Windows-style SEH exception handling.
     *
     * Must be the first non-phi instruction in a catch handler block. Checks whether the
     * in-flight exception matches the handler's criteria (described by [args]).
     *
     * @param catchSwitch the token produced by the [catchSwitch] instruction that dispatched
     *   to this handler block.
     * @param args platform-specific arguments describing the exception filter (e.g. the
     *   type descriptor pointer for MSVC C++ exceptions).
     * @return a `token` value that must be passed to [catchRet] to leave the handler.
     */
    fun catchPad(catchSwitch: Value, args: List<Value>): Value =
        nextRef(Type.Token).also { emit(CatchPad(it, catchSwitch, args)) }

    /**
     * Emits a cleanuppad instruction (`cleanuppad`) for Windows-style SEH cleanup actions.
     *
     * Must be the first non-phi instruction in a cleanup handler block. Analogous to
     * [catchPad] but for cleanup (finally-like) handlers rather than catch handlers.
     *
     * @param parentPad the enclosing pad token, or `null` for the outermost region.
     * @param args platform-specific arguments for the cleanup handler.
     * @return a `token` value that must be passed to [cleanupRet] when cleanup is done.
     */
    fun cleanupPad(parentPad: Value?, args: List<Value>): Value =
        nextRef(Type.Token).also { emit(CleanupPad(it, parentPad, args)) }

    /**
     * Emits a catchret instruction (`catchret`) to leave a catch handler.
     *
     * Terminates the catch handler block established by [catchPad] and transfers control to
     * [dest]. This is a terminator instruction with no SSA result.
     *
     * @param catchPad the token produced by the matching [catchPad] instruction.
     * @param dest the label name of the block to jump to after the handler completes.
     */
    fun catchRet(catchPad: Value, dest: BlockRef) { emit(CatchRet(catchPad, dest)) }
    fun catchRet(catchPad: Value, dest: String) { catchRet(catchPad, BlockRef(dest)) }

    /**
     * Emits a cleanupret instruction (`cleanupret`) to leave a cleanup handler.
     *
     * Terminates the cleanup block established by [cleanupPad] and either resumes unwinding
     * to [unwindDest] or continues unwinding to the caller. This is a terminator instruction
     * with no SSA result.
     *
     * @param cleanupPad the token produced by the matching [cleanupPad] instruction.
     * @param unwindDest the label of the next cleanup or catch block to unwind to, or `null`
     *   to unwind to the caller.
     */
    fun cleanupRet(cleanupPad: Value, unwindDest: BlockRef?) { emit(CleanupRet(cleanupPad, unwindDest)) }
    fun cleanupRet(cleanupPad: Value, unwindDest: String) { cleanupRet(cleanupPad, BlockRef(unwindDest)) }

    // SSA

    /**
     * Emits a phi instruction (`phi`).
     *
     * A phi node merges values from different predecessor basic blocks. It must be placed at
     * the beginning of its block, before any non-phi instructions. Each entry in [incoming]
     * pairs a value with the label of the predecessor block from which that value flows.
     *
     * @param type the common type shared by all incoming values.
     * @param incoming a list of `(value, predecessorLabel)` pairs; one entry per predecessor.
     * @return the SSA value that equals the incoming value from whichever predecessor was
     *   last executed.
     */
    fun phi(type: Type, incoming: List<Pair<Value, BlockRef>>): Value = nextRef(type).also { emit(Phi(it, incoming)) }
    @JvmName("phiByName") fun phi(type: Type, incoming: List<Pair<Value, String>>): Value = phi(type, incoming.map { it.first to BlockRef(it.second) })

    /**
     * Emits a select instruction (`select`).
     *
     * Picks one of two values based on a boolean condition, analogous to the ternary operator
     * `condition ? trueValue : falseValue`. Both branches are conceptually evaluated; this
     * is not a branch.
     *
     * @param condition an `i1` value that selects between the two alternatives.
     * @param trueValue the value returned when [condition] is `1`.
     * @param falseValue the value returned when [condition] is `0`; must have the same type
     *   as [trueValue].
     * @return the SSA value equal to [trueValue] or [falseValue] depending on [condition].
     */
    fun select(condition: Value, trueValue: Value, falseValue: Value): Value =
        nextRef(trueValue.type).also { emit(Select(it, condition, trueValue, falseValue)) }

    /**
     * Emits a freeze instruction (`freeze`).
     *
     * Converts a potentially poison or undef [value] into a fixed, non-deterministic but
     * non-poison value. After freezing, the optimizer may not assume anything about the
     * value except that it is a valid bit pattern for its type. Use this to safely consume
     * values that might otherwise be poison.
     *
     * @param value the potentially poison or undef value to freeze.
     * @return the SSA value with the same type as [value], guaranteed to be non-poison.
     */
    fun freeze(value: Value): Value = nextRef(value.type).also { emit(Freeze(it, value)) }

    // Vector

    /**
     * Emits an extractelement instruction (`extractelement`).
     *
     * Reads a single scalar element from a vector value at a dynamic index.
     *
     * @param vector the source vector value; must be a [Type.Vector].
     * @param index the element index to read; must be an integer type. An out-of-bounds
     *   index produces a poison value.
     * @return the SSA scalar value of the vector's element type at the given [index].
     */
    fun extractElement(vector: Value, index: Value): Value =
        nextRef((vector.type as Type.Vector).element).also { emit(ExtractElement(it, vector, index)) }

    /**
     * Emits an insertelement instruction (`insertelement`).
     *
     * Produces a new vector identical to [vector] except that the element at [index] is
     * replaced by [element]. The original [vector] is not modified (SSA immutability).
     *
     * @param vector the source vector value; must be a [Type.Vector].
     * @param element the scalar value to insert; must match the vector's element type.
     * @param index the position to insert at; must be an integer type. An out-of-bounds
     *   index produces a poison value.
     * @return the SSA vector value with the element at [index] replaced by [element].
     */
    fun insertElement(vector: Value, element: Value, index: Value): Value =
        nextRef(vector.type).also { emit(InsertElement(it, vector, element, index)) }

    /**
     * Emits a shufflevector instruction (`shufflevector`).
     *
     * Selects and reorders elements from [v1] and [v2] according to [mask]. Each mask entry
     * is an index into the concatenation of [v1] and [v2]; a value of `-1` produces a
     * poison element in that lane. The result vector length equals `mask.size`.
     *
     * @param v1 the first source vector; must be a [Type.Vector].
     * @param v2 the second source vector; must have the same type as [v1].
     * @param mask a list of element indices selecting from `[v1 ++ v2]`. Use `-1` for
     *   a poison (don't-care) lane.
     * @return the SSA vector value containing the selected elements.
     */
    fun shuffleVector(v1: Value, v2: Value, mask: List<Int>): Value {
        val vecType = v1.type as Type.Vector
        return nextRef(Type.Vector(vecType.element, mask.size)).also { emit(ShuffleVector(it, v1, v2, mask)) }
    }

    /**
     * Emits a vector splat instruction.
     *
     * Broadcasts a single scalar [scalar] into every lane of a vector of type [vectorType].
     * Equivalent to a [shuffleVector] with a zeroes mask applied to a single-element vector
     * but expressed more directly.
     *
     * @param scalar the scalar value to broadcast; must match [vectorType]'s element type.
     * @param vectorType the target vector type, specifying the element type and lane count.
     * @return the SSA vector value with every lane equal to [scalar].
     */
    fun splat(scalar: Value, vectorType: Type.Vector): Value =
        nextRef(vectorType).also { emit(Splat(it, scalar, vectorType)) }

    /**
     * Emits a vector reduction instruction.
     *
     * Reduces all lanes of [vector] into a single scalar using [op] (e.g.
     * [VectorReduceOp.ADD], [VectorReduceOp.FMAX]). The result is a scalar of the vector's
     * element type.
     *
     * @param op the reduction operation to apply across lanes.
     * @param vector the source vector value to reduce; must be a [Type.Vector].
     * @return the SSA scalar value produced by reducing all lanes with [op].
     */
    fun vectorReduce(op: VectorReduceOp, vector: Value): Value =
        nextRef((vector.type as Type.Vector).element).also { emit(VectorReduce(it, op, vector)) }

    // Aggregate

    /**
     * Emits an extractvalue instruction (`extractvalue`).
     *
     * Reads a field from a struct or array aggregate value using a sequence of static
     * [indices]. The type of the result is determined by traversing the aggregate type
     * at compile time.
     *
     * @param aggregate the struct or array value to read from; must be a [Type.Struct] or
     *   [Type.Array].
     * @param indices one or more zero-based integer indices that form the path through
     *   nested aggregates to the target field or element.
     * @return the SSA value of the field or element at the given path.
     */
    fun extractValue(aggregate: Value, vararg indices: Int): Value {
        var currentType = aggregate.type
        for (idx in indices) {
            currentType = when (currentType) {
                is Type.Struct -> currentType.fields[idx]
                is Type.Array -> currentType.element
                else -> error("Cannot extract from $currentType")
            }
        }
        return nextRef(currentType).also { emit(ExtractValue(it, aggregate, indices.toList())) }
    }

    /**
     * Emits an insertvalue instruction (`insertvalue`).
     *
     * Produces a new aggregate identical to [aggregate] except that the field or element at
     * the path described by [indices] is replaced by [element]. The original [aggregate] is
     * not modified.
     *
     * @param aggregate the struct or array value to update; must be a [Type.Struct] or
     *   [Type.Array].
     * @param element the new value to insert at the indexed position.
     * @param indices one or more zero-based integer indices forming the path to the target
     *   field or element.
     * @return the SSA aggregate value with the field at [indices] replaced by [element].
     */
    fun insertValue(aggregate: Value, element: Value, vararg indices: Int): Value =
        nextRef(aggregate.type).also { emit(InsertValue(it, aggregate, element, indices.toList())) }

    // High-level: objects

    /**
     * Emits a managed object allocation instruction.
     *
     * Allocates a new instance of the class named [className] on the managed heap. The
     * constructor is not called here — use [constructorCall] after allocating.
     *
     * @param className the fully-qualified class name (e.g. `"java/lang/Object"`).
     * @param typeArgs generic type arguments for the class instantiation; empty for
     *   non-generic classes.
     * @return the SSA reference value pointing to the newly allocated object.
     */
    @JvmOverloads fun newObject(className: String, typeArgs: List<Type> = emptyList()): Value =
        nextRef(Type.ClassRef(className)).also { emit(NewObject(it, className, typeArgs)) }

    /**
     * Emits a single-dimensional managed array allocation instruction.
     *
     * Allocates a new array on the managed heap with [size] elements of [elementType].
     *
     * @param elementType the type of each element in the array.
     * @param size an integer value specifying the number of elements to allocate.
     * @return the SSA reference value pointing to the new array object.
     */
    fun newArray(elementType: Type, size: Value): Value =
        nextRef(Type.Array(elementType, 0)).also { emit(NewArray(it, elementType, size)) }

    /**
     * Emits a multi-dimensional managed array allocation instruction.
     *
     * Allocates a multi-dimensional array on the managed heap. Each entry in [dimensions]
     * specifies the length of the corresponding dimension.
     *
     * @param elementType the type of the leaf-level array elements.
     * @param dimensions integer values specifying the length of each array dimension, in
     *   order from outermost to innermost.
     * @return the SSA reference value pointing to the new multi-dimensional array object.
     */
    fun newMultiArray(elementType: Type, dimensions: List<Value>): Value =
        nextRef(Type.Array(elementType, 0)).also { emit(NewMultiArray(it, elementType, dimensions)) }

    // High-level: fields

    /**
     * Emits an instance field read instruction.
     *
     * Reads the instance field named [fieldName] of type [fieldType] from the object [obj],
     * which is an instance of [className].
     *
     * @param obj the object reference to read from.
     * @param className the class that declares the field.
     * @param fieldName the name of the field to read.
     * @param fieldType the declared type of the field.
     * @return the SSA value holding the field's current value.
     */
    fun getField(obj: Value, className: String, fieldName: String, fieldType: Type): Value =
        nextRef(fieldType).also { emit(GetField(it, obj, className, fieldName, fieldType)) }

    /**
     * Emits an instance field write instruction.
     *
     * Writes [value] to the instance field named [fieldName] of type [fieldType] on the
     * object [obj]. This instruction has no SSA result.
     *
     * @param obj the object reference to write to.
     * @param className the class that declares the field.
     * @param fieldName the name of the field to write.
     * @param fieldType the declared type of the field.
     * @param value the value to store in the field.
     */
    fun putField(obj: Value, className: String, fieldName: String, fieldType: Type, value: Value) { emit(PutField(obj, className, fieldName, fieldType, value)) }

    /**
     * Emits a static field read instruction.
     *
     * Reads the value of the static field named [fieldName] of type [fieldType] declared on
     * [className]. No object reference is required.
     *
     * @param className the class that declares the static field.
     * @param fieldName the name of the static field to read.
     * @param fieldType the declared type of the field.
     * @return the SSA value holding the static field's current value.
     */
    fun getStatic(className: String, fieldName: String, fieldType: Type): Value =
        nextRef(fieldType).also { emit(GetStatic(it, className, fieldName, fieldType)) }

    /**
     * Emits a static field write instruction.
     *
     * Writes [value] to the static field named [fieldName] of type [fieldType] declared on
     * [className]. This instruction has no SSA result.
     *
     * @param className the class that declares the static field.
     * @param fieldName the name of the static field to write.
     * @param fieldType the declared type of the field.
     * @param value the value to store in the static field.
     */
    fun putStatic(className: String, fieldName: String, fieldType: Type, value: Value) { emit(PutStatic(className, fieldName, fieldType, value)) }

    // High-level: dispatch
    private fun callOrVoid(ret: Type, make: (InstructionRef?) -> Instruction): Value? {
        if (ret == Type.Void) { emit(make(null)); return null }
        return nextRef(ret).also { emit(make(it)) }
    }

    /**
     * Emits a virtual method dispatch instruction.
     *
     * Invokes [methodName] on [obj] through the virtual method table (vtable) of [className].
     * The actual implementation called depends on the runtime type of [obj]. This is the
     * standard dispatch for overridable instance methods.
     *
     * Returns `null` when [methodType]'s return type is [Type.Void].
     *
     * @param obj the receiver object reference.
     * @param className the class in whose vtable slot the method is looked up.
     * @param methodName the name of the virtual method to call.
     * @param methodType the function signature of the method.
     * @param args argument values (not including the implicit receiver [obj]).
     * @return the SSA return value, or `null` for void-returning methods.
     */
    fun virtualCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? =
        callOrVoid(methodType.ret) { VirtualCall(it, obj, className, methodName, methodType, args) }

    /**
     * Emits an interface method dispatch instruction.
     *
     * Invokes [methodName] on [obj] through the interface table (itable) of [interfaceName].
     * This is the standard dispatch for interface method calls where the concrete type of
     * [obj] is not statically known.
     *
     * Returns `null` when [methodType]'s return type is [Type.Void].
     *
     * @param obj the receiver object reference.
     * @param interfaceName the interface whose itable slot is used for dispatch.
     * @param methodName the name of the interface method to call.
     * @param methodType the function signature of the method.
     * @param args argument values (not including the implicit receiver [obj]).
     * @return the SSA return value, or `null` for void-returning methods.
     */
    fun interfaceCall(obj: Value, interfaceName: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? =
        callOrVoid(methodType.ret) { InterfaceCall(it, obj, interfaceName, methodName, methodType, args) }

    /**
     * Emits a special (non-virtual) instance method call instruction.
     *
     * Invokes [methodName] on [obj] without vtable dispatch, targeting the exact method
     * defined in [className]. Used for `invokespecial` semantics: superclass calls,
     * private methods, and `<init>` constructors.
     *
     * Returns `null` when [methodType]'s return type is [Type.Void].
     *
     * @param obj the receiver object reference.
     * @param className the class that directly declares the method to invoke.
     * @param methodName the name of the method.
     * @param methodType the function signature of the method.
     * @param args argument values (not including the implicit receiver [obj]).
     * @return the SSA return value, or `null` for void-returning methods.
     */
    fun specialCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? =
        callOrVoid(methodType.ret) { SpecialCall(it, obj, className, methodName, methodType, args) }

    /**
     * Emits a static method call instruction.
     *
     * Invokes the static method [methodName] on [className] without a receiver object.
     * Corresponds to `invokestatic` semantics.
     *
     * Returns `null` when [methodType]'s return type is [Type.Void].
     *
     * @param className the class that declares the static method.
     * @param methodName the name of the static method.
     * @param methodType the function signature of the method.
     * @param args argument values to pass.
     * @return the SSA return value, or `null` for void-returning methods.
     */
    fun staticCall(className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? =
        callOrVoid(methodType.ret) { StaticCall(it, className, methodName, methodType, args) }

    /**
     * Emits a dynamic call instruction for `invokedynamic` sites.
     *
     * The actual callee is resolved at first call time by the [bootstrapMethod], which
     * produces a call site bound to a specific target. Subsequent calls use the cached
     * call site directly. Used for lambda metafactory, string concatenation, and other
     * dynamically-linked dispatch sites.
     *
     * Returns `null` when [methodType]'s return type is [Type.Void].
     *
     * @param bootstrapMethod describes the bootstrap method and its static arguments.
     * @param name the dynamic method name hint passed to the bootstrap method.
     * @param methodType the function signature of the dynamically-resolved call site.
     * @param args argument values to pass at the call site.
     * @return the SSA return value, or `null` for void-returning call sites.
     */
    fun dynamicCall(bootstrapMethod: BootstrapMethod, name: String, methodType: Type.Function, args: List<Value>): Value? =
        callOrVoid(methodType.ret) { DynamicCall(it, bootstrapMethod, name, methodType, args) }

    /**
     * Emits a constructor invocation instruction.
     *
     * Calls the constructor of [className] on the already-allocated object [obj]. This
     * should be preceded by a [newObject] allocation. The instruction has no SSA result
     * because constructors are always void-returning.
     *
     * @param obj the object reference to initialize; must already be allocated.
     * @param className the class whose constructor to invoke.
     * @param constructorType the function signature of the constructor (parameter types only;
     *   return is always void).
     * @param args the constructor arguments (not including the implicit receiver [obj]).
     */
    fun constructorCall(obj: Value, className: String, constructorType: Type.Function, args: List<Value>) { emit(ConstructorCall(obj, className, constructorType, args)) }

    // High-level: type ops

    /**
     * Emits an instanceof type-test instruction.
     *
     * Tests whether [obj] is an instance of [checkType] at runtime. A null reference
     * always produces `false`.
     *
     * @param obj the object reference to test.
     * @param checkType the type to test against (class, interface, or array type).
     * @return the SSA `i1` value: `1` if [obj] is an instance of [checkType], `0` otherwise.
     */
    fun instanceOf(obj: Value, checkType: Type): Value = nextRef(Type.I1).also { emit(InstanceOf(it, obj, checkType)) }

    /**
     * Emits a checked cast instruction.
     *
     * Asserts that [obj] can be cast to [castType] at runtime. If the cast fails, the
     * runtime throws a `ClassCastException` (or equivalent). A null reference passes the
     * check and produces a null of [castType].
     *
     * @param obj the object reference to cast.
     * @param castType the target type to cast to.
     * @return the SSA reference value of [castType] pointing to the same object as [obj].
     */
    fun checkCast(obj: Value, castType: Type): Value = nextRef(castType).also { emit(CheckCast(it, obj, castType)) }

    /**
     * Emits a runtime type-ID instruction.
     *
     * Retrieves the integer type identifier for the runtime class of [obj]. The identifier
     * is unique per class within the running program and can be used for fast type equality
     * checks (as opposed to full `instanceof` subtype tests).
     *
     * @param obj the object reference whose runtime type ID to retrieve.
     * @return the SSA `i32` value representing the runtime type identifier of [obj].
     */
    fun typeId(obj: Value): Value = nextRef(Type.I32).also { emit(TypeId(it, obj)) }

    // High-level: managed arrays

    /**
     * Emits a managed array element read instruction.
     *
     * Reads the element at [index] from the managed array [array]. Bounds checking behavior
     * is controlled by the runtime; out-of-bounds access may throw an exception.
     *
     * @param array the managed array reference to read from.
     * @param index an integer value specifying the zero-based element index.
     * @param elementType the type of the array's elements.
     * @return the SSA value holding the element at [index].
     */
    fun arrayGet(array: Value, index: Value, elementType: Type): Value =
        nextRef(elementType).also { emit(ArrayGet(it, array, index, elementType)) }

    /**
     * Emits a managed array element write instruction.
     *
     * Writes [value] to the element at [index] in the managed array [array]. This
     * instruction has no SSA result.
     *
     * @param array the managed array reference to write to.
     * @param index an integer value specifying the zero-based element index.
     * @param value the value to store at [index]; must match [elementType].
     * @param elementType the type of the array's elements.
     */
    fun arraySet(array: Value, index: Value, value: Value, elementType: Type) { emit(ArraySet(array, index, value, elementType)) }

    /**
     * Emits a managed array length instruction.
     *
     * Reads the number of elements in the managed array [array]. Equivalent to Java's
     * `array.length`.
     *
     * @param array the managed array reference whose length to read.
     * @return the SSA `i32` value holding the number of elements in [array].
     */
    fun arrayLength(array: Value): Value = nextRef(Type.I32).also { emit(ArrayLength(it, array)) }

    // High-level: monitors

    /**
     * Emits a monitorenter instruction.
     *
     * Acquires the intrinsic lock (monitor) of the managed object [obj]. Corresponds to the
     * JVM `monitorenter` bytecode and to the entry of a `synchronized` block. This
     * instruction has no SSA result.
     *
     * @param obj the object whose monitor to acquire.
     */
    fun monitorEnter(obj: Value) { emit(MonitorEnter(obj)) }

    /**
     * Emits a monitorexit instruction.
     *
     * Releases the intrinsic lock (monitor) of the managed object [obj]. Corresponds to the
     * JVM `monitorexit` bytecode and to the exit of a `synchronized` block. Must be paired
     * with a preceding [monitorEnter]. This instruction has no SSA result.
     *
     * @param obj the object whose monitor to release.
     */
    fun monitorExit(obj: Value) { emit(MonitorExit(obj)) }

    // High-level: managed exceptions

    /**
     * Emits a managed throw instruction.
     *
     * Throws the managed exception object [exception], transferring control to the nearest
     * enclosing exception handler. This is a terminator instruction with no SSA result.
     *
     * @param exception the exception reference to throw; must be a managed reference type.
     */
    fun throwException(exception: Value) { emit(Throw(exception)) }

    /**
     * Emits a try-catch region descriptor.
     *
     * Declares that the basic block labeled [tryBlock] is the protected region, with the
     * given [catches] describing which exception types are caught and which handler blocks
     * to jump to. An optional [finallyBlock] is always executed after the try or catch.
     *
     * This is a high-level annotation used during lowering; it does not directly correspond
     * to a native instruction. This instruction has no SSA result.
     *
     * @param tryBlock the label of the basic block that starts the protected region.
     * @param catches the list of catch handler descriptors (exception type + handler block label).
     * @param finallyBlock the label of the finally block to execute unconditionally, or
     *   `null` if there is no finally clause.
     */
    @JvmOverloads fun tryCatch(tryBlock: BlockRef, catches: List<CatchHandler>, finallyBlock: BlockRef? = null) { emit(TryCatchRegion(tryBlock, catches, finallyBlock)) }
    @JvmOverloads fun tryCatch(tryBlock: String, catches: List<CatchHandler>, finallyBlock: String? = null) { tryCatch(BlockRef(tryBlock), catches, finallyBlock?.let { BlockRef(it) }) }

    // High-level: boxing

    /**
     * Emits a primitive-to-object boxing instruction.
     *
     * Wraps the primitive [value] in a managed boxed type [boxType] (e.g. `int` → `Integer`).
     * The result is a managed reference to the box object on the heap.
     *
     * @param value the primitive value to box.
     * @param boxType the managed reference type that wraps the primitive (e.g.
     *   `Type.ClassRef("java/lang/Integer")`).
     * @return the SSA managed reference value holding the boxed primitive.
     */
    fun box(value: Value, boxType: Type): Value = nextRef(boxType).also { emit(Box(it, value, boxType)) }

    /**
     * Emits an object-to-primitive unboxing instruction.
     *
     * Extracts the primitive value from a managed box object [obj]. If [obj] is null, the
     * runtime throws a `NullPointerException`.
     *
     * @param obj the managed box reference to unbox.
     * @param unboxType the primitive type to extract (e.g. `Type.I32` for `Integer`).
     * @return the SSA primitive value contained within [obj].
     */
    fun unbox(obj: Value, unboxType: Type): Value = nextRef(unboxType).also { emit(Unbox(it, obj, unboxType)) }

    // High-level: closures

    /**
     * Emits a closure creation instruction.
     *
     * Bundles [function] together with its [captures] (closed-over values) into a callable
     * closure object of type [closureType]. The resulting closure can be invoked later with
     * [closureInvoke].
     *
     * @param function the function value to close over (typically a [GlobalRef] or lambda).
     * @param captures the list of values captured from the enclosing scope.
     * @param closureType the function type of the closure's call signature.
     * @return the SSA value representing the newly created closure object.
     */
    fun closureCreate(function: Value, captures: List<Value>, closureType: Type.Function): Value =
        nextRef(closureType).also { emit(ClosureCreate(it, function, captures, closureType)) }

    /**
     * Emits a closure invocation instruction.
     *
     * Calls the closure [closure] with [args]. The closure's captured environment is
     * implicitly passed. Returns `null` when [returnType] is [Type.Void].
     *
     * @param closure the closure value to invoke (as created by [closureCreate]).
     * @param args the argument values to pass (not including captured variables).
     * @param returnType the type of the value returned by the closure.
     * @return the SSA return value, or `null` for void-returning closures.
     */
    fun closureInvoke(closure: Value, args: List<Value>, returnType: Type): Value? =
        callOrVoid(returnType) { ClosureInvoke(it, closure, args, returnType) }

    // High-level: tagged unions

    /**
     * Emits a tagged union variant construction instruction.
     *
     * Creates a value of the tagged union type [unionType] with the active variant set to
     * [variantName], initializing its fields from [fields].
     *
     * @param unionType the [Type.TaggedUnion] to construct.
     * @param variantName the name of the variant to activate.
     * @param fields the field values for the chosen variant, in declaration order.
     * @return the SSA tagged union value with [variantName] active.
     */
    fun constructVariant(unionType: Type.TaggedUnion, variantName: String, fields: List<Value>): Value =
        nextRef(unionType).also { emit(ConstructVariant(it, unionType, variantName, fields)) }

    /**
     * Emits a tag read instruction on a tagged union.
     *
     * Reads the discriminant (tag) from [union] that identifies the currently active variant.
     * The tag type is determined by the union's type definition.
     *
     * @param union the tagged union value to inspect; must be a [Type.TaggedUnion].
     * @return the SSA tag value (an integer identifying the active variant).
     */
    fun getTag(union: Value): Value {
        val tagType = (union.type as? Type.TaggedUnion)?.tagType ?: Type.I32
        return nextRef(tagType).also { emit(GetTag(it, union)) }
    }

    /**
     * Emits a variant field read instruction on a tagged union.
     *
     * Reads a field from the [variantName] variant of [union]. Calling this when a
     * different variant is active is undefined behavior; guard with [getTag] or [tagSwitch]
     * first.
     *
     * @param union the tagged union value; must be a [Type.TaggedUnion].
     * @param variantName the name of the variant whose field to read.
     * @param fieldIndex the zero-based index of the field within the variant.
     * @param fieldType the type of the field.
     * @return the SSA value of the field at [fieldIndex] in the [variantName] variant.
     */
    fun getVariantField(union: Value, variantName: String, fieldIndex: Int, fieldType: Type): Value =
        nextRef(fieldType).also { emit(GetVariantField(it, union, variantName, fieldIndex)) }

    /**
     * Emits a tag-switch terminator instruction on a tagged union.
     *
     * Dispatches to one of the [cases] based on the active variant tag of [union], or to
     * [defaultTarget] if no case matches. Each entry in [cases] pairs a variant name with
     * the label of the destination block. This is a terminator instruction with no SSA
     * result.
     *
     * @param union the tagged union value to dispatch on; must be a [Type.TaggedUnion].
     * @param cases a list of `(variantName, blockLabel)` pairs.
     * @param defaultTarget the label of the block to jump to when no variant matches, or
     *   `null` if all variants are covered by [cases].
     */
    @JvmOverloads fun tagSwitch(union: Value, cases: List<Pair<String, BlockRef>>, defaultTarget: BlockRef? = null) { emit(TagSwitch(union, cases, defaultTarget)) }
    @JvmName("tagSwitchByName") @JvmOverloads fun tagSwitch(union: Value, cases: List<Pair<String, String>>, defaultTarget: String? = null) {
        tagSwitch(union, cases.map { it.first to BlockRef(it.second) }, defaultTarget?.let { BlockRef(it) })
    }

    // High-level: exception values

    /**
     * Emits a catch-value instruction that retrieves the current in-flight exception.
     *
     * Used in exception handler blocks to obtain the thrown exception object as a managed
     * reference. This is the high-level alternative to the native [landingPad] instruction
     * for managed runtimes.
     *
     * @param exceptionType the expected type of the exception being caught.
     * @return the SSA managed reference value pointing to the in-flight exception object.
     */
    fun catchValue(exceptionType: Type): Value =
        nextRef(Type.Reference(exceptionType)).also { emit(CatchValue(it, exceptionType)) }

    // High-level: weak references

    /**
     * Emits a weak-reference creation instruction.
     *
     * Creates a weak reference to the managed object [obj]. The GC may collect the object
     * even while the weak reference exists; use [readWeakRef] to check whether the object is
     * still alive.
     *
     * @param obj a strong managed reference to the object to weakly reference.
     * @return the SSA [Type.WeakReference] value wrapping [obj].
     */
    fun makeWeakRef(obj: Value): Value =
        nextRef(Type.WeakReference((obj.type as Type.Reference).referent)).also { emit(MakeWeakRef(it, obj)) }

    /**
     * Emits a weak-reference read instruction.
     *
     * Attempts to upgrade the weak reference [weakRef] to a strong (nullable) reference. If
     * the referent has been collected by the GC, the result is a null reference.
     *
     * @param weakRef the [Type.WeakReference] value to dereference.
     * @return a nullable strong managed reference to the referent, or null if collected.
     */
    fun readWeakRef(weakRef: Value): Value {
        val referent = (weakRef.type as Type.WeakReference).referent
        return nextRef(Type.Reference(referent, nullable = true)).also { emit(ReadWeakRef(it, weakRef)) }
    }

    /**
     * Emits a weak-reference clear instruction.
     *
     * Explicitly clears [weakRef], making subsequent [readWeakRef] calls return null.
     * This instruction has no SSA result.
     *
     * @param weakRef the [Type.WeakReference] value to clear.
     */
    fun clearWeakRef(weakRef: Value) { emit(ClearWeakRef(weakRef)) }

    // High-level: GC

    /**
     * Emits a GC-managed heap allocation instruction.
     *
     * Allocates an instance of [allocType] on the garbage-collected heap. Unlike [newObject],
     * this is a lower-level allocation that does not require a class name and works for any
     * type including structs.
     *
     * @param allocType the type to allocate; the GC will manage the resulting object's
     *   lifetime.
     * @param size an optional explicit byte count to allocate. When `null`, the size is
     *   derived from [allocType].
     * @return the SSA managed reference value pointing to the newly allocated object.
     */
    @JvmOverloads fun gcAlloc(allocType: Type, size: Value? = null): Value =
        nextRef(Type.Reference(allocType)).also { emit(GCAlloc(it, allocType, size)) }

    /**
     * Emits a GC safepoint instruction.
     *
     * Marks a point at which the GC may pause the current thread to perform a collection.
     * All managed references live at this point must be registered as GC roots via [gcRoot].
     * This instruction has no SSA result.
     */
    fun gcSafepoint() { emit(GCSafepoint()) }

    /**
     * Emits a GC root registration instruction.
     *
     * Registers [ptr] as a GC root, ensuring the GC can find and potentially update the
     * reference during collection. Roots are typically live managed references held in stack
     * slots. This instruction has no SSA result.
     *
     * @param ptr the managed reference or pointer to register as a GC root.
     * @param metadata an optional metadata value (e.g. type information) associated with
     *   this root.
     */
    @JvmOverloads fun gcRoot(ptr: Value, metadata: Value? = null) { emit(GCRoot(ptr, metadata)) }

    // High-level: pinning and interior pointers

    /**
     * Emits a pin instruction that prevents the GC from moving the referenced object.
     *
     * Produces a [Type.PinnedRef] that can be safely converted to a raw pointer and passed
     * to native code. The pin must be released with [unpin] before the object can be moved
     * again.
     *
     * @param ref the managed reference to pin; must be a [Type.Reference].
     * @return the SSA [Type.PinnedRef] value backed by the same object as [ref].
     */
    fun pin(ref: Value): Value {
        val referent = (ref.type as Type.Reference).referent
        return nextRef(Type.PinnedRef(referent)).also { emit(Pin(it, ref)) }
    }

    /**
     * Emits an unpin instruction that releases a previously pinned object.
     *
     * After this instruction, the GC is free to move the object that was pinned by the
     * matching [pin]. This instruction has no SSA result.
     *
     * @param ref the [Type.PinnedRef] value returned by a prior [pin].
     */
    fun unpin(ref: Value) { emit(Unpin(ref)) }

    /**
     * Emits an interior pointer instruction.
     *
     * Computes a pointer to an element inside a GC-managed array or struct without pinning
     * the whole object. The GC tracks interior pointers and updates them during compaction.
     *
     * @param ref the managed reference to the containing object.
     * @param index an integer offset or element index within the object.
     * @param pointeeType the type of the element the interior pointer addresses.
     * @return the SSA [Type.InteriorRef] value pointing to the element at [index] within [ref].
     */
    fun interiorPtr(ref: Value, index: Value, pointeeType: Type): Value =
        nextRef(Type.InteriorRef(pointeeType)).also { emit(InteriorPtr(it, ref, index, pointeeType)) }

    /**
     * Emits a write barrier instruction for GC-tracked field stores.
     *
     * Notifies the GC that a reference field in [obj] at [fieldIndex] is about to be
     * overwritten with [value]. Required by generational and concurrent GC algorithms to
     * maintain their invariants. This instruction has no SSA result.
     *
     * @param obj the managed object containing the field being written.
     * @param fieldIndex an integer value identifying the field being written (typically a
     *   field offset or index).
     * @param value the new reference value being stored into the field.
     */
    fun writeBarrier(obj: Value, fieldIndex: Value, value: Value) { emit(WriteBarrier(obj, fieldIndex, value)) }

    /**
     * Emits a read barrier instruction for GC-tracked reference loads.
     *
     * Notifies the GC of a reference load, enabling concurrent GC algorithms (e.g.
     * Shenandoah, ZGC) to forward or update the reference if the object has been moved.
     *
     * @param ref the managed reference value being read.
     * @return the SSA reference value, potentially forwarded to the object's new location.
     */
    fun readBarrier(ref: Value): Value =
        nextRef(ref.type).also { emit(ReadBarrier(it, ref)) }

    /**
     * Emits a managed boundary call instruction.
     *
     * Calls [function] with [args], crossing a managed/unmanaged boundary as described by
     * [direction]. The runtime performs any necessary setup (e.g. thread state transitions,
     * handle creation) around the call.
     *
     * Returns `null` when [returnType] is [Type.Void].
     *
     * @param function the function value (global or pointer) to call.
     * @param args the argument values to pass.
     * @param returnType the type of the value the callee returns.
     * @param direction whether the call goes from managed to native or native to managed.
     * @return the SSA return value, or `null` for void-returning callees.
     */
    fun managedCall(function: Value, args: List<Value>, returnType: Type, direction: ManagedCallDirection): Value? {
        if (returnType == Type.Void) { emit(ManagedCall(null, function, args, returnType, direction)); return null }
        return nextRef(returnType).also { emit(ManagedCall(it, function, args, returnType, direction)) }
    }

    // High-level: refcounting

    /**
     * Emits a reference-count retain instruction.
     *
     * Increments the reference count of the reference-counted managed object [obj]. Must be
     * balanced by a corresponding [refRelease]. This instruction has no SSA result.
     *
     * @param obj the managed reference to retain.
     */
    fun refRetain(obj: Value) { emit(RefRetain(obj)) }

    /**
     * Emits a reference-count release instruction.
     *
     * Decrements the reference count of the reference-counted managed object [obj]. When the
     * count reaches zero the runtime may immediately collect the object. Must balance a prior
     * [refRetain]. This instruction has no SSA result.
     *
     * @param obj the managed reference to release.
     */
    fun refRelease(obj: Value) { emit(RefRelease(obj)) }

    /**
     * Emits a reference-count read instruction.
     *
     * Reads the current reference count of the managed object [obj]. Primarily useful for
     * debugging; ordinary code should use [refRetain] and [refRelease] rather than
     * inspecting the raw count.
     *
     * @param obj the managed reference whose reference count to read.
     * @return the SSA `i32` value holding the current reference count of [obj].
     */
    fun refCount(obj: Value): Value = nextRef(Type.I32).also { emit(RefCount(it, obj)) }

    // High-level: coroutines

    /**
     * Emits a coroutine-begin instruction (`coro.begin`).
     *
     * Marks the start of a coroutine frame. [id] is the token produced by an earlier
     * `coro.id` intrinsic call and [mem] is the coroutine frame memory (either a preallocated
     * buffer or null to let the runtime allocate). This instruction must appear exactly once
     * per coroutine function, before any [coroSuspend] instructions.
     *
     * @param id the coroutine identifier token (from `coro.id`).
     * @param mem a pointer to the preallocated coroutine frame, or a null pointer to allow
     *   the runtime to allocate the frame.
     * @return the SSA opaque-pointer coroutine handle used by all subsequent coroutine
     *   instructions in this function.
     */
    fun coroBegin(id: Value, mem: Value): Value = nextRef(Type.OpaquePointer).also { emit(CoroBegin(it, id, mem)) }

    /**
     * Emits a coroutine-end instruction (`coro.end`).
     *
     * Marks the final return point of a coroutine. When [unwind] is `false`, control is
     * returned to the last resumer; when `true`, the coroutine is being unwound due to an
     * exception. This instruction has no SSA result.
     *
     * @param handle the coroutine handle produced by [coroBegin].
     * @param unwind `true` if the coroutine is ending due to an exception unwind, `false`
     *   for a normal completion.
     */
    @JvmOverloads fun coroEnd(handle: Value, unwind: Boolean = false) { emit(CoroEnd(handle, unwind)) }

    /**
     * Emits a coroutine-suspend instruction (`coro.suspend`).
     *
     * Suspends the coroutine and returns control to its caller/resumer. The [save] value, if
     * provided, is a `coro.save` token for this specific suspend point; omit for simple
     * symmetric coroutines. The returned `i8` value indicates the resume path: `0` for
     * normal resume, `1` for final suspend, `-1` for destruction.
     *
     * @param save an optional `coro.save` token that explicitly identifies this suspend point.
     * @param isFinal `true` if this is the final suspend point (after which the coroutine
     *   cannot be resumed — only destroyed).
     * @return the SSA `i8` value indicating how execution resumed at this suspend point.
     */
    @JvmOverloads fun coroSuspend(save: Value? = null, isFinal: Boolean = false): Value = nextRef(Type.I8).also { emit(CoroSuspend(it, save, isFinal)) }

    /**
     * Emits a coroutine-resume instruction (`coro.resume`).
     *
     * Resumes the suspended coroutine identified by [handle], transferring control into the
     * coroutine body at its last suspend point. This instruction has no SSA result.
     *
     * @param handle the coroutine handle (from [coroBegin] or passed by the caller) to resume.
     */
    fun coroResume(handle: Value) { emit(CoroResume(handle)) }

    /**
     * Emits a coroutine-destroy instruction (`coro.destroy`).
     *
     * Destroys the coroutine identified by [handle], running cleanup and freeing the
     * coroutine frame. The coroutine must not be resumed after destruction. This
     * instruction has no SSA result.
     *
     * @param handle the coroutine handle to destroy.
     */
    fun coroDestroy(handle: Value) { emit(CoroDestroy(handle)) }

    /**
     * Emits a coroutine-size query instruction (`coro.size`).
     *
     * Returns the number of bytes required for the coroutine's frame allocation. Used when
     * preallocating the frame buffer passed to [coroBegin].
     *
     * @return the SSA `i64` value holding the byte size of the coroutine frame.
     */
    fun coroSize(): Value = nextRef(Type.I64).also { emit(CoroSize(it)) }

    // Intrinsic / inline assembly

    /**
     * Emits a named intrinsic call instruction.
     *
     * Invokes a well-known compiler or runtime intrinsic by [name] (e.g.
     * `"kgen.safepoint.poll"` or `"llvm.memcpy"`). The intrinsic is lowered by the code
     * generator to the appropriate instruction sequence for the target.
     *
     * Returns `null` when [returnType] is [Type.Void].
     *
     * @param name the intrinsic name; must be recognized by the target code generator.
     * @param args the argument values to pass to the intrinsic.
     * @param returnType the type of the value produced by the intrinsic.
     * @return the SSA result value, or `null` for void intrinsics.
     */
    fun intrinsic(name: String, args: List<Value>, returnType: Type): Value? =
        callOrVoid(returnType) { Intrinsic(it, name, args, returnType) }

    /**
     * Emits an inline assembly instruction.
     *
     * Embeds raw assembly text [assembly] directly into the output. The [constraints] string
     * follows the GCC/LLVM inline assembly constraint syntax and describes input, output, and
     * clobber operands.
     *
     * Returns `null` when [returnType] is [Type.Void].
     *
     * @param assembly the assembly template string (may reference `$0`, `$1`, etc. for
     *   operand placeholders).
     * @param constraints the operand constraint string (e.g. `"=r,r,r"` for two input
     *   registers and one output register).
     * @param args the IR values to bind to the assembly's input operands.
     * @param returnType the type of the value produced by the assembly; [Type.Void] for
     *   no output.
     * @param sideEffects if `true`, the assembly has observable side effects and must not
     *   be reordered or eliminated by the optimizer.
     * @param alignStack if `true`, the stack is aligned to the platform's natural alignment
     *   before the assembly is executed.
     * @param dialect the assembly syntax dialect ([AsmDialect.ATT] for AT&T syntax,
     *   [AsmDialect.INTEL] for Intel syntax).
     * @return the SSA result value, or `null` when [returnType] is [Type.Void].
     */
    @JvmOverloads fun inlineAsm(assembly: String, constraints: String, args: List<Value> = emptyList(), returnType: Type = Type.Void, sideEffects: Boolean = true, alignStack: Boolean = false, dialect: AsmDialect = AsmDialect.ATT): Value? =
        callOrVoid(returnType) { InlineAsm(it, assembly, constraints, sideEffects, alignStack, dialect, args, returnType) }

    // Debug / metadata

    /**
     * Emits a source location annotation (`debug_loc`).
     *
     * Associates the instructions that follow with a specific source file location for
     * debug information. Does not emit any executable code; only affects DWARF/PDB output.
     * This instruction has no SSA result.
     *
     * @param line the 1-based source line number.
     * @param col the 1-based source column number.
     * @param scope the debug scope name (e.g. the enclosing function or lexical block name).
     * @param inlinedAt the scope name of the call site if this location is from an inlined
     *   function, or `null` for non-inlined code.
     */
    @JvmOverloads fun debugLoc(line: Int, col: Int, scope: String, inlinedAt: String? = null) { emit(DebugLoc(line, col, scope, inlinedAt)) }

    /**
     * Emits a debug value annotation (`debug_value`).
     *
     * Associates the SSA [value] with the named source variable [variable] at the current
     * point in the program, so that debuggers can display the variable's value. Does not
     * emit any executable code. This instruction has no SSA result.
     *
     * @param variable the name of the source-level variable.
     * @param value the SSA value that holds the variable's current value.
     * @param expression an optional DWARF expression string (e.g. `"DW_OP_deref"`) to
     *   describe how to compute the final variable value from [value].
     */
    @JvmOverloads fun debugValue(variable: String, value: Value, expression: String? = null) { emit(DebugValue(variable, value, expression)) }

    /**
     * Emits a debug declare annotation (`debug_declare`).
     *
     * Declares that the source variable [variable] lives in memory at the address [address]
     * for the lifetime of its enclosing scope. Typically used with `alloca`-backed variables
     * that have their address taken. Does not emit any executable code. This instruction has
     * no SSA result.
     *
     * @param variable the name of the source-level variable.
     * @param address the SSA pointer value (typically from [alloca]) that holds the variable.
     * @param expression an optional DWARF expression string to describe a transformation on
     *   [address] to find the variable (e.g. a byte offset).
     */
    @JvmOverloads fun debugDeclare(variable: String, address: Value, expression: String? = null) { emit(DebugDeclare(variable, address, expression)) }

    // Optimizer hints

    /**
     * Emits an assume instruction that asserts a condition is always true.
     *
     * Informs the optimizer that [condition] holds at this point. If [condition] is ever
     * actually `false` at runtime, the behavior is undefined. Use this to convey
     * invariants that the optimizer cannot derive on its own (e.g. alignment guarantees,
     * non-null inputs, value ranges). This instruction has no SSA result.
     *
     * @param condition an `i1` value that the optimizer may assume is always `1`.
     */
    fun assume(condition: Value) { emit(Assume(condition)) }

    /**
     * Emits a branch-prediction hint instruction (`expect`).
     *
     * Hints to the optimizer and code generator that [value] is very likely to equal
     * [expected] at runtime (analogous to `__builtin_expect`). The hint may influence
     * branch ordering and basic-block layout but does not change program semantics.
     *
     * @param value the runtime value to hint about.
     * @param expected the constant value that [value] is most likely to take.
     * @return the SSA value identical to [value] (the hint is transparent to the IR value).
     */
    fun expect(value: Value, expected: Constant): Value = nextRef(value.type).also { emit(Expect(it, value, expected)) }
}

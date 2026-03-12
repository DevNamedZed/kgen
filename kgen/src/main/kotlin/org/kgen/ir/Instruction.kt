package org.kgen.ir

/**
 * Sealed interface for all IR instructions. Every instruction has an optional [result]
 * value — null for void instructions (stores, branches, etc.).
 *
 * Instructions are organized into 17 functional [IrCategory] groups (Arithmetic,
 * Memory, Object, Runtime, etc.) which map to [IrTier]s representing their position
 * in the compilation pipeline. See `spec/ir.md` for the full specification.
 */
sealed interface Instruction {
    val result: Value?

    /** Functional category this instruction belongs to. See [IrCategory]. */
    val category: IrCategory

    // =====================================================================
    // ARITHMETIC — integer and float computation
    // =====================================================================

    /** Integer addition: `lhs + rhs`. Both operands must have the same integer type.
     *  [nuw] = poison on unsigned overflow, [nsw] = poison on signed overflow. */
    data class Add(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Integer subtraction: `lhs - rhs`. Same type constraints and flags as [Add]. */
    data class Sub(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Integer multiplication: `lhs * rhs`. Same type constraints and flags as [Add]. */
    data class Mul(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Unsigned integer division: `lhs / rhs` (truncated toward zero).
     *  Division by zero is undefined behavior. [exact] = poison if remainder is non-zero. */
    data class UDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Signed integer division: `lhs / rhs` (truncated toward zero).
     *  Division by zero and INT_MIN / -1 are undefined behavior. */
    data class SDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Unsigned integer remainder: `lhs % rhs`. Remainder by zero is UB. */
    data class URem(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Signed integer remainder: `lhs % rhs`. Sign of result matches sign of dividend. */
    data class SRem(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Integer negation: `-operand`. Equivalent to `sub 0, operand`. */
    data class Neg(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    // --- Overflow-checked arithmetic ---
    // These return a struct {result, i1_overflow_flag}.

    /** Signed add with overflow detection. Result: `{sum, did_overflow}`. */
    data class SAddOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned add with overflow detection. Result: `{sum, did_overflow}`. */
    data class UAddOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Signed subtract with overflow detection. Result: `{difference, did_overflow}`. */
    data class SSubOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned subtract with overflow detection. */
    data class USubOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Signed multiply with overflow detection. */
    data class SMulOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned multiply with overflow detection. */
    data class UMulOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    // --- Saturating arithmetic ---
    // Clamp result to representable range instead of wrapping.

    /** Signed saturating add. Clamps to [INT_MIN, INT_MAX] on overflow. */
    data class SAddSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned saturating add. Clamps to [0, UINT_MAX] on overflow. */
    data class UAddSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Signed saturating subtract. */
    data class SSubSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned saturating subtract. */
    data class USubSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    // --- Min / max ---

    /** Signed integer minimum of two values. */
    data class SMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Signed integer maximum of two values. */
    data class SMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned integer minimum of two values. */
    data class UMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Unsigned integer maximum of two values. */
    data class UMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    /** Integer absolute value. [isIntMin] = result is poison when operand is INT_MIN
     *  (allows more efficient codegen on targets without a single abs instruction). */
    data class Abs(val dest: InstructionRef, val operand: Value, val isIntMin: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    // --- Float arithmetic ---

    /** Float addition: `lhs + rhs`. IEEE 754 semantics unless [fastMath] flags relax them. */
    data class FAdd(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float subtraction: `lhs - rhs`. */
    data class FSub(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float multiplication: `lhs * rhs`. */
    data class FMul(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float division: `lhs / rhs`. */
    data class FDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float remainder (IEEE 754): `lhs % rhs`. Sign of result matches dividend. */
    data class FRem(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float negation: flips the sign bit. `fneg(-0.0) = +0.0`. */
    data class FNeg(val dest: InstructionRef, val operand: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Float absolute value: clears the sign bit. */
    data class FAbs(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Fused multiply-add: `a * b + c` with a single rounding (more accurate than separate mul+add). */
    data class FMA(val dest: InstructionRef, val a: Value, val b: Value, val c: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** IEEE 754 minimum. Returns the non-NaN operand if one is NaN. `fmin(-0, +0) = -0`. */
    data class FMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** IEEE 754 maximum. Returns the non-NaN operand if one is NaN. `fmax(-0, +0) = +0`. */
    data class FMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Square root. `sqrt(negative) = NaN`, `sqrt(+inf) = +inf`, `sqrt(-0) = -0`. */
    data class Sqrt(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Round toward positive infinity (ceiling). */
    data class Ceil(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Round toward negative infinity (floor). */
    data class Floor(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Round to nearest integer, ties away from zero. */
    data class Round(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Round toward zero (truncate fractional part). Not to be confused with [IntTrunc]. */
    data class Trunc(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }
    /** Copy sign bit from [sign] to [magnitude], preserving magnitude's absolute value. */
    data class CopySign(val dest: InstructionRef, val magnitude: Value, val sign: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.ARITHMETIC }

    // =====================================================================
    // BITWISE — bit logic, shifts, rotations, queries
    // =====================================================================

    /** Bitwise AND: each bit is 1 only if both corresponding bits are 1. */
    data class And(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Bitwise OR: each bit is 1 if either corresponding bit is 1. */
    data class Or(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Bitwise XOR: each bit is 1 if the corresponding bits differ. */
    data class Xor(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Bitwise NOT (one's complement): flips every bit. Equivalent to `xor operand, -1`. */
    data class Not(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Shift left: `lhs << rhs`, filling vacated bits with zero. Shift by >= bitwidth is UB. */
    data class Shl(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Logical shift right: `lhs >>> rhs`, filling with zero (unsigned). Shift by >= bitwidth is UB. */
    data class LShr(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Arithmetic shift right: `lhs >> rhs`, filling with sign bit (signed). Shift by >= bitwidth is UB. */
    data class AShr(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Circular rotate left: bits shifted out the high end re-enter at the low end. */
    data class RotateLeft(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Circular rotate right: bits shifted out the low end re-enter at the high end. */
    data class RotateRight(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }

    /** Count Leading Zeros: number of 0-bits before the first 1-bit from MSB.
     *  Returns bitwidth when operand is 0 (unless [isZeroPoison] is set). */
    data class Ctlz(val dest: InstructionRef, val operand: Value, val isZeroPoison: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Count Trailing Zeros: number of 0-bits after the last 1-bit from LSB.
     *  Returns bitwidth when operand is 0 (unless [isZeroPoison] is set). */
    data class Cttz(val dest: InstructionRef, val operand: Value, val isZeroPoison: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Population Count (Hamming weight): counts the number of 1-bits in the operand.
     *  Example: `ctpop(0b10110) = 3`. */
    data class Ctpop(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Byte Swap: reverses byte order for endianness conversion.
     *  Example: `bswap(0x12345678) = 0x78563412`. Operand must be a multiple of 16 bits. */
    data class BSwap(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }
    /** Bit Reverse: reverses the order of all bits (not bytes).
     *  Example for i8: `bitreverse(0b11000001) = 0b10000011`. */
    data class BitReverse(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }

    /** Rotate left. Result type matches operand type. Amount is masked to bit width. */
    data class Rotl(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }

    /** Rotate right. Result type matches operand type. Amount is masked to bit width. */
    data class Rotr(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.BITWISE }

    // =====================================================================
    // COMPARISON — integer and float comparison
    // =====================================================================

    /** Integer comparison. Both operands must have the same integer type. Result is always i1.
     *  See [ICmpPredicate] for available predicates (EQ, NE, signed/unsigned comparisons). */
    data class ICmp(val dest: InstructionRef, val predicate: ICmpPredicate, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.COMPARISON }
    /** Float comparison. Result is always i1. See [FCmpPredicate] for predicates.
     *  Ordered predicates return false if either operand is NaN.
     *  Unordered predicates return true if either operand is NaN. */
    data class FCmp(val dest: InstructionRef, val predicate: FCmpPredicate, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest; override val category get() = IrCategory.COMPARISON }

    // =====================================================================
    // MEMORY — alloca, load, store, GEP, memcpy, stack, lifetime
    // =====================================================================

    /** Stack allocation. Allocates [allocType] (or [numElements] of them) on the stack frame.
     *  Memory is automatically freed when the function returns. Result is a pointer. */
    data class Alloca(val dest: InstructionRef, val allocType: Type, val numElements: Value? = null, val align: Int? = null) : Instruction { override val result get() = dest; override val category get() = IrCategory.MEMORY }
    /** Load a value of [loadType] from a pointer. [volatile] prevents reordering/elimination.
     *  [ordering] makes the load atomic (null = non-atomic). */
    data class Load(val dest: InstructionRef, val ptr: Value, val loadType: Type, val align: Int? = null, val volatile: Boolean = false, val ordering: AtomicOrdering? = null) : Instruction { override val result get() = dest; override val category get() = IrCategory.MEMORY }
    /** Store [value] to the address in [ptr]. No result (void). */
    data class Store(val value: Value, val ptr: Value, val align: Int? = null, val volatile: Boolean = false, val ordering: AtomicOrdering? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }
    /** GetElementPtr: computes a pointer offset without accessing memory.
     *  First index offsets from base pointer; subsequent indices drill into aggregates.
     *  [inBounds] = result is poison if pointer leaves the allocated object. */
    data class GetElementPtr(val dest: InstructionRef, val baseType: Type, val ptr: Value, val indices: List<Value>, val inBounds: Boolean = true) : Instruction { override val result get() = dest; override val category get() = IrCategory.MEMORY }
    /** Copy [len] bytes from [src] to [dst]. Source and destination must not overlap (use [MemMove] for that). */
    data class MemCpy(val dst: Value, val src: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }
    /** Fill [len] bytes at [dst] with byte [value]. */
    data class MemSet(val dst: Value, val value: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }
    /** Copy [len] bytes from [src] to [dst], correctly handling overlapping regions. */
    data class MemMove(val dst: Value, val src: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }
    /** Cache prefetch hint. [rw]: 0=read, 1=write. [locality]: 0(none)–3(high). [cacheType]: 0=icache, 1=dcache. */
    data class Prefetch(val address: Value, val rw: Int, val locality: Int, val cacheType: Int) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }

    /** Save the current stack pointer. Use with [StackRestore] to undo dynamic allocas. */
    data class StackSave(val dest: InstructionRef) : Instruction { override val result get() = dest; override val category get() = IrCategory.MEMORY }
    /** Restore a previously saved stack pointer, freeing any allocas made since the save. */
    data class StackRestore(val ptr: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }

    /** Mark the start of a stack variable's lifetime. Enables stack slot reuse optimization. */
    data class LifetimeStart(val ptr: Value, val size: Long) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }
    /** Mark the end of a stack variable's lifetime. */
    data class LifetimeEnd(val ptr: Value, val size: Long) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.MEMORY }

    // =====================================================================
    // ATOMIC — fence, compare-and-swap, read-modify-write
    // =====================================================================

    /** Memory fence / barrier. Orders memory operations according to [ordering]. */
    data class Fence(val ordering: AtomicOrdering, val syncScope: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.ATOMIC }
    /** Compare-and-exchange: atomically reads `*ptr`, stores [new] if value equals [cmp].
     *  Result is `{old_value, i1_success}`. [weak] allows spurious failures. */
    data class CmpXchg(val dest: InstructionRef, val ptr: Value, val cmp: Value, val new: Value, val successOrdering: AtomicOrdering, val failureOrdering: AtomicOrdering, val weak: Boolean = false, val volatile: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ATOMIC }
    /** Atomic read-modify-write: atomically reads `*ptr`, applies [op] with [value], stores result.
     *  Returns the old value. See [AtomicRMWOp] for operations (ADD, XCHG, AND, etc.). */
    data class AtomicRMW(val dest: InstructionRef, val op: AtomicRMWOp, val ptr: Value, val value: Value, val ordering: AtomicOrdering, val volatile: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.ATOMIC }

    // =====================================================================
    // CONVERSION — type width changes, float/int casts, pointer reinterpretation
    // =====================================================================

    /** Integer truncation: drops high bits to narrow an integer. e.g., i32 → i16. */
    data class IntTrunc(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Zero-extend: widens an integer by filling high bits with zero. e.g., i16 → i32. */
    data class ZExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Sign-extend: widens an integer by filling high bits with the sign bit. */
    data class SExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Float truncation: narrows a float (e.g., f64 → f32), rounding to fit. */
    data class FPTrunc(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Float extension: widens a float (e.g., f32 → f64) with no precision loss. */
    data class FPExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Float to unsigned integer, truncating toward zero. */
    data class FPToUI(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Float to signed integer, truncating toward zero. */
    data class FPToSI(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Unsigned integer to float. */
    data class UIToFP(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Signed integer to float. */
    data class SIToFP(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Pointer to integer. The integer must be wide enough to hold the pointer value. */
    data class PtrToInt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Integer to pointer. */
    data class IntToPtr(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Reinterpret bits as a different type of the same size. No bits are changed. */
    data class BitCast(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }
    /** Cast a pointer between address spaces (e.g., GPU global → shared memory). */
    data class AddrSpaceCast(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CONVERSION }

    // =====================================================================
    // TERMINATOR — branch, return, switch, trap
    // =====================================================================

    /** Return from the function. [value] is null for void functions. */
    data class Ret(val value: Value?) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Unconditional branch to [target] block. */
    data class Br(val target: String) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Conditional branch: jumps to [trueTarget] if [condition] (i1) is true, else [falseTarget]. */
    data class CondBr(val condition: Value, val trueTarget: String, val falseTarget: String,
        val trueWeight: Long = 0, val falseWeight: Long = 0) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Multi-way branch based on [value]. Jumps to the matching case or [defaultTarget]. */
    data class Switch(val value: Value, val defaultTarget: String, val cases: List<Pair<Constant, String>>) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Branch to a computed address. [targets] is the set of possible destinations (for analysis). */
    data class IndirectBr(val address: Value, val targets: List<String>) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Marks code as unreachable. UB if execution reaches this point. Used after noreturn calls. */
    data class Unreachable(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Immediately abort execution (e.g., `__builtin_trap`). */
    data class Trap(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }
    /** Debugger breakpoint (e.g., `int3` on x86). Execution may continue after. */
    data class DebugTrap(val successor: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.TERMINATOR }

    // =====================================================================
    // CALL — direct/indirect calls, invoke, varargs
    // =====================================================================

    /** Direct or indirect function call. Returns null result for void calls.
     *  [tailCall]: TAIL=hint, MUSTTAIL=required, NOTAIL=prohibited. */
    data class Call(
        val dest: InstructionRef?,
        val function: Value,
        val args: List<Value>,
        val returnType: Type,
        val callingConv: CallingConvention = CallingConvention.C,
        val tailCall: TailCallKind = TailCallKind.NONE,
        val attributes: Set<FnAttribute> = emptySet(),
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.CALL }

    /** Call with exception handling. If callee throws, control goes to [unwindDest];
     *  otherwise to [normalDest]. This is a terminator instruction. */
    data class Invoke(
        val dest: InstructionRef?,
        val function: Value,
        val args: List<Value>,
        val returnType: Type,
        val normalDest: String,
        val unwindDest: String,
        val callingConv: CallingConvention = CallingConvention.C,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.CALL }

    /** Call with multiple successor blocks. Used for inline assembly that may branch to
     *  different destinations. Terminator. */
    data class CallBr(
        val dest: InstructionRef?,
        val function: Value,
        val args: List<Value>,
        val returnType: Type,
        val fallthrough: String,
        val indirectDests: List<String>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.CALL }

    /** Initialize a variadic argument list. Must be called before [VAArg]. */
    data class VAStart(val argList: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.CALL }
    /** Clean up a variadic argument list. Must be called before function returns. */
    data class VAEnd(val argList: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.CALL }
    /** Copy a variadic argument list from [src] to [dst]. */
    data class VACopy(val dst: Value, val src: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.CALL }
    /** Retrieve the next variadic argument of [argType] from the argument list. */
    data class VAArg(val dest: InstructionRef, val argList: Value, val argType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.CALL }

    // =====================================================================
    // SSA — phi, select, freeze
    // =====================================================================

    /** Phi node: merges values at a control flow join point. Each entry in [incoming] is
     *  a (value, predecessor_block) pair. Must appear at the start of a block. */
    data class Phi(val dest: InstructionRef, val incoming: List<Pair<Value, String>>) : Instruction { override val result get() = dest; override val category get() = IrCategory.SSA }
    /** Conditional select: returns [trueValue] if [condition] (i1) is true, else [falseValue].
     *  Like a ternary operator. Not a terminator — both values are computed. */
    data class Select(val dest: InstructionRef, val condition: Value, val trueValue: Value, val falseValue: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.SSA }
    /** Freeze: converts undef/poison to an arbitrary but fixed value. Prevents poison propagation. */
    data class Freeze(val dest: InstructionRef, val value: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.SSA }

    // =====================================================================
    // VECTOR — SIMD extract, insert, shuffle, splat, reduce
    // =====================================================================

    /** Extract a scalar element from a SIMD vector at the given [index]. */
    data class ExtractElement(val dest: InstructionRef, val vector: Value, val index: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.VECTOR }
    /** Insert a scalar [element] into a SIMD vector at the given [index]. */
    data class InsertElement(val dest: InstructionRef, val vector: Value, val element: Value, val index: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.VECTOR }
    /** Shuffle (rearrange) lanes from two vectors according to [mask].
     *  `mask[i]` < N selects from v1, >= N selects from v2. Result has `mask.size` lanes. */
    data class ShuffleVector(val dest: InstructionRef, val v1: Value, val v2: Value, val mask: List<Int>) : Instruction { override val result get() = dest; override val category get() = IrCategory.VECTOR }
    /** Broadcast a single [scalar] to all lanes of [vectorType]. */
    data class Splat(val dest: InstructionRef, val scalar: Value, val vectorType: Type.Vector) : Instruction { override val result get() = dest; override val category get() = IrCategory.VECTOR }

    /** Reduce a vector to a single scalar using [op]. See [VectorReduceOp]. */
    data class VectorReduce(val dest: InstructionRef, val op: VectorReduceOp, val vector: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.VECTOR }

    // =====================================================================
    // AGGREGATE — struct and array field access
    // =====================================================================

    /** Extract a field from a struct or array by [indices] (one per nesting level). */
    data class ExtractValue(val dest: InstructionRef, val aggregate: Value, val indices: List<Int>) : Instruction { override val result get() = dest; override val category get() = IrCategory.AGGREGATE }
    /** Insert [element] into a struct or array at [indices], returning the updated aggregate. */
    data class InsertValue(val dest: InstructionRef, val aggregate: Value, val element: Value, val indices: List<Int>) : Instruction { override val result get() = dest; override val category get() = IrCategory.AGGREGATE }

    // =====================================================================
    // EXCEPTION — native EH, SEH, managed throw/catch
    // =====================================================================

    /** Landing pad: receives control when an exception is thrown. Specifies which exception
     *  types to catch ([Catch]) or filter ([Filter]). [cleanup] = also runs for cleanup. */
    data class LandingPad(val dest: InstructionRef, val resultType: Type, val clauses: List<LandingPadClause>, val cleanup: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.EXCEPTION }
    /** Resume unwinding after a landing pad without handling the exception. */
    data class Resume(val value: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.EXCEPTION }
    /** Windows SEH: dispatch to one of several [handlers] based on exception type. */
    data class CatchSwitch(val dest: InstructionRef, val parentPad: Value?, val handlers: List<String>, val unwindDest: String?) : Instruction { override val result get() = dest; override val category get() = IrCategory.EXCEPTION }
    /** Windows SEH: begin a catch handler funclet. */
    data class CatchPad(val dest: InstructionRef, val catchSwitch: Value, val args: List<Value>) : Instruction { override val result get() = dest; override val category get() = IrCategory.EXCEPTION }
    /** Windows SEH: begin a cleanup handler (runs destructors, etc.). */
    data class CleanupPad(val dest: InstructionRef, val parentPad: Value?, val args: List<Value>) : Instruction { override val result get() = dest; override val category get() = IrCategory.EXCEPTION }
    /** Windows SEH: return from a catch handler to normal code at [dest]. */
    data class CatchRet(val catchPad: Value, val dest: String) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.EXCEPTION }
    /** Windows SEH: return from a cleanup handler. [unwindDest]=null means resume unwinding. */
    data class CleanupRet(val cleanupPad: Value, val unwindDest: String?) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.EXCEPTION }

    /** Throw an exception. Terminator: transfers control to the nearest handler. */
    data class Throw(val exception: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }
    /** Try/catch/finally region. [catches] maps exception types to handler blocks. */
    data class TryCatchRegion(
        val tryBlock: String,
        val catches: List<CatchHandler>,
        val finallyBlock: String? = null,
    ) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    // =====================================================================
    // OBJECT — allocation, fields, dispatch, type ops, arrays, closures, ADTs
    // =====================================================================

    /** Allocate a new object of the given class. [typeArgs] for generic instantiation. */
    data class NewObject(val dest: InstructionRef, val className: String, val typeArgs: List<Type> = emptyList()) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Allocate a managed array of [elementType] with [size] elements. */
    data class NewArray(val dest: InstructionRef, val elementType: Type, val size: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Allocate a multi-dimensional managed array. */
    data class NewMultiArray(val dest: InstructionRef, val elementType: Type, val dimensions: List<Value>) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Read instance field [fieldName] from [obj]. */
    data class GetField(val dest: InstructionRef, val obj: Value, val className: String, val fieldName: String, val fieldType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Write [value] to instance field [fieldName] of [obj]. */
    data class PutField(val obj: Value, val className: String, val fieldName: String, val fieldType: Type, val value: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }
    /** Read static field [fieldName] from [className]. */
    data class GetStatic(val dest: InstructionRef, val className: String, val fieldName: String, val fieldType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Write [value] to static field [fieldName] of [className]. */
    data class PutStatic(val className: String, val fieldName: String, val fieldType: Type, val value: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    /** Virtual method call: dispatches through the vtable based on runtime type of [obj]. */
    data class VirtualCall(
        val dest: InstructionRef?, val obj: Value, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Interface method call: dispatches via interface method table. */
    data class InterfaceCall(
        val dest: InstructionRef?, val obj: Value, val interfaceName: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Direct call bypassing vtable: used for super calls, private methods, constructors. */
    data class SpecialCall(
        val dest: InstructionRef?, val obj: Value, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Static method call (no receiver object). */
    data class StaticCall(
        val dest: InstructionRef?, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** invokedynamic-style dispatch: uses a [bootstrapMethod] to resolve the call site at runtime.
     *  Used for lambdas, string concatenation optimization, etc. */
    data class DynamicCall(
        val dest: InstructionRef?, val bootstrapMethod: BootstrapMethod,
        val name: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Constructor invocation: initializes [obj] in place. No return value. */
    data class ConstructorCall(
        val obj: Value, val className: String,
        val constructorType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    /** Runtime type check: result is i1 (true if [obj] is an instance of [checkType]). */
    data class InstanceOf(val dest: InstructionRef, val obj: Value, val checkType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Type cast: throws at runtime if [obj] is not an instance of [castType]. */
    data class CheckCast(val dest: InstructionRef, val obj: Value, val castType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Get runtime type identifier of [obj] (for type-switch dispatch). Result is i32. */
    data class TypeId(val dest: InstructionRef, val obj: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Read element at [index] from managed [array]. */
    data class ArrayGet(val dest: InstructionRef, val array: Value, val index: Value, val elementType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Write [value] to managed [array] at [index]. */
    data class ArraySet(val array: Value, val index: Value, val value: Value, val elementType: Type) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }
    /** Get the length of a managed [array]. Result is i32. */
    data class ArrayLength(val dest: InstructionRef, val array: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Acquire the object's monitor lock (e.g., `synchronized` block entry). */
    data class MonitorEnter(val obj: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }
    /** Release the object's monitor lock. */
    data class MonitorExit(val obj: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    /** Wrap a primitive [value] into a heap object of [boxType] (e.g., int → Integer). */
    data class Box(val dest: InstructionRef, val value: Value, val boxType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Extract the primitive value from a boxed object [obj]. */
    data class Unbox(val dest: InstructionRef, val obj: Value, val unboxType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Create a closure: bundles [function] with [captures] (captured variables from enclosing scope). */
    data class ClosureCreate(
        val dest: InstructionRef, val function: Value,
        val captures: List<Value>, val closureType: Type.Function,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Invoke a closure with [args]. */
    data class ClosureInvoke(
        val dest: InstructionRef?, val closure: Value,
        val args: List<Value>, val returnType: Type,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Construct a tagged union value for the given [variantName] with [fields]. */
    data class ConstructVariant(val dest: InstructionRef, val unionType: Type.TaggedUnion, val variantName: String, val fields: List<Value>) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Extract the discriminant (tag) from a tagged union. */
    data class GetTag(val dest: InstructionRef, val union: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Extract a field from a specific variant of a tagged union. */
    data class GetVariantField(val dest: InstructionRef, val union: Value, val variantName: String, val fieldIndex: Int) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Pattern match: branch based on the tag of a tagged union. Terminator. */
    data class TagSwitch(val union: Value, val cases: List<Pair<String, String>>, val defaultTarget: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    /** Receive the caught exception object in a catch handler block. [exceptionType] is the declared catch type. */
    data class CatchValue(val dest: InstructionRef, val exceptionType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }

    /** Create a weak reference to [obj]. The GC may collect the referent at any time. */
    data class MakeWeakRef(val dest: InstructionRef, val obj: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Read the referent of a weak reference. Returns null if the object has been collected. */
    data class ReadWeakRef(val dest: InstructionRef, val weakRef: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.OBJECT }
    /** Clear a weak reference, releasing its association with the referent. */
    data class ClearWeakRef(val weakRef: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.OBJECT }

    // =====================================================================
    // RUNTIME — GC, barriers, refcounting, coroutines
    // =====================================================================

    /** Allocate GC-managed memory for [allocType]. */
    data class GCAlloc(val dest: InstructionRef, val allocType: Type, val size: Value? = null) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }
    /** GC safepoint: the thread may be suspended here for garbage collection. */
    data class GCSafepoint(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Register a stack pointer as a GC root so the collector can trace it. */
    data class GCRoot(val ptr: Value, val metadata: Value?) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }

    /**
     * Pin a GC-managed reference so the GC will not move the object.
     * Returns a [Type.PinnedRef] that can be converted to a raw native pointer.
     * The object remains pinned until [Unpin] is called on the result.
     * Inspired by C++/CLI `pin_ptr<T>`.
     */
    data class Pin(val dest: InstructionRef, val ref: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.INTEROP }

    /**
     * Unpin a previously pinned reference, allowing the GC to move the object again.
     * After unpinning, any raw pointers derived from the pinned ref are invalid.
     */
    data class Unpin(val ref: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.INTEROP }

    /**
     * Get an interior pointer into a GC-managed object (a field or array element).
     * The GC tracks interior refs and updates them when the containing object moves.
     * [ref] must be a Reference or TrackedRef. [index] is a field index or array offset.
     * Result type is [Type.InteriorRef].
     */
    data class InteriorPtr(val dest: InstructionRef, val ref: Value, val index: Value, val pointeeType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.INTEROP }

    /**
     * GC write barrier: notifies the GC that a reference field is being written.
     * Required for generational and concurrent collectors (e.g., card marking,
     * snapshot-at-the-beginning). [obj] is the object being written to, [fieldIndex]
     * identifies the field, and [value] is the new reference being stored.
     */
    data class WriteBarrier(val obj: Value, val fieldIndex: Value, val value: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }

    /**
     * GC read barrier: interposes on reference loads for concurrent/relocating GCs
     * (e.g., ZGC load barriers, Shenandoah Brooks pointers). [ref] is the reference
     * being read; result is the updated (potentially relocated) reference.
     */
    data class ReadBarrier(val dest: InstructionRef, val ref: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }

    /**
     * Call across a managed/native boundary with automatic transition thunks.
     * Handles GC state save/restore, argument pinning, exception marshaling,
     * and safepoint registration. [direction] specifies the transition direction.
     */
    data class ManagedCall(
        val dest: InstructionRef?,
        val function: Value,
        val args: List<Value>,
        val returnType: Type,
        val direction: ManagedCallDirection,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.INTEROP }

    /** Increment the reference count of [obj]. */
    data class RefRetain(val obj: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Decrement the reference count of [obj]. May deallocate when count reaches zero. */
    data class RefRelease(val obj: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Get the current reference count. Result is i32. */
    data class RefCount(val dest: InstructionRef, val obj: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }

    /** Initialize a coroutine frame. Returns a handle. */
    data class CoroBegin(val dest: InstructionRef, val id: Value, val mem: Value) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }
    /** Mark coroutine completion. [unwind] = true if ending due to exception. */
    data class CoroEnd(val handle: Value, val unwind: Boolean = false) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Suspend the coroutine. Returns i8: 0=resumed normally, 1=should cleanup. */
    data class CoroSuspend(val dest: InstructionRef, val save: Value?, val isFinal: Boolean = false) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }
    /** Resume a suspended coroutine. */
    data class CoroResume(val handle: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Destroy a coroutine frame, freeing its memory. */
    data class CoroDestroy(val handle: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.RUNTIME }
    /** Get the size needed for a coroutine frame. Result is i64. */
    data class CoroSize(val dest: InstructionRef) : Instruction { override val result get() = dest; override val category get() = IrCategory.RUNTIME }

    // =====================================================================
    // DEBUG — source locations, variable tracking, optimizer hints
    // =====================================================================

    /** Attach source location info (line, column, scope) to subsequent instructions. */
    data class DebugLoc(val line: Int, val col: Int, val scope: String, val inlinedAt: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.DEBUG }
    /** Associate an SSA [value] with a source-level variable name (for debugger display). */
    data class DebugValue(val variable: String, val value: Value, val expression: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.DEBUG }
    /** Associate an address (alloca) with a source-level variable (for debugger display). */
    data class DebugDeclare(val variable: String, val address: Value, val expression: String? = null) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.DEBUG }

    /** Tell the optimizer to assume [condition] is true. UB if it's actually false. */
    data class Assume(val condition: Value) : Instruction { override val result: Value? get() = null; override val category get() = IrCategory.DEBUG }
    /** Branch prediction hint: tells the optimizer that [value] is expected to equal [expected]. */
    data class Expect(val dest: InstructionRef, val value: Value, val expected: Constant) : Instruction { override val result get() = dest; override val category get() = IrCategory.DEBUG }

    // =====================================================================
    // INTRINSIC — target-specific operations and inline assembly
    // =====================================================================

    /** Generic intrinsic for target-specific operations not covered by the instruction set. */
    data class Intrinsic(val dest: InstructionRef?, val name: String, val args: List<Value>, val returnType: Type) : Instruction { override val result get() = dest; override val category get() = IrCategory.INTRINSIC }

    /** Inline assembly escape hatch. [constraints] follows GCC constraint syntax.
     *  [dialect]: ATT (default, gas-style) or INTEL. */
    data class InlineAsm(
        val dest: InstructionRef?,
        val assembly: String,
        val constraints: String,
        val sideEffects: Boolean = true,
        val alignStack: Boolean = false,
        val dialect: AsmDialect = AsmDialect.ATT,
        val args: List<Value> = emptyList(),
        val returnType: Type = Type.Void,
    ) : Instruction { override val result get() = dest; override val category get() = IrCategory.INTRINSIC }
}

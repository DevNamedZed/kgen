package org.kgen.ir

/**
 * Sealed interface for all IR instructions. Every instruction has an optional [result]
 * value — null for void instructions (stores, branches, etc.).
 *
 * Instructions are split into two tiers:
 * - **Low-level**: consumed directly by native backends (x86-64, ARM64, RISC-V)
 * - **High-level**: consumed by managed backends (JVM, WASM); lowered to low-level for native
 */
sealed interface Instruction {
    val result: Value?

    // -----------------------------------------------------------------------
    // LOW-LEVEL OPERATIONS
    // -----------------------------------------------------------------------

    // --- Integer arithmetic ---

    /** Integer addition: `lhs + rhs`. Both operands must have the same integer type.
     *  [nuw] = poison on unsigned overflow, [nsw] = poison on signed overflow. */
    data class Add(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest }

    /** Integer subtraction: `lhs - rhs`. Same type constraints and flags as [Add]. */
    data class Sub(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest }

    /** Integer multiplication: `lhs * rhs`. Same type constraints and flags as [Add]. */
    data class Mul(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest }

    /** Unsigned integer division: `lhs / rhs` (truncated toward zero).
     *  Division by zero is undefined behavior. [exact] = poison if remainder is non-zero. */
    data class UDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest }

    /** Signed integer division: `lhs / rhs` (truncated toward zero).
     *  Division by zero and INT_MIN / -1 are undefined behavior. */
    data class SDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest }

    /** Unsigned integer remainder: `lhs % rhs`. Remainder by zero is UB. */
    data class URem(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }

    /** Signed integer remainder: `lhs % rhs`. Sign of result matches sign of dividend. */
    data class SRem(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }

    /** Integer negation: `-operand`. Equivalent to `sub 0, operand`. */
    data class Neg(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }

    // --- Overflow-checked arithmetic ---
    // These return a struct {result, i1_overflow_flag}.

    /** Signed add with overflow detection. Result: `{sum, did_overflow}`. */
    data class SAddOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned add with overflow detection. Result: `{sum, did_overflow}`. */
    data class UAddOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Signed subtract with overflow detection. Result: `{difference, did_overflow}`. */
    data class SSubOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned subtract with overflow detection. */
    data class USubOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Signed multiply with overflow detection. */
    data class SMulOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned multiply with overflow detection. */
    data class UMulOverflow(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }

    // --- Saturating arithmetic ---
    // Clamp result to representable range instead of wrapping.

    /** Signed saturating add. Clamps to [INT_MIN, INT_MAX] on overflow. */
    data class SAddSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned saturating add. Clamps to [0, UINT_MAX] on overflow. */
    data class UAddSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Signed saturating subtract. */
    data class SSubSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned saturating subtract. */
    data class USubSat(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }

    // --- Min / max ---

    /** Signed integer minimum of two values. */
    data class SMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Signed integer maximum of two values. */
    data class SMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned integer minimum of two values. */
    data class UMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Unsigned integer maximum of two values. */
    data class UMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }

    /** Integer absolute value. [isIntMin] = result is poison when operand is INT_MIN
     *  (allows more efficient codegen on targets without a single abs instruction). */
    data class Abs(val dest: InstructionRef, val operand: Value, val isIntMin: Boolean = false) : Instruction { override val result get() = dest }

    // --- Float arithmetic ---

    /** Float addition: `lhs + rhs`. IEEE 754 semantics unless [fastMath] flags relax them. */
    data class FAdd(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float subtraction: `lhs - rhs`. */
    data class FSub(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float multiplication: `lhs * rhs`. */
    data class FMul(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float division: `lhs / rhs`. */
    data class FDiv(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float remainder (IEEE 754): `lhs % rhs`. Sign of result matches dividend. */
    data class FRem(val dest: InstructionRef, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float negation: flips the sign bit. `fneg(-0.0) = +0.0`. */
    data class FNeg(val dest: InstructionRef, val operand: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }
    /** Float absolute value: clears the sign bit. */
    data class FAbs(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Fused multiply-add: `a * b + c` with a single rounding (more accurate than separate mul+add). */
    data class FMA(val dest: InstructionRef, val a: Value, val b: Value, val c: Value) : Instruction { override val result get() = dest }
    /** IEEE 754 minimum. Returns the non-NaN operand if one is NaN. `fmin(-0, +0) = -0`. */
    data class FMin(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** IEEE 754 maximum. Returns the non-NaN operand if one is NaN. `fmax(-0, +0) = +0`. */
    data class FMax(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Square root. `sqrt(negative) = NaN`, `sqrt(+inf) = +inf`, `sqrt(-0) = -0`. */
    data class Sqrt(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Round toward positive infinity (ceiling). */
    data class Ceil(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Round toward negative infinity (floor). */
    data class Floor(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Round to nearest integer, ties away from zero. */
    data class Round(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Round toward zero (truncate fractional part). Not to be confused with [IntTrunc]. */
    data class Trunc(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Copy sign bit from [sign] to [magnitude], preserving magnitude's absolute value. */
    data class CopySign(val dest: InstructionRef, val magnitude: Value, val sign: Value) : Instruction { override val result get() = dest }

    // --- Bitwise operations ---

    /** Bitwise AND: each bit is 1 only if both corresponding bits are 1. */
    data class And(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Bitwise OR: each bit is 1 if either corresponding bit is 1. */
    data class Or(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Bitwise XOR: each bit is 1 if the corresponding bits differ. */
    data class Xor(val dest: InstructionRef, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Bitwise NOT (one's complement): flips every bit. Equivalent to `xor operand, -1`. */
    data class Not(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Shift left: `lhs << rhs`, filling vacated bits with zero. Shift by >= bitwidth is UB. */
    data class Shl(val dest: InstructionRef, val lhs: Value, val rhs: Value, val nuw: Boolean = false, val nsw: Boolean = false) : Instruction { override val result get() = dest }
    /** Logical shift right: `lhs >>> rhs`, filling with zero (unsigned). Shift by >= bitwidth is UB. */
    data class LShr(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest }
    /** Arithmetic shift right: `lhs >> rhs`, filling with sign bit (signed). Shift by >= bitwidth is UB. */
    data class AShr(val dest: InstructionRef, val lhs: Value, val rhs: Value, val exact: Boolean = false) : Instruction { override val result get() = dest }
    /** Circular rotate left: bits shifted out the high end re-enter at the low end. */
    data class RotateLeft(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest }
    /** Circular rotate right: bits shifted out the low end re-enter at the high end. */
    data class RotateRight(val dest: InstructionRef, val value: Value, val amount: Value) : Instruction { override val result get() = dest }

    // --- Bit manipulation ---

    /** Count Leading Zeros: number of 0-bits before the first 1-bit from MSB.
     *  Returns bitwidth when operand is 0 (unless [isZeroPoison] is set). */
    data class Ctlz(val dest: InstructionRef, val operand: Value, val isZeroPoison: Boolean = false) : Instruction { override val result get() = dest }
    /** Count Trailing Zeros: number of 0-bits after the last 1-bit from LSB.
     *  Returns bitwidth when operand is 0 (unless [isZeroPoison] is set). */
    data class Cttz(val dest: InstructionRef, val operand: Value, val isZeroPoison: Boolean = false) : Instruction { override val result get() = dest }
    /** Population Count (Hamming weight): counts the number of 1-bits in the operand.
     *  Example: `ctpop(0b10110) = 3`. */
    data class Ctpop(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Byte Swap: reverses byte order for endianness conversion.
     *  Example: `bswap(0x12345678) = 0x78563412`. Operand must be a multiple of 16 bits. */
    data class BSwap(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }
    /** Bit Reverse: reverses the order of all bits (not bytes).
     *  Example for i8: `bitreverse(0b11000001) = 0b10000011`. */
    data class BitReverse(val dest: InstructionRef, val operand: Value) : Instruction { override val result get() = dest }

    // --- Comparison ---

    /** Integer comparison. Both operands must have the same integer type. Result is always i1.
     *  See [ICmpPredicate] for available predicates (EQ, NE, signed/unsigned comparisons). */
    data class ICmp(val dest: InstructionRef, val predicate: ICmpPredicate, val lhs: Value, val rhs: Value) : Instruction { override val result get() = dest }
    /** Float comparison. Result is always i1. See [FCmpPredicate] for predicates.
     *  Ordered predicates return false if either operand is NaN.
     *  Unordered predicates return true if either operand is NaN. */
    data class FCmp(val dest: InstructionRef, val predicate: FCmpPredicate, val lhs: Value, val rhs: Value, val fastMath: FastMathFlags = FastMathFlags.NONE) : Instruction { override val result get() = dest }

    // --- Memory ---

    /** Stack allocation. Allocates [allocType] (or [numElements] of them) on the stack frame.
     *  Memory is automatically freed when the function returns. Result is a pointer. */
    data class Alloca(val dest: InstructionRef, val allocType: Type, val numElements: Value? = null, val align: Int? = null) : Instruction { override val result get() = dest }
    /** Load a value of [loadType] from a pointer. [volatile] prevents reordering/elimination.
     *  [ordering] makes the load atomic (null = non-atomic). */
    data class Load(val dest: InstructionRef, val ptr: Value, val loadType: Type, val align: Int? = null, val volatile: Boolean = false, val ordering: AtomicOrdering? = null) : Instruction { override val result get() = dest }
    /** Store [value] to the address in [ptr]. No result (void). */
    data class Store(val value: Value, val ptr: Value, val align: Int? = null, val volatile: Boolean = false, val ordering: AtomicOrdering? = null) : Instruction { override val result: Value? get() = null }
    /** GetElementPtr: computes a pointer offset without accessing memory.
     *  First index offsets from base pointer; subsequent indices drill into aggregates.
     *  [inBounds] = result is poison if pointer leaves the allocated object. */
    data class GetElementPtr(val dest: InstructionRef, val baseType: Type, val ptr: Value, val indices: List<Value>, val inBounds: Boolean = true) : Instruction { override val result get() = dest }
    /** Memory fence / barrier. Orders memory operations according to [ordering]. */
    data class Fence(val ordering: AtomicOrdering, val syncScope: String? = null) : Instruction { override val result: Value? get() = null }
    /** Compare-and-exchange: atomically reads `*ptr`, stores [new] if value equals [cmp].
     *  Result is `{old_value, i1_success}`. [weak] allows spurious failures. */
    data class CmpXchg(val dest: InstructionRef, val ptr: Value, val cmp: Value, val new: Value, val successOrdering: AtomicOrdering, val failureOrdering: AtomicOrdering, val weak: Boolean = false, val volatile: Boolean = false) : Instruction { override val result get() = dest }
    /** Atomic read-modify-write: atomically reads `*ptr`, applies [op] with [value], stores result.
     *  Returns the old value. See [AtomicRMWOp] for operations (ADD, XCHG, AND, etc.). */
    data class AtomicRMW(val dest: InstructionRef, val op: AtomicRMWOp, val ptr: Value, val value: Value, val ordering: AtomicOrdering, val volatile: Boolean = false) : Instruction { override val result get() = dest }
    /** Copy [len] bytes from [src] to [dst]. Source and destination must not overlap (use [MemMove] for that). */
    data class MemCpy(val dst: Value, val src: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null }
    /** Fill [len] bytes at [dst] with byte [value]. */
    data class MemSet(val dst: Value, val value: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null }
    /** Copy [len] bytes from [src] to [dst], correctly handling overlapping regions. */
    data class MemMove(val dst: Value, val src: Value, val len: Value, val volatile: Boolean = false) : Instruction { override val result: Value? get() = null }
    /** Cache prefetch hint. [rw]: 0=read, 1=write. [locality]: 0(none)–3(high). [cacheType]: 0=icache, 1=dcache. */
    data class Prefetch(val address: Value, val rw: Int, val locality: Int, val cacheType: Int) : Instruction { override val result: Value? get() = null }

    // --- Stack save/restore ---

    /** Save the current stack pointer. Use with [StackRestore] to undo dynamic allocas. */
    data class StackSave(val dest: InstructionRef) : Instruction { override val result get() = dest }
    /** Restore a previously saved stack pointer, freeing any allocas made since the save. */
    data class StackRestore(val ptr: Value) : Instruction { override val result: Value? get() = null }

    // --- Lifetime markers ---

    /** Mark the start of a stack variable's lifetime. Enables stack slot reuse optimization. */
    data class LifetimeStart(val ptr: Value, val size: Long) : Instruction { override val result: Value? get() = null }
    /** Mark the end of a stack variable's lifetime. */
    data class LifetimeEnd(val ptr: Value, val size: Long) : Instruction { override val result: Value? get() = null }

    // --- Conversions ---

    /** Integer truncation: drops high bits to narrow an integer. e.g., i32 → i16. */
    data class IntTrunc(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Zero-extend: widens an integer by filling high bits with zero. e.g., i16 → i32. */
    data class ZExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Sign-extend: widens an integer by filling high bits with the sign bit. */
    data class SExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Float truncation: narrows a float (e.g., f64 → f32), rounding to fit. */
    data class FPTrunc(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Float extension: widens a float (e.g., f32 → f64) with no precision loss. */
    data class FPExt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Float to unsigned integer, truncating toward zero. */
    data class FPToUI(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Float to signed integer, truncating toward zero. */
    data class FPToSI(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Unsigned integer to float. */
    data class UIToFP(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Signed integer to float. */
    data class SIToFP(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Pointer to integer. The integer must be wide enough to hold the pointer value. */
    data class PtrToInt(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Integer to pointer. */
    data class IntToPtr(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Reinterpret bits as a different type of the same size. No bits are changed. */
    data class BitCast(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }
    /** Cast a pointer between address spaces (e.g., GPU global → shared memory). */
    data class AddrSpaceCast(val dest: InstructionRef, val value: Value, val toType: Type) : Instruction { override val result get() = dest }

    // --- Control flow (all are terminators — must be last instruction in a block) ---

    /** Return from the function. [value] is null for void functions. */
    data class Ret(val value: Value?) : Instruction { override val result: Value? get() = null }
    /** Unconditional branch to [target] block. */
    data class Br(val target: String) : Instruction { override val result: Value? get() = null }
    /** Conditional branch: jumps to [trueTarget] if [condition] (i1) is true, else [falseTarget]. */
    data class CondBr(val condition: Value, val trueTarget: String, val falseTarget: String) : Instruction { override val result: Value? get() = null }
    /** Multi-way branch based on [value]. Jumps to the matching case or [defaultTarget]. */
    data class Switch(val value: Value, val defaultTarget: String, val cases: List<Pair<Constant, String>>) : Instruction { override val result: Value? get() = null }
    /** Branch to a computed address. [targets] is the set of possible destinations (for analysis). */
    data class IndirectBr(val address: Value, val targets: List<String>) : Instruction { override val result: Value? get() = null }
    /** Marks code as unreachable. UB if execution reaches this point. Used after noreturn calls. */
    data class Unreachable(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null }
    /** Immediately abort execution (e.g., `__builtin_trap`). */
    data class Trap(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null }
    /** Debugger breakpoint (e.g., `int3` on x86). Execution may continue after. */
    data class DebugTrap(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null }

    // --- Calls ---

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
    ) : Instruction { override val result get() = dest }

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
    ) : Instruction { override val result get() = dest }

    /** Call with multiple successor blocks. Used for inline assembly that may branch to
     *  different destinations. Terminator. */
    data class CallBr(
        val dest: InstructionRef?,
        val function: Value,
        val args: List<Value>,
        val returnType: Type,
        val fallthrough: String,
        val indirectDests: List<String>,
    ) : Instruction { override val result get() = dest }

    // --- Varargs ---

    /** Initialize a variadic argument list. Must be called before [VAArg]. */
    data class VAStart(val argList: Value) : Instruction { override val result: Value? get() = null }
    /** Clean up a variadic argument list. Must be called before function returns. */
    data class VAEnd(val argList: Value) : Instruction { override val result: Value? get() = null }
    /** Copy a variadic argument list from [src] to [dst]. */
    data class VACopy(val dst: Value, val src: Value) : Instruction { override val result: Value? get() = null }
    /** Retrieve the next variadic argument of [argType] from the argument list. */
    data class VAArg(val dest: InstructionRef, val argList: Value, val argType: Type) : Instruction { override val result get() = dest }

    // --- Exception handling (native / C++ / SEH style) ---

    /** Landing pad: receives control when an exception is thrown. Specifies which exception
     *  types to catch ([Catch]) or filter ([Filter]). [cleanup] = also runs for cleanup. */
    data class LandingPad(val dest: InstructionRef, val resultType: Type, val clauses: List<LandingPadClause>, val cleanup: Boolean = false) : Instruction { override val result get() = dest }
    /** Resume unwinding after a landing pad without handling the exception. */
    data class Resume(val value: Value) : Instruction { override val result: Value? get() = null }
    /** Windows SEH: dispatch to one of several [handlers] based on exception type. */
    data class CatchSwitch(val dest: InstructionRef, val parentPad: Value?, val handlers: List<String>, val unwindDest: String?) : Instruction { override val result get() = dest }
    /** Windows SEH: begin a catch handler funclet. */
    data class CatchPad(val dest: InstructionRef, val catchSwitch: Value, val args: List<Value>) : Instruction { override val result get() = dest }
    /** Windows SEH: begin a cleanup handler (runs destructors, etc.). */
    data class CleanupPad(val dest: InstructionRef, val parentPad: Value?, val args: List<Value>) : Instruction { override val result get() = dest }
    /** Windows SEH: return from a catch handler to normal code at [dest]. */
    data class CatchRet(val catchPad: Value, val dest: String) : Instruction { override val result: Value? get() = null }
    /** Windows SEH: return from a cleanup handler. [unwindDest]=null means resume unwinding. */
    data class CleanupRet(val cleanupPad: Value, val unwindDest: String?) : Instruction { override val result: Value? get() = null }

    // --- SSA ---

    /** Phi node: merges values at a control flow join point. Each entry in [incoming] is
     *  a (value, predecessor_block) pair. Must appear at the start of a block. */
    data class Phi(val dest: InstructionRef, val incoming: List<Pair<Value, String>>) : Instruction { override val result get() = dest }
    /** Conditional select: returns [trueValue] if [condition] (i1) is true, else [falseValue].
     *  Like a ternary operator. Not a terminator — both values are computed. */
    data class Select(val dest: InstructionRef, val condition: Value, val trueValue: Value, val falseValue: Value) : Instruction { override val result get() = dest }
    /** Freeze: converts undef/poison to an arbitrary but fixed value. Prevents poison propagation. */
    data class Freeze(val dest: InstructionRef, val value: Value) : Instruction { override val result get() = dest }

    // --- Vector operations ---

    /** Extract a scalar element from a SIMD vector at the given [index]. */
    data class ExtractElement(val dest: InstructionRef, val vector: Value, val index: Value) : Instruction { override val result get() = dest }
    /** Insert a scalar [element] into a SIMD vector at the given [index]. */
    data class InsertElement(val dest: InstructionRef, val vector: Value, val element: Value, val index: Value) : Instruction { override val result get() = dest }
    /** Shuffle (rearrange) lanes from two vectors according to [mask].
     *  `mask[i]` < N selects from v1, >= N selects from v2. Result has `mask.size` lanes. */
    data class ShuffleVector(val dest: InstructionRef, val v1: Value, val v2: Value, val mask: List<Int>) : Instruction { override val result get() = dest }
    /** Broadcast a single [scalar] to all lanes of [vectorType]. */
    data class Splat(val dest: InstructionRef, val scalar: Value, val vectorType: Type.Vector) : Instruction { override val result get() = dest }

    /** Reduce a vector to a single scalar using [op]. See [VectorReduceOp]. */
    data class VectorReduce(val dest: InstructionRef, val op: VectorReduceOp, val vector: Value) : Instruction { override val result get() = dest }

    // --- Aggregate operations ---

    /** Extract a field from a struct or array by [indices] (one per nesting level). */
    data class ExtractValue(val dest: InstructionRef, val aggregate: Value, val indices: List<Int>) : Instruction { override val result get() = dest }
    /** Insert [element] into a struct or array at [indices], returning the updated aggregate. */
    data class InsertValue(val dest: InstructionRef, val aggregate: Value, val element: Value, val indices: List<Int>) : Instruction { override val result get() = dest }

    // -----------------------------------------------------------------------
    // HIGH-LEVEL OPERATIONS (JVM/WASM consume directly; lowered for native)
    // -----------------------------------------------------------------------

    // --- Object lifecycle ---

    /** Allocate a new object of the given class. [typeArgs] for generic instantiation. */
    data class NewObject(val dest: InstructionRef, val className: String, val typeArgs: List<Type> = emptyList()) : Instruction { override val result get() = dest }
    /** Allocate a managed array of [elementType] with [size] elements. */
    data class NewArray(val dest: InstructionRef, val elementType: Type, val size: Value) : Instruction { override val result get() = dest }
    /** Allocate a multi-dimensional managed array. */
    data class NewMultiArray(val dest: InstructionRef, val elementType: Type, val dimensions: List<Value>) : Instruction { override val result get() = dest }

    // --- Field access ---

    /** Read instance field [fieldName] from [obj]. */
    data class GetField(val dest: InstructionRef, val obj: Value, val className: String, val fieldName: String, val fieldType: Type) : Instruction { override val result get() = dest }
    /** Write [value] to instance field [fieldName] of [obj]. */
    data class PutField(val obj: Value, val className: String, val fieldName: String, val fieldType: Type, val value: Value) : Instruction { override val result: Value? get() = null }
    /** Read static field [fieldName] from [className]. */
    data class GetStatic(val dest: InstructionRef, val className: String, val fieldName: String, val fieldType: Type) : Instruction { override val result get() = dest }
    /** Write [value] to static field [fieldName] of [className]. */
    data class PutStatic(val className: String, val fieldName: String, val fieldType: Type, val value: Value) : Instruction { override val result: Value? get() = null }

    // --- Method dispatch ---

    /** Virtual method call: dispatches through the vtable based on runtime type of [obj]. */
    data class VirtualCall(
        val dest: InstructionRef?, val obj: Value, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest }

    /** Interface method call: dispatches via interface method table. */
    data class InterfaceCall(
        val dest: InstructionRef?, val obj: Value, val interfaceName: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest }

    /** Direct call bypassing vtable: used for super calls, private methods, constructors. */
    data class SpecialCall(
        val dest: InstructionRef?, val obj: Value, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest }

    /** Static method call (no receiver object). */
    data class StaticCall(
        val dest: InstructionRef?, val className: String,
        val methodName: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest }

    /** invokedynamic-style dispatch: uses a [bootstrapMethod] to resolve the call site at runtime.
     *  Used for lambdas, string concatenation optimization, etc. */
    data class DynamicCall(
        val dest: InstructionRef?, val bootstrapMethod: BootstrapMethod,
        val name: String, val methodType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result get() = dest }

    /** Constructor invocation: initializes [obj] in place. No return value. */
    data class ConstructorCall(
        val obj: Value, val className: String,
        val constructorType: Type.Function, val args: List<Value>,
    ) : Instruction { override val result: Value? get() = null }

    // --- Type operations ---

    /** Runtime type check: result is i1 (true if [obj] is an instance of [checkType]). */
    data class InstanceOf(val dest: InstructionRef, val obj: Value, val checkType: Type) : Instruction { override val result get() = dest }
    /** Type cast: throws at runtime if [obj] is not an instance of [castType]. */
    data class CheckCast(val dest: InstructionRef, val obj: Value, val castType: Type) : Instruction { override val result get() = dest }
    /** Get runtime type identifier of [obj] (for type-switch dispatch). Result is i32. */
    data class TypeId(val dest: InstructionRef, val obj: Value) : Instruction { override val result get() = dest }

    // --- Managed array operations (with bounds checking on managed backends) ---

    /** Read element at [index] from managed [array]. */
    data class ArrayGet(val dest: InstructionRef, val array: Value, val index: Value, val elementType: Type) : Instruction { override val result get() = dest }
    /** Write [value] to managed [array] at [index]. */
    data class ArraySet(val array: Value, val index: Value, val value: Value, val elementType: Type) : Instruction { override val result: Value? get() = null }
    /** Get the length of a managed [array]. Result is i32. */
    data class ArrayLength(val dest: InstructionRef, val array: Value) : Instruction { override val result get() = dest }

    // --- Monitor / synchronization ---

    /** Acquire the object's monitor lock (e.g., `synchronized` block entry). */
    data class MonitorEnter(val obj: Value) : Instruction { override val result: Value? get() = null }
    /** Release the object's monitor lock. */
    data class MonitorExit(val obj: Value) : Instruction { override val result: Value? get() = null }

    // --- Exception handling (managed) ---

    /** Throw an exception. Terminator: transfers control to the nearest handler. */
    data class Throw(val exception: Value) : Instruction { override val result: Value? get() = null }
    /** Try/catch/finally region. [catches] maps exception types to handler blocks. */
    data class TryCatchRegion(
        val tryBlock: String,
        val catches: List<CatchHandler>,
        val finallyBlock: String? = null,
    ) : Instruction { override val result: Value? get() = null }

    // --- Boxing / unboxing ---

    /** Wrap a primitive [value] into a heap object of [boxType] (e.g., int → Integer). */
    data class Box(val dest: InstructionRef, val value: Value, val boxType: Type) : Instruction { override val result get() = dest }
    /** Extract the primitive value from a boxed object [obj]. */
    data class Unbox(val dest: InstructionRef, val obj: Value, val unboxType: Type) : Instruction { override val result get() = dest }

    // --- Closures / lambdas ---

    /** Create a closure: bundles [function] with [captures] (captured variables from enclosing scope). */
    data class ClosureCreate(
        val dest: InstructionRef, val function: Value,
        val captures: List<Value>, val closureType: Type.Function,
    ) : Instruction { override val result get() = dest }

    /** Invoke a closure with [args]. */
    data class ClosureInvoke(
        val dest: InstructionRef?, val closure: Value,
        val args: List<Value>, val returnType: Type,
    ) : Instruction { override val result get() = dest }

    // --- Tagged unions / algebraic data types ---

    /** Construct a tagged union value for the given [variantName] with [fields]. */
    data class ConstructVariant(val dest: InstructionRef, val unionType: Type.TaggedUnion, val variantName: String, val fields: List<Value>) : Instruction { override val result get() = dest }
    /** Extract the discriminant (tag) from a tagged union. */
    data class GetTag(val dest: InstructionRef, val union: Value) : Instruction { override val result get() = dest }
    /** Extract a field from a specific variant of a tagged union. */
    data class GetVariantField(val dest: InstructionRef, val union: Value, val variantName: String, val fieldIndex: Int) : Instruction { override val result get() = dest }
    /** Pattern match: branch based on the tag of a tagged union. Terminator. */
    data class TagSwitch(val union: Value, val cases: List<Pair<String, String>>, val defaultTarget: String? = null) : Instruction { override val result: Value? get() = null }

    // --- GC integration ---

    /** Allocate GC-managed memory for [allocType]. */
    data class GCAlloc(val dest: InstructionRef, val allocType: Type, val size: Value? = null) : Instruction { override val result get() = dest }
    /** GC safepoint: the thread may be suspended here for garbage collection. */
    data class GCSafepoint(val dummy: Unit = Unit) : Instruction { override val result: Value? get() = null }
    /** Register a stack pointer as a GC root so the collector can trace it. */
    data class GCRoot(val ptr: Value, val metadata: Value?) : Instruction { override val result: Value? get() = null }

    // --- Pinning and interior pointers (C++/CLI mixed-mode support) ---

    /**
     * Pin a GC-managed reference so the GC will not move the object.
     * Returns a [Type.PinnedRef] that can be converted to a raw native pointer.
     * The object remains pinned until [Unpin] is called on the result.
     * Inspired by C++/CLI `pin_ptr<T>`.
     */
    data class Pin(val dest: InstructionRef, val ref: Value) : Instruction { override val result get() = dest }

    /**
     * Unpin a previously pinned reference, allowing the GC to move the object again.
     * After unpinning, any raw pointers derived from the pinned ref are invalid.
     */
    data class Unpin(val ref: Value) : Instruction { override val result: Value? get() = null }

    /**
     * Get an interior pointer into a GC-managed object (a field or array element).
     * The GC tracks interior refs and updates them when the containing object moves.
     * [ref] must be a Reference or TrackedRef. [index] is a field index or array offset.
     * Result type is [Type.InteriorRef].
     */
    data class InteriorPtr(val dest: InstructionRef, val ref: Value, val index: Value, val pointeeType: Type) : Instruction { override val result get() = dest }

    /**
     * GC write barrier: notifies the GC that a reference field is being written.
     * Required for generational and concurrent collectors (e.g., card marking,
     * snapshot-at-the-beginning). [obj] is the object being written to, [fieldIndex]
     * identifies the field, and [value] is the new reference being stored.
     */
    data class WriteBarrier(val obj: Value, val fieldIndex: Value, val value: Value) : Instruction { override val result: Value? get() = null }

    /**
     * GC read barrier: interposes on reference loads for concurrent/relocating GCs
     * (e.g., ZGC load barriers, Shenandoah Brooks pointers). [ref] is the reference
     * being read; result is the updated (potentially relocated) reference.
     */
    data class ReadBarrier(val dest: InstructionRef, val ref: Value) : Instruction { override val result get() = dest }

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
    ) : Instruction { override val result get() = dest }

    // --- Reference counting ---

    /** Increment the reference count of [obj]. */
    data class RefRetain(val obj: Value) : Instruction { override val result: Value? get() = null }
    /** Decrement the reference count of [obj]. May deallocate when count reaches zero. */
    data class RefRelease(val obj: Value) : Instruction { override val result: Value? get() = null }
    /** Get the current reference count. Result is i32. */
    data class RefCount(val dest: InstructionRef, val obj: Value) : Instruction { override val result get() = dest }

    // --- Coroutines / async ---

    /** Initialize a coroutine frame. Returns a handle. */
    data class CoroBegin(val dest: InstructionRef, val id: Value, val mem: Value) : Instruction { override val result get() = dest }
    /** Mark coroutine completion. [unwind] = true if ending due to exception. */
    data class CoroEnd(val handle: Value, val unwind: Boolean = false) : Instruction { override val result: Value? get() = null }
    /** Suspend the coroutine. Returns i8: 0=resumed normally, 1=should cleanup. */
    data class CoroSuspend(val dest: InstructionRef, val save: Value?, val isFinal: Boolean = false) : Instruction { override val result get() = dest }
    /** Resume a suspended coroutine. */
    data class CoroResume(val handle: Value) : Instruction { override val result: Value? get() = null }
    /** Destroy a coroutine frame, freeing its memory. */
    data class CoroDestroy(val handle: Value) : Instruction { override val result: Value? get() = null }
    /** Get the size needed for a coroutine frame. Result is i64. */
    data class CoroSize(val dest: InstructionRef) : Instruction { override val result get() = dest }

    // --- Intrinsic / target-specific ---

    /** Generic intrinsic for target-specific operations not covered by the instruction set. */
    data class Intrinsic(val dest: InstructionRef?, val name: String, val args: List<Value>, val returnType: Type) : Instruction { override val result get() = dest }

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
    ) : Instruction { override val result get() = dest }

    // --- Debug / metadata ---

    /** Attach source location info (line, column, scope) to subsequent instructions. */
    data class DebugLoc(val line: Int, val col: Int, val scope: String, val inlinedAt: String? = null) : Instruction { override val result: Value? get() = null }
    /** Associate an SSA [value] with a source-level variable name (for debugger display). */
    data class DebugValue(val variable: String, val value: Value, val expression: String? = null) : Instruction { override val result: Value? get() = null }
    /** Associate an address (alloca) with a source-level variable (for debugger display). */
    data class DebugDeclare(val variable: String, val address: Value, val expression: String? = null) : Instruction { override val result: Value? get() = null }

    // --- Optimizer hints ---

    /** Tell the optimizer to assume [condition] is true. UB if it's actually false. */
    data class Assume(val condition: Value) : Instruction { override val result: Value? get() = null }
    /** Branch prediction hint: tells the optimizer that [value] is expected to equal [expected]. */
    data class Expect(val dest: InstructionRef, val value: Value, val expected: Constant) : Instruction { override val result get() = dest }
}

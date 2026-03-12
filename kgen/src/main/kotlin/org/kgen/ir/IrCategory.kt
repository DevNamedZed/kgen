package org.kgen.ir

/**
 * Functional category for IR instructions, inspired by
 * [LLVM's LangRef](https://llvm.org/docs/LangRef.html) grouping.
 *
 * Each instruction belongs to exactly one category. Categories map to
 * [IrTier]s via [tier], which determines at what stage of the compilation
 * pipeline the instruction is expected to exist.
 */
enum class IrCategory {
    // --- Tier 0: Structural (always allowed) ---

    /** Branch, return, switch, trap — end a basic block. */
    TERMINATOR,
    /** Call, invoke, callbr, varargs. */
    CALL,
    /** Phi, select, freeze — SSA bookkeeping. */
    SSA,
    /** DebugLoc, DebugValue, DebugDeclare, Assume, Expect. */
    DEBUG,
    /** Intrinsic, InlineAsm — target-specific escape hatches. */
    INTRINSIC,

    // --- Tier 1: Machine (hardware operations) ---

    /** Integer and float computation: add, mul, sqrt, fma, ... */
    ARITHMETIC,
    /** Bit logic, shifts, rotations, bit queries: and, shl, ctlz, bswap, ... */
    BITWISE,
    /** ICmp, FCmp — integer and float comparison. */
    COMPARISON,
    /** Type width changes, float/int casts, pointer reinterpretation. */
    CONVERSION,
    /** Alloca, load, store, GEP, memcpy, stack save/restore, lifetime. */
    MEMORY,
    /** Fence, CmpXchg, AtomicRMW — concurrency primitives. */
    ATOMIC,
    /** ExtractElement, InsertElement, ShuffleVector, Splat, VectorReduce. */
    VECTOR,
    /** ExtractValue, InsertValue — struct/array field access. */
    AGGREGATE,
    /** Native EH (LandingPad, Resume), SEH (CatchSwitch, CatchPad, CleanupPad, ...). */
    EXCEPTION,

    // --- Tier 2: Runtime ---

    /** GC, barriers, refcounting, coroutines — lower to runtime calls. */
    RUNTIME,

    /** Managed/native boundary: pinning, interior pointers, managed calls. */
    INTEROP,

    // --- Tier 3: Object ---

    /** Allocation, fields, dispatch, type ops, arrays, closures, ADTs — object model. */
    OBJECT;

    /** The compilation pipeline tier this category belongs to. */
    val tier: IrTier get() = when (this) {
        TERMINATOR, CALL, SSA, DEBUG, INTRINSIC -> IrTier.STRUCTURAL
        ARITHMETIC, BITWISE, COMPARISON, CONVERSION,
        MEMORY, ATOMIC, VECTOR, AGGREGATE, EXCEPTION -> IrTier.MACHINE
        RUNTIME -> IrTier.RUNTIME
        INTEROP -> IrTier.INTEROP
        OBJECT -> IrTier.OBJECT
    }
}

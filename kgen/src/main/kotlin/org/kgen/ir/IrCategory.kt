package org.kgen.ir

/**
 * Functional category for IR instructions.
 *
 * Every instruction belongs to exactly one category. Categories serve three purposes:
 *
 * 1. **Constraint enforcement.** The [org.kgen.ir.build.IrBuilder] can restrict which
 *    categories are legal via `allowedCategories`. Emitting an instruction whose category
 *    is not in the allowed set produces an immediate error.
 *
 * 2. **Tier classification.** Categories map to [IrTier]s via [tier], which describes
 *    at what stage of the compilation pipeline the instruction typically exists.
 *    High-tier instructions (Object, Runtime) are lowered to low-tier instructions
 *    (Machine, Structural) by passes before code generation.
 *
 * 3. **Capability baseline.** In the capability system, category membership implies
 *    a baseline set of required capabilities (e.g., all [OBJECT] instructions require
 *    `ManagedObjects`). Individual instructions may declare additional requirements
 *    beyond the baseline.
 *
 * Categories are also the basis for the typed builder API ([InstructionSet] interfaces)
 * and the visitor API ([InstructionVisitor] interfaces) — each category has a
 * corresponding emission interface and a corresponding visitor interface.
 *
 * @see IrTier
 * @see org.kgen.ir.instructions.Instruction.category
 */
enum class IrCategory {

    // --- Tier 0: Structural (always allowed, no capability requirements) ---

    /**
     * Terminator instructions that end a basic block.
     *
     * Every basic block must end with exactly one terminator. Terminators define
     * the control-flow graph edges: branches, returns, switches, traps.
     *
     * Instructions: `Ret`, `Br`, `CondBr`, `Switch`, `IndirectBr`, `Unreachable`, `Trap`, `DebugTrap`.
     */
    TERMINATOR,

    /**
     * Function call instructions.
     *
     * Direct and indirect calls, invocations (calls that may throw with explicit
     * unwind destinations), and `callbr` (calls with multiple possible return points
     * for inline assembly).
     *
     * Instructions: `Call`, `Invoke`, `CallBr`.
     */
    CALL,

    /**
     * SSA bookkeeping instructions.
     *
     * Value merging at control-flow join points, conditional selection without
     * branching, and poison stabilization.
     *
     * Instructions: `Phi`, `Select`, `Freeze`.
     */
    SSA,

    /**
     * Debug and optimizer hint instructions.
     *
     * Source-level debug information (line numbers, variable bindings) and
     * optimizer hints (assumptions, branch predictions). These have no
     * code-generation effect and are always legal regardless of capabilities.
     *
     * Instructions: `DebugLoc`, `DebugValue`, `DebugDeclare`, `Assume`, `Expect`.
     */
    DEBUG,

    /**
     * Target-specific and escape-hatch instructions.
     *
     * Named intrinsics for target-specific operations and inline assembly with
     * GCC-style constraints. Any operation the optimizer needs to reason about
     * should be a first-class instruction, not an intrinsic.
     *
     * Instructions: `Intrinsic`, `InlineAsm`.
     */
    INTRINSIC,

    // --- Tier 1: Machine (hardware operations, require NativeMemory capability) ---

    /**
     * Integer and floating-point computation.
     *
     * Arithmetic with optional overflow flags (nuw/nsw), floating-point math with
     * optional fast-math flags, overflow-checked variants, saturating arithmetic,
     * min/max, and math functions (sqrt, ceil, floor, fma).
     *
     * Instructions: `Add`, `Sub`, `Mul`, `UDiv`, `SDiv`, `URem`, `SRem`, `Neg`,
     * `FAdd`, `FSub`, `FMul`, `FDiv`, `FRem`, `FNeg`, `FAbs`, `FMA`, `Sqrt`,
     * `Ceil`, `Floor`, `Round`, `FTrunc`, `CopySign`, overflow/saturating/min/max variants.
     */
    ARITHMETIC,

    /**
     * Bitwise logic, shifts, rotations, and bit manipulation.
     *
     * Instructions: `And`, `Or`, `Xor`, `Not`, `Shl`, `LShr`, `AShr`, `Rotl`,
     * `Rotr`, `Ctlz`, `Cttz`, `Ctpop`, `BSwap`, `BitReverse`.
     */
    BITWISE,

    /**
     * Integer and floating-point comparison.
     *
     * Instructions: `ICmp` (integer comparison with [ICmpPredicate]),
     * `FCmp` (floating-point comparison with [FCmpPredicate]).
     */
    COMPARISON,

    /**
     * Type conversion and reinterpretation.
     *
     * Integer width changes (truncation, extension), float precision changes,
     * integer/float conversions, pointer/integer casts, bitwise reinterpretation,
     * and address space casts.
     *
     * Instructions: `IntTrunc`, `ZExt`, `SExt`, `FPTrunc`, `FPExt`, `FPToUI`, `FPToSI`,
     * `UIToFP`, `SIToFP`, `PtrToInt`, `IntToPtr`, `BitCast`, `AddrSpaceCast`.
     */
    CONVERSION,

    /**
     * Memory operations.
     *
     * Stack allocation, loads, stores, pointer arithmetic (GEP), bulk memory
     * operations (memcpy, memset, memmove), stack save/restore, lifetime markers,
     * prefetch hints, and C varargs support.
     *
     * Instructions: `Alloca`, `Load`, `Store`, `GetElementPtr`, `MemCpy`, `MemSet`,
     * `MemMove`, `Prefetch`, `StackSave`, `StackRestore`, `LifetimeStart`, `LifetimeEnd`,
     * `VAStart`, `VAEnd`, `VACopy`, `VAArg`.
     */
    MEMORY,

    /**
     * Atomic memory operations and memory fences.
     *
     * Hardware-level concurrency primitives with explicit memory ordering.
     *
     * Instructions: `Fence`, `CmpXchg`, `AtomicRMW`.
     */
    ATOMIC,

    /**
     * SIMD vector operations.
     *
     * Element extraction/insertion, lane shuffling, scalar broadcast (splat),
     * and horizontal reductions across vector lanes.
     *
     * Instructions: `ExtractElement`, `InsertElement`, `ShuffleVector`, `Splat`, `VectorReduce`.
     */
    VECTOR,

    /**
     * Aggregate (struct/array) field access.
     *
     * Extracting and inserting fields in struct and array values without going through
     * memory. Operates on SSA values directly, unlike [MEMORY] which operates through pointers.
     *
     * Instructions: `ExtractValue`, `InsertValue`.
     */
    AGGREGATE,

    /**
     * Native exception handling.
     *
     * DWARF/SEH-style structured exception handling with landing pads, catch pads,
     * cleanup pads, and resume. Used by the C++ and native exception models.
     *
     * Instructions: `LandingPad`, `Resume`, `CatchSwitch`, `CatchPad`, `CleanupPad`,
     * `CatchRet`, `CleanupRet`.
     */
    EXCEPTION,

    // --- Tier 2: Runtime (require GcManaged or NativeInterop capabilities) ---

    /**
     * Runtime system instructions.
     *
     * GC allocation, safepoints, root registration, write/read barriers, reference
     * counting, and coroutine primitives. These are lowered to runtime calls by
     * backend-specific passes.
     *
     * Instructions: `GCAlloc`, `GCSafepoint`, `GCRoot`, `WriteBarrier`, `ReadBarrier`,
     * `RefRetain`, `RefRelease`, `RefCount`, `CoroBegin`, `CoroEnd`, `CoroSuspend`,
     * `CoroResume`, `CoroDestroy`, `CoroSize`.
     */
    RUNTIME,

    /**
     * Managed/native interop boundary instructions.
     *
     * Object pinning (preventing GC relocation), interior pointers into managed objects,
     * and managed-to-native / native-to-managed call transitions.
     *
     * Instructions: `Pin`, `Unpin`, `InteriorPtr`, `ManagedCall`.
     */
    INTEROP,

    // --- Tier 3: Object (require ManagedObjects capability) ---

    /**
     * High-level object model instructions.
     *
     * Object allocation, field access, virtual/interface/static dispatch, type checks,
     * managed arrays, monitors, managed exceptions, boxing, closures, tagged unions,
     * and weak references. These are the instructions frontend authors use most —
     * lowering passes convert them to Machine-tier instructions before code generation.
     *
     * Instructions: `NewObject`, `NewArray`, `GetField`, `PutField`, `VirtualCall`,
     * `InterfaceCall`, `StaticCall`, `InstanceOf`, `CheckCast`, `Throw`, `TryCatchRegion`,
     * `Box`, `Unbox`, `ClosureCreate`, `ClosureInvoke`, `ConstructVariant`, `TagSwitch`,
     * and more.
     */
    OBJECT,

    /**
     * Speculative optimization and deoptimization instructions.
     *
     * Frame state capture, speculative guards with bailout, and on-stack replacement
     * (OSR) entry points. Used by the JIT compiler for speculative optimization that
     * can fall back to the interpreter when assumptions are violated.
     *
     * Requires the `Deoptimization` capability. Instructions will be added in Phase B1.
     */
    DEOPTIMIZATION,

    /**
     * GPU and general-purpose compute instructions.
     *
     * Thread/block/warp identification, shared memory allocation, barrier synchronization,
     * warp-level primitives (shuffle, vote), kernel launch, and compute-specific atomics.
     *
     * Requires the `ComputeKernel` capability. Instructions will be added in Phase B5.
     */
    COMPUTE;

    /**
     * The compilation pipeline tier this category belongs to.
     *
     * Tiers describe at what abstraction level instructions exist:
     * - [IrTier.STRUCTURAL] — always present, no lowering needed
     * - [IrTier.MACHINE] — maps directly to hardware instructions
     * - [IrTier.RUNTIME] — requires runtime system support
     * - [IrTier.INTEROP] — crosses managed/native boundary
     * - [IrTier.OBJECT] — high-level object model, must be lowered before codegen
     */
    val tier: IrTier get() = when (this) {
        TERMINATOR, CALL, SSA, DEBUG, INTRINSIC -> IrTier.STRUCTURAL
        ARITHMETIC, BITWISE, COMPARISON, CONVERSION,
        MEMORY, ATOMIC, VECTOR, AGGREGATE, EXCEPTION -> IrTier.MACHINE
        RUNTIME -> IrTier.RUNTIME
        INTEROP -> IrTier.INTEROP
        OBJECT -> IrTier.OBJECT
        DEOPTIMIZATION -> IrTier.STRUCTURAL
        COMPUTE -> IrTier.MACHINE
    }
}

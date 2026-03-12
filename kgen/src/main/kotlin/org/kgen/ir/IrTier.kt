package org.kgen.ir

/**
 * Compilation pipeline tier. Instructions at higher tiers get lowered
 * (decomposed) into instructions at lower tiers as compilation progresses.
 *
 * ```
 * OBJECT    →  NewObject, VirtualCall, ArrayGet, ...
 *   ↓ lowering
 * RUNTIME   →  GCAlloc, GCSafepoint, WriteBarrier, ...
 * INTEROP   →  Pin, Unpin, InteriorPtr, ManagedCall, ...
 *   ↓ lowering
 * MACHINE   →  Add, Load, Store, Call, GEP, ...
 *   ↓ codegen
 * native code
 * ```
 *
 * [STRUCTURAL] instructions (control flow, SSA, debug) are used at every
 * stage and are never lowered.
 */
enum class IrTier(val level: Int) {
    /** Control flow, SSA, debug, intrinsics — always allowed at any stage. */
    STRUCTURAL(0),

    /** Hardware operations: arithmetic, memory, conversions, atomics, vectors.
     *  Code generators consume these directly. */
    MACHINE(1),

    /** Runtime interactions: GC, barriers, refcounting, coroutines.
     *  Lower to Call instructions into runtime functions. */
    RUNTIME(2),

    /** Managed/native boundary: pinning, interior pointers, managed calls.
     *  Bridge between GC-managed and raw memory worlds. */
    INTEROP(2),

    /** Object model: allocation, fields, dispatch, type ops, arrays, closures, ADTs.
     *  Managed backends consume directly; native backends lower to Runtime + Machine. */
    OBJECT(3);
}

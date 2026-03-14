package org.kgen.ir

/**
 * Semantic capabilities that a compilation context may grant.
 *
 * Capabilities are orthogonal, composable axes that describe what a compilation
 * context may do. Each instruction declares the capabilities it requires; each
 * context declares the capabilities it grants; the verifier checks that every
 * instruction's requirements are satisfied.
 *
 * Capabilities complement the [IrCategory] system: categories determine which
 * instruction *opcodes* are legal, capabilities determine which *semantic
 * features* are available. Both systems coexist — categories for instruction
 * filtering, capabilities for semantic validation.
 *
 * @see CapabilitySet
 * @see IrConstraints
 */
enum class Capability {

    // --- Memory capabilities ---

    /** GC-managed heap present. GCAlloc, GCRoot, GCRelocate, GCSafepoint are legal. */
    GC_MANAGED,

    /** GC may compact. GCRelocate is semantically necessary after safepoints. */
    GC_MOVING,

    /** GC barriers present. WriteBarrier, ReadBarrier are legal. */
    GC_BARRIERS,

    /** Reference counting present. RefRetain, RefRelease are legal. */
    REF_COUNTED,

    /** Unmanaged memory in use. Alloca, Load, Store, GEP are legal. */
    NATIVE_MEMORY,

    /** Compute address spaces in use. SharedMemAlloc, compute-specific Load/Store are legal. */
    COMPUTE_MEMORY,

    // --- Type system capabilities ---

    /** Object category is legal. NewObject, VirtualCall, etc. are legal. */
    MANAGED_OBJECTS,

    /** Virtual and interface dispatch instructions are legal. */
    VIRTUAL_DISPATCH,

    /** InstanceOf, CheckCast, TypeId are legal. */
    TYPE_CHECKS,

    /** Generic types are present. Monomorphization has not yet run. */
    GENERICS,

    // --- Exception capabilities ---

    /** Throw / TryCatchRegion are legal (Object-level EH). */
    MANAGED_EXCEPTIONS,

    /** LandingPad / CatchSwitch are legal (machine-level EH). */
    NATIVE_EXCEPTIONS,

    /** No exception handling of any kind. */
    NO_EXCEPTIONS,

    // --- Interop capabilities ---

    /** Pin, Unpin, InteriorPtr, ManagedCall are legal. */
    NATIVE_INTEROP,

    /** JNI calling convention and transition semantics are supported. */
    JNI,

    /** P/Invoke calling convention is supported. */
    P_INVOKE,

    // --- Control flow capabilities ---

    /** Coroutine instructions (CoroBegin, etc.) are legal. */
    COROUTINES,

    // --- Speculation capabilities ---

    /** Guard, FixedGuard, Deoptimize, OSREntry, FrameState are legal. */
    DEOPTIMIZATION,

    /** OSREntry specifically is legal (subset of Deoptimization). */
    OSR,

    // --- Compute capabilities ---

    /** Compute category is legal. Thread IDs, barriers, kernel-body instructions are legal. */
    COMPUTE_KERNEL,

    /** KernelLaunch instruction is legal (host side). */
    KERNEL_LAUNCH,
}

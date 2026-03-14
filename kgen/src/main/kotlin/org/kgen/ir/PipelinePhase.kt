package org.kgen.ir

/**
 * Descriptive labels for the canonical compilation phases.
 *
 * These phases describe where a module is in the compilation pipeline — what
 * instructions have been lowered and what invariants hold. They are purely
 * informational: the builder does not enforce phase transitions, and users
 * can construct arbitrary pass orderings.
 *
 * Passes may optionally declare which phase transition they perform using
 * [phaseTransition]. Post-phase validators can check that the expected
 * invariants hold after a phase transition.
 *
 * @param ordinal the phase ordering (lower = earlier in compilation)
 * @see IrTier
 * @see IrConstraints
 */
enum class PipelinePhase(val order: Int) {
    /**
     * Direct output of language frontends.
     *
     * All Object-category instructions are present. No Runtime or Interop
     * instructions. FrameState and Guards may appear on JIT paths.
     */
    FRONTEND_OBJECT(0),

    /**
     * After object lowering.
     *
     * Object instructions have been decomposed into runtime primitives.
     *
     * **Must not contain:** NewObject, NewArray, NewMultiArray, VirtualCall,
     * InterfaceCall, GetField, PutField, GetStatic, PutStatic, ArrayGet,
     * ArraySet, Throw, Box, Unbox, InstanceOf, CheckCast, TagSwitch.
     *
     * **Must contain:** GCAlloc for each allocation, GCSafepoint at
     * object-allocating call sites, GCRelocate for live references after
     * each safepoint, WriteBarrier at reference field stores.
     */
    POST_OBJECT_LOWERING(1),

    /**
     * After runtime lowering.
     *
     * Runtime instructions have been expanded to platform-specific sequences.
     *
     * **Must not contain:** Abstract GCAlloc (replaced by allocator sequences
     * or runtime entry calls), abstract WriteBarrier (expanded to
     * platform-specific sequences or eliminated).
     *
     * **Must contain:** Direct runtime calls, explicit GCRoot/GCRelocate,
     * machine-level exception handling.
     */
    POST_RUNTIME_LOWERING(2),

    /**
     * Backend-legal form — only instructions the backend can emit directly.
     *
     * **Must not contain:** Any abstract instructions from higher tiers.
     * Reference types replaced by OpaquePointer at machine level.
     * FrameState encoded as out-of-band GC maps / deopt tables.
     */
    BACKEND_LEGAL(3);

    companion object {
        /**
         * Instructions that must be absent after [POST_OBJECT_LOWERING].
         *
         * Object-category instructions that should have been decomposed
         * by the object lowering pass.
         */
        @JvmField
        val POST_OBJECT_LOWERING_ABSENT: Set<String> = setOf(
            "NewObject", "NewArray", "NewMultiArray",
            "VirtualCall", "InterfaceCall",
            "GetField", "PutField", "GetStatic", "PutStatic",
            "ArrayGet", "ArraySet",
            "Throw", "Box", "Unbox",
            "InstanceOf", "CheckCast", "TagSwitch",
        )

        /**
         * Instructions that must be absent after [POST_RUNTIME_LOWERING].
         *
         * Runtime-category instructions that should have been expanded
         * by the runtime lowering pass.
         */
        @JvmField
        val POST_RUNTIME_LOWERING_ABSENT: Set<String> = setOf(
            "GCAlloc", "WriteBarrier", "ReadBarrier",
        )
    }
}

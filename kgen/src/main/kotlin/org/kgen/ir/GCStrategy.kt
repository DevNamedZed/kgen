package org.kgen.ir

/**
 * GC strategy for a function. Determines how GC roots are tracked
 * and how safepoints are handled during code generation.
 *
 * Set via `IrFunction.gc` field using the strategy's [id] string.
 *
 * ```java
 * // In IrBuilder
 * builder.createFunction("f", params, returnType);
 * builder.setGCStrategy(GCStrategy.STATEPOINT);
 * ```
 */
enum class GCStrategy(val id: String) {
    /**
     * No GC integration. Stack slots are not tracked.
     * Use for pure native code with no managed references.
     */
    NONE("none"),

    /**
     * Shadow stack: maintain a parallel stack of GC root pointers.
     * Simple, portable, works everywhere. Moderate overhead.
     * Each function pushes/pops a frame on the shadow stack at entry/exit.
     */
    SHADOW_STACK("shadow-stack"),

    /**
     * Statepoint: emit stack maps at each safepoint recording which
     * registers/stack slots hold live GC references.
     * Zero overhead between safepoints. Requires runtime stack map lookup.
     */
    STATEPOINT("statepoint"),

    /**
     * Reference counting: emit retain/release calls for managed references.
     * No pause times, deterministic destruction. Cycle collection needed separately.
     */
    REFERENCE_COUNTING("refcount"),
    ;

    companion object {
        @JvmStatic
        fun fromId(id: String): GCStrategy =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown GC strategy: $id")
    }
}

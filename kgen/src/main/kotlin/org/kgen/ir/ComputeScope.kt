package org.kgen.ir

/**
 * Synchronization scope for GPU compute barriers and fences.
 *
 * Used by [org.kgen.ir.instructions.ComputeBarrier] and [org.kgen.ir.instructions.ComputeFence].
 */
enum class ComputeScope {
    WARP,
    BLOCK,
    DEVICE,
}

package org.kgen.ir

/**
 * Dimension index for GPU compute thread/block/grid identification.
 *
 * Used by [org.kgen.ir.instructions.ThreadId], [org.kgen.ir.instructions.BlockId],
 * [org.kgen.ir.instructions.BlockDim], and [org.kgen.ir.instructions.GridDim].
 */
enum class ThreadDim {
    X,
    Y,
    Z,
}

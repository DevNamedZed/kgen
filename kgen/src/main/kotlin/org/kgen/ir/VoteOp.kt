package org.kgen.ir

/**
 * Warp-level vote operation for lane-predicate reduction.
 *
 * Used by [org.kgen.ir.instructions.WarpVote].
 */
enum class VoteOp {
    ALL,
    ANY,
    BALLOT,
}

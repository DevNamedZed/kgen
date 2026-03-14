// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for compute instructions.
 *
 * Implement this interface to receive callbacks for compute instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ComputeInstructionVisitor : InstructionVisitor {

    fun visitThreadId(instruction: ThreadId) {}

    fun visitBlockId(instruction: BlockId) {}

    fun visitWarpId(instruction: WarpId) {}

    fun visitLaneId(instruction: LaneId) {}

    fun visitBlockDim(instruction: BlockDim) {}

    fun visitGridDim(instruction: GridDim) {}

    fun visitComputeBarrier(instruction: ComputeBarrier) {}

    fun visitComputeFence(instruction: ComputeFence) {}

    fun visitKernelLaunch(instruction: KernelLaunch) {}

    fun visitComputeAtomicAdd(instruction: ComputeAtomicAdd) {}

    fun visitComputeAtomicMin(instruction: ComputeAtomicMin) {}

    fun visitComputeAtomicMax(instruction: ComputeAtomicMax) {}

    fun visitComputeAtomicCAS(instruction: ComputeAtomicCAS) {}

    fun visitWarpShuffle(instruction: WarpShuffle) {}

    fun visitWarpShuffleDown(instruction: WarpShuffleDown) {}

    fun visitWarpShuffleUp(instruction: WarpShuffleUp) {}

    fun visitWarpShuffleXor(instruction: WarpShuffleXor) {}

    fun visitBallot(instruction: Ballot) {}

    fun visitWarpVote(instruction: WarpVote) {}

    fun visitSharedMemAlloc(instruction: SharedMemAlloc) {}

    fun visitDivergentBranch(instruction: DivergentBranch) {}
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.ThreadDim
import org.kgen.ir.ComputeScope
import org.kgen.ir.MemorySpace
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.VoteOp

/**
 * Emission interface for compute instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ComputeInstructionSet : InstructionSet {

    /**
     * Returns the thread ID within the current block along the specified dimension.
     *
     * Emits a [ThreadId] instruction into the current block.
     *
     * @param dim ThreadDim
     * @return the SSA value produced by this instruction
     */
    fun threadId(dim: ThreadDim): Value

    /**
     * Returns the block ID within the grid along the specified dimension.
     *
     * Emits a [BlockId] instruction into the current block.
     *
     * @param dim ThreadDim
     * @return the SSA value produced by this instruction
     */
    fun blockId(dim: ThreadDim): Value

    /**
     * Returns the warp/wavefront ID within the current block.
     *
     * Emits a [WarpId] instruction into the current block.
     * @return the SSA value produced by this instruction
     */
    fun warpId(): Value

    /**
     * Returns the lane ID within the current warp/wavefront.
     *
     * Emits a [LaneId] instruction into the current block.
     * @return the SSA value produced by this instruction
     */
    fun laneId(): Value

    /**
     * Returns the number of threads per block in the specified dimension.
     *
     * Emits a [BlockDim] instruction into the current block.
     *
     * @param dim ThreadDim
     * @return the SSA value produced by this instruction
     */
    fun blockDim(dim: ThreadDim): Value

    /**
     * Returns the number of blocks in the grid in the specified dimension.
     *
     * Emits a [GridDim] instruction into the current block.
     *
     * @param dim ThreadDim
     * @return the SSA value produced by this instruction
     */
    fun gridDim(dim: ThreadDim): Value

    /**
     * GPU-native atomic add on global or shared memory.
     *
     * Emits a [ComputeAtomicAdd] instruction into the current block.
     *
     * @param ptr source operand
     * @param value source operand
     * @param ordering the memory ordering for this atomic operation
     * @param memSpace MemorySpace
     * @return the SSA value produced by this instruction
     */
    fun computeAtomicAdd(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value

    /**
     * GPU-native atomic min on global or shared memory.
     *
     * Emits a [ComputeAtomicMin] instruction into the current block.
     *
     * @param ptr source operand
     * @param value source operand
     * @param ordering the memory ordering for this atomic operation
     * @param memSpace MemorySpace
     * @return the SSA value produced by this instruction
     */
    fun computeAtomicMin(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value

    /**
     * GPU-native atomic max on global or shared memory.
     *
     * Emits a [ComputeAtomicMax] instruction into the current block.
     *
     * @param ptr source operand
     * @param value source operand
     * @param ordering the memory ordering for this atomic operation
     * @param memSpace MemorySpace
     * @return the SSA value produced by this instruction
     */
    fun computeAtomicMax(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value

    /**
     * GPU-native atomic compare-and-swap on global or shared memory.
     *
     * Emits a [ComputeAtomicCAS] instruction into the current block.
     *
     * @param ptr source operand
     * @param cmp source operand
     * @param new source operand
     * @param ordering the memory ordering for this atomic operation
     * @param memSpace MemorySpace
     * @return the SSA value produced by this instruction
     */
    fun computeAtomicCAS(ptr: Value, cmp: Value, new: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value

    /**
     * Warp-level data exchange: read value from an arbitrary source lane.
     *
     * Emits a [WarpShuffle] instruction into the current block.
     *
     * @param value source operand
     * @param srcLane source operand
     * @param width Int
     * @return the SSA value produced by this instruction
     */
    fun warpShuffle(value: Value, srcLane: Value, width: Int = 32): Value

    /**
     * Warp shuffle: read from lane + delta.
     *
     * Emits a [WarpShuffleDown] instruction into the current block.
     *
     * @param value source operand
     * @param delta source operand
     * @param width Int
     * @return the SSA value produced by this instruction
     */
    fun warpShuffleDown(value: Value, delta: Value, width: Int = 32): Value

    /**
     * Warp shuffle: read from lane - delta.
     *
     * Emits a [WarpShuffleUp] instruction into the current block.
     *
     * @param value source operand
     * @param delta source operand
     * @param width Int
     * @return the SSA value produced by this instruction
     */
    fun warpShuffleUp(value: Value, delta: Value, width: Int = 32): Value

    /**
     * Warp shuffle: read from lane XOR mask.
     *
     * Emits a [WarpShuffleXor] instruction into the current block.
     *
     * @param value source operand
     * @param mask source operand
     * @param width Int
     * @return the SSA value produced by this instruction
     */
    fun warpShuffleXor(value: Value, mask: Value, width: Int = 32): Value

    /**
     * Returns a bitmask of lanes where the predicate is true.
     *
     * Emits a [Ballot] instruction into the current block.
     *
     * @param predicate source operand
     * @return the SSA value produced by this instruction
     */
    fun ballot(predicate: Value): Value

    /**
     * Lane-predicate reduction: All (all lanes true?) or Any (any lane true?).
     *
     * Emits a [WarpVote] instruction into the current block.
     *
     * @param predicate source operand
     * @param op VoteOp
     * @return the SSA value produced by this instruction
     */
    fun warpVote(predicate: Value, op: VoteOp): Value

    /**
     * Allocates shared (workgroup-local) memory.

The memory is shared among all threads in the block.
     *
     * Emits a [SharedMemAlloc] instruction into the current block.
     *
     * @param size source operand
     * @param alignment memory alignment in bytes
     * @return the SSA value produced by this instruction
     */
    fun sharedMemAlloc(size: Value, alignment: Int): Value

    /**
     * Synchronizes all threads within the specified scope.

All threads must reach the barrier before any can proceed.
     *
     * Emits a [ComputeBarrier] instruction into the current block.
     *
     * @param scope ComputeScope
     */
    fun computeBarrier(scope: ComputeScope): Unit

    /**
     * Memory ordering fence within compute hierarchy.

Ensures memory operations are visible to threads at the specified scope.
     *
     * Emits a [ComputeFence] instruction into the current block.
     *
     * @param scope ComputeScope
     * @param memSpace MemorySpace
     */
    fun computeFence(scope: ComputeScope, memSpace: MemorySpace): Unit

    /**
     * Launches a compute kernel asynchronously.

Host-side instruction. The kernel function must have KernelEntry attribute.
The stream operand is opaque to the IR.
     *
     * Emits a [KernelLaunch] instruction into the current block.
     *
     * @param fn source operand
     * @param grid source operand
     * @param block source operand
     * @param sharedMem source operand
     * @param stream source operand
     * @param args list of source operands
     */
    fun kernelLaunch(fn: Value, grid: Value, block: Value, sharedMem: Value, stream: Value, args: List<Value>): Unit

    /**
     * Branch with expected lane divergence.

Signals to the backend that different lanes may take different paths.
May be lowered to predicated instructions.
     *
     * Emits a [DivergentBranch] instruction into the current block.
     *
     * @param condition source operand
     * @param trueTarget BlockRef
     * @param falseTarget BlockRef
     */
    fun divergentBranch(condition: Value, trueTarget: BlockRef, falseTarget: BlockRef): Unit
}

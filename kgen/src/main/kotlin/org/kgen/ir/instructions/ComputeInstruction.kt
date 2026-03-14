// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.ThreadDim
import org.kgen.ir.ComputeScope
import org.kgen.ir.MemorySpace
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.VoteOp

/**
 * GPU and general-purpose compute instructions.
 *
 * Compute instructions represent operations specific to massively parallel
 * execution contexts: thread/block identification, synchronization barriers,
 * warp-level shuffles and votes, shared memory allocation, and kernel launch.
 */
sealed interface ComputeInstruction : Instruction {
    override val category get() = IrCategory.COMPUTE
}

// --- Thread identification ---

/**
 * Returns the thread ID within the current block along the specified dimension.
 *
 * @param dest the SSA result reference
 * @param dim configuration
 */
data class ThreadId(
    val dest: InstructionRef,
    val dim: ThreadDim,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

/**
 * Returns the block ID within the grid along the specified dimension.
 *
 * @param dest the SSA result reference
 * @param dim configuration
 */
data class BlockId(
    val dest: InstructionRef,
    val dim: ThreadDim,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

/**
 * Returns the warp/wavefront ID within the current block.
 *
 * @param dest the SSA result reference
 */
data class WarpId(
    val dest: InstructionRef,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

/**
 * Returns the lane ID within the current warp/wavefront.
 *
 * @param dest the SSA result reference
 */
data class LaneId(
    val dest: InstructionRef,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

// --- Grid dimensions ---

/**
 * Returns the number of threads per block in the specified dimension.
 *
 * @param dest the SSA result reference
 * @param dim configuration
 */
data class BlockDim(
    val dest: InstructionRef,
    val dim: ThreadDim,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

/**
 * Returns the number of blocks in the grid in the specified dimension.
 *
 * @param dest the SSA result reference
 * @param dim configuration
 */
data class GridDim(
    val dest: InstructionRef,
    val dim: ThreadDim,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

// --- Synchronization ---

/**
 * Synchronizes all threads within the specified scope.
 *
 * All threads must reach the barrier before any can proceed.
 *
 * @param scope configuration
 */
data class ComputeBarrier(
    val scope: ComputeScope,
) : ComputeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = emptyList<Value>()
}

/**
 * Memory ordering fence within compute hierarchy.
 *
 * Ensures memory operations are visible to threads at the specified scope.
 *
 * @param scope configuration
 * @param memSpace configuration
 */
data class ComputeFence(
    val scope: ComputeScope,
    val memSpace: MemorySpace,
) : ComputeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = emptyList<Value>()
}

// --- Kernel launch ---

/**
 * Launches a compute kernel asynchronously.
 *
 * Host-side instruction. The kernel function must have KernelEntry attribute.
 * The stream operand is opaque to the IR.
 *
 * @param fn operand value
 * @param grid operand value
 * @param block operand value
 * @param sharedMem operand value
 * @param stream operand value
 * @param args list of operand values
 */
data class KernelLaunch(
    val fn: Value,
    val grid: Value,
    val block: Value,
    val sharedMem: Value,
    val stream: Value,
    val args: List<Value>,
) : ComputeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(fn, grid, block, sharedMem, stream) + args
}

// --- Compute atomics ---

/**
 * GPU-native atomic add on global or shared memory.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param value operand value
 * @param ordering configuration
 * @param memSpace configuration
 */
data class ComputeAtomicAdd(
    val dest: InstructionRef,
    val ptr: Value,
    val value: Value,
    val ordering: AtomicOrdering,
    val memSpace: MemorySpace,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr, value)
}

/**
 * GPU-native atomic min on global or shared memory.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param value operand value
 * @param ordering configuration
 * @param memSpace configuration
 */
data class ComputeAtomicMin(
    val dest: InstructionRef,
    val ptr: Value,
    val value: Value,
    val ordering: AtomicOrdering,
    val memSpace: MemorySpace,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr, value)
}

/**
 * GPU-native atomic max on global or shared memory.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param value operand value
 * @param ordering configuration
 * @param memSpace configuration
 */
data class ComputeAtomicMax(
    val dest: InstructionRef,
    val ptr: Value,
    val value: Value,
    val ordering: AtomicOrdering,
    val memSpace: MemorySpace,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr, value)
}

/**
 * GPU-native atomic compare-and-swap on global or shared memory.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param cmp operand value
 * @param new operand value
 * @param ordering configuration
 * @param memSpace configuration
 */
data class ComputeAtomicCAS(
    val dest: InstructionRef,
    val ptr: Value,
    val cmp: Value,
    val new: Value,
    val ordering: AtomicOrdering,
    val memSpace: MemorySpace,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr, cmp, new)
}

// --- Warp shuffle ---

/**
 * Warp-level data exchange: read value from an arbitrary source lane.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param srcLane operand value
 * @param width configuration
 */
data class WarpShuffle(
    val dest: InstructionRef,
    val value: Value,
    val srcLane: Value,
    val width: Int = 32,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, srcLane)
}

/**
 * Warp shuffle: read from lane + delta.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param delta operand value
 * @param width configuration
 */
data class WarpShuffleDown(
    val dest: InstructionRef,
    val value: Value,
    val delta: Value,
    val width: Int = 32,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, delta)
}

/**
 * Warp shuffle: read from lane - delta.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param delta operand value
 * @param width configuration
 */
data class WarpShuffleUp(
    val dest: InstructionRef,
    val value: Value,
    val delta: Value,
    val width: Int = 32,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, delta)
}

/**
 * Warp shuffle: read from lane XOR mask.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param mask operand value
 * @param width configuration
 */
data class WarpShuffleXor(
    val dest: InstructionRef,
    val value: Value,
    val mask: Value,
    val width: Int = 32,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value, mask)
}

// --- Warp vote ---

/**
 * Returns a bitmask of lanes where the predicate is true.
 *
 * @param dest the SSA result reference
 * @param predicate operand value
 */
data class Ballot(
    val dest: InstructionRef,
    val predicate: Value,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(predicate)
}

/**
 * Lane-predicate reduction: All (all lanes true?) or Any (any lane true?).
 *
 * @param dest the SSA result reference
 * @param predicate operand value
 * @param op configuration
 */
data class WarpVote(
    val dest: InstructionRef,
    val predicate: Value,
    val op: VoteOp,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(predicate)
}

// --- Shared memory ---

/**
 * Allocates shared (workgroup-local) memory.
 *
 * The memory is shared among all threads in the block.
 *
 * @param dest the SSA result reference
 * @param size operand value
 * @param alignment configuration
 */
data class SharedMemAlloc(
    val dest: InstructionRef,
    val size: Value,
    val alignment: Int,
) : ComputeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(size)
}

// --- Divergence ---

/**
 * Branch with expected lane divergence.
 *
 * Signals to the backend that different lanes may take different paths.
 * May be lowered to predicated instructions.
 *
 * @param condition operand value
 * @param trueTarget configuration
 * @param falseTarget configuration
 */
data class DivergentBranch(
    val condition: Value,
    val trueTarget: BlockRef,
    val falseTarget: BlockRef,
) : ComputeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.IS_BRANCH or InstructionEffects.IS_DIVERGENT or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(condition)
}


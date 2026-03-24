// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.ThreadDim
import org.kgen.ir.ComputeScope
import org.kgen.ir.MemorySpace
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.VoteOp

/**
 * Default implementation of [ComputeInstructionSet] backed by an [InstructionSink].
 */
internal class ComputeInstructionSetImpl(private val sink: InstructionSink) : ComputeInstructionSet {

    override fun threadId(dim: ThreadDim): Value =
        sink.nextRef(Type.I32).also { sink.emit(ThreadId(it, dim)) }

    override fun blockId(dim: ThreadDim): Value =
        sink.nextRef(Type.I32).also { sink.emit(BlockId(it, dim)) }

    override fun warpId(): Value =
        sink.nextRef(Type.I32).also { sink.emit(WarpId(it)) }

    override fun laneId(): Value =
        sink.nextRef(Type.I32).also { sink.emit(LaneId(it)) }

    override fun blockDim(dim: ThreadDim): Value =
        sink.nextRef(Type.I32).also { sink.emit(BlockDim(it, dim)) }

    override fun gridDim(dim: ThreadDim): Value =
        sink.nextRef(Type.I32).also { sink.emit(GridDim(it, dim)) }

    override fun computeBarrier(scope: ComputeScope) {
        sink.emit(ComputeBarrier(scope))
    }

    override fun computeFence(scope: ComputeScope, memSpace: MemorySpace) {
        sink.emit(ComputeFence(scope, memSpace))
    }

    override fun kernelLaunch(fn: Value, grid: Value, block: Value, sharedMem: Value, stream: Value, args: List<Value>) {
        sink.emit(KernelLaunch(fn, grid, block, sharedMem, stream, args))
    }

    override fun computeAtomicAdd(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value =
        sink.nextRef(value.type).also { sink.emit(ComputeAtomicAdd(it, ptr, value, ordering, memSpace)) }

    override fun computeAtomicMin(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value =
        sink.nextRef(value.type).also { sink.emit(ComputeAtomicMin(it, ptr, value, ordering, memSpace)) }

    override fun computeAtomicMax(ptr: Value, value: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value =
        sink.nextRef(value.type).also { sink.emit(ComputeAtomicMax(it, ptr, value, ordering, memSpace)) }

    override fun computeAtomicCAS(ptr: Value, cmp: Value, new: Value, ordering: AtomicOrdering, memSpace: MemorySpace): Value =
        sink.nextRef(Type.I32).also { sink.emit(ComputeAtomicCAS(it, ptr, cmp, new, ordering, memSpace)) }

    override fun warpShuffle(value: Value, srcLane: Value, width: Int): Value =
        sink.nextRef(value.type).also { sink.emit(WarpShuffle(it, value, srcLane, width)) }

    override fun warpShuffleDown(value: Value, delta: Value, width: Int): Value =
        sink.nextRef(value.type).also { sink.emit(WarpShuffleDown(it, value, delta, width)) }

    override fun warpShuffleUp(value: Value, delta: Value, width: Int): Value =
        sink.nextRef(value.type).also { sink.emit(WarpShuffleUp(it, value, delta, width)) }

    override fun warpShuffleXor(value: Value, mask: Value, width: Int): Value =
        sink.nextRef(value.type).also { sink.emit(WarpShuffleXor(it, value, mask, width)) }

    override fun ballot(predicate: Value): Value =
        sink.nextRef(Type.I32).also { sink.emit(Ballot(it, predicate)) }

    override fun warpVote(predicate: Value, op: VoteOp): Value =
        sink.nextRef(Type.I1).also { sink.emit(WarpVote(it, predicate, op)) }

    override fun sharedMemAlloc(size: Value, alignment: Int): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(SharedMemAlloc(it, size, alignment)) }

    override fun divergentBranch(condition: Value, trueTarget: BlockRef, falseTarget: BlockRef) {
        sink.emit(DivergentBranch(condition, trueTarget, falseTarget))
    }
}

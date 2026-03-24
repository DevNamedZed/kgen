// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.AtomicRMWOp

/**
 * Default implementation of [AtomicInstructionSet] backed by an [InstructionSink].
 */
internal class AtomicInstructionSetImpl(private val sink: InstructionSink) : AtomicInstructionSet {

    override fun fence(ordering: AtomicOrdering, syncScope: String?) {
        sink.emit(Fence(ordering, syncScope))
    }

    override fun cmpXchg(ptr: Value, cmp: Value, new: Value, successOrdering: AtomicOrdering, failureOrdering: AtomicOrdering, weak: Boolean, volatile: Boolean): Value =
        sink.nextRef(Type.I32).also { sink.emit(CmpXchg(it, ptr, cmp, new, successOrdering, failureOrdering, weak, volatile)) }

    override fun atomicRMW(op: AtomicRMWOp, ptr: Value, value: Value, ordering: AtomicOrdering, volatile: Boolean): Value =
        sink.nextRef(value.type).also { sink.emit(AtomicRMW(it, op, ptr, value, ordering, volatile)) }
}

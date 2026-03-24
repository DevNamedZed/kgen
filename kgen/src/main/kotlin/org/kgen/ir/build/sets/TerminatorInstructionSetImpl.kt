// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.Constant

/**
 * Default implementation of [TerminatorInstructionSet] backed by an [InstructionSink].
 */
internal class TerminatorInstructionSetImpl(private val sink: InstructionSink) : TerminatorInstructionSet {

    override fun ret(value: Value?) {
        sink.emit(Ret(value))
    }

    override fun br(target: BlockRef) {
        sink.emit(Br(target))
    }

    override fun condBr(condition: Value, trueTarget: BlockRef, falseTarget: BlockRef, trueWeight: Long, falseWeight: Long) {
        sink.emit(CondBr(condition, trueTarget, falseTarget, trueWeight, falseWeight))
    }

    override fun switch(value: Value, defaultTarget: BlockRef, cases: List<Pair<Constant, BlockRef>>) {
        sink.emit(Switch(value, defaultTarget, cases))
    }

    override fun indirectBr(address: Value, targets: List<BlockRef>) {
        sink.emit(IndirectBr(address, targets))
    }

    override fun unreachable() {
        sink.emit(Unreachable())
    }

    override fun trap() {
        sink.emit(Trap())
    }

    override fun debugTrap(successor: BlockRef?) {
        sink.emit(DebugTrap(successor))
    }
}

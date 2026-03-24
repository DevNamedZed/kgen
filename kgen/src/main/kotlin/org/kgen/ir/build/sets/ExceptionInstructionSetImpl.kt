// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.LandingPadClause
import org.kgen.ir.Type

/**
 * Default implementation of [ExceptionInstructionSet] backed by an [InstructionSink].
 */
internal class ExceptionInstructionSetImpl(private val sink: InstructionSink) : ExceptionInstructionSet {

    override fun landingPad(resultType: Type, clauses: List<LandingPadClause>, cleanup: Boolean): Value =
        sink.nextRef(resultType).also { sink.emit(LandingPad(it, resultType, clauses, cleanup)) }

    override fun resume(value: Value) {
        sink.emit(Resume(value))
    }

    override fun catchSwitch(parentPad: Value?, handlers: List<BlockRef>, unwindDest: BlockRef?): Value =
        sink.nextRef(Type.Token).also { sink.emit(CatchSwitch(it, parentPad, handlers, unwindDest)) }

    override fun catchPad(catchSwitch: Value, args: List<Value>): Value =
        sink.nextRef(Type.Token).also { sink.emit(CatchPad(it, catchSwitch, args)) }

    override fun cleanupPad(parentPad: Value?, args: List<Value>): Value =
        sink.nextRef(Type.Token).also { sink.emit(CleanupPad(it, parentPad, args)) }

    override fun catchRet(catchPad: Value, dest: BlockRef) {
        sink.emit(CatchRet(catchPad, dest))
    }

    override fun cleanupRet(cleanupPad: Value, unwindDest: BlockRef?) {
        sink.emit(CleanupRet(cleanupPad, unwindDest))
    }
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.types.MethodRef
import org.kgen.ir.MonitorId
import org.kgen.ir.VirtualObjectState
import org.kgen.ir.DeoptReason
import org.kgen.ir.DeoptAction
import org.kgen.ir.SpeculationId

/**
 * Default implementation of [DeoptimizationInstructionSet] backed by an [InstructionSink].
 */
internal class DeoptimizationInstructionSetImpl(private val sink: InstructionSink) : DeoptimizationInstructionSet {

    override fun frameState(method: MethodRef, bci: Int, locals: List<Value>, stack: List<Value>, locks: List<MonitorId>, outer: FrameState?, virtualObjects: List<VirtualObjectState>) {
        sink.emit(FrameState(method, bci, locals, stack, locks, outer, virtualObjects))
    }

    override fun guard(condition: Value, negated: Boolean, reason: DeoptReason, action: DeoptAction, speculation: SpeculationId?, frameState: Value) {
        sink.emit(Guard(condition, negated, reason, action, speculation, frameState))
    }

    override fun fixedGuard(condition: Value, negated: Boolean, reason: DeoptReason, action: DeoptAction, frameState: Value) {
        sink.emit(FixedGuard(condition, negated, reason, action, frameState))
    }

    override fun deoptimize(reason: DeoptReason, action: DeoptAction, speculation: SpeculationId?, frameState: Value) {
        sink.emit(Deoptimize(reason, action, speculation, frameState))
    }

    override fun oSREntry(targetBci: Int, locals: List<Value>, frameState: Value) {
        sink.emit(OSREntry(targetBci, locals, frameState))
    }
}

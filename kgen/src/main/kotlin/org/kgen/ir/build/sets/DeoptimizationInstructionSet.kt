// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.MethodRef
import org.kgen.ir.MonitorId
import org.kgen.ir.VirtualObjectState
import org.kgen.ir.DeoptReason
import org.kgen.ir.DeoptAction
import org.kgen.ir.SpeculationId

/**
 * Emission interface for deoptimization instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface DeoptimizationInstructionSet : InstructionSet {

    /**
     * Pseudo-instruction capturing interpreter state for deoptimization.

Not emitted as machine code. Must exist for every instruction with
mayDeopt=true or isSafepoint=true in managed compilation contexts.
     *
     * Emits a [FrameState] instruction into the current block.
     *
     * @param method MethodRef
     * @param bci Int
     * @param locals list of source operands
     * @param stack list of source operands
     * @param locks List<MonitorId>
     * @param outer FrameState
     * @param virtualObjects List<VirtualObjectState>
     */
    fun frameState(method: MethodRef, bci: Int, locals: List<Value>, stack: List<Value>, locks: List<MonitorId>, outer: FrameState?, virtualObjects: List<VirtualObjectState>): Unit

    /**
     * Floating conditional deoptimization.

Deoptimize if condition is false (or true if negated). May float
freely between its anchor and the first use of any value it protects.
     *
     * Emits a [Guard] instruction into the current block.
     *
     * @param condition source operand
     * @param negated Boolean
     * @param reason DeoptReason
     * @param action DeoptAction
     * @param speculation SpeculationId
     * @param frameState source operand
     */
    fun guard(condition: Value, negated: Boolean = false, reason: DeoptReason, action: DeoptAction, speculation: SpeculationId? = null, frameState: Value): Unit

    /**
     * Control-flow-fixed guard.

Unlike Guard, FixedGuard is structurally fixed in the control flow
and cannot float. Must be lowered to an explicit conditional branch.
     *
     * Emits a [FixedGuard] instruction into the current block.
     *
     * @param condition source operand
     * @param negated Boolean
     * @param reason DeoptReason
     * @param action DeoptAction
     * @param frameState source operand
     */
    fun fixedGuard(condition: Value, negated: Boolean = false, reason: DeoptReason, action: DeoptAction, frameState: Value): Unit

    /**
     * Unconditional deoptimization terminator.

Must be the last instruction in a basic block. Immediately triggers
deoptimization with the attached frame state.
     *
     * Emits a [Deoptimize] instruction into the current block.
     *
     * @param reason DeoptReason
     * @param action DeoptAction
     * @param speculation SpeculationId
     * @param frameState source operand
     */
    fun deoptimize(reason: DeoptReason, action: DeoptAction, speculation: SpeculationId? = null, frameState: Value): Unit

    /**
     * On-stack replacement entry at a loop header.

Used to transition from the interpreter to compiled code at a safe
point. The localMappings provide SSA values corresponding to
interpreter locals at the OSR point.
     *
     * Emits a [OSREntry] instruction into the current block.
     *
     * @param targetBci Int
     * @param locals list of source operands
     * @param frameState source operand
     */
    fun oSREntry(targetBci: Int, locals: List<Value>, frameState: Value): Unit
}

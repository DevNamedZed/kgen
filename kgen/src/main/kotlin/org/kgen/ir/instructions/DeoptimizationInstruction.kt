// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.types.MethodRef
import org.kgen.ir.MonitorId
import org.kgen.ir.VirtualObjectState
import org.kgen.ir.DeoptReason
import org.kgen.ir.DeoptAction
import org.kgen.ir.SpeculationId

/**
 * Speculative optimization and deoptimization instructions.
 *
 * Deoptimization instructions support JIT compilation with speculative
 * optimization. FrameState captures interpreter state; Guard and FixedGuard
 * provide conditional deoptimization; Deoptimize is unconditional.
 * OSREntry enables on-stack replacement at loop headers.
 */
sealed interface DeoptimizationInstruction : Instruction {
    override val category get() = IrCategory.DEOPTIMIZATION
}

// --- Frame state ---

/**
 * Pseudo-instruction capturing interpreter state for deoptimization.
 *
 * Not emitted as machine code. Must exist for every instruction with
 * mayDeopt=true or isSafepoint=true in managed compilation contexts.
 *
 * @param method configuration
 * @param bci configuration
 * @param locals list of operand values
 * @param stack list of operand values
 * @param locks configuration
 * @param outer configuration
 * @param virtualObjects configuration
 */
data class FrameState(
    val method: MethodRef,
    val bci: Int,
    val locals: List<Value>,
    val stack: List<Value>,
    val locks: List<MonitorId>,
    val outer: FrameState?,
    val virtualObjects: List<VirtualObjectState>,
) : DeoptimizationInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.PURE
    override val operands get() = locals + stack
}

// --- Guards ---

/**
 * Floating conditional deoptimization.
 *
 * Deoptimize if condition is false (or true if negated). May float
 * freely between its anchor and the first use of any value it protects.
 *
 * @param condition operand value
 * @param negated flag (default: false)
 * @param reason configuration
 * @param action configuration
 * @param speculation configuration
 * @param frameState operand value
 */
data class Guard(
    val condition: Value,
    val negated: Boolean = false,
    val reason: DeoptReason,
    val action: DeoptAction,
    val speculation: SpeculationId? = null,
    val frameState: Value,
) : DeoptimizationInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(condition, frameState)
}

/**
 * Control-flow-fixed guard.
 *
 * Unlike Guard, FixedGuard is structurally fixed in the control flow
 * and cannot float. Must be lowered to an explicit conditional branch.
 *
 * @param condition operand value
 * @param negated flag (default: false)
 * @param reason configuration
 * @param action configuration
 * @param frameState operand value
 */
data class FixedGuard(
    val condition: Value,
    val negated: Boolean = false,
    val reason: DeoptReason,
    val action: DeoptAction,
    val frameState: Value,
) : DeoptimizationInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(condition, frameState)
}

// --- Deoptimization ---

/**
 * Unconditional deoptimization terminator.
 *
 * Must be the last instruction in a basic block. Immediately triggers
 * deoptimization with the attached frame state.
 *
 * @param reason configuration
 * @param action configuration
 * @param speculation configuration
 * @param frameState operand value
 */
data class Deoptimize(
    val reason: DeoptReason,
    val action: DeoptAction,
    val speculation: SpeculationId? = null,
    val frameState: Value,
) : DeoptimizationInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(frameState)
}

/**
 * On-stack replacement entry at a loop header.
 *
 * Used to transition from the interpreter to compiled code at a safe
 * point. The localMappings provide SSA values corresponding to
 * interpreter locals at the OSR point.
 *
 * @param targetBci configuration
 * @param locals list of operand values
 * @param frameState operand value
 */
data class OSREntry(
    val targetBci: Int,
    val locals: List<Value>,
    val frameState: Value,
) : DeoptimizationInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(frameState) + locals
}


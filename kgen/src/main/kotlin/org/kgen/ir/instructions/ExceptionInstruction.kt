// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.LandingPadClause
import org.kgen.ir.Type

/**
 * Exception handling instructions.
 *
 * Exception instructions implement structured exception handling in the IR. They model
 * landing pads for catching exceptions, resume for re-throwing, and the Windows-style
 * catch/cleanup pad mechanism.
 */
sealed interface ExceptionInstruction : Instruction {
    override val category get() = IrCategory.EXCEPTION
}

// --- Landing pad and resume ---

/**
 * Exception landing pad: receives control when an Invoke unwinds.
 *
 * @param dest the SSA result reference
 * @param resultType configuration
 * @param clauses configuration
 * @param cleanup flag (default: false)
 */
data class LandingPad(
    val dest: InstructionRef,
    val resultType: Type,
    val clauses: List<LandingPadClause>,
    val cleanup: Boolean = false,
) : ExceptionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = emptyList<Value>()
}

/**
 * Resume unwinding after a landing pad: re-throws the exception.
 *
 * @param value operand value
 */
data class Resume(
    val value: Value,
) : ExceptionInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.CAN_THROW or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(value)
}

// --- Windows-style exception handling ---

/**
 * Windows-style catch dispatch: selects a handler from a list of catch pads.
 *
 * @param dest the SSA result reference
 * @param parentPad optional operand value
 * @param handlers configuration
 * @param unwindDest configuration
 */
data class CatchSwitch(
    val dest: InstructionRef,
    val parentPad: Value?,
    val handlers: List<BlockRef>,
    val unwindDest: BlockRef? = null,
) : ExceptionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOfNotNull(parentPad)
}

/**
 * Windows-style catch pad: begins a catch handler within a CatchSwitch.
 *
 * @param dest the SSA result reference
 * @param catchSwitch operand value
 * @param args list of operand values
 */
data class CatchPad(
    val dest: InstructionRef,
    val catchSwitch: Value,
    val args: List<Value>,
) : ExceptionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(catchSwitch) + args
}

/**
 * Windows-style cleanup pad: begins a cleanup handler during unwinding.
 *
 * @param dest the SSA result reference
 * @param parentPad optional operand value
 * @param args list of operand values
 */
data class CleanupPad(
    val dest: InstructionRef,
    val parentPad: Value?,
    val args: List<Value>,
) : ExceptionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOfNotNull(parentPad) + args
}

/**
 * Return from a catch handler.
 *
 * @param catchPad operand value
 * @param dest configuration
 */
data class CatchRet(
    val catchPad: Value,
    val dest: BlockRef,
) : ExceptionInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.IS_BRANCH or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(catchPad)
}

/**
 * Return from a cleanup handler: continues unwinding.
 *
 * @param cleanupPad operand value
 * @param unwindDest configuration
 */
data class CleanupRet(
    val cleanupPad: Value,
    val unwindDest: BlockRef? = null,
) : ExceptionInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.IS_TERMINATOR or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(cleanupPad)
}


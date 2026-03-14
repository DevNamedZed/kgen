// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.AsmDialect
import org.kgen.ir.Type

/**
 * Target-specific intrinsics and inline assembly instructions.
 *
 * Intrinsic instructions provide access to platform capabilities that cannot be
 * expressed through the general IR.
 */
sealed interface IntrinsicInstruction : Instruction {
    override val category get() = IrCategory.INTRINSIC
}

// --- Intrinsic instructions ---

/**
 * Calls a named intrinsic function with the given arguments.
 *
 * @param dest the SSA result reference, or null for void
 * @param name configuration
 * @param args list of operand values
 * @param returnType configuration
 */
data class Intrinsic(
    val dest: InstructionRef?,
    val name: String,
    val args: List<Value>,
    val returnType: Type,
) : IntrinsicInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = args
}

/**
 * Embeds raw assembly code within the IR instruction stream.
 *
 * @param dest the SSA result reference, or null for void
 * @param assembly configuration
 * @param constraints configuration
 * @param sideEffects flag (default: true)
 * @param alignStack flag (default: false)
 * @param dialect configuration
 * @param args list of operand values
 * @param returnType configuration
 */
data class InlineAsm(
    val dest: InstructionRef?,
    val assembly: String,
    val constraints: String,
    val sideEffects: Boolean = true,
    val alignStack: Boolean = false,
    val dialect: AsmDialect = AsmDialect.ATT,
    val args: List<Value> = emptyList(),
    val returnType: Type = Type.Void,
) : IntrinsicInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = args
}


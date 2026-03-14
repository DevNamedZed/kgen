// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.CallingConvention
import org.kgen.ir.FnAttribute
import org.kgen.ir.TailCallKind
import org.kgen.ir.Type

/**
 * Function invocation instructions.
 *
 * Call instructions transfer control to a target function with arguments
 * and optionally produce a return value.
 */
sealed interface CallInstruction : Instruction {
    override val category get() = IrCategory.CALL
    val function: Value
    val args: List<Value>
    val returnType: Type
}

// --- Call instructions ---

/**
 * Direct or indirect function call.
 *
 * @param dest the SSA result reference, or null for void
 * @param function operand value
 * @param args list of operand values
 * @param returnType configuration
 * @param callingConv configuration
 * @param tailCall configuration
 * @param attributes configuration
 */
data class Call(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val callingConv: CallingConvention = CallingConvention.C,
    val tailCall: TailCallKind = TailCallKind.NONE,
    val attributes: Set<FnAttribute> = emptySet(),
) : CallInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(function) + args
}

/**
 * Function call with exception handling.
 *
 * @param dest the SSA result reference, or null for void
 * @param function operand value
 * @param args list of operand values
 * @param returnType configuration
 * @param normalDest configuration
 * @param unwindDest configuration
 * @param callingConv configuration
 */
data class Invoke(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val normalDest: BlockRef,
    val unwindDest: BlockRef,
    val callingConv: CallingConvention = CallingConvention.C,
) : CallInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.INVOKE
    override val operands get() = listOf(function) + args
}

/**
 * Inline assembly call with multiple successors.
 *
 * @param dest the SSA result reference, or null for void
 * @param function operand value
 * @param args list of operand values
 * @param returnType configuration
 * @param fallthrough configuration
 * @param indirectDests configuration
 */
data class CallBr(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val fallthrough: BlockRef,
    val indirectDests: List<BlockRef>,
) : CallInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.CALL.bits or InstructionEffects.IS_TERMINATOR)
    override val operands get() = listOf(function) + args
}


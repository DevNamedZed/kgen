// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.Constant

/**
 * Basic block terminator instructions.
 *
 * Every basic block must end with exactly one terminator instruction.
 */
sealed interface TerminatorInstruction : Instruction {
    override val category get() = IrCategory.TERMINATOR
}

// --- Terminator instructions ---

/**
 * Return from the current function.
 *
 * @param value optional operand value
 */
data class Ret(
    val value: Value?,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.RETURN
    override val operands get() = listOfNotNull(value)
}

/**
 * Unconditional branch: transfers control to the target block.
 *
 * @param target configuration
 */
data class Br(
    val target: BlockRef,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.BRANCH
    override val operands get() = emptyList<Value>()
}

/**
 * Conditional branch: branches based on a boolean condition.
 *
 * @param condition operand value
 * @param trueTarget configuration
 * @param falseTarget configuration
 * @param trueWeight flag (default: 0)
 * @param falseWeight flag (default: 0)
 */
data class CondBr(
    val condition: Value,
    val trueTarget: BlockRef,
    val falseTarget: BlockRef,
    val trueWeight: Long = 0,
    val falseWeight: Long = 0,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.CONDITIONAL_BRANCH
    override val operands get() = listOf(condition)
}

/**
 * Multi-way branch: switches on an integer value.
 *
 * @param value operand value
 * @param defaultTarget configuration
 * @param cases configuration
 */
data class Switch(
    val value: Value,
    val defaultTarget: BlockRef,
    val cases: List<Pair<Constant, BlockRef>>,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.CONDITIONAL_BRANCH
    override val operands get() = listOf(value)
}

/**
 * Indirect branch: transfers control to a computed address.
 *
 * @param address operand value
 * @param targets configuration
 */
data class IndirectBr(
    val address: Value,
    val targets: List<BlockRef>,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.CONDITIONAL_BRANCH
    override val operands get() = listOf(address)
}

/**
 * Marks a point that is statically known to be unreachable.
 *
 * If execution reaches this instruction, behavior is undefined.
 */
data class Unreachable(
    val dummy: Unit = Unit,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.TRAP
    override val operands get() = emptyList<Value>()
}

/**
 * Unconditional program abort: immediately terminates execution.
 *
 * Generates an illegal instruction or calls an abort intrinsic.
 */
data class Trap(
    val dummy: Unit = Unit,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.TRAP
    override val operands get() = emptyList<Value>()
}

/**
 * Debug breakpoint trap: triggers a debugger breakpoint.
 *
 * Generates a debug break instruction (e.g., `int3` on x86).
 *
 * @param successor configuration
 */
data class DebugTrap(
    val successor: BlockRef? = null,
) : TerminatorInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.TRAP
    override val operands get() = emptyList<Value>()
}


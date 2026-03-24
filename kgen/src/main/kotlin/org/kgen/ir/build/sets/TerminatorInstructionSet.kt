// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.Constant

/**
 * Emission interface for terminator instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface TerminatorInstructionSet : InstructionSet {

    /**
     * Return from the current function.
     *
     * Emits a [Ret] instruction into the current block.
     *
     * @param value optional source operand
     */
    fun ret(value: Value?): Unit

    /**
     * Unconditional branch: transfers control to the target block.
     *
     * Emits a [Br] instruction into the current block.
     *
     * @param target target basic block label
     */
    fun br(target: BlockRef): Unit
    fun br(target: String): Unit = br(BlockRef(target))

    /**
     * Conditional branch: branches based on a boolean condition.
     *
     * Emits a [CondBr] instruction into the current block.
     *
     * @param condition source operand
     * @param trueTarget BlockRef
     * @param falseTarget BlockRef
     * @param trueWeight Long
     * @param falseWeight Long
     */
    fun condBr(condition: Value, trueTarget: BlockRef, falseTarget: BlockRef, trueWeight: Long = 0, falseWeight: Long = 0): Unit
    fun condBr(condition: Value, trueTarget: String, falseTarget: String, trueWeight: Long = 0, falseWeight: Long = 0): Unit =
        condBr(condition, BlockRef(trueTarget), BlockRef(falseTarget), trueWeight, falseWeight)

    /**
     * Multi-way branch: switches on an integer value.
     *
     * Emits a [Switch] instruction into the current block.
     *
     * @param value source operand
     * @param defaultTarget BlockRef
     * @param cases List<Pair<Constant, BlockRef>>
     */
    fun switch(value: Value, defaultTarget: BlockRef, cases: List<Pair<Constant, BlockRef>>): Unit
    fun switch(value: Value, defaultTarget: String, cases: List<Pair<Constant, String>>): Unit =
        switch(value, BlockRef(defaultTarget), cases.map { it.first to BlockRef(it.second) })

    /**
     * Indirect branch: transfers control to a computed address.
     *
     * Emits a [IndirectBr] instruction into the current block.
     *
     * @param address source operand
     * @param targets List<BlockRef>
     */
    fun indirectBr(address: Value, targets: List<BlockRef>): Unit

    /**
     * Marks a point that is statically known to be unreachable.

If execution reaches this instruction, behavior is undefined.
     *
     * Emits a [Unreachable] instruction into the current block.
     */
    fun unreachable(): Unit

    /**
     * Unconditional program abort: immediately terminates execution.

Generates an illegal instruction or calls an abort intrinsic.
     *
     * Emits a [Trap] instruction into the current block.
     */
    fun trap(): Unit

    /**
     * Debug breakpoint trap: triggers a debugger breakpoint.

Generates a debug break instruction (e.g., `int3` on x86).
     *
     * Emits a [DebugTrap] instruction into the current block.
     *
     * @param successor BlockRef
     */
    fun debugTrap(successor: BlockRef? = null): Unit
    fun debugTrap(successor: String): Unit = debugTrap(BlockRef(successor))
}

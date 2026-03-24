// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.LandingPadClause
import org.kgen.ir.Type

/**
 * Emission interface for exception instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ExceptionInstructionSet : InstructionSet {

    /**
     * Exception landing pad: receives control when an Invoke unwinds.
     *
     * Emits a [LandingPad] instruction into the current block.
     *
     * @param resultType the type
     * @param clauses List<LandingPadClause>
     * @param cleanup Boolean
     * @return the SSA value produced by this instruction
     */
    fun landingPad(resultType: Type, clauses: List<LandingPadClause>, cleanup: Boolean = false): Value

    /**
     * Windows-style catch dispatch: selects a handler from a list of catch pads.
     *
     * Emits a [CatchSwitch] instruction into the current block.
     *
     * @param parentPad optional source operand
     * @param handlers List<BlockRef>
     * @param unwindDest BlockRef
     * @return the SSA value produced by this instruction
     */
    fun catchSwitch(parentPad: Value?, handlers: List<BlockRef>, unwindDest: BlockRef? = null): Value

    /**
     * Windows-style catch pad: begins a catch handler within a CatchSwitch.
     *
     * Emits a [CatchPad] instruction into the current block.
     *
     * @param catchSwitch source operand
     * @param args list of source operands
     * @return the SSA value produced by this instruction
     */
    fun catchPad(catchSwitch: Value, args: List<Value>): Value

    /**
     * Windows-style cleanup pad: begins a cleanup handler during unwinding.
     *
     * Emits a [CleanupPad] instruction into the current block.
     *
     * @param parentPad optional source operand
     * @param args list of source operands
     * @return the SSA value produced by this instruction
     */
    fun cleanupPad(parentPad: Value?, args: List<Value>): Value

    /**
     * Resume unwinding after a landing pad: re-throws the exception.
     *
     * Emits a [Resume] instruction into the current block.
     *
     * @param value source operand
     */
    fun resume(value: Value): Unit

    /**
     * Return from a catch handler.
     *
     * Emits a [CatchRet] instruction into the current block.
     *
     * @param catchPad source operand
     * @param dest BlockRef
     */
    fun catchRet(catchPad: Value, dest: BlockRef): Unit
    fun catchRet(catchPad: Value, dest: String): Unit = catchRet(catchPad, BlockRef(dest))

    /**
     * Return from a cleanup handler: continues unwinding.
     *
     * Emits a [CleanupRet] instruction into the current block.
     *
     * @param cleanupPad source operand
     * @param unwindDest BlockRef
     */
    fun cleanupRet(cleanupPad: Value, unwindDest: BlockRef? = null): Unit
    fun cleanupRet(cleanupPad: Value, unwindDest: String): Unit = cleanupRet(cleanupPad, BlockRef(unwindDest))
}

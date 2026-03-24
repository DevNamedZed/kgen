// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.CallingConvention
import org.kgen.ir.FnAttribute
import org.kgen.ir.TailCallKind
import org.kgen.ir.Type

/**
 * Emission interface for call instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface CallInstructionSet : InstructionSet {

    /**
     * Direct or indirect function call.
     *
     * Emits a [Call] instruction into the current block.
     *
     * @param function source operand
     * @param args list of source operands
     * @param returnType the type
     * @param callingConv CallingConvention
     * @param tailCall TailCallKind
     * @param attributes Set<FnAttribute>
     * @return the SSA value produced by this instruction, or null if void
     */
    fun call(function: Value, args: List<Value>, returnType: Type, callingConv: CallingConvention = CallingConvention.C, tailCall: TailCallKind = TailCallKind.NONE, attributes: Set<FnAttribute> = emptySet()): Value?

    /**
     * Function call with exception handling.
     *
     * Emits a [Invoke] instruction into the current block.
     *
     * @param function source operand
     * @param args list of source operands
     * @param returnType the type
     * @param normalDest BlockRef
     * @param unwindDest BlockRef
     * @param callingConv CallingConvention
     * @return the SSA value produced by this instruction, or null if void
     */
    fun invoke(function: Value, args: List<Value>, returnType: Type, normalDest: BlockRef, unwindDest: BlockRef, callingConv: CallingConvention = CallingConvention.C): Value?
    fun invoke(function: Value, args: List<Value>, returnType: Type, normalDest: String, unwindDest: String, callingConv: CallingConvention = CallingConvention.C): Value? =
        invoke(function, args, returnType, BlockRef(normalDest), BlockRef(unwindDest), callingConv)

    /**
     * Inline assembly call with multiple successors.
     *
     * Emits a [CallBr] instruction into the current block.
     *
     * @param function source operand
     * @param args list of source operands
     * @param returnType the type
     * @param fallthrough BlockRef
     * @param indirectDests List<BlockRef>
     * @return the SSA value produced by this instruction, or null if void
     */
    fun callBr(function: Value, args: List<Value>, returnType: Type, fallthrough: BlockRef, indirectDests: List<BlockRef>): Value?

    fun callBrByName(function: Value, args: List<Value>, returnType: Type, fallthrough: String, indirectDests: List<String>): Value? =
        callBr(function, args, returnType, BlockRef(fallthrough), indirectDests.map { BlockRef(it) })
}

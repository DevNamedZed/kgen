// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.AsmDialect
import org.kgen.ir.Type

/**
 * Emission interface for intrinsic instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface IntrinsicInstructionSet : InstructionSet {

    /**
     * Calls a named intrinsic function with the given arguments.
     *
     * Emits a [Intrinsic] instruction into the current block.
     *
     * @param name the name
     * @param args list of source operands
     * @param returnType the type
     * @return the SSA value produced by this instruction, or null if void
     */
    fun intrinsic(name: String, args: List<Value>, returnType: Type): Value?

    /**
     * Embeds raw assembly code within the IR instruction stream.
     *
     * Emits a [InlineAsm] instruction into the current block.
     *
     * @param assembly String
     * @param constraints String
     * @param sideEffects Boolean
     * @param alignStack Boolean
     * @param dialect AsmDialect
     * @param args list of source operands
     * @param returnType the type
     * @return the SSA value produced by this instruction, or null if void
     */
    fun inlineAsm(assembly: String, constraints: String, sideEffects: Boolean = true, alignStack: Boolean = false, dialect: AsmDialect = AsmDialect.ATT, args: List<Value> = emptyList(), returnType: Type = Type.Void): Value?
}

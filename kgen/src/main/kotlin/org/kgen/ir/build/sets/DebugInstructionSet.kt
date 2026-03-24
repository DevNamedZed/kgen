// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.Constant

/**
 * Emission interface for debug instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface DebugInstructionSet : InstructionSet {

    /**
     * Branch prediction hint: tells the optimizer that value is likely equal to expected.
     *
     * Emits a [Expect] instruction into the current block.
     *
     * @param value source operand
     * @param expected source operand
     * @return the SSA value produced by this instruction
     */
    fun expect(value: Value, expected: Constant): Value

    /**
     * Records source location metadata.
     *
     * Emits a [DebugLoc] instruction into the current block.
     *
     * @param line Int
     * @param col Int
     * @param scope String
     * @param inlinedAt String
     */
    fun debugLoc(line: Int, col: Int, scope: String, inlinedAt: String? = null): Unit

    /**
     * Associates a source-level variable with an SSA value.
     *
     * Emits a [DebugValue] instruction into the current block.
     *
     * @param variable String
     * @param value source operand
     * @param expression String
     */
    fun debugValue(variable: String, value: Value, expression: String? = null): Unit

    /**
     * Associates a source-level variable with a memory address.
     *
     * Emits a [DebugDeclare] instruction into the current block.
     *
     * @param variable String
     * @param address source operand
     * @param expression String
     */
    fun debugDeclare(variable: String, address: Value, expression: String? = null): Unit

    /**
     * Optimizer hint asserting that a condition is always true.
     *
     * Emits a [Assume] instruction into the current block.
     *
     * @param condition source operand
     */
    fun assume(condition: Value): Unit
}

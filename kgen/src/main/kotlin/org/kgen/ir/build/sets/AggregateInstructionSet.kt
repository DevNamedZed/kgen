// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Emission interface for aggregate instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface AggregateInstructionSet : InstructionSet {

    /**
     * Extracts a value from a nested aggregate at compile-time indices.
     *
     * Emits a [ExtractValue] instruction into the current block.
     *
     * @param fieldType the type of the extracted field
     * @param aggregate source operand
     * @param indices List<Int>
     * @return the SSA value produced by this instruction
     */
    fun extractValue(fieldType: Type, aggregate: Value, indices: List<Int>): Value

    /**
     * Inserts a value into a nested aggregate at compile-time indices.
     *
     * Emits a [InsertValue] instruction into the current block.
     *
     * @param aggregate source operand
     * @param element source operand
     * @param indices List<Int>
     * @return the SSA value produced by this instruction
     */
    fun insertValue(aggregate: Value, element: Value, indices: List<Int>): Value
}

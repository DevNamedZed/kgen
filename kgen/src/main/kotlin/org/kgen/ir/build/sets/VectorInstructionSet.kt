// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.Type
import org.kgen.ir.VectorReduceOp

/**
 * Emission interface for vector instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface VectorInstructionSet : InstructionSet {

    /**
     * Extracts a single scalar element from a vector at a dynamic index.
     *
     * Emits a [ExtractElement] instruction into the current block.
     *
     * @param elementType the scalar element type of the vector
     * @param vector source operand
     * @param index source operand
     * @return the SSA value produced by this instruction
     */
    fun extractElement(elementType: Type, vector: Value, index: Value): Value

    /**
     * Inserts a scalar element into a vector at a dynamic index.
     *
     * Emits a [InsertElement] instruction into the current block.
     *
     * @param vector source operand
     * @param element source operand
     * @param index source operand
     * @return the SSA value produced by this instruction
     */
    fun insertElement(vector: Value, element: Value, index: Value): Value

    /**
     * Shuffles lanes from two vectors according to a compile-time mask.
     *
     * Emits a [ShuffleVector] instruction into the current block.
     *
     * @param resultType the result vector type (element type from source, lane count from mask)
     * @param v1 source operand
     * @param v2 source operand
     * @param mask List<Int>
     * @return the SSA value produced by this instruction
     */
    fun shuffleVector(resultType: Type.Vector, v1: Value, v2: Value, mask: List<Int>): Value

    /**
     * Broadcasts a scalar value into every lane of a vector.
     *
     * Emits a [Splat] instruction into the current block.
     *
     * @param scalar source operand
     * @param vectorType the type
     * @return the SSA value produced by this instruction
     */
    fun splat(scalar: Value, vectorType: Type.Vector): Value

    /**
     * Reduces all lanes of a vector into a single scalar using a reduction operator.
     *
     * Emits a [VectorReduce] instruction into the current block.
     *
     * @param elementType the scalar element type of the vector being reduced
     * @param op VectorReduceOp
     * @param vector source operand
     * @return the SSA value produced by this instruction
     */
    fun vectorReduce(elementType: Type, op: VectorReduceOp, vector: Value): Value
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.Type
import org.kgen.ir.VectorReduceOp

/**
 * Default implementation of [VectorInstructionSet] backed by an [InstructionSink].
 */
internal class VectorInstructionSetImpl(private val sink: InstructionSink) : VectorInstructionSet {

    override fun extractElement(elementType: Type, vector: Value, index: Value): Value =
        sink.nextRef(elementType).also { sink.emit(ExtractElement(it, vector, index)) }

    override fun insertElement(vector: Value, element: Value, index: Value): Value =
        sink.nextRef(vector.type).also { sink.emit(InsertElement(it, vector, element, index)) }

    override fun shuffleVector(resultType: Type.Vector, v1: Value, v2: Value, mask: List<Int>): Value =
        sink.nextRef(resultType).also { sink.emit(ShuffleVector(it, v1, v2, mask)) }

    override fun splat(scalar: Value, vectorType: Type.Vector): Value =
        sink.nextRef(vectorType).also { sink.emit(Splat(it, scalar, vectorType)) }

    override fun vectorReduce(elementType: Type, op: VectorReduceOp, vector: Value): Value =
        sink.nextRef(elementType).also { sink.emit(VectorReduce(it, op, vector)) }
}

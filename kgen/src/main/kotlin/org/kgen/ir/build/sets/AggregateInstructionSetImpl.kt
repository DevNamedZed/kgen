// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Default implementation of [AggregateInstructionSet] backed by an [InstructionSink].
 */
internal class AggregateInstructionSetImpl(private val sink: InstructionSink) : AggregateInstructionSet {

    override fun extractValue(fieldType: Type, aggregate: Value, indices: List<Int>): Value =
        sink.nextRef(fieldType).also { sink.emit(ExtractValue(it, aggregate, indices)) }

    override fun insertValue(aggregate: Value, element: Value, indices: List<Int>): Value =
        sink.nextRef(aggregate.type).also { sink.emit(InsertValue(it, aggregate, element, indices)) }
}

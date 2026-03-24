// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.Type

/**
 * Default implementation of [SsaInstructionSet] backed by an [InstructionSink].
 */
internal class SsaInstructionSetImpl(private val sink: InstructionSink) : SsaInstructionSet {

    override fun phi(resultType: Type, incoming: List<Pair<Value, BlockRef>>): Value =
        sink.nextRef(resultType).also { sink.emit(Phi(it, incoming)) }

    override fun select(condition: Value, trueValue: Value, falseValue: Value): Value =
        sink.nextRef(trueValue.type).also { sink.emit(Select(it, condition, trueValue, falseValue)) }

    override fun freeze(value: Value): Value =
        sink.nextRef(value.type).also { sink.emit(Freeze(it, value)) }

    override fun piNode(base: Value, refinedType: Type): Value =
        sink.nextRef(refinedType).also { sink.emit(PiNode(it, base, refinedType)) }
}

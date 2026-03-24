// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.FastMathFlags

/**
 * Default implementation of [ComparisonInstructionSet] backed by an [InstructionSink].
 */
internal class ComparisonInstructionSetImpl(private val sink: InstructionSink) : ComparisonInstructionSet {

    override fun icmp(predicate: ICmpPredicate, lhs: Value, rhs: Value): Value =
        sink.nextRef(Type.I1).also { sink.emit(ICmp(it, predicate, lhs, rhs)) }

    override fun fcmp(predicate: FCmpPredicate, lhs: Value, rhs: Value, fastMath: FastMathFlags): Value =
        sink.nextRef(Type.I1).also { sink.emit(FCmp(it, predicate, lhs, rhs, fastMath)) }
}

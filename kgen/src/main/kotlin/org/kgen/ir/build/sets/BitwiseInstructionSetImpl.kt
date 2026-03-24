// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*

/**
 * Default implementation of [BitwiseInstructionSet] backed by an [InstructionSink].
 */
internal class BitwiseInstructionSetImpl(private val sink: InstructionSink) : BitwiseInstructionSet {

    override fun and(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(And(it, lhs, rhs)) }

    override fun or(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(Or(it, lhs, rhs)) }

    override fun xor(lhs: Value, rhs: Value): Value =
        sink.nextRef(lhs.type).also { sink.emit(Xor(it, lhs, rhs)) }

    override fun not(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Not(it, operand)) }

    override fun shl(lhs: Value, rhs: Value, nuw: Boolean, nsw: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(Shl(it, lhs, rhs, nuw, nsw)) }

    override fun lshr(lhs: Value, rhs: Value, exact: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(LShr(it, lhs, rhs, exact)) }

    override fun ashr(lhs: Value, rhs: Value, exact: Boolean): Value =
        sink.nextRef(lhs.type).also { sink.emit(AShr(it, lhs, rhs, exact)) }

    override fun rotl(value: Value, amount: Value): Value =
        sink.nextRef(value.type).also { sink.emit(Rotl(it, value, amount)) }

    override fun rotr(value: Value, amount: Value): Value =
        sink.nextRef(value.type).also { sink.emit(Rotr(it, value, amount)) }

    override fun ctlz(operand: Value, isZeroPoison: Boolean): Value =
        sink.nextRef(operand.type).also { sink.emit(Ctlz(it, operand, isZeroPoison)) }

    override fun cttz(operand: Value, isZeroPoison: Boolean): Value =
        sink.nextRef(operand.type).also { sink.emit(Cttz(it, operand, isZeroPoison)) }

    override fun ctpop(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(Ctpop(it, operand)) }

    override fun bswap(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(BSwap(it, operand)) }

    override fun bitReverse(operand: Value): Value =
        sink.nextRef(operand.type).also { sink.emit(BitReverse(it, operand)) }
}

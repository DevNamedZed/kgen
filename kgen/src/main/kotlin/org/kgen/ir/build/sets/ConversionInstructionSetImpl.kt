// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Default implementation of [ConversionInstructionSet] backed by an [InstructionSink].
 */
internal class ConversionInstructionSetImpl(private val sink: InstructionSink) : ConversionInstructionSet {

    override fun trunc(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(IntTrunc(it, value, toType)) }

    override fun zext(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(ZExt(it, value, toType)) }

    override fun sext(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(SExt(it, value, toType)) }

    override fun fptrunc(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(FPTrunc(it, value, toType)) }

    override fun fpext(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(FPExt(it, value, toType)) }

    override fun fptoui(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(FPToUI(it, value, toType)) }

    override fun fptosi(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(FPToSI(it, value, toType)) }

    override fun uitofp(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(UIToFP(it, value, toType)) }

    override fun sitofp(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(SIToFP(it, value, toType)) }

    override fun ptrtoint(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(PtrToInt(it, value, toType)) }

    override fun inttoptr(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(IntToPtr(it, value, toType)) }

    override fun bitcast(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(BitCast(it, value, toType)) }

    override fun addrspacecast(value: Value, toType: Type): Value =
        sink.nextRef(toType).also { sink.emit(AddrSpaceCast(it, value, toType)) }
}

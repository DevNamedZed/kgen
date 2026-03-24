// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.CallingConvention
import org.kgen.ir.FnAttribute
import org.kgen.ir.TailCallKind
import org.kgen.ir.Type

/**
 * Default implementation of [CallInstructionSet] backed by an [InstructionSink].
 */
internal class CallInstructionSetImpl(private val sink: InstructionSink) : CallInstructionSet {

    override fun call(function: Value, args: List<Value>, returnType: Type, callingConv: CallingConvention, tailCall: TailCallKind, attributes: Set<FnAttribute>): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(Call(dest, function, args, returnType, callingConv, tailCall, attributes))
        return dest
    }

    override fun invoke(function: Value, args: List<Value>, returnType: Type, normalDest: BlockRef, unwindDest: BlockRef, callingConv: CallingConvention): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(Invoke(dest, function, args, returnType, normalDest, unwindDest, callingConv))
        return dest
    }

    override fun callBr(function: Value, args: List<Value>, returnType: Type, fallthrough: BlockRef, indirectDests: List<BlockRef>): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(CallBr(dest, function, args, returnType, fallthrough, indirectDests))
        return dest
    }
}

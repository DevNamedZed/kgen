// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.ManagedCallDirection
import org.kgen.ir.Type

/**
 * Default implementation of [InteropInstructionSet] backed by an [InstructionSink].
 */
internal class InteropInstructionSetImpl(private val sink: InstructionSink) : InteropInstructionSet {

    override fun pin(ref: Value): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(Pin(it, ref)) }

    override fun unpin(ref: Value) {
        sink.emit(Unpin(ref))
    }

    override fun interiorPtr(ref: Value, index: Value, pointeeType: Type): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(InteriorPtr(it, ref, index, pointeeType)) }

    override fun managedCall(function: Value, args: List<Value>, returnType: Type, direction: ManagedCallDirection): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(ManagedCall(dest, function, args, returnType, direction))
        return dest
    }

    override fun managedToDevice(ref: Value, targetAddrSpace: Int): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(ManagedToDevice(it, ref, targetAddrSpace)) }

    override fun deviceRelease(ptr: Value) {
        sink.emit(DeviceRelease(ptr))
    }
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Default implementation of [RuntimeInstructionSet] backed by an [InstructionSink].
 */
internal class RuntimeInstructionSetImpl(private val sink: InstructionSink) : RuntimeInstructionSet {

    override fun gcAlloc(allocType: Type, size: Value?): Value =
        sink.nextRef(Type.Reference(allocType)).also { sink.emit(GCAlloc(it, allocType, size)) }

    override fun gcSafepoint() {
        sink.emit(GCSafepoint())
    }

    override fun gcRoot(ptr: Value, metadata: Value?) {
        sink.emit(GCRoot(ptr, metadata))
    }

    override fun writeBarrier(obj: Value, fieldIndex: Value, value: Value) {
        sink.emit(WriteBarrier(obj, fieldIndex, value))
    }

    override fun readBarrier(ref: Value): Value =
        sink.nextRef(ref.type).also { sink.emit(ReadBarrier(it, ref)) }

    override fun refRetain(obj: Value) {
        sink.emit(RefRetain(obj))
    }

    override fun refRelease(obj: Value) {
        sink.emit(RefRelease(obj))
    }

    override fun refCount(obj: Value): Value =
        sink.nextRef(Type.I32).also { sink.emit(RefCount(it, obj)) }

    override fun coroBegin(id: Value, mem: Value): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(CoroBegin(it, id, mem)) }

    override fun coroEnd(handle: Value, unwind: Boolean) {
        sink.emit(CoroEnd(handle, unwind))
    }

    override fun coroSuspend(save: Value?, isFinal: Boolean): Value =
        sink.nextRef(Type.I8).also { sink.emit(CoroSuspend(it, save, isFinal)) }

    override fun coroResume(handle: Value) {
        sink.emit(CoroResume(handle))
    }

    override fun coroDestroy(handle: Value) {
        sink.emit(CoroDestroy(handle))
    }

    override fun coroSize(): Value =
        sink.nextRef(Type.I64).also { sink.emit(CoroSize(it)) }

    override fun gCRelocate(safepoint: Value, base: Value, derived: Value): Value =
        sink.nextRef(Type.I32).also { sink.emit(GCRelocate(it, safepoint, base, derived)) }
}

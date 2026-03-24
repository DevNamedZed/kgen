// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.Type

/**
 * Default implementation of [MemoryInstructionSet] backed by an [InstructionSink].
 */
internal class MemoryInstructionSetImpl(private val sink: InstructionSink) : MemoryInstructionSet {

    override fun alloca(allocType: Type, numElements: Value?, align: Int?): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(Alloca(it, allocType, numElements, align)) }

    override fun load(ptr: Value, loadType: Type, align: Int?, volatile: Boolean, ordering: AtomicOrdering?): Value =
        sink.nextRef(loadType).also { sink.emit(Load(it, ptr, loadType, align, volatile, ordering)) }

    override fun store(value: Value, ptr: Value, align: Int?, volatile: Boolean, ordering: AtomicOrdering?) {
        sink.emit(Store(value, ptr, align, volatile, ordering))
    }

    override fun gep(baseType: Type, ptr: Value, indices: List<Value>, inBounds: Boolean): Value =
        sink.nextRef(Type.Pointer(baseType)).also { sink.emit(GetElementPtr(it, baseType, ptr, indices, inBounds)) }

    override fun memcpy(dst: Value, src: Value, len: Value, volatile: Boolean) {
        sink.emit(MemCpy(dst, src, len, volatile))
    }

    override fun memset(dst: Value, value: Value, len: Value, volatile: Boolean) {
        sink.emit(MemSet(dst, value, len, volatile))
    }

    override fun memmove(dst: Value, src: Value, len: Value, volatile: Boolean) {
        sink.emit(MemMove(dst, src, len, volatile))
    }

    override fun prefetch(address: Value, rw: Int, locality: Int, cacheType: Int) {
        sink.emit(Prefetch(address, rw, locality, cacheType))
    }

    override fun stackSave(): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(StackSave(it)) }

    override fun stackRestore(ptr: Value) {
        sink.emit(StackRestore(ptr))
    }

    override fun lifetimeStart(ptr: Value, size: Long) {
        sink.emit(LifetimeStart(ptr, size))
    }

    override fun lifetimeEnd(ptr: Value, size: Long) {
        sink.emit(LifetimeEnd(ptr, size))
    }

    override fun vaStart(argList: Value) {
        sink.emit(VAStart(argList))
    }

    override fun vaEnd(argList: Value) {
        sink.emit(VAEnd(argList))
    }

    override fun vaCopy(dst: Value, src: Value) {
        sink.emit(VACopy(dst, src))
    }

    override fun vaArg(argList: Value, argType: Type): Value =
        sink.nextRef(argType).also { sink.emit(VAArg(it, argList, argType)) }
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.BootstrapMethod
import org.kgen.ir.CatchHandler
import org.kgen.ir.Type

/**
 * Default implementation of [ObjectInstructionSet] backed by an [InstructionSink].
 */
internal class ObjectInstructionSetImpl(private val sink: InstructionSink) : ObjectInstructionSet {

    override fun throwException(exception: Value) {
        sink.emit(Throw(exception))
    }

    override fun tryCatch(tryBlock: BlockRef, catches: List<CatchHandler>, finallyBlock: BlockRef?) {
        sink.emit(TryCatchRegion(tryBlock, catches, finallyBlock))
    }

    override fun newObject(className: String, typeArgs: List<Type>): Value =
        sink.nextRef(Type.ClassRef(className)).also { sink.emit(NewObject(it, className, typeArgs)) }

    override fun newArray(elementType: Type, size: Value): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(NewArray(it, elementType, size)) }

    override fun newMultiArray(elementType: Type, dimensions: List<Value>): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(NewMultiArray(it, elementType, dimensions)) }

    override fun getField(obj: Value, className: String, fieldName: String, fieldType: Type): Value =
        sink.nextRef(fieldType).also { sink.emit(GetField(it, obj, className, fieldName, fieldType)) }

    override fun putField(obj: Value, className: String, fieldName: String, fieldType: Type, value: Value) {
        sink.emit(PutField(obj, className, fieldName, fieldType, value))
    }

    override fun getStatic(className: String, fieldName: String, fieldType: Type): Value =
        sink.nextRef(fieldType).also { sink.emit(GetStatic(it, className, fieldName, fieldType)) }

    override fun putStatic(className: String, fieldName: String, fieldType: Type, value: Value) {
        sink.emit(PutStatic(className, fieldName, fieldType, value))
    }

    override fun virtualCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? {
        val dest = if (methodType.ret != Type.Void) { sink.nextRef(methodType.ret) } else { null }
        sink.emit(VirtualCall(dest, obj, className, methodName, methodType, args))
        return dest
    }

    override fun interfaceCall(obj: Value, interfaceName: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? {
        val dest = if (methodType.ret != Type.Void) { sink.nextRef(methodType.ret) } else { null }
        sink.emit(InterfaceCall(dest, obj, interfaceName, methodName, methodType, args))
        return dest
    }

    override fun specialCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? {
        val dest = if (methodType.ret != Type.Void) { sink.nextRef(methodType.ret) } else { null }
        sink.emit(SpecialCall(dest, obj, className, methodName, methodType, args))
        return dest
    }

    override fun staticCall(className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value? {
        val dest = if (methodType.ret != Type.Void) { sink.nextRef(methodType.ret) } else { null }
        sink.emit(StaticCall(dest, className, methodName, methodType, args))
        return dest
    }

    override fun dynamicCall(bootstrapMethod: BootstrapMethod, name: String, methodType: Type.Function, args: List<Value>): Value? {
        val dest = if (methodType.ret != Type.Void) { sink.nextRef(methodType.ret) } else { null }
        sink.emit(DynamicCall(dest, bootstrapMethod, name, methodType, args))
        return dest
    }

    override fun constructorCall(obj: Value, className: String, constructorType: Type.Function, args: List<Value>) {
        sink.emit(ConstructorCall(obj, className, constructorType, args))
    }

    override fun instanceOf(obj: Value, checkType: Type): Value =
        sink.nextRef(Type.I1).also { sink.emit(InstanceOf(it, obj, checkType)) }

    override fun checkCast(obj: Value, castType: Type): Value =
        sink.nextRef(castType).also { sink.emit(CheckCast(it, obj, castType)) }

    override fun typeId(obj: Value): Value =
        sink.nextRef(Type.I32).also { sink.emit(TypeId(it, obj)) }

    override fun arrayGet(array: Value, index: Value, elementType: Type): Value =
        sink.nextRef(elementType).also { sink.emit(ArrayGet(it, array, index, elementType)) }

    override fun arraySet(array: Value, index: Value, value: Value, elementType: Type) {
        sink.emit(ArraySet(array, index, value, elementType))
    }

    override fun arrayLength(array: Value): Value =
        sink.nextRef(Type.I32).also { sink.emit(ArrayLength(it, array)) }

    override fun monitorEnter(obj: Value) {
        sink.emit(MonitorEnter(obj))
    }

    override fun monitorExit(obj: Value) {
        sink.emit(MonitorExit(obj))
    }

    override fun box(value: Value, boxType: Type): Value =
        sink.nextRef(boxType).also { sink.emit(Box(it, value, boxType)) }

    override fun unbox(obj: Value, unboxType: Type): Value =
        sink.nextRef(unboxType).also { sink.emit(Unbox(it, obj, unboxType)) }

    override fun closureCreate(function: Value, captures: List<Value>, closureType: Type.Function, escaping: Boolean): Value =
        sink.nextRef(closureType).also { sink.emit(ClosureCreate(it, function, captures, closureType, escaping)) }

    override fun closureInvoke(closure: Value, args: List<Value>, returnType: Type): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(ClosureInvoke(dest, closure, args, returnType))
        return dest
    }

    override fun closureInvokeOnce(returnType: Type, closure: Value, args: List<Value>): Value =
        sink.nextRef(returnType).also { sink.emit(ClosureInvokeOnce(it, closure, args)) }

    override fun constructVariant(unionType: Type.TaggedUnion, variantName: String, fields: List<Value>): Value =
        sink.nextRef(unionType).also { sink.emit(ConstructVariant(it, unionType, variantName, fields)) }

    override fun getTag(tagType: Type, union: Value): Value =
        sink.nextRef(tagType).also { sink.emit(GetTag(it, union)) }

    override fun getVariantField(fieldType: Type, union: Value, variantName: String, fieldIndex: Int): Value =
        sink.nextRef(fieldType).also { sink.emit(GetVariantField(it, union, variantName, fieldIndex)) }

    override fun tagSwitch(union: Value, cases: List<Pair<String, BlockRef>>, defaultTarget: BlockRef?) {
        sink.emit(TagSwitch(union, cases, defaultTarget))
    }

    override fun catchValue(exceptionType: Type): Value =
        sink.nextRef(exceptionType).also { sink.emit(CatchValue(it, exceptionType)) }

    override fun makeWeakRef(obj: Value): Value =
        sink.nextRef(Type.OpaquePointer).also { sink.emit(MakeWeakRef(it, obj)) }

    override fun readWeakRef(referentType: Type, weakRef: Value): Value =
        sink.nextRef(referentType).also { sink.emit(ReadWeakRef(it, weakRef)) }

    override fun clearWeakRef(weakRef: Value) {
        sink.emit(ClearWeakRef(weakRef))
    }
}

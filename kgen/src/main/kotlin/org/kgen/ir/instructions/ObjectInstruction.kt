package org.kgen.ir.instructions

import org.kgen.ir.BootstrapMethod
import org.kgen.ir.CatchHandler
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface ObjectInstruction : Instruction {
    override val category get() = IrCategory.OBJECT
}

data class Throw(
    val exception: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class TryCatchRegion(
    val tryBlock: String,
    val catches: List<CatchHandler>,
    val finallyBlock: String? = null,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class NewObject(
    val dest: InstructionRef,
    val className: String,
    val typeArgs: List<Type> = emptyList(),
) : ObjectInstruction {
    override val result get() = dest
}

data class NewArray(
    val dest: InstructionRef,
    val elementType: Type,
    val size: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class NewMultiArray(
    val dest: InstructionRef,
    val elementType: Type,
    val dimensions: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class GetField(
    val dest: InstructionRef,
    val obj: Value,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class PutField(
    val obj: Value,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
    val value: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class GetStatic(
    val dest: InstructionRef,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class PutStatic(
    val className: String,
    val fieldName: String,
    val fieldType: Type,
    val value: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class VirtualCall(
    val dest: InstructionRef?,
    val obj: Value,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class InterfaceCall(
    val dest: InstructionRef?,
    val obj: Value,
    val interfaceName: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class SpecialCall(
    val dest: InstructionRef?,
    val obj: Value,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class StaticCall(
    val dest: InstructionRef?,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class DynamicCall(
    val dest: InstructionRef?,
    val bootstrapMethod: BootstrapMethod,
    val name: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class ConstructorCall(
    val obj: Value,
    val className: String,
    val constructorType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class InstanceOf(
    val dest: InstructionRef,
    val obj: Value,
    val checkType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class CheckCast(
    val dest: InstructionRef,
    val obj: Value,
    val castType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class TypeId(
    val dest: InstructionRef,
    val obj: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class ArrayGet(
    val dest: InstructionRef,
    val array: Value,
    val index: Value,
    val elementType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class ArraySet(
    val array: Value,
    val index: Value,
    val value: Value,
    val elementType: Type,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class ArrayLength(
    val dest: InstructionRef,
    val array: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class MonitorEnter(
    val obj: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class MonitorExit(
    val obj: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class Box(
    val dest: InstructionRef,
    val value: Value,
    val boxType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class Unbox(
    val dest: InstructionRef,
    val obj: Value,
    val unboxType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class ClosureCreate(
    val dest: InstructionRef,
    val function: Value,
    val captures: List<Value>,
    val closureType: Type.Function,
) : ObjectInstruction {
    override val result get() = dest
}

data class ClosureInvoke(
    val dest: InstructionRef?,
    val closure: Value,
    val args: List<Value>,
    val returnType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class ConstructVariant(
    val dest: InstructionRef,
    val unionType: Type.TaggedUnion,
    val variantName: String,
    val fields: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
}

data class GetTag(
    val dest: InstructionRef,
    val union: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class GetVariantField(
    val dest: InstructionRef,
    val union: Value,
    val variantName: String,
    val fieldIndex: Int,
) : ObjectInstruction {
    override val result get() = dest
}

data class TagSwitch(
    val union: Value,
    val cases: List<Pair<String, String>>,
    val defaultTarget: String? = null,
) : ObjectInstruction {
    override val result: Value? get() = null
}

data class CatchValue(
    val dest: InstructionRef,
    val exceptionType: Type,
) : ObjectInstruction {
    override val result get() = dest
}

data class MakeWeakRef(
    val dest: InstructionRef,
    val obj: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class ReadWeakRef(
    val dest: InstructionRef,
    val weakRef: Value,
) : ObjectInstruction {
    override val result get() = dest
}

data class ClearWeakRef(
    val weakRef: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
}

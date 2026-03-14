// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.BlockRef
import org.kgen.ir.BootstrapMethod
import org.kgen.ir.CatchHandler
import org.kgen.ir.Type

/**
 * Object-oriented and high-level data structure instructions.
 *
 * Object instructions model heap allocation, field access, method dispatch,
 * array operations, exception handling, closures, and tagged unions.
 */
sealed interface ObjectInstruction : Instruction {
    override val category get() = IrCategory.OBJECT
}

// --- Exception and control flow ---

/**
 * Throws an exception, transferring control to the nearest catch handler.
 *
 * @param exception operand value
 */
data class Throw(
    val exception: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.MANAGED_THROW
    override val operands get() = listOf(exception)
}

/**
 * Declares a try/catch/finally region for structured exception handling.
 *
 * @param tryBlock configuration
 * @param catches configuration
 * @param finallyBlock configuration
 */
data class TryCatchRegion(
    val tryBlock: BlockRef,
    val catches: List<CatchHandler>,
    val finallyBlock: BlockRef? = null,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = emptyList<Value>()
}

// --- Object allocation ---

/**
 * Allocates a new object instance of the given class on the managed heap.
 *
 * @param dest the SSA result reference
 * @param className configuration
 * @param typeArgs configuration
 */
data class NewObject(
    val dest: InstructionRef,
    val className: String,
    val typeArgs: List<Type> = emptyList(),
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.OBJECT_ALLOC
    override val operands get() = emptyList<Value>()
}

/**
 * Allocates a new single-dimension array on the managed heap.
 *
 * @param dest the SSA result reference
 * @param elementType configuration
 * @param size operand value
 */
data class NewArray(
    val dest: InstructionRef,
    val elementType: Type,
    val size: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.OBJECT_ALLOC
    override val operands get() = listOf(size)
}

/**
 * Allocates a new multi-dimensional array on the managed heap.
 *
 * @param dest the SSA result reference
 * @param elementType configuration
 * @param dimensions list of operand values
 */
data class NewMultiArray(
    val dest: InstructionRef,
    val elementType: Type,
    val dimensions: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.OBJECT_ALLOC
    override val operands get() = dimensions
}

// --- Field access ---

/**
 * Reads an instance field from an object.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 * @param className configuration
 * @param fieldName configuration
 * @param fieldType configuration
 */
data class GetField(
    val dest: InstructionRef,
    val obj: Value,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = listOf(obj)
}

/**
 * Writes a value to an instance field of an object.
 *
 * @param obj operand value
 * @param className configuration
 * @param fieldName configuration
 * @param fieldType configuration
 * @param value operand value
 */
data class PutField(
    val obj: Value,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
    val value: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(obj, value)
}

/**
 * Reads a static (class-level) field.
 *
 * @param dest the SSA result reference
 * @param className configuration
 * @param fieldName configuration
 * @param fieldType configuration
 */
data class GetStatic(
    val dest: InstructionRef,
    val className: String,
    val fieldName: String,
    val fieldType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = emptyList<Value>()
}

/**
 * Writes a value to a static (class-level) field.
 *
 * @param className configuration
 * @param fieldName configuration
 * @param fieldType configuration
 * @param value operand value
 */
data class PutStatic(
    val className: String,
    val fieldName: String,
    val fieldType: Type,
    val value: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(value)
}

// --- Method dispatch ---

/**
 * Dispatches a virtual method call on an object using vtable lookup.
 *
 * @param dest the SSA result reference, or null for void
 * @param obj operand value
 * @param className configuration
 * @param methodName configuration
 * @param methodType configuration
 * @param args list of operand values
 */
data class VirtualCall(
    val dest: InstructionRef?,
    val obj: Value,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(obj) + args
}

/**
 * Dispatches an interface method call on an object using itable lookup.
 *
 * @param dest the SSA result reference, or null for void
 * @param obj operand value
 * @param interfaceName configuration
 * @param methodName configuration
 * @param methodType configuration
 * @param args list of operand values
 */
data class InterfaceCall(
    val dest: InstructionRef?,
    val obj: Value,
    val interfaceName: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(obj) + args
}

/**
 * Invokes a method with special (non-virtual) dispatch.
 *
 * @param dest the SSA result reference, or null for void
 * @param obj operand value
 * @param className configuration
 * @param methodName configuration
 * @param methodType configuration
 * @param args list of operand values
 */
data class SpecialCall(
    val dest: InstructionRef?,
    val obj: Value,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(obj) + args
}

/**
 * Invokes a static method (no receiver object).
 *
 * @param dest the SSA result reference, or null for void
 * @param className configuration
 * @param methodName configuration
 * @param methodType configuration
 * @param args list of operand values
 */
data class StaticCall(
    val dest: InstructionRef?,
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = args
}

/**
 * Invokes a dynamically-linked call site via a bootstrap method.
 *
 * @param dest the SSA result reference, or null for void
 * @param bootstrapMethod configuration
 * @param name configuration
 * @param methodType configuration
 * @param args list of operand values
 */
data class DynamicCall(
    val dest: InstructionRef?,
    val bootstrapMethod: BootstrapMethod,
    val name: String,
    val methodType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = args
}

/**
 * Invokes a constructor on an already-allocated object.
 *
 * @param obj operand value
 * @param className configuration
 * @param constructorType configuration
 * @param args list of operand values
 */
data class ConstructorCall(
    val obj: Value,
    val className: String,
    val constructorType: Type.Function,
    val args: List<Value>,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(obj) + args
}

// --- Type checking ---

/**
 * Tests whether an object is an instance of a given type.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 * @param checkType configuration
 */
data class InstanceOf(
    val dest: InstructionRef,
    val obj: Value,
    val checkType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(obj)
}

/**
 * Casts an object reference to a target type, throwing if invalid.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 * @param castType configuration
 */
data class CheckCast(
    val dest: InstructionRef,
    val obj: Value,
    val castType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.CAN_THROW or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(obj)
}

/**
 * Retrieves the runtime type identifier of an object.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 */
data class TypeId(
    val dest: InstructionRef,
    val obj: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(obj)
}

// --- Array operations ---

/**
 * Reads an element from an array at the given index.
 *
 * @param dest the SSA result reference
 * @param array operand value
 * @param index operand value
 * @param elementType configuration
 */
data class ArrayGet(
    val dest: InstructionRef,
    val array: Value,
    val index: Value,
    val elementType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.CAN_THROW)
    override val operands get() = listOf(array, index)
}

/**
 * Writes a value to an array at the given index.
 *
 * @param array operand value
 * @param index operand value
 * @param value operand value
 * @param elementType configuration
 */
data class ArraySet(
    val array: Value,
    val index: Value,
    val value: Value,
    val elementType: Type,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.CAN_THROW or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(array, index, value)
}

/**
 * Retrieves the length of an array.
 *
 * @param dest the SSA result reference
 * @param array operand value
 */
data class ArrayLength(
    val dest: InstructionRef,
    val array: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = listOf(array)
}

// --- Monitors ---

/**
 * Acquires the monitor (intrinsic lock) of an object.
 *
 * @param obj operand value
 */
data class MonitorEnter(
    val obj: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.MONITOR
    override val operands get() = listOf(obj)
}

/**
 * Releases the monitor (intrinsic lock) of an object.
 *
 * @param obj operand value
 */
data class MonitorExit(
    val obj: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.MONITOR
    override val operands get() = listOf(obj)
}

// --- Boxing and closures ---

/**
 * Boxes a primitive value into a managed object.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param boxType configuration
 */
data class Box(
    val dest: InstructionRef,
    val value: Value,
    val boxType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.OBJECT_ALLOC
    override val operands get() = listOf(value)
}

/**
 * Unboxes a managed wrapper object back to a primitive value.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 * @param unboxType configuration
 */
data class Unbox(
    val dest: InstructionRef,
    val obj: Value,
    val unboxType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY or InstructionEffects.CAN_THROW)
    override val operands get() = listOf(obj)
}

/**
 * Creates a closure by capturing a function pointer and a set of captured values.
 *
 * If `escaping` is false, the captured environment may be stack-allocated.
 * If a stack-allocated closure escapes (assigned to a heap field, returned,
 * or passed to a function that captures it), the verifier reports an error.
 *
 * @param dest the SSA result reference
 * @param function operand value
 * @param captures list of operand values
 * @param closureType configuration
 * @param escaping flag (default: true)
 */
data class ClosureCreate(
    val dest: InstructionRef,
    val function: Value,
    val captures: List<Value>,
    val closureType: Type.Function,
    val escaping: Boolean = true,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.OBJECT_ALLOC
    override val operands get() = listOf(function) + captures
}

/**
 * Invokes a closure with the given arguments.
 *
 * @param dest the SSA result reference, or null for void
 * @param closure operand value
 * @param args list of operand values
 * @param returnType configuration
 */
data class ClosureInvoke(
    val dest: InstructionRef?,
    val closure: Value,
    val args: List<Value>,
    val returnType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(closure) + args
}

/**
 * Invoke a closure with move semantics (FnOnce).
 *
 * The closure value is consumed on invocation — no further uses are legal.
 * The verifier checks that the closure SSA value has no uses after
 * ClosureInvokeOnce. Lowers to the same indirect call as ClosureInvoke.
 *
 * @param dest the SSA result reference
 * @param closure operand value
 * @param args list of operand values
 */
data class ClosureInvokeOnce(
    val dest: InstructionRef,
    val closure: Value,
    val args: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(closure) + args
}

// --- Tagged unions ---

/**
 * Constructs a variant of a tagged union with the given field values.
 *
 * @param dest the SSA result reference
 * @param unionType configuration
 * @param variantName configuration
 * @param fields list of operand values
 */
data class ConstructVariant(
    val dest: InstructionRef,
    val unionType: Type.TaggedUnion,
    val variantName: String,
    val fields: List<Value>,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = fields
}

/**
 * Extracts the tag (discriminant) from a tagged union value.
 *
 * @param dest the SSA result reference
 * @param union operand value
 */
data class GetTag(
    val dest: InstructionRef,
    val union: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(union)
}

/**
 * Extracts a field from a specific variant of a tagged union.
 *
 * @param dest the SSA result reference
 * @param union operand value
 * @param variantName configuration
 * @param fieldIndex configuration
 */
data class GetVariantField(
    val dest: InstructionRef,
    val union: Value,
    val variantName: String,
    val fieldIndex: Int,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(union)
}

/**
 * Branches to different basic blocks based on the tag of a tagged union.
 *
 * @param union operand value
 * @param cases configuration
 * @param defaultTarget configuration
 */
data class TagSwitch(
    val union: Value,
    val cases: List<Pair<String, BlockRef>>,
    val defaultTarget: BlockRef? = null,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.CONDITIONAL_BRANCH
    override val operands get() = listOf(union)
}

// --- Catch and weak references ---

/**
 * Retrieves the caught exception value inside a catch handler.
 *
 * @param dest the SSA result reference
 * @param exceptionType configuration
 */
data class CatchValue(
    val dest: InstructionRef,
    val exceptionType: Type,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = emptyList<Value>()
}

/**
 * Creates a weak reference to a managed object.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 */
data class MakeWeakRef(
    val dest: InstructionRef,
    val obj: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(obj)
}

/**
 * Reads the referent of a weak reference. Returns null if the referent has been collected.
 *
 * @param dest the SSA result reference
 * @param weakRef operand value
 */
data class ReadWeakRef(
    val dest: InstructionRef,
    val weakRef: Value,
) : ObjectInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = listOf(weakRef)
}

/**
 * Clears a weak reference, breaking the association with its referent.
 *
 * @param weakRef operand value
 */
data class ClearWeakRef(
    val weakRef: Value,
) : ObjectInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(weakRef)
}


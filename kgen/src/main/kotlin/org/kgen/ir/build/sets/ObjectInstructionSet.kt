// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.BlockRef
import org.kgen.ir.BootstrapMethod
import org.kgen.ir.CatchHandler
import org.kgen.ir.Type

/**
 * Emission interface for object instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ObjectInstructionSet : InstructionSet {

    /**
     * Allocates a new object instance of the given class on the managed heap.
     *
     * Emits a [NewObject] instruction into the current block.
     *
     * @param className String
     * @param typeArgs List<Type>
     * @return the SSA value produced by this instruction
     */
    fun newObject(className: String, typeArgs: List<Type> = emptyList()): Value

    /**
     * Allocates a new single-dimension array on the managed heap.
     *
     * Emits a [NewArray] instruction into the current block.
     *
     * @param elementType the type
     * @param size source operand
     * @return the SSA value produced by this instruction
     */
    fun newArray(elementType: Type, size: Value): Value

    /**
     * Allocates a new multi-dimensional array on the managed heap.
     *
     * Emits a [NewMultiArray] instruction into the current block.
     *
     * @param elementType the type
     * @param dimensions list of source operands
     * @return the SSA value produced by this instruction
     */
    fun newMultiArray(elementType: Type, dimensions: List<Value>): Value

    /**
     * Reads an instance field from an object.
     *
     * Emits a [GetField] instruction into the current block.
     *
     * @param obj source operand
     * @param className String
     * @param fieldName String
     * @param fieldType the type
     * @return the SSA value produced by this instruction
     */
    fun getField(obj: Value, className: String, fieldName: String, fieldType: Type): Value

    /**
     * Reads a static (class-level) field.
     *
     * Emits a [GetStatic] instruction into the current block.
     *
     * @param className String
     * @param fieldName String
     * @param fieldType the type
     * @return the SSA value produced by this instruction
     */
    fun getStatic(className: String, fieldName: String, fieldType: Type): Value

    /**
     * Dispatches a virtual method call on an object using vtable lookup.
     *
     * Emits a [VirtualCall] instruction into the current block.
     *
     * @param obj source operand
     * @param className String
     * @param methodName String
     * @param methodType the type
     * @param args list of source operands
     * @return the SSA value produced by this instruction, or null if void
     */
    fun virtualCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value?

    /**
     * Dispatches an interface method call on an object using itable lookup.
     *
     * Emits a [InterfaceCall] instruction into the current block.
     *
     * @param obj source operand
     * @param interfaceName String
     * @param methodName String
     * @param methodType the type
     * @param args list of source operands
     * @return the SSA value produced by this instruction, or null if void
     */
    fun interfaceCall(obj: Value, interfaceName: String, methodName: String, methodType: Type.Function, args: List<Value>): Value?

    /**
     * Invokes a method with special (non-virtual) dispatch.
     *
     * Emits a [SpecialCall] instruction into the current block.
     *
     * @param obj source operand
     * @param className String
     * @param methodName String
     * @param methodType the type
     * @param args list of source operands
     * @return the SSA value produced by this instruction, or null if void
     */
    fun specialCall(obj: Value, className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value?

    /**
     * Invokes a static method (no receiver object).
     *
     * Emits a [StaticCall] instruction into the current block.
     *
     * @param className String
     * @param methodName String
     * @param methodType the type
     * @param args list of source operands
     * @return the SSA value produced by this instruction, or null if void
     */
    fun staticCall(className: String, methodName: String, methodType: Type.Function, args: List<Value>): Value?

    /**
     * Invokes a dynamically-linked call site via a bootstrap method.
     *
     * Emits a [DynamicCall] instruction into the current block.
     *
     * @param bootstrapMethod BootstrapMethod
     * @param name the name
     * @param methodType the type
     * @param args list of source operands
     * @return the SSA value produced by this instruction, or null if void
     */
    fun dynamicCall(bootstrapMethod: BootstrapMethod, name: String, methodType: Type.Function, args: List<Value>): Value?

    /**
     * Tests whether an object is an instance of a given type.
     *
     * Emits a [InstanceOf] instruction into the current block.
     *
     * @param obj source operand
     * @param checkType the type
     * @return the SSA value produced by this instruction
     */
    fun instanceOf(obj: Value, checkType: Type): Value

    /**
     * Casts an object reference to a target type, throwing if invalid.
     *
     * Emits a [CheckCast] instruction into the current block.
     *
     * @param obj source operand
     * @param castType the type
     * @return the SSA value produced by this instruction
     */
    fun checkCast(obj: Value, castType: Type): Value

    /**
     * Retrieves the runtime type identifier of an object.
     *
     * Emits a [TypeId] instruction into the current block.
     *
     * @param obj source operand
     * @return the SSA value produced by this instruction
     */
    fun typeId(obj: Value): Value

    /**
     * Reads an element from an array at the given index.
     *
     * Emits a [ArrayGet] instruction into the current block.
     *
     * @param array source operand
     * @param index source operand
     * @param elementType the type
     * @return the SSA value produced by this instruction
     */
    fun arrayGet(array: Value, index: Value, elementType: Type): Value

    /**
     * Retrieves the length of an array.
     *
     * Emits a [ArrayLength] instruction into the current block.
     *
     * @param array source operand
     * @return the SSA value produced by this instruction
     */
    fun arrayLength(array: Value): Value

    /**
     * Boxes a primitive value into a managed object.
     *
     * Emits a [Box] instruction into the current block.
     *
     * @param value source operand
     * @param boxType the type
     * @return the SSA value produced by this instruction
     */
    fun box(value: Value, boxType: Type): Value

    /**
     * Unboxes a managed wrapper object back to a primitive value.
     *
     * Emits a [Unbox] instruction into the current block.
     *
     * @param obj source operand
     * @param unboxType the type
     * @return the SSA value produced by this instruction
     */
    fun unbox(obj: Value, unboxType: Type): Value

    /**
     * Creates a closure by capturing a function pointer and a set of captured values.

If `escaping` is false, the captured environment may be stack-allocated.
If a stack-allocated closure escapes (assigned to a heap field, returned,
or passed to a function that captures it), the verifier reports an error.
     *
     * Emits a [ClosureCreate] instruction into the current block.
     *
     * @param function source operand
     * @param captures list of source operands
     * @param closureType the type
     * @param escaping Boolean
     * @return the SSA value produced by this instruction
     */
    fun closureCreate(function: Value, captures: List<Value>, closureType: Type.Function, escaping: Boolean = true): Value

    /**
     * Invokes a closure with the given arguments.
     *
     * Emits a [ClosureInvoke] instruction into the current block.
     *
     * @param closure source operand
     * @param args list of source operands
     * @param returnType the type
     * @return the SSA value produced by this instruction, or null if void
     */
    fun closureInvoke(closure: Value, args: List<Value>, returnType: Type): Value?

    /**
     * Invoke a closure with move semantics (FnOnce).

The closure value is consumed on invocation — no further uses are legal.
The verifier checks that the closure SSA value has no uses after
ClosureInvokeOnce. Lowers to the same indirect call as ClosureInvoke.
     *
     * Emits a [ClosureInvokeOnce] instruction into the current block.
     *
     * @param returnType the return type of the closure
     * @param closure source operand
     * @param args list of source operands
     * @return the SSA value produced by this instruction
     */
    fun closureInvokeOnce(returnType: Type, closure: Value, args: List<Value>): Value

    /**
     * Constructs a variant of a tagged union with the given field values.
     *
     * Emits a [ConstructVariant] instruction into the current block.
     *
     * @param unionType the type
     * @param variantName String
     * @param fields list of source operands
     * @return the SSA value produced by this instruction
     */
    fun constructVariant(unionType: Type.TaggedUnion, variantName: String, fields: List<Value>): Value

    /**
     * Extracts the tag (discriminant) from a tagged union value.
     *
     * Emits a [GetTag] instruction into the current block.
     *
     * @param tagType the type of the tag field (typically Type.I32)
     * @param union source operand
     * @return the SSA value produced by this instruction
     */
    fun getTag(tagType: Type = Type.I32, union: Value): Value

    /**
     * Extracts a field from a specific variant of a tagged union.
     *
     * Emits a [GetVariantField] instruction into the current block.
     *
     * @param fieldType the type of the variant field being extracted
     * @param union source operand
     * @param variantName String
     * @param fieldIndex Int
     * @return the SSA value produced by this instruction
     */
    fun getVariantField(fieldType: Type, union: Value, variantName: String, fieldIndex: Int): Value

    /**
     * Retrieves the caught exception value inside a catch handler.
     *
     * Emits a [CatchValue] instruction into the current block.
     *
     * @param exceptionType the type
     * @return the SSA value produced by this instruction
     */
    fun catchValue(exceptionType: Type): Value

    /**
     * Creates a weak reference to a managed object.
     *
     * Emits a [MakeWeakRef] instruction into the current block.
     *
     * @param obj source operand
     * @return the SSA value produced by this instruction
     */
    fun makeWeakRef(obj: Value): Value

    /**
     * Reads the referent of a weak reference. Returns null if the referent has been collected.
     *
     * Emits a [ReadWeakRef] instruction into the current block.
     *
     * @param referentType the type of the weak reference's referent
     * @param weakRef source operand
     * @return the SSA value produced by this instruction
     */
    fun readWeakRef(referentType: Type, weakRef: Value): Value

    /**
     * Throws an exception, transferring control to the nearest catch handler.
     *
     * Emits a [Throw] instruction into the current block.
     *
     * @param exception source operand
     */
    fun throwException(exception: Value): Unit

    /**
     * Declares a try/catch/finally region for structured exception handling.
     *
     * Emits a [TryCatchRegion] instruction into the current block.
     *
     * @param tryBlock BlockRef
     * @param catches List<CatchHandler>
     * @param finallyBlock BlockRef
     */
    fun tryCatch(tryBlock: BlockRef, catches: List<CatchHandler>, finallyBlock: BlockRef? = null): Unit
    fun tryCatch(tryBlock: String, catches: List<CatchHandler>, finallyBlock: String? = null): Unit =
        tryCatch(BlockRef(tryBlock), catches, finallyBlock?.let { BlockRef(it) })

    /**
     * Writes a value to an instance field of an object.
     *
     * Emits a [PutField] instruction into the current block.
     *
     * @param obj source operand
     * @param className String
     * @param fieldName String
     * @param fieldType the type
     * @param value source operand
     */
    fun putField(obj: Value, className: String, fieldName: String, fieldType: Type, value: Value): Unit

    /**
     * Writes a value to a static (class-level) field.
     *
     * Emits a [PutStatic] instruction into the current block.
     *
     * @param className String
     * @param fieldName String
     * @param fieldType the type
     * @param value source operand
     */
    fun putStatic(className: String, fieldName: String, fieldType: Type, value: Value): Unit

    /**
     * Invokes a constructor on an already-allocated object.
     *
     * Emits a [ConstructorCall] instruction into the current block.
     *
     * @param obj source operand
     * @param className String
     * @param constructorType the type
     * @param args list of source operands
     */
    fun constructorCall(obj: Value, className: String, constructorType: Type.Function, args: List<Value>): Unit

    /**
     * Writes a value to an array at the given index.
     *
     * Emits a [ArraySet] instruction into the current block.
     *
     * @param array source operand
     * @param index source operand
     * @param value source operand
     * @param elementType the type
     */
    fun arraySet(array: Value, index: Value, value: Value, elementType: Type): Unit

    /**
     * Acquires the monitor (intrinsic lock) of an object.
     *
     * Emits a [MonitorEnter] instruction into the current block.
     *
     * @param obj source operand
     */
    fun monitorEnter(obj: Value): Unit

    /**
     * Releases the monitor (intrinsic lock) of an object.
     *
     * Emits a [MonitorExit] instruction into the current block.
     *
     * @param obj source operand
     */
    fun monitorExit(obj: Value): Unit

    /**
     * Branches to different basic blocks based on the tag of a tagged union.
     *
     * Emits a [TagSwitch] instruction into the current block.
     *
     * @param union source operand
     * @param cases List<Pair<String, BlockRef>>
     * @param defaultTarget BlockRef
     */
    fun tagSwitch(union: Value, cases: List<Pair<String, BlockRef>>, defaultTarget: BlockRef? = null): Unit

    /**
     * Clears a weak reference, breaking the association with its referent.
     *
     * Emits a [ClearWeakRef] instruction into the current block.
     *
     * @param weakRef source operand
     */
    fun clearWeakRef(weakRef: Value): Unit
}

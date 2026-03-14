// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for object instructions.
 *
 * Implement this interface to receive callbacks for object instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ObjectInstructionVisitor : InstructionVisitor {

    fun visitThrow(instruction: Throw) {}

    fun visitTryCatchRegion(instruction: TryCatchRegion) {}

    fun visitNewObject(instruction: NewObject) {}

    fun visitNewArray(instruction: NewArray) {}

    fun visitNewMultiArray(instruction: NewMultiArray) {}

    fun visitGetField(instruction: GetField) {}

    fun visitPutField(instruction: PutField) {}

    fun visitGetStatic(instruction: GetStatic) {}

    fun visitPutStatic(instruction: PutStatic) {}

    fun visitVirtualCall(instruction: VirtualCall) {}

    fun visitInterfaceCall(instruction: InterfaceCall) {}

    fun visitSpecialCall(instruction: SpecialCall) {}

    fun visitStaticCall(instruction: StaticCall) {}

    fun visitDynamicCall(instruction: DynamicCall) {}

    fun visitConstructorCall(instruction: ConstructorCall) {}

    fun visitInstanceOf(instruction: InstanceOf) {}

    fun visitCheckCast(instruction: CheckCast) {}

    fun visitTypeId(instruction: TypeId) {}

    fun visitArrayGet(instruction: ArrayGet) {}

    fun visitArraySet(instruction: ArraySet) {}

    fun visitArrayLength(instruction: ArrayLength) {}

    fun visitMonitorEnter(instruction: MonitorEnter) {}

    fun visitMonitorExit(instruction: MonitorExit) {}

    fun visitBox(instruction: Box) {}

    fun visitUnbox(instruction: Unbox) {}

    fun visitClosureCreate(instruction: ClosureCreate) {}

    fun visitClosureInvoke(instruction: ClosureInvoke) {}

    fun visitClosureInvokeOnce(instruction: ClosureInvokeOnce) {}

    fun visitConstructVariant(instruction: ConstructVariant) {}

    fun visitGetTag(instruction: GetTag) {}

    fun visitGetVariantField(instruction: GetVariantField) {}

    fun visitTagSwitch(instruction: TagSwitch) {}

    fun visitCatchValue(instruction: CatchValue) {}

    fun visitMakeWeakRef(instruction: MakeWeakRef) {}

    fun visitReadWeakRef(instruction: ReadWeakRef) {}

    fun visitClearWeakRef(instruction: ClearWeakRef) {}
}

// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.ManagedCallDirection
import org.kgen.ir.Type

/**
 * Emission interface for interop instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface InteropInstructionSet : InstructionSet {

    /**
     * Pins a managed object reference, preventing the GC from relocating it.
     *
     * Emits a [Pin] instruction into the current block.
     *
     * @param ref source operand
     * @return the SSA value produced by this instruction
     */
    fun pin(ref: Value): Value

    /**
     * Computes a pointer to an element within a managed object.
     *
     * Emits a [InteriorPtr] instruction into the current block.
     *
     * @param ref source operand
     * @param index source operand
     * @param pointeeType the type
     * @return the SSA value produced by this instruction
     */
    fun interiorPtr(ref: Value, index: Value, pointeeType: Type): Value

    /**
     * Calls across the managed/native boundary with appropriate transition logic.
     *
     * Emits a [ManagedCall] instruction into the current block.
     *
     * @param function source operand
     * @param args list of source operands
     * @param returnType the type
     * @param direction ManagedCallDirection
     * @return the SSA value produced by this instruction, or null if void
     */
    fun managedCall(function: Value, args: List<Value>, returnType: Type, direction: ManagedCallDirection): Value?

    /**
     * Pins a managed reference into GPU-accessible memory.

The GC must not relocate the object while any pin is active.
Host-side only; requires GcManaged + KernelLaunch capability.
     *
     * Emits a [ManagedToDevice] instruction into the current block.
     *
     * @param ref source operand
     * @param targetAddrSpace Int
     * @return the SSA value produced by this instruction
     */
    fun managedToDevice(ref: Value, targetAddrSpace: Int = 1): Value

    /**
     * Unpins a previously pinned managed object.
     *
     * Emits a [Unpin] instruction into the current block.
     *
     * @param ref source operand
     */
    fun unpin(ref: Value): Unit

    /**
     * Releases a ManagedToDevice pin.

The host code is responsible for calling this after confirming
no in-flight kernel still holds the pointer.
     *
     * Emits a [DeviceRelease] instruction into the current block.
     *
     * @param ptr source operand
     */
    fun deviceRelease(ptr: Value): Unit
}

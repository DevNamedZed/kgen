// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.ManagedCallDirection
import org.kgen.ir.Type

/**
 * Managed/native interop instructions.
 *
 * Interop instructions handle the boundary between managed and native code:
 * pinning objects, computing interior pointers, and performing cross-boundary calls.
 */
sealed interface InteropInstruction : Instruction {
    override val category get() = IrCategory.INTEROP
}

// --- Interop instructions ---

/**
 * Pins a managed object reference, preventing the GC from relocating it.
 *
 * @param dest the SSA result reference
 * @param ref operand value
 */
data class Pin(
    val dest: InstructionRef,
    val ref: Value,
) : InteropInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ref)
}

/**
 * Unpins a previously pinned managed object.
 *
 * @param ref operand value
 */
data class Unpin(
    val ref: Value,
) : InteropInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ref)
}

/**
 * Computes a pointer to an element within a managed object.
 *
 * @param dest the SSA result reference
 * @param ref operand value
 * @param index operand value
 * @param pointeeType configuration
 */
data class InteriorPtr(
    val dest: InstructionRef,
    val ref: Value,
    val index: Value,
    val pointeeType: Type,
) : InteropInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(ref, index)
}

/**
 * Calls across the managed/native boundary with appropriate transition logic.
 *
 * @param dest the SSA result reference, or null for void
 * @param function operand value
 * @param args list of operand values
 * @param returnType configuration
 * @param direction configuration
 */
data class ManagedCall(
    val dest: InstructionRef?,
    val function: Value,
    val args: List<Value>,
    val returnType: Type,
    val direction: ManagedCallDirection,
) : InteropInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.CALL
    override val operands get() = listOf(function) + args
}

// --- GPU memory ---

/**
 * Pins a managed reference into GPU-accessible memory.
 *
 * The GC must not relocate the object while any pin is active.
 * Host-side only; requires GcManaged + KernelLaunch capability.
 *
 * @param dest the SSA result reference
 * @param ref operand value
 * @param targetAddrSpace configuration
 */
data class ManagedToDevice(
    val dest: InstructionRef,
    val ref: Value,
    val targetAddrSpace: Int = 1,
) : InteropInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ref)
}

/**
 * Releases a ManagedToDevice pin.
 *
 * The host code is responsible for calling this after confirming
 * no in-flight kernel still holds the pointer.
 *
 * @param ptr operand value
 */
data class DeviceRelease(
    val ptr: Value,
) : InteropInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr)
}


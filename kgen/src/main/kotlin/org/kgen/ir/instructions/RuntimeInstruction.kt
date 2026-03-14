// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.Type

/**
 * Garbage collection and runtime support instructions.
 *
 * Runtime instructions manage the interaction between compiled code and the
 * managed runtime: heap allocation, GC safepoints, root tracking, write/read
 * barriers, reference counting, and coroutine lifecycle.
 */
sealed interface RuntimeInstruction : Instruction {
    override val category get() = IrCategory.RUNTIME
}

// --- GC and memory management ---

/**
 * Allocates an object of the given type on the garbage-collected heap.
 *
 * @param dest the SSA result reference
 * @param allocType configuration
 * @param size optional operand value
 */
data class GCAlloc(
    val dest: InstructionRef,
    val allocType: Type,
    val size: Value? = null,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.GC_ALLOC
    override val operands get() = listOfNotNull(size)
}

/**
 * Inserts a GC safepoint poll.
 */
data class GCSafepoint(
    val dummy: Unit = Unit,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.SAFEPOINT
    override val operands get() = emptyList<Value>()
}

/**
 * Registers a pointer as a GC root so the collector can trace through it.
 *
 * @param ptr operand value
 * @param metadata optional operand value
 */
data class GCRoot(
    val ptr: Value,
    val metadata: Value?,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOfNotNull(ptr, metadata)
}

/**
 * Emits a write barrier for a reference store into an object field.
 *
 * @param obj operand value
 * @param fieldIndex operand value
 * @param value operand value
 */
data class WriteBarrier(
    val obj: Value,
    val fieldIndex: Value,
    val value: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.WRITE_BARRIER
    override val operands get() = listOf(obj, fieldIndex, value)
}

/**
 * Emits a read barrier for loading a reference from an object field.
 *
 * @param dest the SSA result reference
 * @param ref operand value
 */
data class ReadBarrier(
    val dest: InstructionRef,
    val ref: Value,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = listOf(ref)
}

// --- Reference counting ---

/**
 * Increments the reference count of a reference-counted object.
 *
 * @param obj operand value
 */
data class RefRetain(
    val obj: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(obj)
}

/**
 * Decrements the reference count of a reference-counted object.
 *
 * @param obj operand value
 */
data class RefRelease(
    val obj: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(obj)
}

/**
 * Reads the current reference count of a reference-counted object.
 *
 * @param dest the SSA result reference
 * @param obj operand value
 */
data class RefCount(
    val dest: InstructionRef,
    val obj: Value,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_HEAP_MEMORY)
    override val operands get() = listOf(obj)
}

// --- Coroutine lifecycle ---

/**
 * Begins a coroutine, creating a handle from an ID and memory buffer.
 *
 * @param dest the SSA result reference
 * @param id operand value
 * @param mem operand value
 */
data class CoroBegin(
    val dest: InstructionRef,
    val id: Value,
    val mem: Value,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(id, mem)
}

/**
 * Marks the end of a coroutine's execution.
 *
 * @param handle operand value
 * @param unwind flag (default: false)
 */
data class CoroEnd(
    val handle: Value,
    val unwind: Boolean = false,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(handle)
}

/**
 * Suspends a coroutine, yielding control to the caller or resumer.
 *
 * @param dest the SSA result reference
 * @param save optional operand value
 * @param isFinal flag (default: false)
 */
data class CoroSuspend(
    val dest: InstructionRef,
    val save: Value?,
    val isFinal: Boolean = false,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.IS_SAFEPOINT or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOfNotNull(save)
}

/**
 * Resumes a suspended coroutine from its last suspension point.
 *
 * @param handle operand value
 */
data class CoroResume(
    val handle: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(handle)
}

/**
 * Destroys a coroutine, releasing its frame memory.
 *
 * @param handle operand value
 */
data class CoroDestroy(
    val handle: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_HEAP_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(handle)
}

/**
 * Returns the size in bytes required for a coroutine frame.
 *
 * @param dest the SSA result reference
 */
data class CoroSize(
    val dest: InstructionRef,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = emptyList<Value>()
}

// --- GC relocate ---

/**
 * Produce post-safepoint SSA value for a potentially-relocated reference.
 *
 * After any instruction with `isSafepoint=true`, live GC references may have
 * been moved by a compacting collector. GCRelocate produces a new SSA value
 * representing the post-safepoint location. For non-moving GC strategies,
 * GCRelocate is the identity and is eliminated.
 *
 * @param dest the SSA result reference
 * @param safepoint operand value
 * @param base operand value
 * @param derived operand value
 */
data class GCRelocate(
    val dest: InstructionRef,
    val safepoint: Value,
    val base: Value,
    val derived: Value,
) : RuntimeInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(safepoint, base, derived)
}


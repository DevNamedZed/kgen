// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Emission interface for runtime instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface RuntimeInstructionSet : InstructionSet {

    /**
     * Allocates an object of the given type on the garbage-collected heap.
     *
     * Emits a [GCAlloc] instruction into the current block.
     *
     * @param allocType the type
     * @param size optional source operand
     * @return the SSA value produced by this instruction
     */
    fun gcAlloc(allocType: Type, size: Value? = null): Value

    /**
     * Emits a read barrier for loading a reference from an object field.
     *
     * Emits a [ReadBarrier] instruction into the current block.
     *
     * @param ref source operand
     * @return the SSA value produced by this instruction
     */
    fun readBarrier(ref: Value): Value

    /**
     * Reads the current reference count of a reference-counted object.
     *
     * Emits a [RefCount] instruction into the current block.
     *
     * @param obj source operand
     * @return the SSA value produced by this instruction
     */
    fun refCount(obj: Value): Value

    /**
     * Begins a coroutine, creating a handle from an ID and memory buffer.
     *
     * Emits a [CoroBegin] instruction into the current block.
     *
     * @param id source operand
     * @param mem source operand
     * @return the SSA value produced by this instruction
     */
    fun coroBegin(id: Value, mem: Value): Value

    /**
     * Suspends a coroutine, yielding control to the caller or resumer.
     *
     * Emits a [CoroSuspend] instruction into the current block.
     *
     * @param save optional source operand
     * @param isFinal Boolean
     * @return the SSA value produced by this instruction
     */
    fun coroSuspend(save: Value?, isFinal: Boolean = false): Value

    /**
     * Returns the size in bytes required for a coroutine frame.
     *
     * Emits a [CoroSize] instruction into the current block.
     * @return the SSA value produced by this instruction
     */
    fun coroSize(): Value

    /**
     * Produce post-safepoint SSA value for a potentially-relocated reference.

After any instruction with `isSafepoint=true`, live GC references may have
been moved by a compacting collector. GCRelocate produces a new SSA value
representing the post-safepoint location. For non-moving GC strategies,
GCRelocate is the identity and is eliminated.
     *
     * Emits a [GCRelocate] instruction into the current block.
     *
     * @param safepoint source operand
     * @param base source operand
     * @param derived source operand
     * @return the SSA value produced by this instruction
     */
    fun gCRelocate(safepoint: Value, base: Value, derived: Value): Value

    /**
     * Inserts a GC safepoint poll.
     *
     * Emits a [GCSafepoint] instruction into the current block.
     */
    fun gcSafepoint(): Unit

    /**
     * Registers a pointer as a GC root so the collector can trace through it.
     *
     * Emits a [GCRoot] instruction into the current block.
     *
     * @param ptr source operand
     * @param metadata optional source operand
     */
    fun gcRoot(ptr: Value, metadata: Value?): Unit

    /**
     * Emits a write barrier for a reference store into an object field.
     *
     * Emits a [WriteBarrier] instruction into the current block.
     *
     * @param obj source operand
     * @param fieldIndex source operand
     * @param value source operand
     */
    fun writeBarrier(obj: Value, fieldIndex: Value, value: Value): Unit

    /**
     * Increments the reference count of a reference-counted object.
     *
     * Emits a [RefRetain] instruction into the current block.
     *
     * @param obj source operand
     */
    fun refRetain(obj: Value): Unit

    /**
     * Decrements the reference count of a reference-counted object.
     *
     * Emits a [RefRelease] instruction into the current block.
     *
     * @param obj source operand
     */
    fun refRelease(obj: Value): Unit

    /**
     * Marks the end of a coroutine's execution.
     *
     * Emits a [CoroEnd] instruction into the current block.
     *
     * @param handle source operand
     * @param unwind Boolean
     */
    fun coroEnd(handle: Value, unwind: Boolean = false): Unit

    /**
     * Resumes a suspended coroutine from its last suspension point.
     *
     * Emits a [CoroResume] instruction into the current block.
     *
     * @param handle source operand
     */
    fun coroResume(handle: Value): Unit

    /**
     * Destroys a coroutine, releasing its frame memory.
     *
     * Emits a [CoroDestroy] instruction into the current block.
     *
     * @param handle source operand
     */
    fun coroDestroy(handle: Value): Unit
}

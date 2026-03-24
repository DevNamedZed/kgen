// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.AtomicRMWOp

/**
 * Emission interface for atomic instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface AtomicInstructionSet : InstructionSet {

    /**
     * Atomic compare-and-exchange: `dest = cmpxchg ptr, cmp, new`.

Atomically reads the value at ptr, compares to cmp, and if equal writes new.
     *
     * Emits a [CmpXchg] instruction into the current block.
     *
     * @param ptr source operand
     * @param cmp source operand
     * @param new source operand
     * @param successOrdering AtomicOrdering
     * @param failureOrdering AtomicOrdering
     * @param weak Boolean
     * @param volatile whether this is a volatile memory access
     * @return the SSA value produced by this instruction
     */
    fun cmpXchg(ptr: Value, cmp: Value, new: Value, successOrdering: AtomicOrdering, failureOrdering: AtomicOrdering, weak: Boolean = false, volatile: Boolean = false): Value

    /**
     * Atomic read-modify-write: `dest = atomicrmw op ptr, value`.
     *
     * Emits a [AtomicRMW] instruction into the current block.
     *
     * @param op AtomicRMWOp
     * @param ptr source operand
     * @param value source operand
     * @param ordering the memory ordering for this atomic operation
     * @param volatile whether this is a volatile memory access
     * @return the SSA value produced by this instruction
     */
    fun atomicRMW(op: AtomicRMWOp, ptr: Value, value: Value, ordering: AtomicOrdering, volatile: Boolean = false): Value

    /**
     * Memory fence: enforces an ordering constraint without accessing memory.
     *
     * Emits a [Fence] instruction into the current block.
     *
     * @param ordering the memory ordering for this atomic operation
     * @param syncScope String
     */
    fun fence(ordering: AtomicOrdering, syncScope: String? = null): Unit
}

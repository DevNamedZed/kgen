// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.AtomicRMWOp

/**
 * Atomic synchronization instructions.
 *
 * Atomic instructions provide hardware-level guarantees for concurrent memory access.
 * They include memory fences, compare-and-swap, and atomic read-modify-write operations.
 */
sealed interface AtomicInstruction : Instruction {
    override val category get() = IrCategory.ATOMIC
}

// --- Atomic instructions ---

/**
 * Memory fence: enforces an ordering constraint without accessing memory.
 *
 * @param ordering configuration
 * @param syncScope configuration
 */
data class Fence(
    val ordering: AtomicOrdering,
    val syncScope: String? = null,
) : AtomicInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.FENCE
    override val operands get() = emptyList<Value>()
}

/**
 * Atomic compare-and-exchange: `dest = cmpxchg ptr, cmp, new`.
 *
 * Atomically reads the value at ptr, compares to cmp, and if equal writes new.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param cmp operand value
 * @param new operand value
 * @param successOrdering configuration
 * @param failureOrdering configuration
 * @param weak flag (default: false)
 * @param volatile flag (default: false)
 */
data class CmpXchg(
    val dest: InstructionRef,
    val ptr: Value,
    val cmp: Value,
    val new: Value,
    val successOrdering: AtomicOrdering,
    val failureOrdering: AtomicOrdering,
    val weak: Boolean = false,
    val volatile: Boolean = false,
) : AtomicInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.ATOMIC_RMW
    override val operands get() = listOf(ptr, cmp, new)
}

/**
 * Atomic read-modify-write: `dest = atomicrmw op ptr, value`.
 *
 * @param dest the SSA result reference
 * @param op configuration
 * @param ptr operand value
 * @param value operand value
 * @param ordering configuration
 * @param volatile flag (default: false)
 */
data class AtomicRMW(
    val dest: InstructionRef,
    val op: AtomicRMWOp,
    val ptr: Value,
    val value: Value,
    val ordering: AtomicOrdering,
    val volatile: Boolean = false,
) : AtomicInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.ATOMIC_RMW
    override val operands get() = listOf(ptr, value)
}


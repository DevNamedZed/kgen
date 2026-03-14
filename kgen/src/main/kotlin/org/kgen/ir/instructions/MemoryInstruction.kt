// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.Type

/**
 * Memory access and manipulation instructions.
 *
 * Memory instructions cover stack allocation, loads, stores, pointer arithmetic,
 * bulk memory operations (copy/set/move), and lifetime/variadic-argument management.
 */
sealed interface MemoryInstruction : Instruction {
    override val category get() = IrCategory.MEMORY
}

// --- Core memory operations ---

/**
 * Allocate space on the stack: `dest = alloca allocType [, numElements] [, align]`.
 *
 * @param dest the SSA result reference
 * @param allocType configuration
 * @param numElements optional operand value
 * @param align configuration
 */
data class Alloca(
    val dest: InstructionRef,
    val allocType: Type,
    val numElements: Value? = null,
    val align: Int? = null,
) : MemoryInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_STACK_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOfNotNull(numElements)
}

/**
 * Load a value from memory: `dest = *ptr`.
 *
 * @param dest the SSA result reference
 * @param ptr operand value
 * @param loadType configuration
 * @param align configuration
 * @param volatile flag (default: false)
 * @param ordering configuration
 */
data class Load(
    val dest: InstructionRef,
    val ptr: Value,
    val loadType: Type,
    val align: Int? = null,
    val volatile: Boolean = false,
    val ordering: AtomicOrdering? = null,
) : MemoryInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.READS_HEAP
    override val operands get() = listOf(ptr)
}

/**
 * Store a value to memory: `*ptr = value`.
 *
 * @param value operand value
 * @param ptr operand value
 * @param align configuration
 * @param volatile flag (default: false)
 * @param ordering configuration
 */
data class Store(
    val value: Value,
    val ptr: Value,
    val align: Int? = null,
    val volatile: Boolean = false,
    val ordering: AtomicOrdering? = null,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.WRITES_HEAP
    override val operands get() = listOf(value, ptr)
}

/**
 * Compute an element address within an aggregate or array.
 *
 * @param dest the SSA result reference
 * @param baseType configuration
 * @param ptr operand value
 * @param indices list of operand values
 * @param inBounds flag (default: true)
 */
data class GetElementPtr(
    val dest: InstructionRef,
    val baseType: Type,
    val ptr: Value,
    val indices: List<Value>,
    val inBounds: Boolean = true,
) : MemoryInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(ptr) + indices
}

// --- Bulk memory operations ---

/**
 * Copy a block of memory: `memcpy(dst, src, len)`.
 *
 * Source and destination must not overlap.
 *
 * @param dst operand value
 * @param src operand value
 * @param len operand value
 * @param volatile flag (default: false)
 */
data class MemCpy(
    val dst: Value,
    val src: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.READ_WRITE_HEAP
    override val operands get() = listOf(dst, src, len)
}

/**
 * Fill a block of memory with a byte value: `memset(dst, value, len)`.
 *
 * @param dst operand value
 * @param value operand value
 * @param len operand value
 * @param volatile flag (default: false)
 */
data class MemSet(
    val dst: Value,
    val value: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.WRITES_HEAP
    override val operands get() = listOf(dst, value, len)
}

/**
 * Copy a block of memory, handling overlapping regions.
 *
 * @param dst operand value
 * @param src operand value
 * @param len operand value
 * @param volatile flag (default: false)
 */
data class MemMove(
    val dst: Value,
    val src: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.READ_WRITE_HEAP
    override val operands get() = listOf(dst, src, len)
}

/**
 * Prefetch a cache line: hint to the processor that address will be accessed soon.
 *
 * @param address operand value
 * @param rw configuration
 * @param locality configuration
 * @param cacheType configuration
 */
data class Prefetch(
    val address: Value,
    val rw: Int,
    val locality: Int,
    val cacheType: Int,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(address)
}

// --- Stack management ---

/**
 * Save the current stack pointer.
 *
 * @param dest the SSA result reference
 */
data class StackSave(
    val dest: InstructionRef,
) : MemoryInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_STACK_MEMORY)
    override val operands get() = emptyList<Value>()
}

/**
 * Restore a previously saved stack pointer.
 *
 * @param ptr operand value
 */
data class StackRestore(
    val ptr: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_STACK_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(ptr)
}

/**
 * Mark the beginning of a stack object's lifetime.
 *
 * @param ptr operand value
 * @param size configuration
 */
data class LifetimeStart(
    val ptr: Value,
    val size: Long,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(ptr)
}

/**
 * Mark the end of a stack object's lifetime.
 *
 * @param ptr operand value
 * @param size configuration
 */
data class LifetimeEnd(
    val ptr: Value,
    val size: Long,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(ptr)
}

// --- Variadic argument support ---

/**
 * Initialize a variadic argument list.
 *
 * @param argList operand value
 */
data class VAStart(
    val argList: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.WRITES_STACK_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(argList)
}

/**
 * Clean up a variadic argument list.
 *
 * @param argList operand value
 */
data class VAEnd(
    val argList: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(argList)
}

/**
 * Copy a variadic argument list.
 *
 * @param dst operand value
 * @param src operand value
 */
data class VACopy(
    val dst: Value,
    val src: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
    override val effects get() = InstructionEffects(InstructionEffects.READS_STACK_MEMORY or InstructionEffects.WRITES_STACK_MEMORY or InstructionEffects.HAS_SIDE_EFFECTS)
    override val operands get() = listOf(dst, src)
}

/**
 * Retrieve the next variadic argument.
 *
 * @param dest the SSA result reference
 * @param argList operand value
 * @param argType configuration
 */
data class VAArg(
    val dest: InstructionRef,
    val argList: Value,
    val argType: Type,
) : MemoryInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects(InstructionEffects.READS_STACK_MEMORY)
    override val operands get() = listOf(argList)
}


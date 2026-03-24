// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.AtomicOrdering
import org.kgen.ir.Type

/**
 * Emission interface for memory instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface MemoryInstructionSet : InstructionSet {

    /**
     * Allocate space on the stack: `dest = alloca allocType [, numElements] [, align]`.
     *
     * Emits a [Alloca] instruction into the current block.
     *
     * @param allocType the type
     * @param numElements optional source operand
     * @param align memory alignment in bytes
     * @return the SSA value produced by this instruction
     */
    fun alloca(allocType: Type, numElements: Value? = null, align: Int? = null): Value

    /**
     * Load a value from memory: `dest = *ptr`.
     *
     * Emits a [Load] instruction into the current block.
     *
     * @param ptr source operand
     * @param loadType the type
     * @param align memory alignment in bytes
     * @param volatile whether this is a volatile memory access
     * @param ordering the memory ordering for this atomic operation
     * @return the SSA value produced by this instruction
     */
    fun load(ptr: Value, loadType: Type, align: Int? = null, volatile: Boolean = false, ordering: AtomicOrdering? = null): Value

    /**
     * Compute an element address within an aggregate or array.
     *
     * Emits a [GetElementPtr] instruction into the current block.
     *
     * @param baseType the type
     * @param ptr source operand
     * @param indices list of source operands
     * @param inBounds Boolean
     * @return the SSA value produced by this instruction
     */
    fun gep(baseType: Type, ptr: Value, indices: List<Value>, inBounds: Boolean = true): Value

    /**
     * Save the current stack pointer.
     *
     * Emits a [StackSave] instruction into the current block.
     * @return the SSA value produced by this instruction
     */
    fun stackSave(): Value

    /**
     * Retrieve the next variadic argument.
     *
     * Emits a [VAArg] instruction into the current block.
     *
     * @param argList source operand
     * @param argType the type
     * @return the SSA value produced by this instruction
     */
    fun vaArg(argList: Value, argType: Type): Value

    /**
     * Store a value to memory: `*ptr = value`.
     *
     * Emits a [Store] instruction into the current block.
     *
     * @param value source operand
     * @param ptr source operand
     * @param align memory alignment in bytes
     * @param volatile whether this is a volatile memory access
     * @param ordering the memory ordering for this atomic operation
     */
    fun store(value: Value, ptr: Value, align: Int? = null, volatile: Boolean = false, ordering: AtomicOrdering? = null): Unit

    /**
     * Copy a block of memory: `memcpy(dst, src, len)`.

Source and destination must not overlap.
     *
     * Emits a [MemCpy] instruction into the current block.
     *
     * @param dst source operand
     * @param src source operand
     * @param len source operand
     * @param volatile whether this is a volatile memory access
     */
    fun memcpy(dst: Value, src: Value, len: Value, volatile: Boolean = false): Unit

    /**
     * Fill a block of memory with a byte value: `memset(dst, value, len)`.
     *
     * Emits a [MemSet] instruction into the current block.
     *
     * @param dst source operand
     * @param value source operand
     * @param len source operand
     * @param volatile whether this is a volatile memory access
     */
    fun memset(dst: Value, value: Value, len: Value, volatile: Boolean = false): Unit

    /**
     * Copy a block of memory, handling overlapping regions.
     *
     * Emits a [MemMove] instruction into the current block.
     *
     * @param dst source operand
     * @param src source operand
     * @param len source operand
     * @param volatile whether this is a volatile memory access
     */
    fun memmove(dst: Value, src: Value, len: Value, volatile: Boolean = false): Unit

    /**
     * Prefetch a cache line: hint to the processor that address will be accessed soon.
     *
     * Emits a [Prefetch] instruction into the current block.
     *
     * @param address source operand
     * @param rw Int
     * @param locality Int
     * @param cacheType the type
     */
    fun prefetch(address: Value, rw: Int, locality: Int, cacheType: Int): Unit

    /**
     * Restore a previously saved stack pointer.
     *
     * Emits a [StackRestore] instruction into the current block.
     *
     * @param ptr source operand
     */
    fun stackRestore(ptr: Value): Unit

    /**
     * Mark the beginning of a stack object's lifetime.
     *
     * Emits a [LifetimeStart] instruction into the current block.
     *
     * @param ptr source operand
     * @param size Long
     */
    fun lifetimeStart(ptr: Value, size: Long): Unit

    /**
     * Mark the end of a stack object's lifetime.
     *
     * Emits a [LifetimeEnd] instruction into the current block.
     *
     * @param ptr source operand
     * @param size Long
     */
    fun lifetimeEnd(ptr: Value, size: Long): Unit

    /**
     * Initialize a variadic argument list.
     *
     * Emits a [VAStart] instruction into the current block.
     *
     * @param argList source operand
     */
    fun vaStart(argList: Value): Unit

    /**
     * Clean up a variadic argument list.
     *
     * Emits a [VAEnd] instruction into the current block.
     *
     * @param argList source operand
     */
    fun vaEnd(argList: Value): Unit

    /**
     * Copy a variadic argument list.
     *
     * Emits a [VACopy] instruction into the current block.
     *
     * @param dst source operand
     * @param src source operand
     */
    fun vaCopy(dst: Value, src: Value): Unit
}

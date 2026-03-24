package org.kgen.ir.build.extensions

import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.build.sets.MemoryInstructionSet

/**
 * A reference to a stack-allocated variable, created by [MemoryExtensions.variable].
 *
 * Wraps the alloca pointer and the variable's element type for use with
 * [MemoryExtensions.get] and [MemoryExtensions.set].
 */
data class VarRef(
    val pointer: Value,
    val type: Type,
)

/**
 * Sugar methods for memory operations. Extends [MemoryInstructionSet]
 * with a variable abstraction (alloca + load/store).
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * VarRef counter = ins.variable(Type.i32(0));           // alloca + store
 * Value current = ins.get(counter);                      // load
 * ins.set(counter, ins.add(current, Type.i32(1)));      // store
 * ```
 */
interface MemoryExtensions : MemoryInstructionSet {

    fun variable(initialValue: Value): VarRef {
        val pointer = alloca(initialValue.type)
        store(initialValue, pointer)
        return VarRef(pointer, initialValue.type)
    }

    fun variable(type: Type, initialValue: Value): VarRef {
        val pointer = alloca(type)
        store(initialValue, pointer)
        return VarRef(pointer, type)
    }

    fun get(ref: VarRef): Value = load(ref.pointer, ref.type)

    fun set(ref: VarRef, value: Value) {
        store(value, ref.pointer)
    }
}

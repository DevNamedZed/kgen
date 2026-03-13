package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Result of querying whether two memory locations alias.
 *
 * Ordered from strongest to weakest guarantee:
 * - [NoAlias]: locations never overlap — safe to reorder/eliminate
 * - [MustAlias]: locations always refer to the same memory
 * - [PartialAlias]: locations partially overlap (e.g., struct field within struct)
 * - [MayAlias]: cannot determine — must assume they might overlap
 */
enum class AliasResult {
    NoAlias,
    MustAlias,
    PartialAlias,
    MayAlias,
}

/**
 * Alias analysis interface — determines whether two pointer values
 * may refer to overlapping memory locations.
 *
 * Implementations provide varying precision. The framework is designed
 * so that passes (LICM, GVN, DSE) can query alias information without
 * caring which analysis backs it.
 *
 * ```java
 * var aa = new BasicAliasAnalysis(fn);
 * if (aa.alias(loadPtr, storePtr) == AliasResult.NoAlias) {
 *     // safe to reorder or hoist the load past the store
 * }
 * ```
 */
interface AliasAnalysis {

    /**
     * Query whether [a] and [b] may refer to overlapping memory.
     */
    fun alias(a: Value, b: Value): AliasResult

    /**
     * Returns true if [inst] may read from memory.
     */
    fun readsMemory(inst: Instruction): Boolean = when (inst) {
        is Load -> true
        is MemCpy -> true
        is MemMove -> true
        is Call -> true
        is Invoke -> true
        else -> false
    }

    /**
     * Returns true if [inst] may write to memory.
     */
    fun writesMemory(inst: Instruction): Boolean = when (inst) {
        is Store -> true
        is MemCpy -> true
        is MemSet -> true
        is MemMove -> true
        is Call -> true
        is Invoke -> true
        else -> false
    }

    /**
     * Returns the pointer operand for a memory instruction, or null.
     */
    fun memoryPointer(inst: Instruction): Value? = when (inst) {
        is Load -> inst.ptr
        is Store -> inst.ptr
        else -> null
    }
}

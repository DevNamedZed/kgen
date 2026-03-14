package org.kgen.ir.instructions

/**
 * Effect-based instruction ordering rules.
 *
 * Two instructions must maintain their relative program order when any of the
 * [7 ordering rules][mustOrder] holds. Instructions that violate none of these
 * rules commute freely and may be reordered by any optimization pass.
 *
 * These rules encode the semantic constraints that all IR transformations must
 * respect. They replace ad-hoc ordering checks scattered across passes with a
 * single authoritative source.
 *
 * ```java
 * if (EffectOrdering.mustOrder(earlier, later)) {
 *     // cannot reorder — maintain program order
 * } else {
 *     // safe to reorder
 * }
 * ```
 *
 * @see InstructionEffects
 * @see EffectComputation
 */
object EffectOrdering {

    /**
     * Returns `true` if [first] and [second] must maintain their relative ordering.
     *
     * This applies all 7 ordering rules from the effect system spec. If any rule
     * fires, the instructions cannot be reordered. If none fire, the instructions
     * commute freely.
     *
     * This uses the **static** effects from [Instruction.effects]. For more precise
     * analysis, use [mustOrderRefined] which applies dynamic effect refinement.
     *
     * @param first the instruction that appears earlier in program order
     * @param second the instruction that appears later in program order
     * @return true if the ordering must be preserved
     */
    @JvmStatic
    fun mustOrder(first: Instruction, second: Instruction): Boolean {
        return mustOrderWithEffects(first, first.effects, second, second.effects)
    }

    /**
     * Returns `true` if [first] and [second] must maintain their relative ordering,
     * using dynamically refined effects from [EffectComputation].
     *
     * This is more precise than [mustOrder] but slightly more expensive. Use this
     * in passes that benefit from the extra precision (barrier elimination, LICM).
     */
    @JvmStatic
    fun mustOrderRefined(first: Instruction, second: Instruction): Boolean {
        val firstEffects = EffectComputation.computeEffects(first)
        val secondEffects = EffectComputation.computeEffects(second)
        return mustOrderWithEffects(first, firstEffects, second, secondEffects)
    }

    private fun mustOrderWithEffects(
        first: Instruction,
        firstEffects: InstructionEffects,
        second: Instruction,
        secondEffects: InstructionEffects,
    ): Boolean {
        // Rule 1: Either has hasSideEffects=true
        if (firstEffects.hasSideEffects() || secondEffects.hasSideEffects()) {
            return true
        }

        // Rule 2: A safepoint may not be moved across an instruction that creates,
        // consumes, or transforms a managed reference value. Static approximation:
        // any instruction that reads or produces a Reference type.
        if (hasSafepointReferenceConflict(first, firstEffects, second, secondEffects)) {
            return true
        }

        // Rule 3: Either is a barrier and the other accesses managed heap memory
        if (hasBarrierHeapConflict(firstEffects, secondEffects)) {
            return true
        }

        // Rule 4: Both access the same alias region with at least one write.
        // Without alias analysis, conservatively assume any overlapping memory
        // access with a write is a conflict.
        if (hasMemoryConflict(firstEffects, secondEffects)) {
            return true
        }

        // Rule 5: Either is a terminator, branch, or return
        if (isControlFlow(firstEffects) || isControlFlow(secondEffects)) {
            return true
        }

        // Rule 6: Either has canThrow or canTrap
        if (firstEffects.canThrow() || firstEffects.canTrap() ||
            secondEffects.canThrow() || secondEffects.canTrap()
        ) {
            return true
        }

        // Rule 7: Either is divergent and the other is a ComputeBarrier or ComputeFence
        if (hasDivergenceBarrierConflict(first, firstEffects, second, secondEffects)) {
            return true
        }

        return false
    }

    private fun hasSafepointReferenceConflict(
        first: Instruction,
        firstEffects: InstructionEffects,
        second: Instruction,
        secondEffects: InstructionEffects,
    ): Boolean {
        if (firstEffects.isSafepoint() && touchesReference(second)) {
            return true
        }
        if (secondEffects.isSafepoint() && touchesReference(first)) {
            return true
        }
        return false
    }

    private fun touchesReference(instruction: Instruction): Boolean {
        val result = instruction.result
        if (result != null && isReferenceType(result.type)) {
            return true
        }
        for (operand in instruction.operands) {
            if (isReferenceType(operand.type)) {
                return true
            }
        }
        return false
    }

    private fun isReferenceType(type: org.kgen.ir.Type): Boolean {
        return type is org.kgen.ir.Type.Reference || type is org.kgen.ir.Type.WeakReference
    }

    private fun hasBarrierHeapConflict(
        firstEffects: InstructionEffects,
        secondEffects: InstructionEffects,
    ): Boolean {
        if (firstEffects.isBarrier() && accessesHeap(secondEffects)) {
            return true
        }
        if (secondEffects.isBarrier() && accessesHeap(firstEffects)) {
            return true
        }
        return false
    }

    private fun accessesHeap(effects: InstructionEffects): Boolean {
        return effects.readsHeapMemory() || effects.writesHeapMemory()
    }

    private fun hasMemoryConflict(
        firstEffects: InstructionEffects,
        secondEffects: InstructionEffects,
    ): Boolean {
        val firstReads = firstEffects.readsMemory()
        val firstWrites = firstEffects.writesMemory()
        val secondReads = secondEffects.readsMemory()
        val secondWrites = secondEffects.writesMemory()

        if (!firstReads && !firstWrites) {
            return false
        }
        if (!secondReads && !secondWrites) {
            return false
        }

        // At least one must write for a conflict
        return firstWrites || secondWrites
    }

    private fun isControlFlow(effects: InstructionEffects): Boolean {
        return effects.isTerminator() || effects.isBranch() || effects.isReturn()
    }

    private fun hasDivergenceBarrierConflict(
        first: Instruction,
        firstEffects: InstructionEffects,
        second: Instruction,
        secondEffects: InstructionEffects,
    ): Boolean {
        if (firstEffects.isDivergent() && isComputeSyncInstruction(second)) {
            return true
        }
        if (secondEffects.isDivergent() && isComputeSyncInstruction(first)) {
            return true
        }
        return false
    }

    private fun isComputeSyncInstruction(instruction: Instruction): Boolean {
        return instruction is ComputeBarrier || instruction is ComputeFence
    }
}

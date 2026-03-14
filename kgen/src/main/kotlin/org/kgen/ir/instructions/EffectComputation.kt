package org.kgen.ir.instructions

import org.kgen.ir.Type

/**
 * Dynamic effect refinement for IR instructions.
 *
 * The static [Instruction.effects] property returns worst-case effects for each opcode.
 * This object provides [computeEffects] which narrows those effects based on the
 * instruction's actual operand types and flags.
 *
 * Dynamic refinement may only **clear** effects — it never introduces effects stronger
 * than the static declaration. The static effects are always a conservative upper bound.
 *
 * Passes that need precision (barrier elimination, safepoint insertion, poison analysis)
 * use [computeEffects]. Passes that need only fast structural queries use the static
 * [Instruction.effects] directly.
 *
 * ```java
 * InstructionEffects refined = EffectComputation.computeEffects(instruction);
 * if (refined.isPure()) {
 *     // safe to eliminate
 * }
 * ```
 */
object EffectComputation {

    /**
     * Computes refined effects for [instruction] based on its operand types and flags.
     *
     * The result is always a subset of [instruction]'s static effects — bits may be
     * cleared but never set. For most instructions this returns the static effects
     * unchanged. The following instructions have dynamic refinement:
     *
     * - **WriteBarrier**: if the stored value is not a reference type, the barrier is
     *   unnecessary and all effects are cleared (returns [InstructionEffects.PURE]).
     * - **Add/Sub/Mul** with `nuw=false` and `nsw=false`: cannot produce poison, so
     *   these remain pure (no change from static). With overflow flags, the static
     *   effects already capture the correct behavior.
     */
    @JvmStatic
    fun computeEffects(instruction: Instruction): InstructionEffects {
        return when (instruction) {
            is WriteBarrier -> computeWriteBarrierEffects(instruction)
            else -> instruction.effects
        }
    }

    private fun computeWriteBarrierEffects(barrier: WriteBarrier): InstructionEffects {
        val valueType = barrier.value.type
        if (valueType !is Type.Reference && valueType !is Type.WeakReference) {
            return InstructionEffects.PURE
        }
        return barrier.effects
    }
}

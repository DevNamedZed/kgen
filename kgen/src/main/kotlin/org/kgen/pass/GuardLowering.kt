package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Lowers floating [Guard] and [FixedGuard] instructions to explicit control flow.
 *
 * Each Guard becomes a [CondBr] that branches to a deoptimization stub block
 * on failure, or falls through on success. The deopt stub block contains a
 * [Deoptimize] instruction with the Guard's reason, action, and frame state.
 *
 * **Before:**
 * ```
 * entry:
 *   Guard %cond, reason=NULL_CHECK, action=INVALIDATE_REPROFILE, frameState=%fs
 *   ... rest of block ...
 * ```
 *
 * **After:**
 * ```
 * entry:
 *   CondBr %cond, continue_0, deopt_0
 * continue_0:
 *   ... rest of block ...
 * deopt_0:
 *   Deoptimize reason=NULL_CHECK, action=INVALIDATE_REPROFILE, frameState=%fs
 * ```
 */
class GuardLowering : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) {
                fn
            } else {
                lowerGuards(fn)
            }
        })
    }

    private fun lowerGuards(fn: IrFunction): IrFunction {
        val hasGuards = fn.blocks.any { block ->
            block.instructions.any { it is Guard || it is FixedGuard }
        }
        if (!hasGuards) {
            return fn
        }

        var deoptCounter = 0
        val newBlocks = mutableListOf<BasicBlock>()

        for (block in fn.blocks) {
            val guardIndices = block.instructions.indices.filter {
                block.instructions[it] is Guard || block.instructions[it] is FixedGuard
            }

            if (guardIndices.isEmpty()) {
                newBlocks.add(block)
                continue
            }

            var currentLabel = block.label
            var startIndex = 0

            for (guardIndex in guardIndices) {
                val guard = block.instructions[guardIndex]
                val continueLabel = "guard_continue_$deoptCounter"
                val deoptLabel = "guard_deopt_$deoptCounter"
                deoptCounter++

                val condition: Value
                val negated: Boolean
                val reason: DeoptReason
                val action: DeoptAction
                val speculation: SpeculationId?
                val frameState: Value

                when (guard) {
                    is Guard -> {
                        condition = guard.condition
                        negated = guard.negated
                        reason = guard.reason
                        action = guard.action
                        speculation = guard.speculation
                        frameState = guard.frameState
                    }
                    is FixedGuard -> {
                        condition = guard.condition
                        negated = guard.negated
                        reason = guard.reason
                        action = guard.action
                        speculation = null
                        frameState = guard.frameState
                    }
                    else -> continue
                }

                val trueTarget: BlockRef
                val falseTarget: BlockRef
                if (negated) {
                    trueTarget = BlockRef(deoptLabel)
                    falseTarget = BlockRef(continueLabel)
                } else {
                    trueTarget = BlockRef(continueLabel)
                    falseTarget = BlockRef(deoptLabel)
                }

                val preGuardInstructions = block.instructions.subList(startIndex, guardIndex).toMutableList()
                preGuardInstructions.add(CondBr(condition, trueTarget, falseTarget))
                newBlocks.add(BasicBlock(currentLabel, preGuardInstructions))

                newBlocks.add(BasicBlock(deoptLabel, listOf(
                    Deoptimize(reason, action, speculation, frameState)
                )))

                currentLabel = continueLabel
                startIndex = guardIndex + 1
            }

            val remaining = block.instructions.subList(startIndex, block.instructions.size).toList()
            if (remaining.isNotEmpty()) {
                newBlocks.add(BasicBlock(currentLabel, remaining))
            } else {
                newBlocks.add(BasicBlock(currentLabel, listOf(Unreachable())))
            }
        }

        return fn.copy(blocks = newBlocks)
    }
}

package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Removes instructions whose results are never used.
 *
 * An instruction is dead if:
 * 1. It produces a result (non-void)
 * 2. No other instruction references that result
 * 3. It has no side effects
 *
 * Instructions with side effects (calls, stores, branches, returns) are never removed.
 */
class DeadCodeElimination : PipelineStage {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) fn else eliminateDeadCode(fn)
        })
    }

    private fun eliminateDeadCode(fn: IrFunction): IrFunction {
        var changed = true
        var blocks = fn.blocks

        while (changed) {
            changed = false
            val usedNames = collectUsedNames(blocks)
            val newBlocks = blocks.map { block ->
                val filtered = block.instructions.filter { inst ->
                    val result = inst.result
                    if (result == null || inst.effects.hasSideEffects()) {
                        true
                    } else if (result.name !in usedNames) {
                        changed = true
                        false
                    } else {
                        true
                    }
                }
                BasicBlock(block.label, filtered)
            }
            blocks = newBlocks
        }

        return fn.copy(blocks = blocks)
    }

    private fun collectUsedNames(blocks: List<BasicBlock>): Set<String> {
        val used = mutableSetOf<String>()
        for (block in blocks) {
            for (inst in block.instructions) {
                for (value in inst.operands) {
                    used.add(value.name)
                }
            }
        }
        return used
    }

}

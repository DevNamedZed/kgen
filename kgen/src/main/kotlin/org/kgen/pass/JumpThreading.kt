package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Jump threading — eliminates redundant conditional branches.
 *
 * When a block ends with a conditional branch whose condition is known
 * from the predecessor (e.g., a phi with constant incoming values, or
 * a condition that was already tested), the branch is replaced with
 * an unconditional jump to the correct target.
 *
 * Also simplifies:
 * - Blocks that unconditionally branch to a block with a single predecessor (merge)
 * - Conditional branches where both targets are the same
 * - Conditional branches with constant conditions
 */
class JumpThreading : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.isEmpty()) fn else threadFunction(fn)
        })
    }

    private fun threadFunction(fn: IrFunction): IrFunction {
        var blocks = fn.blocks.toMutableList()
        var changed = true

        while (changed) {
            changed = false

            // Pass 1: Fold constant conditional branches
            blocks = blocks.map { block ->
                val last = block.instructions.lastOrNull()
                if (last is CondBr) {
                    val cond = last.condition
                    if (cond is Constant.I1) {
                        val target = if (cond.value) last.trueTarget else last.falseTarget
                        val newInsts = block.instructions.dropLast(1) + Br(BlockRef(target.label))
                        changed = true
                        BasicBlock(block.label, newInsts)
                    } else if (last.trueTarget == last.falseTarget) {
                        val newInsts = block.instructions.dropLast(1) + Br(BlockRef(last.trueTarget.label))
                        changed = true
                        BasicBlock(block.label, newInsts)
                    } else {
                        block
                    }
                } else {
                    block
                }
            }.toMutableList()

            // Pass 2: Thread through phi nodes with constant condition
            blocks = blocks.map { block ->
                val last = block.instructions.lastOrNull()
                if (last is CondBr) {
                    val condRef = last.condition
                    if (condRef is InstructionRef) {
                        val condInst = block.instructions.find { it.result?.name == condRef.name }
                        if (condInst is Phi) {
                            val allConstant = condInst.incoming.all { it.first is Constant.I1 }
                            if (allConstant && condInst.incoming.isNotEmpty()) {
                                // All incoming values are constant — we can thread each predecessor
                                // This is handled by creating separate paths, but for simplicity
                                // we check if all values agree
                                val allTrue = condInst.incoming.all { (it.first as Constant.I1).value }
                                val allFalse = condInst.incoming.all { !(it.first as Constant.I1).value }
                                if (allTrue) {
                                    val newInsts = block.instructions.dropLast(1) + Br(BlockRef(last.trueTarget.label))
                                    changed = true
                                    BasicBlock(block.label, newInsts)
                                } else if (allFalse) {
                                    val newInsts = block.instructions.dropLast(1) + Br(BlockRef(last.falseTarget.label))
                                    changed = true
                                    BasicBlock(block.label, newInsts)
                                } else block
                            } else block
                        } else block
                    } else block
                } else block
            }.toMutableList()

            // Pass 3: Merge blocks — if block A has a single successor B,
            // and B has a single predecessor (A), merge B into A
            val predCounts = computePredCounts(blocks)
            val merged = mutableSetOf<String>()
            val blockMap = blocks.associateBy { it.label }
            val newBlocks = mutableListOf<BasicBlock>()

            for (block in blocks) {
                if (block.label in merged) continue
                var current = block
                while (true) {
                    val last = current.instructions.lastOrNull()
                    if (last !is Br) break
                    val target = last.target.label
                    if ((predCounts[target] ?: 0) != 1) break
                    val successor = blockMap[target] ?: break
                    if (successor.label in merged) break
                    // Merge: replace current's terminator with successor's instructions
                    val mergedInsts = current.instructions.dropLast(1) + successor.instructions
                    current = BasicBlock(current.label, mergedInsts)
                    merged.add(successor.label)
                    changed = true
                }
                newBlocks.add(current)
            }
            blocks = newBlocks
        }

        // Remove unreachable blocks
        val reachable = computeReachable(blocks)
        val finalBlocks = blocks.filter { it.label in reachable }

        return fn.copy(blocks = finalBlocks)
    }

    private fun computePredCounts(blocks: List<BasicBlock>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (block in blocks) {
            val last = block.instructions.lastOrNull() ?: continue
            for (target in terminatorTargets(last)) {
                counts[target] = (counts[target] ?: 0) + 1
            }
        }
        return counts
    }

    private fun computeReachable(blocks: List<BasicBlock>): Set<String> {
        if (blocks.isEmpty()) return emptySet()
        val reachable = mutableSetOf<String>()
        val worklist = ArrayDeque<String>()
        worklist.add(blocks[0].label)
        val blockMap = blocks.associateBy { it.label }
        while (worklist.isNotEmpty()) {
            val label = worklist.removeFirst()
            if (!reachable.add(label)) continue
            val block = blockMap[label] ?: continue
            val last = block.instructions.lastOrNull() ?: continue
            for (target in terminatorTargets(last)) {
                if (target !in reachable) worklist.add(target)
            }
        }
        return reachable
    }

    private fun terminatorTargets(inst: Instruction): List<String> = when (inst) {
        is Br -> listOf(inst.target.label)
        is CondBr -> listOf(inst.trueTarget.label, inst.falseTarget.label)
        is Switch -> listOf(inst.defaultTarget.label) + inst.cases.map { it.second.label }
        is IndirectBr -> inst.targets.map { it.label }
        else -> emptyList()
    }
}

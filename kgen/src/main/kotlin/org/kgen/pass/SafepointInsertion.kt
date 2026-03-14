package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Inserts GC safepoints at strategic locations in functions that use a GC strategy.
 *
 * Safepoints are inserted at:
 * - Loop back-edges (branches that jump to a block earlier in the function)
 * - Call sites (before each Call/Invoke/ManagedCall instruction)
 *
 * Functions without a GC strategy (`gc == null` or `gc == "none"`) are not modified.
 *
 * ```java
 * var pipeline = new PassPipeline();
 * pipeline.add(new SafepointInsertion());
 * var result = pipeline.execute(module);
 * ```
 */
class SafepointInsertion : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || !needsSafepoints(fn)) fn else insertSafepoints(fn)
        })
    }

    private fun needsSafepoints(fn: IrFunction): Boolean {
        val gc = fn.gc ?: return false
        return gc != "none"
    }

    private fun insertSafepoints(fn: IrFunction): IrFunction {
        val blockLabels = fn.blocks.mapIndexed { i, b -> b.label to i }.toMap()
        val newBlocks = fn.blocks.mapIndexed { blockIdx, block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                when (inst) {
                    is Call, is Invoke, is ManagedCall -> {
                        if (!isPrecedingGCSafepoint(newInstructions)) {
                            newInstructions.add(GCSafepoint())
                        }
                        newInstructions.add(inst)
                    }
                    is Br -> {
                        val targetIdx = blockLabels[inst.target.label]
                        if (targetIdx != null && targetIdx <= blockIdx) {
                            if (!isPrecedingGCSafepoint(newInstructions)) {
                                newInstructions.add(GCSafepoint())
                            }
                        }
                        newInstructions.add(inst)
                    }
                    is CondBr -> {
                        val trueIdx = blockLabels[inst.trueTarget.label]
                        val falseIdx = blockLabels[inst.falseTarget.label]
                        val isBackEdge = (trueIdx != null && trueIdx <= blockIdx) ||
                                (falseIdx != null && falseIdx <= blockIdx)
                        if (isBackEdge && !isPrecedingGCSafepoint(newInstructions)) {
                            newInstructions.add(GCSafepoint())
                        }
                        newInstructions.add(inst)
                    }
                    else -> newInstructions.add(inst)
                }
            }
            BasicBlock(block.label, newInstructions)
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun isPrecedingGCSafepoint(instructions: List<Instruction>): Boolean {
        return instructions.isNotEmpty() && instructions.last() is GCSafepoint
    }
}

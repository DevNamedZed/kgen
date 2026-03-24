package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Inserts [GCRelocate] instructions after safepoints for GC-managed references.
 *
 * When the garbage collector runs at a safepoint, it may relocate objects in
 * the managed heap. Any GC reference that is live across the safepoint must be
 * "relocated" — the pointer updated to reflect the new address. This pass
 * inserts explicit [GCRelocate] instructions that model this relocation in SSA.
 *
 * For each safepoint (instruction with `isSafepoint()` effect), the pass finds
 * all live GC references defined before the safepoint and used after it. Each
 * such reference gets a GCRelocate inserted after the safepoint, and all
 * subsequent uses are updated to reference the relocated value.
 *
 * This pass should run after safepoint insertion and before instruction selection.
 */
class GCRelocateInsertion : PipelineStage {

    private var refCounter = 0

    override fun run(module: Module): Module {
        refCounter = 0
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) {
                fn
            } else {
                insertGCRelocates(fn)
            }
        })
    }

    private fun insertGCRelocates(fn: IrFunction): IrFunction {
        var changed = false
        val newBlocks = fn.blocks.map { block ->
            val result = processBlock(block, fn)
            if (result !== block) {
                changed = true
            }
            result
        }

        if (!changed) {
            return fn
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun processBlock(block: BasicBlock, fn: IrFunction): BasicBlock {
        val newInstructions = mutableListOf<Instruction>()
        val replacements = mutableMapOf<String, Value>()
        var modified = false

        for (inst in block.instructions) {
            val resolved = if (replacements.isNotEmpty()) {
                resolveInstruction(inst, replacements)
            } else {
                inst
            }
            newInstructions.add(resolved)

            if (resolved.effects.isSafepoint()) {
                val definedBefore = collectDefinedBefore(resolved, newInstructions, fn)
                val usedAfter = collectUsedAfter(resolved, block.instructions, fn)

                val liveReferences = definedBefore.filter { value ->
                    isGCReference(value.type) && value.name in usedAfter
                }

                for (ref in liveReferences) {
                    val relocatedName = "gc_reloc_${refCounter++}"
                    val resolvedRef = resolve(ref, replacements)
                    val relocatedDest = InstructionRef(relocatedName, resolvedRef.type)
                    val safepointValue = resolved.result ?: InstructionRef("_safepoint", Type.Void)

                    newInstructions.add(GCRelocate(
                        dest = relocatedDest,
                        safepoint = safepointValue,
                        base = resolvedRef,
                        derived = resolvedRef,
                    ))

                    replacements[ref.name] = relocatedDest
                    modified = true
                }
            }
        }

        if (!modified) {
            return block
        }
        return BasicBlock(block.label, newInstructions)
    }

    private fun collectDefinedBefore(safepoint: Instruction, processedInstructions: List<Instruction>, fn: IrFunction): List<Value> {
        val values = mutableListOf<Value>()
        for (param in fn.params) {
            values.add(param)
        }
        for (inst in processedInstructions) {
            if (inst === safepoint) {
                break
            }
            val result = inst.result
            if (result != null) {
                values.add(result)
            }
        }
        return values
    }

    private fun collectUsedAfter(safepoint: Instruction, allInstructions: List<Instruction>, fn: IrFunction): Set<String> {
        val used = mutableSetOf<String>()
        var afterSafepoint = false
        for (inst in allInstructions) {
            if (inst === safepoint) {
                afterSafepoint = true
                continue
            }
            if (afterSafepoint) {
                for (operand in inst.operands) {
                    used.add(operand.name)
                }
            }
        }
        return used
    }

    private fun isGCReference(type: Type): Boolean {
        return type is Type.Reference || type is Type.WeakReference
    }

    private fun resolve(value: Value, replacements: Map<String, Value>): Value {
        var current = value
        while (current.name in replacements) {
            current = replacements[current.name]!!
        }
        return current
    }

    private fun resolveInstruction(inst: Instruction, replacements: Map<String, Value>): Instruction {
        val hasReplacements = inst.operands.any { it.name in replacements }
        if (!hasReplacements) {
            return inst
        }
        return when (inst) {
            is Call -> inst.copy(
                function = resolve(inst.function, replacements),
                args = inst.args.map { resolve(it, replacements) },
            )
            is Store -> inst.copy(
                value = resolve(inst.value, replacements),
                ptr = resolve(inst.ptr, replacements),
            )
            is Load -> inst.copy(ptr = resolve(inst.ptr, replacements))
            is Ret -> inst.copy(value = inst.value?.let { resolve(it, replacements) })
            is GetField -> inst.copy(obj = resolve(inst.obj, replacements))
            is PutField -> inst.copy(
                obj = resolve(inst.obj, replacements),
                value = resolve(inst.value, replacements),
            )
            is VirtualCall -> inst.copy(
                obj = resolve(inst.obj, replacements),
                args = inst.args.map { resolve(it, replacements) },
            )
            is WriteBarrier -> inst.copy(
                obj = resolve(inst.obj, replacements),
                value = resolve(inst.value, replacements),
            )
            else -> inst
        }
    }
}

package org.kgen.pass

import org.kgen.ir.*

/**
 * Inlines small function calls.
 *
 * A function is eligible for inlining if:
 * - It is defined (not external)
 * - It has a single basic block
 * - Its body is small (instruction count <= [maxInstructionCount])
 * - It is not recursive (doesn't call itself)
 * - It has the [FnAttribute.ALWAYS_INLINE] attribute, or meets size threshold
 *
 * Inlining replaces a call instruction with the body of the callee,
 * substituting parameters with call arguments and replacing the
 * return value with the callee's returned expression.
 */
class Inlining(private val maxInstructionCount: Int = 20) : ModulePass {

    override fun run(module: Module): Module {
        val funcMap = module.functions.filter { !it.isExternal }.associateBy { it.name }
        var changed = true
        var current = module

        while (changed) {
            changed = false
            val result = inlinePass(current, funcMap)
            if (result !== current) {
                current = result
                changed = true
            }
        }

        return current
    }

    private fun inlinePass(module: Module, funcMap: Map<String, IrFunction>): Module {
        var any = false
        val newFunctions = module.functions.map { fn ->
            if (fn.isExternal) fn
            else {
                val result = inlineInFunction(fn, funcMap)
                if (result !== fn) any = true
                result
            }
        }
        return if (any) module.copy(functions = newFunctions) else module
    }

    private fun inlineInFunction(fn: IrFunction, funcMap: Map<String, IrFunction>): IrFunction {
        var nextId = findMaxInstructionId(fn) + 1
        var anyInlined = false
        var inlineSiteId = 0

        // Tracks call result -> inlined return value mappings across the entire function.
        // When a call is inlined, its result name maps to the inlined return value.
        // Subsequent instructions must be remapped through this.
        val resultMap = mutableMapOf<String, Value>()

        fun remapThroughResults(inst: Instruction): Instruction {
            if (resultMap.isEmpty()) return inst
            return remapOperands(inst, resultMap)
        }

        val newBlocks = mutableListOf<BasicBlock>()
        for (block in fn.blocks) {
            val newInstructions = mutableListOf<Instruction>()
            var splitNeeded = false

            for ((instIdx, inst) in block.instructions.withIndex()) {
                val remapped = remapThroughResults(inst)
                if (remapped is Instruction.Call && shouldInline(remapped, funcMap)) {
                    val callee = funcMap[calleeName(remapped)]!!
                    val inlined = inlineCall(remapped, callee, nextId, inlineSiteId)
                    if (inlined != null) {
                        // Record the call result -> inlined return value mapping
                        if (remapped.dest != null && inlined.returnValue != null) {
                            resultMap[remapped.dest.name] = inlined.returnValue
                        }

                        if (callee.blocks.size == 1) {
                            // Single-block: splice instructions directly
                            newInstructions.addAll(inlined.blocks[0].instructions)
                        } else {
                            // Multi-block: splice first block's instructions, add remaining blocks,
                            // then continue remaining instructions in a new continuation block
                            newInstructions.addAll(inlined.blocks[0].instructions)

                            // Close the current block with a branch to the second inlined block
                            // (the first block's last instruction should be a Br to the next)
                            newBlocks.add(BasicBlock(block.label + if (splitNeeded) "_cont$inlineSiteId" else "", newInstructions.toList()))

                            // Add intermediate inlined blocks (all except first and last)
                            for (k in 1 until inlined.blocks.size - 1) {
                                newBlocks.add(inlined.blocks[k])
                            }

                            // Start continuation block with remaining inlined block (exit) instructions
                            val lastInlined = inlined.blocks.last()
                            val contLabel = "${block.label}_cont${inlineSiteId + 1}"
                            val remainingInsts = mutableListOf<Instruction>()
                            remainingInsts.addAll(lastInlined.instructions)

                            // Add remaining instructions from the original block after the call
                            val remaining = block.instructions.subList(instIdx + 1, block.instructions.size)
                            for (rem in remaining) {
                                remainingInsts.add(remapThroughResults(rem))
                            }
                            newBlocks.add(BasicBlock(contLabel, remainingInsts))

                            nextId = inlined.nextId
                            inlineSiteId++
                            anyInlined = true
                            splitNeeded = true
                            // We've consumed all remaining instructions via the continuation block
                            break
                        }
                        nextId = inlined.nextId
                        inlineSiteId++
                        anyInlined = true
                        continue
                    }
                }
                newInstructions.add(remapped)
            }

            if (!splitNeeded) {
                newBlocks.add(BasicBlock(block.label, newInstructions))
            }
        }

        return if (anyInlined) fn.copy(blocks = newBlocks) else fn
    }


    private fun calleeName(call: Instruction.Call): String {
        return when (val f = call.function) {
            is FunctionRef -> f.name
            is InstructionRef -> f.name
            else -> f.name
        }
    }

    private fun shouldInline(call: Instruction.Call, funcMap: Map<String, IrFunction>): Boolean {
        val name = calleeName(call)
        val callee = funcMap[name] ?: return false

        if (callee.isExternal || callee.blocks.isEmpty()) return false

        val isRecursive = callee.blocks.any { block ->
            block.instructions.any { inst ->
                inst is Instruction.Call && calleeName(inst) == name
            }
        }
        if (isRecursive) return false

        if (callee.attributes.contains(FnAttribute.ALWAYSINLINE)) return true

        val instCount = callee.blocks.sumOf { it.instructions.size }
        return instCount <= maxInstructionCount
    }

    private data class InlineResult(val blocks: List<BasicBlock>, val nextId: Int, val returnValue: Value? = null)

    private fun inlineCall(call: Instruction.Call, callee: IrFunction, startId: Int, siteId: Int): InlineResult? {
        val paramMap = mutableMapOf<String, Value>()
        for (i in callee.params.indices) {
            if (i < call.args.size) {
                paramMap[callee.params[i].name] = call.args[i]
            }
        }

        var nextId = startId
        val nameMap = mutableMapOf<String, String>()
        val labelMap = mutableMapOf<String, String>()

        fun newName(): String {
            val name = "%$nextId"
            nextId++
            return name
        }

        // Create label mapping for multi-block callees
        for (block in callee.blocks) {
            labelMap[block.label] = "inline_${siteId}_${block.label}"
        }

        val callDest = call.dest
        val inlinedBlocks = mutableListOf<BasicBlock>()
        var returnValue: Value? = null

        for (block in callee.blocks) {
            val inlinedInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                if (inst is Instruction.Ret) {
                    val retValue = inst.value
                    if (callDest != null && retValue != null) {
                        val retVal = remapValue(retValue, paramMap, nameMap)
                        nameMap[callDest.name] = retVal.name
                        paramMap[callDest.name] = retVal
                        returnValue = retVal
                    }
                    continue
                }

                val remapped = remapInstruction(inst, paramMap, nameMap, ::newName, labelMap)
                if (remapped != null) {
                    inlinedInstructions.add(remapped)
                }
            }

            val blockLabel = if (inlinedBlocks.isEmpty()) {
                // First block's instructions will be spliced into the caller block
                labelMap[block.label] ?: block.label
            } else {
                labelMap[block.label] ?: block.label
            }
            inlinedBlocks.add(BasicBlock(blockLabel, inlinedInstructions))
        }

        return InlineResult(inlinedBlocks, nextId, returnValue)
    }

    private fun remapValue(v: Value, paramMap: Map<String, Value>, nameMap: Map<String, String>): Value {
        if (v is Parameter) {
            return paramMap[v.name] ?: v
        }
        if (v is InstructionRef) {
            val mapped = paramMap[v.name]
            if (mapped != null) return mapped
            val newName = nameMap[v.name]
            if (newName != null) return InstructionRef(newName, v.type)
        }
        return v
    }

    /** Remap only operand values (not destinations) through a value substitution map. */
    private fun remapOperands(inst: Instruction, valueMap: Map<String, Value>): Instruction {
        fun rv(v: Value): Value {
            if (v is InstructionRef) {
                val mapped = valueMap[v.name]
                if (mapped != null) return mapped
            }
            if (v is Parameter) {
                val mapped = valueMap[v.name]
                if (mapped != null) return mapped
            }
            return v
        }
        return when (inst) {
            is Instruction.Add -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Sub -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Mul -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.SDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.UDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.SRem -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.URem -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.And -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Or -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Xor -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Shl -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.LShr -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.AShr -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.ICmp -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FAdd -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FSub -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FMul -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FCmp -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Neg -> inst.copy(operand = rv(inst.operand))
            is Instruction.Not -> inst.copy(operand = rv(inst.operand))
            is Instruction.FNeg -> inst.copy(operand = rv(inst.operand))
            is Instruction.Ret -> inst.copy(value = inst.value?.let { rv(it) })
            is Instruction.Call -> inst.copy(args = inst.args.map { rv(it) })
            is Instruction.CondBr -> inst.copy(condition = rv(inst.condition))
            is Instruction.Switch -> inst.copy(value = rv(inst.value))
            is Instruction.Select -> inst.copy(condition = rv(inst.condition), trueValue = rv(inst.trueValue), falseValue = rv(inst.falseValue))
            is Instruction.Load -> inst.copy(ptr = rv(inst.ptr))
            is Instruction.Store -> inst.copy(value = rv(inst.value), ptr = rv(inst.ptr))
            is Instruction.ZExt -> inst.copy(value = rv(inst.value))
            is Instruction.SExt -> inst.copy(value = rv(inst.value))
            is Instruction.Trunc -> inst.copy(operand = rv(inst.operand))
            is Instruction.IntTrunc -> inst.copy(value = rv(inst.value))
            is Instruction.GetElementPtr -> inst.copy(ptr = rv(inst.ptr), indices = inst.indices.map { rv(it) })
            is Instruction.Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rv(v) to l })
            else -> inst
        }
    }

    private fun remapInstruction(
        inst: Instruction,
        paramMap: MutableMap<String, Value>,
        nameMap: MutableMap<String, String>,
        newName: () -> String,
        labelMap: Map<String, String> = emptyMap(),
    ): Instruction? {
        fun rv(v: Value): Value = remapValue(v, paramMap, nameMap)
        fun rl(label: String): String = labelMap[label] ?: label
        fun remapDest(dest: InstructionRef): InstructionRef {
            val nn = newName()
            nameMap[dest.name] = nn
            val ref = InstructionRef(nn, dest.type)
            paramMap[dest.name] = ref
            return ref
        }

        return when (inst) {
            is Instruction.Add -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Sub -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Mul -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.SDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.UDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.SRem -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.URem -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.And -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Or -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Xor -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Shl -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.LShr -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.AShr -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.Neg -> inst.copy(dest = remapDest(inst.dest), operand = rv(inst.operand))
            is Instruction.ICmp -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FCmp -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FAdd -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FSub -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FMul -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Instruction.FNeg -> inst.copy(dest = remapDest(inst.dest), operand = rv(inst.operand))
            is Instruction.ZExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.SExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.IntTrunc -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.Select -> inst.copy(dest = remapDest(inst.dest), condition = rv(inst.condition), trueValue = rv(inst.trueValue), falseValue = rv(inst.falseValue))
            is Instruction.Call -> inst.copy(dest = inst.dest?.let { remapDest(it) }, args = inst.args.map { rv(it) })
            is Instruction.Load -> inst.copy(dest = remapDest(inst.dest), ptr = rv(inst.ptr))
            is Instruction.Store -> inst.copy(value = rv(inst.value), ptr = rv(inst.ptr))
            is Instruction.Alloca -> inst.copy(dest = remapDest(inst.dest))
            is Instruction.SIToFP -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.UIToFP -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.FPToSI -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.FPToUI -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.FPExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.FPTrunc -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.GetElementPtr -> inst.copy(dest = remapDest(inst.dest), ptr = rv(inst.ptr), indices = inst.indices.map { rv(it) })
            is Instruction.BitCast -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.PtrToInt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.IntToPtr -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Instruction.Br -> inst.copy(target = rl(inst.target))
            is Instruction.CondBr -> inst.copy(condition = rv(inst.condition), trueTarget = rl(inst.trueTarget), falseTarget = rl(inst.falseTarget))
            is Instruction.Switch -> inst.copy(
                value = rv(inst.value),
                defaultTarget = rl(inst.defaultTarget),
                cases = inst.cases.map { (v, label) -> v to rl(label) },
            )
            is Instruction.Phi -> inst.copy(
                dest = remapDest(inst.dest),
                incoming = inst.incoming.map { (v, label) -> rv(v) to rl(label) },
            )
            else -> null
        }
    }

    private fun findMaxInstructionId(fn: IrFunction): Int {
        var max = 0
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val ref = inst.result as? InstructionRef ?: continue
                val id = ref.name.removePrefix("%").toIntOrNull() ?: continue
                if (id > max) max = id
            }
        }
        for (p in fn.params) {
            val id = p.name.removePrefix("%").toIntOrNull()
            if (id != null && id > max) max = id
        }
        return max
    }
}

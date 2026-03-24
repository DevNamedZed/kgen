package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*

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
class Inlining(private val maxInstructionCount: Int = 20) : PipelineStage {

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
                if (remapped is Call && shouldInline(remapped, funcMap)) {
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
                            // Multi-block: splice first block into current block, add all
                            // remaining blocks with their labels preserved, then create a
                            // continuation block for the rest of the caller.
                            val contLabel = "${block.label}_cont${inlineSiteId + 1}"

                            // First inlined block: splice into current caller block
                            newInstructions.addAll(inlined.blocks[0].instructions)
                            newBlocks.add(BasicBlock(block.label + if (splitNeeded) { "_cont$inlineSiteId" } else { "" }, newInstructions.toList()))

                            // All remaining inlined blocks: keep labels intact. Replace
                            // blocks that end without a terminator (Ret was stripped) with
                            // a branch to the continuation.
                            for (k in 1 until inlined.blocks.size) {
                                val inlinedBlock = inlined.blocks[k]
                                val lastInst = inlinedBlock.instructions.lastOrNull()
                                val hasTerminator = lastInst is Br || lastInst is CondBr || lastInst is Switch || lastInst is Ret
                                if (!hasTerminator) {
                                    val patchedInstructions = inlinedBlock.instructions.toMutableList()
                                    patchedInstructions.add(Br(BlockRef(contLabel)))
                                    newBlocks.add(BasicBlock(inlinedBlock.label, patchedInstructions))
                                } else {
                                    newBlocks.add(inlinedBlock)
                                }
                            }

                            // Continuation block: remaining caller instructions after the call
                            val remaining = block.instructions.subList(instIdx + 1, block.instructions.size)
                            val contInstructions = remaining.map { remapThroughResults(it) }
                            newBlocks.add(BasicBlock(contLabel, contInstructions))

                            nextId = inlined.nextId
                            inlineSiteId++
                            anyInlined = true
                            splitNeeded = true
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


    private fun calleeName(call: Call): String {
        return when (val f = call.function) {
            is FunctionRef -> f.name
            is InstructionRef -> f.name
            else -> f.name
        }
    }

    private fun shouldInline(call: Call, funcMap: Map<String, IrFunction>): Boolean {
        val name = calleeName(call)
        val callee = funcMap[name] ?: return false

        if (callee.isExternal || callee.blocks.isEmpty()) return false

        val isRecursive = callee.blocks.any { block ->
            block.instructions.any { inst ->
                inst is Call && calleeName(inst) == name
            }
        }
        if (isRecursive) return false

        if (callee.attributes.contains(FnAttribute.ALWAYSINLINE)) return true

        val instCount = callee.blocks.sumOf { it.instructions.size }
        return instCount <= maxInstructionCount
    }

    private data class InlineResult(val blocks: List<BasicBlock>, val nextId: Int, val returnValue: Value? = null)

    private fun inlineCall(call: Call, callee: IrFunction, startId: Int, siteId: Int): InlineResult? {
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
                if (inst is Ret) {
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
            is Add -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Sub -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Mul -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is SDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is UDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is SRem -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is URem -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is And -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Or -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Xor -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Shl -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is LShr -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is AShr -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is ICmp -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FAdd -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FSub -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FMul -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FDiv -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FCmp -> inst.copy(lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Neg -> inst.copy(operand = rv(inst.operand))
            is Not -> inst.copy(operand = rv(inst.operand))
            is FNeg -> inst.copy(operand = rv(inst.operand))
            is Ret -> inst.copy(value = inst.value?.let { rv(it) })
            is Call -> inst.copy(args = inst.args.map { rv(it) })
            is CondBr -> inst.copy(condition = rv(inst.condition))
            is Switch -> inst.copy(value = rv(inst.value))
            is Select -> inst.copy(condition = rv(inst.condition), trueValue = rv(inst.trueValue), falseValue = rv(inst.falseValue))
            is Load -> inst.copy(ptr = rv(inst.ptr))
            is Store -> inst.copy(value = rv(inst.value), ptr = rv(inst.ptr))
            is ZExt -> inst.copy(value = rv(inst.value))
            is SExt -> inst.copy(value = rv(inst.value))
            is FTrunc -> inst.copy(operand = rv(inst.operand))
            is IntTrunc -> inst.copy(value = rv(inst.value))
            is GetElementPtr -> inst.copy(ptr = rv(inst.ptr), indices = inst.indices.map { rv(it) })
            is Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rv(v) to l })
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
            is Add -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Sub -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Mul -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is SDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is UDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is SRem -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is URem -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is And -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Or -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Xor -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Shl -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is LShr -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is AShr -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is Neg -> inst.copy(dest = remapDest(inst.dest), operand = rv(inst.operand))
            is ICmp -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FCmp -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FAdd -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FSub -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FMul -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FDiv -> inst.copy(dest = remapDest(inst.dest), lhs = rv(inst.lhs), rhs = rv(inst.rhs))
            is FNeg -> inst.copy(dest = remapDest(inst.dest), operand = rv(inst.operand))
            is ZExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is SExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is IntTrunc -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Select -> inst.copy(dest = remapDest(inst.dest), condition = rv(inst.condition), trueValue = rv(inst.trueValue), falseValue = rv(inst.falseValue))
            is Call -> inst.copy(dest = inst.dest?.let { remapDest(it) }, args = inst.args.map { rv(it) })
            is Load -> inst.copy(dest = remapDest(inst.dest), ptr = rv(inst.ptr))
            is Store -> inst.copy(value = rv(inst.value), ptr = rv(inst.ptr))
            is Alloca -> inst.copy(dest = remapDest(inst.dest))
            is SIToFP -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is UIToFP -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is FPToSI -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is FPToUI -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is FPExt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is FPTrunc -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is GetElementPtr -> inst.copy(dest = remapDest(inst.dest), ptr = rv(inst.ptr), indices = inst.indices.map { rv(it) })
            is BitCast -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is PtrToInt -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is IntToPtr -> inst.copy(dest = remapDest(inst.dest), value = rv(inst.value))
            is Br -> inst.copy(target = BlockRef(rl(inst.target.label)))
            is CondBr -> inst.copy(condition = rv(inst.condition), trueTarget = BlockRef(rl(inst.trueTarget.label)), falseTarget = BlockRef(rl(inst.falseTarget.label)))
            is Switch -> inst.copy(
                value = rv(inst.value),
                defaultTarget = BlockRef(rl(inst.defaultTarget.label)),
                cases = inst.cases.map { (v, label) -> v to BlockRef(rl(label.label)) },
            )
            is Phi -> inst.copy(
                dest = remapDest(inst.dest),
                incoming = inst.incoming.map { (v, label) -> rv(v) to BlockRef(rl(label.label)) },
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

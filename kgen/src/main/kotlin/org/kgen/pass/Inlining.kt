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

        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                if (inst is Instruction.Call && shouldInline(inst, funcMap)) {
                    val callee = funcMap[calleeName(inst)]!!
                    val inlined = inlineCall(inst, callee, nextId)
                    if (inlined != null) {
                        newInstructions.addAll(inlined.instructions)
                        nextId = inlined.nextId
                        anyInlined = true
                        continue
                    }
                }
                newInstructions.add(inst)
            }
            BasicBlock(block.label, newInstructions)
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

        if (callee.isExternal) return false
        if (callee.blocks.size != 1) return false

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

    private data class InlineResult(val instructions: List<Instruction>, val nextId: Int)

    private fun inlineCall(call: Instruction.Call, callee: IrFunction, startId: Int): InlineResult? {
        if (callee.blocks.size != 1) return null
        val block = callee.blocks[0]

        val paramMap = mutableMapOf<String, Value>()
        for (i in callee.params.indices) {
            if (i < call.args.size) {
                paramMap[callee.params[i].name] = call.args[i]
            }
        }

        var nextId = startId
        val nameMap = mutableMapOf<String, String>()

        fun newName(): String {
            val name = "%$nextId"
            nextId++
            return name
        }

        val inlinedInstructions = mutableListOf<Instruction>()
        val callDest = call.dest

        for (inst in block.instructions) {
            if (inst is Instruction.Ret) {
                val retValue = inst.value
                if (callDest != null && retValue != null) {
                    val retVal = remapValue(retValue, paramMap, nameMap)
                    nameMap[callDest.name] = retVal.name
                    paramMap[callDest.name] = retVal
                }
                continue
            }

            val remapped = remapInstruction(inst, paramMap, nameMap, ::newName)
            if (remapped != null) {
                inlinedInstructions.add(remapped)
            }
        }

        return InlineResult(inlinedInstructions, nextId)
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

    private fun remapInstruction(inst: Instruction, paramMap: MutableMap<String, Value>, nameMap: MutableMap<String, String>, newName: () -> String): Instruction? {
        fun rv(v: Value): Value = remapValue(v, paramMap, nameMap)
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

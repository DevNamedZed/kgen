package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Scalar Replacement of Aggregates (SROA).
 *
 * Breaks aggregate allocas (structs, small fixed-size arrays) into individual
 * scalar allocas per field. These can then be promoted by Mem2Reg.
 *
 * An aggregate alloca is replaceable if:
 * - It allocates a struct or small fixed-size array (no dynamic numElements)
 * - Every use of the alloca is a GEP with constant indices that drills down to a scalar
 * - Every use of each GEP result is a Load or Store (no address escaping)
 */
class ScalarReplacementOfAggregates : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.isEmpty()) fn else transformFunction(fn)
        })
    }

    private fun transformFunction(fn: IrFunction): IrFunction {
        val allocas = findReplaceableAllocas(fn)
        if (allocas.isEmpty()) return fn

        var nextId = findMaxInstructionId(fn) + 1
        val replacements = mutableMapOf<String, Value>()
        val gepToFieldAlloca = mutableMapOf<String, String>()
        val newAllocas = mutableListOf<Instruction>()
        val deadInsts = mutableSetOf<String>()

        for ((allocaName, alloca) in allocas) {
            val fieldTypes = flattenFields(alloca.allocType)
            val fieldAllocaNames = mutableMapOf<List<Int>, String>()

            for ((indices, fieldType) in fieldTypes) {
                val fieldName = "%$nextId"
                nextId++
                val fieldAlloca = Alloca(
                    dest = InstructionRef(fieldName, Type.OpaquePointer),
                    allocType = fieldType,
                )
                newAllocas.add(fieldAlloca)
                fieldAllocaNames[indices] = fieldName
            }

            deadInsts.add(allocaName)

            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst is GetElementPtr && inst.ptr.name == allocaName) {
                        val indices = extractConstantIndices(inst) ?: continue
                        val fieldKey = normalizeIndices(indices)
                        val fieldAllocaName = fieldAllocaNames[fieldKey] ?: continue
                        gepToFieldAlloca[inst.dest.name] = fieldAllocaName
                        deadInsts.add(inst.dest.name)
                    }
                }
            }
        }

        val resultBlocks = fn.blocks.map { block ->
            val newInsts = mutableListOf<Instruction>()

            if (block == fn.blocks[0]) {
                newInsts.addAll(newAllocas)
            }

            for (inst in block.instructions) {
                val destName = inst.result?.name
                if (destName != null && destName in deadInsts) continue

                when (inst) {
                    is Load -> {
                        val fieldAlloca = gepToFieldAlloca[inst.ptr.name]
                        if (fieldAlloca != null) {
                            newInsts.add(inst.copy(ptr = InstructionRef(fieldAlloca, Type.OpaquePointer)))
                        } else {
                            newInsts.add(rewriteOperands(inst, replacements))
                        }
                    }
                    is Store -> {
                        val fieldAlloca = gepToFieldAlloca[inst.ptr.name]
                        if (fieldAlloca != null) {
                            newInsts.add(inst.copy(
                                value = rewriteValue(inst.value, replacements),
                                ptr = InstructionRef(fieldAlloca, Type.OpaquePointer),
                            ))
                        } else {
                            newInsts.add(rewriteOperands(inst, replacements))
                        }
                    }
                    else -> newInsts.add(rewriteOperands(inst, replacements))
                }
            }
            BasicBlock(block.label, newInsts)
        }

        return fn.copy(blocks = resultBlocks)
    }

    private fun findReplaceableAllocas(fn: IrFunction): Map<String, Alloca> {
        val allocas = mutableMapOf<String, Alloca>()

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is Alloca && inst.numElements == null && isAggregate(inst.allocType)) {
                    allocas[inst.dest.name] = inst
                }
            }
        }

        if (allocas.isEmpty()) return emptyMap()

        val gepResults = mutableMapOf<String, String>()
        val nonReplaceable = mutableSetOf<String>()

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is GetElementPtr && inst.ptr.name in allocas) {
                    val indices = extractConstantIndices(inst)
                    if (indices == null) {
                        nonReplaceable.add(inst.ptr.name)
                        continue
                    }
                    val allocType = allocas[inst.ptr.name]!!.allocType
                    val fieldType = resolveFieldType(allocType, normalizeIndices(indices))
                    if (fieldType == null || isAggregate(fieldType)) {
                        nonReplaceable.add(inst.ptr.name)
                        continue
                    }
                    gepResults[inst.dest.name] = inst.ptr.name
                    continue
                }

                for (op in allOperands(inst)) {
                    val opName = op.name
                    if (opName in allocas) {
                        if (inst !is GetElementPtr) {
                            nonReplaceable.add(opName)
                        }
                    }
                    if (opName in gepResults) {
                        if (inst !is Load && inst !is Store) {
                            nonReplaceable.add(gepResults[opName]!!)
                        }
                    }
                }
            }
        }

        return allocas.filterKeys { it !in nonReplaceable }
    }

    private fun isAggregate(type: Type): Boolean = when (type) {
        is Type.Struct -> true
        is Type.Array -> type.size <= MAX_ARRAY_SIZE
        else -> false
    }

    private fun flattenFields(type: Type, prefix: List<Int> = emptyList()): List<Pair<List<Int>, Type>> {
        return when (type) {
            is Type.Struct -> type.fields.flatMapIndexed { i, fieldType ->
                val path = prefix + i
                if (isAggregate(fieldType)) flattenFields(fieldType, path)
                else listOf(path to fieldType)
            }
            is Type.Array -> (0 until type.size.toInt()).flatMap { i ->
                val path = prefix + i
                if (isAggregate(type.element)) flattenFields(type.element, path)
                else listOf(path to type.element)
            }
            else -> listOf(prefix to type)
        }
    }

    private fun resolveFieldType(type: Type, indices: List<Int>): Type? {
        var current = type
        for (idx in indices) {
            current = when (current) {
                is Type.Struct -> current.fields.getOrNull(idx) ?: return null
                is Type.Array -> if (idx < current.size) current.element else return null
                else -> return null
            }
        }
        return current
    }

    private fun extractConstantIndices(gep: GetElementPtr): List<Int>? {
        return gep.indices.map { idx ->
            when (idx) {
                is Constant.I32 -> idx.value
                is Constant.I64 -> idx.value.toInt()
                else -> return null
            }
        }
    }

    private fun normalizeIndices(indices: List<Int>): List<Int> {
        return if (indices.isNotEmpty() && indices[0] == 0) indices.drop(1) else indices
    }

    private fun allOperands(inst: Instruction): List<Value> = when (inst) {
        is Load -> listOf(inst.ptr)
        is Store -> listOf(inst.value, inst.ptr)
        is GetElementPtr -> listOf(inst.ptr) + inst.indices
        is Call -> inst.args
        is Ret -> listOfNotNull(inst.value)
        is Add -> listOf(inst.lhs, inst.rhs)
        is Sub -> listOf(inst.lhs, inst.rhs)
        is Mul -> listOf(inst.lhs, inst.rhs)
        is ICmp -> listOf(inst.lhs, inst.rhs)
        is Select -> listOf(inst.condition, inst.trueValue, inst.falseValue)
        is Phi -> inst.incoming.map { it.first }
        is PtrToInt -> listOf(inst.value)
        is IntToPtr -> listOf(inst.value)
        is BitCast -> listOf(inst.value)
        is CondBr -> listOf(inst.condition)
        is ExtractValue -> listOf(inst.aggregate)
        is InsertValue -> listOf(inst.aggregate, inst.element)
        else -> emptyList()
    }

    private fun rewriteValue(v: Value, replacements: Map<String, Value>): Value =
        if (v is InstructionRef || v is Parameter) replacements[v.name] ?: v else v

    private fun rewriteOperands(inst: Instruction, replacements: Map<String, Value>): Instruction {
        if (replacements.isEmpty()) return inst
        fun rw(v: Value): Value = rewriteValue(v, replacements)
        return when (inst) {
            is Add -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Sub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Mul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is SDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is UDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is SRem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is URem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is And -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Or -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Xor -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Shl -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is LShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is AShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Neg -> inst.copy(operand = rw(inst.operand))
            is ICmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FAdd -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FSub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FMul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FNeg -> inst.copy(operand = rw(inst.operand))
            is FCmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is ZExt -> inst.copy(value = rw(inst.value))
            is SExt -> inst.copy(value = rw(inst.value))
            is IntTrunc -> inst.copy(value = rw(inst.value))
            is FTrunc -> inst.copy(operand = rw(inst.operand))
            is Ret -> inst.copy(value = inst.value?.let { rw(it) })
            is Call -> inst.copy(args = inst.args.map { rw(it) })
            is Select -> inst.copy(condition = rw(inst.condition), trueValue = rw(inst.trueValue), falseValue = rw(inst.falseValue))
            is Store -> inst.copy(value = rw(inst.value), ptr = rw(inst.ptr))
            is Load -> inst.copy(ptr = rw(inst.ptr))
            is CondBr -> inst.copy(condition = rw(inst.condition))
            is SIToFP -> inst.copy(value = rw(inst.value))
            is UIToFP -> inst.copy(value = rw(inst.value))
            is FPToSI -> inst.copy(value = rw(inst.value))
            is FPToUI -> inst.copy(value = rw(inst.value))
            is FPExt -> inst.copy(value = rw(inst.value))
            is FPTrunc -> inst.copy(value = rw(inst.value))
            is GetElementPtr -> inst.copy(ptr = rw(inst.ptr), indices = inst.indices.map { rw(it) })
            is Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rw(v) to l })
            is ExtractValue -> inst.copy(aggregate = rw(inst.aggregate))
            is InsertValue -> inst.copy(aggregate = rw(inst.aggregate), element = rw(inst.element))
            else -> inst
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

    companion object {
        private const val MAX_ARRAY_SIZE = 16L
    }
}

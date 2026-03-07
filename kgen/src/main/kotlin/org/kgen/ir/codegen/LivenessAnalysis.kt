package org.kgen.ir.codegen

import org.kgen.ir.*

/**
 * A live interval for an SSA value.
 *
 * Tracks where a value is defined, where it's last used, and whether it lives
 * across a function call (which affects register assignment on register machines).
 */
data class LiveInterval(
    val name: String,
    val start: Int,
    val end: Int,
    val type: Type,
    val useCount: Int = 1,
    val acrossCall: Boolean = false,
)

/**
 * Computes live intervals for all SSA values in an [IrFunction].
 *
 * This analysis is target-independent — it works purely on IR values and
 * basic block structure. Used by both register allocators (x86, ARM64, RISC-V)
 * and local slot allocators (WASM, JVM, CLR).
 *
 * ```java
 * var analysis = new LivenessAnalysis(irFunction);
 * List<LiveInterval> intervals = analysis.intervals();
 * ```
 */
class LivenessAnalysis(private val fn: IrFunction) {

    /**
     * Compute live intervals for all values in the function, sorted by start position.
     */
    fun intervals(): List<LiveInterval> {
        val defs = mutableMapOf<String, Int>()
        val lastUses = mutableMapOf<String, Int>()
        val types = mutableMapOf<String, Type>()
        val useCounts = mutableMapOf<String, Int>()
        val callPositions = mutableListOf<Int>()

        var instIdx = 0

        for (param in fn.params) {
            defs[param.name] = 0
            types[param.name] = param.type
        }

        val blockLabelToIndex = mutableMapOf<String, Int>()
        val blockStartPos = mutableMapOf<Int, Int>()
        val blockEndPos = mutableMapOf<Int, Int>()

        for ((blockIdx, block) in fn.blocks.withIndex()) {
            blockLabelToIndex[block.label] = blockIdx
            val blockFirst = instIdx + 1
            for (inst in block.instructions) {
                instIdx++
                if (inst is Instruction.Call) {
                    callPositions.add(instIdx)
                }
                val result = inst.result
                if (result != null) {
                    defs[result.name] = instIdx
                    types[result.name] = result.type
                }
                for (use in operandValues(inst)) {
                    val name = use.name
                    if (name in defs || name in types) {
                        lastUses[name] = instIdx
                        useCounts[name] = (useCounts[name] ?: 0) + 1
                    }
                }
            }
            blockStartPos[blockIdx] = blockFirst
            blockEndPos[blockIdx] = instIdx
        }

        // Extend live ranges across loop back-edges
        for ((blockIdx, block) in fn.blocks.withIndex()) {
            val lastInst = block.instructions.lastOrNull() ?: continue
            val targets: List<String> = when (lastInst) {
                is Instruction.Br -> listOf(lastInst.target)
                is Instruction.CondBr -> listOf(lastInst.trueTarget, lastInst.falseTarget)
                else -> emptyList()
            }
            for (target in targets) {
                val targetIdx = blockLabelToIndex[target] ?: continue
                if (targetIdx <= blockIdx) {
                    val headerStart = blockStartPos[targetIdx] ?: continue
                    val loopEnd = blockEndPos[blockIdx] ?: continue
                    for ((name, defPos) in defs) {
                        if (defPos < headerStart) {
                            val lastUse = lastUses[name] ?: defPos
                            if (lastUse >= headerStart && lastUse < loopEnd) {
                                lastUses[name] = loopEnd
                            }
                        }
                    }
                }
            }
        }

        val intervals = mutableListOf<LiveInterval>()
        for ((name, start) in defs) {
            val end = lastUses[name] ?: start
            val type = types[name] ?: continue
            val count = useCounts[name] ?: 0
            val acrossCall = callPositions.any { it in (start + 1)..end }
            intervals.add(LiveInterval(name, start, end, type, count, acrossCall))
        }
        return intervals.sortedBy { it.start }
    }

    companion object {
        /**
         * Extract operand values from an instruction.
         */
        @JvmStatic
        fun operandValues(inst: Instruction): List<Value> {
            val values = mutableListOf<Value>()
            fun add(v: Value) {
                if (v is Parameter || v is InstructionRef) values.add(v)
            }
            when (inst) {
                is Instruction.Add -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.Sub -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.Mul -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.And -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.Or -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.Xor -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.ICmp -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.Ret -> inst.value?.let { add(it) }
                is Instruction.Call -> inst.args.forEach { add(it) }
                is Instruction.GetElementPtr -> { add(inst.ptr); inst.indices.forEach { add(it) } }
                is Instruction.Neg -> add(inst.operand)
                is Instruction.Not -> add(inst.operand)
                is Instruction.Shl -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.LShr -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.AShr -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.UDiv -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.SDiv -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.URem -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.SRem -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.ZExt -> add(inst.value)
                is Instruction.SExt -> add(inst.value)
                is Instruction.Trunc -> add(inst.operand)
                is Instruction.IntTrunc -> add(inst.value)
                is Instruction.PtrToInt -> add(inst.value)
                is Instruction.IntToPtr -> add(inst.value)
                is Instruction.BitCast -> add(inst.value)
                is Instruction.Alloca -> inst.numElements?.let { add(it) }
                is Instruction.Load -> add(inst.ptr)
                is Instruction.Store -> { add(inst.value); add(inst.ptr) }
                is Instruction.Select -> { add(inst.condition); add(inst.trueValue); add(inst.falseValue) }
                is Instruction.Phi -> inst.incoming.forEach { add(it.first) }
                is Instruction.CondBr -> add(inst.condition)
                is Instruction.Switch -> add(inst.value)
                is Instruction.Br -> {}
                is Instruction.FAdd -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.FSub -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.FMul -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.FDiv -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.FNeg -> add(inst.operand)
                is Instruction.FCmp -> { add(inst.lhs); add(inst.rhs) }
                is Instruction.SIToFP -> add(inst.value)
                is Instruction.UIToFP -> add(inst.value)
                is Instruction.FPToUI -> add(inst.value)
                is Instruction.FPToSI -> add(inst.value)
                is Instruction.FPTrunc -> add(inst.value)
                is Instruction.FPExt -> add(inst.value)
                is Instruction.ExtractValue -> add(inst.aggregate)
                is Instruction.InsertValue -> { add(inst.aggregate); add(inst.element) }
                is Instruction.VAStart -> add(inst.argList)
                is Instruction.VAEnd -> add(inst.argList)
                is Instruction.VACopy -> { add(inst.dst); add(inst.src) }
                is Instruction.VAArg -> add(inst.argList)
                else -> {}
            }
            return values
        }
    }
}

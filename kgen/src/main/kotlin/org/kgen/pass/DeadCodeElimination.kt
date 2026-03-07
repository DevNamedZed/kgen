package org.kgen.pass

import org.kgen.ir.*

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
class DeadCodeElimination : ModulePass {

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
                    if (result == null || hasSideEffects(inst)) {
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
                for (v in operandValues(inst)) {
                    used.add(v.name)
                }
            }
        }
        return used
    }

    private fun hasSideEffects(inst: Instruction): Boolean = when (inst) {
        is Instruction.Call -> true
        is Instruction.Store -> true
        is Instruction.Ret -> true
        is Instruction.Br -> true
        is Instruction.CondBr -> true
        is Instruction.Switch -> true
        is Instruction.IndirectBr -> true
        is Instruction.Unreachable -> true
        is Instruction.Invoke -> true
        is Instruction.Resume -> true
        is Instruction.CatchSwitch -> true
        is Instruction.CatchRet -> true
        is Instruction.CleanupRet -> true
        is Instruction.Fence -> true
        is Instruction.AtomicRMW -> true
        is Instruction.CmpXchg -> true
        else -> false
    }

    private fun operandValues(inst: Instruction): List<Value> {
        val values = mutableListOf<Value>()
        fun add(v: Value) { if (v is InstructionRef || v is Parameter) values.add(v) }

        when (inst) {
            is Instruction.Add -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Sub -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Mul -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.SDiv -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.UDiv -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.SRem -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.URem -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.And -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Or -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Xor -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Shl -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.LShr -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.AShr -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Neg -> add(inst.operand)
            is Instruction.ICmp -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FCmp -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FAdd -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FSub -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FMul -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FDiv -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.FNeg -> add(inst.operand)
            is Instruction.Ret -> inst.value?.let { add(it) }
            is Instruction.Call -> inst.args.forEach { add(it) }
            is Instruction.Select -> { add(inst.condition); add(inst.trueValue); add(inst.falseValue) }
            is Instruction.Store -> { add(inst.value); add(inst.ptr) }
            is Instruction.Load -> add(inst.ptr)
            is Instruction.CondBr -> add(inst.condition)
            is Instruction.ZExt -> add(inst.value)
            is Instruction.SExt -> add(inst.value)
            is Instruction.Trunc -> add(inst.operand)
            is Instruction.IntTrunc -> add(inst.value)
            is Instruction.SIToFP -> add(inst.value)
            is Instruction.UIToFP -> add(inst.value)
            is Instruction.FPToSI -> add(inst.value)
            is Instruction.FPToUI -> add(inst.value)
            is Instruction.FPExt -> add(inst.value)
            is Instruction.FPTrunc -> add(inst.value)
            is Instruction.GetElementPtr -> { add(inst.ptr); inst.indices.forEach { add(it) } }
            is Instruction.Phi -> inst.incoming.forEach { add(it.first) }
            else -> {}
        }
        return values
    }
}

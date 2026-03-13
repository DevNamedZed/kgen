package org.kgen.pass

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
        is Call -> true
        is Store -> true
        is Ret -> true
        is Br -> true
        is CondBr -> true
        is Switch -> true
        is IndirectBr -> true
        is Unreachable -> true
        is Invoke -> true
        is Resume -> true
        is CatchSwitch -> true
        is CatchRet -> true
        is CleanupRet -> true
        is Fence -> true
        is AtomicRMW -> true
        is CmpXchg -> true
        else -> false
    }

    private fun operandValues(inst: Instruction): List<Value> {
        val values = mutableListOf<Value>()
        fun add(v: Value) { if (v is InstructionRef || v is Parameter) values.add(v) }

        when (inst) {
            is Add -> { add(inst.lhs); add(inst.rhs) }
            is Sub -> { add(inst.lhs); add(inst.rhs) }
            is Mul -> { add(inst.lhs); add(inst.rhs) }
            is SDiv -> { add(inst.lhs); add(inst.rhs) }
            is UDiv -> { add(inst.lhs); add(inst.rhs) }
            is SRem -> { add(inst.lhs); add(inst.rhs) }
            is URem -> { add(inst.lhs); add(inst.rhs) }
            is And -> { add(inst.lhs); add(inst.rhs) }
            is Or -> { add(inst.lhs); add(inst.rhs) }
            is Xor -> { add(inst.lhs); add(inst.rhs) }
            is Shl -> { add(inst.lhs); add(inst.rhs) }
            is LShr -> { add(inst.lhs); add(inst.rhs) }
            is AShr -> { add(inst.lhs); add(inst.rhs) }
            is Neg -> add(inst.operand)
            is ICmp -> { add(inst.lhs); add(inst.rhs) }
            is FCmp -> { add(inst.lhs); add(inst.rhs) }
            is FAdd -> { add(inst.lhs); add(inst.rhs) }
            is FSub -> { add(inst.lhs); add(inst.rhs) }
            is FMul -> { add(inst.lhs); add(inst.rhs) }
            is FDiv -> { add(inst.lhs); add(inst.rhs) }
            is FNeg -> add(inst.operand)
            is Ret -> inst.value?.let { add(it) }
            is Call -> inst.args.forEach { add(it) }
            is Select -> { add(inst.condition); add(inst.trueValue); add(inst.falseValue) }
            is Store -> { add(inst.value); add(inst.ptr) }
            is Load -> add(inst.ptr)
            is CondBr -> add(inst.condition)
            is ZExt -> add(inst.value)
            is SExt -> add(inst.value)
            is Trunc -> add(inst.operand)
            is IntTrunc -> add(inst.value)
            is SIToFP -> add(inst.value)
            is UIToFP -> add(inst.value)
            is FPToSI -> add(inst.value)
            is FPToUI -> add(inst.value)
            is FPExt -> add(inst.value)
            is FPTrunc -> add(inst.value)
            is GetElementPtr -> { add(inst.ptr); inst.indices.forEach { add(it) } }
            is Phi -> inst.incoming.forEach { add(it.first) }
            else -> {}
        }
        return values
    }
}

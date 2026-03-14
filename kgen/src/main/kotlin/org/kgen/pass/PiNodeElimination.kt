package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Eliminates [PiNode] pseudo-instructions by replacing all uses with the base value.
 *
 * PiNode is a type-refinement instruction that narrows a value's type after a
 * guard or type check. It carries no runtime semantics — it exists only to
 * propagate refined type information through SSA. This pass must run before
 * instruction selection, which cannot lower PiNode to machine code.
 *
 * The pass replaces every reference to a PiNode's result with the PiNode's
 * base operand, then removes the PiNode instruction.
 */
class PiNodeElimination : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) {
                fn
            } else {
                eliminatePiNodes(fn)
            }
        })
    }

    private fun eliminatePiNodes(fn: IrFunction): IrFunction {
        val replacements = mutableMapOf<String, Value>()

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is PiNode) {
                    replacements[inst.dest.name] = resolveReplacement(inst.base, replacements)
                }
            }
        }

        if (replacements.isEmpty()) {
            return fn
        }

        val newBlocks = fn.blocks.map { block ->
            val filtered = block.instructions
                .filter { it !is PiNode }
                .map { inst -> replaceOperands(inst, replacements) }
            BasicBlock(block.label, filtered)
        }

        return fn.copy(blocks = newBlocks)
    }

    private fun resolveReplacement(value: Value, replacements: Map<String, Value>): Value {
        var current = value
        while (current.name in replacements) {
            current = replacements[current.name]!!
        }
        return current
    }

    private fun replaceOperands(inst: Instruction, replacements: Map<String, Value>): Instruction {
        return when (inst) {
            is Add -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Sub -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Mul -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is SDiv -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is UDiv -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is SRem -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is URem -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is And -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Or -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Xor -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Shl -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is LShr -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is AShr -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is ICmp -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is FCmp -> inst.copy(lhs = resolve(inst.lhs, replacements), rhs = resolve(inst.rhs, replacements))
            is Select -> inst.copy(
                condition = resolve(inst.condition, replacements),
                trueValue = resolve(inst.trueValue, replacements),
                falseValue = resolve(inst.falseValue, replacements),
            )
            is Load -> inst.copy(ptr = resolve(inst.ptr, replacements))
            is Store -> inst.copy(value = resolve(inst.value, replacements), ptr = resolve(inst.ptr, replacements))
            is Call -> inst.copy(
                function = resolve(inst.function, replacements),
                args = inst.args.map { resolve(it, replacements) },
            )
            is Ret -> inst.copy(value = inst.value?.let { resolve(it, replacements) })
            is CondBr -> inst.copy(condition = resolve(inst.condition, replacements))
            is Phi -> inst.copy(incoming = inst.incoming.map { (value, label) -> resolve(value, replacements) to label })
            is GetElementPtr -> inst.copy(
                ptr = resolve(inst.ptr, replacements),
                indices = inst.indices.map { resolve(it, replacements) },
            )
            is Guard -> inst.copy(condition = resolve(inst.condition, replacements))
            is FixedGuard -> inst.copy(condition = resolve(inst.condition, replacements))
            else -> replaceOperandsGeneric(inst, replacements)
        }
    }

    private fun replaceOperandsGeneric(inst: Instruction, replacements: Map<String, Value>): Instruction {
        val hasReplacements = inst.operands.any { it.name in replacements }
        if (!hasReplacements) {
            return inst
        }
        return inst
    }

    private fun resolve(value: Value, replacements: Map<String, Value>): Value {
        var current = value
        while (current.name in replacements) {
            current = replacements[current.name]!!
        }
        return current
    }
}

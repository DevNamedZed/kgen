package org.kgen.pass

import org.kgen.ir.*

/**
 * Global Value Numbering (GVN) — eliminates redundant computations.
 *
 * Two instructions compute the same value if they have the same opcode
 * and operands (after value numbering). When a duplicate is found, all
 * uses of the duplicate are replaced with the original.
 *
 * This is a simplified dominator-based GVN that processes blocks in
 * dominator tree order, maintaining a scoped value table.
 *
 * Handles: arithmetic, bitwise, comparisons, conversions, GEP.
 * Does NOT handle: loads (would need alias analysis), calls (side effects).
 */
class GlobalValueNumbering : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.isEmpty()) fn else gvnFunction(fn)
        })
    }

    private fun gvnFunction(fn: IrFunction): IrFunction {
        val cfg = buildCfg(fn)
        val idom = computeImmediateDominators(fn, cfg)
        val domChildren = buildDomTree(fn, idom)
        val entry = fn.blocks[0].label

        val replacements = mutableMapOf<String, Value>()
        val blockInsts = fn.blocks.associate { it.label to it.instructions.toMutableList() }

        val valueTable = ScopedValueTable()

        fun processBlock(blockLabel: String) {
            valueTable.pushScope()
            val insts = blockInsts[blockLabel]!!
            val toRemove = mutableSetOf<Int>()

            for (i in insts.indices) {
                val inst = insts[i]
                val rewritten = rewriteOperands(inst, replacements)
                insts[i] = rewritten

                val key = computeKey(rewritten)
                if (key != null) {
                    val existing = valueTable.lookup(key)
                    if (existing != null) {
                        val destName = rewritten.result?.name ?: continue
                        replacements[destName] = existing
                        toRemove.add(i)
                    } else {
                        val result = rewritten.result
                        if (result != null) {
                            valueTable.insert(key, result)
                        }
                    }
                }
            }

            for (i in toRemove.sortedDescending()) {
                insts.removeAt(i)
            }

            for (child in domChildren[blockLabel] ?: emptyList()) {
                processBlock(child)
            }

            valueTable.popScope()
        }

        processBlock(entry)

        val resultBlocks = fn.blocks.map { block ->
            val insts = blockInsts[block.label]!!
            BasicBlock(block.label, insts.map { rewriteOperands(it, replacements) })
        }
        return fn.copy(blocks = resultBlocks)
    }

    private fun computeKey(inst: Instruction): String? = when (inst) {
        is Instruction.Add -> "add(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Sub -> "sub(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Mul -> "mul(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.SDiv -> "sdiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.UDiv -> "udiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.SRem -> "srem(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.URem -> "urem(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.And -> "and(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Or -> "or(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Xor -> "xor(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Shl -> "shl(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.LShr -> "lshr(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.AShr -> "ashr(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.Neg -> "neg(${vn(inst.operand)})"
        is Instruction.Not -> "not(${vn(inst.operand)})"
        is Instruction.ICmp -> "icmp.${inst.predicate}(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FCmp -> "fcmp.${inst.predicate}(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FAdd -> "fadd(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FSub -> "fsub(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FMul -> "fmul(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FDiv -> "fdiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Instruction.FNeg -> "fneg(${vn(inst.operand)})"
        is Instruction.ZExt -> "zext.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.SExt -> "sext.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.IntTrunc -> "trunc.${inst.toType}(${vn(inst.value)})"
        is Instruction.BitCast -> "bitcast.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.SIToFP -> "sitofp.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.UIToFP -> "uitofp.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.FPToSI -> "fptosi.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.FPToUI -> "fptoui.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.FPExt -> "fpext.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.FPTrunc -> "fptrunc.${inst.dest.type}(${vn(inst.value)})"
        is Instruction.GetElementPtr -> "gep.${inst.baseType}(${vn(inst.ptr)},${inst.indices.joinToString(",") { vn(it) }})"
        is Instruction.Select -> "select(${vn(inst.condition)},${vn(inst.trueValue)},${vn(inst.falseValue)})"
        else -> null
    }

    private fun vn(v: Value): String = v.name

    private class ScopedValueTable {
        private val scopes = ArrayDeque<MutableMap<String, Value>>()

        fun pushScope() { scopes.addFirst(mutableMapOf()) }

        fun popScope() { scopes.removeFirst() }

        fun lookup(key: String): Value? {
            for (scope in scopes) {
                scope[key]?.let { return it }
            }
            return null
        }

        fun insert(key: String, value: Value) {
            scopes.first()[key] = value
        }
    }

    private data class Cfg(
        val predecessors: Map<String, Set<String>>,
        val successors: Map<String, Set<String>>,
    )

    private fun buildCfg(fn: IrFunction): Cfg {
        val preds = mutableMapOf<String, MutableSet<String>>()
        val succs = mutableMapOf<String, MutableSet<String>>()
        for (block in fn.blocks) {
            preds.getOrPut(block.label) { mutableSetOf() }
            succs.getOrPut(block.label) { mutableSetOf() }
        }
        for (block in fn.blocks) {
            val term = block.instructions.lastOrNull() ?: continue
            for (t in terminatorTargets(term)) {
                succs.getOrPut(block.label) { mutableSetOf() }.add(t)
                preds.getOrPut(t) { mutableSetOf() }.add(block.label)
            }
        }
        return Cfg(preds, succs)
    }

    private fun terminatorTargets(inst: Instruction): List<String> = when (inst) {
        is Instruction.Br -> listOf(inst.target)
        is Instruction.CondBr -> listOf(inst.trueTarget, inst.falseTarget)
        is Instruction.Switch -> listOf(inst.defaultTarget) + inst.cases.map { it.second }
        is Instruction.IndirectBr -> inst.targets
        is Instruction.Invoke -> listOf(inst.normalDest, inst.unwindDest)
        else -> emptyList()
    }

    private fun computeImmediateDominators(fn: IrFunction, cfg: Cfg): Map<String, String> {
        val blocks = fn.blocks.map { it.label }
        val entry = blocks[0]
        val allBlocks = blocks.toSet()

        val dom = mutableMapOf<String, MutableSet<String>>()
        dom[entry] = mutableSetOf(entry)
        for (b in blocks) {
            if (b != entry) dom[b] = allBlocks.toMutableSet()
        }

        var changed = true
        while (changed) {
            changed = false
            for (b in blocks) {
                if (b == entry) continue
                val preds = cfg.predecessors[b] ?: continue
                if (preds.isEmpty()) continue
                var newDom = allBlocks.toMutableSet()
                for (p in preds) {
                    newDom = newDom.intersect(dom[p] ?: emptySet()).toMutableSet()
                }
                newDom.add(b)
                if (newDom != dom[b]) {
                    dom[b] = newDom
                    changed = true
                }
            }
        }

        val idom = mutableMapOf<String, String>()
        idom[entry] = entry
        for (b in blocks) {
            if (b == entry) continue
            val dominators = dom[b] ?: continue
            val strictDoms = dominators - b
            if (strictDoms.isEmpty()) continue
            idom[b] = strictDoms.maxByOrNull { dom[it]?.size ?: 0 } ?: entry
        }
        return idom
    }

    private fun buildDomTree(fn: IrFunction, idom: Map<String, String>): Map<String, List<String>> {
        val children = mutableMapOf<String, MutableList<String>>()
        for (block in fn.blocks) {
            children[block.label] = mutableListOf()
        }
        for ((child, parent) in idom) {
            if (child != parent) {
                children.getOrPut(parent) { mutableListOf() }.add(child)
            }
        }
        return children
    }

    private fun rewriteOperands(inst: Instruction, replacements: Map<String, Value>): Instruction {
        if (replacements.isEmpty()) return inst
        fun rw(v: Value): Value = if (v is InstructionRef || v is Parameter) replacements[v.name] ?: v else v

        return when (inst) {
            is Instruction.Add -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Sub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Mul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.SDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.UDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.SRem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.URem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.And -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Or -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Xor -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Shl -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.LShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.AShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Neg -> inst.copy(operand = rw(inst.operand))
            is Instruction.ICmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FAdd -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FSub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FMul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FNeg -> inst.copy(operand = rw(inst.operand))
            is Instruction.FCmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.ZExt -> inst.copy(value = rw(inst.value))
            is Instruction.SExt -> inst.copy(value = rw(inst.value))
            is Instruction.IntTrunc -> inst.copy(value = rw(inst.value))
            is Instruction.Trunc -> inst.copy(operand = rw(inst.operand))
            is Instruction.Ret -> inst.copy(value = inst.value?.let { rw(it) })
            is Instruction.Call -> inst.copy(args = inst.args.map { rw(it) })
            is Instruction.Select -> inst.copy(condition = rw(inst.condition), trueValue = rw(inst.trueValue), falseValue = rw(inst.falseValue))
            is Instruction.Store -> inst.copy(value = rw(inst.value), ptr = rw(inst.ptr))
            is Instruction.Load -> inst.copy(ptr = rw(inst.ptr))
            is Instruction.CondBr -> inst.copy(condition = rw(inst.condition))
            is Instruction.SIToFP -> inst.copy(value = rw(inst.value))
            is Instruction.UIToFP -> inst.copy(value = rw(inst.value))
            is Instruction.FPToSI -> inst.copy(value = rw(inst.value))
            is Instruction.FPToUI -> inst.copy(value = rw(inst.value))
            is Instruction.FPExt -> inst.copy(value = rw(inst.value))
            is Instruction.FPTrunc -> inst.copy(value = rw(inst.value))
            is Instruction.GetElementPtr -> inst.copy(ptr = rw(inst.ptr), indices = inst.indices.map { rw(it) })
            is Instruction.Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rw(v) to l })
            else -> inst
        }
    }
}

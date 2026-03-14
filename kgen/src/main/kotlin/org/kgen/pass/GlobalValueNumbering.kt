package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

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
 * Handles: arithmetic, bitwise, comparisons, conversions, GEP, loads.
 * Does NOT handle: calls (side effects).
 *
 * Load elimination uses [BasicAliasAnalysis] to determine when intervening
 * stores can be proven not to alias a cached load, allowing redundant loads
 * to be eliminated even across stores to unrelated memory.
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
        val aa = BasicAliasAnalysis(fn)

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

                val key = computeKey(rewritten, aa)
                if (key != null) {
                    val existing = valueTable.lookup(key)
                    if (existing != null) {
                        val destName = rewritten.result?.name ?: continue
                        replacements[destName] = existing
                        toRemove.add(i)
                    } else {
                        val result = rewritten.result
                        if (result != null) {
                            if (rewritten is Load) {
                                valueTable.insertLoad(key, result, rewritten.ptr)
                            } else {
                                valueTable.insert(key, result)
                            }
                        }
                    }
                }

                // Stores invalidate load entries for aliasing pointers
                if (rewritten is Store) {
                    valueTable.invalidateLoads(rewritten.ptr, aa)
                }
                // Calls may write to any memory — invalidate all loads
                if (rewritten.effects.isCall()) {
                    valueTable.invalidateAllLoads()
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

    private fun computeKey(inst: Instruction, aa: AliasAnalysis): String? = when (inst) {
        is Add -> "add(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Sub -> "sub(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Mul -> "mul(${vn(inst.lhs)},${vn(inst.rhs)})"
        is SDiv -> "sdiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is UDiv -> "udiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is SRem -> "srem(${vn(inst.lhs)},${vn(inst.rhs)})"
        is URem -> "urem(${vn(inst.lhs)},${vn(inst.rhs)})"
        is And -> "and(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Or -> "or(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Xor -> "xor(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Shl -> "shl(${vn(inst.lhs)},${vn(inst.rhs)})"
        is LShr -> "lshr(${vn(inst.lhs)},${vn(inst.rhs)})"
        is AShr -> "ashr(${vn(inst.lhs)},${vn(inst.rhs)})"
        is Neg -> "neg(${vn(inst.operand)})"
        is Not -> "not(${vn(inst.operand)})"
        is ICmp -> "icmp.${inst.predicate}(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FCmp -> "fcmp.${inst.predicate}(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FAdd -> "fadd(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FSub -> "fsub(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FMul -> "fmul(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FDiv -> "fdiv(${vn(inst.lhs)},${vn(inst.rhs)})"
        is FNeg -> "fneg(${vn(inst.operand)})"
        is ZExt -> "zext.${inst.dest.type}(${vn(inst.value)})"
        is SExt -> "sext.${inst.dest.type}(${vn(inst.value)})"
        is IntTrunc -> "trunc.${inst.toType}(${vn(inst.value)})"
        is BitCast -> "bitcast.${inst.dest.type}(${vn(inst.value)})"
        is SIToFP -> "sitofp.${inst.dest.type}(${vn(inst.value)})"
        is UIToFP -> "uitofp.${inst.dest.type}(${vn(inst.value)})"
        is FPToSI -> "fptosi.${inst.dest.type}(${vn(inst.value)})"
        is FPToUI -> "fptoui.${inst.dest.type}(${vn(inst.value)})"
        is FPExt -> "fpext.${inst.dest.type}(${vn(inst.value)})"
        is FPTrunc -> "fptrunc.${inst.dest.type}(${vn(inst.value)})"
        is GetElementPtr -> "gep.${inst.baseType}(${vn(inst.ptr)},${inst.indices.joinToString(",") { vn(it) }})"
        is Select -> "select(${vn(inst.condition)},${vn(inst.trueValue)},${vn(inst.falseValue)})"
        // Loads: two loads from the same pointer with no intervening store produce the same value
        is Load -> if (!inst.volatile) "load.${inst.loadType}(${vn(inst.ptr)})" else null
        else -> null
    }

    private fun vn(v: Value): String = v.name

    private class ScopedValueTable {
        private val scopes = ArrayDeque<MutableMap<String, Value>>()
        // Track the actual pointer Value for each load key, so alias analysis works correctly
        private val loadPointers = ArrayDeque<MutableMap<String, Value>>()

        fun pushScope() {
            scopes.addFirst(mutableMapOf())
            loadPointers.addFirst(mutableMapOf())
        }

        fun popScope() {
            scopes.removeFirst()
            loadPointers.removeFirst()
        }

        fun lookup(key: String): Value? {
            for (scope in scopes) {
                scope[key]?.let { return it }
            }
            return null
        }

        fun insert(key: String, value: Value) {
            scopes.first()[key] = value
        }

        fun insertLoad(key: String, value: Value, ptr: Value) {
            scopes.first()[key] = value
            loadPointers.first()[key] = ptr
        }

        /**
         * Invalidate load entries that may alias the stored pointer.
         */
        fun invalidateLoads(storePtr: Value, aa: AliasAnalysis) {
            for (i in scopes.indices) {
                val scope = scopes.elementAt(i)
                val ptrs = loadPointers.elementAt(i)
                val toRemove = scope.keys.filter { key ->
                    if (!key.startsWith("load.")) return@filter false
                    val loadPtr = ptrs[key] ?: return@filter true // unknown ptr → invalidate
                    aa.alias(storePtr, loadPtr) != AliasResult.NoAlias
                }
                for (k in toRemove) {
                    scope.remove(k)
                    ptrs.remove(k)
                }
            }
        }

        /**
         * Invalidate all load entries (e.g., after a call that may write memory).
         */
        fun invalidateAllLoads() {
            for (i in scopes.indices) {
                val scope = scopes.elementAt(i)
                val ptrs = loadPointers.elementAt(i)
                val loadKeys = scope.keys.filter { it.startsWith("load.") }
                for (k in loadKeys) {
                    scope.remove(k)
                    ptrs.remove(k)
                }
            }
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
        is Br -> listOf(inst.target.label)
        is CondBr -> listOf(inst.trueTarget.label, inst.falseTarget.label)
        is Switch -> listOf(inst.defaultTarget.label) + inst.cases.map { it.second.label }
        is IndirectBr -> inst.targets.map { it.label }
        is Invoke -> listOf(inst.normalDest.label, inst.unwindDest.label)
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
            else -> inst
        }
    }
}

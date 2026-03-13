package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Promotes alloca/load/store patterns to SSA values with phi nodes.
 *
 * An alloca is promotable if:
 * - It allocates a single scalar value (no numElements)
 * - Its address is only used in load and store instructions (not passed to calls, GEP, etc.)
 * - Loads and stores are non-volatile and non-atomic
 *
 * For each promotable alloca, this pass:
 * 1. Computes which blocks contain stores (def sites)
 * 2. Inserts phi nodes at iterated dominance frontiers
 * 3. Renames values by walking the dominator tree
 * 4. Removes the now-dead alloca, load, and store instructions
 */
class Mem2Reg : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.isEmpty()) fn else promoteFunction(fn)
        })
    }

    private fun promoteFunction(fn: IrFunction): IrFunction {
        val allocas = findPromotableAllocas(fn)
        if (allocas.isEmpty()) return fn

        val cfg = buildCfg(fn)
        val idom = computeImmediateDominators(fn, cfg)
        val domFrontier = computeDominanceFrontier(fn, cfg, idom)
        val domChildren = buildDomTree(fn, idom)
        val entry = fn.blocks[0].label

        val blockInsts = fn.blocks.associate { it.label to it.instructions.toMutableList() }
        val globalReplacements = mutableMapOf<String, Value>()

        var nextId = findMaxInstructionId(fn) + 1

        for ((allocaName, alloca) in allocas) {
            val allocType = alloca.allocType

            val defBlocks = mutableSetOf<String>()
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst is Store && inst.ptr.name == allocaName) {
                        defBlocks.add(block.label)
                    }
                }
            }

            val phiBlocks = computeIteratedDominanceFrontier(defBlocks, domFrontier)

            val phiRefs = mutableMapOf<String, InstructionRef>()
            for (phiBlock in phiBlocks) {
                val phiRef = InstructionRef("%$nextId", allocType)
                nextId++
                val preds = cfg.predecessors[phiBlock] ?: emptySet()
                val placeholder = undefFor(allocType)
                val phi = Phi(phiRef, preds.map { placeholder to it })
                val insts = blockInsts[phiBlock]!!
                insts.add(0, phi)
                phiRefs[phiBlock] = phiRef
            }

            val defStack = ArrayDeque<Value>()
            defStack.addFirst(undefFor(allocType))

            fun renameBlock(blockLabel: String) {
                val stackSize = defStack.size
                val insts = blockInsts[blockLabel]!!
                val toRemove = mutableSetOf<Int>()

                for (i in insts.indices) {
                    val inst = insts[i]
                    when {
                        inst is Phi && phiRefs[blockLabel] == inst.dest -> {
                            defStack.addFirst(inst.dest)
                        }
                        inst is Alloca && inst.dest.name == allocaName -> {
                            toRemove.add(i)
                        }
                        inst is Store && inst.ptr.name == allocaName -> {
                            defStack.addFirst(inst.value)
                            toRemove.add(i)
                        }
                        inst is Load && inst.ptr.name == allocaName -> {
                            globalReplacements[inst.dest.name] = defStack.first()
                            toRemove.add(i)
                        }
                    }
                }

                // Remove marked instructions in reverse order
                for (i in toRemove.sortedDescending()) {
                    insts.removeAt(i)
                }

                // Fill in phi incoming values for successors
                for (succ in cfg.successors[blockLabel] ?: emptySet()) {
                    val phiRef = phiRefs[succ] ?: continue
                    val succInsts = blockInsts[succ]!!
                    for (i in succInsts.indices) {
                        val phi = succInsts[i]
                        if (phi is Phi && phi.dest == phiRef) {
                            val currentVal = defStack.first()
                            succInsts[i] = phi.copy(
                                incoming = phi.incoming.map { (v, pred) ->
                                    if (pred == blockLabel) currentVal to pred else v to pred
                                }
                            )
                        }
                    }
                }

                // Recurse into dominator tree children
                for (child in domChildren[blockLabel] ?: emptyList()) {
                    renameBlock(child)
                }

                // Restore the def stack
                while (defStack.size > stackSize) {
                    defStack.removeFirst()
                }
            }

            renameBlock(entry)
        }

        // Resolve transitive replacements: if %6 → %7 and %7 → %15, then %6 → %15
        for (key in globalReplacements.keys.toList()) {
            var value = globalReplacements[key] ?: continue
            val seen = mutableSetOf(key)
            while (value is InstructionRef && value.name in globalReplacements && value.name !in seen) {
                seen.add(value.name)
                value = globalReplacements[value.name]!!
            }
            globalReplacements[key] = value
        }

        // Rewrite all remaining instructions to use SSA replacements
        val resultBlocks = fn.blocks.map { block ->
            val insts = blockInsts[block.label]!!
            BasicBlock(block.label, insts.map { rewriteOperands(it, globalReplacements) })
        }

        return fn.copy(blocks = resultBlocks)
    }

    private fun findPromotableAllocas(fn: IrFunction): Map<String, Alloca> {
        val allocas = mutableMapOf<String, Alloca>()

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is Alloca && inst.numElements == null) {
                    allocas[inst.dest.name] = inst
                }
            }
        }

        if (allocas.isEmpty()) return emptyMap()

        val addressTaken = mutableSetOf<String>()

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                when (inst) {
                    is Load -> {
                        if (inst.ptr.name in allocas && (inst.volatile || inst.ordering != null)) {
                            addressTaken.add(inst.ptr.name)
                        }
                    }
                    is Store -> {
                        if (inst.ptr.name in allocas && (inst.volatile || inst.ordering != null)) {
                            addressTaken.add(inst.ptr.name)
                        }
                        // If stored value IS the alloca (address escapes), mark it
                        if (inst.value.name in allocas) {
                            addressTaken.add(inst.value.name)
                        }
                    }
                    else -> {
                        // Any other use of the alloca address means it escapes
                        for (op in nonLoadStoreOperands(inst)) {
                            if (op.name in allocas) {
                                addressTaken.add(op.name)
                            }
                        }
                    }
                }
            }
        }

        return allocas.filterKeys { it !in addressTaken }
    }

    private fun nonLoadStoreOperands(inst: Instruction): List<Value> = when (inst) {
        is Call -> inst.args
        is GetElementPtr -> listOf(inst.ptr) + inst.indices
        is MemCpy -> listOf(inst.dst, inst.src)
        is MemSet -> listOf(inst.dst)
        is MemMove -> listOf(inst.dst, inst.src)
        is CmpXchg -> listOf(inst.ptr)
        is AtomicRMW -> listOf(inst.ptr)
        is PtrToInt -> listOf(inst.value)
        is BitCast -> listOf(inst.value)
        is Ret -> listOfNotNull(inst.value)
        is Select -> listOf(inst.condition, inst.trueValue, inst.falseValue)
        is ICmp -> listOf(inst.lhs, inst.rhs)
        is Add -> listOf(inst.lhs, inst.rhs)
        is Sub -> listOf(inst.lhs, inst.rhs)
        is Mul -> listOf(inst.lhs, inst.rhs)
        is Phi -> inst.incoming.map { it.first }
        else -> emptyList()
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
        is Br -> listOf(inst.target)
        is CondBr -> listOf(inst.trueTarget, inst.falseTarget)
        is Switch -> listOf(inst.defaultTarget) + inst.cases.map { it.second }
        is IndirectBr -> inst.targets
        is Invoke -> listOf(inst.normalDest, inst.unwindDest)
        is CallBr -> listOf(inst.fallthrough) + inst.indirectDests
        is CatchSwitch -> inst.handlers + listOfNotNull(inst.unwindDest)
        else -> emptyList()
    }

    private fun computeImmediateDominators(fn: IrFunction, cfg: Cfg): Map<String, String> {
        val blocks = ArrayList<String>(fn.blocks.size)
        for (b in fn.blocks) blocks.add(b.label)
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

    private fun computeDominanceFrontier(fn: IrFunction, cfg: Cfg, idom: Map<String, String>): Map<String, Set<String>> {
        val df = mutableMapOf<String, MutableSet<String>>()
        for (block in fn.blocks) {
            df[block.label] = mutableSetOf()
        }

        for (block in fn.blocks) {
            val b = block.label
            val preds = cfg.predecessors[b] ?: emptySet()
            if (preds.size < 2) continue
            for (p in preds) {
                var runner = p
                while (runner != idom[b]) {
                    df.getOrPut(runner) { mutableSetOf() }.add(b)
                    runner = idom[runner] ?: break
                }
            }
        }
        return df
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

    private fun computeIteratedDominanceFrontier(defBlocks: Set<String>, domFrontier: Map<String, Set<String>>): Set<String> {
        val result = mutableSetOf<String>()
        val worklist = ArrayDeque(defBlocks)
        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            for (df in domFrontier[block] ?: emptySet()) {
                if (result.add(df)) {
                    worklist.add(df)
                }
            }
        }
        return result
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

    private fun undefFor(type: Type): Constant = when (type) {
        Type.I1 -> Constant.I1(false)
        Type.I8 -> Constant.I8(0)
        Type.I16 -> Constant.I16(0)
        Type.I32 -> Constant.I32(0)
        Type.I64 -> Constant.I64(0)
        Type.F32 -> Constant.F32(0f)
        Type.F64 -> Constant.F64(0.0)
        else -> Constant.I32(0)
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
            is Trunc -> inst.copy(operand = rw(inst.operand))
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

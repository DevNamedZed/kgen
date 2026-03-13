package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Loop-invariant code motion (LICM) — hoists instructions out of loops
 * when their operands are defined outside the loop or are themselves
 * loop-invariant.
 *
 * Detects natural loops via back edges in the CFG, identifies invariant
 * instructions, and moves them to a preheader block (created if needed).
 */
class LoopInvariantCodeMotion : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.size < 2) fn else hoistFunction(fn)
        })
    }

    private fun hoistFunction(fn: IrFunction): IrFunction {
        val cfg = buildCfg(fn)
        val domTree = computeDominators(fn, cfg)
        val loops = findNaturalLoops(fn, cfg, domTree)
        if (loops.isEmpty()) return fn

        val aa = BasicAliasAnalysis(fn)
        var blocks = fn.blocks.toMutableList()

        for (loop in loops) {
            blocks = hoistLoop(blocks, loop, cfg, aa)
        }

        return fn.copy(blocks = blocks)
    }

    private fun hoistLoop(
        blocks: MutableList<BasicBlock>,
        loop: NaturalLoop,
        cfg: Cfg,
        aa: AliasAnalysis,
    ): MutableList<BasicBlock> {
        val loopBlocks = loop.body
        val header = loop.header

        // Collect all definitions inside the loop
        val loopDefs = mutableSetOf<String>()
        for (block in blocks) {
            if (block.label !in loopBlocks) continue
            for (inst in block.instructions) {
                val result = inst.result
                if (result != null) loopDefs.add(result.name)
            }
        }

        // Collect all stores inside the loop for alias checking
        val loopStores = mutableListOf<Instruction>()
        for (block in blocks) {
            if (block.label !in loopBlocks) continue
            for (inst in block.instructions) {
                if (aa.writesMemory(inst)) loopStores.add(inst)
            }
        }

        // Identify loop-invariant instructions (iterative fixed point)
        val invariant = mutableSetOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for (block in blocks) {
                if (block.label !in loopBlocks) continue
                for (inst in block.instructions) {
                    val result = inst.result ?: continue
                    if (result.name in invariant) continue
                    if (!canHoist(inst, loopStores, aa)) continue
                    if (allOperandsInvariant(inst, loopDefs, invariant)) {
                        invariant.add(result.name)
                        changed = true
                    }
                }
            }
        }

        if (invariant.isEmpty()) return blocks

        // Find or create preheader
        val preds = cfg.predecessors[header] ?: emptySet()
        val externalPreds = preds.filter { it !in loopBlocks }

        val preheaderLabel = "${header}_preheader"
        val hoistedInsts = mutableListOf<Instruction>()

        // Collect hoisted instructions in order
        val newBlocks = mutableListOf<BasicBlock>()
        for (block in blocks) {
            if (block.label !in loopBlocks) {
                newBlocks.add(block)
                continue
            }
            val remaining = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                val result = inst.result
                if (result != null && result.name in invariant) {
                    hoistedInsts.add(inst)
                } else {
                    remaining.add(inst)
                }
            }
            newBlocks.add(BasicBlock(block.label, remaining))
        }

        // Create preheader block: hoisted instructions + br to header
        val preheader = BasicBlock(preheaderLabel, hoistedInsts + Br(header))

        // Redirect external predecessors to preheader
        val result = mutableListOf<BasicBlock>()
        var preheaderInserted = false
        for (block in newBlocks) {
            if (block.label == header && !preheaderInserted) {
                result.add(preheader)
                preheaderInserted = true
            }
            if (block.label in externalPreds.toSet()) {
                // Redirect this block's branches from header to preheader
                val redirected = redirectTerminator(block, header, preheaderLabel)
                result.add(redirected)
            } else {
                result.add(block)
            }
        }

        // Update phi nodes in header: external preds now come from preheader
        val extPredSet = externalPreds.toSet()
        return result.map { block ->
            if (block.label != header) return@map block
            val newInsts = block.instructions.map { inst ->
                if (inst !is Phi) return@map inst
                val newIncoming = inst.incoming.map { (value, pred) ->
                    if (pred in extPredSet) value to preheaderLabel
                    else value to pred
                }
                Phi(inst.dest, newIncoming)
            }
            BasicBlock(block.label, newInsts)
        }.toMutableList()
    }

    private fun canHoist(
        inst: Instruction,
        loopStores: List<Instruction>,
        aa: AliasAnalysis,
    ): Boolean = when (inst) {
        // Pure computation instructions can always be hoisted
        is Add, is Sub, is Mul,
        is SDiv, is UDiv, is SRem, is URem,
        is And, is Or, is Xor,
        is Shl, is LShr, is AShr,
        is ICmp, is Neg,
        is ZExt, is SExt, is Trunc, is IntTrunc,
        is FAdd, is FSub, is FMul, is FDiv,
        is FNeg, is FCmp,
        is SIToFP, is UIToFP, is FPToSI, is FPToUI,
        is FPTrunc, is FPExt,
        is Select, is GetElementPtr -> true

        // Loads can be hoisted if they don't alias any memory write in the loop
        is Load -> {
            !inst.volatile && loopStores.all { store ->
                val storePtr = aa.memoryPointer(store)
                // If we can't determine the write target (e.g., calls), assume it may alias
                storePtr != null && aa.alias(inst.ptr, storePtr) == AliasResult.NoAlias
            }
        }

        // Cannot hoist: stores, calls, branches, phis, allocas
        else -> false
    }

    private fun allOperandsInvariant(
        inst: Instruction,
        loopDefs: Set<String>,
        invariant: Set<String>,
    ): Boolean {
        for (operand in operands(inst)) {
            if (operand is Constant) continue
            if (operand is GlobalRef) continue
            if (operand is FunctionRef) continue
            val name = operand.name
            // Defined outside loop — invariant
            if (name !in loopDefs) continue
            // Defined inside loop but already marked invariant — ok
            if (name in invariant) continue
            return false
        }
        return true
    }

    private fun operands(inst: Instruction): List<Value> = when (inst) {
        is Add -> listOf(inst.lhs, inst.rhs)
        is Sub -> listOf(inst.lhs, inst.rhs)
        is Mul -> listOf(inst.lhs, inst.rhs)
        is SDiv -> listOf(inst.lhs, inst.rhs)
        is UDiv -> listOf(inst.lhs, inst.rhs)
        is SRem -> listOf(inst.lhs, inst.rhs)
        is URem -> listOf(inst.lhs, inst.rhs)
        is And -> listOf(inst.lhs, inst.rhs)
        is Or -> listOf(inst.lhs, inst.rhs)
        is Xor -> listOf(inst.lhs, inst.rhs)
        is Shl -> listOf(inst.lhs, inst.rhs)
        is LShr -> listOf(inst.lhs, inst.rhs)
        is AShr -> listOf(inst.lhs, inst.rhs)
        is ICmp -> listOf(inst.lhs, inst.rhs)
        is Neg -> listOf(inst.operand)
        is ZExt -> listOf(inst.value)
        is SExt -> listOf(inst.value)
        is Trunc -> listOf(inst.operand)
        is IntTrunc -> listOf(inst.value)
        is FAdd -> listOf(inst.lhs, inst.rhs)
        is FSub -> listOf(inst.lhs, inst.rhs)
        is FMul -> listOf(inst.lhs, inst.rhs)
        is FDiv -> listOf(inst.lhs, inst.rhs)
        is FNeg -> listOf(inst.operand)
        is FCmp -> listOf(inst.lhs, inst.rhs)
        is SIToFP -> listOf(inst.value)
        is UIToFP -> listOf(inst.value)
        is FPToSI -> listOf(inst.value)
        is FPToUI -> listOf(inst.value)
        is FPTrunc -> listOf(inst.value)
        is FPExt -> listOf(inst.value)
        is Select -> listOf(inst.condition, inst.trueValue, inst.falseValue)
        is GetElementPtr -> listOf(inst.ptr) + inst.indices
        is Load -> listOf(inst.ptr)
        else -> emptyList()
    }

    private fun redirectTerminator(block: BasicBlock, from: String, to: String): BasicBlock {
        val last = block.instructions.lastOrNull() ?: return block
        val newLast = when (last) {
            is Br -> if (last.target == from) Br(to) else last
            is CondBr -> CondBr(
                last.condition,
                if (last.trueTarget == from) to else last.trueTarget,
                if (last.falseTarget == from) to else last.falseTarget,
            )
            is Switch -> Switch(
                last.value,
                if (last.defaultTarget == from) to else last.defaultTarget,
                last.cases.map { (c, t) -> c to (if (t == from) to else t) },
            )
            else -> last
        }
        return BasicBlock(block.label, block.instructions.dropLast(1) + newLast)
    }

    // --- CFG and loop detection infrastructure ---

    private data class Cfg(
        val predecessors: Map<String, Set<String>>,
        val successors: Map<String, Set<String>>,
    )

    private data class NaturalLoop(
        val header: String,
        val body: Set<String>,
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
        else -> emptyList()
    }

    private fun computeDominators(fn: IrFunction, cfg: Cfg): Map<String, String> {
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

    private fun findNaturalLoops(fn: IrFunction, cfg: Cfg, domTree: Map<String, String>): List<NaturalLoop> {
        val loops = mutableListOf<NaturalLoop>()
        val domSets = computeDomSets(fn, cfg)

        // Find back edges: edge (a → b) where b dominates a
        for (block in fn.blocks) {
            val term = block.instructions.lastOrNull() ?: continue
            for (target in terminatorTargets(term)) {
                val domSet = domSets[block.label] ?: emptySet()
                if (target in domSet) {
                    // Back edge: block.label → target
                    val body = computeLoopBody(target, block.label, cfg)
                    loops.add(NaturalLoop(header = target, body = body))
                }
            }
        }
        return loops
    }

    private fun computeDomSets(fn: IrFunction, cfg: Cfg): Map<String, Set<String>> {
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
        return dom
    }

    private fun computeLoopBody(header: String, backEdgeSource: String, cfg: Cfg): Set<String> {
        // Natural loop body: all blocks that can reach backEdgeSource without going through header
        val body = mutableSetOf(header, backEdgeSource)
        val worklist = ArrayDeque<String>()
        if (backEdgeSource != header) {
            worklist.add(backEdgeSource)
        }
        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            for (pred in cfg.predecessors[block] ?: emptySet()) {
                if (pred !in body) {
                    body.add(pred)
                    worklist.add(pred)
                }
            }
        }
        return body
    }
}

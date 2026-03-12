package org.kgen.ir.verify

import org.kgen.ir.*
import org.kgen.ir.text.IrPrinter

/**
 * Validates the structural and type-level correctness of a [Module].
 *
 * Checks performed:
 * - No duplicate function/global/struct names
 * - External functions have no body; non-external functions have at least one block
 * - No duplicate block labels within a function
 * - Every block ends with a terminator, no terminators in the middle
 * - Phi nodes appear only at the start of blocks
 * - All branch targets reference existing blocks
 * - Integer operations have matching integer operand types
 * - Float operations have matching float operand types
 * - Memory operations use pointer types
 * - Return type matches function signature
 * - Call argument count and types match the callee signature
 * - Conversions use appropriate source/target type categories
 * - Vector operations use vector types
 * - SSA dominance: every use of a value is dominated by its definition
 * - Phi predecessor validation: incoming blocks must be actual predecessors
 * - Use-def validation: every referenced value must be defined
 *
 * ```kotlin
 * val result = IrVerifier.verify(module)
 * if (!result.isValid) {
 *     result.errors.forEach { println("ERROR: $it") }
 * }
 * ```
 */
class IrVerifier {
    private val errors = mutableListOf<VerificationError>()

    fun verify(module: Module): VerificationResult {
        errors.clear()
        verifyModule(module)
        return VerificationResult(errors.toList())
    }

    private fun verifyModule(module: Module) {
        val functionNames = mutableSetOf<String>()
        for (fn in module.functions) {
            if (!functionNames.add(fn.name)) {
                error("Duplicate function name: @${fn.name}")
            }
            verifyFunction(fn)
        }

        val globalNames = mutableSetOf<String>()
        for (g in module.globals) {
            if (!globalNames.add(g.name)) {
                error("Duplicate global name: @${g.name}")
            }
            if (g.initializer != null) {
                verifyConstantType(g.initializer, g.type, "global @${g.name}")
            }
        }

        val structNames = mutableSetOf<String>()
        for (s in module.structs) {
            if (!structNames.add(s.name)) {
                error("Duplicate struct name: %${s.name}")
            }
        }
    }

    private fun verifyFunction(fn: IrFunction) {
        val ctx = "function @${fn.name}"

        if (fn.isExternal && fn.blocks.isNotEmpty()) {
            error("External function @${fn.name} must not have a body")
        }
        if (!fn.isExternal && fn.blocks.isEmpty()) {
            error("Non-external function @${fn.name} must have at least one basic block")
        }

        if (fn.isExternal) return

        val definedValues = mutableSetOf<String>()
        val blockLabels = fn.blocks.map { it.label }.toSet()

        // Check for duplicate block labels
        val labelSet = mutableSetOf<String>()
        for (block in fn.blocks) {
            if (!labelSet.add(block.label)) {
                error("Duplicate block label %${block.label} in $ctx")
            }
        }

        // Add parameters as defined values
        for (param in fn.params) {
            definedValues.add(param.name)
        }

        // First pass: collect all defined values (for phi forward references)
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val result = inst.result
                if (result is InstructionRef) {
                    if (!definedValues.add(result.name)) {
                        error("Duplicate value definition ${result.name} in $ctx")
                    }
                }
            }
        }

        // Second pass: verify instructions
        for (block in fn.blocks) {
            verifyBlock(block, fn, blockLabels, definedValues)
        }

        // Check terminators
        for (block in fn.blocks) {
            if (block.instructions.isEmpty()) {
                error("Block %${block.label} in $ctx is empty (no terminator)")
                continue
            }
            val last = block.instructions.last()
            if (!isTerminator(last)) {
                error("Block %${block.label} in $ctx does not end with a terminator")
            }
            // Check no terminator in the middle
            for (i in 0 until block.instructions.size - 1) {
                if (isTerminator(block.instructions[i])) {
                    error("Terminator in middle of block %${block.label} in $ctx at position $i")
                }
            }
        }

        // Entry block must not be a branch target (no predecessors)
        if (fn.blocks.isNotEmpty()) {
            val entryLabel = fn.blocks[0].label
            val allTargets = mutableSetOf<String>()
            for (block in fn.blocks) {
                if (block.instructions.isNotEmpty()) {
                    allTargets += terminatorTargets(block.instructions.last())
                }
            }
            if (entryLabel in allTargets) {
                error("Entry block %$entryLabel in $ctx has predecessors (must not be a branch target)")
            }
        }

        // Invoke unwind destination must begin with LandingPad
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is Instruction.Invoke) {
                    val unwindBlock = fn.blocks.find { it.label == inst.unwindDest }
                    if (unwindBlock != null && unwindBlock.instructions.isNotEmpty()) {
                        val firstNonDebug = unwindBlock.instructions.firstOrNull {
                            it !is Instruction.DebugLoc && it !is Instruction.DebugValue && it !is Instruction.DebugDeclare
                        }
                        if (firstNonDebug != null && firstNonDebug !is Instruction.LandingPad) {
                            error("Invoke unwind destination %${inst.unwindDest} in $ctx must begin with LandingPad")
                        }
                    }
                }
            }
        }

        // Functions with Invoke or LandingPad require a personality function
        val hasInvoke = fn.blocks.any { b -> b.instructions.any { it is Instruction.Invoke } }
        val hasLandingPad = fn.blocks.any { b -> b.instructions.any { it is Instruction.LandingPad } }
        val hasSeh = fn.blocks.any { b -> b.instructions.any { it is Instruction.CatchSwitch || it is Instruction.CatchPad || it is Instruction.CleanupPad } }
        if ((hasInvoke || hasLandingPad || hasSeh) && fn.personality == null) {
            error("Function $ctx uses exception handling but has no personality function")
        }

        // GCRoot and InteriorPtr require a gc strategy on the function
        if (fn.gc == null) {
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst is Instruction.GCRoot) {
                        error("GCRoot in $ctx requires a gc strategy (fn.gc must be set)")
                    }
                    if (inst is Instruction.InteriorPtr) {
                        error("InteriorPtr in $ctx requires a gc strategy (fn.gc must be set)")
                    }
                }
            }
        }

        // Coroutine ordering: CoroEnd/CoroSuspend/CoroResume/CoroDestroy/CoroSize must be dominated by CoroBegin
        val hasCoroBegin = fn.blocks.any { b -> b.instructions.any { it is Instruction.CoroBegin } }
        val hasOtherCoro = fn.blocks.any { b -> b.instructions.any {
            it is Instruction.CoroEnd || it is Instruction.CoroSuspend ||
            it is Instruction.CoroResume || it is Instruction.CoroDestroy || it is Instruction.CoroSize
        }}
        if (!hasCoroBegin && hasOtherCoro) {
            error("Coroutine instructions in $ctx require a CoroBegin")
        }
        if (hasCoroBegin && hasOtherCoro && fn.blocks.isNotEmpty() && blockLabels.size == fn.blocks.size &&
            fn.blocks.all { it.instructions.isNotEmpty() && isTerminator(it.instructions.last()) }) {
            val idom = computeImmediateDominators(fn)
            val coroBeginBlock = fn.blocks.first { b -> b.instructions.any { it is Instruction.CoroBegin } }.label
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    val isCoroDep = inst is Instruction.CoroEnd || inst is Instruction.CoroSuspend ||
                        inst is Instruction.CoroResume || inst is Instruction.CoroDestroy || inst is Instruction.CoroSize
                    if (isCoroDep && !dominates(coroBeginBlock, block.label, idom)) {
                        error("${inst::class.simpleName} in block %${block.label} of $ctx is not dominated by CoroBegin")
                    }
                }
            }
        }

        // SSA dominance and use-def checks (only if no structural errors so far that would break CFG analysis)
        if (fn.blocks.isNotEmpty() && blockLabels.size == fn.blocks.size && fn.blocks.all { it.instructions.isNotEmpty() && isTerminator(it.instructions.last()) }) {
            verifySsaDominance(fn)
            verifyPhiPredecessors(fn)
        }
    }

    private fun verifyBlock(block: BasicBlock, fn: IrFunction, blockLabels: Set<String>, definedValues: Set<String>) {
        val ctx = "block %${block.label} of @${fn.name}"

        for (inst in block.instructions) {
            verifyInstruction(inst, ctx, blockLabels, definedValues, fn)
        }

        // Phi nodes must be at the start
        var seenNonPhi = false
        for (inst in block.instructions) {
            if (inst is Instruction.Phi) {
                if (seenNonPhi) {
                    error("Phi node after non-phi instruction in $ctx")
                }
            } else if (inst !is Instruction.DebugLoc && inst !is Instruction.DebugValue && inst !is Instruction.DebugDeclare) {
                seenNonPhi = true
            }
        }
    }

    // --- SSA Dominance ---

    private fun buildCfg(fn: IrFunction): Map<String, Set<String>> {
        val successors = mutableMapOf<String, MutableSet<String>>()
        for (block in fn.blocks) {
            successors[block.label] = mutableSetOf()
        }
        for (block in fn.blocks) {
            for (target in terminatorTargets(block.instructions.last())) {
                successors[block.label]?.add(target)
            }
        }
        return successors
    }

    private fun buildPredecessors(fn: IrFunction): Map<String, Set<String>> {
        val preds = mutableMapOf<String, MutableSet<String>>()
        for (block in fn.blocks) {
            preds[block.label] = mutableSetOf()
        }
        for (block in fn.blocks) {
            for (target in terminatorTargets(block.instructions.last())) {
                preds[target]?.add(block.label)
            }
        }
        return preds
    }

    private fun terminatorTargets(inst: Instruction): List<String> = when (inst) {
        is Instruction.Br -> listOf(inst.target)
        is Instruction.CondBr -> listOf(inst.trueTarget, inst.falseTarget)
        is Instruction.Switch -> listOf(inst.defaultTarget) + inst.cases.map { it.second }
        is Instruction.IndirectBr -> inst.targets
        is Instruction.Invoke -> listOf(inst.normalDest, inst.unwindDest)
        is Instruction.CallBr -> listOf(inst.fallthrough) + inst.indirectDests
        is Instruction.CatchSwitch -> inst.handlers + listOfNotNull(inst.unwindDest)
        is Instruction.CatchRet -> listOf(inst.dest)
        is Instruction.CleanupRet -> listOfNotNull(inst.unwindDest)
        is Instruction.TagSwitch -> inst.cases.map { it.second } + listOfNotNull(inst.defaultTarget)
        is Instruction.DebugTrap -> listOfNotNull(inst.successor)
        else -> emptyList()
    }

    /**
     * Compute immediate dominators using the iterative dataflow algorithm.
     * Entry block (first block) dominates all reachable blocks.
     * Returns a map from block label to its immediate dominator label (entry maps to itself).
     */
    private fun computeImmediateDominators(fn: IrFunction): Map<String, String> {
        val blocks = fn.blocks.map { it.label }
        val entry = blocks.first()
        val preds = buildPredecessors(fn)

        // dom[b] = set of blocks that dominate b
        val dom = mutableMapOf<String, MutableSet<String>>()
        dom[entry] = mutableSetOf(entry)
        for (b in blocks) {
            if (b != entry) dom[b] = blocks.toMutableSet()
        }

        var changed = true
        while (changed) {
            changed = false
            for (b in blocks) {
                if (b == entry) continue
                val predSet = preds[b] ?: emptySet()
                if (predSet.isEmpty()) continue
                val newDom = predSet
                    .map { dom[it] ?: emptySet() }
                    .reduce { acc, s -> acc.intersect(s).toMutableSet() }
                    .toMutableSet()
                newDom.add(b)
                if (newDom != dom[b]) {
                    dom[b] = newDom
                    changed = true
                }
            }
        }

        // Extract immediate dominators from dominator sets
        val idom = mutableMapOf<String, String>()
        idom[entry] = entry
        for (b in blocks) {
            if (b == entry) continue
            val dominators = dom[b] ?: continue
            // idom(b) is the dominator of b that is dominated by all other dominators of b (except b itself)
            val strictDoms = dominators - b
            if (strictDoms.isEmpty()) continue
            // idom is the one whose dominator set is largest (closest to b in the tree)
            idom[b] = strictDoms.maxByOrNull { dom[it]?.size ?: 0 } ?: entry
        }
        return idom
    }

    /**
     * Check that a block `user` is dominated by block `definer`.
     * A block dominates itself.
     */
    private fun dominates(defBlock: String, useBlock: String, idom: Map<String, String>): Boolean {
        if (defBlock == useBlock) return true
        var b = useBlock
        while (true) {
            val parent = idom[b] ?: return false
            if (parent == defBlock) return true
            if (parent == b) return false // reached entry without finding defBlock
            b = parent
        }
    }

    private fun verifySsaDominance(fn: IrFunction) {
        val ctx = "function @${fn.name}"
        val idom = computeImmediateDominators(fn)

        // Map each value name to the block where it's defined
        val valueDef = mutableMapOf<String, String>()
        // Parameters are available everywhere (dominate all blocks from entry)
        for (param in fn.params) {
            valueDef[param.name] = fn.blocks.first().label
        }
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val result = inst.result
                if (result is InstructionRef) {
                    valueDef[result.name] = block.label
                }
            }
        }

        // Check each instruction's operands
        for (block in fn.blocks) {
            // Track which instruction results have been seen so far in this block
            // (for same-block dominance: def must come before use)
            val seenInBlock = mutableSetOf<String>()
            // Params are always available
            fn.params.forEach { seenInBlock.add(it.name) }

            for (inst in block.instructions) {
                // For phi nodes, the values come from predecessor blocks, not the current block
                // So we check phi incoming values differently
                if (inst is Instruction.Phi) {
                    for ((value, predLabel) in inst.incoming) {
                        checkValueDominance(value, predLabel, valueDef, idom, fn, "phi in block %${block.label} of $ctx (from %$predLabel)")
                    }
                } else {
                    for (operand in instructionOperands(inst)) {
                        if (operand is InstructionRef) {
                            val defBlock = valueDef[operand.name]
                            if (defBlock == null) {
                                error("Use of undefined value ${operand.name} in block %${block.label} of $ctx")
                            } else if (defBlock == block.label) {
                                // Same block: must have been defined earlier
                                if (operand.name !in seenInBlock) {
                                    error("Value ${operand.name} used before definition in block %${block.label} of $ctx")
                                }
                            } else {
                                // Different block: def block must dominate use block
                                if (!dominates(defBlock, block.label, idom)) {
                                    error("Value ${operand.name} (defined in %$defBlock) does not dominate use in block %${block.label} of $ctx")
                                }
                            }
                        }
                    }
                }

                // After this instruction, its result is available in the block
                val result = inst.result
                if (result is InstructionRef) {
                    seenInBlock.add(result.name)
                }
            }
        }
    }

    private fun checkValueDominance(value: Value, useBlock: String, valueDef: Map<String, String>, idom: Map<String, String>, fn: IrFunction, ctx: String) {
        if (value is InstructionRef) {
            val defBlock = valueDef[value.name]
            if (defBlock == null) {
                error("Use of undefined value ${value.name} in $ctx")
            } else if (!dominates(defBlock, useBlock, idom)) {
                error("Value ${value.name} (defined in %$defBlock) does not dominate use in $ctx")
            }
        }
    }

    private fun verifyPhiPredecessors(fn: IrFunction) {
        val preds = buildPredecessors(fn)
        val ctx = "function @${fn.name}"

        for (block in fn.blocks) {
            val blockPreds = preds[block.label] ?: emptySet()
            for (inst in block.instructions) {
                if (inst !is Instruction.Phi) {
                    if (inst !is Instruction.DebugLoc && inst !is Instruction.DebugValue && inst !is Instruction.DebugDeclare) break
                    continue
                }
                val incomingLabels = inst.incoming.map { it.second }

                // Each incoming block must be an actual predecessor
                for (label in incomingLabels) {
                    if (label !in blockPreds) {
                        error("Phi in block %${block.label} of $ctx has incoming from %$label which is not a predecessor")
                    }
                }

                // Each predecessor must have exactly one entry
                val labelCounts = incomingLabels.groupingBy { it }.eachCount()
                for ((label, count) in labelCounts) {
                    if (count > 1) {
                        error("Phi in block %${block.label} of $ctx has $count entries for predecessor %$label (expected 1)")
                    }
                }

                // Each predecessor should be represented
                for (pred in blockPreds) {
                    if (pred !in incomingLabels) {
                        error("Phi in block %${block.label} of $ctx is missing entry for predecessor %$pred")
                    }
                }
            }
        }
    }

    /**
     * Extract all Value operands from an instruction (excluding the result).
     * Used for use-def and dominance checking.
     */
    private fun instructionOperands(inst: Instruction): List<Value> = when (inst) {
        is Instruction.Add -> listOf(inst.lhs, inst.rhs)
        is Instruction.Sub -> listOf(inst.lhs, inst.rhs)
        is Instruction.Mul -> listOf(inst.lhs, inst.rhs)
        is Instruction.UDiv -> listOf(inst.lhs, inst.rhs)
        is Instruction.SDiv -> listOf(inst.lhs, inst.rhs)
        is Instruction.URem -> listOf(inst.lhs, inst.rhs)
        is Instruction.SRem -> listOf(inst.lhs, inst.rhs)
        is Instruction.Neg -> listOf(inst.operand)
        is Instruction.SAddOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.UAddOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.SSubOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.USubOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.SMulOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.UMulOverflow -> listOf(inst.lhs, inst.rhs)
        is Instruction.SAddSat -> listOf(inst.lhs, inst.rhs)
        is Instruction.UAddSat -> listOf(inst.lhs, inst.rhs)
        is Instruction.SSubSat -> listOf(inst.lhs, inst.rhs)
        is Instruction.USubSat -> listOf(inst.lhs, inst.rhs)
        is Instruction.SMin -> listOf(inst.lhs, inst.rhs)
        is Instruction.SMax -> listOf(inst.lhs, inst.rhs)
        is Instruction.UMin -> listOf(inst.lhs, inst.rhs)
        is Instruction.UMax -> listOf(inst.lhs, inst.rhs)
        is Instruction.Abs -> listOf(inst.operand)
        is Instruction.FAdd -> listOf(inst.lhs, inst.rhs)
        is Instruction.FSub -> listOf(inst.lhs, inst.rhs)
        is Instruction.FMul -> listOf(inst.lhs, inst.rhs)
        is Instruction.FDiv -> listOf(inst.lhs, inst.rhs)
        is Instruction.FRem -> listOf(inst.lhs, inst.rhs)
        is Instruction.FNeg -> listOf(inst.operand)
        is Instruction.FAbs -> listOf(inst.operand)
        is Instruction.FMA -> listOf(inst.a, inst.b, inst.c)
        is Instruction.FMin -> listOf(inst.lhs, inst.rhs)
        is Instruction.FMax -> listOf(inst.lhs, inst.rhs)
        is Instruction.Sqrt -> listOf(inst.operand)
        is Instruction.Ceil -> listOf(inst.operand)
        is Instruction.Floor -> listOf(inst.operand)
        is Instruction.Round -> listOf(inst.operand)
        is Instruction.Trunc -> listOf(inst.operand)
        is Instruction.CopySign -> listOf(inst.magnitude, inst.sign)
        is Instruction.And -> listOf(inst.lhs, inst.rhs)
        is Instruction.Or -> listOf(inst.lhs, inst.rhs)
        is Instruction.Xor -> listOf(inst.lhs, inst.rhs)
        is Instruction.Not -> listOf(inst.operand)
        is Instruction.Shl -> listOf(inst.lhs, inst.rhs)
        is Instruction.LShr -> listOf(inst.lhs, inst.rhs)
        is Instruction.AShr -> listOf(inst.lhs, inst.rhs)
        is Instruction.RotateLeft -> listOf(inst.value, inst.amount)
        is Instruction.RotateRight -> listOf(inst.value, inst.amount)
        is Instruction.Rotl -> listOf(inst.value, inst.amount)
        is Instruction.Rotr -> listOf(inst.value, inst.amount)
        is Instruction.Ctlz -> listOf(inst.operand)
        is Instruction.Cttz -> listOf(inst.operand)
        is Instruction.Ctpop -> listOf(inst.operand)
        is Instruction.BSwap -> listOf(inst.operand)
        is Instruction.BitReverse -> listOf(inst.operand)
        is Instruction.ICmp -> listOf(inst.lhs, inst.rhs)
        is Instruction.FCmp -> listOf(inst.lhs, inst.rhs)
        is Instruction.Alloca -> listOfNotNull(inst.numElements)
        is Instruction.Load -> listOf(inst.ptr)
        is Instruction.Store -> listOf(inst.value, inst.ptr)
        is Instruction.GetElementPtr -> listOf(inst.ptr) + inst.indices
        is Instruction.Fence -> emptyList()
        is Instruction.CmpXchg -> listOf(inst.ptr, inst.cmp, inst.new)
        is Instruction.AtomicRMW -> listOf(inst.ptr, inst.value)
        is Instruction.MemCpy -> listOf(inst.dst, inst.src, inst.len)
        is Instruction.MemSet -> listOf(inst.dst, inst.value, inst.len)
        is Instruction.MemMove -> listOf(inst.dst, inst.src, inst.len)
        is Instruction.Prefetch -> listOf(inst.address)
        is Instruction.StackSave -> emptyList()
        is Instruction.StackRestore -> listOf(inst.ptr)
        is Instruction.LifetimeStart -> listOf(inst.ptr)
        is Instruction.LifetimeEnd -> listOf(inst.ptr)
        is Instruction.IntTrunc -> listOf(inst.value)
        is Instruction.ZExt -> listOf(inst.value)
        is Instruction.SExt -> listOf(inst.value)
        is Instruction.FPTrunc -> listOf(inst.value)
        is Instruction.FPExt -> listOf(inst.value)
        is Instruction.FPToUI -> listOf(inst.value)
        is Instruction.FPToSI -> listOf(inst.value)
        is Instruction.UIToFP -> listOf(inst.value)
        is Instruction.SIToFP -> listOf(inst.value)
        is Instruction.PtrToInt -> listOf(inst.value)
        is Instruction.IntToPtr -> listOf(inst.value)
        is Instruction.BitCast -> listOf(inst.value)
        is Instruction.AddrSpaceCast -> listOf(inst.value)
        is Instruction.Ret -> listOfNotNull(inst.value)
        is Instruction.Br -> emptyList()
        is Instruction.CondBr -> listOf(inst.condition)
        is Instruction.Switch -> listOf(inst.value)
        is Instruction.IndirectBr -> listOf(inst.address)
        is Instruction.Unreachable -> emptyList()
        is Instruction.Trap -> emptyList()
        is Instruction.DebugTrap -> emptyList()
        is Instruction.Call -> listOf(inst.function) + inst.args
        is Instruction.Invoke -> listOf(inst.function) + inst.args
        is Instruction.CallBr -> listOf(inst.function) + inst.args
        is Instruction.VAStart -> listOf(inst.argList)
        is Instruction.VAEnd -> listOf(inst.argList)
        is Instruction.VACopy -> listOf(inst.dst, inst.src)
        is Instruction.VAArg -> listOf(inst.argList)
        is Instruction.LandingPad -> emptyList()
        is Instruction.Resume -> listOf(inst.value)
        is Instruction.CatchSwitch -> listOfNotNull(inst.parentPad) + inst.args()
        is Instruction.CatchPad -> listOf(inst.catchSwitch) + inst.args
        is Instruction.CleanupPad -> listOfNotNull(inst.parentPad) + inst.args
        is Instruction.CatchRet -> listOf(inst.catchPad)
        is Instruction.CleanupRet -> listOf(inst.cleanupPad)
        is Instruction.Phi -> emptyList() // phi operands handled separately in dominance check
        is Instruction.Select -> listOf(inst.condition, inst.trueValue, inst.falseValue)
        is Instruction.Freeze -> listOf(inst.value)
        is Instruction.ExtractElement -> listOf(inst.vector, inst.index)
        is Instruction.InsertElement -> listOf(inst.vector, inst.element, inst.index)
        is Instruction.ShuffleVector -> listOf(inst.v1, inst.v2)
        is Instruction.Splat -> listOf(inst.scalar)
        is Instruction.VectorReduce -> listOf(inst.vector)
        is Instruction.ExtractValue -> listOf(inst.aggregate)
        is Instruction.InsertValue -> listOf(inst.aggregate, inst.element)
        is Instruction.NewObject -> emptyList()
        is Instruction.NewArray -> listOf(inst.size)
        is Instruction.NewMultiArray -> inst.dimensions
        is Instruction.GetField -> listOf(inst.obj)
        is Instruction.PutField -> listOf(inst.obj, inst.value)
        is Instruction.GetStatic -> emptyList()
        is Instruction.PutStatic -> listOf(inst.value)
        is Instruction.VirtualCall -> listOf(inst.obj) + inst.args
        is Instruction.InterfaceCall -> listOf(inst.obj) + inst.args
        is Instruction.SpecialCall -> listOf(inst.obj) + inst.args
        is Instruction.StaticCall -> inst.args
        is Instruction.DynamicCall -> inst.args
        is Instruction.ConstructorCall -> listOf(inst.obj) + inst.args
        is Instruction.InstanceOf -> listOf(inst.obj)
        is Instruction.CheckCast -> listOf(inst.obj)
        is Instruction.TypeId -> listOf(inst.obj)
        is Instruction.ArrayGet -> listOf(inst.array, inst.index)
        is Instruction.ArraySet -> listOf(inst.array, inst.index, inst.value)
        is Instruction.ArrayLength -> listOf(inst.array)
        is Instruction.MonitorEnter -> listOf(inst.obj)
        is Instruction.MonitorExit -> listOf(inst.obj)
        is Instruction.Throw -> listOf(inst.exception)
        is Instruction.TryCatchRegion -> emptyList()
        is Instruction.Box -> listOf(inst.value)
        is Instruction.Unbox -> listOf(inst.obj)
        is Instruction.CatchValue -> emptyList()
        is Instruction.MakeWeakRef -> listOf(inst.obj)
        is Instruction.ReadWeakRef -> listOf(inst.weakRef)
        is Instruction.ClearWeakRef -> listOf(inst.weakRef)
        is Instruction.ClosureCreate -> listOf(inst.function) + inst.captures
        is Instruction.ClosureInvoke -> listOf(inst.closure) + inst.args
        is Instruction.ConstructVariant -> inst.fields
        is Instruction.GetTag -> listOf(inst.union)
        is Instruction.GetVariantField -> listOf(inst.union)
        is Instruction.TagSwitch -> listOf(inst.union)
        is Instruction.GCAlloc -> listOfNotNull(inst.size)
        is Instruction.GCSafepoint -> emptyList()
        is Instruction.GCRoot -> listOfNotNull(inst.ptr, inst.metadata)
        is Instruction.Pin -> listOf(inst.ref)
        is Instruction.Unpin -> listOf(inst.ref)
        is Instruction.InteriorPtr -> listOf(inst.ref, inst.index)
        is Instruction.WriteBarrier -> listOf(inst.obj, inst.fieldIndex, inst.value)
        is Instruction.ReadBarrier -> listOf(inst.ref)
        is Instruction.ManagedCall -> inst.args + inst.function
        is Instruction.RefRetain -> listOf(inst.obj)
        is Instruction.RefRelease -> listOf(inst.obj)
        is Instruction.RefCount -> listOf(inst.obj)
        is Instruction.CoroBegin -> listOf(inst.id, inst.mem)
        is Instruction.CoroEnd -> listOf(inst.handle)
        is Instruction.CoroSuspend -> listOfNotNull(inst.save)
        is Instruction.CoroResume -> listOf(inst.handle)
        is Instruction.CoroDestroy -> listOf(inst.handle)
        is Instruction.CoroSize -> emptyList()
        is Instruction.Intrinsic -> inst.args
        is Instruction.InlineAsm -> inst.args
        is Instruction.DebugLoc -> emptyList()
        is Instruction.DebugValue -> listOf(inst.value)
        is Instruction.DebugDeclare -> listOf(inst.address)
        is Instruction.Assume -> listOf(inst.condition)
        is Instruction.Expect -> listOf(inst.value)
    }

    private fun Instruction.CatchSwitch.args(): List<Value> = emptyList()

    // --- Type checking helpers ---

    private fun verifyInstruction(inst: Instruction, ctx: String, blockLabels: Set<String>, definedValues: Set<String>, fn: IrFunction) {
        when (inst) {
            // Integer binary ops: operands must have same type, must be integer
            is Instruction.Add -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "add")
            is Instruction.Sub -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "sub")
            is Instruction.Mul -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "mul")
            is Instruction.UDiv -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "udiv")
            is Instruction.SDiv -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "sdiv")
            is Instruction.URem -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "urem")
            is Instruction.SRem -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "srem")
            is Instruction.SAddOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "sadd.overflow")
            is Instruction.UAddOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "uadd.overflow")
            is Instruction.SSubOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "ssub.overflow")
            is Instruction.USubOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "usub.overflow")
            is Instruction.SMulOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "smul.overflow")
            is Instruction.UMulOverflow -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "umul.overflow")
            is Instruction.SAddSat -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "sadd.sat")
            is Instruction.UAddSat -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "uadd.sat")
            is Instruction.SSubSat -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "ssub.sat")
            is Instruction.USubSat -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "usub.sat")
            is Instruction.SMin -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "smin")
            is Instruction.SMax -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "smax")
            is Instruction.UMin -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "umin")
            is Instruction.UMax -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "umax")

            // Float binary ops
            is Instruction.FAdd -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fadd")
            is Instruction.FSub -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fsub")
            is Instruction.FMul -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fmul")
            is Instruction.FDiv -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fdiv")
            is Instruction.FRem -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "frem")
            is Instruction.FMin -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fmin")
            is Instruction.FMax -> verifyFloatBinOp(inst.lhs, inst.rhs, ctx, "fmax")
            is Instruction.CopySign -> verifyFloatBinOp(inst.magnitude, inst.sign, ctx, "copysign")

            // Float unary: operand must be float
            is Instruction.FNeg -> verifyFloatUnary(inst.operand, ctx, "fneg")
            is Instruction.FAbs -> verifyFloatUnary(inst.operand, ctx, "fabs")
            is Instruction.Sqrt -> verifyFloatUnary(inst.operand, ctx, "sqrt")
            is Instruction.Ceil -> verifyFloatUnary(inst.operand, ctx, "ceil")
            is Instruction.Floor -> verifyFloatUnary(inst.operand, ctx, "floor")
            is Instruction.Round -> verifyFloatUnary(inst.operand, ctx, "round")
            is Instruction.Trunc -> verifyFloatUnary(inst.operand, ctx, "trunc")

            // FMA: all three operands must be same float type
            is Instruction.FMA -> {
                if (inst.a.type != inst.b.type || inst.b.type != inst.c.type) {
                    error("fma operands must all be same type in $ctx")
                }
                verifyFloatUnary(inst.a, ctx, "fma")
            }

            // Bitwise: operands must be integer
            is Instruction.And -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "and")
            is Instruction.Or -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "or")
            is Instruction.Xor -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "xor")
            is Instruction.Shl -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "shl")
            is Instruction.LShr -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "lshr")
            is Instruction.AShr -> verifyIntBinOp(inst.lhs, inst.rhs, ctx, "ashr")
            is Instruction.RotateLeft -> verifyIntBinOp(inst.value, inst.amount, ctx, "rotl")
            is Instruction.RotateRight -> verifyIntBinOp(inst.value, inst.amount, ctx, "rotr")
            is Instruction.Rotl -> verifyIntBinOp(inst.value, inst.amount, ctx, "rotl")
            is Instruction.Rotr -> verifyIntBinOp(inst.value, inst.amount, ctx, "rotr")

            // Bit manipulation: operand must be integer
            is Instruction.Not -> verifyIntUnary(inst.operand, ctx, "not")
            is Instruction.Neg -> verifyIntUnary(inst.operand, ctx, "neg")
            is Instruction.Abs -> verifyIntUnary(inst.operand, ctx, "abs")
            is Instruction.Ctlz -> verifyIntUnary(inst.operand, ctx, "ctlz")
            is Instruction.Cttz -> verifyIntUnary(inst.operand, ctx, "cttz")
            is Instruction.Ctpop -> verifyIntUnary(inst.operand, ctx, "ctpop")
            is Instruction.BSwap -> verifyIntUnary(inst.operand, ctx, "bswap")
            is Instruction.BitReverse -> verifyIntUnary(inst.operand, ctx, "bitreverse")

            // Comparison
            is Instruction.ICmp -> {
                if (inst.lhs.type != inst.rhs.type) {
                    error("icmp operands have different types: ${IrPrinter.typeStr(inst.lhs.type)} vs ${IrPrinter.typeStr(inst.rhs.type)} in $ctx")
                }
                if (inst.dest.type != Type.I1) {
                    error("icmp result must be i1 in $ctx")
                }
            }
            is Instruction.FCmp -> {
                if (inst.lhs.type != inst.rhs.type) {
                    error("fcmp operands have different types in $ctx")
                }
                if (!isFloatType(inst.lhs.type)) {
                    error("fcmp operands must be float type in $ctx")
                }
                if (inst.dest.type != Type.I1) {
                    error("fcmp result must be i1 in $ctx")
                }
            }

            // Memory
            is Instruction.Alloca -> {
                if (inst.numElements != null && !isIntegerType(inst.numElements.type)) {
                    error("alloca numElements must be integer type in $ctx")
                }
            }
            is Instruction.Load -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("load ptr operand must be pointer type in $ctx, got ${IrPrinter.typeStr(inst.ptr.type)}")
                }
                if (inst.dest.type != inst.loadType) {
                    error("load result type ${IrPrinter.typeStr(inst.dest.type)} doesn't match load type ${IrPrinter.typeStr(inst.loadType)} in $ctx")
                }
            }
            is Instruction.Store -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("store ptr operand must be pointer type in $ctx")
                }
            }
            is Instruction.CmpXchg -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("cmpxchg ptr operand must be pointer type in $ctx")
                }
                if (inst.cmp.type != inst.new.type) {
                    error("cmpxchg compare and new values must have same type in $ctx")
                }
            }
            is Instruction.AtomicRMW -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("atomicrmw ptr operand must be pointer type in $ctx")
                }
            }
            is Instruction.MemCpy -> {
                if (!isPointerType(inst.dst.type) || !isPointerType(inst.src.type)) {
                    error("memcpy dst and src must be pointer types in $ctx")
                }
            }
            is Instruction.MemSet -> {
                if (!isPointerType(inst.dst.type)) {
                    error("memset dst must be pointer type in $ctx")
                }
            }
            is Instruction.MemMove -> {
                if (!isPointerType(inst.dst.type) || !isPointerType(inst.src.type)) {
                    error("memmove dst and src must be pointer types in $ctx")
                }
            }

            // Branch targets
            is Instruction.Br -> verifyBlockRef(inst.target, blockLabels, ctx, "br")
            is Instruction.CondBr -> {
                if (inst.condition.type != Type.I1) {
                    error("condbr condition must be i1 in $ctx")
                }
                verifyBlockRef(inst.trueTarget, blockLabels, ctx, "condbr true")
                verifyBlockRef(inst.falseTarget, blockLabels, ctx, "condbr false")
            }
            is Instruction.Switch -> {
                verifyBlockRef(inst.defaultTarget, blockLabels, ctx, "switch default")
                for ((c, target) in inst.cases) {
                    verifyBlockRef(target, blockLabels, ctx, "switch case")
                    if (c.type != inst.value.type) {
                        error("switch case type ${IrPrinter.typeStr(c.type)} doesn't match value type ${IrPrinter.typeStr(inst.value.type)} in $ctx")
                    }
                }
            }
            is Instruction.IndirectBr -> {
                if (!isPointerType(inst.address.type)) {
                    error("indirectbr address must be pointer type in $ctx")
                }
                for (target in inst.targets) {
                    verifyBlockRef(target, blockLabels, ctx, "indirectbr")
                }
            }

            // Ret
            is Instruction.Ret -> {
                if (fn.returnType == Type.Void && inst.value != null) {
                    error("ret with value in void function @${fn.name}")
                }
                if (fn.returnType != Type.Void && inst.value == null) {
                    error("ret without value in non-void function @${fn.name}")
                }
                if (inst.value != null && inst.value.type != fn.returnType) {
                    error("ret type ${IrPrinter.typeStr(inst.value.type)} doesn't match function return type ${IrPrinter.typeStr(fn.returnType)} in @${fn.name}")
                }
            }

            // Call: verify arg count and types
            is Instruction.Call -> verifyCallArgs(inst.function, inst.args, ctx, "call")

            // Invoke: arg types + block refs
            is Instruction.Invoke -> {
                verifyBlockRef(inst.normalDest, blockLabels, ctx, "invoke normal")
                verifyBlockRef(inst.unwindDest, blockLabels, ctx, "invoke unwind")
                verifyCallArgs(inst.function, inst.args, ctx, "invoke")
            }

            // CallBr: arg types + block refs
            is Instruction.CallBr -> {
                verifyBlockRef(inst.fallthrough, blockLabels, ctx, "callbr fallthrough")
                for (dest in inst.indirectDests) {
                    verifyBlockRef(dest, blockLabels, ctx, "callbr indirect")
                }
                verifyCallArgs(inst.function, inst.args, ctx, "callbr")
            }

            // Phi
            is Instruction.Phi -> {
                if (inst.incoming.isEmpty()) {
                    error("phi must have at least one incoming value in $ctx")
                }
                for ((value, label) in inst.incoming) {
                    verifyBlockRef(label, blockLabels, ctx, "phi")
                    if (value.type != inst.dest.type) {
                        error("phi incoming value type ${IrPrinter.typeStr(value.type)} doesn't match phi type ${IrPrinter.typeStr(inst.dest.type)} from %$label in $ctx")
                    }
                }
            }

            // Conversions: check type categories make sense
            is Instruction.IntTrunc -> verifyIntConversion(inst.value.type, inst.toType, ctx, "inttrunc")
            is Instruction.ZExt -> verifyIntConversion(inst.value.type, inst.toType, ctx, "zext")
            is Instruction.SExt -> verifyIntConversion(inst.value.type, inst.toType, ctx, "sext")
            is Instruction.FPTrunc -> verifyFloatConversion(inst.value.type, inst.toType, ctx, "fptrunc")
            is Instruction.FPExt -> verifyFloatConversion(inst.value.type, inst.toType, ctx, "fpext")
            is Instruction.FPToUI -> {
                if (!isFloatType(inst.value.type)) error("fptoui source must be float type in $ctx")
                if (!isIntegerType(inst.toType)) error("fptoui target must be integer type in $ctx")
            }
            is Instruction.FPToSI -> {
                if (!isFloatType(inst.value.type)) error("fptosi source must be float type in $ctx")
                if (!isIntegerType(inst.toType)) error("fptosi target must be integer type in $ctx")
            }
            is Instruction.UIToFP -> {
                if (!isIntegerType(inst.value.type)) error("uitofp source must be integer type in $ctx")
                if (!isFloatType(inst.toType)) error("uitofp target must be float type in $ctx")
            }
            is Instruction.SIToFP -> {
                if (!isIntegerType(inst.value.type)) error("sitofp source must be integer type in $ctx")
                if (!isFloatType(inst.toType)) error("sitofp target must be float type in $ctx")
            }
            is Instruction.PtrToInt -> {
                if (!isPointerType(inst.value.type)) error("ptrtoint source must be pointer type in $ctx")
                if (!isIntegerType(inst.toType)) error("ptrtoint target must be integer type in $ctx")
            }
            is Instruction.IntToPtr -> {
                if (!isIntegerType(inst.value.type)) error("inttoptr source must be integer type in $ctx")
                if (!isPointerType(inst.toType)) error("inttoptr target must be pointer type in $ctx")
            }

            // Vector: verify index types and vector types
            is Instruction.ExtractElement -> {
                if (inst.vector.type !is Type.Vector) error("extractelement operand must be vector type in $ctx")
            }
            is Instruction.InsertElement -> {
                if (inst.vector.type !is Type.Vector) error("insertelement operand must be vector type in $ctx")
            }
            is Instruction.ShuffleVector -> {
                if (inst.v1.type !is Type.Vector) error("shufflevector operand must be vector type in $ctx")
                if (inst.v1.type != inst.v2.type) error("shufflevector operands must have same type in $ctx")
            }
            is Instruction.Splat -> {
                if (inst.scalar.type != inst.vectorType.element) {
                    error("splat scalar type must match vector element type in $ctx")
                }
            }
            is Instruction.VectorReduce -> {
                if (inst.vector.type !is Type.Vector) error("vector.reduce operand must be vector type in $ctx")
            }

            // EH block refs
            is Instruction.CatchSwitch -> {
                for (handler in inst.handlers) verifyBlockRef(handler, blockLabels, ctx, "catchswitch handler")
                inst.unwindDest?.let { verifyBlockRef(it, blockLabels, ctx, "catchswitch unwind") }
            }
            is Instruction.CatchRet -> verifyBlockRef(inst.dest, blockLabels, ctx, "catchret")
            is Instruction.CleanupRet -> {
                inst.unwindDest?.let { verifyBlockRef(it, blockLabels, ctx, "cleanupret unwind") }
            }

            // High-level: TryCatchRegion block refs
            is Instruction.TryCatchRegion -> {
                verifyBlockRef(inst.tryBlock, blockLabels, ctx, "trycatch try")
                for (handler in inst.catches) {
                    verifyBlockRef(handler.handlerBlock, blockLabels, ctx, "trycatch handler")
                }
                inst.finallyBlock?.let { verifyBlockRef(it, blockLabels, ctx, "trycatch finally") }
            }

            // High-level: TagSwitch block refs + exhaustiveness
            is Instruction.TagSwitch -> {
                for ((_, target) in inst.cases) verifyBlockRef(target, blockLabels, ctx, "tagswitch case")
                inst.defaultTarget?.let { verifyBlockRef(it, blockLabels, ctx, "tagswitch default") }
                if (inst.defaultTarget == null && inst.union.type is Type.TaggedUnion) {
                    val unionType = inst.union.type as Type.TaggedUnion
                    val variantNames = unionType.variants.map { it.name }.toSet()
                    val caseNames = inst.cases.map { it.first }.toSet()
                    val missing = variantNames - caseNames
                    if (missing.isNotEmpty()) {
                        error("tagswitch without default in $ctx is missing variants: ${missing.joinToString()}")
                    }
                }
            }

            // Select result type
            is Instruction.Select -> {
                if (inst.condition.type != Type.I1) {
                    error("select condition must be i1 in $ctx")
                }
                if (inst.trueValue.type != inst.falseValue.type) {
                    error("select true/false values have different types in $ctx")
                }
                if (inst.dest.type != inst.trueValue.type) {
                    error("select result type ${IrPrinter.typeStr(inst.dest.type)} doesn't match operand type ${IrPrinter.typeStr(inst.trueValue.type)} in $ctx")
                }
            }

            // Memory: pointer operand checks for remaining ops
            is Instruction.Prefetch -> {
                if (!isPointerType(inst.address.type)) {
                    error("prefetch address must be pointer type in $ctx")
                }
            }
            is Instruction.StackRestore -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("stackrestore operand must be pointer type in $ctx")
                }
            }
            is Instruction.LifetimeStart -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("lifetime.start operand must be pointer type in $ctx")
                }
            }
            is Instruction.LifetimeEnd -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("lifetime.end operand must be pointer type in $ctx")
                }
            }

            // BitCast/AddrSpaceCast: source and target must be same-sized or pointer
            is Instruction.BitCast -> {
                val from = inst.value.type
                val to = inst.toType
                val bothPtr = isPointerType(from) && isPointerType(to)
                val bothInt = isIntegerType(from) && isIntegerType(to)
                val bothFloat = isFloatType(from) && isFloatType(to)
                if (!bothPtr && !bothInt && !bothFloat && from !is Type.Vector && to !is Type.Vector) {
                    error("bitcast between incompatible type categories: ${IrPrinter.typeStr(from)} to ${IrPrinter.typeStr(to)} in $ctx")
                }
            }
            is Instruction.AddrSpaceCast -> {
                if (!isPointerType(inst.value.type)) error("addrspacecast source must be pointer type in $ctx")
                if (!isPointerType(inst.toType)) error("addrspacecast target must be pointer type in $ctx")
            }

            // GEP indices must be integer
            is Instruction.GetElementPtr -> {
                if (!isPointerType(inst.ptr.type)) {
                    error("getelementptr ptr operand must be pointer type in $ctx")
                }
                if (inst.indices.isEmpty()) {
                    error("getelementptr must have at least one index in $ctx")
                }
                for ((i, idx) in inst.indices.withIndex()) {
                    if (!isIntegerType(idx.type)) {
                        error("getelementptr index $i must be integer type in $ctx, got ${IrPrinter.typeStr(idx.type)}")
                    }
                }
            }

            // Expect: constant type must match value type
            is Instruction.Expect -> {
                if (inst.value.type != inst.expected.type) {
                    error("expect value type ${IrPrinter.typeStr(inst.value.type)} doesn't match expected constant type ${IrPrinter.typeStr(inst.expected.type)} in $ctx")
                }
            }

            // Assume: condition must be i1
            is Instruction.Assume -> {
                if (inst.condition.type != Type.I1) {
                    error("assume condition must be i1 in $ctx")
                }
            }

            // High-level: array ops
            is Instruction.NewArray -> {
                if (!isIntegerType(inst.size.type)) {
                    error("newarray size must be integer type in $ctx")
                }
            }
            is Instruction.NewMultiArray -> {
                for ((i, dim) in inst.dimensions.withIndex()) {
                    if (!isIntegerType(dim.type)) {
                        error("newmultiarray dimension $i must be integer type in $ctx")
                    }
                }
            }
            is Instruction.ArrayGet -> {
                if (!isIntegerType(inst.index.type)) {
                    error("arrayget index must be integer type in $ctx")
                }
            }
            is Instruction.ArraySet -> {
                if (!isIntegerType(inst.index.type)) {
                    error("arrayset index must be integer type in $ctx")
                }
            }

            is Instruction.Box -> {
                if (inst.dest.type !is Type.Reference) {
                    error("box result must be Reference type in $ctx, got ${inst.dest.type}")
                }
            }
            is Instruction.Unbox -> {
                if (inst.obj.type !is Type.Reference) {
                    error("unbox operand must be Reference type in $ctx, got ${inst.obj.type}")
                }
            }
            is Instruction.MonitorEnter -> {
                if (inst.obj.type !is Type.Reference) {
                    error("monitorenter operand must be Reference type in $ctx, got ${inst.obj.type}")
                }
            }
            is Instruction.MonitorExit -> {
                if (inst.obj.type !is Type.Reference) {
                    error("monitorexit operand must be Reference type in $ctx, got ${inst.obj.type}")
                }
            }

            // CatchValue: must be in a catch handler block with matching exception type
            is Instruction.CatchValue -> {
                val currentBlock = ctx.substringAfter("block %").substringBefore(" ")
                val catchHandlers = fn.blocks.flatMap { b ->
                    b.instructions.filterIsInstance<Instruction.TryCatchRegion>().flatMap { it.catches }
                }
                val matchingHandler = catchHandlers.find { it.handlerBlock == currentBlock }
                if (matchingHandler == null) {
                    error("CatchValue in $ctx is not in a catch handler block")
                } else if (matchingHandler.exceptionType != inst.exceptionType) {
                    error("CatchValue exception type doesn't match CatchHandler declaration in $ctx")
                }
            }

            // Everything else: no additional type constraints
            else -> {}
        }
    }

    private fun verifyIntUnary(operand: Value, ctx: String, name: String) {
        if (!isIntegerType(operand.type) && operand.type !is Type.Vector) {
            error("$name operand must be integer type in $ctx, got ${IrPrinter.typeStr(operand.type)}")
        }
    }

    private fun verifyFloatUnary(operand: Value, ctx: String, name: String) {
        if (!isFloatType(operand.type) && operand.type !is Type.Vector) {
            error("$name operand must be float type in $ctx, got ${IrPrinter.typeStr(operand.type)}")
        }
    }

    private fun verifyIntConversion(from: Type, to: Type, ctx: String, name: String) {
        if (!isIntegerType(from)) error("$name source must be integer type in $ctx")
        if (!isIntegerType(to)) error("$name target must be integer type in $ctx")
    }

    private fun verifyFloatConversion(from: Type, to: Type, ctx: String, name: String) {
        if (!isFloatType(from)) error("$name source must be float type in $ctx")
        if (!isFloatType(to)) error("$name target must be float type in $ctx")
    }

    private fun verifyIntBinOp(lhs: Value, rhs: Value, ctx: String, name: String) {
        if (lhs.type != rhs.type) {
            error("$name operands have different types: ${IrPrinter.typeStr(lhs.type)} vs ${IrPrinter.typeStr(rhs.type)} in $ctx")
        }
        if (!isIntegerType(lhs.type) && lhs.type !is Type.Vector) {
            error("$name operands must be integer type in $ctx, got ${IrPrinter.typeStr(lhs.type)}")
        }
    }

    private fun verifyFloatBinOp(lhs: Value, rhs: Value, ctx: String, name: String) {
        if (lhs.type != rhs.type) {
            error("$name operands have different types in $ctx")
        }
        if (!isFloatType(lhs.type) && lhs.type !is Type.Vector) {
            error("$name operands must be float type in $ctx, got ${IrPrinter.typeStr(lhs.type)}")
        }
    }

    private fun verifyCallArgs(function: Value, args: List<Value>, ctx: String, name: String) {
        val funcType = function.type
        if (funcType is Type.Function) {
            if (!funcType.vararg && args.size != funcType.params.size) {
                error("$name arg count ${args.size} doesn't match function param count ${funcType.params.size} in $ctx")
            }
            if (funcType.vararg && args.size < funcType.params.size) {
                error("$name has fewer args than required params for vararg function in $ctx")
            }
            for (i in funcType.params.indices) {
                if (i < args.size && args[i].type != funcType.params[i]) {
                    error("$name arg $i type ${IrPrinter.typeStr(args[i].type)} doesn't match param type ${IrPrinter.typeStr(funcType.params[i])} in $ctx")
                }
            }
        }
    }

    private fun verifyBlockRef(label: String, blockLabels: Set<String>, ctx: String, usage: String) {
        if (label !in blockLabels) {
            error("$usage references undefined block %$label in $ctx")
        }
    }

    private fun verifyConstantType(c: Constant, expectedType: Type, ctx: String) {
        if (c.type != expectedType && c !is Constant.ZeroInitializer && c !is Constant.Undef && c !is Constant.Poison) {
            error("Constant type ${IrPrinter.typeStr(c.type)} doesn't match expected ${IrPrinter.typeStr(expectedType)} in $ctx")
        }
    }

    private fun error(message: String) {
        errors += VerificationError(message)
    }

    companion object {
        /** Verify a module. Convenience shorthand for `IrVerifier().verify(module)`. */
        fun verify(module: Module): VerificationResult = IrVerifier().verify(module)

        /** True if [type] is an integer type (I1, I8, I16, I32, I64, I128, or IntN). */
        fun isIntegerType(type: Type): Boolean = when (type) {
            is Type.I1, is Type.I8, is Type.I16, is Type.I32, is Type.I64, is Type.I128, is Type.IntN -> true
            else -> false
        }

        /** True if [type] is a floating-point type (F16, BF16, F32, F64, F80, F128). */
        fun isFloatType(type: Type): Boolean = when (type) {
            is Type.F16, is Type.BF16, is Type.F32, is Type.F64, is Type.F80, is Type.F128 -> true
            else -> false
        }

        /** True if [type] is a pointer type (Pointer or OpaquePointer). */
        fun isPointerType(type: Type): Boolean = when (type) {
            is Type.Pointer, is Type.OpaquePointer -> true
            else -> false
        }

        /** True if [inst] is a terminator instruction (ret, br, condbr, switch, unreachable, etc.). */
        fun isTerminator(inst: Instruction): Boolean = when (inst) {
            is Instruction.Ret, is Instruction.Br, is Instruction.CondBr,
            is Instruction.Switch, is Instruction.IndirectBr,
            is Instruction.Unreachable, is Instruction.Resume,
            is Instruction.Invoke, is Instruction.CallBr,
            is Instruction.CatchRet, is Instruction.CleanupRet,
            is Instruction.Throw, is Instruction.Trap,
            is Instruction.TagSwitch -> true
            else -> false
        }
    }
}

/** A single verification error with a human-readable [message]. */
data class VerificationError(val message: String) {
    override fun toString() = message
}

/** Result of verification. [isValid] is true if no errors were found. */
data class VerificationResult(val errors: List<VerificationError>) {
    val isValid: Boolean get() = errors.isEmpty()

    override fun toString(): String =
        if (isValid) "Verification passed"
        else "Verification failed with ${errors.size} error(s):\n${errors.joinToString("\n") { "  - $it" }}"
}

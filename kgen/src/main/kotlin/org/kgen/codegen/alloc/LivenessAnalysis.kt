package org.kgen.codegen.alloc

import org.kgen.ir.*
import org.kgen.ir.instructions.*

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
    val isPhi: Boolean = false,
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
        val phiNames = mutableSetOf<String>()

        var instIdx = 0

        for (param in fn.params) {
            defs[param.name] = 0
            types[param.name] = param.type
        }

        val blockLabelToIndex = mutableMapOf<String, Int>()
        val blockStartPos = mutableMapOf<Int, Int>()
        val blockEndPos = mutableMapOf<Int, Int>()

        val sortedBlocks = sortBlocksRPO(fn)
        for ((blockIdx, block) in sortedBlocks.withIndex()) {
            blockLabelToIndex[block.label] = blockIdx
            val blockFirst = instIdx + 1
            var inPhiRegion = true
            for (inst in block.instructions) {
                instIdx++
                if (inPhiRegion && inst !is Phi) {
                    inPhiRegion = false
                }
                if (inst is Call || inst is Invoke || inst is CallBr) {
                    callPositions.add(instIdx)
                }
                val result = inst.result
                if (result != null) {
                    // Phi defs all share the block entry position because phi
                    // assignments are conceptually simultaneous. Sequential
                    // positions would let the allocator reuse registers between
                    // phis whose intervals don't overlap, but the phi copies
                    // at the predecessor write to all destinations at once.
                    defs[result.name] = if (inPhiRegion) { phiNames.add(result.name); blockFirst } else { instIdx }
                    types[result.name] = result.type
                }
                for (use in inst.operands) {
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

        // Pass 2: Fix phi liveness across back-edges.
        // Phi copies are emitted at the END of predecessor blocks. For back-edges
        // (predecessor after successor in linear order), the incoming value must be
        // live until the predecessor block end, and the phi dest register must not
        // be reassigned before the copy writes.
        for ((blockIdx, block) in sortedBlocks.withIndex()) {
            for (inst in block.instructions) {
                if (inst !is Phi) break
                val destName = inst.dest.name
                for ((value, predLabel) in inst.incoming) {
                    val predIdx = blockLabelToIndex[predLabel.label] ?: continue
                    if (predIdx < blockIdx) continue // forward edge — already handled by pass 1
                    val predEnd = blockEndPos[predIdx] ?: continue
                    // Extend incoming value's live range to back-edge predecessor block end
                    if (value is Parameter || value is InstructionRef) {
                        val name = value.name
                        if (name in defs) {
                            val current = lastUses[name] ?: defs[name]!!
                            if (predEnd > current) {
                                lastUses[name] = predEnd
                            }
                        }
                    }
                    // Extend phi dest range to prevent register reassignment before copy
                    val currentDest = lastUses[destName] ?: defs[destName] ?: continue
                    if (predEnd > currentDest) {
                        lastUses[destName] = predEnd
                    }
                }
            }
        }

        // Pass 3: Extend live ranges across loop back-edges for non-phi values
        // defined before the loop that are used inside.
        for ((blockIdx, block) in sortedBlocks.withIndex()) {
            val lastInst = block.instructions.lastOrNull() ?: continue
            val targets: List<String> = when (lastInst) {
                is Br -> listOf(lastInst.target.label)
                is CondBr -> listOf(lastInst.trueTarget.label, lastInst.falseTarget.label)
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

        val sortedCalls = callPositions.toIntArray().also { it.sort() }

        val intervals = mutableListOf<LiveInterval>()
        for ((name, start) in defs) {
            val end = lastUses[name] ?: start
            val type = types[name] ?: continue
            val count = useCounts[name] ?: 0
            val acrossCall = hasCallInRange(sortedCalls, start + 1, end)
            intervals.add(LiveInterval(name, start, end, type, count, acrossCall, isPhi = name in phiNames))
        }
        return intervals.sortedBy { it.start }
    }

    /**
     * Compute clobber events — instruction positions where specific registers
     * are implicitly destroyed. The caller provides a [RegisterConstraints] to map
     * instruction types to clobbered register sets.
     *
     * Detects: SDiv/UDiv/SRem/URem → INT_DIV, Shl/LShr/AShr → VARIABLE_SHIFT,
     * Call → CALL.
     */
    fun clobberEvents(constraints: RegisterConstraints): List<ClobberEvent> {
        val events = mutableListOf<ClobberEvent>()
        var instIdx = 0
        for (block in sortBlocksRPO(fn)) {
            for (inst in block.instructions) {
                instIdx++
                val kind = when (inst) {
                    is SDiv, is UDiv,
                    is SRem, is URem -> InstructionClobber.INT_DIV
                    is Shl, is LShr,
                    is AShr -> InstructionClobber.VARIABLE_SHIFT
                    is Call -> InstructionClobber.CALL
                    else -> null
                }
                if (kind != null) {
                    val regs = constraints.clobbers[kind]
                    if (!regs.isNullOrEmpty()) {
                        events.add(ClobberEvent(instIdx, regs))
                    }
                }
            }
        }
        return events
    }

    private fun hasCallInRange(sortedCalls: IntArray, low: Int, high: Int): Boolean {
        if (sortedCalls.isEmpty() || low > high) return false
        var lo = 0
        var hi = sortedCalls.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val pos = sortedCalls[mid]
            if (pos < low) {
                lo = mid + 1
            } else if (pos > high) {
                hi = mid - 1
            } else {
                return true
            }
        }
        return false
    }

    companion object {

        /**
         * Sort blocks in reverse postorder (RPO). This ensures that in acyclic
         * regions, definitions appear before uses in the linear instruction
         * numbering, which is required for correct liveness intervals.
         */
        @JvmStatic
        fun sortBlocksRPO(fn: IrFunction): List<BasicBlock> {
            if (fn.blocks.size <= 1) return fn.blocks

            val blockByLabel = fn.blocks.associateBy { it.label }
            val visited = mutableSetOf<String>()
            val postOrder = mutableListOf<BasicBlock>()

            fun successors(block: BasicBlock): List<String> {
                val last = block.instructions.lastOrNull() ?: return emptyList()
                return when (last) {
                    is Br -> listOf(last.target.label)
                    is CondBr -> listOf(last.trueTarget.label, last.falseTarget.label)
                    is Switch -> listOf(last.defaultTarget.label) + last.cases.map { it.second.label }
                    is Invoke -> listOf(last.normalDest.label, last.unwindDest.label)
                    is CallBr -> listOf(last.fallthrough.label) + last.indirectDests.map { it.label }
                    is CatchSwitch -> last.handlers.map { it.label } + listOfNotNull(last.unwindDest?.label)
                    is IndirectBr -> last.targets.map { it.label }
                    else -> emptyList()
                }
            }

            fun dfs(label: String) {
                if (!visited.add(label)) return
                val block = blockByLabel[label] ?: return
                for (succ in successors(block)) {
                    dfs(succ)
                }
                postOrder.add(block)
            }

            dfs(fn.blocks[0].label)
            postOrder.reverse()
            return postOrder
        }
        /**
         * Extract operand values from an instruction, filtered to only [Parameter]
         * and [InstructionRef] values (excludes constants, globals, function refs).
         *
         * Delegates to [Instruction.operands] and filters by value type.
         */
        @JvmStatic
        fun operandValues(inst: Instruction): List<Value> {
            return inst.operands.filter { it is Parameter || it is InstructionRef }
        }
    }
}
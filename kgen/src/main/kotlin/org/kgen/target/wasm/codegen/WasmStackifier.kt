package org.kgen.target.wasm.codegen

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Converts an IR function's CFG into WASM structured control flow.
 *
 * WASM requires nested block/loop/if structures rather than arbitrary jumps.
 * This stackifier analyzes the CFG and determines the correct nesting.
 *
 * Algorithm:
 * 1. Sort blocks in reverse postorder (RPO)
 * 2. Identify loop headers (targets of back-edges)
 * 3. For forward branch targets, create block scopes
 * 4. Ensure proper nesting via opening order
 */
class WasmStackifier(private val fn: IrFunction) {

    lateinit var orderedBlocks: List<BasicBlock>
        private set

    val loopHeaders = mutableSetOf<String>()

    data class ScopeEntry(
        val kind: Kind,
        val label: String,
        val openAt: Int,
        val closeAt: Int,
    ) {
        enum class Kind { BLOCK, LOOP }
    }

    private val scopes = mutableListOf<ScopeEntry>()

    fun analyze(): List<BasicBlock> {
        computeRPO()
        findLoopHeaders()
        computeScopes()
        return orderedBlocks
    }

    /** Scopes to open before emitting block at [index], sorted outermost-first. */
    fun scopeOpensAt(index: Int): List<ScopeEntry> =
        scopes.filter { it.openAt == index }
            .sortedWith(compareByDescending<ScopeEntry> { it.closeAt }
                .thenBy { it.kind }) // BLOCK before LOOP at same closeAt

    /** Number of scopes to close after emitting block at [index]. */
    fun scopeClosesAfter(index: Int): Int =
        scopes.count { it.closeAt == index }

    /** RPO index of a block by label. */
    fun blockIndex(label: String): Int = blockIndexMap[label] ?: -1

    private lateinit var blockIndexMap: Map<String, Int>

    private fun computeRPO() {
        // Compute reachable blocks via DFS, then use original IR order filtered to reachable.
        // The original IR order from ModuleBuilder naturally has loop bodies before exits
        // and follows the control flow structure the programmer intended.
        val blockMap = fn.blocks.associateBy { it.label }
        val reachable = mutableSetOf<String>()

        fun markReachable(label: String) {
            if (!reachable.add(label)) return
            val block = blockMap[label] ?: return
            for (succ in successors(block)) markReachable(succ)
        }

        if (fn.blocks.isNotEmpty()) markReachable(fn.blocks[0].label)

        orderedBlocks = fn.blocks.filter { it.label in reachable }
        blockIndexMap = orderedBlocks.withIndex().associate { (i, b) -> b.label to i }
    }

    private fun findLoopHeaders() {
        for ((idx, block) in orderedBlocks.withIndex()) {
            for (succ in successors(block)) {
                val succIdx = blockIndexMap[succ] ?: continue
                if (succIdx <= idx) loopHeaders.add(succ)
            }
        }
    }

    private fun computeScopes() {
        // Loop scopes: open at header, close after last back-edge source
        for (header in loopHeaders) {
            val headerIdx = blockIndexMap[header]!!
            var lastBackEdge = headerIdx
            for ((idx, block) in orderedBlocks.withIndex()) {
                if (idx > headerIdx && successors(block).contains(header)) {
                    lastBackEdge = maxOf(lastBackEdge, idx)
                }
            }
            scopes.add(ScopeEntry(ScopeEntry.Kind.LOOP, header, headerIdx, lastBackEdge))
        }

        // Forward branch targets: blocks reached by non-adjacent forward edges
        val forwardTargets = mutableSetOf<String>()
        for ((idx, block) in orderedBlocks.withIndex()) {
            for (succ in successors(block)) {
                if (succ in loopHeaders) continue
                val succIdx = blockIndexMap[succ] ?: continue
                if (succIdx > idx + 1) {
                    forwardTargets.add(succ)
                }
            }
        }
        // Also: blocks targeted from non-adjacent predecessor
        for ((idx, block) in orderedBlocks.withIndex()) {
            if (idx == 0 || block.label in loopHeaders) continue
            for (pred in orderedBlocks) {
                val predIdx = blockIndexMap[pred.label]!!
                if (predIdx != idx - 1 && successors(pred).contains(block.label)
                    && predIdx < idx
                ) {
                    forwardTargets.add(block.label)
                }
            }
        }

        // For each forward target, create a block scope.
        // Compute openAt to ensure proper nesting with loop scopes.
        // A BLOCK scope that closes inside a LOOP must open inside that LOOP.
        // A BLOCK scope that closes after a LOOP can open before or at the LOOP.
        for (target in forwardTargets) {
            val targetIdx = blockIndexMap[target]!!
            val closeAt = targetIdx - 1
            // Find the innermost enclosing loop scope
            var openAt = 0
            for (loop in scopes) {
                if (loop.kind != ScopeEntry.Kind.LOOP) continue
                // If this BLOCK closes inside the LOOP, it must open inside the LOOP too
                if (closeAt >= loop.openAt && closeAt <= loop.closeAt) {
                    openAt = maxOf(openAt, loop.openAt)
                }
            }
            scopes.add(ScopeEntry(ScopeEntry.Kind.BLOCK, target, openAt, closeAt))
        }
    }

    companion object {
        fun successors(block: BasicBlock): List<String> {
            val last = block.instructions.lastOrNull() ?: return emptyList()
            return when (last) {
                is Br -> listOf(last.target.label)
                is CondBr -> listOf(last.trueTarget.label, last.falseTarget.label)
                is Switch -> listOf(last.defaultTarget.label) + last.cases.map { it.second.label }
                else -> emptyList()
            }
        }
    }
}

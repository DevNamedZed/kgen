package org.kgen.codegen.alloc

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Graph coloring register allocator using Chaitin-Briggs with optimistic spilling.
 *
 * Produces better register assignments than linear scan at the cost of higher
 * compile time. Best suited for AOT compilation where code quality matters more
 * than compilation speed.
 *
 * Features:
 * - Interference graph built from live intervals
 * - Aggressive coalescing with George's test / Briggs conservative test
 * - Optimistic spilling — deferred spill decisions for better coloring
 * - Callee-saved preference for values live across calls
 * - Clobber awareness (INT_DIV, VARIABLE_SHIFT, CALL)
 *
 * ```java
 * var allocator = new GraphColoringAllocator();
 * var analysis = new LivenessAnalysis(fn);
 * var assignment = allocator.allocate(fn, analysis.intervals(), constraints);
 * ```
 */
class GraphColoringAllocator : RegisterAllocator {

    override fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        constraints: RegisterConstraints,
    ): RegisterAssignment {
        return Coloring(fn, intervals, constraints, emptyList()).run()
    }

    fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        constraints: RegisterConstraints,
        clobberEvents: List<ClobberEvent>,
    ): RegisterAssignment {
        return Coloring(fn, intervals, constraints, clobberEvents).run()
    }

    private class Coloring(
        private val fn: IrFunction,
        private val intervals: List<LiveInterval>,
        private val constraints: RegisterConstraints,
        private val clobberEvents: List<ClobberEvent>,
    ) {
        // Register classes
        private val regClasses = constraints.allocatable.map { it.registerClass }.distinct()
        private val allocatableByClass = constraints.allocatable.groupBy { it.registerClass }
        private val calleeSavedSet = constraints.calleeSaved

        // Interference graph
        private val adjList = mutableMapOf<String, MutableSet<String>>()
        private val degree = mutableMapOf<String, Int>()
        private val intervalMap = mutableMapOf<String, LiveInterval>()

        // Move worklists
        private data class Move(val src: String, val dst: String)
        private val moveList = mutableMapOf<String, MutableSet<Move>>()

        // Worklists
        private val simplifyWorklist = mutableSetOf<String>()
        private val freezeWorklist = mutableSetOf<String>()
        private val spillWorklist = mutableSetOf<String>()
        private val spilledNodes = mutableSetOf<String>()
        private val coalescedNodes = mutableSetOf<String>()
        private val selectStack = mutableListOf<String>()
        private val selectStackSet = mutableSetOf<String>()

        private val coalescedMoves = mutableSetOf<Move>()
        private val constrainedMoves = mutableSetOf<Move>()
        private val frozenMoves = mutableSetOf<Move>()
        private val worklistMoves = mutableSetOf<Move>()
        private val activeMoves = mutableSetOf<Move>()

        private val alias = mutableMapOf<String, String>()
        private val color = mutableMapOf<String, Int>()

        // Pre-colored nodes (parameters in ABI registers)
        private val precolored = mutableSetOf<String>()
        private val paramMoves = mutableMapOf<String, Int>()

        private val spillPool = SpillSlotPool()
        private val splitPoints = mutableListOf<SplitPoint>()

        fun run(): RegisterAssignment {
            // Build interval lookup
            for (iv in intervals) {
                intervalMap[iv.name] = iv
            }

            buildInterferenceGraph()
            buildMoveWorklist()
            makeWorklist()

            // Main loop
            while (simplifyWorklist.isNotEmpty() || worklistMoves.isNotEmpty()
                || freezeWorklist.isNotEmpty() || spillWorklist.isNotEmpty()) {
                when {
                    simplifyWorklist.isNotEmpty() -> simplify()
                    worklistMoves.isNotEmpty() -> coalesce()
                    freezeWorklist.isNotEmpty() -> freeze()
                    spillWorklist.isNotEmpty() -> selectSpill()
                }
            }

            return assignColors()
        }

        private fun k(name: String): Int {
            val iv = intervalMap[name] ?: return regClasses.firstOrNull()?.registers?.size ?: 0
            val cls = classForType(iv.type)
            return allocatableByClass[cls]?.size ?: 0
        }

        private fun classForType(type: Type): RegisterClass {
            return when {
                type == Type.F32 || type == Type.F64 -> regClasses.getOrElse(1) { regClasses.first() }
                else -> regClasses.first()
            }
        }

        private fun buildInterferenceGraph() {
            // Initialize all nodes
            for (iv in intervals) {
                adjList[iv.name] = mutableSetOf()
                degree[iv.name] = 0
                moveList[iv.name] = mutableSetOf()
            }

            // Assign pre-colored parameters
            var gpIdx = 0
            for (param in fn.params) {
                if (gpIdx >= constraints.paramRegisters.size) break
                val iv = intervalMap[param.name] ?: continue
                val paramReg = constraints.paramRegisters[gpIdx]
                val cls = classForType(iv.type)
                val allocatable = allocatableByClass[cls] ?: emptyList()
                val colorIdx = allocatable.indexOf(paramReg)

                val needsMove = (iv.acrossCall && paramReg !in calleeSavedSet)
                    || paramReg !in allocatable
                    || clobberedRegistersFor(iv).contains(paramReg)

                if (needsMove) {
                    paramMoves[param.name] = gpIdx
                } else if (colorIdx >= 0) {
                    color[param.name] = colorIdx
                    precolored.add(param.name)
                    degree[param.name] = Int.MAX_VALUE / 2 // effectively infinite
                }
                gpIdx++
            }

            // Build edges: two values interfere if their live ranges overlap
            // and they need the same register class
            val sorted = intervals.sortedBy { it.start }
            for (i in sorted.indices) {
                val a = sorted[i]
                for (j in i + 1 until sorted.size) {
                    val b = sorted[j]
                    if (b.start > a.end) break // no more overlaps with a
                    if (classForType(a.type) == classForType(b.type)) {
                        addEdge(a.name, b.name)
                    }
                }
            }
        }

        private fun addEdge(u: String, v: String) {
            if (u == v) return
            if (v in (adjList[u] ?: emptySet())) return // already exists

            adjList.getOrPut(u) { mutableSetOf() }.add(v)
            adjList.getOrPut(v) { mutableSetOf() }.add(u)

            if (u !in precolored) {
                degree[u] = (degree[u] ?: 0) + 1
            }
            if (v !in precolored) {
                degree[v] = (degree[v] ?: 0) + 1
            }
        }

        private fun buildMoveWorklist() {
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    // Phi nodes: incoming values should prefer the phi destination
                    if (inst is Phi) {
                        for ((value, _) in inst.incoming) {
                            if (value is Parameter || value is InstructionRef) {
                                if (value.name in intervalMap && inst.dest.name in intervalMap) {
                                    val move = Move(value.name, inst.dest.name)
                                    worklistMoves.add(move)
                                    moveList.getOrPut(value.name) { mutableSetOf() }.add(move)
                                    moveList.getOrPut(inst.dest.name) { mutableSetOf() }.add(move)
                                }
                            }
                        }
                    }

                    // Copy-like instructions — only same-width, same-class copies
                    // ZExt/SExt/IntTrunc/FTrunc change bit width — coalescing would lose the conversion
                    val copyPair = when (inst) {
                        is BitCast -> {
                            if (inst.value.type == inst.dest.type) {
                                inst.value.name to inst.dest.name
                            } else {
                                null
                            }
                        }
                        else -> null
                    }
                    if (copyPair != null && copyPair.first in intervalMap && copyPair.second in intervalMap) {
                        val srcIv = intervalMap[copyPair.first]!!
                        val dstIv = intervalMap[copyPair.second]!!
                        if (classForType(srcIv.type) == classForType(dstIv.type)) {
                            val move = Move(copyPair.first, copyPair.second)
                            worklistMoves.add(move)
                            moveList.getOrPut(copyPair.first) { mutableSetOf() }.add(move)
                            moveList.getOrPut(copyPair.second) { mutableSetOf() }.add(move)
                        }
                    }
                }
            }
        }

        private fun makeWorklist() {
            for (iv in intervals) {
                val name = iv.name
                if (name in precolored) continue
                if (name in paramMoves) continue // will be handled separately

                val k = k(name)
                when {
                    (degree[name] ?: 0) >= k -> spillWorklist.add(name)
                    moveRelated(name) -> freezeWorklist.add(name)
                    else -> simplifyWorklist.add(name)
                }
            }
        }

        private fun adjacent(u: String): Set<String> {
            return (adjList[u] ?: emptySet()).filterTo(mutableSetOf()) {
                it !in selectStackSet && it !in coalescedNodes
            }
        }

        private fun nodeMoves(u: String): Set<Move> {
            return (moveList[u] ?: emptySet()).filterTo(mutableSetOf()) {
                it in activeMoves || it in worklistMoves
            }
        }

        private fun moveRelated(u: String): Boolean = nodeMoves(u).isNotEmpty()

        private fun simplify() {
            val node = simplifyWorklist.first()
            simplifyWorklist.remove(node)
            selectStack.add(node)
            selectStackSet.add(node)
            for (adj in adjacent(node)) {
                decrementDegree(adj)
            }
        }

        private fun decrementDegree(node: String) {
            if (node in precolored) return
            val d = degree[node] ?: return
            degree[node] = d - 1
            val k = k(node)
            if (d == k) {
                // Degree dropped below K — enable moves and move to appropriate worklist
                enableMoves(adjacent(node) + node)
                spillWorklist.remove(node)
                if (moveRelated(node)) {
                    freezeWorklist.add(node)
                } else {
                    simplifyWorklist.add(node)
                }
            }
        }

        private fun enableMoves(nodes: Set<String>) {
            for (n in nodes) {
                for (m in nodeMoves(n)) {
                    if (m in activeMoves) {
                        activeMoves.remove(m)
                        worklistMoves.add(m)
                    }
                }
            }
        }

        private fun getAlias(n: String): String {
            var cur = n
            while (cur in coalescedNodes) {
                cur = alias[cur] ?: break
            }
            return cur
        }

        private fun coalesce() {
            val m = worklistMoves.first()
            worklistMoves.remove(m)

            val x = getAlias(m.src)
            val y = getAlias(m.dst)
            val (u, v) = if (y in precolored) (y to x) else (x to y)

            when {
                u == v -> {
                    coalescedMoves.add(m)
                    addWorklistIfSimplifiable(u)
                }
                v in precolored || v in (adjList[u] ?: emptySet()) -> {
                    constrainedMoves.add(m)
                    addWorklistIfSimplifiable(u)
                    addWorklistIfSimplifiable(v)
                }
                canCoalesce(u, v) -> {
                    coalescedMoves.add(m)
                    combine(u, v)
                    addWorklistIfSimplifiable(u)
                }
                else -> {
                    activeMoves.add(m)
                }
            }
        }

        private fun canCoalesce(u: String, v: String): Boolean {
            return if (u in precolored) {
                // George's test: every neighbor of v either interferes with u or has degree < K
                adjacent(v).all { t ->
                    t in (adjList[u] ?: emptySet()) || (degree[t] ?: 0) < k(t)
                }
            } else {
                // Briggs conservative test: merged node has < K high-degree neighbors
                val combined = adjacent(u) + adjacent(v)
                val k = k(u)
                combined.count { (degree[it] ?: 0) >= k } < k
            }
        }

        private fun combine(u: String, v: String) {
            if (v in freezeWorklist) {
                freezeWorklist.remove(v)
            } else {
                spillWorklist.remove(v)
            }
            coalescedNodes.add(v)
            alias[v] = u
            moveList.getOrPut(u) { mutableSetOf() }.addAll(moveList[v] ?: emptySet())
            for (t in adjacent(v)) {
                addEdge(t, u)
                decrementDegree(t)
            }
            val k = k(u)
            if ((degree[u] ?: 0) >= k && u in freezeWorklist) {
                freezeWorklist.remove(u)
                spillWorklist.add(u)
            }
        }

        private fun addWorklistIfSimplifiable(u: String) {
            if (u !in precolored && !moveRelated(u) && (degree[u] ?: 0) < k(u)) {
                freezeWorklist.remove(u)
                simplifyWorklist.add(u)
            }
        }

        private fun freeze() {
            val u = freezeWorklist.first()
            freezeWorklist.remove(u)
            simplifyWorklist.add(u)
            freezeMoves(u)
        }

        private fun freezeMoves(u: String) {
            for (m in nodeMoves(u)) {
                val v = if (getAlias(m.dst) == getAlias(u)) getAlias(m.src) else getAlias(m.dst)
                activeMoves.remove(m)
                frozenMoves.add(m)
                if (v !in precolored && nodeMoves(v).isEmpty() && (degree[v] ?: 0) < k(v)) {
                    freezeWorklist.remove(v)
                    simplifyWorklist.add(v)
                }
            }
        }

        private fun selectSpill() {
            // Pick node with lowest spill cost
            val victim = spillWorklist.minByOrNull { spillCost(it) } ?: return
            spillWorklist.remove(victim)
            simplifyWorklist.add(victim)
            freezeMoves(victim)
        }

        private fun spillCost(name: String): Double {
            val iv = intervalMap[name] ?: return Double.MAX_VALUE
            val range = (iv.end - iv.start).coerceAtLeast(1)
            var cost = iv.useCount.toDouble() / range
            if (iv.acrossCall) cost *= 2.0
            return cost
        }

        private fun assignColors(): RegisterAssignment {
            val locations = mutableMapOf<String, ValueLocation>()
            val usedCallee = mutableSetOf<PhysicalRegister>()

            // Colors for pre-colored nodes are already set
            for (name in precolored) {
                val iv = intervalMap[name]!!
                val cls = classForType(iv.type)
                val regs = allocatableByClass[cls] ?: continue
                val reg = regs[color[name]!!]
                locations[name] = ValueLocation.Register(reg)
                if (reg in calleeSavedSet) usedCallee.add(reg)
            }

            // Pop from select stack and assign colors
            while (selectStack.isNotEmpty()) {
                val name = selectStack.removeLast()
                selectStackSet.remove(name)
                val iv = intervalMap[name] ?: continue
                val cls = classForType(iv.type)
                val regs = allocatableByClass[cls] ?: continue

                // Find colors used by neighbors
                val usedColors = mutableSetOf<Int>()
                for (adj in adjList[name] ?: emptySet()) {
                    val a = getAlias(adj)
                    if (a in color) {
                        // Only count the color if same register class
                        val adjIv = intervalMap[a]
                        if (adjIv != null && classForType(adjIv.type) == cls) {
                            usedColors.add(color[a]!!)
                        }
                    }
                }

                // Find clobbered register indices during this interval
                val clobbered = clobberedRegistersFor(iv)
                val clobberedIndices = mutableSetOf<Int>()
                for ((i, reg) in regs.withIndex()) {
                    if (reg in clobbered) clobberedIndices.add(i)
                }

                // Pick a color
                val available = (0 until regs.size).filter { it !in usedColors && it !in clobberedIndices }
                val colorIdx = if (available.isEmpty()) {
                    // Try without clobber avoidance (will need split points)
                    val fallback = (0 until regs.size).filter { it !in usedColors }
                    if (fallback.isEmpty()) {
                        // Actual spill
                        spilledNodes.add(name)
                        null
                    } else {
                        pickPreferredColor(name, iv, fallback, regs)
                    }
                } else {
                    pickPreferredColor(name, iv, available, regs)
                }

                if (colorIdx != null) {
                    color[name] = colorIdx
                    val reg = regs[colorIdx]
                    locations[name] = ValueLocation.Register(reg)
                    if (reg in calleeSavedSet) usedCallee.add(reg)

                    // Generate split points if using a clobbered register
                    if (colorIdx in clobberedIndices) {
                        generateSplitPoints(iv, reg)
                    }
                }
            }

            // Handle coalesced nodes: copy the representative's location
            for (name in coalescedNodes) {
                val rep = getAlias(name)
                if (rep in color) {
                    val iv = intervalMap[name] ?: continue
                    val cls = classForType(iv.type)
                    val regs = allocatableByClass[cls] ?: continue
                    val repIv = intervalMap[rep]
                    if (repIv != null && classForType(repIv.type) == cls) {
                        val reg = regs[color[rep]!!]
                        locations[name] = ValueLocation.Register(reg)
                    }
                }
            }

            // Assign spill slots
            for (name in spilledNodes) {
                val slot = spillPool.acquire()
                locations[name] = ValueLocation.SpillSlot(spillPool.offsetFor(slot))
            }

            // Handle param moves
            for ((name, _) in paramMoves) {
                if (name !in locations) {
                    // Param needs a move — assign to a register or spill
                    val iv = intervalMap[name] ?: continue
                    val cls = classForType(iv.type)
                    val regs = allocatableByClass[cls] ?: continue

                    val usedColors = mutableSetOf<Int>()
                    for (adj in adjList[name] ?: emptySet()) {
                        val a = getAlias(adj)
                        if (a in color) {
                            val adjIv = intervalMap[a]
                            if (adjIv != null && classForType(adjIv.type) == cls) {
                                usedColors.add(color[a]!!)
                            }
                        }
                    }

                    val available = (0 until regs.size).filter { it !in usedColors }
                    if (available.isNotEmpty()) {
                        val colorIdx = if (iv.acrossCall) {
                            available.firstOrNull { regs[it] in calleeSavedSet } ?: available.first()
                        } else {
                            available.first()
                        }
                        color[name] = colorIdx
                        val reg = regs[colorIdx]
                        locations[name] = ValueLocation.Register(reg)
                        if (reg in calleeSavedSet) usedCallee.add(reg)
                    } else {
                        val slot = spillPool.acquire()
                        locations[name] = ValueLocation.SpillSlot(spillPool.offsetFor(slot))
                    }
                }
            }

            return RegisterAssignment(
                locations = locations,
                spillSlots = spillPool.maxSlots,
                usedCalleeRegisters = usedCallee,
                paramMoves = paramMoves,
                splitPoints = splitPoints,
            )
        }

        private fun pickPreferredColor(
            name: String, iv: LiveInterval,
            available: List<Int>, regs: List<PhysicalRegister>,
        ): Int {
            // Prefer callee-saved for values across calls
            if (iv.acrossCall) {
                val calleeSaved = available.filter { regs[it] in calleeSavedSet }
                if (calleeSaved.isNotEmpty()) return calleeSaved.first()
            }

            // Prefer move-related partner's color
            for (m in moveList[name] ?: emptySet()) {
                val partner = if (getAlias(m.src) == name) getAlias(m.dst) else getAlias(m.src)
                if (partner in color && color[partner]!! in available) {
                    return color[partner]!!
                }
            }

            // For values NOT across calls, prefer caller-saved (avoid callee-saved)
            if (!iv.acrossCall) {
                val callerSaved = available.filter { regs[it] !in calleeSavedSet }
                if (callerSaved.isNotEmpty()) return callerSaved.first()
            }

            return available.first()
        }

        private fun clobberedRegistersFor(iv: LiveInterval): Set<PhysicalRegister> {
            if (clobberEvents.isEmpty()) return emptySet()
            val result = mutableSetOf<PhysicalRegister>()
            for (event in clobberEvents) {
                if (event.position in (iv.start + 1)..iv.end) {
                    result.addAll(event.clobberedRegisters)
                }
            }
            return result
        }

        private fun generateSplitPoints(iv: LiveInterval, reg: PhysicalRegister) {
            val slot = spillPool.acquire()
            val offset = spillPool.offsetFor(slot)
            for (event in clobberEvents) {
                if (event.position in (iv.start + 1)..iv.end && reg in event.clobberedRegisters) {
                    splitPoints.add(SplitPoint(iv.name, event.position, event.position, offset))
                }
            }
        }
    }

    private class SpillSlotPool {
        private val freeSlots = mutableListOf<Int>()
        var maxSlots = 0
            private set

        fun acquire(): Int {
            return if (freeSlots.isNotEmpty()) freeSlots.removeFirst()
            else ++maxSlots
        }

        fun release(slot: Int) {
            freeSlots.add(slot)
        }

        fun offsetFor(slot: Int) = -slot * 8
    }
}

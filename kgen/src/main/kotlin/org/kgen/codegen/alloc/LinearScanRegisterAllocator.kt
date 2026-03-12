package org.kgen.codegen.alloc

import org.kgen.ir.IrFunction
import org.kgen.ir.Type

/**
 * Target-independent linear scan register allocator.
 *
 * Assigns physical registers to IR values using a linear scan over live intervals.
 * Supports multiple register classes (GP, FP/SIMD), callee-saved preference for
 * values live across calls, and cost-based eviction when registers are exhausted.
 *
 * ```java
 * var allocator = new LinearScanRegisterAllocator();
 * var analysis = new LivenessAnalysis(fn);
 * var assignment = allocator.allocate(fn, analysis.intervals(), constraints);
 *
 * for (var entry : assignment.locations().entrySet()) {
 *     if (entry.getValue() instanceof ValueLocation.Register reg) {
 *         System.out.println(entry.getKey() + " → " + reg.reg());
 *     }
 * }
 * ```
 */
class LinearScanRegisterAllocator : RegisterAllocator {

    override fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        constraints: RegisterConstraints,
    ): RegisterAssignment {
        return LinearScan(fn, intervals, constraints, emptyList()).run()
    }

    /**
     * Allocate with clobber awareness. Values live across a clobber point
     * will not be assigned to registers that the clobber destroys.
     *
     * Use [LivenessAnalysis.clobberEvents] to compute clobber events.
     */
    fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        constraints: RegisterConstraints,
        clobberEvents: List<ClobberEvent>,
    ): RegisterAssignment {
        return LinearScan(fn, intervals, constraints, clobberEvents).run()
    }

    private class LinearScan(
        private val fn: IrFunction,
        private val intervals: List<LiveInterval>,
        private val constraints: RegisterConstraints,
        private val clobberEvents: List<ClobberEvent>,
    ) {
        private val locations = mutableMapOf<String, ValueLocation>()
        private val paramMoves = mutableMapOf<String, Int>()
        private val usedCallee = mutableSetOf<PhysicalRegister>()

        // Group allocatable registers by class
        private val freeByClass = mutableMapOf<RegisterClass, MutableList<PhysicalRegister>>()
        private val activeByClass = mutableMapOf<RegisterClass, MutableList<ActiveEntry>>()

        private data class ActiveEntry(val interval: LiveInterval, val reg: PhysicalRegister)

        private val spillPool = SpillSlotPool()
        private val spilledIntervals = mutableMapOf<String, Int>()
        private val splitPoints = mutableListOf<SplitPoint>()

        fun run(): RegisterAssignment {
            initializeFreeLists()
            assignParameters()
            allocateIntervals()
            return RegisterAssignment(
                locations = locations,
                spillSlots = spillPool.maxSlots,
                usedCalleeRegisters = usedCallee,
                paramMoves = paramMoves,
                splitPoints = splitPoints,
            )
        }

        private fun initializeFreeLists() {
            for (reg in constraints.allocatable) {
                freeByClass.getOrPut(reg.registerClass) { mutableListOf() }.add(reg)
                activeByClass.putIfAbsent(reg.registerClass, mutableListOf())
            }
        }

        private fun assignParameters() {
            var gpIdx = 0
            for (param in fn.params) {
                if (gpIdx >= constraints.paramRegisters.size) break
                val interval = intervals.firstOrNull { it.name == param.name } ?: continue

                val paramReg = constraints.paramRegisters[gpIdx]
                val needsMove = (interval.acrossCall && paramReg !in constraints.calleeSaved)
                    || paramReg in clobberedRegistersFor(interval)
                if (needsMove) {
                    paramMoves[param.name] = gpIdx
                } else {
                    locations[param.name] = ValueLocation.Register(paramReg)
                    val cls = paramReg.registerClass
                    freeByClass[cls]?.remove(paramReg)
                    activeByClass.getOrPut(cls) { mutableListOf() }.add(ActiveEntry(interval, paramReg))
                    if (paramReg in constraints.calleeSaved) usedCallee.add(paramReg)
                }
                gpIdx++
            }
        }

        private fun allocateIntervals() {
            for (interval in intervals) {
                if (interval.name in locations) continue
                expireOld(interval.start)

                val cls = classForType(interval.type)
                val free = freeByClass[cls]
                val active = activeByClass.getOrPut(cls) { mutableListOf() }

                if (free != null && free.isNotEmpty()) {
                    val reg = pickBestReg(free, interval)
                    free.remove(reg)
                    locations[interval.name] = ValueLocation.Register(reg)
                    active.add(ActiveEntry(interval, reg))
                    if (reg in constraints.calleeSaved) usedCallee.add(reg)
                } else if (active.isNotEmpty()) {
                    val evictable = tryEvict(interval, active)
                    if (evictable != null) {
                        active.remove(evictable)
                        val evictedReg = evictable.reg
                        locations[evictable.interval.name] = spillToPool(evictable.interval.name)
                        locations[interval.name] = ValueLocation.Register(evictedReg)
                        active.add(ActiveEntry(interval, evictedReg))
                    } else {
                        locations[interval.name] = spillToPool(interval.name)
                    }
                } else {
                    locations[interval.name] = spillToPool(interval.name)
                }
            }
        }

        private fun expireOld(currentStart: Int) {
            for ((_, active) in activeByClass) {
                active.removeAll { (iv, reg) ->
                    if (iv.end < currentStart) {
                        freeByClass[reg.registerClass]?.add(reg)
                        true
                    } else false
                }
            }
            val expired = spilledIntervals.entries.filter { (name, _) ->
                intervals.firstOrNull { it.name == name }?.end?.let { it < currentStart } == true
            }
            for ((name, slot) in expired) {
                spilledIntervals.remove(name)
                spillPool.release(slot)
            }
        }

        private fun pickBestReg(free: List<PhysicalRegister>, interval: LiveInterval): PhysicalRegister {
            // Find registers clobbered during this interval's live range
            val clobberedDuringInterval = clobberedRegistersFor(interval)
            val safe = if (clobberedDuringInterval.isNotEmpty()) {
                free.filter { it !in clobberedDuringInterval }
            } else free

            val useSafe = safe.isNotEmpty()
            val candidates = if (useSafe) safe else free

            val picked = if (interval.acrossCall) {
                candidates.firstOrNull { it in constraints.calleeSaved }
                    ?: candidates.first()
            } else {
                candidates.firstOrNull { it !in constraints.calleeSaved }
                    ?: candidates.first()
            }

            // If we had to pick a clobbered register, generate split points
            if (!useSafe && clobberedDuringInterval.isNotEmpty() && picked in clobberedDuringInterval) {
                generateSplitPoints(interval, picked)
            }

            return picked
        }

        private fun generateSplitPoints(interval: LiveInterval, reg: PhysicalRegister) {
            val slot = spillPool.acquire()
            val offset = spillPool.offsetFor(slot)
            for (event in clobberEvents) {
                if (event.position in (interval.start + 1)..interval.end && reg in event.clobberedRegisters) {
                    splitPoints.add(SplitPoint(interval.name, event.position, event.position, offset))
                }
            }
        }

        private fun clobberedRegistersFor(interval: LiveInterval): Set<PhysicalRegister> {
            if (clobberEvents.isEmpty()) return emptySet()
            val result = mutableSetOf<PhysicalRegister>()
            for (event in clobberEvents) {
                if (event.position in (interval.start + 1)..interval.end) {
                    result.addAll(event.clobberedRegisters)
                }
            }
            return result
        }

        private fun tryEvict(
            interval: LiveInterval,
            active: List<ActiveEntry>,
        ): ActiveEntry? {
            val currentCost = spillCost(interval)
            val clobbered = clobberedRegistersFor(interval)

            // Prefer evicting a value whose register is NOT clobbered during our interval
            // so we get a safe register
            val candidates = active.filter { spillCost(it.interval) < currentCost }
            if (candidates.isEmpty()) return null

            // If we have clobber concerns, prefer evicting from a non-clobbered register
            if (clobbered.isNotEmpty()) {
                val safe = candidates.filter { it.reg !in clobbered }
                if (safe.isNotEmpty()) return safe.minByOrNull { spillCost(it.interval) }
            }

            return candidates.minByOrNull { spillCost(it.interval) }
        }

        private fun spillCost(iv: LiveInterval): Double {
            val range = (iv.end - iv.start).coerceAtLeast(1)
            var cost = iv.useCount.toDouble() / range
            if (iv.acrossCall) cost *= 2.0
            return cost
        }

        private fun spillToPool(name: String): ValueLocation.SpillSlot {
            val slot = spillPool.acquire()
            spilledIntervals[name] = slot
            return ValueLocation.SpillSlot(spillPool.offsetFor(slot))
        }

        private fun classForType(type: Type): RegisterClass {
            // Find the first register class by convention:
            // If there are multiple classes, assume first is GP, second is FP
            val classes = constraints.allocatable.map { it.registerClass }.distinct()
            return when {
                type == Type.F32 || type == Type.F64 -> classes.getOrElse(1) { classes.first() }
                else -> classes.first()
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

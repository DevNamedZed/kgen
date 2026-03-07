package org.kgen.backend.x86.codegen

import org.kgen.ir.*
import org.kgen.ir.codegen.LiveInterval
import org.kgen.ir.codegen.LivenessAnalysis
import org.kgen.backend.x86.*

sealed interface Location {
    data class Reg64(val reg: X86Register64) : Location
    data class Reg32(val reg: X86Register32) : Location
    data class RegXmm(val reg: X86Xmm) : Location
    data class Spill(val offset: Int) : Location
}

data class AllocResult(
    val locations: Map<String, Location>,
    val spillSlots: Int,
    val usedCalleeRegs64: Set<X86Register64>,
    val usedCalleeRegs32: Set<X86Register32>,
    val paramMoves: Map<String, Int> = emptyMap(),
)

class LinearScanAllocator(
    private val fn: IrFunction,
    private val availableRegs64: List<X86Register64>,
    private val availableRegs32: List<X86Register32>,
    private val availableXmm: List<X86Xmm> = emptyList(),
    private val calleeSaved64: Set<X86Register64>,
    private val calleeSaved32: Set<X86Register32>,
    private val paramRegs64: Array<X86Register64>,
    private val paramRegs32: Array<X86Register32>,
    private val paramXmm: Array<X86Xmm> = emptyArray(),
    private val gpParamOffset: Int = 0,
) {

    fun allocate(): AllocResult {
        val intervals = LivenessAnalysis(fn).intervals()
        return linearScan(intervals)
    }

    private val hasDiv: Boolean by lazy {
        fn.blocks.any { block ->
            block.instructions.any {
                it is Instruction.SDiv || it is Instruction.UDiv ||
                it is Instruction.SRem || it is Instruction.URem
            }
        }
    }

    private fun isFloatType(type: Type) = type == Type.F32 || type == Type.F64

    private fun structSize(type: Type.Struct): Int = type.fields.sumOf { fieldSize(it) }

    private fun fieldSize(type: Type): Int = when (type) {
        Type.I1, Type.I8 -> 1; Type.I16 -> 2; Type.I32, Type.F32 -> 4
        Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> 8
        is Type.Struct -> structSize(type)
        is Type.Array -> fieldSize(type.element) * type.size.toInt()
        else -> 8
    }

    private class SpillSlotPool {
        private val freeSlots = mutableListOf<Int>()
        var maxSlots = 0
            private set

        fun acquire(): Int {
            return if (freeSlots.isNotEmpty()) {
                freeSlots.removeFirst()
            } else {
                ++maxSlots
            }
        }

        fun release(slot: Int) {
            freeSlots.add(slot)
        }

        fun acquireContiguous(count: Int): Int {
            val first = maxSlots + 1
            maxSlots += count
            return first
        }

        fun offsetFor(slot: Int) = -slot * 8
    }

    private fun spillCost(iv: LiveInterval): Double {
        val range = (iv.end - iv.start).coerceAtLeast(1)
        var cost = iv.useCount.toDouble() / range
        if (iv.acrossCall) cost *= 2.0
        return cost
    }

    private fun linearScan(intervals: List<LiveInterval>): AllocResult {
        val locations = mutableMapOf<String, Location>()
        val active64 = mutableListOf<Pair<LiveInterval, X86Register64>>()
        val active32 = mutableListOf<Pair<LiveInterval, X86Register32>>()
        val activeXmm = mutableListOf<Pair<LiveInterval, X86Xmm>>()
        val free64 = availableRegs64.toMutableList()
        val free32 = availableRegs32.toMutableList()
        val freeXmm = availableXmm.toMutableList()
        val usedCallee64 = mutableSetOf<X86Register64>()
        val usedCallee32 = mutableSetOf<X86Register32>()
        val spillPool = SpillSlotPool()

        val spilledIntervals = mutableMapOf<String, Int>()

        fun expireOld(currentStart: Int) {
            active64.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { free64.add(reg); true } else false
            }
            active32.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { free32.add(reg); true } else false
            }
            activeXmm.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { freeXmm.add(reg); true } else false
            }
            val expired = spilledIntervals.entries.filter { (name, _) ->
                val iv = intervals.firstOrNull { it.name == name }
                iv != null && iv.end < currentStart
            }
            for ((name, slot) in expired) {
                spilledIntervals.remove(name)
                spillPool.release(slot)
            }
        }

        fun spillToPool(name: String): Location.Spill {
            val slot = spillPool.acquire()
            spilledIntervals[name] = slot
            return Location.Spill(spillPool.offsetFor(slot))
        }

        // Pre-assign parameter registers
        var gpIdx = gpParamOffset
        var xmmIdx = 0
        val paramMoves = mutableMapOf<String, Int>()
        for (param in fn.params) {
            val interval = intervals.firstOrNull { it.name == param.name } ?: continue
            if (isFloatType(param.type)) {
                if (xmmIdx < paramXmm.size) {
                    if (interval.acrossCall) {
                        paramMoves[param.name] = xmmIdx
                    } else {
                        val reg = paramXmm[xmmIdx]
                        locations[param.name] = Location.RegXmm(reg)
                        freeXmm.remove(reg)
                        activeXmm.add(interval to reg)
                    }
                    xmmIdx++
                }
            } else if (param.type == Type.I64 || param.type == Type.OpaquePointer || param.type is Type.Pointer) {
                if (gpIdx < paramRegs64.size) {
                    val reg = paramRegs64[gpIdx]
                    val needsMove = interval.acrossCall || (hasDiv && reg !in availableRegs64)
                    if (needsMove) {
                        paramMoves[param.name] = gpIdx
                    } else {
                        locations[param.name] = Location.Reg64(reg)
                        free64.remove(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    }
                    gpIdx++
                }
            } else {
                if (gpIdx < paramRegs32.size) {
                    val reg = paramRegs32[gpIdx]
                    val needsMove = interval.acrossCall || (hasDiv && reg !in availableRegs32)
                    if (needsMove) {
                        paramMoves[param.name] = gpIdx
                    } else {
                        locations[param.name] = Location.Reg32(reg)
                        free32.remove(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) usedCallee32.add(reg)
                    }
                    gpIdx++
                }
            }
        }

        for (interval in intervals) {
            if (interval.name in locations) continue
            expireOld(interval.start)

            when {
                interval.type is Type.Struct -> {
                    val slotsNeeded = ((structSize(interval.type as Type.Struct) + 7) / 8).coerceAtLeast(1)
                    val firstSlot = if (slotsNeeded == 1) spillPool.acquire()
                        else spillPool.acquireContiguous(slotsNeeded)
                    spilledIntervals[interval.name] = firstSlot
                    locations[interval.name] = Location.Spill(spillPool.offsetFor(firstSlot))
                }
                isFloatType(interval.type) -> {
                    if (freeXmm.isNotEmpty()) {
                        val reg = freeXmm.removeFirst()
                        locations[interval.name] = Location.RegXmm(reg)
                        activeXmm.add(interval to reg)
                    } else {
                        val evicted = tryEvictXmm(interval, activeXmm)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            activeXmm.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv.name)
                            locations[interval.name] = Location.RegXmm(evictedReg)
                            activeXmm.add(interval to evictedReg)
                        } else {
                            locations[interval.name] = spillToPool(interval.name)
                        }
                    }
                }
                interval.type == Type.I64 || interval.type == Type.OpaquePointer || interval.type is Type.Pointer -> {
                    val reg = pickBestReg64(free64, interval)
                    if (reg != null) {
                        free64.remove(reg)
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        val evicted = tryEvict64(interval, active64)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            active64.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv.name)
                            locations[interval.name] = Location.Reg64(evictedReg)
                            active64.add(interval to evictedReg)
                            if (evictedReg in calleeSaved64) usedCallee64.add(evictedReg)
                        } else {
                            locations[interval.name] = spillToPool(interval.name)
                        }
                    }
                }
                interval.type == Type.I32 || interval.type == Type.I16 || interval.type == Type.I8 || interval.type == Type.I1 -> {
                    val reg = pickBestReg32(free32, interval)
                    if (reg != null) {
                        free32.remove(reg)
                        locations[interval.name] = Location.Reg32(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) usedCallee32.add(reg)
                    } else {
                        val evicted = tryEvict32(interval, active32)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            active32.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv.name)
                            locations[interval.name] = Location.Reg32(evictedReg)
                            active32.add(interval to evictedReg)
                            if (evictedReg in calleeSaved32) usedCallee32.add(evictedReg)
                        } else {
                            locations[interval.name] = spillToPool(interval.name)
                        }
                    }
                }
                else -> {
                    val reg = pickBestReg64(free64, interval)
                    if (reg != null) {
                        free64.remove(reg)
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        locations[interval.name] = spillToPool(interval.name)
                    }
                }
            }
        }

        return AllocResult(
            locations = locations,
            spillSlots = spillPool.maxSlots,
            usedCalleeRegs64 = usedCallee64,
            usedCalleeRegs32 = usedCallee32,
            paramMoves = paramMoves,
        )
    }

    private fun pickBestReg64(free: List<X86Register64>, interval: LiveInterval): X86Register64? {
        if (free.isEmpty()) return null
        return if (interval.acrossCall) {
            free.sortedByDescending { it in calleeSaved64 }.first()
        } else {
            free.sortedBy { it in calleeSaved64 }.first()
        }
    }

    private fun pickBestReg32(free: List<X86Register32>, interval: LiveInterval): X86Register32? {
        if (free.isEmpty()) return null
        return if (interval.acrossCall) {
            free.sortedByDescending { it in calleeSaved32 }.first()
        } else {
            free.sortedBy { it in calleeSaved32 }.first()
        }
    }

    private fun tryEvict64(
        interval: LiveInterval,
        active: List<Pair<LiveInterval, X86Register64>>,
    ): Pair<LiveInterval, X86Register64>? {
        val currentCost = spillCost(interval)
        return active
            .filter { spillCost(it.first) < currentCost }
            .minByOrNull { spillCost(it.first) }
    }

    private fun tryEvict32(
        interval: LiveInterval,
        active: List<Pair<LiveInterval, X86Register32>>,
    ): Pair<LiveInterval, X86Register32>? {
        val currentCost = spillCost(interval)
        return active
            .filter { spillCost(it.first) < currentCost }
            .minByOrNull { spillCost(it.first) }
    }

    private fun tryEvictXmm(
        interval: LiveInterval,
        active: List<Pair<LiveInterval, X86Xmm>>,
    ): Pair<LiveInterval, X86Xmm>? {
        val currentCost = spillCost(interval)
        return active
            .filter { spillCost(it.first) < currentCost }
            .minByOrNull { spillCost(it.first) }
    }
}

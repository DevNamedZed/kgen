package org.kgen.target.x86.codegen

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.codegen.alloc.LiveInterval
import org.kgen.codegen.alloc.LivenessAnalysis
import org.kgen.target.x86.*

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
    val intervals: List<org.kgen.codegen.alloc.LiveInterval> = emptyList(),
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
    private val sharedParamSlots: Boolean = false,
) {

    // Map 32-bit callee-saved registers to their 64-bit counterparts
    // so the prologue saves the full register.
    private val calleeSaved32to64: Map<X86Register32, X86Register64> by lazy {
        val map = mutableMapOf<X86Register32, X86Register64>()
        for (r32 in calleeSaved32) {
            val enc = (r32 as X86Register).encoding
            val r64 = calleeSaved64.firstOrNull { (it as X86Register).encoding == enc }
            if (r64 != null) map[r32] = r64
        }
        map
    }

    fun allocate(): AllocResult {
        val intervals = LivenessAnalysis(fn).intervals()
        val hints = buildRegisterHints()
        val result = linearScan(intervals, hints)
        verifyNoOverlap(intervals, result)
        return result
    }

    private fun verifyNoOverlap(intervals: List<LiveInterval>, result: AllocResult) {
        // Check spill slot overlaps
        val spillsByOffset = mutableMapOf<Int, MutableList<LiveInterval>>()
        for (interval in intervals) {
            val location = result.locations[interval.name]
            if (location is Location.Spill) {
                spillsByOffset.getOrPut(location.offset) { mutableListOf() }.add(interval)
            }
        }
        for ((offset, occupants) in spillsByOffset) {
            val sorted = occupants.sortedBy { it.start }
            for (index in 0 until sorted.size - 1) {
                val current = sorted[index]
                val next = sorted[index + 1]
                if (current.end >= next.start) {
                    throw IllegalStateException(
                        "Spill slot overlap in ${fn.name} at offset $offset: " +
                                "${current.name}[${current.start}-${current.end}] overlaps " +
                                "${next.name}[${next.start}-${next.end}]"
                    )
                }
            }
        }

        // Check register overlaps — two values in the same register with overlapping lifetimes
        fun regKey(location: Location): Int? = when (location) {
            is Location.Reg32 -> (location.reg as X86Register).encoding
            is Location.Reg64 -> (location.reg as X86Register).encoding
            is Location.RegXmm -> (location.reg as X86Register).encoding + 100
            else -> null
        }
        val byRegister = mutableMapOf<Int, MutableList<LiveInterval>>()
        for (interval in intervals) {
            val location = result.locations[interval.name] ?: continue
            val key = regKey(location) ?: continue
            byRegister.getOrPut(key) { mutableListOf() }.add(interval)
        }
        for ((regId, occupants) in byRegister) {
            val sorted = occupants.sortedBy { it.start }
            for (index in 0 until sorted.size - 1) {
                val current = sorted[index]
                val next = sorted[index + 1]
                if (current.end >= next.start) {
                    val regName = occupants.firstNotNullOfOrNull { iv ->
                        when (val loc = result.locations[iv.name]) {
                            is Location.Reg32 -> loc.reg.toString()
                            is Location.Reg64 -> loc.reg.toString()
                            is Location.RegXmm -> loc.reg.toString()
                            else -> null
                        }
                    } ?: "reg$regId"
                    throw IllegalStateException(
                        "Register overlap in ${fn.name} at $regName: " +
                                "${current.name}[${current.start}-${current.end}] overlaps " +
                                "${next.name}[${next.start}-${next.end}]"
                    )
                }
            }
        }
    }

    /**
     * Build register hints from IR copy relationships.
     * Maps dest value name → source value name (prefer same register).
     */
    private fun buildRegisterHints(): Map<String, String> {
        val hints = mutableMapOf<String, String>()
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                // Binary ops: dest should prefer lhs register (x86 is destructive: dest = lhs op rhs)
                val lhsName = when (inst) {
                    is Add -> inst.lhs.name
                    is Sub -> inst.lhs.name
                    is Mul -> inst.lhs.name
                    is And -> inst.lhs.name
                    is Or -> inst.lhs.name
                    is Xor -> inst.lhs.name
                    is Shl -> inst.lhs.name
                    is LShr -> inst.lhs.name
                    is AShr -> inst.lhs.name
                    is FAdd -> inst.lhs.name
                    is FSub -> inst.lhs.name
                    is FMul -> inst.lhs.name
                    is FDiv -> inst.lhs.name
                    else -> null
                }
                if (lhsName != null) {
                    val destName = when (inst) {
                        is Add -> inst.dest.name
                        is Sub -> inst.dest.name
                        is Mul -> inst.dest.name
                        is And -> inst.dest.name
                        is Or -> inst.dest.name
                        is Xor -> inst.dest.name
                        is Shl -> inst.dest.name
                        is LShr -> inst.dest.name
                        is AShr -> inst.dest.name
                        is FAdd -> inst.dest.name
                        is FSub -> inst.dest.name
                        is FMul -> inst.dest.name
                        is FDiv -> inst.dest.name
                        else -> null
                    }
                    if (destName != null) hints[destName] = lhsName
                }

                // Copy-like: only same-width BitCast is a true register copy
                val copyPair: Pair<String, String>? = when (inst) {
                    is BitCast -> {
                        if (inst.value.type == inst.dest.type) inst.dest.name to inst.value.name else null
                    }
                    else -> null
                }
                if (copyPair != null) {
                    hints[copyPair.first] = copyPair.second
                }
            }
        }
        return hints
    }

    private val hasDiv: Boolean by lazy {
        fn.blocks.any { block ->
            block.instructions.any {
                it is SDiv || it is UDiv ||
                        it is SRem || it is URem
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

        fun allocateFresh(): Int = ++maxSlots

        /**
         * Acquire a slot that doesn't conflict with the given liveness range.
         * The codegen eagerly stores values to their spill slot at the definition
         * site, so a slot can only be reused if no existing reservation's range
         * overlaps with [start, end].
         */
        fun acquireSafe(
            start: Int,
            end: Int,
            reservations: List<Triple<Int, Int, Int>>,
        ): Int {
            val iterator = freeSlots.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val conflicts = reservations.any { (slot, resStart, resEnd) ->
                    slot == candidate && resEnd >= start && resStart <= end
                }
                if (!conflicts) {
                    iterator.remove()
                    return candidate
                }
            }
            return ++maxSlots
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

    private fun corresponding32(reg64: X86Register64): X86Register32? {
        val encoding = (reg64 as X86Register).encoding
        return availableRegs32.firstOrNull { (it as X86Register).encoding == encoding }
    }

    private fun corresponding64(reg32: X86Register32): X86Register64? {
        val encoding = (reg32 as X86Register).encoding
        return availableRegs64.firstOrNull { (it as X86Register).encoding == encoding }
    }

    private fun linearScan(intervals: List<LiveInterval>, hints: Map<String, String> = emptyMap()): AllocResult {
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

        data class SpillEntry(val slot: Int, val end: Int)
        val spilledIntervals = mutableMapOf<String, SpillEntry>()

        fun allocate64(reg: X86Register64) {
            free64.remove(reg)
            corresponding32(reg)?.let { free32.remove(it) }
        }

        fun allocate32(reg: X86Register32) {
            free32.remove(reg)
            corresponding64(reg)?.let { free64.remove(it) }
        }

        fun release64(reg: X86Register64) {
            free64.add(reg)
            val r32 = corresponding32(reg) ?: return
            if (active32.none { it.second == r32 }) {
                free32.add(r32)
            }
        }

        fun release32(reg: X86Register32) {
            free32.add(reg)
            val r64 = corresponding64(reg) ?: return
            if (active64.none { it.second == r64 }) {
                free64.add(r64)
            }
        }

        fun expireOld(currentStart: Int) {
            active64.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { release64(reg); true } else false
            }
            active32.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { release32(reg); true } else false
            }
            activeXmm.removeAll { (iv, reg) ->
                if (iv.end < currentStart) { freeXmm.add(reg); true } else false
            }
            val expired = spilledIntervals.entries.filter { (_, entry) ->
                entry.end < currentStart
            }
            for ((name, entry) in expired) {
                spilledIntervals.remove(name)
                spillPool.release(entry.slot)
            }
        }

        // Track all slot reservations by their full [start, end] range.
        // This prevents the pool from reusing a slot while another value
        // whose liveness overlaps has been eagerly stored there.
        val slotReservations = mutableListOf<Triple<Int, Int, Int>>() // (slot, start, end)

        fun spillToPool(interval: LiveInterval): Location.Spill {
            val reserveStart = if (interval.isPhi) { 1 } else { interval.start }
            val slot = spillPool.acquireSafe(reserveStart, interval.end, slotReservations)
            spilledIntervals[interval.name] = SpillEntry(slot, interval.end)
            slotReservations.add(Triple(slot, reserveStart, interval.end))
            return Location.Spill(spillPool.offsetFor(slot))
        }


        // Pre-assign parameter registers
        var gpIdx = gpParamOffset
        var xmmIdx = 0
        val paramMoves = mutableMapOf<String, Int>()
        for (param in fn.params) {
            val interval = intervals.firstOrNull { it.name == param.name } ?: continue
            if (isFloatType(param.type)) {
                val xmmSlot = if (sharedParamSlots) { gpIdx } else { xmmIdx }
                if (xmmSlot < paramXmm.size) {
                    if (interval.acrossCall) {
                        paramMoves[param.name] = xmmSlot
                    } else {
                        val reg = paramXmm[xmmSlot]
                        locations[param.name] = Location.RegXmm(reg)
                        freeXmm.remove(reg)
                        activeXmm.add(interval to reg)
                    }
                }
                if (sharedParamSlots) { gpIdx++ } else { xmmIdx++ }
            } else if (param.type == Type.I64 || param.type == Type.OpaquePointer || param.type is Type.Pointer) {
                if (gpIdx < paramRegs64.size) {
                    val reg = paramRegs64[gpIdx]
                    val needsMove = interval.acrossCall || reg !in availableRegs64
                    if (needsMove) {
                        paramMoves[param.name] = gpIdx
                    } else {
                        locations[param.name] = Location.Reg64(reg)
                        allocate64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    }
                } else {
                    val stackOffset = 0x10 + gpIdx * 8
                    locations[param.name] = Location.Spill(stackOffset)
                    System.err.println("[ALLOC] ${fn.name}: stack param ${param.name} (I64) at [RBP+0x${stackOffset.toString(16)}], gpIdx=$gpIdx")
                }
                gpIdx++
            } else {
                if (gpIdx < paramRegs32.size) {
                    val reg = paramRegs32[gpIdx]
                    val needsMove = interval.acrossCall || reg !in availableRegs32
                    if (needsMove) {
                        paramMoves[param.name] = gpIdx
                    } else {
                        locations[param.name] = Location.Reg32(reg)
                        allocate32(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) {
                            usedCallee32.add(reg)
                            calleeSaved32to64[reg]?.let { usedCallee64.add(it) }
                        }
                    }
                } else {
                    val stackOffset = 0x10 + gpIdx * 8
                    locations[param.name] = Location.Spill(stackOffset)
                    System.err.println("[ALLOC] ${fn.name}: stack param ${param.name} (I32) at [RBP+0x${stackOffset.toString(16)}], gpIdx=$gpIdx")
                }
                gpIdx++
            }
        }

        for (interval in intervals) {
            if (interval.name in locations) continue
            expireOld(interval.start)

            when {
                interval.type is Type.Struct -> {
                    val slotsNeeded = ((structSize(interval.type as Type.Struct) + 7) / 8).coerceAtLeast(1)
                    val firstSlot = if (slotsNeeded == 1) {
                        spillPool.acquireSafe(interval.start, interval.end, slotReservations)
                    } else {
                        spillPool.acquireContiguous(slotsNeeded)
                    }
                    spilledIntervals[interval.name] = SpillEntry(firstSlot, interval.end)
                    slotReservations.add(Triple(firstSlot, interval.start, interval.end))
                    locations[interval.name] = Location.Spill(spillPool.offsetFor(firstSlot))
                }
                isFloatType(interval.type) -> {
                    if (interval.acrossCall) {
                        // All XMM registers are caller-saved — must spill across calls
                        locations[interval.name] = spillToPool(interval)
                    } else if (freeXmm.isNotEmpty()) {
                        val reg = pickBestXmm(freeXmm, interval, hints, locations)
                        locations[interval.name] = Location.RegXmm(reg)
                        activeXmm.add(interval to reg)
                    } else {
                        val evicted = tryEvictXmm(interval, activeXmm)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            activeXmm.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv)
                            locations[interval.name] = Location.RegXmm(evictedReg)
                            activeXmm.add(interval to evictedReg)
                        } else {
                            locations[interval.name] = spillToPool(interval)
                        }
                    }
                }
                interval.type == Type.I64 || interval.type == Type.OpaquePointer || interval.type is Type.Pointer -> {
                    val reg = pickBestReg64(free64, interval, hints, locations)
                    if (reg != null) {
                        allocate64(reg)
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        // When the interval is live across a call, we can only place it
                        // in a callee-saved register — caller-saved would be destroyed.
                        // Filter eviction candidates accordingly; if no callee-saved
                        // holder can be evicted, the interval correctly spills.
                        val candidates = if (interval.acrossCall) {
                            active64.filter { it.second in calleeSaved64 }
                        } else {
                            active64
                        }
                        val evicted = tryEvict64(interval, candidates)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            active64.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv)
                            locations[interval.name] = Location.Reg64(evictedReg)
                            active64.add(interval to evictedReg)
                            if (evictedReg in calleeSaved64) usedCallee64.add(evictedReg)
                        } else {
                            locations[interval.name] = spillToPool(interval)
                        }
                    }
                }
                interval.type == Type.I32 || interval.type == Type.I16 || interval.type == Type.I8 || interval.type == Type.I1 -> {
                    val reg = pickBestReg32(free32, interval, hints, locations)
                    if (reg != null) {
                        allocate32(reg)
                        locations[interval.name] = Location.Reg32(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) {
                            usedCallee32.add(reg)
                            calleeSaved32to64[reg]?.let { usedCallee64.add(it) }
                        }
                    } else {
                        val candidates = if (interval.acrossCall) {
                            active32.filter { it.second in calleeSaved32 }
                        } else {
                            active32
                        }
                        val evicted = tryEvict32(interval, candidates)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            active32.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv)
                            locations[interval.name] = Location.Reg32(evictedReg)
                            active32.add(interval to evictedReg)
                            if (evictedReg in calleeSaved32) {
                                usedCallee32.add(evictedReg)
                                calleeSaved32to64[evictedReg]?.let { usedCallee64.add(it) }
                            }
                        } else {
                            locations[interval.name] = spillToPool(interval)
                        }
                    }
                }
                else -> {
                    val reg = pickBestReg64(free64, interval, hints, locations)
                    if (reg != null) {
                        allocate64(reg)
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        locations[interval.name] = spillToPool(interval)
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
            intervals = intervals,
        )
    }

    private fun pickBestReg64(
        free: List<X86Register64>, interval: LiveInterval,
        hints: Map<String, String> = emptyMap(), locations: Map<String, Location> = emptyMap(),
    ): X86Register64? {
        if (free.isEmpty()) return null
        // Check for register hint: if a related value already has a register, prefer it
        val hintSource = hints[interval.name]
        if (hintSource != null) {
            val hintLoc = locations[hintSource]
            if (hintLoc is Location.Reg64 && hintLoc.reg in free) {
                // For acrossCall values, only use the hint if it's callee-saved
                if (!interval.acrossCall || hintLoc.reg in calleeSaved64) {
                    return hintLoc.reg
                }
            }
        }
        return if (interval.acrossCall) {
            // Only use callee-saved registers for values live across calls — caller-saved would be clobbered
            free.filter { it in calleeSaved64 }.firstOrNull()
        } else {
            free.sortedBy { it in calleeSaved64 }.first()
        }
    }

    private fun pickBestReg32(
        free: List<X86Register32>, interval: LiveInterval,
        hints: Map<String, String> = emptyMap(), locations: Map<String, Location> = emptyMap(),
    ): X86Register32? {
        if (free.isEmpty()) return null
        val hintSource = hints[interval.name]
        if (hintSource != null) {
            val hintLoc = locations[hintSource]
            if (hintLoc is Location.Reg32 && hintLoc.reg in free) {
                if (!interval.acrossCall || hintLoc.reg in calleeSaved32) {
                    return hintLoc.reg
                }
            }
        }
        return if (interval.acrossCall) {
            free.filter { it in calleeSaved32 }.firstOrNull()
        } else {
            free.sortedBy { it in calleeSaved32 }.first()
        }
    }

    private fun pickBestXmm(
        free: MutableList<X86Xmm>, interval: LiveInterval,
        hints: Map<String, String> = emptyMap(), locations: Map<String, Location> = emptyMap(),
    ): X86Xmm {
        val hintSource = hints[interval.name]
        if (hintSource != null) {
            val hintLoc = locations[hintSource]
            if (hintLoc is Location.RegXmm && hintLoc.reg in free) {
                free.remove(hintLoc.reg)
                return hintLoc.reg
            }
        }
        return free.removeFirst()
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
package org.kgen.target.x86.codegen

import org.kgen.ir.*
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
        return linearScan(intervals, hints)
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
                    is Instruction.Add -> inst.lhs.name
                    is Instruction.Sub -> inst.lhs.name
                    is Instruction.Mul -> inst.lhs.name
                    is Instruction.And -> inst.lhs.name
                    is Instruction.Or -> inst.lhs.name
                    is Instruction.Xor -> inst.lhs.name
                    is Instruction.Shl -> inst.lhs.name
                    is Instruction.LShr -> inst.lhs.name
                    is Instruction.AShr -> inst.lhs.name
                    is Instruction.FAdd -> inst.lhs.name
                    is Instruction.FSub -> inst.lhs.name
                    is Instruction.FMul -> inst.lhs.name
                    is Instruction.FDiv -> inst.lhs.name
                    else -> null
                }
                if (lhsName != null) {
                    val destName = when (inst) {
                        is Instruction.Add -> inst.dest.name
                        is Instruction.Sub -> inst.dest.name
                        is Instruction.Mul -> inst.dest.name
                        is Instruction.And -> inst.dest.name
                        is Instruction.Or -> inst.dest.name
                        is Instruction.Xor -> inst.dest.name
                        is Instruction.Shl -> inst.dest.name
                        is Instruction.LShr -> inst.dest.name
                        is Instruction.AShr -> inst.dest.name
                        is Instruction.FAdd -> inst.dest.name
                        is Instruction.FSub -> inst.dest.name
                        is Instruction.FMul -> inst.dest.name
                        is Instruction.FDiv -> inst.dest.name
                        else -> null
                    }
                    if (destName != null) hints[destName] = lhsName
                }

                // Copy-like: bitcast, trunc, zext, sext — dest should prefer source register
                val copyPair: Pair<String, String>? = when (inst) {
                    is Instruction.BitCast -> inst.dest.name to inst.value.name
                    is Instruction.Trunc -> inst.dest.name to inst.operand.name
                    is Instruction.ZExt -> inst.dest.name to inst.value.name
                    is Instruction.SExt -> inst.dest.name to inst.value.name
                    is Instruction.IntTrunc -> inst.dest.name to inst.value.name
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
                        if (reg in calleeSaved32) {
                            usedCallee32.add(reg)
                            calleeSaved32to64[reg]?.let { usedCallee64.add(it) }
                        }
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
                    if (interval.acrossCall) {
                        // All XMM registers are caller-saved — must spill across calls
                        locations[interval.name] = spillToPool(interval.name)
                    } else if (freeXmm.isNotEmpty()) {
                        val reg = pickBestXmm(freeXmm, interval, hints, locations)
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
                    val reg = pickBestReg64(free64, interval, hints, locations)
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
                    val reg = pickBestReg32(free32, interval, hints, locations)
                    if (reg != null) {
                        free32.remove(reg)
                        locations[interval.name] = Location.Reg32(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) {
                            usedCallee32.add(reg)
                            calleeSaved32to64[reg]?.let { usedCallee64.add(it) }
                        }
                    } else {
                        val evicted = tryEvict32(interval, active32)
                        if (evicted != null) {
                            val (evictedIv, evictedReg) = evicted
                            active32.remove(evicted)
                            locations[evictedIv.name] = spillToPool(evictedIv.name)
                            locations[interval.name] = Location.Reg32(evictedReg)
                            active32.add(interval to evictedReg)
                            if (evictedReg in calleeSaved32) {
                                usedCallee32.add(evictedReg)
                                calleeSaved32to64[evictedReg]?.let { usedCallee64.add(it) }
                            }
                        } else {
                            locations[interval.name] = spillToPool(interval.name)
                        }
                    }
                }
                else -> {
                    val reg = pickBestReg64(free64, interval, hints, locations)
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

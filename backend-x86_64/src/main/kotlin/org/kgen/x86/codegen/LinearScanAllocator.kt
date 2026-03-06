package org.kgen.x86.codegen

import org.kgen.ir.*
import org.kgen.x86.*

data class LiveInterval(
    val name: String,
    val start: Int,
    val end: Int,
    val type: Type,
)

sealed interface Location {
    data class Reg64(val reg: X86Register64) : Location
    data class Reg32(val reg: X86Register32) : Location
    data class Spill(val offset: Int) : Location
}

data class AllocResult(
    val locations: Map<String, Location>,
    val spillSlots: Int,
    val usedCalleeRegs64: Set<X86Register64>,
    val usedCalleeRegs32: Set<X86Register32>,
)

class LinearScanAllocator(
    private val fn: IrFunction,
    private val availableRegs64: List<X86Register64>,
    private val availableRegs32: List<X86Register32>,
    private val calleeSaved64: Set<X86Register64>,
    private val calleeSaved32: Set<X86Register32>,
    private val paramRegs64: Array<X86Register64>,
    private val paramRegs32: Array<X86Register32>,
) {

    fun allocate(): AllocResult {
        val intervals = computeLiveIntervals()
        return linearScan(intervals)
    }

    private fun computeLiveIntervals(): List<LiveInterval> {
        val defs = mutableMapOf<String, Int>()
        val lastUses = mutableMapOf<String, Int>()
        val types = mutableMapOf<String, Type>()

        var instIdx = 0

        for (param in fn.params) {
            defs[param.name] = 0
            types[param.name] = param.type
        }

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                instIdx++
                val result = inst.result
                if (result != null) {
                    defs[result.name] = instIdx
                    types[result.name] = result.type
                }
                for (use in operandValues(inst)) {
                    val name = use.name
                    if (name in defs || name in types) {
                        lastUses[name] = instIdx
                    }
                }
            }
        }

        val intervals = mutableListOf<LiveInterval>()
        for ((name, start) in defs) {
            val end = lastUses[name] ?: start
            val type = types[name] ?: continue
            intervals.add(LiveInterval(name, start, end, type))
        }
        return intervals.sortedBy { it.start }
    }

    private fun operandValues(inst: Instruction): List<Value> {
        val values = mutableListOf<Value>()
        fun add(v: Value) {
            if (v is Parameter || v is InstructionRef) values.add(v)
        }
        when (inst) {
            is Instruction.Add -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Sub -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Mul -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.And -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Or -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Xor -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.ICmp -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.Ret -> inst.value?.let { add(it) }
            is Instruction.Call -> inst.args.forEach { add(it) }
            is Instruction.GetElementPtr -> { add(inst.ptr); inst.indices.forEach { add(it) } }
            is Instruction.Neg -> add(inst.operand)
            is Instruction.Shl -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.LShr -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.AShr -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.UDiv -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.SDiv -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.URem -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.SRem -> { add(inst.lhs); add(inst.rhs) }
            is Instruction.ZExt -> add(inst.value)
            is Instruction.SExt -> add(inst.value)
            is Instruction.Trunc -> add(inst.operand)
            is Instruction.Load -> add(inst.ptr)
            is Instruction.Store -> { add(inst.value); add(inst.ptr) }
            is Instruction.Select -> { add(inst.condition); add(inst.trueValue); add(inst.falseValue) }
            is Instruction.Phi -> inst.incoming.forEach { add(it.first) }
            else -> {}
        }
        return values
    }

    private fun linearScan(intervals: List<LiveInterval>): AllocResult {
        val locations = mutableMapOf<String, Location>()
        val active64 = mutableListOf<Pair<LiveInterval, X86Register64>>()
        val active32 = mutableListOf<Pair<LiveInterval, X86Register32>>()
        val free64 = availableRegs64.toMutableList()
        val free32 = availableRegs32.toMutableList()
        val usedCallee64 = mutableSetOf<X86Register64>()
        val usedCallee32 = mutableSetOf<X86Register32>()
        var nextSpillSlot = 0

        // Pre-assign parameter registers
        for ((i, param) in fn.params.withIndex()) {
            if (i >= paramRegs64.size) continue
            when (param.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val reg = paramRegs64[i]
                    val interval = intervals.firstOrNull { it.name == param.name }
                    if (interval != null) {
                        locations[param.name] = Location.Reg64(reg)
                        free64.remove(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    }
                }
                Type.I32, Type.I16, Type.I8, Type.I1 -> {
                    val reg = paramRegs32[i]
                    val interval = intervals.firstOrNull { it.name == param.name }
                    if (interval != null) {
                        locations[param.name] = Location.Reg32(reg)
                        free32.remove(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) usedCallee32.add(reg)
                    }
                }
                else -> {}
            }
        }

        for (interval in intervals) {
            if (interval.name in locations) continue

            // Expire old intervals
            active64.removeAll { (iv, reg) ->
                if (iv.end < interval.start) { free64.add(reg); true } else false
            }
            active32.removeAll { (iv, reg) ->
                if (iv.end < interval.start) { free32.add(reg); true } else false
            }

            when (interval.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    if (free64.isNotEmpty()) {
                        val reg = free64.removeFirst()
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        // Spill: pick the interval ending latest
                        val spillCandidate = active64.maxByOrNull { it.first.end }
                        if (spillCandidate != null && spillCandidate.first.end > interval.end) {
                            // Spill the one that ends later, give its register to current
                            val (spilledInterval, spilledReg) = spillCandidate
                            active64.remove(spillCandidate)
                            nextSpillSlot++
                            locations[spilledInterval.name] = Location.Spill(-nextSpillSlot * 8)
                            locations[interval.name] = Location.Reg64(spilledReg)
                            active64.add(interval to spilledReg)
                        } else {
                            nextSpillSlot++
                            locations[interval.name] = Location.Spill(-nextSpillSlot * 8)
                        }
                    }
                }
                Type.I32, Type.I16, Type.I8, Type.I1 -> {
                    if (free32.isNotEmpty()) {
                        val reg = free32.removeFirst()
                        locations[interval.name] = Location.Reg32(reg)
                        active32.add(interval to reg)
                        if (reg in calleeSaved32) usedCallee32.add(reg)
                    } else {
                        val spillCandidate = active32.maxByOrNull { it.first.end }
                        if (spillCandidate != null && spillCandidate.first.end > interval.end) {
                            val (spilledInterval, spilledReg) = spillCandidate
                            active32.remove(spillCandidate)
                            nextSpillSlot++
                            locations[spilledInterval.name] = Location.Spill(-nextSpillSlot * 8)
                            locations[interval.name] = Location.Reg32(spilledReg)
                            active32.add(interval to spilledReg)
                        } else {
                            nextSpillSlot++
                            locations[interval.name] = Location.Spill(-nextSpillSlot * 8)
                        }
                    }
                }
                else -> {
                    // Default to 64-bit for unknown types
                    if (free64.isNotEmpty()) {
                        val reg = free64.removeFirst()
                        locations[interval.name] = Location.Reg64(reg)
                        active64.add(interval to reg)
                        if (reg in calleeSaved64) usedCallee64.add(reg)
                    } else {
                        nextSpillSlot++
                        locations[interval.name] = Location.Spill(-nextSpillSlot * 8)
                    }
                }
            }
        }

        return AllocResult(
            locations = locations,
            spillSlots = nextSpillSlot,
            usedCalleeRegs64 = usedCallee64,
            usedCalleeRegs32 = usedCallee32,
        )
    }
}

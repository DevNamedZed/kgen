package org.kgen.backend.riscv.codegen

import org.kgen.backend.riscv.*
import org.kgen.ir.*
import org.kgen.ir.codegen.LiveInterval
import org.kgen.ir.codegen.LivenessAnalysis

sealed interface RiscVLocation {
    data class Reg(val reg: RiscVGpReg) : RiscVLocation
    data class Spill(val offset: Int) : RiscVLocation
}

data class RiscVAllocResult(
    val locations: Map<String, RiscVLocation>,
    val spillSlots: Int,
    val usedCalleeRegs: Set<RiscVGpReg>,
)

class RiscVAllocator(
    private val fn: IrFunction,
    private val availableRegs: List<RiscVGpReg>,
    private val calleeSaved: Set<RiscVGpReg>,
    private val paramRegs: Array<RiscVGpReg>,
) {

    fun allocate(): RiscVAllocResult {
        val intervals = LivenessAnalysis(fn).intervals()
        return linearScan(intervals)
    }

    private fun linearScan(intervals: List<LiveInterval>): RiscVAllocResult {
        val locations = mutableMapOf<String, RiscVLocation>()
        val freeRegs = availableRegs.toMutableList()
        data class ActiveEntry(val interval: LiveInterval, val reg: RiscVGpReg)
        val active = mutableListOf<ActiveEntry>()
        val usedCalleeRegs = mutableSetOf<RiscVGpReg>()
        var spillSlots = 0

        // Assign parameter registers
        var gpIdx = 0
        for (param in fn.params) {
            if (gpIdx < paramRegs.size) {
                val paramReg = paramRegs[gpIdx]
                freeRegs.remove(paramReg)
                locations[param.name] = RiscVLocation.Reg(paramReg)
                val interval = intervals.firstOrNull { it.name == param.name }
                if (interval != null && interval.end > 0) {
                    active.add(ActiveEntry(interval, paramReg))
                }
                gpIdx++
            } else {
                spillSlots++
                locations[param.name] = RiscVLocation.Spill(-spillSlots * 8)
            }
        }

        for (interval in intervals) {
            if (interval.name in locations) continue

            // Expire old intervals
            active.removeAll { (old, reg) ->
                if (old.end < interval.start) {
                    freeRegs.add(reg)
                    true
                } else false
            }

            // Prefer callee-saved if value lives across a call, caller-saved otherwise
            val reg = if (interval.acrossCall) {
                freeRegs.firstOrNull { it in calleeSaved } ?: freeRegs.firstOrNull()
            } else {
                freeRegs.firstOrNull { it !in calleeSaved } ?: freeRegs.firstOrNull()
            }

            if (reg != null) {
                freeRegs.remove(reg)
                locations[interval.name] = RiscVLocation.Reg(reg)
                active.add(ActiveEntry(interval, reg))
                if (reg in calleeSaved) usedCalleeRegs.add(reg)
            } else {
                spillSlots++
                locations[interval.name] = RiscVLocation.Spill(-spillSlots * 8)
            }
        }

        return RiscVAllocResult(locations, spillSlots, usedCalleeRegs)
    }
}

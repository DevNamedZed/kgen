package org.kgen.backend.arm64.codegen

import org.kgen.backend.arm64.*
import org.kgen.ir.*
import org.kgen.ir.codegen.LiveInterval
import org.kgen.ir.codegen.LivenessAnalysis

sealed interface Arm64Location {
    data class Reg64(val reg: Arm64Register) : Arm64Location
    data class Reg32(val reg: Arm64Register) : Arm64Location
    data class RegFp(val reg: Arm64Register) : Arm64Location // D or S register
    data class Spill(val offset: Int) : Arm64Location
}

data class Arm64AllocResult(
    val locations: Map<String, Arm64Location>,
    val spillSlots: Int,
    val usedCalleeRegs: Set<Arm64Register>,
)

class Arm64Allocator(
    private val fn: IrFunction,
    private val availableRegs64: List<Arm64Register>,
    private val availableRegs32: List<Arm64Register>,
    private val calleeSaved: Set<Arm64Register>,
    private val paramRegs64: Array<Arm64Register>,
    private val paramRegs32: Array<Arm64Register>,
    private val availableFpRegs: List<Arm64Register> = emptyList(),
    private val fpCalleeSaved: Set<Arm64Register> = emptySet(),
    private val fpParamRegs: Array<Arm64Register> = emptyArray(),
) {

    fun allocate(): Arm64AllocResult {
        val intervals = LivenessAnalysis(fn).intervals()
        return linearScan(intervals)
    }

    private enum class RegClass { GP64, GP32, FP }

    private fun linearScan(intervals: List<LiveInterval>): Arm64AllocResult {
        val locations = mutableMapOf<String, Arm64Location>()
        val freeRegs64 = availableRegs64.toMutableList()
        val freeRegs32 = availableRegs32.toMutableList()
        val freeFpRegs = availableFpRegs.toMutableList()
        data class ActiveEntry(val interval: LiveInterval, val reg: Arm64Register, val cls: RegClass)
        val active = mutableListOf<ActiveEntry>()
        val usedCalleeRegs = mutableSetOf<Arm64Register>()
        var spillSlots = 0

        // Assign parameter registers
        var gpIdx = 0
        var fpIdx = 0
        for (param in fn.params) {
            val cls = regClassFor(param.type)
            if (cls == RegClass.FP) {
                if (fpIdx < fpParamRegs.size) {
                    val paramReg = fpParamRegs[fpIdx]
                    freeFpRegs.remove(paramReg)
                    locations[param.name] = Arm64Location.RegFp(paramReg)
                    val interval = intervals.firstOrNull { it.name == param.name }
                    if (interval != null && interval.end > 0) {
                        active.add(ActiveEntry(interval, paramReg, RegClass.FP))
                    }
                    fpIdx++
                } else {
                    spillSlots++
                    locations[param.name] = Arm64Location.Spill(-spillSlots * 8)
                }
            } else {
                val is64 = cls == RegClass.GP64
                if (gpIdx < paramRegs64.size) {
                    val paramReg = if (is64) paramRegs64[gpIdx] else paramRegs32[gpIdx]
                    val availList = if (is64) freeRegs64 else freeRegs32
                    availList.remove(paramReg)
                    locations[param.name] = if (is64) Arm64Location.Reg64(paramReg) else Arm64Location.Reg32(paramReg)
                    val interval = intervals.firstOrNull { it.name == param.name }
                    if (interval != null && interval.end > 0) {
                        active.add(ActiveEntry(interval, paramReg, cls))
                    }
                    gpIdx++
                } else {
                    spillSlots++
                    locations[param.name] = Arm64Location.Spill(-spillSlots * 8)
                }
            }
        }

        for (interval in intervals) {
            if (interval.name in locations) continue

            // Expire old intervals
            active.removeAll { (old, reg, cls) ->
                if (old.end < interval.start) {
                    when (cls) {
                        RegClass.GP64 -> freeRegs64.add(reg)
                        RegClass.GP32 -> freeRegs32.add(reg)
                        RegClass.FP -> freeFpRegs.add(reg)
                    }
                    true
                } else false
            }

            val cls = regClassFor(interval.type)
            val allCalleeSaved = calleeSaved + fpCalleeSaved

            when (cls) {
                RegClass.FP -> {
                    val reg = if (interval.acrossCall) {
                        freeFpRegs.firstOrNull { it in fpCalleeSaved } ?: freeFpRegs.firstOrNull()
                    } else {
                        freeFpRegs.firstOrNull { it !in fpCalleeSaved } ?: freeFpRegs.firstOrNull()
                    }
                    if (reg != null) {
                        freeFpRegs.remove(reg)
                        locations[interval.name] = Arm64Location.RegFp(reg)
                        active.add(ActiveEntry(interval, reg, RegClass.FP))
                        if (reg in fpCalleeSaved) usedCalleeRegs.add(reg)
                    } else {
                        spillSlots++
                        locations[interval.name] = Arm64Location.Spill(-spillSlots * 8)
                    }
                }
                else -> {
                    val is64 = cls == RegClass.GP64
                    val freeList = if (is64) freeRegs64 else freeRegs32
                    val reg = if (interval.acrossCall) {
                        freeList.firstOrNull { it in calleeSaved } ?: freeList.firstOrNull()
                    } else {
                        freeList.firstOrNull { it !in calleeSaved } ?: freeList.firstOrNull()
                    }
                    if (reg != null) {
                        freeList.remove(reg)
                        locations[interval.name] = if (is64) Arm64Location.Reg64(reg) else Arm64Location.Reg32(reg)
                        active.add(ActiveEntry(interval, reg, cls))
                        if (reg in calleeSaved) usedCalleeRegs.add(reg)
                    } else {
                        spillSlots++
                        locations[interval.name] = Arm64Location.Spill(-spillSlots * 8)
                    }
                }
            }
        }

        return Arm64AllocResult(locations, spillSlots, usedCalleeRegs)
    }

    private fun regClassFor(type: Type): RegClass = when (type) {
        Type.F32, Type.F64 -> RegClass.FP
        Type.I64, Type.OpaquePointer -> RegClass.GP64
        is Type.Pointer -> RegClass.GP64
        else -> RegClass.GP32
    }

    private fun Type.is64Bit(): Boolean = when (this) {
        Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> true
        else -> false
    }
}

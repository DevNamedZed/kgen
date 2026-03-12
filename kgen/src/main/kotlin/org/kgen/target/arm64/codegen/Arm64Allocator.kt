package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.*
import org.kgen.ir.*
import org.kgen.codegen.alloc.*

sealed interface Arm64Location {
    data class Reg64(val reg: Arm64Register64) : Arm64Location
    data class Reg32(val reg: Arm64Register32) : Arm64Location
    data class RegFp(val reg: Arm64Reg) : Arm64Location // D or S register
    data class Spill(val offset: Int) : Arm64Location
}

data class Arm64AllocResult(
    val locations: Map<String, Arm64Location>,
    val spillSlots: Int,
    val usedCalleeRegs: Set<Arm64Reg>,
)

class Arm64Allocator(
    private val fn: IrFunction,
    private val availableRegs64: List<Arm64Register64>,
    private val availableRegs32: List<Arm64Register32>,
    private val calleeSaved: Set<Arm64Reg>,
    private val paramRegs64: List<Arm64Register64>,
    private val paramRegs32: List<Arm64Register32>,
    private val availableFpRegs: List<Arm64Reg> = emptyList(),
    private val fpCalleeSaved: Set<Arm64Reg> = emptySet(),
    private val fpParamRegs: List<Arm64Reg> = emptyList(),
) {

    fun allocate(): Arm64AllocResult {
        val intervals = LivenessAnalysis(fn).intervals()

        // Build physical register maps for GP and FP classes
        val gpClass = RegisterClass("GP", emptyList())
        val fpClass = RegisterClass("FP", emptyList())

        val gpPhysMap = mutableMapOf<Int, PhysicalRegister>()
        val fpPhysMap = mutableMapOf<Int, PhysicalRegister>()

        // First pass to get register lists
        val gpPhysList = availableRegs64.map { reg ->
            PhysicalRegister(reg.name(), reg.encoding(), gpClass).also { gpPhysMap[reg.encoding()] = it }
        }
        val fpPhysList = availableFpRegs.map { reg ->
            PhysicalRegister(reg.name(), reg.encoding(), fpClass).also { fpPhysMap[reg.encoding()] = it }
        }

        // Rebuild with correct class references
        val fullGpClass = RegisterClass("GP", gpPhysList.map {
            PhysicalRegister(it.name, it.encoding, RegisterClass("GP", emptyList()))
        })
        val fullFpClass = RegisterClass("FP", fpPhysList.map {
            PhysicalRegister(it.name, it.encoding, RegisterClass("FP", emptyList()))
        })

        gpPhysMap.clear()
        fpPhysMap.clear()
        val gpRegsFixed = availableRegs64.map { reg ->
            PhysicalRegister(reg.name(), reg.encoding(), fullGpClass).also { gpPhysMap[reg.encoding()] = it }
        }
        val fpRegsFixed = availableFpRegs.map { reg ->
            PhysicalRegister(reg.name(), reg.encoding(), fullFpClass).also { fpPhysMap[reg.encoding()] = it }
        }

        // Pre-assign parameters (ARM64 has independent GP/FP param counters)
        val locations = mutableMapOf<String, Arm64Location>()
        val usedCalleeRegs = mutableSetOf<Arm64Reg>()
        val takenGpEncodings = mutableSetOf<Int>()
        val takenFpEncodings = mutableSetOf<Int>()

        var gpIdx = 0
        var fpIdx = 0
        for (param in fn.params) {
            val cls = regClassFor(param.type)
            val interval = intervals.firstOrNull { it.name == param.name }
            val isLive = interval != null && interval.end > 0

            if (cls == RegClass.FP) {
                if (fpIdx < fpParamRegs.size) {
                    val paramReg = fpParamRegs[fpIdx]
                    locations[param.name] = Arm64Location.RegFp(paramReg)
                    if (isLive) takenFpEncodings.add(paramReg.encoding())
                    if (paramReg in fpCalleeSaved) usedCalleeRegs.add(paramReg)
                    fpIdx++
                } else {
                    val spillSlot = takenGpEncodings.size + takenFpEncodings.size + 1
                    locations[param.name] = Arm64Location.Spill(-spillSlot * 8)
                }
            } else {
                if (gpIdx < paramRegs64.size) {
                    val is64 = cls == RegClass.GP64
                    if (is64) {
                        val paramReg = paramRegs64[gpIdx]
                        locations[param.name] = Arm64Location.Reg64(paramReg)
                        if (isLive) takenGpEncodings.add(paramReg.encoding())
                        if (paramReg in calleeSaved) usedCalleeRegs.add(paramReg)
                    } else {
                        val paramReg = paramRegs32[gpIdx]
                        locations[param.name] = Arm64Location.Reg32(paramReg)
                        if (isLive) takenGpEncodings.add(paramReg.encoding())
                        if (paramReg in calleeSaved) usedCalleeRegs.add(paramReg)
                    }
                    gpIdx++
                } else {
                    val spillSlot = takenGpEncodings.size + takenFpEncodings.size + 1
                    locations[param.name] = Arm64Location.Spill(-spillSlot * 8)
                }
            }
        }

        // Build constraints with remaining (non-taken) registers
        val remainingGp = gpRegsFixed.filter { it.encoding !in takenGpEncodings }
        val remainingFp = fpRegsFixed.filter { it.encoding !in takenFpEncodings }

        val allCalleeSavedPhys = calleeSaved.mapNotNull { gpPhysMap[it.encoding()] }.toSet() +
            fpCalleeSaved.mapNotNull { fpPhysMap[it.encoding()] }.toSet()

        val constraints = RegisterConstraints(
            allocatable = remainingGp + remainingFp,
            reserved = emptySet(),
            calleeSaved = allCalleeSavedPhys,
            paramRegisters = emptyList(), // params already handled above
            returnRegisters = listOfNotNull(gpPhysMap[Arm64Register.X0.encoding()]),
        )

        // Filter intervals to only non-param values
        val remainingIntervals = intervals.filter { it.name !in locations }

        val assignment = LinearScanRegisterAllocator().allocate(fn, remainingIntervals, constraints)

        // Map shared allocator results back to ARM64 types
        for ((name, loc) in assignment.locations) {
            when (loc) {
                is ValueLocation.Register -> {
                    val interval = intervals.first { it.name == name }
                    val armReg = fromPhysical(loc.reg, interval.type)
                    locations[name] = armReg
                    val archReg = resolveArchReg(loc.reg, interval.type)
                    if (archReg in calleeSaved || archReg in fpCalleeSaved) {
                        usedCalleeRegs.add(archReg)
                    }
                }
                is ValueLocation.SpillSlot -> {
                    locations[name] = Arm64Location.Spill(loc.offset)
                }
            }
        }

        return Arm64AllocResult(locations, assignment.spillSlots, usedCalleeRegs)
    }

    private enum class RegClass { GP64, GP32, FP }

    private fun regClassFor(type: Type): RegClass = when (type) {
        Type.F32, Type.F64 -> RegClass.FP
        Type.I64, Type.OpaquePointer -> RegClass.GP64
        is Type.Pointer -> RegClass.GP64
        else -> RegClass.GP32
    }

    private fun fromPhysical(reg: PhysicalRegister, type: Type): Arm64Location {
        return when (regClassFor(type)) {
            RegClass.FP -> Arm64Location.RegFp(Arm64Register.byEncodingD(reg.encoding))
            RegClass.GP64 -> Arm64Location.Reg64(Arm64Register.byEncoding64(reg.encoding))
            RegClass.GP32 -> Arm64Location.Reg32(Arm64Register.byEncoding32(reg.encoding))
        }
    }

    private fun resolveArchReg(reg: PhysicalRegister, type: Type): Arm64Reg {
        return when (regClassFor(type)) {
            RegClass.FP -> Arm64Register.byEncodingD(reg.encoding)
            RegClass.GP64 -> Arm64Register.byEncoding64(reg.encoding)
            RegClass.GP32 -> Arm64Register.byEncoding32(reg.encoding)
        }
    }
}

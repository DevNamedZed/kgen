package org.kgen.target.riscv.codegen

import org.kgen.target.riscv.*
import org.kgen.ir.*
import org.kgen.codegen.alloc.*

sealed interface RiscVLocation {
    data class Reg(val reg: RiscVGpReg) : RiscVLocation
    data class RegFp(val reg: RiscVFpReg) : RiscVLocation
    data class Spill(val offset: Int) : RiscVLocation
}

data class RiscVAllocResult(
    val locations: Map<String, RiscVLocation>,
    val spillSlots: Int,
    val usedCalleeRegs: Set<RiscVGpReg>,
    val usedCalleeFpRegs: Set<RiscVFpReg> = emptySet(),
)

class RiscVAllocator(
    private val fn: IrFunction,
    private val availableRegs: List<RiscVGpReg>,
    private val calleeSaved: Set<RiscVGpReg>,
    private val paramRegs: Array<RiscVGpReg>,
    private val availableFpRegs: List<RiscVFpReg> = emptyList(),
    private val fpCalleeSaved: Set<RiscVFpReg> = emptySet(),
    private val fpParamRegs: Array<RiscVFpReg> = emptyArray(),
) {
    fun allocate(): RiscVAllocResult {
        // Build physical register mapping — GP registers
        val physMap = mutableMapOf<Int, PhysicalRegister>()
        val fullGpClass = RegisterClass("GP", emptyList())
        val physRegsFixed = availableRegs.map { reg ->
            val phys = PhysicalRegister(reg.name, reg.encoding, fullGpClass)
            physMap[reg.encoding] = phys
            phys
        }

        // Build FP register mapping if FP registers are provided
        val fpPhysMap = mutableMapOf<Int, PhysicalRegister>()
        val fpClass = RegisterClass("FP", emptyList())
        val fpPhysRegs = availableFpRegs.map { reg ->
            val phys = PhysicalRegister(reg.name, reg.encoding + 100, fpClass)
            fpPhysMap[reg.encoding] = phys
            phys
        }

        // Determine which values need FP vs GP register class
        val fpValues = mutableSetOf<String>()
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val result = inst.result ?: continue
                if (result.type == Type.F32 || result.type == Type.F64) {
                    fpValues.add(result.name)
                }
            }
        }
        for (p in fn.params) {
            if (p.type == Type.F32 || p.type == Type.F64) {
                fpValues.add(p.name)
            }
        }

        // Use combined allocation with all registers
        val allAllocatable = physRegsFixed + fpPhysRegs
        val allCalleeSaved = calleeSaved.mapNotNull { physMap[it.encoding] }.toSet() +
            fpCalleeSaved.mapNotNull { fpPhysMap[it.encoding] }.toSet()
        val allParamRegs = paramRegs.mapNotNull { physMap[it.encoding] } +
            fpParamRegs.mapNotNull { fpPhysMap[it.encoding] }
        val allReturnRegs = listOfNotNull(physMap[X10.encoding]) +
            listOfNotNull(fpPhysMap[F10.encoding])

        val constraints = RegisterConstraints(
            allocatable = allAllocatable,
            reserved = emptySet(),
            calleeSaved = allCalleeSaved,
            paramRegisters = allParamRegs,
            returnRegisters = allReturnRegs,
        )

        val intervals = LivenessAnalysis(fn).intervals()
        val assignment = LinearScanRegisterAllocator().allocate(fn, intervals, constraints)

        // Map back to RISC-V types
        val locations = mutableMapOf<String, RiscVLocation>()
        val usedCalleeRegs = mutableSetOf<RiscVGpReg>()
        val usedCalleeFpRegs = mutableSetOf<RiscVFpReg>()

        for ((name, loc) in assignment.locations) {
            locations[name] = when (loc) {
                is ValueLocation.Register -> {
                    val fpReg = fromPhysicalFp(loc.reg)
                    if (fpReg != null) {
                        if (fpReg in fpCalleeSaved) usedCalleeFpRegs.add(fpReg)
                        RiscVLocation.RegFp(fpReg)
                    } else {
                        val reg = fromPhysical(loc.reg)
                        if (reg in calleeSaved) usedCalleeRegs.add(reg)
                        RiscVLocation.Reg(reg)
                    }
                }
                is ValueLocation.SpillSlot -> RiscVLocation.Spill(loc.offset)
            }
        }

        // Handle paramMoves — params that the shared allocator deferred
        for ((name, paramIdx) in assignment.paramMoves) {
            if (name !in locations) {
                if (name in fpValues) {
                    val freeCalleeFp = fpCalleeSaved.filter { reg ->
                        locations.values.none { it is RiscVLocation.RegFp && it.reg == reg }
                    }
                    if (freeCalleeFp.isNotEmpty()) {
                        val reg = freeCalleeFp.first()
                        locations[name] = RiscVLocation.RegFp(reg)
                        usedCalleeFpRegs.add(reg)
                    } else {
                        val spillSlot = assignment.spillSlots + 1
                        locations[name] = RiscVLocation.Spill(-spillSlot * 8)
                    }
                } else {
                    val freeCallee = calleeSaved.filter { reg ->
                        locations.values.none { it is RiscVLocation.Reg && it.reg == reg }
                    }
                    if (freeCallee.isNotEmpty()) {
                        val reg = freeCallee.first()
                        locations[name] = RiscVLocation.Reg(reg)
                        usedCalleeRegs.add(reg)
                    } else {
                        val spillSlot = assignment.spillSlots + 1
                        locations[name] = RiscVLocation.Spill(-spillSlot * 8)
                    }
                }
            }
        }

        return RiscVAllocResult(locations, assignment.spillSlots, usedCalleeRegs, usedCalleeFpRegs)
    }

    private fun fromPhysical(reg: PhysicalRegister): RiscVGpReg {
        return availableRegs.first { it.encoding == reg.encoding }
    }

    private fun fromPhysicalFp(reg: PhysicalRegister): RiscVFpReg? {
        if (reg.encoding < 100) return null
        val fpEncoding = reg.encoding - 100
        return availableFpRegs.firstOrNull { it.encoding == fpEncoding }
    }
}

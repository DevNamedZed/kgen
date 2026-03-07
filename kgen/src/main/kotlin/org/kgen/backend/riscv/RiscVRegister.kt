package org.kgen.backend.riscv

/**
 * RISC-V register file.
 *
 * RV32/RV64 has 32 integer registers (x0-x31) and 32 floating-point registers (f0-f31).
 * x0 is hardwired to zero. The ABI names (a0, sp, ra, etc.) are provided as aliases.
 */
sealed class RiscVRegister(val name: String, val encoding: Int) {
    override fun toString(): String = name
}

sealed class RiscVGpReg(name: String, encoding: Int) : RiscVRegister(name, encoding)
sealed class RiscVFpReg(name: String, encoding: Int) : RiscVRegister(name, encoding)

// Integer registers x0-x31 with ABI names
object X0  : RiscVGpReg("zero", 0)
object X1  : RiscVGpReg("ra", 1)
object X2  : RiscVGpReg("sp", 2)
object X3  : RiscVGpReg("gp", 3)
object X4  : RiscVGpReg("tp", 4)
object X5  : RiscVGpReg("t0", 5)
object X6  : RiscVGpReg("t1", 6)
object X7  : RiscVGpReg("t2", 7)
object X8  : RiscVGpReg("s0", 8)    // also fp (frame pointer)
object X9  : RiscVGpReg("s1", 9)
object X10 : RiscVGpReg("a0", 10)
object X11 : RiscVGpReg("a1", 11)
object X12 : RiscVGpReg("a2", 12)
object X13 : RiscVGpReg("a3", 13)
object X14 : RiscVGpReg("a4", 14)
object X15 : RiscVGpReg("a5", 15)
object X16 : RiscVGpReg("a6", 16)
object X17 : RiscVGpReg("a7", 17)
object X18 : RiscVGpReg("s2", 18)
object X19 : RiscVGpReg("s3", 19)
object X20 : RiscVGpReg("s4", 20)
object X21 : RiscVGpReg("s5", 21)
object X22 : RiscVGpReg("s6", 22)
object X23 : RiscVGpReg("s7", 23)
object X24 : RiscVGpReg("s8", 24)
object X25 : RiscVGpReg("s9", 25)
object X26 : RiscVGpReg("s10", 26)
object X27 : RiscVGpReg("s11", 27)
object X28 : RiscVGpReg("t3", 28)
object X29 : RiscVGpReg("t4", 29)
object X30 : RiscVGpReg("t5", 30)
object X31 : RiscVGpReg("t6", 31)

// Floating-point registers f0-f31
object F0  : RiscVFpReg("ft0", 0)
object F1  : RiscVFpReg("ft1", 1)
object F2  : RiscVFpReg("ft2", 2)
object F3  : RiscVFpReg("ft3", 3)
object F4  : RiscVFpReg("ft4", 4)
object F5  : RiscVFpReg("ft5", 5)
object F6  : RiscVFpReg("ft6", 6)
object F7  : RiscVFpReg("ft7", 7)
object F8  : RiscVFpReg("fs0", 8)
object F9  : RiscVFpReg("fs1", 9)
object F10 : RiscVFpReg("fa0", 10)
object F11 : RiscVFpReg("fa1", 11)
object F12 : RiscVFpReg("fa2", 12)
object F13 : RiscVFpReg("fa3", 13)
object F14 : RiscVFpReg("fa4", 14)
object F15 : RiscVFpReg("fa5", 15)
object F16 : RiscVFpReg("fa6", 16)
object F17 : RiscVFpReg("fa7", 17)
object F18 : RiscVFpReg("fs2", 18)
object F19 : RiscVFpReg("fs3", 19)
object F20 : RiscVFpReg("fs4", 20)
object F21 : RiscVFpReg("fs5", 21)
object F22 : RiscVFpReg("fs6", 22)
object F23 : RiscVFpReg("fs7", 23)
object F24 : RiscVFpReg("fs8", 24)
object F25 : RiscVFpReg("fs9", 25)
object F26 : RiscVFpReg("fs10", 26)
object F27 : RiscVFpReg("fs11", 27)
object F28 : RiscVFpReg("ft8", 28)
object F29 : RiscVFpReg("ft9", 29)
object F30 : RiscVFpReg("ft10", 30)
object F31 : RiscVFpReg("ft11", 31)

// Internal sentinel FP registers for encoding sub-operations (rs2 field)
internal object RiscVFpReg0 : RiscVFpReg("_fp0", 0)
internal object RiscVFpReg1 : RiscVFpReg("_fp1", 1)

// Convenience ABI aliases
val ZERO = X0
val RA = X1
val SP = X2
val GP = X3
val TP = X4
val FP = X8  // frame pointer = s0

// Lookup tables
object RiscVRegisters {
    val gpRegs: List<RiscVGpReg> = listOf(
        X0, X1, X2, X3, X4, X5, X6, X7, X8, X9,
        X10, X11, X12, X13, X14, X15, X16, X17, X18, X19,
        X20, X21, X22, X23, X24, X25, X26, X27, X28, X29, X30, X31,
    )

    val fpRegs: List<RiscVFpReg> = listOf(
        F0, F1, F2, F3, F4, F5, F6, F7, F8, F9,
        F10, F11, F12, F13, F14, F15, F16, F17, F18, F19,
        F20, F21, F22, F23, F24, F25, F26, F27, F28, F29, F30, F31,
    )

    fun gpByEncoding(enc: Int): RiscVGpReg = gpRegs[enc]
    fun fpByEncoding(enc: Int): RiscVFpReg = fpRegs[enc]

    // ABI categories
    val callerSaved = listOf(X1, X5, X6, X7, X10, X11, X12, X13, X14, X15, X16, X17, X28, X29, X30, X31)
    val calleeSaved = listOf(X8, X9, X18, X19, X20, X21, X22, X23, X24, X25, X26, X27)
    val argRegs = listOf(X10, X11, X12, X13, X14, X15, X16, X17)
    val fpCallerSaved = listOf(F0, F1, F2, F3, F4, F5, F6, F7, F10, F11, F12, F13, F14, F15, F16, F17, F28, F29, F30, F31)
    val fpCalleeSaved = listOf(F8, F9, F18, F19, F20, F21, F22, F23, F24, F25, F26, F27)
    val fpArgRegs = listOf(F10, F11, F12, F13, F14, F15, F16, F17)
}

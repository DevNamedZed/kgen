package org.kgen.x86

// Operand type hierarchy for x86-64 assembler
// These types enable overload resolution — the compiler picks the right
// instruction encoding based on operand types.

// Base sealed interfaces for register sizes
sealed interface X86Operand8
sealed interface X86Operand16
sealed interface X86Operand32
sealed interface X86Operand64

// Register types (subtypes of operands so reg-reg forms work with r/m overloads)
sealed interface X86Register8 : X86Operand8
sealed interface X86Register16 : X86Operand16
sealed interface X86Register32 : X86Operand32
sealed interface X86Register64 : X86Operand64

// Vector register types
sealed interface X86Xmm
sealed interface X86Ymm
sealed interface X86Zmm
sealed interface X86Mm

// Special register types
sealed interface X86SegReg
sealed interface X86ControlReg
sealed interface X86DebugReg
sealed interface X86FpuReg
sealed interface X86MaskReg

// Memory operand — implements all operand sizes (width selected by instruction context)
class X86Memory internal constructor(
    internal val base: Int,
    internal val index: Int,
    internal val scale: Int,
    internal val displacement: Long,
    internal val ripRelative: Boolean,
    internal val label: String?,
) : X86Operand8, X86Operand16, X86Operand32, X86Operand64 {

    companion object {
        @JvmStatic fun base(reg: X86Register64): Builder = Builder(reg)
        @JvmStatic fun ripRelative(label: String): X86Memory = X86Memory(-1, -1, 0, 0, true, label)
        @JvmStatic fun absolute(addr: Long): X86Memory = X86Memory(-1, -1, 0, addr, false, null)
    }

    class Builder internal constructor(private val baseReg: X86Register64) {
        private var index: Int = -1
        private var scale: Int = 0
        private var disp: Long = 0

        fun index(reg: X86Register64, scale: Int = 1): Builder {
            this.index = (reg as X86Register).encoding
            this.scale = scale
            return this
        }

        fun offset(disp: Int): X86Memory {
            this.disp = disp.toLong()
            return build()
        }

        fun offset(disp: Long): X86Memory {
            this.disp = disp
            return build()
        }

        fun build(): X86Memory = X86Memory(
            (baseReg as X86Register).encoding, index, scale, disp, false, null
        )
    }
}

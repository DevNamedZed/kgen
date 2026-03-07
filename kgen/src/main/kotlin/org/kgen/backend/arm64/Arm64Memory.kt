package org.kgen.backend.arm64

/**
 * ARM64 memory addressing modes.
 *
 * ARM64 supports several addressing modes:
 * - Base register: [Xn]
 * - Base + offset: [Xn, #imm]
 * - Pre-indexed: [Xn, #imm]! (updates base before access)
 * - Post-indexed: [Xn], #imm (updates base after access)
 * - Base + register: [Xn, Xm]
 * - Base + extended register: [Xn, Wm, SXTW #shift]
 * - PC-relative (for ADRP/ADR/LDR literal)
 */
class Arm64Memory private constructor(
    internal val base: Int,
    internal val offset: Long,
    internal val indexReg: Int,
    internal val shift: Int,
    internal val mode: AddressMode,
) {
    enum class AddressMode {
        BASE_OFFSET,
        PRE_INDEX,
        POST_INDEX,
        BASE_REGISTER,
    }

    companion object {
        @JvmStatic fun base(reg: Arm64Register64): Builder = Builder((reg as Arm64Register).encoding)

        @JvmStatic fun offset(reg: Arm64Register64, imm: Int): Arm64Memory =
            Arm64Memory((reg as Arm64Register).encoding, imm.toLong(), -1, 0, AddressMode.BASE_OFFSET)

        @JvmStatic fun offset(reg: Arm64Register64, imm: Long): Arm64Memory =
            Arm64Memory((reg as Arm64Register).encoding, imm, -1, 0, AddressMode.BASE_OFFSET)

        @JvmStatic fun preIndex(reg: Arm64Register64, imm: Int): Arm64Memory =
            Arm64Memory((reg as Arm64Register).encoding, imm.toLong(), -1, 0, AddressMode.PRE_INDEX)

        @JvmStatic fun postIndex(reg: Arm64Register64, imm: Int): Arm64Memory =
            Arm64Memory((reg as Arm64Register).encoding, imm.toLong(), -1, 0, AddressMode.POST_INDEX)
    }

    class Builder internal constructor(private val baseEnc: Int) {
        fun offset(imm: Int): Arm64Memory =
            Arm64Memory(baseEnc, imm.toLong(), -1, 0, AddressMode.BASE_OFFSET)

        fun build(): Arm64Memory =
            Arm64Memory(baseEnc, 0, -1, 0, AddressMode.BASE_OFFSET)
    }
}

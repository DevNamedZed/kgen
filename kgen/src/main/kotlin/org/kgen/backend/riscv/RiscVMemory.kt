package org.kgen.backend.riscv

/**
 * RISC-V memory operand: base register + 12-bit signed immediate offset.
 * Used for load/store instructions: `lw rd, offset(rs1)`.
 */
data class RiscVMemory(val base: RiscVGpReg, val offset: Int = 0) {
    init {
        require(offset in -2048..2047) { "Offset must fit in 12-bit signed immediate: $offset" }
    }

    override fun toString(): String = "$offset($base)"
}

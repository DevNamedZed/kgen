package org.kgen.ir.instructions

import org.kgen.ir.IrCategory

sealed interface ComputeInstruction : Instruction {
    override val category get() = IrCategory.COMPUTE
}

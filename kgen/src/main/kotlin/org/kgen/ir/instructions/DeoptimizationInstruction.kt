package org.kgen.ir.instructions

import org.kgen.ir.IrCategory

sealed interface DeoptimizationInstruction : Instruction {
    override val category get() = IrCategory.DEOPTIMIZATION
}

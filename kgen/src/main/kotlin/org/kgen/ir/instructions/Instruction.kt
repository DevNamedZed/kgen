package org.kgen.ir.instructions

import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface Instruction {
    val result: Value?
    val category: IrCategory
}

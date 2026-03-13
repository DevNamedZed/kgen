package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface AggregateInstruction : Instruction {
    override val category get() = IrCategory.AGGREGATE
}

data class ExtractValue(
    val dest: InstructionRef,
    val aggregate: Value,
    val indices: List<Int>,
) : AggregateInstruction {
    override val result get() = dest
}

data class InsertValue(
    val dest: InstructionRef,
    val aggregate: Value,
    val element: Value,
    val indices: List<Int>,
) : AggregateInstruction {
    override val result get() = dest
}

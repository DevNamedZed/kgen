package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface SsaInstruction : Instruction {
    override val category get() = IrCategory.SSA
}

data class Phi(
    val dest: InstructionRef,
    val incoming: List<Pair<Value, String>>,
) : SsaInstruction {
    override val result get() = dest
}

data class Select(
    val dest: InstructionRef,
    val condition: Value,
    val trueValue: Value,
    val falseValue: Value,
) : SsaInstruction {
    override val result get() = dest
}

data class Freeze(
    val dest: InstructionRef,
    val value: Value,
) : SsaInstruction {
    override val result get() = dest
}

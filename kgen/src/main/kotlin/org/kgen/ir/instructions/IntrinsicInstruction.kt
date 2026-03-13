package org.kgen.ir.instructions

import org.kgen.ir.AsmDialect
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface IntrinsicInstruction : Instruction {
    override val category get() = IrCategory.INTRINSIC
}

data class Intrinsic(
    val dest: InstructionRef?,
    val name: String,
    val args: List<Value>,
    val returnType: Type,
) : IntrinsicInstruction {
    override val result get() = dest
}

data class InlineAsm(
    val dest: InstructionRef?,
    val assembly: String,
    val constraints: String,
    val sideEffects: Boolean = true,
    val alignStack: Boolean = false,
    val dialect: AsmDialect = AsmDialect.ATT,
    val args: List<Value> = emptyList(),
    val returnType: Type = Type.Void,
) : IntrinsicInstruction {
    override val result get() = dest
}

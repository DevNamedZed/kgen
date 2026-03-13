package org.kgen.ir.instructions

import org.kgen.ir.CallingConvention
import org.kgen.ir.FnAttribute
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.TailCallKind
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface CallInstruction : Instruction {
    override val category get() = IrCategory.CALL
    val function: Value
    val args: List<Value>
    val returnType: Type
}

data class Call(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val callingConv: CallingConvention = CallingConvention.C,
    val tailCall: TailCallKind = TailCallKind.NONE,
    val attributes: Set<FnAttribute> = emptySet(),
) : CallInstruction {
    override val result get() = dest
}

data class Invoke(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val normalDest: String,
    val unwindDest: String,
    val callingConv: CallingConvention = CallingConvention.C,
) : CallInstruction {
    override val result get() = dest
}

data class CallBr(
    val dest: InstructionRef?,
    override val function: Value,
    override val args: List<Value>,
    override val returnType: Type,
    val fallthrough: String,
    val indirectDests: List<String>,
) : CallInstruction {
    override val result get() = dest
}


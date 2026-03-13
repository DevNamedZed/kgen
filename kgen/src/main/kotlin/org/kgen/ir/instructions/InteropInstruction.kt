package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.ManagedCallDirection
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface InteropInstruction : Instruction {
    override val category get() = IrCategory.INTEROP
}

data class Pin(
    val dest: InstructionRef,
    val ref: Value,
) : InteropInstruction {
    override val result get() = dest
}

data class Unpin(
    val ref: Value,
) : InteropInstruction {
    override val result: Value? get() = null
}

data class InteriorPtr(
    val dest: InstructionRef,
    val ref: Value,
    val index: Value,
    val pointeeType: Type,
) : InteropInstruction {
    override val result get() = dest
}

data class ManagedCall(
    val dest: InstructionRef?,
    val function: Value,
    val args: List<Value>,
    val returnType: Type,
    val direction: ManagedCallDirection,
) : InteropInstruction {
    override val result get() = dest
}

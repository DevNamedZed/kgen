package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface BitwiseInstruction : Instruction {
    override val category get() = IrCategory.BITWISE
}

data class And(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Or(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Xor(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Not(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Shl(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val nuw: Boolean = false,
    val nsw: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
}

data class LShr(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
}

data class AShr(
    val dest: InstructionRef,
    val lhs: Value,
    val rhs: Value,
    val exact: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
}

data class RotateLeft(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class RotateRight(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Ctlz(
    val dest: InstructionRef,
    val operand: Value,
    val isZeroPoison: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Cttz(
    val dest: InstructionRef,
    val operand: Value,
    val isZeroPoison: Boolean = false,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Ctpop(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class BSwap(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class BitReverse(
    val dest: InstructionRef,
    val operand: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Rotl(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

data class Rotr(
    val dest: InstructionRef,
    val value: Value,
    val amount: Value,
) : BitwiseInstruction {
    override val result get() = dest
}

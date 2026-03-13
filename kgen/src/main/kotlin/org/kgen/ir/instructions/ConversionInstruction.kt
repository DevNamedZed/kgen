package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface ConversionInstruction : Instruction {
    override val category get() = IrCategory.CONVERSION
}

data class IntTrunc(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class ZExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class SExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class FPTrunc(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class FPExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class FPToUI(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class FPToSI(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class UIToFP(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class SIToFP(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class PtrToInt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class IntToPtr(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class BitCast(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

data class AddrSpaceCast(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
}

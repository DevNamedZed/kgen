package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.VectorReduceOp

sealed interface VectorInstruction : Instruction {
    override val category get() = IrCategory.VECTOR
}

data class ExtractElement(
    val dest: InstructionRef,
    val vector: Value,
    val index: Value,
) : VectorInstruction {
    override val result get() = dest
}

data class InsertElement(
    val dest: InstructionRef,
    val vector: Value,
    val element: Value,
    val index: Value,
) : VectorInstruction {
    override val result get() = dest
}

data class ShuffleVector(
    val dest: InstructionRef,
    val v1: Value,
    val v2: Value,
    val mask: List<Int>,
) : VectorInstruction {
    override val result get() = dest
}

data class Splat(
    val dest: InstructionRef,
    val scalar: Value,
    val vectorType: Type.Vector,
) : VectorInstruction {
    override val result get() = dest
}

data class VectorReduce(
    val dest: InstructionRef,
    val op: VectorReduceOp,
    val vector: Value,
) : VectorInstruction {
    override val result get() = dest
}

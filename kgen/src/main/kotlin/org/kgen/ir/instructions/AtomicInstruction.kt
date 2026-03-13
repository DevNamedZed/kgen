package org.kgen.ir.instructions

import org.kgen.ir.AtomicOrdering
import org.kgen.ir.AtomicRMWOp
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface AtomicInstruction : Instruction {
    override val category get() = IrCategory.ATOMIC
}

data class Fence(
    val ordering: AtomicOrdering,
    val syncScope: String? = null,
) : AtomicInstruction {
    override val result: Value? get() = null
}

data class CmpXchg(
    val dest: InstructionRef,
    val ptr: Value,
    val cmp: Value,
    val new: Value,
    val successOrdering: AtomicOrdering,
    val failureOrdering: AtomicOrdering,
    val weak: Boolean = false,
    val volatile: Boolean = false,
) : AtomicInstruction {
    override val result get() = dest
}

data class AtomicRMW(
    val dest: InstructionRef,
    val op: AtomicRMWOp,
    val ptr: Value,
    val value: Value,
    val ordering: AtomicOrdering,
    val volatile: Boolean = false,
) : AtomicInstruction {
    override val result get() = dest
}

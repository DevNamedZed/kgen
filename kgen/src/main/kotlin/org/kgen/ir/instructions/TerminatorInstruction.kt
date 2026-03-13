package org.kgen.ir.instructions

import org.kgen.ir.Constant
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface TerminatorInstruction : Instruction {
    override val category get() = IrCategory.TERMINATOR
}

data class Ret(
    val value: Value?,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class Br(
    val target: String,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class CondBr(
    val condition: Value,
    val trueTarget: String,
    val falseTarget: String,
    val trueWeight: Long = 0,
    val falseWeight: Long = 0,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class Switch(
    val value: Value,
    val defaultTarget: String,
    val cases: List<Pair<Constant, String>>,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class IndirectBr(
    val address: Value,
    val targets: List<String>,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class Unreachable(
    val dummy: Unit = Unit,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class Trap(
    val dummy: Unit = Unit,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

data class DebugTrap(
    val successor: String? = null,
) : TerminatorInstruction {
    override val result: Value? get() = null
}

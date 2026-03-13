package org.kgen.ir.instructions

import org.kgen.ir.Constant
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface DebugInstruction : Instruction {
    override val category get() = IrCategory.DEBUG
}

data class DebugLoc(
    val line: Int,
    val col: Int,
    val scope: String,
    val inlinedAt: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
}

data class DebugValue(
    val variable: String,
    val value: Value,
    val expression: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
}

data class DebugDeclare(
    val variable: String,
    val address: Value,
    val expression: String? = null,
) : DebugInstruction {
    override val result: Value? get() = null
}

data class Assume(
    val condition: Value,
) : DebugInstruction {
    override val result: Value? get() = null
}

data class Expect(
    val dest: InstructionRef,
    val value: Value,
    val expected: Constant,
) : DebugInstruction {
    override val result get() = dest
}

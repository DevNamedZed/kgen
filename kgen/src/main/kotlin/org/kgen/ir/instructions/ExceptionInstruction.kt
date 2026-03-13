package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.LandingPadClause
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface ExceptionInstruction : Instruction {
    override val category get() = IrCategory.EXCEPTION
}

data class LandingPad(
    val dest: InstructionRef,
    val resultType: Type,
    val clauses: List<LandingPadClause>,
    val cleanup: Boolean = false,
) : ExceptionInstruction {
    override val result get() = dest
}

data class Resume(
    val value: Value,
) : ExceptionInstruction {
    override val result: Value? get() = null
}

data class CatchSwitch(
    val dest: InstructionRef,
    val parentPad: Value?,
    val handlers: List<String>,
    val unwindDest: String?,
) : ExceptionInstruction {
    override val result get() = dest
}

data class CatchPad(
    val dest: InstructionRef,
    val catchSwitch: Value,
    val args: List<Value>,
) : ExceptionInstruction {
    override val result get() = dest
}

data class CleanupPad(
    val dest: InstructionRef,
    val parentPad: Value?,
    val args: List<Value>,
) : ExceptionInstruction {
    override val result get() = dest
}

data class CatchRet(
    val catchPad: Value,
    val dest: String,
) : ExceptionInstruction {
    override val result: Value? get() = null
}

data class CleanupRet(
    val cleanupPad: Value,
    val unwindDest: String?,
) : ExceptionInstruction {
    override val result: Value? get() = null
}

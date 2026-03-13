package org.kgen.ir.instructions

import org.kgen.ir.FCmpPredicate
import org.kgen.ir.FastMathFlags
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value

sealed interface ComparisonInstruction : Instruction {
    override val category get() = IrCategory.COMPARISON
}

data class ICmp(
    val dest: InstructionRef,
    val predicate: ICmpPredicate,
    val lhs: Value,
    val rhs: Value,
) : ComparisonInstruction {
    override val result get() = dest
}

data class FCmp(
    val dest: InstructionRef,
    val predicate: FCmpPredicate,
    val lhs: Value,
    val rhs: Value,
    val fastMath: FastMathFlags = FastMathFlags.NONE,
) : ComparisonInstruction {
    override val result get() = dest
}

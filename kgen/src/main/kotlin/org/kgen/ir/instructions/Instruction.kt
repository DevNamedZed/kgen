package org.kgen.ir.instructions

import org.kgen.ir.IrCategory
import org.kgen.ir.Value

/**
 * Root of the instruction hierarchy.
 *
 * Every IR instruction is a data class that implements this sealed interface
 * (via a category-specific marker like [ArithmeticInstruction] or [MemoryInstruction]).
 *
 * **For pass/codegen authors:** use [operands] to enumerate an instruction's input values
 * without writing per-opcode `when` blocks. Use `is ArithmeticInstruction` for coarse
 * category matching, or match individual types (`is Add`, `is Load`) for precise dispatch.
 *
 * **For frontend authors:** you rarely interact with instruction data classes directly.
 * Use [org.kgen.ir.build.InstructionEmitter] or the typed `InstructionSet` builder API instead.
 *
 * @see org.kgen.ir.IrCategory
 */
sealed interface Instruction {

    /**
     * The SSA value produced by this instruction, or `null` for void-result instructions
     * (e.g., [Store], [Ret], [Fence]).
     *
     * When non-null, this is always an [org.kgen.ir.InstructionRef] whose [org.kgen.ir.Value.name]
     * is unique within the enclosing function.
     */
    val result: Value?

    /**
     * The functional category this instruction belongs to.
     *
     * Categories determine which constraint sets and capabilities are required. Every
     * instruction belongs to exactly one category. The category is typically inherited
     * from the marker sub-interface (e.g., all [ArithmeticInstruction] subtypes return
     * [IrCategory.ARITHMETIC]).
     */
    val category: IrCategory

    /**
     * Static effect descriptor for this instruction.
     *
     * Describes the observable behavior of this opcode: memory reads/writes, traps,
     * throws, barriers, safepoints, terminators, commutativity. Optimization passes
     * query this property instead of writing per-opcode `when` blocks.
     *
     * The returned effects are *static* (worst-case for the opcode). Passes with
     * additional information (e.g., alias analysis) may narrow effects dynamically
     * but never widen them.
     *
     * @see InstructionEffects
     */
    val effects: InstructionEffects

    /**
     * All [Value] operands consumed by this instruction.
     *
     * This is the canonical list for use-def analysis: liveness, dead code elimination,
     * and operand traversal should use this property instead of per-instruction `when` blocks.
     *
     * The list includes only [Value] inputs — not string labels, type parameters, boolean
     * flags, or other non-value configuration. The [result] value is **not** included.
     *
     * Ordering matches the instruction's semantic operand order (e.g., for [Add],
     * operands are `[lhs, rhs]`).
     */
    val operands: List<Value>
}

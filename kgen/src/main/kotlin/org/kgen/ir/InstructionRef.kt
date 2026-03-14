package org.kgen.ir

/**
 * A reference to the SSA result of an instruction.
 *
 * Created automatically by the builder when emitting instructions that produce a value
 * (e.g., `add`, `load`, `call` with non-void return type). The [name] is typically
 * auto-assigned as `%0`, `%1`, `%2`, etc., and is unique within the enclosing function.
 *
 * An `InstructionRef` appears as the [org.kgen.ir.instructions.Instruction.result] of
 * the instruction that defines it, and as an operand ([org.kgen.ir.instructions.Instruction.operands])
 * of every instruction that uses the value.
 *
 * **Do not construct directly** in normal usage — the builder creates these for you.
 * Direct construction is only needed when building IR data structures manually
 * (e.g., in tests or code generators).
 *
 * @param name the SSA name, unique within the enclosing function (e.g., `"%0"`, `"%sum"`)
 * @param type the IR type of the value produced by the defining instruction
 */
data class InstructionRef(override val name: String, override val type: Type) : Value

package org.kgen.ir

/**
 * An SSA value in the kgen IR. All values are typed and named.
 *
 * In SSA (Static Single Assignment) form, every value is defined exactly once.
 * Values flow through the program as operands to instructions and results of instructions.
 * The value's [type] determines what operations are legal on it, and its [name] provides
 * a unique identifier within the enclosing scope.
 *
 * **Concrete subtypes:**
 *
 * | Subtype | Syntax | Description |
 * |---------|--------|-------------|
 * | [Parameter] | `%name` | A function's formal parameter |
 * | [InstructionRef] | `%0`, `%1` | The result of an instruction (SSA name) |
 * | [GlobalRef] | `@name` | Reference to a global variable |
 * | [FunctionRef] | `@name` | Reference to a function |
 * | [BlockRef] | `%label` | Reference to a basic block label |
 * | [Constant] | `42`, `3.14`, `null` | Compile-time constant value |
 *
 * **For pass authors:** use [org.kgen.ir.instructions.Instruction.operands] to enumerate
 * the values consumed by an instruction without per-opcode `when` blocks.
 *
 * @see Type
 * @see Constant
 */
sealed interface Value {

    /** The IR type of this value (e.g., [Type.I32], [Type.Pointer], [Type.ClassRef]). */
    val type: Type

    /**
     * The name of this value, unique within its scope.
     *
     * Local values (parameters, instruction results) use `%`-prefixed names.
     * Global values (globals, functions) use `@`-prefixed names.
     * Constants return a string representation of their value.
     */
    val name: String
}

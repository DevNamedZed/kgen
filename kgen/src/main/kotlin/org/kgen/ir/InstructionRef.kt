package org.kgen.ir

/**
 * A reference to the result of an instruction. Created by the builders when emitting
 * instructions that produce a value (e.g., `add`, `load`, `call`).
 * Names are auto-assigned: `%0`, `%1`, `%2`, ...
 */
data class InstructionRef(override val name: String, override val type: Type) : Value

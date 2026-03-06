package org.kgen.ir

/**
 * A value in the kgen IR. All values are typed and named.
 *
 * In SSA form, every value is defined exactly once. The concrete subtypes are:
 * - [Parameter] — function parameter (`%name`)
 * - [InstructionRef] — result of an instruction (`%0`, `%1`, ...)
 * - [GlobalRef] — reference to a global variable (`@name`)
 * - [FunctionRef] — reference to a function (`@name`)
 * - [BlockRef] — reference to a basic block label (`%label`)
 * - [Constant] — compile-time constant (integers, floats, null, aggregates, etc.)
 */
sealed interface Value {
    val type: Type
    val name: String
}

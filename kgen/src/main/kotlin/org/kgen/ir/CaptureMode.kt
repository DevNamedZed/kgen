package org.kgen.ir

/**
 * Mode for capturing variables in a closure.
 *
 * Used by [org.kgen.ir.instructions.ClosureCreate] to indicate how each
 * captured variable is bound in the closure environment.
 */
enum class CaptureMode {
    /** Copy value at closure creation time. */
    BY_VALUE,

    /** Capture mutable reference; callee may read and write. */
    BY_REF,

    /** Same as BY_REF; explicit in the type system for borrow-checking frontends. */
    BY_MUT_REF,
}

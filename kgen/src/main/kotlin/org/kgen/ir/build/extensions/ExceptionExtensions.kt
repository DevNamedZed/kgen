package org.kgen.ir.build.extensions

import org.kgen.ir.build.sets.ExceptionInstructionSet

/**
 * Sugar methods for exception operations. Extends [ExceptionInstructionSet].
 *
 * Currently a marker interface that composes into scopes. Additional
 * convenience methods (e.g., structured try-catch) will be added as
 * the exception model evolves.
 */
interface ExceptionExtensions : ExceptionInstructionSet

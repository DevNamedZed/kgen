package org.kgen.ir.build.extensions

import org.kgen.ir.Value
import org.kgen.ir.build.sets.BitwiseInstructionSet

/**
 * Sugar methods for bitwise shift operations. Extends [BitwiseInstructionSet]
 * with conventional shift names.
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * Value arithmetic = ins.shr(a, Type.i32(1));   // arithmetic shift right (ashr)
 * Value logical = ins.ushr(a, Type.i32(1));     // logical shift right (lshr)
 * ```
 */
interface BitwiseExtensions : BitwiseInstructionSet {

    fun shr(left: Value, right: Value): Value = ashr(left, right)

    fun ushr(left: Value, right: Value): Value = lshr(left, right)
}

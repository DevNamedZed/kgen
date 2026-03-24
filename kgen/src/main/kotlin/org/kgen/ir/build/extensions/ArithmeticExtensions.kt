package org.kgen.ir.build.extensions

import org.kgen.ir.Value
import org.kgen.ir.build.sets.ArithmeticInstructionSet

/**
 * Sugar methods for arithmetic operations. Extends [ArithmeticInstructionSet]
 * with signed-default division and remainder.
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * Value quotient = ins.div(a, b);   // signed division (sdiv)
 * Value remainder = ins.rem(a, b);  // signed remainder (srem)
 * ```
 */
interface ArithmeticExtensions : ArithmeticInstructionSet {

    fun div(left: Value, right: Value): Value = sdiv(left, right)

    fun rem(left: Value, right: Value): Value = srem(left, right)
}

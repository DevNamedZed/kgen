package org.kgen.ir.build.extensions

import org.kgen.ir.FCmpPredicate
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.Value
import org.kgen.ir.build.sets.ComparisonInstructionSet

/**
 * Sugar methods for comparison operations. Extends [ComparisonInstructionSet]
 * with named comparison helpers that delegate to [icmp] and [fcmp].
 *
 * Scopes that include this interface get human-readable comparison methods
 * like [eq], [lt], [gt] instead of requiring raw predicate enums.
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * Value isEqual = ins.eq(a, b);       // instead of ins.icmp(ICmpPredicate.EQ, a, b)
 * Value isLess = ins.lt(a, b);        // instead of ins.icmp(ICmpPredicate.SLT, a, b)
 * Value floatLt = ins.flt(x, y);      // instead of ins.fcmp(FCmpPredicate.OLT, x, y)
 * ```
 */
interface ComparisonExtensions : ComparisonInstructionSet {

    fun eq(left: Value, right: Value): Value = icmp(ICmpPredicate.EQ, left, right)

    fun ne(left: Value, right: Value): Value = icmp(ICmpPredicate.NE, left, right)

    fun lt(left: Value, right: Value): Value = icmp(ICmpPredicate.SLT, left, right)

    fun le(left: Value, right: Value): Value = icmp(ICmpPredicate.SLE, left, right)

    fun gt(left: Value, right: Value): Value = icmp(ICmpPredicate.SGT, left, right)

    fun ge(left: Value, right: Value): Value = icmp(ICmpPredicate.SGE, left, right)

    fun ult(left: Value, right: Value): Value = icmp(ICmpPredicate.ULT, left, right)

    fun ule(left: Value, right: Value): Value = icmp(ICmpPredicate.ULE, left, right)

    fun ugt(left: Value, right: Value): Value = icmp(ICmpPredicate.UGT, left, right)

    fun uge(left: Value, right: Value): Value = icmp(ICmpPredicate.UGE, left, right)

    fun feq(left: Value, right: Value): Value = fcmp(FCmpPredicate.OEQ, left, right)

    fun fne(left: Value, right: Value): Value = fcmp(FCmpPredicate.ONE, left, right)

    fun flt(left: Value, right: Value): Value = fcmp(FCmpPredicate.OLT, left, right)

    fun fle(left: Value, right: Value): Value = fcmp(FCmpPredicate.OLE, left, right)

    fun fgt(left: Value, right: Value): Value = fcmp(FCmpPredicate.OGT, left, right)

    fun fge(left: Value, right: Value): Value = fcmp(FCmpPredicate.OGE, left, right)
}

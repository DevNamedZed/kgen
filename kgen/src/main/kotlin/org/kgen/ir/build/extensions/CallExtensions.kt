package org.kgen.ir.build.extensions

import org.kgen.ir.FunctionRef
import org.kgen.ir.Value
import org.kgen.ir.build.sets.CallInstructionSet

/**
 * Sugar methods for call operations. Extends [CallInstructionSet]
 * with a convenience overload that auto-fills the return type from
 * the function reference.
 *
 * ```java
 * NativeScope ins = fn.instructions();
 * FunctionRef addRef = fn.functionRef("add", List.of(Type.I32, Type.I32), Type.I32);
 * Value result = ins.call(addRef, List.of(a, b));  // return type inferred from addRef
 * ```
 */
interface CallExtensions : CallInstructionSet {

    fun call(target: FunctionRef, args: List<Value>): Value? {
        return call(target, args, target.type.ret)
    }
}

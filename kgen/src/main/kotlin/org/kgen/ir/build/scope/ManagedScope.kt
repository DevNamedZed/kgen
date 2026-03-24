package org.kgen.ir.build.scope

import org.kgen.ir.build.extensions.*
import org.kgen.ir.build.sets.*

/**
 * Scope for managed/high-level code generation.
 *
 * Includes object model operations (newObject, virtualCall, getField, etc.)
 * plus arithmetic, comparisons, and calls. Suitable for JVM/CLR/KVM-style
 * bytecode emission where memory is managed by the runtime.
 *
 * Does NOT include raw memory operations ([MemoryInstructionSet]) or bitwise
 * operations ([BitwiseInstructionSet]) — use [NativeScope] or [FullScope]
 * for machine-level code.
 *
 * ```java
 * ClassBuilder<ManagedScope> cls = module.createClass(ManagedScope.class, "UserService");
 * cls.defineFunction("getUser", params, userType, fn -> {
 *     ManagedScope ins = fn.instructions();
 *     Value user = ins.newObject("User");
 *     ins.putField(user, "User", "name", Type.OpaquePointer, nameValue);
 *     fn.ret(user);
 * });
 * ```
 */
interface ManagedScope :
    ArithmeticInstructionSet,
    ComparisonInstructionSet,
    ObjectInstructionSet,
    CallInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    DebugInstructionSet,
    ComparisonExtensions,
    ArithmeticExtensions,
    CallExtensions

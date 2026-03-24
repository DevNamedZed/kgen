package org.kgen.ir.build.scope

import org.kgen.ir.build.extensions.*
import org.kgen.ir.build.sets.*

/**
 * Scope for native/low-level code generation.
 *
 * Includes machine-level instruction sets (arithmetic, memory, bitwise, comparisons,
 * conversions, exceptions) plus sugar extensions. Suitable for x86/ARM64/RISC-V/WASM
 * native code emission.
 *
 * Does NOT include object model operations ([ObjectInstructionSet]) — use
 * [ManagedScope] or [FullScope] for managed code.
 *
 * ```java
 * ClassBuilder<NativeScope> cls = module.createClass(NativeScope.class, "Calculator");
 * cls.defineFunction("add", params, Type.I32, fn -> {
 *     NativeScope ins = fn.instructions();
 *     fn.ret(ins.add(fn.param(0), fn.param(1)));
 * });
 * ```
 */
interface NativeScope :
    ArithmeticInstructionSet,
    BitwiseInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    MemoryInstructionSet,
    CallInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    ExceptionInstructionSet,
    DebugInstructionSet,
    ComparisonExtensions,
    ArithmeticExtensions,
    BitwiseExtensions,
    ConversionExtensions,
    MemoryExtensions,
    CallExtensions,
    ExceptionExtensions

package org.kgen.ir.build.scope

import org.kgen.ir.build.extensions.*
import org.kgen.ir.build.sets.*

/**
 * Scope that includes all instruction sets and extensions.
 *
 * Combines managed and native capabilities plus runtime, interop, exceptions,
 * atomics, vectors, aggregates, and debug. Use when you need access to everything
 * or for mixed-mode code that operates at both managed and native levels.
 *
 * ```java
 * ClassBuilder<FullScope> cls = module.createClass(FullScope.class, "Bridge");
 * ```
 */
interface FullScope :
    ArithmeticInstructionSet,
    BitwiseInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    MemoryInstructionSet,
    ObjectInstructionSet,
    CallInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    ExceptionInstructionSet,
    AtomicInstructionSet,
    VectorInstructionSet,
    AggregateInstructionSet,
    RuntimeInstructionSet,
    InteropInstructionSet,
    DebugInstructionSet,
    ComparisonExtensions,
    ArithmeticExtensions,
    BitwiseExtensions,
    ConversionExtensions,
    MemoryExtensions,
    CallExtensions,
    ExceptionExtensions

// Generated from scopes.yaml — do not edit
package org.kgen.ir.build.sets

/**
 * Scope for managed/high-level code generation.
 *
 * Includes arithmetic, object operations, comparisons, calls, terminators, SSA,
 * and debug. Suitable for JVM/CLR-style bytecode emission where memory is managed.
 *
 * ```kotlin
 * val b = ir.createInstructionBuilder<ManagedScope>()
 * b.add(x, y)          // ArithmeticInstructionSet
 * b.newObject("Foo")    // ObjectInstructionSet
 * ```
 */
interface ManagedScope : ArithmeticInstructionSet,
    ObjectInstructionSet,
    CallInstructionSet,
    ComparisonInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    DebugInstructionSet

/**
 * Scope for native/low-level code generation.
 *
 * Includes arithmetic, memory operations, bitwise, comparisons, conversions,
 * calls, terminators, and SSA. Suitable for x86/ARM64/RISC-V native code emission.
 *
 * ```kotlin
 * val b = ir.createInstructionBuilder<NativeScope>()
 * b.add(x, y)     // ArithmeticInstructionSet
 * b.load(ptr)      // MemoryInstructionSet
 * ```
 */
interface NativeScope : ArithmeticInstructionSet,
    MemoryInstructionSet,
    BitwiseInstructionSet,
    CallInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet

/**
 * Scope that includes all commonly used instruction sets.
 *
 * Combines managed and native capabilities plus runtime, interop, exceptions,
 * and debug. Use when you need access to everything.
 *
 * ```kotlin
 * val b = ir.createInstructionBuilder<FullScope>()
 * ```
 */
interface FullScope : ArithmeticInstructionSet,
    ObjectInstructionSet,
    MemoryInstructionSet,
    BitwiseInstructionSet,
    CallInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    DebugInstructionSet,
    RuntimeInstructionSet,
    InteropInstructionSet,
    ExceptionInstructionSet

/**
 * Scope for compute/GPGPU-style code generation.
 *
 * Includes arithmetic, memory, bitwise, comparisons, conversions, calls,
 * terminators, SSA, and compute-specific instructions.
 *
 * ```kotlin
 * val b = ir.createInstructionBuilder<ComputeScope>()
 * ```
 */
interface ComputeScope : ArithmeticInstructionSet,
    MemoryInstructionSet,
    BitwiseInstructionSet,
    CallInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    ComputeInstructionSet

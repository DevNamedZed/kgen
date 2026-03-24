package org.kgen.ir.build.scope

import org.kgen.ir.build.extensions.*
import org.kgen.ir.build.sets.*

/**
 * Scope for compute/GPGPU-style code generation.
 *
 * Includes native instruction sets plus compute-specific instructions
 * (thread dimensions, barriers, shared memory). Suitable for GPU kernel bodies.
 *
 * Does NOT include object model operations or exceptions.
 *
 * ```java
 * ClassBuilder<ComputeScope> cls = module.createClass(ComputeScope.class, "Kernel");
 * ```
 */
interface ComputeScope :
    ArithmeticInstructionSet,
    BitwiseInstructionSet,
    ComparisonInstructionSet,
    ConversionInstructionSet,
    MemoryInstructionSet,
    CallInstructionSet,
    TerminatorInstructionSet,
    SsaInstructionSet,
    ComputeInstructionSet,
    ComparisonExtensions,
    ArithmeticExtensions,
    BitwiseExtensions,
    ConversionExtensions,
    MemoryExtensions,
    CallExtensions

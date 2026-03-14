package org.kgen.ir

import java.util.EnumSet

/**
 * Predefined instruction category constraint sets for common compilation scenarios.
 *
 * The IR has 17 instruction categories ([IrCategory]) spanning from low-level machine
 * operations (ARITHMETIC, MEMORY, ATOMIC) to high-level managed concepts (OBJECT, RUNTIME).
 * Constraints restrict which categories an [IrBuilder][org.kgen.ir.build.IrBuilder] accepts,
 * catching misuse at build time rather than during codegen.
 *
 * Preset hierarchy (each level adds categories):
 * - [STRUCTURAL] -- terminators, calls, SSA, debug, intrinsics (always needed)
 * - [NATIVE] -- structural + all machine categories (no GC, no objects)
 * - [RUNTIME_NATIVE] -- native + RUNTIME (for GC/runtime code written in native)
 * - [MIXED] -- runtime-native + INTEROP (managed/native boundary)
 * - [MANAGED_VM] -- structural + OBJECT only (JVM/MSIL targets)
 * - [MANAGED_NATIVE] / [ALL] -- everything
 *
 * ```java
 * // Only machine-level code (no GC, no objects)
 * var ir = new IrBuilder("kernel", Target.x86_64(), IrConstraints.NATIVE);
 *
 * // Runtime + machine (GC runtime written in native code)
 * var ir = new IrBuilder("gc", Target.x86_64(), IrConstraints.RUNTIME_NATIVE);
 * ```
 *
 * Pass `null` to allow all categories (the default).
 *
 * See `spec/ir.md` for the full constraint system specification.
 */
object IrConstraints {

    /** Structural categories only: terminators, calls, SSA, debug, intrinsics. */
    @JvmField val STRUCTURAL: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
    )

    /** Native code: structural + all machine categories. No runtime, interop, or object. */
    @JvmField val NATIVE: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
    )

    /** Runtime implementation: structural + machine + runtime. For GC/runtime code written in native. */
    @JvmField val RUNTIME_NATIVE: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
        IrCategory.RUNTIME,
    )

    /** Mixed mode: structural + machine + runtime + interop. For managed/native boundary code. */
    @JvmField val MIXED: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
        IrCategory.RUNTIME, IrCategory.INTEROP,
    )

    /** Managed VM (JVM, MSIL): structural + object. No machine/runtime/interop. */
    @JvmField val MANAGED_VM: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.OBJECT,
    )

    /** Managed native: structural + machine + runtime + interop + object. */
    @JvmField val MANAGED_NATIVE: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
        IrCategory.RUNTIME, IrCategory.INTEROP, IrCategory.OBJECT,
    )

    /** JIT compilation: managed native + deoptimization (guards, frame states, OSR). */
    @JvmField val JIT: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
        IrCategory.RUNTIME, IrCategory.INTEROP, IrCategory.OBJECT,
        IrCategory.DEOPTIMIZATION,
    )

    /** Compute kernel: native + compute. For GPU kernel bodies. */
    @JvmField val COMPUTE_KERNEL: Set<IrCategory> = unmodifiable(
        IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
        IrCategory.DEBUG, IrCategory.INTRINSIC,
        IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
        IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
        IrCategory.VECTOR, IrCategory.AGGREGATE,
        IrCategory.COMPUTE,
    )

    /** All categories allowed. Equivalent to passing `null` as constraints. */
    @JvmField val ALL: Set<IrCategory> = unmodifiable(
        *IrCategory.entries.toTypedArray()
    )

    private fun unmodifiable(vararg categories: IrCategory): Set<IrCategory> =
        java.util.Collections.unmodifiableSet(EnumSet.of(categories[0], *categories.drop(1).toTypedArray()))
}

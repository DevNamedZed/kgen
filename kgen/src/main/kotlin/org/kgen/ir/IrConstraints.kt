package org.kgen.ir

import java.util.EnumSet

/**
 * Predefined instruction category constraint sets for common compilation scenarios.
 *
 * Use these with [IrBuilder][org.kgen.ir.build.IrBuilder] to restrict which
 * instruction categories are allowed:
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

    /** Managed native: structural + machine + runtime + interop + object. Everything except nothing. */
    @JvmField val MANAGED_NATIVE: Set<IrCategory> = unmodifiable(
        *IrCategory.entries.toTypedArray()
    )

    /** All categories allowed. Equivalent to passing `null` as constraints. */
    @JvmField val ALL: Set<IrCategory> = unmodifiable(
        *IrCategory.entries.toTypedArray()
    )

    private fun unmodifiable(vararg categories: IrCategory): Set<IrCategory> =
        java.util.Collections.unmodifiableSet(EnumSet.of(categories[0], *categories.drop(1).toTypedArray()))
}

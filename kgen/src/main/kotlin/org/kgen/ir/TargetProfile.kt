package org.kgen.ir

/**
 * Describes what kind of IR a module contains, independent of the
 * specific architecture it will run on.
 *
 * Used at [ModuleBuilder][org.kgen.ir.build.ModuleBuilder] construction to enforce
 * instruction category constraints. The actual [Target][org.kgen.ir.target.Target]
 * (arch, CPU, features) is supplied at codegen time.
 *
 * ```java
 * // Profile constrains what you can emit — target is supplied later at codegen
 * var module = new ModuleBuilder("math", TargetProfile.NATIVE);
 * ```
 *
 * @param constraints the set of allowed [IrCategory] values, or null for unconstrained
 */
enum class TargetProfile(
    @JvmField val constraints: Set<IrCategory>?,
) {
    /** Pure native machine code. No GC, no objects. Runs on any native arch. */
    NATIVE(IrConstraints.NATIVE),

    /** GC runtime implementation. Native + runtime instructions. */
    RUNTIME_NATIVE(IrConstraints.RUNTIME_NATIVE),

    /** Managed VM (JVM, CLR, KVM). Object model only, no machine instructions. */
    MANAGED_VM(IrConstraints.MANAGED_VM),

    /** Managed code with native interop. Everything except deoptimization. */
    MANAGED_NATIVE(IrConstraints.MANAGED_NATIVE),

    /** Mixed: native + interop. Cross-boundary code. */
    MIXED(IrConstraints.MIXED),

    /** JIT: everything including deoptimization (guards, frame states, OSR). */
    JIT(IrConstraints.JIT),

    /** Compute kernel: native + compute-specific instructions. */
    COMPUTE(IrConstraints.COMPUTE_KERNEL),

    /** Unconstrained. All categories allowed. */
    ANY(null);

    /**
     * Returns true if [other]'s constraints are a subset of this profile's constraints.
     * A child module's profile must be compatible with its parent.
     */
    fun isCompatibleWith(other: TargetProfile): Boolean {
        if (other.constraints == null) {
            return true
        }
        if (constraints == null) {
            return false
        }
        return other.constraints.containsAll(constraints)
    }
}

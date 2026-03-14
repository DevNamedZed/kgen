package org.kgen.ir

import java.util.EnumSet

/**
 * An immutable set of [Capability] grants with validation of implication and
 * exclusivity rules.
 *
 * Use the [of] factory or the predefined configurations ([KGEN_NATIVE],
 * [KGEN_JIT], [PLAIN_NATIVE], etc.) to create instances. The factory
 * validates that all capability constraints are satisfied.
 *
 * ```java
 * CapabilitySet caps = CapabilitySet.of(
 *     Capability.NATIVE_MEMORY,
 *     Capability.NATIVE_EXCEPTIONS
 * );
 *
 * // Or use a predefined configuration:
 * CapabilitySet caps = CapabilitySet.KGEN_NATIVE;
 * ```
 */
class CapabilitySet private constructor(
    private val capabilities: Set<Capability>,
) {

    /**
     * Returns true if the given capability is granted.
     */
    fun has(capability: Capability): Boolean {
        return capability in capabilities
    }

    /**
     * Returns true if all the given capabilities are granted.
     */
    fun hasAll(vararg required: Capability): Boolean {
        return required.all { it in capabilities }
    }

    /**
     * Returns true if any of the given capabilities is granted.
     */
    fun hasAny(vararg required: Capability): Boolean {
        return required.any { it in capabilities }
    }

    /**
     * Returns the set of all granted capabilities.
     */
    fun granted(): Set<Capability> {
        return capabilities
    }

    /**
     * Returns a new [CapabilitySet] with the given capabilities added.
     *
     * @throws IllegalArgumentException if the result would violate any rules
     */
    fun with(vararg additional: Capability): CapabilitySet {
        val combined = EnumSet.copyOf(capabilities)
        combined.addAll(additional.toList())
        return of(combined)
    }

    /**
     * Returns a new [CapabilitySet] with the given capabilities removed.
     */
    fun without(vararg removed: Capability): CapabilitySet {
        val remaining = EnumSet.copyOf(capabilities)
        remaining.removeAll(removed.toSet())
        return of(remaining)
    }

    override fun equals(other: Any?): Boolean {
        return other is CapabilitySet && capabilities == other.capabilities
    }

    override fun hashCode(): Int {
        return capabilities.hashCode()
    }

    override fun toString(): String {
        return "CapabilitySet${capabilities.sortedBy { it.ordinal }}"
    }

    companion object {

        // --- Implication rules ---

        private val IMPLICATIONS: Map<Capability, Capability> = mapOf(
            Capability.GC_MOVING to Capability.GC_MANAGED,
            Capability.GC_BARRIERS to Capability.GC_MANAGED,
            Capability.REF_COUNTED to Capability.GC_MANAGED,
            Capability.OSR to Capability.DEOPTIMIZATION,
            Capability.VIRTUAL_DISPATCH to Capability.MANAGED_OBJECTS,
            Capability.TYPE_CHECKS to Capability.MANAGED_OBJECTS,
        )

        // --- Mutual exclusivity rules ---

        private val EXCLUSIVE_PAIRS: List<Pair<Capability, Capability>> = listOf(
            Capability.NO_EXCEPTIONS to Capability.MANAGED_EXCEPTIONS,
            Capability.NO_EXCEPTIONS to Capability.NATIVE_EXCEPTIONS,
            Capability.COMPUTE_KERNEL to Capability.MANAGED_OBJECTS,
            Capability.COMPUTE_KERNEL to Capability.GC_MANAGED,
        )

        /**
         * Creates a [CapabilitySet] from the given capabilities.
         *
         * Validates implication and exclusivity rules. Throws [IllegalArgumentException]
         * if any rule is violated.
         */
        @JvmStatic
        fun of(vararg capabilities: Capability): CapabilitySet {
            return of(capabilities.toCollection(EnumSet.noneOf(Capability::class.java)))
        }

        /**
         * Creates a [CapabilitySet] from the given set.
         *
         * @throws IllegalArgumentException if any rule is violated
         */
        @JvmStatic
        fun of(capabilities: Set<Capability>): CapabilitySet {
            validate(capabilities)
            return CapabilitySet(EnumSet.copyOf(capabilities))
        }

        private fun validate(capabilities: Set<Capability>) {
            for ((granted, required) in IMPLICATIONS) {
                if (granted in capabilities && required !in capabilities) {
                    throw IllegalArgumentException(
                        "Capability $granted requires $required to also be granted"
                    )
                }
            }

            for ((first, second) in EXCLUSIVE_PAIRS) {
                if (first in capabilities && second in capabilities) {
                    throw IllegalArgumentException(
                        "Capabilities $first and $second are mutually exclusive"
                    )
                }
            }
        }

        // --- Predefined configurations ---

        /**
         * kgen native runtime — full-featured configuration for kgen's own runtime.
         *
         * All Object, Runtime, and Deoptimization instructions are legal.
         */
        @JvmField
        val KGEN_NATIVE: CapabilitySet = CapabilitySet(EnumSet.of(
            Capability.NATIVE_MEMORY,
            Capability.NATIVE_EXCEPTIONS,
            Capability.MANAGED_OBJECTS,
            Capability.VIRTUAL_DISPATCH,
            Capability.TYPE_CHECKS,
            Capability.MANAGED_EXCEPTIONS,
            Capability.GC_MANAGED,
            Capability.GC_MOVING,
            Capability.GC_BARRIERS,
            Capability.DEOPTIMIZATION,
        ))

        /**
         * kgen JIT with speculation — adds OSR for on-stack replacement.
         */
        @JvmField
        val KGEN_JIT: CapabilitySet = CapabilitySet(EnumSet.of(
            Capability.NATIVE_MEMORY,
            Capability.NATIVE_EXCEPTIONS,
            Capability.MANAGED_OBJECTS,
            Capability.VIRTUAL_DISPATCH,
            Capability.TYPE_CHECKS,
            Capability.MANAGED_EXCEPTIONS,
            Capability.GC_MANAGED,
            Capability.GC_MOVING,
            Capability.GC_BARRIERS,
            Capability.DEOPTIMIZATION,
            Capability.OSR,
        ))

        /**
         * Plain native — no managed object model (C/C++/Rust-like).
         */
        @JvmField
        val PLAIN_NATIVE: CapabilitySet = CapabilitySet(EnumSet.of(
            Capability.NATIVE_MEMORY,
            Capability.NATIVE_EXCEPTIONS,
        ))

        /**
         * External VM target — JVM, CIL bytecode output.
         *
         * No GcMoving or GcBarriers — the host VM handles all GC internally.
         */
        @JvmField
        val EXTERNAL_VM: CapabilitySet = CapabilitySet(EnumSet.of(
            Capability.NATIVE_MEMORY,
            Capability.MANAGED_OBJECTS,
            Capability.VIRTUAL_DISPATCH,
            Capability.TYPE_CHECKS,
            Capability.MANAGED_EXCEPTIONS,
            Capability.GC_MANAGED,
        ))

        /**
         * Compute kernel body — GPU/SIMD compute without managed objects.
         */
        @JvmField
        val COMPUTE_KERNEL: CapabilitySet = CapabilitySet(EnumSet.of(
            Capability.NATIVE_MEMORY,
            Capability.COMPUTE_MEMORY,
            Capability.COMPUTE_KERNEL,
        ))

        /**
         * Empty capability set — no capabilities granted.
         */
        @JvmField
        val NONE: CapabilitySet = CapabilitySet(EnumSet.noneOf(Capability::class.java))
    }
}

package org.kgen.ir

/**
 * A named grouping of functions and globals within a [Module] that share
 * instruction category constraints.
 *
 * Submodules enable mixed-mode modules where different sections of code
 * operate at different abstraction levels. For example, a single module
 * might contain managed application code (OBJECT tier) alongside a native
 * GC implementation (RUNTIME_NATIVE constraints).
 *
 * Constraint resolution order:
 * 1. Per-function override (if set)
 * 2. Submodule constraints
 * 3. Module-level constraints (ModuleBuilder.allowedCategories)
 *
 * ```kotlin
 * val ir = ModuleBuilder("mixed", Target.x86_64())
 * ir.beginSubmodule("gc", IrConstraints.RUNTIME_NATIVE)
 * // ... functions here are restricted to RUNTIME_NATIVE
 * ir.endSubmodule()
 *
 * ir.beginSubmodule("app", IrConstraints.ALL)
 * // ... functions here can use any category
 * ir.endSubmodule()
 * ```
 */
data class Submodule(
    val name: String,
    val constraints: Set<IrCategory>,
    val functions: List<String> = emptyList(),
    val globals: List<String> = emptyList(),
)

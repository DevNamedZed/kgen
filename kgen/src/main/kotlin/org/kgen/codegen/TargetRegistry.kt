package org.kgen.codegen

/**
 * Registry for discovering and looking up target backends.
 *
 * ```kotlin
 * val registry = TargetRegistry()
 * registry.registerGenerator(X86CodeGenerator())
 * val gen = registry.generator("x86_64")
 * val bytes = gen.generate(module)
 * ```
 */
class TargetRegistry {
    private val generators = mutableMapOf<String, CodeGenerator>()

    /** Register a [CodeGenerator] under its [CodeGenerator.targetName]. */
    fun registerGenerator(generator: CodeGenerator) {
        generators[generator.targetName] = generator
    }

    /** Look up a code generator by target name. Throws if not registered. */
    fun generator(targetName: String): CodeGenerator =
        generators[targetName] ?: error("No code generator registered for target '$targetName'")

    /** Returns the set of all registered target names. */
    fun availableTargets(): Set<String> = generators.keys.toSet()
}

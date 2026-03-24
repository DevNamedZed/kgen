package org.kgen.pipeline

import org.kgen.codegen.OptLevel
import org.kgen.ir.target.Target

/**
 * Named stage entry in a pipeline, allowing insertion/removal by name.
 */
data class NamedStage(
    val name: String,
    val stage: PipelineStage,
)

/**
 * Builder for constructing pipelines with named stages, position-relative insertion,
 * and override capabilities.
 *
 * Starts from a preset (e.g., [OptLevel.O2]) and allows customization:
 *
 * ```java
 * // Java
 * Pipeline pipeline = PipelineBuilder.forOptLevel(OptLevel.O2, Target.x86_64())
 *     .skip("auto-vectorization")
 *     .addAfter("inlining", "my-specialization", new MySpecializationStage())
 *     .build();
 * Module optimized = pipeline.execute(module);
 * ```
 *
 * ```kotlin
 * // Kotlin
 * val pipeline = PipelineBuilder.forOptLevel(OptLevel.O2, Target.x86_64())
 *     .skip("auto-vectorization")
 *     .addAfter("inlining", "my-specialization", MySpecializationStage())
 *     .build()
 * ```
 */
class PipelineBuilder private constructor(
    private val stages: MutableList<NamedStage>,
    private val target: Target?,
) {

    /**
     * Add a named stage at the end of the pipeline.
     */
    fun add(name: String, stage: PipelineStage): PipelineBuilder {
        stages.add(NamedStage(name, stage))
        return this
    }

    /**
     * Add a named stage after an existing stage with the given name.
     * Throws if the named stage is not found.
     */
    fun addAfter(existingName: String, newName: String, stage: PipelineStage): PipelineBuilder {
        val index = stages.indexOfFirst { it.name == existingName }
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages.add(index + 1, NamedStage(newName, stage))
        return this
    }

    /**
     * Add a named stage before an existing stage with the given name.
     * Throws if the named stage is not found.
     */
    fun addBefore(existingName: String, newName: String, stage: PipelineStage): PipelineBuilder {
        val index = stages.indexOfFirst { it.name == existingName }
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages.add(index, NamedStage(newName, stage))
        return this
    }

    /**
     * Replace an existing named stage with a new one.
     * Throws if the named stage is not found.
     */
    fun replace(existingName: String, stage: PipelineStage): PipelineBuilder {
        val index = stages.indexOfFirst { it.name == existingName }
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages[index] = NamedStage(existingName, stage)
        return this
    }

    /**
     * Remove a named stage from the pipeline.
     * Throws if the named stage is not found.
     */
    fun skip(name: String): PipelineBuilder {
        val removed = stages.removeAll { it.name == name }
        require(removed) { "Stage '$name' not found in pipeline" }
        return this
    }

    /**
     * Returns the list of stage names in current order.
     */
    fun stageNames(): List<String> = stages.map { it.name }

    /**
     * Build the final [Pipeline] from the configured stages.
     */
    fun build(): Pipeline {
        val pipeline = Pipeline(target = target)
        for (namedStage in stages) {
            pipeline.add(namedStage.stage)
        }
        return pipeline
    }

    companion object {
        /**
         * Create an empty PipelineBuilder.
         */
        @JvmStatic
        fun create(target: Target? = null): PipelineBuilder {
            return PipelineBuilder(mutableListOf(), target)
        }

        /**
         * Create a PipelineBuilder pre-populated with the canonical pipeline for the given [OptLevel].
         *
         * ```java
         * Pipeline pipeline = PipelineBuilder.forOptLevel(OptLevel.O2, Target.x86_64())
         *     .skip("loop-invariant-code-motion")
         *     .addAfter("inlining", "my-pass", new MyPass())
         *     .build();
         * ```
         */
        @JvmStatic
        @JvmOverloads
        fun forOptLevel(optLevel: OptLevel, target: Target? = null): PipelineBuilder {
            val stages = mutableListOf<NamedStage>()

            when (optLevel) {
                OptLevel.O0 -> {}
                OptLevel.O1, OptLevel.OS, OptLevel.OZ -> {
                    stages.add(NamedStage("mem2reg", Mem2Reg()))
                    stages.add(NamedStage("constant-folding", ConstantFolding()))
                    stages.add(NamedStage("instruction-combining", InstructionCombining()))
                    stages.add(NamedStage("dead-code-elimination", DeadCodeElimination()))
                }
                OptLevel.O2, OptLevel.O3 -> {
                    stages.add(NamedStage("scalar-replacement", ScalarReplacementOfAggregates()))
                    stages.add(NamedStage("mem2reg", Mem2Reg()))
                    stages.add(NamedStage("inlining", Inlining()))
                    stages.add(NamedStage("constant-folding", ConstantFolding()))
                    stages.add(NamedStage("instruction-combining", InstructionCombining()))
                    stages.add(NamedStage("global-value-numbering", GlobalValueNumbering()))
                    stages.add(NamedStage("dead-code-elimination-1", DeadCodeElimination()))
                    stages.add(NamedStage("jump-threading", JumpThreading()))
                    stages.add(NamedStage("loop-invariant-code-motion", LoopInvariantCodeMotion()))
                    stages.add(NamedStage("constant-folding-2", ConstantFolding()))
                    stages.add(NamedStage("dead-code-elimination-2", DeadCodeElimination()))
                }
            }

            return PipelineBuilder(stages, target)
        }
    }
}

package org.kgen.pipeline

import org.kgen.codegen.OptLevel
import org.kgen.ir.Module
import org.kgen.ir.PipelinePhase
import org.kgen.ir.target.Target

/**
 * A single transformation step in a [Pipeline].
 * Takes an immutable [Module] and returns a new transformed [Module].
 */
fun interface PipelineStage {
    fun run(module: Module): Module
}

/**
 * A [PipelineStage] that requires a [Target] to make architecture-specific decisions.
 *
 * Stages like VTableLowering, TransitionThunkLowering, and PinningLowering vary
 * their output based on the target (pointer size, calling convention, ABI, etc.).
 *
 * When a [Pipeline] encounters a TargetAwareStage, it passes its target to the
 * two-argument [run] method. If the pipeline has no target, it throws.
 *
 * ```java
 * // Java
 * var pipeline = new Pipeline(Target.x86_64());
 * pipeline.add(new VTableLowering());   // TargetAwareStage — gets the target
 * pipeline.add(new DeadCodeElimination()); // regular PipelineStage — no target needed
 * ```
 */
/**
 * A [PipelineStage] that declares preconditions — which pipeline phases must
 * NOT have been validated yet. This catches pass ordering errors early.
 *
 * For example, VTableLowering needs object instructions to still be present.
 * If a POST_OBJECT_LOWERING validator already ran, those instructions are confirmed
 * absent, so running VTableLowering would be wrong.
 *
 * ```kotlin
 * class VTableLowering : TargetAwareStage, PreconditionStage {
 *     override val requiresBefore: Set<PipelinePhase> = setOf(PipelinePhase.POST_OBJECT_LOWERING)
 *     override fun run(module: Module, target: Target): Module { ... }
 * }
 * ```
 */
interface PreconditionStage {
    /**
     * Set of phases that must NOT have been validated yet when this stage runs.
     * If any of these phases have already been validated, the pipeline throws.
     */
    val requiresBefore: Set<PipelinePhase>
}

interface TargetAwareStage : PipelineStage {
    /**
     * Run this stage with target information.
     */
    fun run(module: Module, target: Target): Module

    /**
     * Default [PipelineStage.run] — throws because this stage requires a target.
     * Use [Pipeline] with a target, or call [run] with target directly.
     */
    override fun run(module: Module): Module =
        error("${this::class.simpleName} requires a target — use Pipeline with a target parameter")
}

/**
 * A [PipelineStage] that declares which pipeline phase transition it performs.
 *
 * Stages that implement this interface indicate that after they run, the module
 * should satisfy the invariants of the [targetPhase]. This is purely descriptive —
 * the pipeline does not enforce phase transitions automatically.
 *
 * Post-phase validators can check these invariants to catch ordering errors early.
 */
interface PhasedStage : PipelineStage {
    val targetPhase: PipelinePhase
}

/**
 * Executes a sequence of [PipelineStage]s on a [Module].
 *
 * Stages are applied left-to-right. Each stage receives the output of the previous
 * one. [TargetAwareStage]s receive the pipeline's [target] automatically.
 *
 * ```java
 * // Java — simple pipeline, no target needed
 * var pipeline = new Pipeline();
 * pipeline.add(new Mem2Reg());
 * pipeline.add(new ConstantFolding());
 * pipeline.add(new DeadCodeElimination());
 * Module optimized = pipeline.execute(module);
 *
 * // Java — pipeline with target for lowering stages
 * var pipeline = new Pipeline(Target.x86_64());
 * pipeline.add(new VTableLowering());
 * pipeline.add(new DeadCodeElimination());
 * Module lowered = pipeline.execute(module);
 * ```
 */
/**
 * Validates that a [Module] satisfies the invariants for a [PipelinePhase].
 *
 * After a lowering stage (e.g., object lowering), add a validator to confirm
 * that the expected instructions have been removed.
 *
 * ```java
 * pipeline.add(new ObjectLowering());
 * pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING);
 * ```
 */
class PhaseValidator(val phase: PipelinePhase) : PipelineStage {
    override fun run(module: Module): Module {
        val absentSet = when (phase) {
            PipelinePhase.POST_OBJECT_LOWERING -> PipelinePhase.POST_OBJECT_LOWERING_ABSENT
            PipelinePhase.POST_RUNTIME_LOWERING -> PipelinePhase.POST_RUNTIME_LOWERING_ABSENT
            else -> emptySet()
        }
        if (absentSet.isEmpty()) {
            return module
        }

        val violations = mutableListOf<String>()
        for (function in module.functions) {
            for (block in function.blocks) {
                for (instruction in block.instructions) {
                    val simpleName = instruction::class.simpleName ?: continue
                    if (simpleName in absentSet) {
                        violations.add("${function.name}/${block.label}: $simpleName still present after $phase")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            error("Phase validation failed for $phase:\n${violations.joinToString("\n")}")
        }

        return module
    }
}

class Pipeline(
    private val stages: MutableList<PipelineStage> = mutableListOf(),
    private val stageNames: MutableList<String?> = mutableListOf(),
    val target: Target? = null,
) {
    /**
     * Construct with just a target (convenience for pipelines that need lowering stages).
     */
    constructor(target: Target) : this(mutableListOf(), mutableListOf(), target)

    /** Add an unnamed stage. */
    fun add(stage: PipelineStage): Pipeline {
        stages += stage
        stageNames += null
        return this
    }

    /** Add a named stage. Named stages can be targeted by [addAfter]/[addBefore]/[replace]/[skip]. */
    fun add(name: String, stage: PipelineStage): Pipeline {
        stages += stage
        stageNames += name
        return this
    }

    /** Insert a stage after an existing named stage. */
    fun addAfter(existingName: String, stage: PipelineStage): Pipeline {
        val index = stageNames.indexOf(existingName)
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages.add(index + 1, stage)
        stageNames.add(index + 1, null)
        return this
    }

    /** Insert a named stage after an existing named stage. */
    fun addAfter(existingName: String, newName: String, stage: PipelineStage): Pipeline {
        val index = stageNames.indexOf(existingName)
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages.add(index + 1, stage)
        stageNames.add(index + 1, newName)
        return this
    }

    /** Insert a stage before an existing named stage. */
    fun addBefore(existingName: String, stage: PipelineStage): Pipeline {
        val index = stageNames.indexOf(existingName)
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages.add(index, stage)
        stageNames.add(index, null)
        return this
    }

    /** Replace an existing named stage. */
    fun replace(existingName: String, stage: PipelineStage): Pipeline {
        val index = stageNames.indexOf(existingName)
        require(index >= 0) { "Stage '$existingName' not found in pipeline" }
        stages[index] = stage
        return this
    }

    /** Remove a named stage. */
    fun skip(name: String): Pipeline {
        val index = stageNames.indexOf(name)
        require(index >= 0) { "Stage '$name' not found in pipeline" }
        stages.removeAt(index)
        stageNames.removeAt(index)
        return this
    }

    /**
     * Add a post-phase validator. Checks that the module satisfies the invariants
     * for the given phase (e.g., no NewObject after POST_OBJECT_LOWERING).
     */
    fun addValidator(phase: PipelinePhase): Pipeline {
        stages += PhaseValidator(phase)
        stageNames += "validator:${phase.name}"
        return this
    }

    fun execute(module: Module): Module {
        val validatedPhases = mutableSetOf<PipelinePhase>()

        return stages.fold(module) { currentModule, stage ->
            // Check preconditions
            if (stage is PreconditionStage) {
                for (phase in stage.requiresBefore) {
                    if (phase in validatedPhases) {
                        error("${stage::class.simpleName} requires $phase instructions to still be present, " +
                            "but a POST_${phase.name} validator already confirmed they were removed. " +
                            "Move this stage before the $phase validator.")
                    }
                }
            }

            // Track validated phases
            if (stage is PhaseValidator) {
                validatedPhases.add(stage.phase)
            }

            // Run the stage
            when (stage) {
                is TargetAwareStage -> {
                    val resolvedTarget = target
                        ?: error("${stage::class.simpleName} requires a target — construct Pipeline with a Target")
                    stage.run(currentModule, resolvedTarget)
                }
                else -> stage.run(currentModule)
            }
        }
    }

    /** Returns a copy of the current stage list. */
    fun stages(): List<PipelineStage> = stages.toList()

    /** Returns the names of named stages in order. Unnamed stages return null. */
    fun names(): List<String?> = stageNames.toList()

    companion object {
        /**
         * Create a pipeline pre-populated with the canonical stages for the given [OptLevel].
         *
         * ```java
         * Pipeline pipeline = Pipeline.forOptLevel(OptLevel.O2);
         * Module optimized = pipeline.execute(module);
         * ```
         */
        @JvmStatic
        @JvmOverloads
        fun forOptLevel(optLevel: OptLevel, target: Target? = null): Pipeline {
            val pipeline = optLevel.pipeline()
            return if (target != null) {
                val result = Pipeline(target = target)
                for (stage in pipeline.stages()) {
                    result.add(stage)
                }
                result
            } else {
                pipeline
            }
        }
    }
}

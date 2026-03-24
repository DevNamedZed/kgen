package org.kgen.examples.api

import org.kgen.codegen.OptLevel
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.pipeline.*

/**
 * Demonstrates the Pipeline API:
 * - Creating pipelines from OptLevel presets
 * - Custom pipeline stages
 * - PipelineBuilder with named stages
 * - Phase validators
 * - Target-aware stages
 */
object PipelineExample {

    private fun buildTestModule(): Module {
        val module = ModuleBuilder("pipeline_demo", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "compute",
            listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions

            val sum = ins.add(fn.param(0), fn.param(1))
            val doubled = ins.add(sum, sum)
            val result = ins.add(doubled, Constant.I32(0))
            fn.ret(result)
        }

        module.defineFunction(NativeScope::class.java, "identity",
            listOf(Param("x", Type.I32)), Type.I32) { fn ->
            fn.ret(fn.param(0))
        }

        return module.build()
    }

    /**
     * Use a preset pipeline from OptLevel.
     */
    @JvmStatic
    fun presetPipeline() {
        val module = buildTestModule()

        val pipeline = Pipeline.forOptLevel(OptLevel.O2)
        val optimized = pipeline.execute(module)

        println("Before: ${module.functions[0].blocks.flatMap { it.instructions }.size} instructions")
        println("After:  ${optimized.functions[0].blocks.flatMap { it.instructions }.size} instructions")
    }

    /**
     * Build a pipeline manually from individual stages.
     */
    @JvmStatic
    fun manualPipeline() {
        val module = buildTestModule()

        val pipeline = Pipeline()
        pipeline.add(Mem2Reg())
        pipeline.add(ConstantFolding())
        pipeline.add(DeadCodeElimination())

        val optimized = pipeline.execute(module)
        println("Manual pipeline: ${optimized.functions.size} functions")
    }

    /**
     * Use a custom inline stage (lambda).
     */
    @JvmStatic
    fun customStage() {
        val module = buildTestModule()

        var stageCount = 0
        val pipeline = Pipeline()
        pipeline.add(PipelineStage { currentModule ->
            stageCount++
            println("Custom stage running on module '${currentModule.name}' with ${currentModule.functions.size} functions")
            currentModule
        })
        pipeline.add(DeadCodeElimination())

        pipeline.execute(module)
        println("Custom stage ran $stageCount time(s)")
    }

    /**
     * Use PipelineBuilder to customize a preset pipeline.
     */
    @JvmStatic
    fun pipelineBuilder() {
        val module = buildTestModule()

        val pipeline = PipelineBuilder.forOptLevel(OptLevel.O2)
            .skip("loop-invariant-code-motion")
            .addAfter("inlining", "my-analysis", PipelineStage { currentModule ->
                println("  [my-analysis] Module has ${currentModule.functions.size} functions")
                currentModule
            })
            .build()

        println("Pipeline stages: ${pipeline.names().filterNotNull()}")
        pipeline.execute(module)
    }

    /**
     * Use named stages on Pipeline directly.
     */
    @JvmStatic
    fun namedStages() {
        val module = buildTestModule()

        val pipeline = Pipeline()
        pipeline.add("mem2reg", Mem2Reg())
        pipeline.add("fold", ConstantFolding())
        pipeline.add("dce", DeadCodeElimination())

        println("Before skip: ${pipeline.names()}")
        pipeline.skip("fold")
        println("After skip: ${pipeline.names()}")

        pipeline.execute(module)
    }

    /**
     * Add a phase validator to catch lowering violations.
     */
    @JvmStatic
    fun phaseValidation() {
        val module = buildTestModule()

        val pipeline = Pipeline()
        pipeline.add(ConstantFolding())
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        pipeline.add(DeadCodeElimination())

        val result = pipeline.execute(module)
        println("Phase validation passed — module has ${result.functions.size} functions")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Preset Pipeline ===")
        presetPipeline()

        println("\n=== Manual Pipeline ===")
        manualPipeline()

        println("\n=== Custom Stage ===")
        customStage()

        println("\n=== PipelineBuilder ===")
        pipelineBuilder()

        println("\n=== Named Stages ===")
        namedStages()

        println("\n=== Phase Validation ===")
        phaseValidation()
    }
}

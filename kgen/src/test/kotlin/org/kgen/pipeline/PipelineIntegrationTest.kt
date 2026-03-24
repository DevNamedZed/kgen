package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.codegen.OptLevel
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelineIntegrationTest {

    private fun buildSimpleModule(): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("identity", listOf(Param("x", Type.I32)), Type.I32)
        fn.ret(fn.param(0))
        fn.end()
        return builder.build()
    }

    private fun buildModuleWithNewObject(): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("createWidget", emptyList(), Type.OpaquePointer)
        val ins = fn.instructions
        val widget = ins.newObject("Widget")
        fn.ret(widget)
        fn.end()
        return builder.build()
    }

    private fun buildModuleWithGetField(): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("readField", listOf(Param("obj", Type.OpaquePointer)), Type.I32)
        val ins = fn.instructions
        val value = ins.getField(fn.param(0), "Widget", "count", Type.I32)
        fn.ret(value)
        fn.end()
        return builder.build()
    }

    // --- Pipeline.forOptLevel(O0) produces empty pipeline ---

    @Test
    fun forOptLevelO0ProducesEmptyPipeline() {
        val pipeline = Pipeline.forOptLevel(OptLevel.O0)
        assertTrue(pipeline.stages().isEmpty())
    }

    @Test
    fun forOptLevelO0ExecutesIdentity() {
        val module = buildSimpleModule()
        val pipeline = Pipeline.forOptLevel(OptLevel.O0)
        val result = pipeline.execute(module)
        assertEquals("test", result.name)
        assertEquals(1, result.functions.size)
    }

    // --- Pipeline.forOptLevel(O1) has mem2reg, constant-folding, dce ---

    @Test
    fun forOptLevelO1HasExpectedStages() {
        val pipeline = Pipeline.forOptLevel(OptLevel.O1)
        val stages = pipeline.stages()
        assertTrue(stages.isNotEmpty())
        assertTrue(stages.size >= 3, "O1 should have at least 3 stages")
    }

    @Test
    fun forOptLevelO1Executes() {
        val module = buildSimpleModule()
        val pipeline = Pipeline.forOptLevel(OptLevel.O1)
        val result = pipeline.execute(module)
        assertNotNull(result)
        assertEquals("test", result.name)
    }

    // --- Pipeline.forOptLevel(O2) has all expected stages ---

    @Test
    fun forOptLevelO2HasMoreStagesThanO1() {
        val pipelineO1 = Pipeline.forOptLevel(OptLevel.O1)
        val pipelineO2 = Pipeline.forOptLevel(OptLevel.O2)
        assertTrue(pipelineO2.stages().size > pipelineO1.stages().size,
            "O2 should have more stages than O1")
    }

    @Test
    fun forOptLevelO2Executes() {
        val module = buildSimpleModule()
        val pipeline = Pipeline.forOptLevel(OptLevel.O2)
        val result = pipeline.execute(module)
        assertNotNull(result)
    }

    // --- Pipeline with target propagates to TargetAwareStage ---

    @Test
    fun pipelineWithTargetPropagates() {
        val module = buildSimpleModule()
        var receivedTarget: Target? = null

        val stage = object : TargetAwareStage {
            override fun run(module: Module, target: Target): Module {
                receivedTarget = target
                return module
            }
        }

        val pipeline = Pipeline(Target.x86_64())
        pipeline.add(stage)
        pipeline.execute(module)

        assertNotNull(receivedTarget)
        assertEquals(Arch.X86_64, receivedTarget!!.arch)
    }

    // --- Pipeline without target throws for TargetAwareStage ---

    @Test
    fun pipelineWithoutTargetThrowsForTargetAwareStage() {
        val module = buildSimpleModule()

        val stage = object : TargetAwareStage {
            override fun run(module: Module, target: Target): Module = module
        }

        val pipeline = Pipeline()
        pipeline.add(stage)

        assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
    }

    // --- PhaseValidator catches NewObject after POST_OBJECT_LOWERING ---

    @Test
    fun phaseValidatorCatchesNewObjectAfterObjectLowering() {
        val module = buildModuleWithNewObject()

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)

        val error = assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
        assertTrue(error.message!!.contains("NewObject"))
        assertTrue(error.message!!.contains("POST_OBJECT_LOWERING"))
    }

    // --- PhaseValidator catches GetField after POST_OBJECT_LOWERING ---

    @Test
    fun phaseValidatorCatchesGetFieldAfterObjectLowering() {
        val module = buildModuleWithGetField()

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)

        val error = assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
        assertTrue(error.message!!.contains("GetField"))
    }

    // --- PhaseValidator passes clean module ---

    @Test
    fun phaseValidatorPassesCleanModule() {
        val module = buildSimpleModule()

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        val result = pipeline.execute(module)
        assertEquals("test", result.name)
    }

    // --- PreconditionStage catches wrong ordering ---

    @Test
    fun preconditionStageCatchesWrongOrdering() {
        val module = buildSimpleModule()

        val stageWithPrecondition = object : PipelineStage, PreconditionStage {
            override val requiresBefore = setOf(PipelinePhase.POST_OBJECT_LOWERING)
            override fun run(module: Module): Module = module
        }

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        pipeline.add(stageWithPrecondition)

        val error = assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
        assertTrue(error.message!!.contains("POST_OBJECT_LOWERING"))
    }

    // --- PreconditionStage passes correct ordering ---

    @Test
    fun preconditionStagePassesCorrectOrdering() {
        val module = buildSimpleModule()

        val stageWithPrecondition = object : PipelineStage, PreconditionStage {
            override val requiresBefore = setOf(PipelinePhase.POST_OBJECT_LOWERING)
            override fun run(module: Module): Module = module
        }

        val pipeline = Pipeline()
        pipeline.add(stageWithPrecondition)
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)

        val result = pipeline.execute(module)
        assertEquals("test", result.name)
    }

    // --- Named stages: add, addAfter, addBefore, replace, skip ---

    @Test
    fun namedStagesAddAndRetrieve() {
        val pipeline = Pipeline()
        pipeline.add("first", PipelineStage { m -> m })
        pipeline.add("second", PipelineStage { m -> m })

        val names = pipeline.names()
        assertEquals(listOf("first", "second"), names)
    }

    @Test
    fun namedStagesAddAfter() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("alpha", PipelineStage { m -> executionOrder.add("alpha"); m })
        pipeline.add("gamma", PipelineStage { m -> executionOrder.add("gamma"); m })
        pipeline.addAfter("alpha", PipelineStage { m -> executionOrder.add("beta"); m })

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("alpha", "beta", "gamma"), executionOrder)
    }

    @Test
    fun namedStagesAddBefore() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("alpha", PipelineStage { m -> executionOrder.add("alpha"); m })
        pipeline.add("gamma", PipelineStage { m -> executionOrder.add("gamma"); m })
        pipeline.addBefore("gamma", PipelineStage { m -> executionOrder.add("beta"); m })

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("alpha", "beta", "gamma"), executionOrder)
    }

    @Test
    fun namedStagesReplace() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("first", PipelineStage { m -> executionOrder.add("original"); m })
        pipeline.replace("first", PipelineStage { m -> executionOrder.add("replaced"); m })

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("replaced"), executionOrder)
    }

    @Test
    fun namedStagesSkip() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("first", PipelineStage { m -> executionOrder.add("first"); m })
        pipeline.add("second", PipelineStage { m -> executionOrder.add("second"); m })
        pipeline.add("third", PipelineStage { m -> executionOrder.add("third"); m })
        pipeline.skip("second")

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("first", "third"), executionOrder)
    }

    // --- Pipeline.names() returns correct names ---

    @Test
    fun pipelineNamesReturnsCorrectNames() {
        val pipeline = Pipeline()
        pipeline.add("mem2reg", PipelineStage { m -> m })
        pipeline.add(PipelineStage { m -> m })
        pipeline.add("dce", PipelineStage { m -> m })

        val names = pipeline.names()
        assertEquals(3, names.size)
        assertEquals("mem2reg", names[0])
        assertEquals(null, names[1])
        assertEquals("dce", names[2])
    }

    // --- Pipeline addAfter nonexistent throws ---

    @Test
    fun addAfterNonexistentStageThrows() {
        val pipeline = Pipeline()
        pipeline.add("existing", PipelineStage { m -> m })

        assertThrows<IllegalArgumentException> {
            pipeline.addAfter("nonexistent", PipelineStage { m -> m })
        }
    }

    // --- Multiple validators in one pipeline ---

    @Test
    fun multipleValidatorsInOnePipeline() {
        val module = buildSimpleModule()

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        pipeline.addValidator(PipelinePhase.POST_RUNTIME_LOWERING)

        val result = pipeline.execute(module)
        assertEquals("test", result.name)
    }

    // --- Mix of named and unnamed stages ---

    @Test
    fun mixOfNamedAndUnnamedStages() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("named-first", PipelineStage { m -> executionOrder.add("named-first"); m })
        pipeline.add(PipelineStage { m -> executionOrder.add("unnamed"); m })
        pipeline.add("named-last", PipelineStage { m -> executionOrder.add("named-last"); m })

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("named-first", "unnamed", "named-last"), executionOrder)
    }

    // --- Pipeline preserves module immutability ---

    @Test
    fun pipelinePreservesInputModuleImmutability() {
        val module = buildSimpleModule()
        val originalFunctionCount = module.functions.size
        val originalName = module.name

        val pipeline = Pipeline()
        pipeline.add(PipelineStage { m ->
            m.copy(functions = m.functions + IrFunction(
                "injected", emptyList(), Type.Void, emptyList(), isExternal = true,
            ))
        })

        val result = pipeline.execute(module)

        assertEquals(originalFunctionCount, module.functions.size,
            "Original module should not be modified")
        assertEquals(originalName, module.name)
        assertEquals(originalFunctionCount + 1, result.functions.size,
            "Result module should have the new function")
    }

    // --- PipelineBuilder.forOptLevel skips stages correctly ---

    @Test
    fun pipelineBuilderForOptLevelSkipsCorrectly() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O2)
            .skip("loop-invariant-code-motion")

        val names = builder.stageNames()
        assertFalse(names.contains("loop-invariant-code-motion"),
            "Skipped stage should not be present")
        assertTrue(names.contains("mem2reg"),
            "Non-skipped stages should remain")
    }

    // --- PipelineBuilder.forOptLevel adds stages correctly ---

    @Test
    fun pipelineBuilderForOptLevelAddsStageAfter() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O2)
            .addAfter("inlining", "custom-pass", PipelineStage { m -> m })

        val names = builder.stageNames()
        val inliningIndex = names.indexOf("inlining")
        val customIndex = names.indexOf("custom-pass")
        assertTrue(customIndex == inliningIndex + 1,
            "Custom pass should be immediately after inlining")
    }

    @Test
    fun pipelineBuilderForOptLevelO0IsEmpty() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O0)
        assertTrue(builder.stageNames().isEmpty())
    }

    @Test
    fun pipelineBuilderForOptLevelO1HasBasicStages() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O1)
        val names = builder.stageNames()
        assertTrue(names.contains("mem2reg"))
        assertTrue(names.contains("constant-folding"))
        assertTrue(names.contains("dead-code-elimination"))
    }

    // --- Pipeline with addAfter using named overload ---

    @Test
    fun addAfterWithNameOnPipeline() {
        val executionOrder = mutableListOf<String>()

        val pipeline = Pipeline()
        pipeline.add("alpha", PipelineStage { m -> executionOrder.add("alpha"); m })
        pipeline.add("gamma", PipelineStage { m -> executionOrder.add("gamma"); m })
        pipeline.addAfter("alpha", "beta", PipelineStage { m -> executionOrder.add("beta"); m })

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("alpha", "beta", "gamma"), executionOrder)

        val names = pipeline.names()
        assertTrue(names.contains("beta"))
    }

    // --- PipelineBuilder with target ---

    @Test
    fun pipelineBuilderWithTargetPropagates() {
        val pipeline = PipelineBuilder.forOptLevel(OptLevel.O2, Target.arm64()).build()
        assertEquals(Arch.ARM64, pipeline.target!!.arch)
    }

    // --- Verify O2/O3 equivalence ---

    @Test
    fun o3HasSameStagesAsO2() {
        val pipelineO2 = Pipeline.forOptLevel(OptLevel.O2)
        val pipelineO3 = Pipeline.forOptLevel(OptLevel.O3)
        assertEquals(pipelineO2.stages().size, pipelineO3.stages().size,
            "O3 should have the same stages as O2")
    }
}

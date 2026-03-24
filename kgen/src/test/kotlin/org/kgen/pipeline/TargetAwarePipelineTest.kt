package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TargetAwarePipelineTest {

    private fun buildSimpleModule(): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("identity", listOf(Param("x", Type.I32)), Type.I32)
        fn.ret(fn.param(0))
        fn.end()
        return builder.build()
    }

    @Test
    fun pipelineWithTarget() {
        val module = buildSimpleModule()

        var targetSeen: Target? = null
        val stage = object : TargetAwareStage {
            override fun run(module: Module, target: Target): Module {
                targetSeen = target
                return module
            }
        }

        val pipeline = Pipeline(Target.x86_64())
        pipeline.add(stage)
        val result = pipeline.execute(module)

        assertNotNull(targetSeen)
        assertEquals(Arch.X86_64, targetSeen!!.arch)
        assertEquals("test", result.name)
    }

    @Test
    fun targetAwareStageThrowsWithoutTarget() {
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

    @Test
    fun mixedPipelineWithTargetAndRegularStages() {
        val module = buildSimpleModule()
        val executionOrder = mutableListOf<String>()

        val regularStage = PipelineStage { m ->
            executionOrder.add("regular")
            m
        }

        val targetStage = object : TargetAwareStage {
            override fun run(module: Module, target: Target): Module {
                executionOrder.add("target-aware:${target.arch}")
                return module
            }
        }

        val pipeline = Pipeline(Target.arm64())
        pipeline.add(regularStage)
        pipeline.add(targetStage)
        pipeline.add(regularStage)
        pipeline.execute(module)

        assertEquals(listOf("regular", "target-aware:ARM64", "regular"), executionOrder)
    }

    @Test
    fun pipelineTargetConstructor() {
        val pipeline = Pipeline(Target.x86_64())
        assertEquals(Arch.X86_64, pipeline.target!!.arch)
    }

    @Test
    fun moduleProfileField() {
        val module = ModuleBuilder("test", TargetProfile.NATIVE).build()
        assertEquals(TargetProfile.NATIVE, module.profile)
    }

    @Test
    fun targetProfileMethod() {
        assertEquals(TargetProfile.NATIVE, Target.x86_64().profile())
        assertEquals(TargetProfile.NATIVE, Target.arm64().profile())
        assertEquals(TargetProfile.MANAGED_VM, Target.jvm().profile())
        assertEquals(TargetProfile.NATIVE, Target.wasm().profile())
    }

    @Test
    fun targetProfileCompatibility() {
        assertTrue(TargetProfile.NATIVE.isCompatibleWith(TargetProfile.MIXED))
        assertTrue(TargetProfile.NATIVE.isCompatibleWith(TargetProfile.ANY))
        assertTrue(TargetProfile.MANAGED_VM.isCompatibleWith(TargetProfile.ANY))
    }

    @Test
    fun phaseValidatorPassesOnCleanModule() {
        val module = buildSimpleModule()
        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        val result = pipeline.execute(module)
        assertEquals("test", result.name)
    }

    @Test
    fun preconditionCatchesOrderingError() {
        val module = buildSimpleModule()

        val stageWithPrecondition = object : PipelineStage, PreconditionStage {
            override val requiresBefore = setOf(PipelinePhase.POST_OBJECT_LOWERING)
            override fun run(module: Module): Module = module
        }

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)
        pipeline.add(stageWithPrecondition)

        val error = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
        assertTrue(error.message!!.contains("POST_OBJECT_LOWERING"))
    }

    @Test
    fun preconditionPassesWhenOrderIsCorrect() {
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

    @Test
    fun phaseValidatorFailsOnViolation() {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("createObj", emptyList(), Type.OpaquePointer)
        val ins = fn.instructions
        val obj = ins.newObject("Widget")
        fn.ret(obj)
        fn.end()
        val module = builder.build()

        val pipeline = Pipeline()
        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)

        val error = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            pipeline.execute(module)
        }
        assertTrue(error.message!!.contains("NewObject"))
        assertTrue(error.message!!.contains("POST_OBJECT_LOWERING"))
    }
}

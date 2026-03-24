package org.kgen.pipeline

import org.kgen.codegen.OptLevel

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PipelineBuilderTest {

    private fun buildSimpleModule(): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        val fn = builder.function("identity", listOf(Param("x", Type.I32)), Type.I32)
        fn.ret(fn.param(0))
        fn.end()
        return builder.build()
    }

    @Test
    fun emptyBuilder() {
        val pipeline = PipelineBuilder.create().build()
        val module = buildSimpleModule()
        val result = pipeline.execute(module)
        assertEquals("test", result.name)
    }

    @Test
    fun addStage() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.create()
            .add("stage-1", PipelineStage { m -> executed.add("1"); m })
            .add("stage-2", PipelineStage { m -> executed.add("2"); m })
            .build()

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("1", "2"), executed)
    }

    @Test
    fun addAfter() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.create()
            .add("a", PipelineStage { m -> executed.add("a"); m })
            .add("b", PipelineStage { m -> executed.add("b"); m })
            .addAfter("a", "inserted", PipelineStage { m -> executed.add("inserted"); m })
            .build()

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("a", "inserted", "b"), executed)
    }

    @Test
    fun addBefore() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.create()
            .add("a", PipelineStage { m -> executed.add("a"); m })
            .add("b", PipelineStage { m -> executed.add("b"); m })
            .addBefore("b", "inserted", PipelineStage { m -> executed.add("inserted"); m })
            .build()

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("a", "inserted", "b"), executed)
    }

    @Test
    fun replaceStage() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.create()
            .add("a", PipelineStage { m -> executed.add("original-a"); m })
            .add("b", PipelineStage { m -> executed.add("b"); m })
            .replace("a", PipelineStage { m -> executed.add("replaced-a"); m })
            .build()

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("replaced-a", "b"), executed)
    }

    @Test
    fun skipStage() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.create()
            .add("a", PipelineStage { m -> executed.add("a"); m })
            .add("b", PipelineStage { m -> executed.add("b"); m })
            .add("c", PipelineStage { m -> executed.add("c"); m })
            .skip("b")
            .build()

        pipeline.execute(buildSimpleModule())
        assertEquals(listOf("a", "c"), executed)
    }

    @Test
    fun addAfterNonExistentThrows() {
        assertThrows<IllegalArgumentException> {
            PipelineBuilder.create()
                .addAfter("nonexistent", "new", PipelineStage { m -> m })
        }
    }

    @Test
    fun addBeforeNonExistentThrows() {
        assertThrows<IllegalArgumentException> {
            PipelineBuilder.create()
                .addBefore("nonexistent", "new", PipelineStage { m -> m })
        }
    }

    @Test
    fun replaceNonExistentThrows() {
        assertThrows<IllegalArgumentException> {
            PipelineBuilder.create()
                .replace("nonexistent", PipelineStage { m -> m })
        }
    }

    @Test
    fun skipNonExistentThrows() {
        assertThrows<IllegalArgumentException> {
            PipelineBuilder.create()
                .skip("nonexistent")
        }
    }

    @Test
    fun stageNames() {
        val builder = PipelineBuilder.create()
            .add("alpha", PipelineStage { m -> m })
            .add("beta", PipelineStage { m -> m })
            .add("gamma", PipelineStage { m -> m })

        assertEquals(listOf("alpha", "beta", "gamma"), builder.stageNames())
    }

    @Test
    fun forOptLevelO0IsEmpty() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O0)
        assertTrue(builder.stageNames().isEmpty())
    }

    @Test
    fun forOptLevelO1HasBasicStages() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O1)
        val names = builder.stageNames()
        assertTrue(names.contains("mem2reg"))
        assertTrue(names.contains("constant-folding"))
        assertTrue(names.contains("dead-code-elimination"))
    }

    @Test
    fun forOptLevelO2HasAdvancedStages() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O2)
        val names = builder.stageNames()
        assertTrue(names.contains("mem2reg"))
        assertTrue(names.contains("inlining"))
        assertTrue(names.contains("global-value-numbering"))
        assertTrue(names.contains("jump-threading"))
        assertTrue(names.contains("loop-invariant-code-motion"))
    }

    @Test
    fun forOptLevelO2WithTarget() {
        val builder = PipelineBuilder.forOptLevel(OptLevel.O2, Target.x86_64())
        val pipeline = builder.build()
        assertEquals(Target.x86_64().arch, pipeline.target!!.arch)
    }

    @Test
    fun forOptLevelO2SkipAndCustomize() {
        val executed = mutableListOf<String>()
        val pipeline = PipelineBuilder.forOptLevel(OptLevel.O2)
            .skip("loop-invariant-code-motion")
            .addAfter("inlining", "my-custom-pass", PipelineStage { m ->
                executed.add("custom")
                m
            })
            .build()

        assertFalse(pipeline.execute(buildSimpleModule()).name.isEmpty())
        assertTrue(executed.contains("custom"))
    }

    @Test
    fun builderWithTargetPropagates() {
        val pipeline = PipelineBuilder.create(Target.arm64())
            .add("noop", PipelineStage { m -> m })
            .build()

        assertEquals(Target.arm64().arch, pipeline.target!!.arch)
    }
}

package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class JumpThreadingTest {

    private val jt = JumpThreading()

    private fun buildAndThread(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return jt.run(ir.build())
    }

    @Test
    fun `folds constant true condition and merges`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        // entry → then (merged), else is dead
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge entry+then, dead code removed: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
        assertEquals(1, ((last as Ret).value as Constant.I32).value, "Should take true branch")
    }

    @Test
    fun `folds constant false condition and merges`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge entry+else, dead code removed: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
        assertEquals(2, ((last as Ret).value as Constant.I32).value, "Should take false branch")
    }

    @Test
    fun `simplifies same-target condBr and merges`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("target"), BlockRef("target"))

            appendBlock("target")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        // Same target → br → merge → single block
        assertEquals(1, blocks.size, "Should merge into one block: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
    }

    @Test
    fun `merges single-predecessor blocks`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("middle"))

            appendBlock("middle")
            br(BlockRef("end"))

            appendBlock("end")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "All blocks should merge into one: ${blocks.map { it.label }}")
    }

    @Test
    fun `removes unreachable blocks`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("live"), BlockRef("dead"))

            appendBlock("live")
            ret(Constant.I32(1))

            appendBlock("dead")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        val labels = blocks.map { it.label }
        assertFalse("dead" in labels, "Dead block should be removed: $labels")
    }

    @Test
    fun `preserves non-constant condBr`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val entry = module.functions[0].blocks[0]
        val last = entry.instructions.last()
        assertTrue(last is CondBr, "Dynamic condBr should be preserved: $last")
    }

    @Test
    fun `does not merge block with multiple predecessors`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            br(BlockRef("merge"))

            appendBlock("b")
            br(BlockRef("merge"))

            appendBlock("merge")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.any { it.label == "merge" }, "Merge block has 2 preds, should not be merged")
    }

    @Test
    fun `chain of constant branches simplified`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("a"), BlockRef("dead1"))

            appendBlock("a")
            condBr(Constant.I1(false), BlockRef("dead2"), BlockRef("b"))

            appendBlock("b")
            ret(Constant.I32(42))

            appendBlock("dead1")
            ret(Constant.I32(0))

            appendBlock("dead2")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Chain should merge into one block: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
        assertEquals(42, ((last as Ret).value as Constant.I32).value)
    }

    @Test
    fun `skips external functions`() {
        val module = buildAndThread {
            declareFunction("ext", emptyList(), Type.I32)
        }
        assertTrue(module.functions[0].isExternal)
    }
}

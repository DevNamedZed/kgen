package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class JumpThreadingTest {

    private val jt = JumpThreading()

    private fun buildAndThread(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return jt.run(ir.build())
    }

    @Test
    fun `folds constant true condition and merges`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(Constant.I1(true), "then", "else")

            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(2))

            finalizeFunction()
        }
        // entry → then (merged), else is dead
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge entry+then, dead code removed: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Instruction.Ret)
        assertEquals(1, ((last as Instruction.Ret).value as Constant.I32).value, "Should take true branch")
    }

    @Test
    fun `folds constant false condition and merges`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(Constant.I1(false), "then", "else")

            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge entry+else, dead code removed: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Instruction.Ret)
        assertEquals(2, ((last as Instruction.Ret).value as Constant.I32).value, "Should take false branch")
    }

    @Test
    fun `simplifies same-target condBr and merges`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(params[0], "target", "target")

            positionAtEnd(appendBlock("target"))
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        // Same target → br → merge → single block
        assertEquals(1, blocks.size, "Should merge into one block: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Instruction.Ret)
    }

    @Test
    fun `merges single-predecessor blocks`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            br("middle")

            positionAtEnd(appendBlock("middle"))
            br("end")

            positionAtEnd(appendBlock("end"))
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
            positionAtEnd(appendBlock("entry"))
            condBr(Constant.I1(true), "live", "dead")

            positionAtEnd(appendBlock("live"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("dead"))
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
            positionAtEnd(appendBlock("entry"))
            condBr(params[0], "then", "else")

            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val entry = module.functions[0].blocks[0]
        val last = entry.instructions.last()
        assertTrue(last is Instruction.CondBr, "Dynamic condBr should be preserved: $last")
    }

    @Test
    fun `does not merge block with multiple predecessors`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(params[0], "a", "b")

            positionAtEnd(appendBlock("a"))
            br("merge")

            positionAtEnd(appendBlock("b"))
            br("merge")

            positionAtEnd(appendBlock("merge"))
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
            positionAtEnd(appendBlock("entry"))
            condBr(Constant.I1(true), "a", "dead1")

            positionAtEnd(appendBlock("a"))
            condBr(Constant.I1(false), "dead2", "b")

            positionAtEnd(appendBlock("b"))
            ret(Constant.I32(42))

            positionAtEnd(appendBlock("dead1"))
            ret(Constant.I32(0))

            positionAtEnd(appendBlock("dead2"))
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Chain should merge into one block: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Instruction.Ret)
        assertEquals(42, ((last as Instruction.Ret).value as Constant.I32).value)
    }

    @Test
    fun `skips external functions`() {
        val module = buildAndThread {
            declareFunction("ext", emptyList(), Type.I32)
        }
        assertTrue(module.functions[0].isExternal)
    }
}

package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class JumpThreadingExtendedTest {

    private val jt = JumpThreading()

    private fun buildAndThread(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return jt.run(ir.build())
    }

    @Test
    fun `folds true then merges two blocks`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("a"), BlockRef("dead"))

            appendBlock("a")
            val v = add(Constant.I32(1), Constant.I32(2))
            ret(v)

            appendBlock("dead")
            ret(Constant.I32(99))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].instructions.any { it is Add })
    }

    @Test
    fun `merges long chain of unconditional branches`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("a"))

            appendBlock("a")
            br(BlockRef("b"))

            appendBlock("b")
            br(BlockRef("c"))

            appendBlock("c")
            br(BlockRef("d"))

            appendBlock("d")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
        assertEquals(42, ((last as Ret).value as Constant.I32).value)
    }

    @Test
    fun `preserves blocks with multiple predecessors`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            br(BlockRef("join"))

            appendBlock("b")
            br(BlockRef("join"))

            appendBlock("join")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.any { it.label == "join" }, "Join block should remain: ${blocks.map { it.label }}")
    }

    @Test
    fun `removes both dead branches with constant true`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("live"), BlockRef("dead1"))

            appendBlock("live")
            ret(Constant.I32(1))

            appendBlock("dead1")
            condBr(Constant.I1(false), BlockRef("dead2"), BlockRef("dead3"))

            appendBlock("dead2")
            ret(Constant.I32(2))

            appendBlock("dead3")
            ret(Constant.I32(3))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        val labels = blocks.map { it.label }
        assertFalse("dead1" in labels)
        assertFalse("dead2" in labels)
        assertFalse("dead3" in labels)
    }

    @Test
    fun `preserves dynamic condition with multiple targets`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c1", Type.I1), Param("c2", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            condBr(params[1], BlockRef("aa"), BlockRef("ab"))

            appendBlock("aa")
            ret(Constant.I32(1))

            appendBlock("ab")
            ret(Constant.I32(2))

            appendBlock("b")
            ret(Constant.I32(3))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.size >= 4, "Dynamic branches should be preserved")
    }

    @Test
    fun `merges block with single predecessor after folding`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("dead"), BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(0))

            appendBlock("live")
            br(BlockRef("end"))

            appendBlock("end")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
    }

    @Test
    fun `handles single block function`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
    }

    @Test
    fun `handles multiple functions`() {
        val module = buildAndThread {
            createFunction("f1", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("a"), BlockRef("b"))
            appendBlock("a")
            ret(Constant.I32(1))
            appendBlock("b")
            ret(Constant.I32(2))
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("a"), BlockRef("b"))
            appendBlock("a")
            ret(Constant.I32(3))
            appendBlock("b")
            ret(Constant.I32(4))
            finalizeFunction()
        }
        assertEquals(1, module.functions[0].blocks.size)
        assertEquals(1, module.functions[1].blocks.size)
        assertEquals(1, ((module.functions[0].blocks[0].instructions.last() as Ret).value as Constant.I32).value)
        assertEquals(4, ((module.functions[1].blocks[0].instructions.last() as Ret).value as Constant.I32).value)
    }

    @Test
    fun `merges entry with single unconditional successor`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val v = add(Constant.I32(1), Constant.I32(2))
            br(BlockRef("exit"))

            appendBlock("exit")
            ret(v)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].instructions.any { it is Add })
        assertTrue(blocks[0].instructions.last() is Ret)
    }

    @Test
    fun `preserves instructions in merged blocks`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            br(BlockRef("middle"))

            appendBlock("middle")
            val b = mul(a, Constant.I32(3))
            br(BlockRef("end"))

            appendBlock("end")
            ret(b)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        val insts = blocks[0].instructions
        assertTrue(insts.any { it is Add })
        assertTrue(insts.any { it is Mul })
        assertTrue(insts.last() is Ret)
    }

    @Test
    fun `false branch folding preserves code`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("dead"), BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(0))

            appendBlock("live")
            val v = add(Constant.I32(5), Constant.I32(10))
            ret(v)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].instructions.any { it is Add })
    }

    @Test
    fun `same target condBr with instructions before branch`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1), Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val v = add(params[1], Constant.I32(1))
            condBr(params[0], BlockRef("target"), BlockRef("target"))

            appendBlock("target")
            ret(v)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].instructions.any { it is Add })
    }

    @Test
    fun `mixed constant and dynamic branches`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("a"), BlockRef("dead"))

            appendBlock("dead")
            ret(Constant.I32(0))

            appendBlock("a")
            condBr(params[0], BlockRef("b"), BlockRef("c"))

            appendBlock("b")
            ret(Constant.I32(1))

            appendBlock("c")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertFalse(blocks.any { it.label == "dead" })
        // a's dynamic condBr should remain
        val blockLabels = blocks.map { it.label }.toSet()
        assertTrue("b" in blockLabels || blocks.size <= 3)
    }

    @Test
    fun `deeply nested constant folding`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("a"), BlockRef("d1"))

            appendBlock("a")
            condBr(Constant.I1(true), BlockRef("b"), BlockRef("d2"))

            appendBlock("b")
            condBr(Constant.I1(true), BlockRef("c"), BlockRef("d3"))

            appendBlock("c")
            ret(Constant.I32(100))

            appendBlock("d1")
            ret(Constant.I32(0))

            appendBlock("d2")
            ret(Constant.I32(0))

            appendBlock("d3")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertEquals(100, ((blocks[0].instructions.last() as Ret).value as Constant.I32).value)
    }

    @Test
    fun `preserves loop structure with dynamic condition`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("loop"))

            appendBlock("loop")
            val cmp = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(cmp, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.any { it.label == "loop" }, "Loop block should remain")
        assertTrue(blocks.any { it.label == "exit" }, "Exit block should remain")
    }

    @Test
    fun `idempotent on already optimized code`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            ret(Constant.I32(1))

            appendBlock("b")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val result = jt.run(module)
        assertEquals(module.functions[0].blocks.size, result.functions[0].blocks.size)
    }

    @Test
    fun `handles void return in merged block`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            br(BlockRef("a"))

            appendBlock("a")
            br(BlockRef("b"))

            appendBlock("b")
            ret(null)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].instructions.last() is Ret)
    }

    @Test
    fun `constant false in nested branch`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            condBr(Constant.I1(false), BlockRef("dead"), BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(0))

            appendBlock("live")
            ret(Constant.I32(1))

            appendBlock("b")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertFalse(blocks.any { it.label == "dead" })
    }

    @Test
    fun `preserves switch instruction`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(params[0], BlockRef("default"), listOf(Constant.I32(0) to BlockRef("c0"), Constant.I32(1) to BlockRef("c1")))

            appendBlock("c0")
            ret(Constant.I32(10))

            appendBlock("c1")
            ret(Constant.I32(20))

            appendBlock("default")
            ret(Constant.I32(30))

            finalizeFunction()
        }
        val entry = module.functions[0].blocks[0]
        assertTrue(entry.instructions.last() is Switch)
    }

    @Test
    fun `merges br target that is only reached from one place`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val v = add(params[0], Constant.I32(1))
            br(BlockRef("only"))

            appendBlock("only")
            val w = mul(v, Constant.I32(2))
            ret(w)

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
    }

    @Test
    fun `folds same target condBr to unconditional br`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("same"), BlockRef("same"))

            appendBlock("same")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
    }

    @Test
    fun `folds constant false to false branch`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("dead"), BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(0))

            appendBlock("live")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size)
        assertEquals(42, ((blocks[0].instructions.last() as Ret).value as Constant.I32).value)
    }

    @Test
    fun `preserves block used by switch and condBr`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[1], BlockRef("a"), BlockRef("b"))

            appendBlock("a")
            switch(params[0], BlockRef("default"), listOf(Constant.I32(0) to BlockRef("b")))

            appendBlock("b")
            ret(Constant.I32(1))

            appendBlock("default")
            ret(Constant.I32(2))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.any { it.label == "b" }, "Block b has multiple predecessors")
    }

    @Test
    fun `handles diamond with no constant folding`() {
        val module = buildAndThread {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("left"), BlockRef("right"))

            appendBlock("left")
            br(BlockRef("exit"))

            appendBlock("right")
            br(BlockRef("exit"))

            appendBlock("exit")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertTrue(blocks.any { it.label == "exit" }, "Exit block has two predecessors so should remain")
    }

    @Test
    fun `handles function with unreachable`() {
        val module = buildAndThread {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("live"), BlockRef("dead"))

            appendBlock("live")
            ret(null)

            appendBlock("dead")
            unreachable()

            finalizeFunction()
        }
        val blocks = module.functions[0].blocks
        assertFalse(blocks.any { it.label == "dead" })
    }
}

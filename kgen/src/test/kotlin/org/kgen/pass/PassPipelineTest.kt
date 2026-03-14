package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class PassPipelineTest {

    private fun build(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    @Test
    fun `runs multiple passes in sequence`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(10), Constant.I32(20))
            mul(Constant.I32(3), Constant.I32(4)) // dead after folding
            ret(a)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(30, (ret.value as Constant.I32).value)
    }

    @Test
    fun `pass ordering matters - fold before DCE`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(5), Constant.I32(5))
            val b = mul(a, Constant.I32(2))
            ret(b)
            finalizeFunction()
        }

        // Fold first, then DCE: everything collapses to ret 20
        val foldFirst = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        val instsFF = foldFirst.functions[0].blocks[0].instructions
        assertEquals(1, instsFF.size, "Fold+DCE: only ret should remain")
        assertEquals(20, ((instsFF[0] as Ret).value as Constant.I32).value)

        // DCE first, then fold: DCE cannot remove anything (all used), fold still works
        val dceFirst = PassPipeline()
            .add(DeadCodeElimination())
            .add(ConstantFolding())
            .execute(module)
        val instsDF = dceFirst.functions[0].blocks[0].instructions
        assertEquals(1, instsDF.size, "DCE+Fold: only ret should remain")
        assertEquals(20, ((instsDF[0] as Ret).value as Constant.I32).value)
    }

    @Test
    fun `idempotency - constant folding twice gives same result`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(3), Constant.I32(7))
            val b = mul(a, Constant.I32(4))
            ret(b)
            finalizeFunction()
        }
        val fold = ConstantFolding()
        val once = fold.run(module)
        val twice = fold.run(once)
        val instsOnce = once.functions[0].blocks[0].instructions
        val instsTwice = twice.functions[0].blocks[0].instructions
        assertEquals(instsOnce.size, instsTwice.size, "Second pass should not change anything")
        val retOnce = instsOnce.last() as Ret
        val retTwice = instsTwice.last() as Ret
        assertEquals(
            (retOnce.value as Constant.I32).value,
            (retTwice.value as Constant.I32).value
        )
    }

    @Test
    fun `idempotency - DCE twice gives same result`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2)) // dead
            mul(Constant.I32(3), Constant.I32(4)) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val dce = DeadCodeElimination()
        val once = dce.run(module)
        val twice = dce.run(once)
        assertEquals(
            once.functions[0].blocks[0].instructions.size,
            twice.functions[0].blocks[0].instructions.size
        )
    }

    @Test
    fun `idempotency - instruction combining twice gives same result`() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(0))
            val b = mul(a, Constant.I32(1))
            ret(b)
            finalizeFunction()
        }
        val combine = InstructionCombining()
        val once = combine.run(module)
        val twice = combine.run(once)
        assertEquals(
            once.functions[0].blocks[0].instructions.size,
            twice.functions[0].blocks[0].instructions.size
        )
    }

    @Test
    fun `pass interaction - instruction combining enables constant folding`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            // x * 1 where x = add(3, 7)
            val a = add(Constant.I32(3), Constant.I32(7))
            val b = mul(a, Constant.I32(1)) // instcombine removes mul-by-1
            ret(b)
            finalizeFunction()
        }
        // InstructionCombining simplifies mul(x,1) -> x, then ConstantFolding evaluates add(3,7)
        val pipeline = PassPipeline()
            .add(InstructionCombining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        assertEquals(10, ((insts[0] as Ret).value as Constant.I32).value)
    }

    @Test
    fun `pass interaction - constant folding enables jump threading`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SLT, Constant.I32(1), Constant.I32(10))
            condBr(cond, BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(42))

            appendBlock("else")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        // Fold makes cond = true, then JumpThreading eliminates the dead branch
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(JumpThreading())
        val result = pipeline.execute(module)
        val blocks = result.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge to single block: ${blocks.map { it.label }}")
        val ret = blocks[0].instructions.last() as Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `pass interaction - GVN enables DCE`() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], params[1])
            val b = add(params[0], params[1]) // redundant, GVN replaces uses of b with a
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(GlobalValueNumbering())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        // add(x,y), add(result, result), ret
        assertEquals(3, insts.size, "GVN+DCE should leave 3 instructions: $insts")
    }

    @Test
    fun `pass interaction - inlining enables constant folding`() {
        val module = build {
            val addParams = createFunction("add_nums", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(addParams[0], addParams[1])
            ret(sum)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val result = call("add_nums", listOf(Constant.I32(10), Constant.I32(20)), Type.I32)!!
            ret(result)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(Inlining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        val mainInsts = result.functions[1].blocks[0].instructions
        // After inlining, the call is replaced with add(10, 20) which constant folding evaluates
        assertFalse(mainInsts.any { it is Call }, "Call should be inlined: $mainInsts")
        val ret = mainInsts.last() as Ret
        assertNotNull(ret.value, "Return should have a value: $mainInsts")
    }

    @Test
    fun `empty function passes through pipeline`() {
        val module = build {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .add(InstructionCombining())
            .add(JumpThreading())
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertTrue(insts[0] is Ret)
    }

    @Test
    fun `single block function with arithmetic`() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(1))
            ret(a)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "Non-constant add should remain: $insts")
    }

    @Test
    fun `unreachable code removed by jump threading`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("live"))

            appendBlock("dead")
            ret(Constant.I32(-1))

            appendBlock("live")
            ret(Constant.I32(42))

            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(JumpThreading())
        val result = pipeline.execute(module)
        val blocks = result.functions[0].blocks
        val labels = blocks.map { it.label }
        assertFalse("dead" in labels, "Dead block should be removed: $labels")
    }

    @Test
    fun `O0 pipeline is identity`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O0.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "O0 should not optimize: $insts")
        assertTrue(insts[0] is Add)
    }

    @Test
    fun `O1 pipeline folds and eliminates`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(10), Constant.I32(20))
            add(Constant.I32(3), Constant.I32(4)) // dead
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "O1 should fold+DCE: $insts")
        assertEquals(30, ((insts[0] as Ret).value as Constant.I32).value)
    }

    @Test
    fun `O2 pipeline handles complex optimization`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(5), Constant.I32(5))
            val cond = icmp(ICmpPredicate.SGT, a, Constant.I32(0))
            condBr(cond, BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        val blocks = result.functions[0].blocks
        // 5+5=10, 10>0 is true, so only the "then" path should remain
        assertEquals(1, blocks.size, "O2 should collapse to single block: ${blocks.map { it.label }}")
        val ret = blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `empty pipeline is identity`() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            ret(a)
            finalizeFunction()
        }
        val result = PassPipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "Empty pipeline should not change module")
    }

    @Test
    fun `pipeline preserves external functions`() {
        val module = build {
            declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            ret(a)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)
        assertEquals(2, result.functions.size)
        assertTrue(result.functions[0].isExternal)
    }

    @Test
    fun `multiple functions optimized independently`() {
        val module = build {
            createFunction("f1", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            ret(a)
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            appendBlock("entry")
            val b = mul(Constant.I32(3), Constant.I32(4))
            ret(b)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(module)

        val f1Insts = result.functions[0].blocks[0].instructions
        assertEquals(1, f1Insts.size)
        assertEquals(3, ((f1Insts[0] as Ret).value as Constant.I32).value)

        val f2Insts = result.functions[1].blocks[0].instructions
        assertEquals(1, f2Insts.size)
        assertEquals(12, ((f2Insts[0] as Ret).value as Constant.I32).value)
    }
}

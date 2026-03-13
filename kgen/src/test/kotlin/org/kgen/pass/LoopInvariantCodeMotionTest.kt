package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class LoopInvariantCodeMotionTest {

    private val licm = LoopInvariantCodeMotion()
    private val mem2reg = Mem2Reg()

    private fun buildModule(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    private fun buildWithMem2Reg(block: IrBuilder.() -> Unit): Module {
        return mem2reg.run(buildModule(block))
    }

    @Test
    fun `hoists loop-invariant addition`() {
        // Build: for (i = 0; i < n; i += a+b) { } return i
        // a + b is loop-invariant and should be hoisted
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            val invariant = add(params[0], params[1]) // loop-invariant
            val iNext = add(i, invariant)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        // Verify loop exists (phi in loop block after mem2reg)
        val beforeLicm = module.functions[0]
        val loopBlock = beforeLicm.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock, "Loop block should exist")
        assertTrue(loopBlock!!.instructions.any { it is Add },
            "Loop should contain add before LICM")

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader, "Preheader should be created: ${fn.blocks.map { it.label }}")

        // The invariant add(a, b) should be in the preheader
        val preheaderAdds = preheader!!.instructions.filterIsInstance<Add>()
        assertTrue(preheaderAdds.isNotEmpty(), "Preheader should contain hoisted add: ${preheader.instructions}")
    }

    @Test
    fun `does not hoist loop-variant instruction`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            val iNext = add(i, Constant.I32(1)) // depends on loop-variant i
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val loopBlock = fn.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        // i + 1 depends on i (loop-variant via phi) — should stay in loop
        assertTrue(loopBlock!!.instructions.any { it is Add },
            "Loop-variant add should stay in loop: ${loopBlock.instructions}")
    }

    @Test
    fun `hoists constant multiplication`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            val stride = mul(params[0], Constant.I32(4)) // loop-invariant
            val iNext = add(i, stride)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Mul },
            "Hoisted mul should be in preheader: ${preheader.instructions}")
    }

    @Test
    fun `no change for function without loops`() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = add(params[0], Constant.I32(1))
            ret(result)
            finalizeFunction()
        }

        val result = licm.run(module)
        val fn = result.functions[0]
        assertEquals(1, fn.blocks.size)
        assertEquals("entry", fn.blocks[0].label)
    }

    @Test
    fun `skips external functions`() {
        val module = buildModule {
            declareFunction("ext", emptyList(), Type.I32)
        }
        val result = licm.run(module)
        assertTrue(result.functions[0].isExternal)
    }

    @Test
    fun `hoists chained invariant instructions`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            val sum = add(params[0], params[1])   // invariant
            val doubled = mul(sum, Constant.I32(2)) // invariant (depends on invariant)
            val iNext = add(i, doubled)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Add },
            "Hoisted add: ${preheader.instructions}")
        assertTrue(preheader.instructions.any { it is Mul },
            "Hoisted mul: ${preheader.instructions}")
    }

    @Test
    fun `does not hoist calls`() {
        val module = buildWithMem2Reg {
            declareFunction("side_effect", listOf(Param("x", Type.I32)), Type.I32)
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            call("side_effect", listOf(Constant.I32(42)), Type.I32)
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions.find { !it.isExternal }!!
        val loopBlock = fn.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        assertTrue(loopBlock!!.instructions.any { it is Call },
            "Call should stay in loop: ${loopBlock.instructions}")
    }

    @Test
    fun `hoists load from param when loop has no aliasing stores`() {
        // After mem2reg, iSlot becomes a phi — no stores remain in the loop.
        // The load from params[0] (a param) doesn't alias any loop store,
        // so alias analysis allows it to be hoisted.
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("ptr", Type.Pointer(Type.I32)), Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val i = load(Type.I32, iSlot)
            val loaded = load(Type.I32, params[0])
            val iNext = add(i, loaded)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label.contains("preheader") }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        val preheaderLoads = preheader!!.instructions.filterIsInstance<Load>()
        assertTrue(preheaderLoads.isNotEmpty(),
            "Load from param should be hoisted when loop has no aliasing stores")
    }
}

package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class InliningTest {

    private val inliner = Inlining()

    private fun buildAndInline(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return inliner.run(ir.build())
    }

    @Test
    fun `inlines simple add function`() {
        val module = buildAndInline {
            val addParams = createFunction("add_nums", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(addParams[0], addParams[1])
            ret(sum)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("add_nums", listOf(Constant.I32(10), Constant.I32(20)), Type.I32)!!
            ret(result)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        // Should be inlined: add(10, 20) + ret
        assertFalse(mainInsts.any { it is Instruction.Call }, "Call should be inlined away: $mainInsts")
        assertTrue(mainInsts.any { it is Instruction.Add }, "Inlined add should be present: $mainInsts")
    }

    @Test
    fun `inlined function uses correct arguments`() {
        val module = buildAndInline {
            val addParams = createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = add(addParams[0], addParams[0])
            ret(result)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("double", listOf(Constant.I32(5)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        val addInst = mainInsts.filterIsInstance<Instruction.Add>().firstOrNull()
        assertNotNull(addInst, "Should have inlined add: $mainInsts")
        assertEquals("5", addInst!!.lhs.name, "Both operands should be the argument 5")
        assertEquals("5", addInst.rhs.name)
    }

    @Test
    fun `does not inline external functions`() {
        val module = buildAndInline {
            declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("external_fn", listOf(Constant.I32(5)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Instruction.Call }, "External call should remain")
    }

    @Test
    fun `does not inline recursive functions`() {
        val module = buildAndInline {
            val params = createFunction("rec", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("rec", listOf(params[0]), Type.I32)!!
            ret(r)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("rec", listOf(Constant.I32(5)), Type.I32)!!
            ret(result)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Instruction.Call }, "Recursive call should not be inlined")
    }

    @Test
    fun `does not inline large functions`() {
        val smallInliner = Inlining(maxInstructionCount = 2)
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("big", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = ir.add(params[0], Constant.I32(1))
        val b = ir.add(a, Constant.I32(2))
        val c = ir.add(b, Constant.I32(3))
        ir.ret(c)
        ir.finalizeFunction()

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val r = ir.call("big", listOf(Constant.I32(0)), Type.I32)!!
        ir.ret(r)
        ir.finalizeFunction()

        val module = smallInliner.run(ir.build())
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Instruction.Call }, "Large function should not be inlined: $mainInsts")
    }

    @Test
    fun `inlines chain of calls`() {
        val module = buildAndInline {
            val p1 = createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r1 = add(p1[0], Constant.I32(1))
            ret(r1)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = call("inc", listOf(Constant.I32(0)), Type.I32)!!
            val b = call("inc", listOf(a), Type.I32)!!
            ret(b)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Instruction.Call }, "Both calls should be inlined: $mainInsts")
        val adds = mainInsts.filterIsInstance<Instruction.Add>()
        assertEquals(2, adds.size, "Should have two inlined adds: $mainInsts")
    }

    @Test
    fun `inlines void function`() {
        val module = buildAndInline {
            val p1 = createFunction("noop", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret(null)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            call("noop", emptyList(), Type.Void)
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Instruction.Call }, "Void call should be inlined: $mainInsts")
        assertEquals(1, mainInsts.size, "Only ret should remain: $mainInsts")
    }

    @Test
    fun `preserves callee function`() {
        val module = buildAndInline {
            val p1 = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(p1[0])
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("helper", listOf(Constant.I32(42)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        assertEquals(2, module.functions.size, "Callee function should be preserved")
        assertFalse(module.functions[0].isExternal)
    }

    @Test
    fun `inlined return value is used correctly`() {
        val module = buildAndInline {
            val p1 = createFunction("square", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sq = mul(p1[0], p1[0])
            ret(sq)
            finalizeFunction()

            val p2 = createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("square", listOf(p2[0]), Type.I32)!!
            val result = add(r, Constant.I32(1))
            ret(result)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Instruction.Call }, "Call should be inlined: $mainInsts")
        val mulInst = mainInsts.filterIsInstance<Instruction.Mul>().first()
        assertTrue(mulInst.lhs is Parameter, "Should use caller's parameter: ${mulInst.lhs}")
    }
}

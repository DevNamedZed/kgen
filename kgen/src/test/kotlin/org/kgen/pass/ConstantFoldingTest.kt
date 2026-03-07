package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class ConstantFoldingTest {

    private val fold = ConstantFolding()

    private fun buildAndFold(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return fold.run(ir.build())
    }

    @Test
    fun `folds constant i32 addition`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(Constant.I32(10), Constant.I32(20))
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        val retVal = ret.value as Constant.I32
        assertEquals(30, retVal.value)
    }

    @Test
    fun `folds constant i32 subtraction`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val diff = sub(Constant.I32(50), Constant.I32(30))
            ret(diff)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(20, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant i32 multiplication`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val prod = mul(Constant.I32(6), Constant.I32(7))
            ret(prod)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant i32 division`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val quot = sdiv(Constant.I32(100), Constant.I32(7))
            ret(quot)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(14, (ret.value as Constant.I32).value)
    }

    @Test
    fun `does not fold division by zero`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val quot = sdiv(Constant.I32(100), Constant.I32(0))
            ret(quot)
            finalizeFunction()
        }
        // Division by zero should NOT be folded — instruction remains
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Instruction.SDiv })
    }

    @Test
    fun `folds chained constant expressions`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(10), Constant.I32(20)) // 30
            val b = mul(a, Constant.I32(2)) // 60
            val c = sub(b, Constant.I32(18)) // 42
            ret(c)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(42, (ret.value as Constant.I32).value)

        // All intermediate instructions should be eliminated
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
    }

    @Test
    fun `preserves non-constant operations`() {
        val module = buildAndFold {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(params[0], Constant.I32(1))
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size) // add + ret
        assertTrue(insts[0] is Instruction.Add)
    }

    @Test
    fun `folds constant i64 arithmetic`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val sum = add(Constant.I64(1000000000L), Constant.I64(2000000000L))
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(3000000000L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds constant bitwise operations`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = and(Constant.I32(0xFF00), Constant.I32(0x0FF0))
            ret(a)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(0x0F00, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant shifts`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val shifted = shl(Constant.I32(1), Constant.I32(10))
            ret(shifted)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(1024, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant icmp`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cmp = icmp(ICmpPredicate.SLT, Constant.I32(5), Constant.I32(10))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant f64 arithmetic`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val sum = fadd(Constant.F64(1.5), Constant.F64(2.5))
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(4.0, (ret.value as Constant.F64).value)
    }

    @Test
    fun `folds constant negation`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val neg = neg(Constant.I32(42))
            ret(neg)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(-42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds zext of constant`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val ext = zext(Constant.I32(42), Type.I64)
            ret(ext)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(42L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `does not fold calls`() {
        val module = buildAndFold {
            declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("external_fn", listOf(Constant.I32(42)), Type.I32)
            ret(result)
            finalizeFunction()
        }
        val insts = module.functions[1].blocks[0].instructions
        assertTrue(insts.any { it is Instruction.Call })
    }

    @Test
    fun `pipeline with constant folding and DCE`() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = ir.add(Constant.I32(10), Constant.I32(20))
        val b = ir.mul(Constant.I32(3), Constant.I32(4))
        // Only 'a' is used in the return; 'b' is dead
        ir.ret(a)
        ir.finalizeFunction()

        val pipeline = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val result = pipeline.execute(ir.build())

        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Instruction.Ret
        assertEquals(30, (ret.value as Constant.I32).value)
    }
}

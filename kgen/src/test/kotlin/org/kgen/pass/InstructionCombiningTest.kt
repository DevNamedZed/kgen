package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class InstructionCombiningTest {

    private val combine = InstructionCombining()

    private fun buildAndCombine(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return combine.run(ir.build())
    }

    @Test
    fun `simplifies x plus 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(params[0], Constant.I32(0))
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x + 0 should simplify to x: ${ret.value}")
    }

    @Test
    fun `simplifies 0 plus x`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(Constant.I32(0), params[0])
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "0 + x should simplify to x")
    }

    @Test
    fun `simplifies x minus 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val diff = sub(params[0], Constant.I32(0))
            ret(diff)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x - 0 should simplify to x")
    }

    @Test
    fun `simplifies x minus x`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val diff = sub(params[0], params[0])
            ret(diff)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(0, (ret.value as Constant.I32).value, "x - x should be 0")
    }

    @Test
    fun `simplifies x times 1`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val prod = mul(params[0], Constant.I32(1))
            ret(prod)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x * 1 should simplify to x")
    }

    @Test
    fun `simplifies x times 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val prod = mul(params[0], Constant.I32(0))
            ret(prod)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(0, (ret.value as Constant.I32).value, "x * 0 should be 0")
    }

    @Test
    fun `simplifies x and 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = and(params[0], Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(0, (ret.value as Constant.I32).value, "x & 0 should be 0")
    }

    @Test
    fun `simplifies x and minus 1`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = and(params[0], Constant.I32(-1))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x & -1 should simplify to x")
    }

    @Test
    fun `simplifies x or 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = or(params[0], Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x | 0 should simplify to x")
    }

    @Test
    fun `simplifies x xor 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = xor(params[0], Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x ^ 0 should simplify to x")
    }

    @Test
    fun `simplifies x xor x`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = xor(params[0], params[0])
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(0, (ret.value as Constant.I32).value, "x ^ x should be 0")
    }

    @Test
    fun `simplifies x shl 0`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = shl(params[0], Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "x << 0 should simplify to x")
    }

    @Test
    fun `simplifies select with constant true`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = select(Constant.I1(true), params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals("a", ret.value?.name, "select(true, a, b) should be a")
    }

    @Test
    fun `simplifies select with constant false`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = select(Constant.I1(false), params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals("b", ret.value?.name, "select(false, a, b) should be b")
    }

    @Test
    fun `simplifies select with same true and false`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("cond", Type.I1), Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = select(params[0], params[1], params[1])
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals("x", ret.value?.name, "select(c, x, x) should be x")
    }

    @Test
    fun `chained simplifications`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0)) // → x
            val b = mul(a, Constant.I32(1))          // → x
            val c = sub(b, Constant.I32(0))          // → x
            ret(c)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "Chained identity ops should simplify to x: ${ret.value}")
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
    }

    @Test
    fun `i64 simplifications`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = add(params[0], Constant.I64(0))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertTrue(ret.value is Parameter, "i64: x + 0 should simplify to x")
    }

    @Test
    fun `preserves non-identity operations`() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = add(params[0], Constant.I32(5))
            ret(result)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "Non-identity add should remain")
        assertTrue(insts[0] is Instruction.Add)
    }
}

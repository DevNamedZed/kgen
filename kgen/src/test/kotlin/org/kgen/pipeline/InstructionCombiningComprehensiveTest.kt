package org.kgen.pipeline

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class InstructionCombiningComprehensiveTest {

    private val combine = InstructionCombining()

    private fun buildAndCombine(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return combine.run(ir.build())
    }

    @Nested
    inner class AddIdentities {

        @Test
        fun `x plus 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = add(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size, "add(x, 0) should be eliminated: $insts")
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `0 plus x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = add(Constant.I32(0), params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size, "add(0, x) should be eliminated: $insts")
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `x plus 0 i64 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val r = add(params[0], Constant.I64(0L))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
        }
    }

    @Nested
    inner class SubIdentities {

        @Test
        fun `x minus 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = sub(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size, "sub(x, 0) should be eliminated: $insts")
        }

        @Test
        fun `x minus x simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = sub(params[0], params[0])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }
    }

    @Nested
    inner class MulIdentities {

        @Test
        fun `x times 1 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = mul(params[0], Constant.I32(1))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `1 times x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = mul(Constant.I32(1), params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `x times 0 simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = mul(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `0 times x simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = mul(Constant.I32(0), params[0])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `x times 1 i64 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val r = mul(params[0], Constant.I64(1L))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
        }
    }

    @Nested
    inner class AndIdentities {

        @Test
        fun `x and 0 simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = and(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `0 and x simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = and(Constant.I32(0), params[0])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `x and all-ones simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = and(params[0], Constant.I32(-1))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `all-ones and x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = and(Constant.I32(-1), params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `x and x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = and(params[0], params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }
    }

    @Nested
    inner class OrIdentities {

        @Test
        fun `x or 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = or(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `0 or x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = or(Constant.I32(0), params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `x or x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = or(params[0], params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }
    }

    @Nested
    inner class XorIdentities {

        @Test
        fun `x xor 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = xor(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `0 xor x simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = xor(Constant.I32(0), params[0])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `x xor x simplifies to 0`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = xor(params[0], params[0])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }
    }

    @Nested
    inner class ShiftIdentities {

        @Test
        fun `shl x by 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = shl(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `lshr x by 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = lshr(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }

        @Test
        fun `ashr x by 0 simplifies to x`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = ashr(params[0], Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size)
            assertTrue((insts[0] as Ret).value is Parameter)
        }
    }

    @Nested
    inner class SelectSimplification {

        @Test
        fun `select with true constant picks true branch`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = select(Constant.I1(true), params[0], params[1])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals("a", ret.value!!.name)
        }

        @Test
        fun `select with false constant picks false branch`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = select(Constant.I1(false), params[0], params[1])
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals("b", ret.value!!.name)
        }

        @Test
        fun `select with same true and false simplifies to either`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(
                    Param("c", Type.I1), Param("x", Type.I32)
                ), Type.I32)
                appendBlock("entry")
                val r = select(params[0], params[1], params[1])
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size, "select(c, x, x) should simplify to x: $insts")
        }
    }

    @Nested
    inner class ChainedSimplifications {

        @Test
        fun `chained add and mul identities collapse`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = add(params[0], Constant.I32(0))
                val b = mul(a, Constant.I32(1))
                val c = sub(b, Constant.I32(0))
                ret(c)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(1, insts.size, "All identity ops should be eliminated: $insts")
            assertTrue((insts[0] as Ret).value is Parameter)
        }
    }

    @Nested
    inner class NonSimplifiableCases {

        @Test
        fun `non-identity add is preserved`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = add(params[0], Constant.I32(5))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(2, insts.size)
            assertTrue(insts[0] is Add)
        }

        @Test
        fun `non-identity mul is preserved`() {
            val module = buildAndCombine {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val r = mul(params[0], Constant.I32(3))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertEquals(2, insts.size)
            assertTrue(insts[0] is Mul)
        }

        @Test
        fun `external functions are skipped`() {
            val module = buildAndCombine {
                declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            }
            assertTrue(module.functions[0].isExternal)
        }
    }
}

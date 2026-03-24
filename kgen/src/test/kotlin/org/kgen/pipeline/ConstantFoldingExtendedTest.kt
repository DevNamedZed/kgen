package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class ConstantFoldingExtendedTest {

    private val fold = ConstantFolding()

    private fun buildAndFold(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return fold.run(ir.build())
    }

    // --- Floating point folding (f32) ---

    @Test
    fun `folds constant f32 addition`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val sum = fadd(Constant.F32(1.5f), Constant.F32(2.5f))
            ret(sum)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(4.0f, (ret.value as Constant.F32).value)
    }

    @Test
    fun `folds constant f32 subtraction`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val diff = fsub(Constant.F32(10.0f), Constant.F32(3.5f))
            ret(diff)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(6.5f, (ret.value as Constant.F32).value)
    }

    @Test
    fun `folds constant f32 multiplication`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val prod = fmul(Constant.F32(3.0f), Constant.F32(4.0f))
            ret(prod)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(12.0f, (ret.value as Constant.F32).value)
    }

    @Test
    fun `folds constant f32 division`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val quot = fdiv(Constant.F32(10.0f), Constant.F32(4.0f))
            ret(quot)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(2.5f, (ret.value as Constant.F32).value)
    }

    @Test
    fun `folds constant f32 negation`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val neg = fneg(Constant.F32(3.14f))
            ret(neg)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(-3.14f, (ret.value as Constant.F32).value)
    }

    // --- Floating point folding (f64) ---

    @Test
    fun `folds constant f64 subtraction`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val diff = fsub(Constant.F64(10.0), Constant.F64(3.5))
            ret(diff)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(6.5, (ret.value as Constant.F64).value)
    }

    @Test
    fun `folds constant f64 multiplication`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val prod = fmul(Constant.F64(2.5), Constant.F64(4.0))
            ret(prod)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(10.0, (ret.value as Constant.F64).value)
    }

    @Test
    fun `folds constant f64 division`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val quot = fdiv(Constant.F64(7.0), Constant.F64(2.0))
            ret(quot)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(3.5, (ret.value as Constant.F64).value)
    }

    @Test
    fun `folds constant f64 negation`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val neg = fneg(Constant.F64(2.718))
            ret(neg)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(-2.718, (ret.value as Constant.F64).value)
    }

    // --- Comparison folding ---

    @Test
    fun `folds i32 EQ comparison - true`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, Constant.I32(42), Constant.I32(42))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i32 EQ comparison - false`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, Constant.I32(1), Constant.I32(2))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i32 NE comparison`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.NE, Constant.I32(5), Constant.I32(10))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i32 SGT comparison`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, Constant.I32(10), Constant.I32(5))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i32 SGE comparison - equal`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGE, Constant.I32(7), Constant.I32(7))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i32 SLE comparison`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SLE, Constant.I32(3), Constant.I32(5))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i64 comparison`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SLT, Constant.I64(100L), Constant.I64(200L))
            val result = zext(cmp, Type.I64)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds unsigned comparison ULT`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            // -1 as unsigned is MAX, so ULT(-1, 1) should be false
            val cmp = icmp(ICmpPredicate.ULT, Constant.I32(-1), Constant.I32(1))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds unsigned comparison UGT`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            // -1 unsigned > 1 unsigned
            val cmp = icmp(ICmpPredicate.UGT, Constant.I32(-1), Constant.I32(1))
            val result = zext(cmp, Type.I32)
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    // --- Nested arithmetic folding ---

    @Test
    fun `folds deeply nested arithmetic`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))   // 3
            val b = mul(a, Constant.I32(3))                  // 9
            val c = sub(b, Constant.I32(4))                  // 5
            val d = add(c, Constant.I32(15))                 // 20
            val e = sdiv(d, Constant.I32(4))                 // 5
            ret(e)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(5, (ret.value as Constant.I32).value)
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "All intermediates should be eliminated: $insts")
    }

    @Test
    fun `folds nested mixed types i64`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val a = add(Constant.I64(100L), Constant.I64(200L))  // 300
            val b = mul(a, Constant.I64(2L))                     // 600
            val c = sub(b, Constant.I64(100L))                   // 500
            ret(c)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(500L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds nested f64 arithmetic`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val a = fadd(Constant.F64(1.0), Constant.F64(2.0))   // 3.0
            val b = fmul(a, Constant.F64(2.0))                    // 6.0
            val c = fsub(b, Constant.F64(1.0))                    // 5.0
            ret(c)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(5.0, (ret.value as Constant.F64).value)
    }

    @Test
    fun `partial folding with variable operand`() {
        val module = buildAndFold {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(3), Constant.I32(7))  // folds to 10
            val b = add(a, params[0])                       // cannot fold: 10 + x
            ret(b)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "One fold, one remains: $insts")
        val addInst = insts[0] as Add
        assertTrue(addInst.lhs is Constant.I32, "Folded constant should be propagated")
        assertEquals(10, (addInst.lhs as Constant.I32).value)
    }

    // --- Bitwise operation folding ---

    @Test
    fun `folds constant OR`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val result = or(Constant.I32(0xF0), Constant.I32(0x0F))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0xFF, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant XOR`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val result = xor(Constant.I32(0xFF00), Constant.I32(0x00FF))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0xFFFF, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant left shift`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val result = shl(Constant.I32(3), Constant.I32(4))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(48, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant logical right shift`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val result = lshr(Constant.I32(256), Constant.I32(4))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(16, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant arithmetic right shift`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val result = ashr(Constant.I32(-128), Constant.I32(2))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(-32, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds i64 bitwise AND`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val result = and(Constant.I64(0xFFFF0000L), Constant.I64(0x0000FFFFL))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds i64 bitwise OR`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val result = or(Constant.I64(0xFF00L), Constant.I64(0x00FFL))
            ret(result)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0xFFFFL, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds chained bitwise operations`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = or(Constant.I32(0x0F), Constant.I32(0xF0))     // 0xFF
            val b = and(a, Constant.I32(0x3C))                      // 0x3C
            val c = xor(b, Constant.I32(0xFF))                      // 0xC3
            ret(c)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0xC3, (ret.value as Constant.I32).value)
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "All intermediates should be eliminated: $insts")
    }

    // --- Extension/truncation folding ---

    @Test
    fun `folds sext of constant i32 to i64`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ext = sext(Constant.I32(-42), Type.I64)
            ret(ext)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(-42L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `folds trunc of constant i64 to i32`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val trunc = trunc(Constant.I64(0x1_0000_002AL), Type.I32)
            ret(trunc)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds zext of i1 true to i32`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ext = zext(Constant.I1(true), Type.I32)
            ret(ext)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(1, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds zext of i1 false to i32`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ext = zext(Constant.I1(false), Type.I32)
            ret(ext)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    // --- Remainder folding ---

    @Test
    fun `folds constant signed remainder`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val rem = srem(Constant.I32(17), Constant.I32(5))
            ret(rem)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(2, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant unsigned remainder`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val rem = urem(Constant.I32(17), Constant.I32(5))
            ret(rem)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(2, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds constant unsigned division`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val quot = udiv(Constant.I32(100), Constant.I32(7))
            ret(quot)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(14, (ret.value as Constant.I32).value)
    }

    // --- Edge cases ---

    @Test
    fun `preserves non-constant floating point operations`() {
        val module = buildAndFold {
            val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
            appendBlock("entry")
            val sum = fadd(params[0], Constant.F64(1.0))
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size)
        assertTrue(insts[0] is FAdd)
    }

    @Test
    fun `folds i32 negation of zero`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val n = neg(Constant.I32(0))
            ret(n)
            finalizeFunction()
        }
        val ret = module.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `folds comparison feeding condBr`() {
        val module = buildAndFold {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, Constant.I32(5), Constant.I32(5))
            condBr(cmp, BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        // After folding, the condBr should have a constant true condition
        val entry = module.functions[0].blocks[0]
        val last = entry.instructions.last() as CondBr
        assertTrue(last.condition is Constant.I1, "Condition should be folded to constant: ${last.condition}")
        assertEquals(true, (last.condition as Constant.I1).value)
    }
}

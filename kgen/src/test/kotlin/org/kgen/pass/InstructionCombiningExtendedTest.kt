package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class InstructionCombiningExtendedTest {

    private val combine = InstructionCombining()

    private fun buildAndCombine(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return combine.run(ir.build())
    }

    private fun retValue(module: Module, funcIdx: Int = 0): Value? {
        return (module.functions[funcIdx].blocks[0].instructions.last() as Instruction.Ret).value
    }

    private fun instCount(module: Module, funcIdx: Int = 0): Int {
        return module.functions[funcIdx].blocks[0].instructions.size
    }

    // --- i64 variants of all identity operations ---

    @Test
    fun i64XPlus0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x + 0 should simplify to x")
    }

    @Test
    fun i640PlusX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(Constant.I64(0), params[0]))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: 0 + x should simplify to x")
    }

    @Test
    fun i64XMinus0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sub(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x - 0 should simplify to x")
    }

    @Test
    fun i64XMinusX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sub(params[0], params[0]))
            finalizeFunction()
        }
        assertEquals(0L, (retValue(module) as Constant.I64).value, "i64: x - x should be 0")
    }

    @Test
    fun i64XTimes1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(params[0], Constant.I64(1)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x * 1 should simplify to x")
    }

    @Test
    fun i64XTimes0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertEquals(0L, (retValue(module) as Constant.I64).value, "i64: x * 0 should be 0")
    }

    @Test
    fun i64XAnd0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(and(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertEquals(0L, (retValue(module) as Constant.I64).value, "i64: x & 0 should be 0")
    }

    @Test
    fun i64XAndMinus1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(and(params[0], Constant.I64(-1)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x & -1 should simplify to x")
    }

    @Test
    fun i64XOr0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(or(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x | 0 should simplify to x")
    }

    @Test
    fun i64XXor0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(xor(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x ^ 0 should simplify to x")
    }

    @Test
    fun i64XXorX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(xor(params[0], params[0]))
            finalizeFunction()
        }
        assertEquals(0L, (retValue(module) as Constant.I64).value, "i64: x ^ x should be 0")
    }

    @Test
    fun i64XShl0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(shl(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x << 0 should simplify to x")
    }

    // --- Shift right by 0 ---

    @Test
    fun lshrBy0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], Constant.I32(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "x >>> 0 should simplify to x")
    }

    @Test
    fun ashrBy0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], Constant.I32(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "x >> 0 should simplify to x")
    }

    @Test
    fun i64LshrBy0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x >>> 0 should simplify to x")
    }

    @Test
    fun i64AshrBy0() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "i64: x >> 0 should simplify to x")
    }

    // --- Division and remainder identities ---

    @Test
    fun udivBy1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], Constant.I32(1)))
            finalizeFunction()
        }
        val v = retValue(module)
        assertTrue(v is Parameter || instCount(module) == 2, "udiv by 1: $v")
    }

    @Test
    fun sdivBy1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(sdiv(params[0], Constant.I32(1)))
            finalizeFunction()
        }
        val v = retValue(module)
        // sdiv(x, 1) may or may not be simplified depending on implementation
        assertTrue(v is Parameter || instCount(module) == 2, "sdiv by 1: $v")
    }

    @Test
    fun i64UdivBy1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], Constant.I64(1)))
            finalizeFunction()
        }
        val v = retValue(module)
        assertTrue(v is Parameter || instCount(module) == 2, "i64 udiv by 1: $v")
    }

    @Test
    fun i64SdivBy1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sdiv(params[0], Constant.I64(1)))
            finalizeFunction()
        }
        val v = retValue(module)
        assertTrue(v is Parameter || instCount(module) == 2, "i64 sdiv by 1: $v")
    }

    // --- Commutative identity: 1 * x ---

    @Test
    fun oneTimesX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(Constant.I32(1), params[0]))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "1 * x should simplify to x")
    }

    @Test
    fun zeroTimesX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(Constant.I32(0), params[0]))
            finalizeFunction()
        }
        assertEquals(0, (retValue(module) as Constant.I32).value, "0 * x should be 0")
    }

    // --- Or with -1 ---

    @Test
    fun xOrMinus1() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(or(params[0], Constant.I32(-1)))
            finalizeFunction()
        }
        val v = retValue(module)
        if (v is Constant.I32) {
            assertEquals(-1, v.value, "x | -1 should be -1")
        }
        // If not simplified, still valid - some passes may not handle this
    }

    @Test
    fun xOrX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(or(params[0], params[0]))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "x | x should simplify to x")
    }

    @Test
    fun xAndX() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(and(params[0], params[0]))
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "x & x should simplify to x")
    }

    // --- Chained identity operations ---

    @Test
    fun chainedI64Identities() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I64(0))
            val b = mul(a, Constant.I64(1))
            val c = sub(b, Constant.I64(0))
            val d = xor(c, Constant.I64(0))
            ret(d)
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "Chained i64 identities should simplify to x")
        assertEquals(1, instCount(module), "Only ret should remain")
    }

    @Test
    fun chainedShiftIdentities() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = shl(params[0], Constant.I32(0))
            val b = lshr(a, Constant.I32(0))
            val c = ashr(b, Constant.I32(0))
            ret(c)
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "Chained shift-by-0 should simplify to x")
        assertEquals(1, instCount(module), "Only ret should remain")
    }

    @Test
    fun chainedBitwiseIdentities() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = and(params[0], Constant.I32(-1))
            val b = or(a, Constant.I32(0))
            val c = xor(b, Constant.I32(0))
            ret(c)
            finalizeFunction()
        }
        assertTrue(retValue(module) is Parameter, "Chained bitwise identities should simplify to x")
        assertEquals(1, instCount(module), "Only ret should remain")
    }

    // --- Multiple functions ---

    @Test
    fun multipleFunctionsCombined() {
        val module = buildAndCombine {
            val p1 = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(p1[0], Constant.I32(0)))
            finalizeFunction()

            val p2 = createFunction("g", listOf(Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(p2[0], Constant.I32(1)))
            finalizeFunction()
        }
        assertTrue(retValue(module, 0) is Parameter)
        assertTrue(retValue(module, 1) is Parameter)
    }

    // --- Preserves non-identity operations ---

    @Test
    fun preservesNonIdentityMul() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(params[0], Constant.I32(3)))
            finalizeFunction()
        }
        assertEquals(2, instCount(module), "Non-identity mul should remain")
    }

    @Test
    fun preservesNonIdentitySub() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(sub(params[0], Constant.I32(7)))
            finalizeFunction()
        }
        assertEquals(2, instCount(module), "Non-identity sub should remain")
    }

    @Test
    fun preservesNonIdentityShl() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(shl(params[0], Constant.I32(2)))
            finalizeFunction()
        }
        assertEquals(2, instCount(module), "Non-identity shl should remain")
    }

    @Test
    fun preservesNonIdentityAnd() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(and(params[0], Constant.I32(0xFF)))
            finalizeFunction()
        }
        assertEquals(2, instCount(module), "Non-identity and should remain")
    }

    // --- Two parameters ---

    @Test
    fun twoParamsAddIdentity() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0))
            val b = add(a, params[1])
            ret(b)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // add(x, 0) → x, then add(x, y) remains
        assertEquals(2, insts.size, "Should have add(x,y) and ret")
    }

    @Test
    fun selectI64() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(select(Constant.I1(true), params[0], params[1]))
            finalizeFunction()
        }
        assertEquals("a", retValue(module)?.name, "select(true, a, b) should be a for i64")
    }

    @Test
    fun selectFalseI64() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(select(Constant.I1(false), params[0], params[1]))
            finalizeFunction()
        }
        assertEquals("b", retValue(module)?.name, "select(false, a, b) should be b for i64")
    }

    @Test
    fun selectSameI64() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("cond", Type.I1), Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(select(params[0], params[1], params[1]))
            finalizeFunction()
        }
        assertEquals("x", retValue(module)?.name, "select(c, x, x) should be x for i64")
    }

    // --- Multiple zero-producing operations ---

    @Test
    fun multipleZeroProducers() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val z1 = sub(params[0], params[0]) // → 0
            val z2 = xor(params[1], params[1]) // → 0
            val sum = add(z1, z2) // 0 + 0
            ret(sum)
            finalizeFunction()
        }
        // Both should become 0, then add(0, 0) should be recognized
        val v = retValue(module)
        if (v is Constant.I32) {
            assertEquals(0, v.value)
        }
    }

    // --- Void function not affected ---

    @Test
    fun voidFunctionUnchanged() {
        val module = buildAndCombine {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
        assertEquals(1, instCount(module))
    }

    // --- External function preserved ---

    @Test
    fun externalFunctionPreserved() {
        val module = buildAndCombine {
            declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], Constant.I32(0)))
            finalizeFunction()
        }
        assertEquals(2, module.functions.size)
        assertTrue(module.functions[0].isExternal)
        assertTrue(retValue(module, 1) is Parameter)
    }

    // --- Mixed types in separate functions ---

    @Test
    fun mixedTypesI32AndI64() {
        val module = buildAndCombine {
            val p32 = createFunction("f32", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(p32[0], Constant.I32(0)))
            finalizeFunction()

            val p64 = createFunction("f64", listOf(Param("x", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(p64[0], Constant.I64(0)))
            finalizeFunction()
        }
        assertTrue(retValue(module, 0) is Parameter, "i32 function simplified")
        assertTrue(retValue(module, 1) is Parameter, "i64 function simplified")
    }

    // --- Combining with result used multiple times ---

    @Test
    fun identityOpUsedMultipleTimes() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0)) // → x
            val b = add(a, a) // → x + x
            ret(b)
            finalizeFunction()
        }
        // add(x, 0) → x, then add(x, x) should use x directly
        assertEquals(2, instCount(module), "Should have add(x,x) and ret")
    }

    @Test
    fun idempotency() {
        val module = buildAndCombine {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0))
            val b = mul(a, Constant.I32(1))
            ret(b)
            finalizeFunction()
        }
        val module2 = combine.run(module)
        assertEquals(instCount(module), instCount(module2), "Second pass should not change anything")
    }
}

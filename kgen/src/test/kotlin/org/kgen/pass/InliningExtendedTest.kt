package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class InliningExtendedTest {

    private val inliner = Inlining()

    private fun buildAndInline(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return inliner.run(ir.build())
    }

    @Test
    fun `inlines identity function`() {
        val module = buildAndInline {
            val p = createFunction("identity", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(p[0])
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("identity", listOf(Constant.I32(42)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        // After inlining identity(42), the call is removed and the return value
        // references the argument constant directly
        val ret = mainInsts.last() as Ret
        assertNotNull(ret.value, "Return value should not be null")
    }

    @Test
    fun `inlines function with sub`() {
        val module = buildAndInline {
            val p = createFunction("negate", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sub(Constant.I32(0), p[0])
            ret(r)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val result = call("negate", listOf(Constant.I32(5)), Type.I32)!!
            ret(result)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Sub })
    }

    @Test
    fun `inlines function with multiple params`() {
        val module = buildAndInline {
            val p = createFunction("sum3", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            appendBlock("entry")
            val ab = add(p[0], p[1])
            val abc = add(ab, p[2])
            ret(abc)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("sum3", listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        val adds = mainInsts.filterIsInstance<Add>()
        assertEquals(2, adds.size)
    }

    @Test
    fun `inlines function with bitwise operations`() {
        val module = buildAndInline {
            val p = createFunction("mask", listOf(Param("x", Type.I32), Param("m", Type.I32)), Type.I32)
            appendBlock("entry")
            val masked = and(p[0], p[1])
            ret(masked)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("mask", listOf(Constant.I32(0xFF), Constant.I32(0x0F)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is And })
    }

    @Test
    fun `inlines function with icmp and select`() {
        val module = buildAndInline {
            val p = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, p[0], Constant.I32(0))
            val negated = neg(p[0])
            val selected = select(cmp, p[0], negated)
            ret(selected)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("abs", listOf(Constant.I32(-5)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is ICmp })
        assertTrue(mainInsts.any { it is Select })
    }

    @Test
    fun `inlines function with multiple blocks`() {
        val module = buildAndInline {
            val p = createFunction("multiblock", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(p[0], BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(Constant.I32(1))
            appendBlock("else")
            ret(Constant.I32(2))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("multiblock", listOf(Constant.I1(true)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        // Multi-block functions should now be inlined — no Call instructions remain
        val allInsts = module.functions[1].blocks.flatMap { it.instructions }
        assertFalse(allInsts.any { it is Call }, "Multi-block function should be inlined")
        // The inlined function should contain a CondBr (from the multiblock's entry)
        assertTrue(allInsts.any { it is CondBr }, "Inlined function should have CondBr")
    }

    @Test
    fun `inlines with i64 return type`() {
        val module = buildAndInline {
            val p = createFunction("to64", listOf(Param("x", Type.I32)), Type.I64)
            appendBlock("entry")
            val extended = zext(p[0], Type.I64)
            ret(extended)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I64)
            appendBlock("entry")
            val r = call("to64", listOf(Constant.I32(42)), Type.I64)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is ZExt })
    }

    @Test
    fun `inlines with f64 operations`() {
        val module = buildAndInline {
            val p = createFunction("fadd1", listOf(Param("x", Type.F64)), Type.F64)
            appendBlock("entry")
            val added = fadd(p[0], Constant.F64(1.0))
            ret(added)
            finalizeFunction()

            createFunction("main", emptyList(), Type.F64)
            appendBlock("entry")
            val r = call("fadd1", listOf(Constant.F64(3.14)), Type.F64)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is FAdd })
    }

    @Test
    fun `inlines multiple calls to same function`() {
        val module = buildAndInline {
            val p = createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = add(p[0], Constant.I32(1))
            ret(r)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val a = call("inc", listOf(Constant.I32(0)), Type.I32)!!
            val b = call("inc", listOf(a), Type.I32)!!
            val c = call("inc", listOf(b), Type.I32)!!
            ret(c)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        val adds = mainInsts.filterIsInstance<Add>()
        assertEquals(3, adds.size)
    }

    @Test
    fun `inlines function using result in arithmetic`() {
        val module = buildAndInline {
            val p = createFunction("square", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(p[0], p[0])
            ret(r)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val sq = call("square", listOf(params[0]), Type.I32)!!
            val result = add(sq, Constant.I32(1))
            ret(result)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Mul })
        assertTrue(mainInsts.any { it is Add })
    }

    @Test
    fun `inlines two different functions`() {
        val module = buildAndInline {
            val p1 = createFunction("addOne", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p1[0], Constant.I32(1)))
            finalizeFunction()

            val p2 = createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p2[0], Constant.I32(2)))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val a = call("addOne", listOf(Constant.I32(5)), Type.I32)!!
            val b = call("double", listOf(a), Type.I32)!!
            ret(b)
            finalizeFunction()
        }
        val mainInsts = module.functions[2].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Add })
        assertTrue(mainInsts.any { it is Mul })
    }

    @Test
    fun `respects max instruction count`() {
        val small = Inlining(maxInstructionCount = 1)
        val ir = IrBuilder("test", Target.x86_64())
        val p = ir.createFunction("twoInst", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val r = ir.add(p[0], Constant.I32(1))
        ir.ret(r)
        ir.finalizeFunction()

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val v = ir.call("twoInst", listOf(Constant.I32(0)), Type.I32)!!
        ir.ret(v)
        ir.finalizeFunction()

        val module = small.run(ir.build())
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Call }, "Should not inline with max=1")
    }

    @Test
    fun `inlines void function that has stores`() {
        val module = buildAndInline {
            val p = createFunction("storeVal", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            store(Constant.I32(42), p[0])
            ret(null)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            call("storeVal", listOf(params[0]), Type.Void)
            val v = load(Type.I32, params[0])
            ret(v)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Store })
    }

    @Test
    fun `inlines function with multiple return value uses`() {
        val module = buildAndInline {
            val p = createFunction("triple", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(p[0], Constant.I32(3))
            ret(r)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = call("triple", listOf(params[0]), Type.I32)!!
            val b = add(a, Constant.I32(10))
            val c = mul(a, Constant.I32(2))
            val d = add(b, c)
            ret(d)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Mul })
    }

    @Test
    fun `preserves both functions after inlining`() {
        val module = buildAndInline {
            val p = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(1)))
            finalizeFunction()

            createFunction("caller1", emptyList(), Type.I32)
            appendBlock("entry")
            val r1 = call("helper", listOf(Constant.I32(1)), Type.I32)!!
            ret(r1)
            finalizeFunction()

            createFunction("caller2", emptyList(), Type.I32)
            appendBlock("entry")
            val r2 = call("helper", listOf(Constant.I32(2)), Type.I32)!!
            ret(r2)
            finalizeFunction()
        }
        assertEquals(3, module.functions.size, "All functions should be preserved")
        // Both callers should be inlined
        assertFalse(module.functions[1].blocks[0].instructions.any { it is Call })
        assertFalse(module.functions[2].blocks[0].instructions.any { it is Call })
    }

    @Test
    fun `inlines function with alloca and load store`() {
        val module = buildAndInline {
            val p = createFunction("loadStore", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(p[0], ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("loadStore", listOf(Constant.I32(42)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Alloca })
    }

    @Test
    fun `inlines function with shift operations`() {
        val module = buildAndInline {
            val p = createFunction("shlBy2", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val shifted = shl(p[0], Constant.I32(2))
            ret(shifted)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("shlBy2", listOf(Constant.I32(3)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Shl })
    }

    @Test
    fun `inlines function with conversion`() {
        val module = buildAndInline {
            val p = createFunction("toFloat", listOf(Param("x", Type.I32)), Type.F64)
            appendBlock("entry")
            val converted = sitofp(p[0], Type.F64)
            ret(converted)
            finalizeFunction()

            createFunction("main", emptyList(), Type.F64)
            appendBlock("entry")
            val r = call("toFloat", listOf(Constant.I32(42)), Type.F64)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is SIToFP })
    }

    @Test
    fun `handles external and internal calls in same function`() {
        val module = buildAndInline {
            declareFunction("extern", listOf(Param("x", Type.I32)), Type.I32)

            val p = createFunction("intern", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p[0], Constant.I32(1)))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val a = call("extern", listOf(Constant.I32(1)), Type.I32)!!
            val b = call("intern", listOf(a), Type.I32)!!
            ret(b)
            finalizeFunction()
        }
        val mainInsts = module.functions[2].blocks[0].instructions
        // extern call should remain, intern call should be inlined
        val calls = mainInsts.filterIsInstance<Call>()
        assertEquals(1, calls.size, "Only extern call should remain: $mainInsts")
    }

    @Test
    fun `inlines function with no params`() {
        val module = buildAndInline {
            createFunction("getConst", emptyList(), Type.I32)
            appendBlock("entry")
            val sum = add(Constant.I32(40), Constant.I32(2))
            ret(sum)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r2 = call("getConst", emptyList(), Type.I32)!!
            ret(r2)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Add })
    }

    @Test
    fun `inlines function result used in store`() {
        val module = buildAndInline {
            val p = createFunction("compute", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p[0], Constant.I32(2)))
            finalizeFunction()

            val params = createFunction("main", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            val r = call("compute", listOf(Constant.I32(21)), Type.I32)!!
            store(r, params[0])
            ret(null)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Mul })
        assertTrue(mainInsts.any { it is Store })
    }

    @Test
    fun `handles function with gep`() {
        val module = buildAndInline {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val p = createFunction("getField", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val fieldPtr = gep(structType, p[0], Constant.I32(0), Constant.I32(1))
            val v = load(Type.I32, fieldPtr)
            ret(v)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val r = call("getField", listOf(params[0]), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is GetElementPtr })
    }

    @Test
    fun `does not inline external function`() {
        val module = buildAndInline {
            declareFunction("external", listOf(Param("x", Type.I32)), Type.I32)

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("external", listOf(Constant.I32(1)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Call }, "External call should remain")
    }

    @Test
    fun `does not inline recursive function`() {
        val module = buildAndInline {
            val p = createFunction("rec", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = call("rec", listOf(p[0]), Type.I32)!!
            ret(r)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val v = call("rec", listOf(Constant.I32(5)), Type.I32)!!
            ret(v)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertTrue(mainInsts.any { it is Call },
            "Recursive function should not be inlined")
    }

    @Test
    fun `inlines function with or and xor`() {
        val module = buildAndInline {
            val p = createFunction("bitOps", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val ored = or(p[0], p[1])
            val xored = xor(ored, Constant.I32(0xFF))
            ret(xored)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("bitOps", listOf(Constant.I32(0xAA), Constant.I32(0x55)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call })
        assertTrue(mainInsts.any { it is Or })
        assertTrue(mainInsts.any { it is Xor })
    }

    @Test
    fun `idempotent on already inlined code`() {
        val module = buildAndInline {
            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val v = add(Constant.I32(1), Constant.I32(2))
            ret(v)
            finalizeFunction()
        }
        val result = inliner.run(module)
        assertEquals(module.functions[0].blocks[0].instructions.size,
            result.functions[0].blocks[0].instructions.size)
    }
}

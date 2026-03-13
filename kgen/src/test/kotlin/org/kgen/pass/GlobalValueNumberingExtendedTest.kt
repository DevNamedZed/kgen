package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class GlobalValueNumberingExtendedTest {

    private val gvn = GlobalValueNumbering()

    private fun buildAndGvn(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return gvn.run(ir.build())
    }

    @Test
    fun `eliminates redundant sub`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = sub(params[0], params[1])
            val b = sub(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
        val addInst = insts[1] as Add
        assertEquals(addInst.lhs.name, addInst.rhs.name)
    }

    @Test
    fun `eliminates redundant and`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = and(params[0], params[1])
            val b = and(params[0], params[1])
            val sum = or(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant or`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = or(params[0], params[1])
            val b = or(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant xor`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = xor(params[0], params[1])
            val b = xor(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant shl`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = shl(params[0], params[1])
            val b = shl(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant lshr`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = lshr(params[0], params[1])
            val b = lshr(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant ashr`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = ashr(params[0], params[1])
            val b = ashr(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `eliminates redundant sext`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = sext(params[0], Type.I64)
            val b = sext(params[0], Type.I64)
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val sextCount = insts.count { it is SExt }
        assertEquals(1, sextCount)
    }

    @Test
    fun `eliminates redundant neg`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = neg(params[0])
            val b = neg(params[0])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val negCount = insts.count { it is Neg }
        assertEquals(1, negCount)
    }

    @Test
    fun `eliminates redundant fadd`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val a = fadd(params[0], params[1])
            val b = fadd(params[0], params[1])
            val sum = fadd(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val faddCount = insts.count { it is FAdd }
        assertEquals(2, faddCount, "Should eliminate one redundant fadd: $insts")
    }

    @Test
    fun `eliminates redundant fmul`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val a = fmul(params[0], params[1])
            val b = fmul(params[0], params[1])
            val sum = fadd(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val fmulCount = insts.count { it is FMul }
        assertEquals(1, fmulCount)
    }

    @Test
    fun `eliminates redundant select`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("c", Type.I1), Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = select(params[0], params[1], params[2])
            val b = select(params[0], params[1], params[2])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val selectCount = insts.count { it is Select }
        assertEquals(1, selectCount)
    }

    @Test
    fun `does not eliminate different predicates`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = icmp(ICmpPredicate.EQ, params[0], params[1])
            val b = icmp(ICmpPredicate.NE, params[0], params[1])
            val r = select(a, Constant.I32(1), Constant.I32(0))
            ret(r)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val icmpCount = insts.count { it is ICmp }
        assertEquals(2, icmpCount)
    }

    @Test
    fun `eliminates three redundant adds`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = add(params[0], params[1])
            val c = add(params[0], params[1])
            val d = add(a, b)
            val e = add(d, c)
            ret(e)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // Only one add(x,y) should survive + add(a,a) + add(d,a) + ret = 4
        assertEquals(4, insts.size, "All redundant adds eliminated: $insts")
    }

    @Test
    fun `eliminates redundant gep`() {
        val module = buildAndGvn {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = gep(structType, params[0], Constant.I32(0), Constant.I32(0))
            val b = gep(structType, params[0], Constant.I32(0), Constant.I32(0))
            val v1 = load(Type.I32, a)
            val v2 = load(Type.I32, b)
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val gepCount = insts.count { it is GetElementPtr }
        assertEquals(1, gepCount, "Redundant GEP should be eliminated")
    }

    @Test
    fun `does not eliminate gep with different indices`() {
        val module = buildAndGvn {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = gep(structType, params[0], Constant.I32(0), Constant.I32(0))
            val b = gep(structType, params[0], Constant.I32(0), Constant.I32(1))
            val v1 = load(Type.I32, a)
            val v2 = load(Type.I32, b)
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val gepCount = insts.count { it is GetElementPtr }
        assertEquals(2, gepCount)
    }

    @Test
    fun `eliminates in dominating block but not sibling`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("y", Type.I32),
                Param("c1", Type.I1), Param("c2", Type.I1)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            condBr(params[2], "left", "right")

            positionAtEnd(appendBlock("left"))
            val b = add(params[0], params[1]) // dominated by entry, should be eliminated
            condBr(params[3], "ll", "lr")

            positionAtEnd(appendBlock("ll"))
            val c = add(params[0], params[1]) // dominated by entry, should be eliminated
            ret(c)

            positionAtEnd(appendBlock("lr"))
            ret(b)

            positionAtEnd(appendBlock("right"))
            val d = add(params[0], params[1]) // dominated by entry, should be eliminated
            ret(d)

            finalizeFunction()
        }
        // All child blocks should have their adds eliminated
        val fn = module.functions[0]
        for (block in fn.blocks) {
            if (block.label == "entry") continue
            val addCount = block.instructions.count { it is Add }
            assertEquals(0, addCount, "Block ${block.label} should not have adds: ${block.instructions}")
        }
    }

    @Test
    fun `handles constant expressions`() {
        val module = buildAndGvn {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(1), Constant.I32(2))
            val b = add(Constant.I32(1), Constant.I32(2))
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `does not eliminate stores`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            positionAtEnd(appendBlock("entry"))
            store(Constant.I32(42), params[0])
            store(Constant.I32(42), params[0])
            ret(null)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val storeCount = insts.count { it is Store }
        assertEquals(2, storeCount, "Stores should not be eliminated")
    }

    @Test
    fun `eliminates redundant trunc`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = trunc(params[0], Type.I32)
            val b = trunc(params[0], Type.I32)
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val truncCount = insts.count { it is IntTrunc }
        assertEquals(1, truncCount)
    }

    @Test
    fun `eliminates redundant sitofp`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val a = sitofp(params[0], Type.F64)
            val b = sitofp(params[0], Type.F64)
            val sum = fadd(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val convCount = insts.count { it is SIToFP }
        assertEquals(1, convCount)
    }

    @Test
    fun `handles empty function`() {
        val module = buildAndGvn {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret(null)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `handles multiple functions independently`() {
        val module = buildAndGvn {
            val p1 = createFunction("f1", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a1 = add(p1[0], p1[1])
            val b1 = add(p1[0], p1[1])
            val s1 = add(a1, b1)
            ret(s1)
            finalizeFunction()

            val p2 = createFunction("f2", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a2 = mul(p2[0], p2[1])
            val b2 = mul(p2[0], p2[1])
            val s2 = add(a2, b2)
            ret(s2)
            finalizeFunction()
        }
        assertEquals(3, module.functions[0].blocks[0].instructions.size)
        assertEquals(3, module.functions[1].blocks[0].instructions.size)
    }

    @Test
    fun `does not confuse different types`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = zext(params[0], Type.I64)
            val b = sext(params[0], Type.I64)
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // zext and sext are different operations, both should remain
        assertEquals(4, insts.size)
    }

    @Test
    fun `eliminates redundant sdiv`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = sdiv(params[0], params[1])
            val b = sdiv(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val divCount = insts.count { it is SDiv }
        assertEquals(1, divCount)
    }

    @Test
    fun `eliminates redundant fcmp`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            val b = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            val r = select(a, Constant.I32(1), Constant.I32(0))
            ret(r)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val fcmpCount = insts.count { it is FCmp }
        assertEquals(1, fcmpCount)
    }

    @Test
    fun `does not eliminate fcmp with different predicates`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            val b = fcmp(FCmpPredicate.OLT, params[0], params[1])
            val r = select(a, Constant.I32(1), Constant.I32(0))
            ret(r)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val fcmpCount = insts.count { it is FCmp }
        assertEquals(2, fcmpCount)
    }
}

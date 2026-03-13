package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class DeadCodeEliminationExtendedTest {

    private val dce = DeadCodeElimination()

    private fun buildAndDCE(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return dce.run(ir.build())
    }

    @Test
    fun `removes unused subtraction`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            sub(Constant.I32(10), Constant.I32(3))
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertTrue(insts[0] is Ret)
    }

    @Test
    fun `removes unused division`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            sdiv(Constant.I32(10), Constant.I32(2))
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused bitwise operations`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            and(params[0], Constant.I32(0xFF))
            or(params[0], Constant.I32(0x10))
            xor(params[0], Constant.I32(0x01))
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertTrue(insts[0] is Ret)
    }

    @Test
    fun `removes unused shift operations`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            shl(params[0], Constant.I32(2))
            lshr(params[0], Constant.I32(1))
            ashr(params[0], Constant.I32(3))
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused comparison`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused zext`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            zext(params[0], Type.I64) // dead
            ret(Constant.I64(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused sext`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            sext(params[0], Type.I64) // dead
            ret(Constant.I64(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused trunc`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            trunc(params[0], Type.I32) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused select`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            select(params[0], Constant.I32(1), Constant.I32(2)) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `preserves used select`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val v = select(params[0], Constant.I32(1), Constant.I32(2))
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size)
    }

    @Test
    fun `removes unused neg`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            neg(params[0]) // dead
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `removes unused floating point operations`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            fadd(params[0], Constant.F64(1.0))
            fsub(params[0], Constant.F64(2.0))
            fmul(params[0], Constant.F64(3.0))
            fdiv(params[0], Constant.F64(4.0))
            fneg(params[0])
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `preserves used floating point chain`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val a = fadd(params[0], Constant.F64(1.0))
            val b = fmul(a, Constant.F64(2.0))
            ret(b)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size)
    }

    @Test
    fun `removes unused load`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            load(Type.I32, params[0]) // dead - result unused
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `preserves store even when load of same pointer is dead`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            store(Constant.I32(42), params[0])
            load(Type.I32, params[0]) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size)
        assertTrue(insts[0] is Store)
    }

    @Test
    fun `removes dead code across multiple blocks`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(params[0], "then", "else")

            positionAtEnd(appendBlock("then"))
            add(Constant.I32(1), Constant.I32(2)) // dead
            ret(Constant.I32(10))

            positionAtEnd(appendBlock("else"))
            mul(Constant.I32(3), Constant.I32(4)) // dead
            ret(Constant.I32(20))

            finalizeFunction()
        }
        val thenInsts = module.functions[0].blocks[1].instructions
        val elseInsts = module.functions[0].blocks[2].instructions
        assertEquals(1, thenInsts.size)
        assertEquals(1, elseInsts.size)
    }

    @Test
    fun `preserves alloca instruction`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Alloca })
    }

    @Test
    fun `removes deep chain of dead values`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(1))
            val b = mul(a, Constant.I32(2))
            val c = sub(b, Constant.I32(3))
            val d = add(c, Constant.I32(4))
            val e = mul(d, Constant.I32(5))
            ret(Constant.I32(99))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Entire dead chain should be removed: $insts")
    }

    @Test
    fun `preserves partially used chain`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(1))
            val b = mul(a, Constant.I32(2)) // dead
            val c = sub(a, Constant.I32(3)) // used in ret
            ret(c)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "add, sub, ret should remain: $insts")
    }

    @Test
    fun `preserves void call`() {
        val module = buildAndDCE {
            declareFunction("sideEffect", emptyList(), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            call("sideEffect", emptyList(), Type.Void)
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[1].blocks[0].instructions
        assertEquals(2, insts.size)
        assertTrue(insts[0] is Call)
    }

    @Test
    fun `removes dead code in loop body`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            br("loop")

            positionAtEnd(appendBlock("loop"))
            add(Constant.I32(1), Constant.I32(2)) // dead
            mul(Constant.I32(3), Constant.I32(4)) // dead
            condBr(params[0], "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val loopInsts = module.functions[0].blocks[1].instructions
        assertEquals(1, loopInsts.size)
        assertTrue(loopInsts[0] is CondBr)
    }

    @Test
    fun `preserves switch instruction`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            switch(params[0], "default", listOf(Constant.I32(0) to "case0", Constant.I32(1) to "case1"))

            positionAtEnd(appendBlock("case0"))
            ret(Constant.I32(10))

            positionAtEnd(appendBlock("case1"))
            ret(Constant.I32(20))

            positionAtEnd(appendBlock("default"))
            ret(Constant.I32(30))

            finalizeFunction()
        }
        val entryInsts = module.functions[0].blocks[0].instructions
        assertTrue(entryInsts[0] is Switch)
    }

    @Test
    fun `removes unused gep`() {
        val module = buildAndDCE {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            gep(structType, params[0], Constant.I32(0), Constant.I32(0)) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `handles multiple functions independently`() {
        val module = buildAndDCE {
            createFunction("f1", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            add(Constant.I32(1), Constant.I32(2)) // dead
            ret(Constant.I32(0))
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val v = add(Constant.I32(3), Constant.I32(4)) // used
            ret(v)
            finalizeFunction()
        }
        assertEquals(1, module.functions[0].blocks[0].instructions.size)
        assertEquals(2, module.functions[1].blocks[0].instructions.size)
    }

    @Test
    fun `removes unused conversion chain`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = zext(params[0], Type.I64)
            val b = trunc(a, Type.I32) // dead chain
            ret(params[0])
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `preserves unreachable instruction`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            add(Constant.I32(1), Constant.I32(2)) // dead
            unreachable()
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertTrue(insts[0] is Unreachable)
    }

    @Test
    fun `removes unused i64 arithmetic`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            add(Constant.I64(100L), Constant.I64(200L)) // dead
            mul(Constant.I64(300L), Constant.I64(400L)) // dead
            ret(Constant.I64(0L))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `preserves used icmp in condBr`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cmp = icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            condBr(cmp, "yes", "no")

            positionAtEnd(appendBlock("yes"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("no"))
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val entryInsts = module.functions[0].blocks[0].instructions
        assertEquals(2, entryInsts.size)
        assertTrue(entryInsts[0] is ICmp)
    }

    @Test
    fun `idempotent on already clean code`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val v = add(params[0], Constant.I32(1))
            ret(v)
            finalizeFunction()
        }
        val result = dce.run(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(2, insts.size)
    }

    @Test
    fun `removes unused phi node`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(params[0], "a", "b")

            positionAtEnd(appendBlock("a"))
            br("merge")

            positionAtEnd(appendBlock("b"))
            br("merge")

            positionAtEnd(appendBlock("merge"))
            phi(Type.I32, listOf(Constant.I32(1) to "a", Constant.I32(2) to "b")) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val mergeInsts = module.functions[0].blocks[3].instructions
        assertEquals(1, mergeInsts.size)
        assertTrue(mergeInsts[0] is Ret)
    }
}

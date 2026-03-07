package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class Mem2RegExtendedTest {

    private val mem2reg = Mem2Reg()

    private fun buildAndPromote(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return mem2reg.run(ir.build())
    }

    @Test
    fun `promotes i1 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I1)
            store(Constant.I1(true), ptr)
            val v = load(Type.I1, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertTrue((ret.value as Constant.I1).value)
    }

    @Test
    fun `promotes i8 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I8)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I8)
            store(Constant.I8(42), ptr)
            val v = load(Type.I8, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(42.toByte(), (ret.value as Constant.I8).value)
    }

    @Test
    fun `promotes i16 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I16)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I16)
            store(Constant.I16(1000), ptr)
            val v = load(Type.I16, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(1000.toShort(), (ret.value as Constant.I16).value)
    }

    @Test
    fun `promotes f32 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.F32)
            store(Constant.F32(2.5f), ptr)
            val v = load(Type.F32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(2.5f, (ret.value as Constant.F32).value)
    }

    @Test
    fun `load before store in i64 returns zero`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I64)
            val v = load(Type.I64, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(0L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `load before store in f64 returns zero`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.F64)
            val v = load(Type.F64, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(0.0, (ret.value as Constant.F64).value)
    }

    @Test
    fun `load before store in i1 returns false`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I1)
            val v = load(Type.I1, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertFalse((ret.value as Constant.I1).value)
    }

    @Test
    fun `promotes three allocas independently`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            val c = alloca(Type.I32)
            store(Constant.I32(1), a)
            store(Constant.I32(2), b)
            store(Constant.I32(3), c)
            val va = load(Type.I32, a)
            val vb = load(Type.I32, b)
            val vc = load(Type.I32, c)
            val ab = add(va, vb)
            val abc = add(ab, vc)
            ret(abc)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Instruction.Alloca })
        assertFalse(insts.any { it is Instruction.Store })
        assertFalse(insts.any { it is Instruction.Load })
    }

    @Test
    fun `promotes with overwritten store`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(100), ptr)
            store(Constant.I32(200), ptr) // overwrites
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(200, (ret.value as Constant.I32).value)
    }

    @Test
    fun `promotes with store in then branch only`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("cond", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            condBr(params[0], "then", "merge")

            positionAtEnd(appendBlock("then"))
            store(Constant.I32(42), ptr)
            br("merge")

            positionAtEnd(appendBlock("merge"))
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val mergeBlock = module.functions[0].blocks[2]
        val insts = mergeBlock.instructions
        assertTrue(insts.any { it is Instruction.Phi }, "Should insert phi: $insts")
        assertFalse(insts.any { it is Instruction.Load })
    }

    @Test
    fun `phi has correct values from diamond`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("cond", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            condBr(params[0], "then", "else")

            positionAtEnd(appendBlock("then"))
            store(Constant.I32(10), ptr)
            br("merge")

            positionAtEnd(appendBlock("else"))
            store(Constant.I32(20), ptr)
            br("merge")

            positionAtEnd(appendBlock("merge"))
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val mergeBlock = module.functions[0].blocks[3]
        val phi = mergeBlock.instructions.first { it is Instruction.Phi } as Instruction.Phi
        val values = phi.incoming.map { (v, _) -> (v as Constant.I32).value }.toSet()
        assertEquals(setOf(10, 20), values)
    }

    @Test
    fun `does not promote volatile store`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr, volatile = true)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Instruction.Alloca })
    }

    @Test
    fun `promotes alloca used in add computation`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(5), a)
            store(Constant.I32(7), b)
            val va = load(Type.I32, a)
            val vb = load(Type.I32, b)
            val sum = add(va, vb)
            val prod = mul(sum, Constant.I32(2))
            ret(prod)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Instruction.Alloca })
        val addInst = insts[0] as Instruction.Add
        assertEquals(5, (addInst.lhs as Constant.I32).value)
        assertEquals(7, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `promotes alloca stored with expression result`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            val sum = add(params[0], params[1])
            store(sum, ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size) // add + ret
        val ret = insts[1] as Instruction.Ret
        assertTrue(ret.value is InstructionRef)
    }

    @Test
    fun `promotes multiple loads from same alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v1 = load(Type.I32, ptr)
            val v2 = load(Type.I32, ptr)
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Instruction.Load })
        val addInst = insts[0] as Instruction.Add
        assertEquals(42, (addInst.lhs as Constant.I32).value)
        assertEquals(42, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `preserves non-promotable alloca when stored as value`() {
        val module = buildAndPromote {
            declareFunction("use_ptr", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            // ptr value itself is stored somewhere -> address escapes
            val ptrStore = alloca(Type.OpaquePointer)
            store(ptr, ptrStore)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val fn = module.functions.find { it.name == "f" }!!
        val insts = fn.blocks[0].instructions
        // ptr alloca should not be promoted since address is used in store as value
        assertTrue(insts.any { it is Instruction.Load })
    }

    @Test
    fun `handles store then load then store then load`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(1), ptr)
            val v1 = load(Type.I32, ptr)
            store(Constant.I32(2), ptr)
            val v2 = load(Type.I32, ptr)
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Instruction.Alloca })
        val addInst = insts[0] as Instruction.Add
        assertEquals(1, (addInst.lhs as Constant.I32).value)
        assertEquals(2, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `does not promote alloca passed to call`() {
        val module = buildAndPromote {
            declareFunction("side", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(1), ptr)
            call("side", listOf(ptr), Type.Void)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val fn = module.functions.find { it.name == "f" }!!
        assertTrue(fn.blocks[0].instructions.any { it is Instruction.Alloca })
    }

    @Test
    fun `promotes alloca in presence of unpromoted one`() {
        val module = buildAndPromote {
            declareFunction("side", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val good = alloca(Type.I32)
            val bad = alloca(Type.I32)
            store(Constant.I32(10), good)
            store(Constant.I32(20), bad)
            call("side", listOf(bad), Type.Void) // bad escapes
            val v = load(Type.I32, good) // good is promotable
            ret(v)
            finalizeFunction()
        }
        val fn = module.functions.find { it.name == "f" }!!
        val insts = fn.blocks[0].instructions
        // good alloca should be promoted, bad should remain
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(1, allocas.size, "Only bad alloca should remain: $allocas")
    }

    @Test
    fun `handles empty function`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret(null)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun `handles function with no allocas`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val v = add(params[0], Constant.I32(1))
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size)
    }

    @Test
    fun `promotes alloca in loop with phi`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            br("loop")

            positionAtEnd(appendBlock("loop"))
            val v = load(Type.I32, ptr)
            val next = add(v, Constant.I32(1))
            store(next, ptr)
            val cond = icmp(ICmpPredicate.SLT, next, params[0])
            condBr(cond, "loop", "exit")

            positionAtEnd(appendBlock("exit"))
            val result = load(Type.I32, ptr)
            ret(result)
            finalizeFunction()
        }
        val fn = module.functions[0]
        assertFalse(fn.blocks.any { b -> b.instructions.any { it is Instruction.Alloca } })
        val loopBlock = fn.blocks.find { it.label == "loop" }!!
        assertTrue(loopBlock.instructions.any { it is Instruction.Phi })
    }

    @Test
    fun `promotes two allocas with diamond and independent phis`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("cond", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(0), a)
            store(Constant.I32(0), b)
            condBr(params[0], "then", "else")

            positionAtEnd(appendBlock("then"))
            store(Constant.I32(1), a)
            store(Constant.I32(10), b)
            br("merge")

            positionAtEnd(appendBlock("else"))
            store(Constant.I32(2), a)
            store(Constant.I32(20), b)
            br("merge")

            positionAtEnd(appendBlock("merge"))
            val va = load(Type.I32, a)
            val vb = load(Type.I32, b)
            val sum = add(va, vb)
            ret(sum)
            finalizeFunction()
        }
        val mergeBlock = module.functions[0].blocks[3]
        val phis = mergeBlock.instructions.filterIsInstance<Instruction.Phi>()
        assertEquals(2, phis.size, "Should have two phis: $phis")
    }

    @Test
    fun `promotes i32 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(99), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(99, (ret.value as Constant.I32).value)
    }

    @Test
    fun `promotes f64 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.F64)
            store(Constant.F64(3.14), ptr)
            val v = load(Type.F64, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        val ret = insts[0] as Instruction.Ret
        assertEquals(3.14, (ret.value as Constant.F64).value)
    }

    @Test
    fun `handles multiple functions`() {
        val module = buildAndPromote {
            createFunction("f1", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val p1 = alloca(Type.I32)
            store(Constant.I32(10), p1)
            val v1 = load(Type.I32, p1)
            ret(v1)
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val p2 = alloca(Type.I32)
            store(Constant.I32(20), p2)
            val v2 = load(Type.I32, p2)
            ret(v2)
            finalizeFunction()
        }
        assertEquals(10, ((module.functions[0].blocks[0].instructions[0] as Instruction.Ret).value as Constant.I32).value)
        assertEquals(20, ((module.functions[1].blocks[0].instructions[0] as Instruction.Ret).value as Constant.I32).value)
    }
}

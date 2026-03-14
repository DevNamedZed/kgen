package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class LoopInvariantCodeMotionExtendedTest {

    private val licm = LoopInvariantCodeMotion()
    private val mem2reg = Mem2Reg()

    private fun buildModule(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    private fun buildWithMem2Reg(block: IrBuilder.() -> Unit): Module {
        return mem2reg.run(buildModule(block))
    }

    @Test
    fun `hoists loop-invariant subtraction`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val invariant = sub(params[0], params[1])
            val iNext = add(i, invariant)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Sub },
            "Hoisted sub should be in preheader")
    }

    @Test
    fun `hoists loop-invariant bitwise and`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val mask = and(params[0], params[1])
            val iNext = add(i, mask)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is And })
    }

    @Test
    fun `hoists loop-invariant shift`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val shifted = shl(params[0], Constant.I32(2))
            val iNext = add(i, shifted)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Shl })
    }

    @Test
    fun `hoists loop-invariant comparison`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val cmpInv = icmp(ICmpPredicate.SGT, params[0], params[1])
            val step = select(cmpInv, Constant.I32(2), Constant.I32(1))
            val iNext = add(i, step)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        // The invariant ICmp and Select should be hoisted
        val preheaderInsts = preheader!!.instructions
        assertTrue(preheaderInsts.any { it is ICmp },
            "Invariant icmp should be hoisted: $preheaderInsts")
        assertTrue(preheaderInsts.any { it is Select },
            "Invariant select should be hoisted: $preheaderInsts")
    }

    @Test
    fun `does not hoist store`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("ptr", Type.OpaquePointer), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            store(Constant.I32(42), params[0]) // side effect
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val loopBlock = fn.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        assertTrue(loopBlock!!.instructions.any { it is Store },
            "Store should remain in loop")
    }

    @Test
    fun `hoists floating point invariant`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val fInvariant = fmul(params[0], params[1]) // loop-invariant
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is FMul })
    }

    @Test
    fun `hoists invariant zext`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val ext = zext(params[0], Type.I64) // loop-invariant
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is ZExt })
    }

    @Test
    fun `multiple invariant instructions hoisted in order`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val inv1 = add(params[0], params[1])
            val inv2 = mul(params[1], params[2])
            val inv3 = sub(inv1, inv2)
            val iNext = add(i, inv3)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[3])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        val preInsts = preheader!!.instructions
        assertTrue(preInsts.any { it is Add })
        assertTrue(preInsts.any { it is Mul })
        assertTrue(preInsts.any { it is Sub })
    }

    @Test
    fun `does not hoist phi nodes`() {
        // Phi nodes can't be hoisted - they depend on the block structure
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val loopBlock = fn.blocks.find { it.label == "loop" }!!
        // Phi should remain in loop
        assertTrue(loopBlock.instructions.any { it is Phi },
            "Phi should remain in loop: ${loopBlock.instructions}")
    }

    @Test
    fun `handles function with single block`() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(params[0])
            finalizeFunction()
        }

        val result = licm.run(module)
        assertEquals(1, result.functions[0].blocks.size)
    }

    @Test
    fun `handles function with no back edges`() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            condBr(params[0], BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(Constant.I32(1))

            appendBlock("else")
            ret(Constant.I32(2))

            finalizeFunction()
        }

        val result = licm.run(module)
        val fn = result.functions[0]
        assertNull(fn.blocks.find { it.label.contains("preheader") },
            "No preheader should be created for non-loop")
    }

    @Test
    fun `hoists invariant neg`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val negX = neg(params[0]) // loop-invariant
            val iNext = add(i, negX)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Neg })
    }

    @Test
    fun `does not hoist alloca`() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("loop"))

            appendBlock("loop")
            val ptr = alloca(Type.I32) // allocas should not be hoisted
            store(Constant.I32(1), ptr)
            val v = load(Type.I32, ptr)
            val cond = icmp(ICmpPredicate.SGT, v, params[0])
            condBr(cond, BlockRef("exit"), BlockRef("loop"))

            appendBlock("exit")
            ret(Constant.I32(0))

            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val loopBlock = fn.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        assertTrue(loopBlock!!.instructions.any { it is Alloca },
            "Alloca should stay in loop")
    }

    @Test
    fun `hoists constant-only arithmetic`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val constant = add(Constant.I32(3), Constant.I32(7)) // purely constant, invariant
            val iNext = add(i, constant)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Add },
            "Constant add should be hoisted: ${preheader.instructions}")
    }

    @Test
    fun `handles multiple external functions`() {
        val module = buildModule {
            declareFunction("ext1", emptyList(), Type.I32)
            declareFunction("ext2", listOf(Param("x", Type.I32)), Type.Void)
        }
        val result = licm.run(module)
        assertEquals(2, result.functions.size)
        assertTrue(result.functions.all { it.isExternal })
    }

    @Test
    fun `hoists invariant gep`() {
        val module = buildWithMem2Reg {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val params = createFunction("f", listOf(
                Param("ptr", Type.OpaquePointer), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val fieldPtr = gep(structType, params[0], Constant.I32(0), Constant.I32(1)) // invariant
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is GetElementPtr })
    }

    @Test
    fun `preheader branches to header`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val inv = add(params[0], params[1])
            val iNext = add(i, inv)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        val last = preheader!!.instructions.last()
        assertTrue(last is Br, "Preheader should end with br")
        assertEquals(BlockRef("loop"), (last as Br).target, "Preheader should branch to loop header")
    }

    @Test
    fun `hoists invariant or`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val ored = or(params[0], params[1])
            val iNext = add(i, ored)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Or })
    }

    @Test
    fun `hoists invariant xor`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val xored = xor(params[0], params[1])
            val iNext = add(i, xored)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Xor })
    }

    @Test
    fun `hoists invariant mul`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val product = mul(params[0], params[1])
            val iNext = add(i, product)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is Mul })
    }

    @Test
    fun `does not hoist call`() {
        val module = buildWithMem2Reg {
            declareFunction("sideEffect", emptyList(), Type.I32)
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val v = call("sideEffect", emptyList(), Type.I32)!!
            val iNext = add(i, v)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions.find { it.name == "f" }!!
        val loopBlock = fn.blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        assertTrue(loopBlock!!.instructions.any { it is Call },
            "Call should remain in loop")
    }

    @Test
    fun `hoists invariant sext`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val ext = sext(params[0], Type.I64)
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is SExt })
    }

    @Test
    fun `hoists invariant fadd`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val fInvariant = fadd(params[0], params[1])
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is FAdd })
    }

    @Test
    fun `hoists invariant sitofp`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val converted = sitofp(params[0], Type.F64)
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label == "loop_preheader" }
        assertNotNull(preheader)
        assertTrue(preheader!!.instructions.any { it is SIToFP })
    }

    @Test
    fun `hoists load from param when loop has no aliasing stores`() {
        // After mem2reg, iSlot becomes a phi — no stores remain in the loop.
        // Alias analysis: param vs alloca = NoAlias, so load is hoistable.
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("ptr", Type.OpaquePointer), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val v = load(Type.I32, params[0])
            val iNext = add(i, v)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[1])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label.contains("preheader") }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Load },
            "Load from param should be hoisted (no aliasing stores in loop)")
    }

    @Test
    fun `idempotent on code without loops`() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val v = add(params[0], Constant.I32(1))
            ret(v)
            finalizeFunction()
        }
        val result = licm.run(module)
        assertEquals(module.functions[0].blocks.size, result.functions[0].blocks.size)
    }

    @Test
    fun `entry block redirected to preheader`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val inv = add(params[0], params[1])
            val iNext = add(i, inv)
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[2])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, iSlot)
            ret(result)
            finalizeFunction()
        }

        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val entry = fn.blocks[0]
        val last = entry.instructions.last()
        assertTrue(last is Br)
        assertEquals(BlockRef("loop_preheader"), (last as Br).target,
            "Entry should branch to preheader, not loop")
    }
}

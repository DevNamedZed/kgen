package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class OptimizationPassExtendedTest {

    private val dce = DeadCodeElimination()
    private val licm = LoopInvariantCodeMotion()
    private val inliner = Inlining()
    private val jt = JumpThreading()
    private val gvn = GlobalValueNumbering()
    private val mem2reg = Mem2Reg()
    private val sroa = ScalarReplacementOfAggregates()
    private val instCombine = InstructionCombining()

    private fun build(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    private fun buildWithMem2Reg(block: IrBuilder.() -> Unit): Module {
        return mem2reg.run(build(block))
    }

    // --- DCE: dead stores to local memory ---

    @Test
    fun `dce removes dead sub instruction`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            sub(params[0], Constant.I32(1)) // dead
            ret(params[0])
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Dead sub should be removed: $insts")
        assertTrue(insts[0] is Ret)
    }

    @Test
    fun `dce removes dead icmp`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            icmp(ICmpPredicate.EQ, params[0], Constant.I32(0)) // dead
            ret(params[0])
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Dead icmp should be removed: $insts")
    }

    @Test
    fun `dce removes dead zext`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            appendBlock("entry")
            zext(params[0], Type.I64) // dead
            ret(Constant.I64(0))
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Dead zext should be removed: $insts")
    }

    @Test
    fun `dce preserves alloca used by store`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(10), a)
            val v = load(Type.I32, a)
            store(v, params[0])
            ret(null)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Alloca }, "Alloca used by store should remain")
    }

    @Test
    fun `dce removes long dead chain across multiple types`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(1))
            val b = mul(a, Constant.I32(2))
            val c = sub(b, Constant.I32(3))
            val d = add(c, Constant.I32(4))
            sub(d, Constant.I32(5)) // entire chain is dead
            ret(Constant.I32(99))
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Entire dead chain should be removed: $insts")
    }

    @Test
    fun `dce preserves load with used result`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val v = load(Type.I32, params[0])
            ret(v)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "Load + ret should remain: $insts")
    }

    @Test
    fun `dce removes dead select`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            select(params[0], Constant.I32(1), Constant.I32(2)) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Dead select should be removed: $insts")
    }

    @Test
    fun `dce removes dead neg`() {
        val module = dce.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            neg(params[0]) // dead
            ret(params[0])
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Dead neg should be removed: $insts")
    }

    // --- LICM: various invariant patterns ---

    @Test
    fun `licm hoists invariant subtraction`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val diff = sub(params[0], params[1]) // loop-invariant
            val iNext = add(i, diff)
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
        assertNotNull(preheader, "Preheader should be created: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Sub },
            "Hoisted sub should be in preheader: ${preheader.instructions}")
    }

    @Test
    fun `licm does not hoist stores`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("ptr", Type.OpaquePointer), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            store(Constant.I32(42), params[0]) // side-effecting, must not hoist
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
            "Store should stay in loop: ${loopBlock.instructions}")
    }

    @Test
    fun `licm hoists invariant icmp`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val flag = icmp(ICmpPredicate.SGT, params[0], params[1]) // loop-invariant
            val chosen = select(flag, Constant.I32(10), Constant.I32(1))
            val iNext = add(i, chosen)
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
        assertTrue(preheader!!.instructions.any { it is ICmp },
            "Invariant icmp should be hoisted: ${preheader.instructions}")
    }

    @Test
    fun `licm handles single block function without crash`() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(params[0])
            finalizeFunction()
        }
        val result = licm.run(module)
        assertEquals(1, result.functions[0].blocks.size)
    }

    @Test
    fun `licm hoists invariant select`() {
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("c", Type.I1), Param("a", Type.I32), Param("b", Type.I32), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val chosen = select(params[0], params[1], params[2]) // loop-invariant
            val iNext = add(i, chosen)
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
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        assertTrue(preheader!!.instructions.any { it is Select },
            "Invariant select should be hoisted: ${preheader.instructions}")
    }

    // --- Inlining ---

    @Test
    fun `inlining respects max instruction count threshold`() {
        val tinyInliner = Inlining(maxInstructionCount = 1)
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("add2", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val r = ir.add(params[0], Constant.I32(2))
        ir.ret(r)
        ir.finalizeFunction()

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val v = ir.call("add2", listOf(Constant.I32(3)), Type.I32)!!
        ir.ret(v)
        ir.finalizeFunction()

        val module = tinyInliner.run(ir.build())
        val mainInsts = module.functions[1].blocks[0].instructions
        // add2 has 2 instructions (add + ret), threshold is 1, so should NOT inline
        assertTrue(mainInsts.any { it is Call }, "Should not inline: $mainInsts")
    }

    @Test
    fun `inlining handles multiple call sites to same function`() {
        val module = inliner.run(build {
            val p = createFunction("inc", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = add(p[0], Constant.I32(1))
            ret(r)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val x = call("inc", listOf(params[0]), Type.I32)!!
            val y = call("inc", listOf(params[1]), Type.I32)!!
            val sum = add(x, y)
            ret(sum)
            finalizeFunction()
        })
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call }, "Both calls should be inlined: $mainInsts")
        val adds = mainInsts.filterIsInstance<Add>()
        assertEquals(3, adds.size, "Should have 2 inlined adds + 1 sum add: $mainInsts")
    }

    @Test
    fun `inlining substitutes parameters correctly for sub`() {
        val module = inliner.run(build {
            val p = createFunction("diff", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sub(p[0], p[1])
            ret(r)
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val result = call("diff", listOf(Constant.I32(10), Constant.I32(3)), Type.I32)!!
            ret(result)
            finalizeFunction()
        })
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call }, "Call should be inlined: $mainInsts")
        val subInst = mainInsts.filterIsInstance<Sub>().firstOrNull()
        assertNotNull(subInst, "Should have inlined sub: $mainInsts")
        assertEquals("10", subInst!!.lhs.name)
        assertEquals("3", subInst.rhs.name)
    }

    @Test
    fun `inlining handles nested inlining in same function`() {
        val module = inliner.run(build {
            val p1 = createFunction("add1", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p1[0], Constant.I32(1)))
            finalizeFunction()

            val p2 = createFunction("add2", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = call("add1", listOf(p2[0]), Type.I32)!!
            ret(add(r, Constant.I32(1)))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            appendBlock("entry")
            val result = call("add2", listOf(Constant.I32(0)), Type.I32)!!
            ret(result)
            finalizeFunction()
        })
        val mainInsts = module.functions[2].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call }, "All calls should be inlined: $mainInsts")
    }

    @Test
    fun `inlining with mul instruction`() {
        val module = inliner.run(build {
            val p = createFunction("cube", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val sq = mul(p[0], p[0])
            val cu = mul(sq, p[0])
            ret(cu)
            finalizeFunction()

            val params = createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("cube", listOf(params[0]), Type.I32)!!
            ret(result)
            finalizeFunction()
        })
        val mainInsts = module.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Call }, "Call should be inlined: $mainInsts")
        val muls = mainInsts.filterIsInstance<Mul>()
        assertEquals(2, muls.size, "Should have 2 inlined muls: $mainInsts")
    }

    // --- Jump Threading ---

    @Test
    fun `jt threads through chain of unconditional branches`() {
        val module = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("a"))
            appendBlock("a")
            br(BlockRef("b"))
            appendBlock("b")
            br(BlockRef("c"))
            appendBlock("c")
            ret(Constant.I32(42))
            finalizeFunction()
        })
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "All blocks should merge: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last()
        assertTrue(last is Ret)
        assertEquals(42, ((last as Ret).value as Constant.I32).value)
    }

    @Test
    fun `jt removes dead blocks from constant switch`() {
        val module = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("live"), BlockRef("dead"))

            appendBlock("live")
            ret(Constant.I32(1))

            appendBlock("dead")
            ret(Constant.I32(2))

            finalizeFunction()
        })
        val labels = module.functions[0].blocks.map { it.label }
        assertFalse("dead" in labels, "Dead block should be removed: $labels")
    }

    @Test
    fun `jt handles diamond pattern with constant conditions`() {
        val module = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(true), BlockRef("left"), BlockRef("right"))

            appendBlock("left")
            br(BlockRef("merge"))

            appendBlock("right")
            br(BlockRef("merge"))

            appendBlock("merge")
            ret(Constant.I32(0))

            finalizeFunction()
        })
        val blocks = module.functions[0].blocks
        val labels = blocks.map { it.label }
        // "right" is unreachable and should be removed
        assertFalse("right" in labels, "Dead right branch should be removed: $labels")
        // entry + left should merge (entry branches unconditionally to left after constant fold)
        assertTrue(blocks.size <= 3, "Should have at most 3 blocks: $labels")
    }

    @Test
    fun `jt preserves block with instructions before branch`() {
        val module = jt.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val v = add(params[0], Constant.I32(1))
            br(BlockRef("next"))
            appendBlock("next")
            ret(v)
            finalizeFunction()
        })
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "Should merge: ${blocks.map { it.label }}")
        val insts = blocks[0].instructions
        assertEquals(2, insts.size, "add + ret: $insts")
    }

    @Test
    fun `jt handles multiple constant branches in sequence`() {
        val module = jt.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            condBr(Constant.I1(false), BlockRef("d1"), BlockRef("a"))

            appendBlock("a")
            condBr(Constant.I1(true), BlockRef("b"), BlockRef("d2"))

            appendBlock("b")
            condBr(Constant.I1(false), BlockRef("d3"), BlockRef("c"))

            appendBlock("c")
            ret(Constant.I32(100))

            appendBlock("d1")
            ret(Constant.I32(0))
            appendBlock("d2")
            ret(Constant.I32(0))
            appendBlock("d3")
            ret(Constant.I32(0))

            finalizeFunction()
        })
        val blocks = module.functions[0].blocks
        assertEquals(1, blocks.size, "All resolved to single path: ${blocks.map { it.label }}")
        val last = blocks[0].instructions.last() as Ret
        assertEquals(100, (last.value as Constant.I32).value)
    }

    // --- GVN ---

    @Test
    fun `gvn eliminates redundant sub`() {
        val module = gvn.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = sub(params[0], params[1])
            val b = sub(params[0], params[1]) // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "Redundant sub should be eliminated: $insts")
        val sumInst = insts[1] as Add
        assertEquals(sumInst.lhs.name, sumInst.rhs.name)
    }

    @Test
    fun `gvn eliminates redundant and`() {
        val module = gvn.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = and(params[0], params[1])
            val b = and(params[0], params[1]) // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "Redundant and should be eliminated: $insts")
    }

    @Test
    fun `gvn eliminates redundant select`() {
        val module = gvn.run(build {
            val params = createFunction("f", listOf(Param("c", Type.I1), Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = select(params[0], params[1], params[2])
            val b = select(params[0], params[1], params[2]) // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "Redundant select should be eliminated: $insts")
    }

    @Test
    fun `gvn eliminates three redundant adds`() {
        val module = gvn.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], params[1])
            val b = add(params[0], params[1]) // redundant
            val c = add(params[0], params[1]) // redundant
            val sum = add(a, add(b, c))
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        val addCount = insts.count { it is Add }
        // a remains, b and c eliminated, then add(b,c) -> add(a,a), add(a, add(a,a)) remains
        assertEquals(3, addCount, "Only 3 adds should remain (original + 2 combining): $insts")
    }

    @Test
    fun `gvn preserves stores even with same operands`() {
        val module = gvn.run(build {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            store(Constant.I32(42), params[0])
            store(Constant.I32(42), params[0]) // NOT redundant (side effect)
            ret(null)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        val storeCount = insts.count { it is Store }
        assertEquals(2, storeCount, "Stores should not be eliminated")
    }

    // --- Mem2Reg ---

    @Test
    fun `mem2reg promotes three allocas`() {
        val module = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            val c = alloca(Type.I32)
            store(Constant.I32(1), a)
            store(Constant.I32(2), b)
            store(Constant.I32(3), c)
            val va = load(Type.I32, a)
            val vb = load(Type.I32, b)
            val vc = load(Type.I32, c)
            val sum = add(va, add(vb, vc))
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Alloca }, "All allocas should be promoted: $insts")
        assertFalse(insts.any { it is Store }, "All stores should be removed: $insts")
        assertFalse(insts.any { it is Load }, "All loads should be removed: $insts")
    }

    @Test
    fun `mem2reg inserts phi at merge point with different values`() {
        val module = mem2reg.run(build {
            val params = createFunction("f", listOf(Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            condBr(params[0], BlockRef("left"), BlockRef("right"))

            appendBlock("left")
            store(Constant.I32(10), ptr)
            br(BlockRef("merge"))

            appendBlock("right")
            store(Constant.I32(20), ptr)
            br(BlockRef("merge"))

            appendBlock("merge")
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        })
        val mergeBlock = module.functions[0].blocks.find { it.label == "merge" }
        assertNotNull(mergeBlock)
        assertTrue(mergeBlock!!.instructions.any { it is Phi },
            "Should have phi at merge: ${mergeBlock.instructions}")
        val phi = mergeBlock.instructions.first { it is Phi } as Phi
        val vals = phi.incoming.map { (v, _) -> (v as Constant.I32).value }.toSet()
        assertEquals(setOf(10, 20), vals)
    }

    @Test
    fun `mem2reg handles nested if-else`() {
        val module = mem2reg.run(build {
            val params = createFunction("f", listOf(Param("c1", Type.I1), Param("c2", Type.I1)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            condBr(params[0], BlockRef("outer_true"), BlockRef("outer_false"))

            appendBlock("outer_true")
            condBr(params[1], BlockRef("inner_true"), BlockRef("inner_false"))

            appendBlock("inner_true")
            store(Constant.I32(1), ptr)
            br(BlockRef("outer_merge"))

            appendBlock("inner_false")
            store(Constant.I32(2), ptr)
            br(BlockRef("outer_merge"))

            appendBlock("outer_false")
            store(Constant.I32(3), ptr)
            br(BlockRef("outer_merge"))

            appendBlock("outer_merge")
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        })
        val mergeBlock = module.functions[0].blocks.find { it.label == "outer_merge" }
        assertNotNull(mergeBlock)
        assertTrue(mergeBlock!!.instructions.any { it is Phi },
            "Should have phi: ${mergeBlock.instructions}")
        assertFalse(mergeBlock.instructions.any { it is Load },
            "Load should be removed: ${mergeBlock.instructions}")
    }

    @Test
    fun `mem2reg promotes i1 alloca`() {
        val module = mem2reg.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I1)
            store(Constant.I1(true), ptr)
            val v = load(Type.I1, ptr)
            val result = select(v, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertFalse(insts.any { it is Alloca }, "I1 alloca should be promoted: $insts")
    }

    @Test
    fun `mem2reg with store-load in loop`() {
        val module = mem2reg.run(build {
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
        })
        // After mem2reg, the loop block should have a phi for i
        val loopBlock = module.functions[0].blocks.find { it.label == "loop" }
        assertNotNull(loopBlock)
        assertTrue(loopBlock!!.instructions.any { it is Phi },
            "Loop should have phi after mem2reg: ${loopBlock.instructions}")
        assertFalse(loopBlock.instructions.any { it is Load },
            "Loads should be removed: ${loopBlock.instructions}")
    }

    // --- SROA ---

    @Test
    fun `sroa splits struct alloca into field allocas`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            val f0 = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(10), f0)
            val f1 = gep(structType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(20), f1)
            val v0 = load(Type.I32, f0)
            val v1 = load(Type.I32, f1)
            val sum = add(v0, v1)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        // Original struct alloca should be gone, replaced by two scalar allocas
        val allocas = insts.filterIsInstance<Alloca>()
        assertTrue(allocas.all { it.allocType == Type.I32 },
            "Struct alloca should be split into I32 allocas: $allocas")
        assertEquals(2, allocas.size, "Should have 2 scalar allocas: $allocas")
    }

    @Test
    fun `sroa splits small array alloca`() {
        val arrayType = Type.Array(Type.I32, 2)
        val module = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arrayType)
            val e0 = gep(arrayType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(100), e0)
            val e1 = gep(arrayType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(200), e1)
            val v0 = load(Type.I32, e0)
            val v1 = load(Type.I32, e1)
            val sum = add(v0, v1)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(2, allocas.size, "Array[2] should be split into 2 allocas: $allocas")
        assertTrue(allocas.all { it.allocType == Type.I32 })
    }

    @Test
    fun `sroa does not split when address escapes to call`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = sroa.run(build {
            declareFunction("use_struct", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            call("use_struct", listOf(ptr), Type.Void) // address escapes
            val f0 = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
            val v = load(Type.I32, f0)
            ret(v)
            finalizeFunction()
        })
        val insts = module.functions[1].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertTrue(allocas.any { it.allocType == structType },
            "Struct alloca should remain when address escapes: $allocas")
    }

    @Test
    fun `sroa handles struct with three fields`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64, Type.I32))
        val module = sroa.run(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(structType)
            val f0 = gep(structType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), f0)
            val f2 = gep(structType, ptr, Constant.I32(0), Constant.I32(2))
            store(Constant.I32(3), f2)
            val v0 = load(Type.I32, f0)
            val v2 = load(Type.I32, f2)
            val sum = add(v0, v2)
            ret(sum)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        // Should have 3 allocas: I32, I64, I32 for the three fields
        assertEquals(3, allocas.size, "Should have 3 field allocas: $allocas")
    }

    // --- Instruction Combining ---

    @Test
    fun `instcombine simplifies x + 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = add(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x + 0 should be simplified: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return x directly: ${ret.value}")
    }

    @Test
    fun `instcombine simplifies x * 1`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(params[0], Constant.I32(1))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x * 1 should be simplified: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return x directly: ${ret.value}")
    }

    @Test
    fun `instcombine simplifies x * 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x * 0 should be simplified: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Constant.I32, "Should return 0: ${ret.value}")
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `instcombine simplifies x - 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sub(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x - 0 should be simplified: $insts")
    }

    @Test
    fun `instcombine simplifies x and 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = and(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x & 0 should be simplified: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Constant.I32, "Should return 0: ${ret.value}")
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `instcombine simplifies x or 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = or(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x | 0 should be simplified: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return x directly: ${ret.value}")
    }

    @Test
    fun `instcombine simplifies x xor 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = xor(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x ^ 0 should be simplified: $insts")
    }

    @Test
    fun `instcombine simplifies x shl 0`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = shl(params[0], Constant.I32(0))
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "x << 0 should be simplified: $insts")
    }

    @Test
    fun `instcombine simplifies select with constant true`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = select(Constant.I1(true), params[0], params[1])
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "select(true, a, b) should simplify to a: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return param: ${ret.value}")
        assertEquals("a", (ret.value as Parameter).name.removePrefix("%"))
    }

    @Test
    fun `instcombine simplifies select with constant false`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = select(Constant.I1(false), params[0], params[1])
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "select(false, a, b) should simplify to b: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return param: ${ret.value}")
        assertEquals("b", (ret.value as Parameter).name.removePrefix("%"))
    }

    @Test
    fun `instcombine simplifies 0 + x`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = add(Constant.I32(0), params[0])
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "0 + x should be simplified: $insts")
    }

    @Test
    fun `instcombine simplifies 1 * x`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(Constant.I32(1), params[0])
            ret(r)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "1 * x should be simplified: $insts")
    }

    @Test
    fun `instcombine chained simplifications`() {
        val module = instCombine.run(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(0))  // x + 0 -> x
            val b = mul(a, Constant.I32(1))           // x * 1 -> x (after a -> x)
            ret(b)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Chained simplifications should reduce to ret: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return x directly: ${ret.value}")
    }

    // --- Pass Pipeline ---

    @Test
    fun `pipeline combines mem2reg and dce`() {
        val pipeline = PassPipeline()
            .add(mem2reg)
            .add(dce)

        val module = pipeline.execute(build {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(42), a)
            val v = load(Type.I32, a)
            add(v, Constant.I32(0)) // dead after mem2reg
            ret(v)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "After mem2reg+dce only ret should remain: $insts")
    }

    @Test
    fun `pipeline combines instcombine and dce`() {
        val pipeline = PassPipeline()
            .add(instCombine)
            .add(dce)

        val module = pipeline.execute(build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(0)) // simplified to x
            val b = mul(params[0], Constant.I32(0)) // simplified to 0, now dead if unused...
            // but b is used:
            val c = add(a, b) // add(x, 0) after instcombine
            ret(c)
            finalizeFunction()
        })
        // After instcombine: add(x,0)->x, mul(x,0)->0, add(x,0)->x => ret(x)
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Pipeline should reduce to ret: $insts")
    }

    @Test
    fun `pipeline of gvn then dce removes duplicates and dead code`() {
        val pipeline = PassPipeline()
            .add(gvn)
            .add(dce)

        val module = pipeline.execute(build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], params[1])
            add(params[0], params[1]) // redundant, removed by gvn
            val c = mul(a, a)
            ret(c)
            finalizeFunction()
        })
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "add, mul, ret: $insts")
    }
}

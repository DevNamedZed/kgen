package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class LicmAliasAnalysisTest {

    private val licm = LoopInvariantCodeMotion()
    private val mem2reg = Mem2Reg()

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    private fun buildWithMem2Reg(block: ModuleBuilder.() -> Unit): Module {
        return mem2reg.run(buildModule(block))
    }

    @Test
    fun `hoists load from non-aliasing alloca out of loop`() {
        // Two allocas: 'a' is only read in the loop, 'b' is written.
        // After mem2reg, 'b' becomes a phi but 'a' remains as alloca+load
        // because it's used as a pointer (loaded from, not promoted).
        // Actually, mem2reg will promote both. So we need to use pointer params instead.
        // Use a pattern where the load is from a global (not promotable by mem2reg).
        val module = buildWithMem2Reg {
            addGlobal("readOnly", Type.I32, Constant.I32(42))
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            val sumSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            store(Constant.I32(0), sumSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val sum = load(Type.I32, sumSlot)
            val gval = load(Type.I32, GlobalRef("readOnly", Type.Pointer(Type.I32)))
            val newSum = add(sum, gval)
            store(newSum, sumSlot)
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, sumSlot)
            ret(result)
            finalizeFunction()
        }

        // After mem2reg, the load from global remains (not promotable).
        // The loop stores only to promotable allocas (now phis), so the global load
        // should be hoistable since globals don't alias allocas.
        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label.contains("preheader") }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        val preheaderLoads = preheader!!.instructions.filterIsInstance<Load>()
        assertTrue(preheaderLoads.isNotEmpty(), "Load from global should be hoisted to preheader")
    }

    @Test
    fun `does not hoist load that may alias loop store`() {
        // Load from a pointer parameter — store also goes to same pointer
        val module = buildWithMem2Reg {
            val params = createFunction("f", listOf(
                Param("ptr", Type.Pointer(Type.I32)), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val v = load(Type.I32, params[0])   // load from param ptr
            store(v, params[0])                 // store to same param ptr
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
        val loopBlock = fn.blocks.find { it.label == "loop" }!!
        val loopLoads = loopBlock.instructions.filterIsInstance<Load>()
        assertTrue(loopLoads.isNotEmpty(), "Load aliasing loop store should NOT be hoisted")
    }

    @Test
    fun `hoists load from global when loop only writes to local alloca`() {
        val module = buildWithMem2Reg {
            addGlobal("g", Type.I32, Constant.I32(42))
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val gval = load(Type.I32, GlobalRef("g", Type.Pointer(Type.I32)))
            val iNext = add(i, gval)
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
        val preheader = fn.blocks.find { it.label.contains("preheader") }
        assertNotNull(preheader, "Preheader should exist")
        val preheaderLoads = preheader!!.instructions.filterIsInstance<Load>()
        assertTrue(preheaderLoads.isNotEmpty(), "Load from global should be hoisted when loop has no stores to globals")
    }

    @Test
    fun `does not hoist volatile load even when non-aliasing`() {
        val module = buildWithMem2Reg {
            addGlobal("g", Type.I32, Constant.I32(42))
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val gval = load(Type.I32, GlobalRef("g", Type.Pointer(Type.I32)), null, true) // volatile
            val iNext = add(i, gval)
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
        val loopLoads = loopBlock.instructions.filterIsInstance<Load>()
        assertTrue(loopLoads.any { it.volatile }, "Volatile load should NOT be hoisted")
    }

    @Test
    fun `hoists load when loop store is to different struct field`() {
        val module = buildModule {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            val params = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val s = alloca(structType)
            val f0 = gep(structType, s, Constant.I32(0), Constant.I32(0))
            val f1 = gep(structType, s, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(42), f0)
            store(Constant.I32(0), f1)
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val v = load(Type.I32, f0)   // field 0 — should be hoistable
            store(v, f1)                 // writes to field 1 only
            val iNext = add(i, Constant.I32(1))
            store(iNext, iSlot)
            val cond = icmp(ICmpPredicate.SLT, iNext, params[0])
            condBr(cond, BlockRef("loop"), BlockRef("exit"))

            appendBlock("exit")
            val result = load(Type.I32, f1)
            ret(result)
            finalizeFunction()
        }

        // Don't use mem2reg here — the GEP/struct pattern shouldn't be promoted
        val hoisted = licm.run(module)
        val fn = hoisted.functions[0]
        val preheader = fn.blocks.find { it.label.contains("preheader") }
        assertNotNull(preheader, "Preheader should exist: ${fn.blocks.map { it.label }}")
        val preheaderLoads = preheader!!.instructions.filterIsInstance<Load>()
        assertTrue(preheaderLoads.isNotEmpty(), "Load from field 0 should be hoisted when loop only stores to field 1")
    }

    @Test
    fun `does not hoist load when call exists in loop`() {
        val module = buildWithMem2Reg {
            declareFunction("sideEffect", emptyList(), Type.Void)
            val params = createFunction("f", listOf(
                Param("ptr", Type.Pointer(Type.I32)), Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val iSlot = alloca(Type.I32)
            store(Constant.I32(0), iSlot)
            br(BlockRef("loop"))

            appendBlock("loop")
            val i = load(Type.I32, iSlot)
            val v = load(Type.I32, params[0])  // cannot hoist — call may modify *ptr
            call("sideEffect", emptyList(), Type.Void)
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
        val fn = hoisted.functions.find { !it.isExternal }!!
        val loopBlock = fn.blocks.find { it.label == "loop" }!!
        val loopLoads = loopBlock.instructions.filterIsInstance<Load>()
        assertTrue(loopLoads.isNotEmpty(), "Load should NOT be hoisted when loop contains a call")
    }
}

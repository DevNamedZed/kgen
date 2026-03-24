package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class GvnLoadEliminationTest {

    private val gvn = GlobalValueNumbering()

    private fun buildAndGvn(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return gvn.run(ir.build())
    }

    @Test
    fun `eliminates redundant load from same pointer`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
            appendBlock("entry")
            val a = load(Type.I32, params[0])
            val b = load(Type.I32, params[0])  // redundant — same pointer, no store between
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // Should have: load, add, ret (second load eliminated)
        assertEquals(3, insts.size, "Redundant load should be eliminated: $insts")
        val addInst = insts[1] as Add
        assertEquals(addInst.lhs.name, addInst.rhs.name, "Both operands should be the same load result")
    }

    @Test
    fun `does not eliminate load after intervening store to same pointer`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
            appendBlock("entry")
            val a = load(Type.I32, params[0])
            store(Constant.I32(42), params[0])  // invalidates
            val b = load(Type.I32, params[0])    // NOT redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(2, loads.size, "Both loads should remain after intervening store")
    }

    @Test
    fun `eliminates redundant load when intervening store is to different alloca`() {
        val module = buildAndGvn {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(10), a)
            store(Constant.I32(20), b)
            val v1 = load(Type.I32, a)
            store(Constant.I32(30), b)  // store to b — doesn't alias a
            val v2 = load(Type.I32, a)  // redundant — a not modified
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(1, loads.size, "Second load from 'a' should be eliminated (store was to 'b')")
    }

    @Test
    fun `does not eliminate volatile load`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
            appendBlock("entry")
            val a = load(Type.I32, params[0], volatile = true)
            val b = load(Type.I32, params[0], volatile = true)  // volatile — keep
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(2, loads.size, "Volatile loads should not be eliminated")
    }

    @Test
    fun `eliminates redundant load after store to different global`() {
        val module = buildAndGvn {
            addGlobal("g1", Type.I32, Constant.I32(0))
            addGlobal("g2", Type.I32, Constant.I32(0))
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val g1ref = GlobalRef("g1", Type.Pointer(Type.I32))
            val g2ref = GlobalRef("g2", Type.Pointer(Type.I32))
            val v1 = load(Type.I32, g1ref)
            store(Constant.I32(99), g2ref)   // store to g2 — doesn't alias g1
            val v2 = load(Type.I32, g1ref)   // redundant
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(1, loads.size, "Second load from g1 should be eliminated (store was to g2)")
    }

    @Test
    fun `call invalidates all loads`() {
        val module = buildAndGvn {
            declareFunction("sideEffect", emptyList(), Type.Void)
            val params = createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
            appendBlock("entry")
            val a = load(Type.I32, params[0])
            call("sideEffect", emptyList(), Type.Void)
            val b = load(Type.I32, params[0])  // not redundant — call may modify *ptr
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val fn = module.functions.find { !it.isExternal }!!
        val insts = fn.blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(2, loads.size, "Both loads should remain after intervening call")
    }

    @Test
    fun `eliminates three redundant loads from same alloca`() {
        val module = buildAndGvn {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(42), a)
            val v1 = load(Type.I32, a)
            val v2 = load(Type.I32, a)  // redundant
            val v3 = load(Type.I32, a)  // redundant
            val sum = add(v1, add(v2, v3))
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(1, loads.size, "Two redundant loads should be eliminated")
    }

    @Test
    fun `load elimination across dominator tree`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32)), Param("c", Type.I1)), Type.I32)
            appendBlock("entry")
            val v1 = load(Type.I32, params[0])
            condBr(params[1], BlockRef("left"), BlockRef("right"))

            appendBlock("left")
            val v2 = load(Type.I32, params[0])  // redundant — dominated by entry's load, no store
            ret(v2)

            appendBlock("right")
            val v3 = load(Type.I32, params[0])  // redundant
            ret(v3)
            finalizeFunction()
        }
        val fn = module.functions[0]
        val entryLoads = fn.blocks[0].instructions.filterIsInstance<Load>()
        assertEquals(1, entryLoads.size, "Entry should keep its load")

        val leftLoads = fn.blocks.find { it.label == "left" }!!.instructions.filterIsInstance<Load>()
        val rightLoads = fn.blocks.find { it.label == "right" }!!.instructions.filterIsInstance<Load>()
        assertEquals(0, leftLoads.size, "Left branch load should be eliminated")
        assertEquals(0, rightLoads.size, "Right branch load should be eliminated")
    }

    @Test
    fun `store to struct field does not invalidate load from different field`() {
        val module = buildAndGvn {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val s = alloca(structType)
            val f0 = gep(structType, s, Constant.I32(0), Constant.I32(0))
            val f1 = gep(structType, s, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(10), f0)
            store(Constant.I32(20), f1)
            val v1 = load(Type.I32, f0)
            store(Constant.I32(99), f1)  // store to field 1
            val v2 = load(Type.I32, f0)  // redundant — field 0 not modified
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loads = insts.filterIsInstance<Load>()
        assertEquals(1, loads.size, "Second load from field 0 should be eliminated (store was to field 1)")
    }
}

package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class Mem2RegTest {

    private val mem2reg = Mem2Reg()

    private fun buildAndPromote(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return mem2reg.run(ir.build())
    }

    @Test
    fun `promotes simple alloca store load`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `promotes alloca with parameter stored`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(params[0], ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter, "Should return the parameter directly: ${ret.value}")
    }

    @Test
    fun `promotes multiple allocas independently`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(10), a)
            store(Constant.I32(20), b)
            val va = load(Type.I32, a)
            val vb = load(Type.I32, b)
            val sum = add(va, vb)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "add + ret should remain: $insts")
        val addInst = insts[0] as Add
        assertEquals(10, (addInst.lhs as Constant.I32).value)
        assertEquals(20, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `preserves non-promotable alloca with address taken`() {
        val module = buildAndPromote {
            declareFunction("use_ptr", listOf(Param("p", Type.Pointer(Type.I32))), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            call("use_ptr", listOf(ptr), Type.Void)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[1].blocks[0].instructions
        assertTrue(insts.any { it is Alloca }, "Alloca should remain when address escapes: $insts")
        assertTrue(insts.any { it is Store }, "Store should remain: $insts")
        assertTrue(insts.any { it is Load }, "Load should remain: $insts")
    }

    @Test
    fun `promotes with diamond control flow and phi`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("cond", Type.I1)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            condBr(params[0], BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            store(Constant.I32(1), ptr)
            br(BlockRef("merge"))

            appendBlock("else")
            store(Constant.I32(2), ptr)
            br(BlockRef("merge"))

            appendBlock("merge")
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val mergeBlock = module.functions[0].blocks[3]
        val insts = mergeBlock.instructions
        assertTrue(insts.any { it is Phi }, "Should have phi in merge block: $insts")
        assertFalse(insts.any { it is Load }, "Load should be removed: $insts")
        assertFalse(insts.any { it is Alloca }, "Alloca should be removed")

        val phi = insts.first { it is Phi } as Phi
        val values = phi.incoming.map { (v, _) -> (v as Constant.I32).value }.toSet()
        assertEquals(setOf(1, 2), values, "Phi should have values from both branches")
    }

    @Test
    fun `promotes with single block multiple stores`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(1), ptr)
            store(Constant.I32(2), ptr)
            store(Constant.I32(3), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(3, (ret.value as Constant.I32).value, "Should use last stored value")
    }

    @Test
    fun `load before store uses default zero`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(0, (ret.value as Constant.I32).value, "Uninitialized should be zero")
    }

    @Test
    fun `promotes i64 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(Type.I64)
            store(Constant.I64(999L), ptr)
            val v = load(Type.I64, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(999L, (ret.value as Constant.I64).value)
    }

    @Test
    fun `promotes f64 alloca`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val ptr = alloca(Type.F64)
            store(Constant.F64(3.14), ptr)
            val v = load(Type.F64, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Ret
        assertEquals(3.14, (ret.value as Constant.F64).value)
    }

    @Test
    fun `does not promote volatile load`() {
        val module = buildAndPromote {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v = load(Type.I32, ptr, volatile = true)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Alloca }, "Volatile load should prevent promotion")
    }

    @Test
    fun `skips external functions`() {
        val module = buildAndPromote {
            declareFunction("ext", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        assertTrue(module.functions[0].isExternal, "External function should be preserved")
        assertEquals(1, module.functions[1].blocks[0].instructions.size, "Internal function should be promoted")
    }

    @Test
    fun `load used in computation`() {
        val module = buildAndPromote {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(10), ptr)
            val v = load(Type.I32, ptr)
            val sum = add(v, params[0])
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "add + ret should remain: $insts")
        val addInst = insts[0] as Add
        assertEquals(10, (addInst.lhs as Constant.I32).value, "Load should be replaced with constant")
    }
}

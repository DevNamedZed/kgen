package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.instructions.*

class DeadCodeEliminationTest {

    private val dce = DeadCodeElimination()

    private fun buildAndDCE(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return dce.run(ir.build())
    }

    @Test
    fun `removes unused arithmetic`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2)) // dead
            mul(Constant.I32(3), Constant.I32(4)) // dead
            ret(Constant.I32(42))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        assertTrue(insts[0] is Ret)
    }

    @Test
    fun `preserves used values`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], Constant.I32(1))
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(2, insts.size) // add + ret
    }

    @Test
    fun `preserves calls even when result unused`() {
        val module = buildAndDCE {
            declareFunction("side_effect", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            call("side_effect", emptyList(), Type.I32) // result unused, but call has side effects
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[1].blocks[0].instructions
        assertEquals(2, insts.size) // call + ret
        assertTrue(insts[0] is Call)
    }

    @Test
    fun `preserves stores`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            store(Constant.I32(42), params[0])
            ret(null)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertTrue(insts.any { it is Store })
    }

    @Test
    fun `removes chains of dead code`() {
        val module = buildAndDCE {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(2))
            val b = mul(a, Constant.I32(3)) // uses a, but b itself is dead
            val c = sub(b, Constant.I32(4)) // uses b, but c is dead too
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "All dead code should be removed: $insts")
    }

    @Test
    fun `preserves branch instructions`() {
        val module = buildAndDCE {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(cmp, BlockRef("pos"), BlockRef("neg"))

            appendBlock("pos")
            ret(Constant.I32(1))

            appendBlock("neg")
            ret(Constant.I32(-1))

            finalizeFunction()
        }
        val entryInsts = module.functions[0].blocks[0].instructions
        assertTrue(entryInsts.any { it is CondBr })
    }

    @Test
    fun `does not remove external functions`() {
        val module = buildAndDCE {
            declareFunction("external", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        assertEquals(2, module.functions.size)
        assertTrue(module.functions[0].isExternal)
    }
}

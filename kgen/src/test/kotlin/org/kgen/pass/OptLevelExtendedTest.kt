package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class OptLevelExtendedTest {

    private fun buildModule(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    @Test
    fun o0PipelineIsEmpty() {
        val pipeline = OptLevel.O0.pipeline()
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(42))
            finalizeFunction()
        }
        val result = pipeline.execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
    }

    @Test
    fun o1FoldsAddConstants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(3), Constant.I32(7))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(10, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1FoldsSubConstants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = sub(Constant.I32(20), Constant.I32(5))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(15, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1FoldsMulConstants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = mul(Constant.I32(6), Constant.I32(7))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1EliminatesDeadCode() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            add(Constant.I32(1), Constant.I32(2))  // dead
            mul(Constant.I32(3), Constant.I32(4))  // dead
            ret(Constant.I32(99))
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "Should only have ret: $insts")
    }

    @Test
    fun o1CombinesAddZero() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(0))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertTrue(ret.value is Parameter, "add x,0 should simplify to x: ${ret.value}")
    }

    @Test
    fun o1CombinesMulOne() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = mul(params[0], Constant.I32(1))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertTrue(ret.value is Parameter, "mul x,1 should simplify to x: ${ret.value}")
    }

    @Test
    fun o1CombinesMulZero() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = mul(params[0], Constant.I32(0))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0, (ret.value as Constant.I32).value, "mul x,0 should simplify to 0")
    }

    @Test
    fun o2FoldsChainedArithmetic() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(2), Constant.I32(3))   // 5
            val b = mul(a, Constant.I32(4))                  // 20
            val c = add(b, Constant.I32(2))                  // 22
            ret(c)
            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(22, (ret.value as Constant.I32).value)
    }

    @Test
    fun o0PreservesRedundantArithmetic() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = add(params[0], Constant.I32(0))
            val b = mul(a, Constant.I32(1))
            ret(b)
            finalizeFunction()
        }
        val result = OptLevel.O0.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "O0 should not optimize: $insts")
    }

    @Test
    fun o1PreservesCallsAndStores() {
        val module = buildModule {
            declareFunction("side_effect", listOf(Param("x", Type.I32)), Type.Void)
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            call("side_effect", listOf(params[0]), Type.Void)
            ret(params[0])
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val insts = result.functions[1].blocks[0].instructions
        assertTrue(insts.any { it is Call }, "Call should survive O1: $insts")
    }

    @Test
    fun o1FoldsI64Constants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val a = add(Constant.I64(100_000), Constant.I64(200_000))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(300_000L, (ret.value as Constant.I64).value)
    }

    @Test
    fun o1SubSameValueIsZero() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = sub(params[0], params[0])
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertTrue(ret.value is Constant.I32 && (ret.value as Constant.I32).value == 0,
            "x - x should fold to 0: ${ret.value}")
    }

    @Test
    fun o2PreservesFunctionCount() {
        val module = buildModule {
            val p1 = createFunction("f1", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(p1[0])
            finalizeFunction()

            val p2 = createFunction("f2", listOf(Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p2[0], Constant.I32(0)))
            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        assertEquals(2, result.functions.size)
    }

    @Test
    fun o1HandlesEmptyFunction() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        assertEquals(1, result.functions.size)
        assertTrue(result.functions[0].blocks.isNotEmpty())
    }

    @Test
    fun o2FoldsNestedExpressions() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(1), Constant.I32(1))  // 2
            val b = add(Constant.I32(2), Constant.I32(2))  // 4
            val c = mul(a, b)                                // 8
            ret(c)
            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(8, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1AndRemovesDeadBranches() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val dead1 = add(Constant.I32(1), Constant.I32(2))
            val dead2 = sub(Constant.I32(10), Constant.I32(5))
            val dead3 = mul(dead1, dead2)
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "All dead code should be removed: $insts")
    }

    @Test
    fun o1XorSameIsZero() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = xor(params[0], params[0])
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertTrue(ret.value is Constant.I32 && (ret.value as Constant.I32).value == 0,
            "x ^ x should fold to 0: ${ret.value}")
    }

    @Test
    fun pipelineIsIdempotent() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(params[0])
            finalizeFunction()
        }
        val first = OptLevel.O2.pipeline().execute(module)
        val second = OptLevel.O2.pipeline().execute(first)
        assertEquals(first.functions[0].blocks[0].instructions.size,
            second.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun o1FoldsAndConstants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = and(Constant.I32(0xFF), Constant.I32(0x0F))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0x0F, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1FoldsOrConstants() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = or(Constant.I32(0xF0), Constant.I32(0x0F))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(0xFF, (ret.value as Constant.I32).value)
    }

    @Test
    fun o1FoldsShlConstant() {
        val module = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val a = shl(Constant.I32(1), Constant.I32(4))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(16, (ret.value as Constant.I32).value)
    }

    @Test
    fun o2MultipleFunctionsOptimized() {
        val module = buildModule {
            createFunction("f1", emptyList(), Type.I32)
            appendBlock("entry")
            val dead = add(Constant.I32(1), Constant.I32(2))
            ret(Constant.I32(0))
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            appendBlock("entry")
            val a = add(Constant.I32(3), Constant.I32(4))
            ret(a)
            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        // f1: dead code removed
        assertEquals(1, result.functions[0].blocks[0].instructions.size)
        // f2: constant folded
        val ret = result.functions[1].blocks[0].instructions.last() as Ret
        assertEquals(7, (ret.value as Constant.I32).value)
    }
}

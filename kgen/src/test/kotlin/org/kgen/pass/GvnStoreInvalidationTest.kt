package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class GvnStoreInvalidationTest {

    private val gvn = GlobalValueNumbering()

    private fun buildAndGvn(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return gvn.run(ir.build())
    }

    @Nested
    inner class StoreInvalidatesLoad {

        @Test
        fun `store between loads invalidates second load`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("p", Type.Pointer(Type.I32))), Type.I32)
                appendBlock("entry")
                val a = load(Type.I32, params[0])
                store(Constant.I32(99), params[0])
                val b = load(Type.I32, params[0])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val loadCount = insts.count { it is Load }
            assertEquals(2, loadCount, "Store between loads should prevent elimination: $insts")
        }

        @Test
        fun `store to different alloca does not invalidate load`() {
            val module = buildAndGvn {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val a = alloca(Type.I32)
                val b = alloca(Type.I32)
                store(Constant.I32(10), a)
                store(Constant.I32(20), b)
                val v1 = load(Type.I32, a)
                store(Constant.I32(30), b)
                val v2 = load(Type.I32, a)
                val sum = add(v1, v2)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val loadCount = insts.count { it is Load }
            assertEquals(1, loadCount, "Store to unrelated alloca should not invalidate load: $insts")
        }
    }

    @Nested
    inner class CallInvalidatesLoads {

        @Test
        fun `call between loads invalidates second load`() {
            val module = buildAndGvn {
                declareFunction("side_effect", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("p", Type.Pointer(Type.I32))), Type.I32)
                appendBlock("entry")
                val a = load(Type.I32, params[0])
                call("side_effect", emptyList(), Type.Void)
                val b = load(Type.I32, params[0])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[1].blocks[0].instructions
            val loadCount = insts.count { it is Load }
            assertEquals(2, loadCount, "Call should invalidate all cached loads: $insts")
        }
    }

    @Nested
    inner class NoInterveningStore {

        @Test
        fun `consecutive loads from same pointer eliminate second`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("p", Type.Pointer(Type.I32))), Type.I32)
                appendBlock("entry")
                val a = load(Type.I32, params[0])
                val b = load(Type.I32, params[0])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val loadCount = insts.count { it is Load }
            assertEquals(1, loadCount, "Second load should be eliminated: $insts")
        }

        @Test
        fun `loads separated by pure computation still deduplicate`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(
                    Param("p", Type.Pointer(Type.I32)), Param("x", Type.I32)
                ), Type.I32)
                appendBlock("entry")
                val a = load(Type.I32, params[0])
                val temp = add(params[1], Constant.I32(42))
                val b = load(Type.I32, params[0])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val loadCount = insts.count { it is Load }
            assertEquals(1, loadCount, "Pure computation between loads should not prevent elimination: $insts")
        }
    }

    @Nested
    inner class ArithmeticDeduplication {

        @Test
        fun `redundant sub in same block is eliminated`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = sub(params[0], params[1])
                val b = sub(params[0], params[1])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val subCount = insts.count { it is Sub }
            assertEquals(1, subCount, "Redundant sub should be eliminated: $insts")
        }

        @Test
        fun `redundant and in same block is eliminated`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = and(params[0], params[1])
                val b = and(params[0], params[1])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val andCount = insts.count { it is And }
            assertEquals(1, andCount, "Redundant and should be eliminated: $insts")
        }

        @Test
        fun `redundant or is eliminated`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = or(params[0], params[1])
                val b = or(params[0], params[1])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val orCount = insts.count { it is Or }
            assertEquals(1, orCount, "Redundant or should be eliminated: $insts")
        }

        @Test
        fun `redundant xor is eliminated`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = xor(params[0], params[1])
                val b = xor(params[0], params[1])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val xorCount = insts.count { it is Xor }
            assertEquals(1, xorCount, "Redundant xor should be eliminated: $insts")
        }

        @Test
        fun `redundant shl is eliminated`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
                appendBlock("entry")
                val a = shl(params[0], params[1])
                val b = shl(params[0], params[1])
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            val shlCount = insts.count { it is Shl }
            assertEquals(1, shlCount, "Redundant shl should be eliminated: $insts")
        }
    }

    @Nested
    inner class DominatorScoping {

        @Test
        fun `value from entry dominates both branches`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(
                    Param("x", Type.I32), Param("y", Type.I32), Param("c", Type.I1)
                ), Type.I32)
                appendBlock("entry")
                val a = add(params[0], params[1])
                condBr(params[2], BlockRef("left"), BlockRef("right"))

                appendBlock("left")
                val b = add(params[0], params[1])
                ret(b)

                appendBlock("right")
                val c = add(params[0], params[1])
                ret(c)

                finalizeFunction()
            }
            val leftInsts = module.functions[0].blocks[1].instructions
            val rightInsts = module.functions[0].blocks[2].instructions
            assertEquals(1, leftInsts.size, "Redundant add in left should be eliminated: $leftInsts")
            assertEquals(1, rightInsts.size, "Redundant add in right should be eliminated: $rightInsts")
        }

        @Test
        fun `value from one branch does not dominate sibling`() {
            val module = buildAndGvn {
                val params = createFunction("f", listOf(
                    Param("x", Type.I32), Param("y", Type.I32), Param("c", Type.I1)
                ), Type.I32)
                appendBlock("entry")
                condBr(params[2], BlockRef("left"), BlockRef("right"))

                appendBlock("left")
                val a = mul(params[0], params[1])
                ret(a)

                appendBlock("right")
                val b = mul(params[0], params[1])
                ret(b)

                finalizeFunction()
            }
            val leftInsts = module.functions[0].blocks[1].instructions
            val rightInsts = module.functions[0].blocks[2].instructions
            assertEquals(2, leftInsts.size, "Left mul should remain (not dominated): $leftInsts")
            assertEquals(2, rightInsts.size, "Right mul should remain (not dominated): $rightInsts")
        }
    }

    @Nested
    inner class ExternalFunctionsSkipped {

        @Test
        fun `external function passes through unchanged`() {
            val module = buildAndGvn {
                declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            }
            assertTrue(module.functions[0].isExternal)
            assertTrue(module.functions[0].blocks.isEmpty())
        }
    }
}

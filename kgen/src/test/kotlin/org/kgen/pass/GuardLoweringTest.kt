package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*

class GuardLoweringTest {

    private val pass = GuardLowering()
    private val dummyFrameState = Parameter("fs", Type.Void, 99)

    @Nested
    inner class BasicLowering {

        @Test
        fun guardBecomesCondBrAndDeoptBlock() {
            val paramX = Parameter("x", Type.I1, 0)
            val addDest = InstructionRef("sum", Type.I32)
            val paramA = Parameter("a", Type.I32, 1)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "guarded",
                        params = listOf(paramX, paramA),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Guard(paramX, false, DeoptReason.NULL_CHECK, DeoptAction.INVALIDATE_REPROFILE, null, dummyFrameState),
                                Add(addDest, paramA, paramA),
                                Ret(addDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]

            assertTrue(fn.blocks.size >= 3, "Expected at least 3 blocks, got ${fn.blocks.size}")

            val entryBlock = fn.blocks[0]
            assertEquals("entry", entryBlock.label)
            val lastInst = entryBlock.instructions.last()
            assertTrue(lastInst is CondBr, "Entry should end with CondBr, got ${lastInst::class.simpleName}")

            val deoptBlock = fn.blocks.find { block ->
                block.instructions.any { it is Deoptimize }
            }
            assertNotNull(deoptBlock, "Should have a deopt block")

            val deopt = deoptBlock!!.instructions.first { it is Deoptimize } as Deoptimize
            assertEquals(DeoptReason.NULL_CHECK, deopt.reason)
            assertEquals(DeoptAction.INVALIDATE_REPROFILE, deopt.action)

            val continueBlock = fn.blocks.find { block ->
                block.instructions.any { it is Add }
            }
            assertNotNull(continueBlock, "Should have a continue block with the Add")
        }

        @Test
        fun negatedGuardSwapsBranchTargets() {
            val paramX = Parameter("x", Type.I1, 0)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "negated",
                        params = listOf(paramX),
                        returnType = Type.Void,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Guard(paramX, true, DeoptReason.CLASS_CAST, DeoptAction.INVALIDATE_RECOMPILE, null, dummyFrameState),
                                Ret(null),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val condBr = fn.blocks[0].instructions.last() as CondBr

            val deoptLabel = fn.blocks.first { b -> b.instructions.any { it is Deoptimize } }.label
            assertEquals(BlockRef(deoptLabel), condBr.trueTarget)
        }

        @Test
        fun noGuardsReturnsFunctionUnchanged() {
            val paramX = Parameter("x", Type.I32, 0)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "no_guards",
                        params = listOf(paramX),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Ret(paramX),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            assertEquals(1, fn.blocks.size)
        }
    }

    @Nested
    inner class FixedGuardLowering {

        @Test
        fun fixedGuardIsAlsoLowered() {
            val paramX = Parameter("x", Type.I1, 0)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "fixed",
                        params = listOf(paramX),
                        returnType = Type.Void,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                FixedGuard(paramX, false, DeoptReason.BOUNDS_CHECK, DeoptAction.INVALIDATE_STOP_COMPILING, dummyFrameState),
                                Ret(null),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]

            assertTrue(fn.blocks.size >= 3)
            assertTrue(fn.blocks.any { b -> b.instructions.any { it is Deoptimize } })

            val deopt = fn.blocks.flatMap { it.instructions }.filterIsInstance<Deoptimize>().first()
            assertEquals(DeoptReason.BOUNDS_CHECK, deopt.reason)
        }
    }

    @Nested
    inner class MultipleGuards {

        @Test
        fun multipleGuardsInSameBlockCreateMultipleDeoptBlocks() {
            val cond1 = Parameter("c1", Type.I1, 0)
            val cond2 = Parameter("c2", Type.I1, 1)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "multi",
                        params = listOf(cond1, cond2),
                        returnType = Type.Void,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Guard(cond1, false, DeoptReason.NULL_CHECK, DeoptAction.INVALIDATE_REPROFILE, null, dummyFrameState),
                                Guard(cond2, false, DeoptReason.BOUNDS_CHECK, DeoptAction.INVALIDATE_RECOMPILE, null, dummyFrameState),
                                Ret(null),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]

            val deoptBlocks = fn.blocks.filter { b -> b.instructions.any { it is Deoptimize } }
            assertEquals(2, deoptBlocks.size)

            val deoptReasons = deoptBlocks.flatMap { it.instructions }
                .filterIsInstance<Deoptimize>()
                .map { it.reason }
                .toSet()
            assertTrue(DeoptReason.NULL_CHECK in deoptReasons)
            assertTrue(DeoptReason.BOUNDS_CHECK in deoptReasons)
        }
    }
}

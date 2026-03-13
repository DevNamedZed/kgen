package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.ICmpPredicate

class SortBlocksRPOTest {

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        return module.functions.last { !it.isExternal }
    }

    @Nested
    inner class BasicOrdering {

        @Test
        fun singleBlockUnchanged() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals(1, sorted.size)
            assertEquals("entry", sorted[0].label)
        }

        @Test
        fun emptyBlockListUnchanged() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals(fn.blocks.size, sorted.size)
        }

        @Test
        fun linearChainPreservesOrder() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                val entry = appendBlock("entry")
                val mid = appendBlock("mid")
                val exit = appendBlock("exit")
                positionAtEnd(entry)
                br("mid")
                positionAtEnd(mid)
                br("exit")
                positionAtEnd(exit)
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals("entry", sorted[0].label)
            assertEquals("mid", sorted[1].label)
            assertEquals("exit", sorted[2].label)
        }
    }

    @Nested
    inner class DiamondCFG {

        @Test
        fun diamondEntryBeforeBranches() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("cond", Type.I1)), Type.I64)
                val entry = appendBlock("entry")
                val left = appendBlock("left")
                val right = appendBlock("right")
                val merge = appendBlock("merge")
                positionAtEnd(entry)
                condBr(Parameter("cond", Type.I1, 0), "left", "right")
                positionAtEnd(left)
                br("merge")
                positionAtEnd(right)
                br("merge")
                positionAtEnd(merge)
                ret(Constant.I64(0))
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals("entry", sorted[0].label)
            // merge must come after both left and right
            val mergeIdx = sorted.indexOfFirst { it.label == "merge" }
            val leftIdx = sorted.indexOfFirst { it.label == "left" }
            val rightIdx = sorted.indexOfFirst { it.label == "right" }
            assertTrue(mergeIdx > leftIdx)
            assertTrue(mergeIdx > rightIdx)
        }
    }

    @Nested
    inner class LoopCFG {

        @Test
        fun loopHeaderBeforeBody() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                val entry = appendBlock("entry")
                val header = appendBlock("header")
                val body = appendBlock("body")
                val exit = appendBlock("exit")
                positionAtEnd(entry)
                br("header")
                positionAtEnd(header)
                val n = Parameter("n", Type.I64, 0)
                val cmp = icmp(ICmpPredicate.SLT, n, Constant.I64(10))
                condBr(cmp, "body", "exit")
                positionAtEnd(body)
                br("header")
                positionAtEnd(exit)
                ret(Constant.I64(0))
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            val entryIdx = sorted.indexOfFirst { it.label == "entry" }
            val headerIdx = sorted.indexOfFirst { it.label == "header" }
            val bodyIdx = sorted.indexOfFirst { it.label == "body" }
            val exitIdx = sorted.indexOfFirst { it.label == "exit" }
            assertTrue(entryIdx < headerIdx)
            assertTrue(headerIdx < bodyIdx)
            assertTrue(headerIdx < exitIdx)
        }
    }

    @Nested
    inner class UnreachableBlocks {

        @Test
        fun unreachableBlocksAppendedAtEnd() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                val entry = appendBlock("entry")
                val unreachable = appendBlock("dead")
                val normal = appendBlock("normal")
                positionAtEnd(entry)
                br("normal")
                positionAtEnd(unreachable)
                ret()
                positionAtEnd(normal)
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals("entry", sorted[0].label)
            // dead block should be last since it's unreachable
            val deadIdx = sorted.indexOfFirst { it.label == "dead" }
            val normalIdx = sorted.indexOfFirst { it.label == "normal" }
            assertTrue(deadIdx > normalIdx,
                "Unreachable block should come after reachable blocks")
        }

        @Test
        fun allBlocksPreserved() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                val entry = appendBlock("entry")
                val dead = appendBlock("dead")
                positionAtEnd(entry)
                ret()
                positionAtEnd(dead)
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals(fn.blocks.size, sorted.size)
            assertTrue(sorted.any { it.label == "dead" })
        }
    }
}

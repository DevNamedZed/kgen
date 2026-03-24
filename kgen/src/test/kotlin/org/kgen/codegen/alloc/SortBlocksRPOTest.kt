package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.ICmpPredicate

class SortBlocksRPOTest {

    private fun buildFunction(block: ModuleBuilder.() -> Unit): IrFunction {
        val ir = ModuleBuilder("test", Target.x86_64())
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
                appendBlock("entry")
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
                appendBlock("entry")
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
                val entry = createBlock("entry")
                val mid = createBlock("mid")
                val exit = createBlock("exit")
                appendBlock(entry)
                br(BlockRef("mid"))
                appendBlock(mid)
                br(BlockRef("exit"))
                appendBlock(exit)
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
                val entry = createBlock("entry")
                val left = createBlock("left")
                val right = createBlock("right")
                val merge = createBlock("merge")
                appendBlock(entry)
                condBr(Parameter("cond", Type.I1, 0), BlockRef("left"), BlockRef("right"))
                appendBlock(left)
                br(BlockRef("merge"))
                appendBlock(right)
                br(BlockRef("merge"))
                appendBlock(merge)
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
                val entry = createBlock("entry")
                val header = createBlock("header")
                val body = createBlock("body")
                val exit = createBlock("exit")
                appendBlock(entry)
                br(BlockRef("header"))
                appendBlock(header)
                val n = Parameter("n", Type.I64, 0)
                val cmp = icmp(ICmpPredicate.SLT, n, Constant.I64(10))
                condBr(cmp, BlockRef("body"), BlockRef("exit"))
                appendBlock(body)
                br(BlockRef("header"))
                appendBlock(exit)
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
        fun unreachableBlocksExcludedFromRpo() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                val entry = createBlock("entry")
                val unreachable = createBlock("dead")
                val normal = createBlock("normal")
                appendBlock(entry)
                br(BlockRef("normal"))
                appendBlock(unreachable)
                ret()
                appendBlock(normal)
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals("entry", sorted[0].label)
            assertTrue(sorted.any { it.label == "normal" })
            assertFalse(sorted.any { it.label == "dead" },
                "Unreachable block should be excluded from RPO")
        }

        @Test
        fun onlyReachableBlocksReturned() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                val entry = createBlock("entry")
                val dead = createBlock("dead")
                appendBlock(entry)
                ret()
                appendBlock(dead)
                ret()
                finalizeFunction()
            }
            val sorted = LivenessAnalysis.sortBlocksRPO(fn)
            assertEquals(1, sorted.size)
            assertFalse(sorted.any { it.label == "dead" })
        }
    }
}

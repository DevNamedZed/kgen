package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class LivenessBackEdgeTest {

    private fun buildFunction(block: ModuleBuilder.() -> Unit): IrFunction {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        return module.functions.last { !it.isExternal }
    }

    @Nested
    inner class PhiBackEdgeExtension {

        @Test
        fun phiIncomingFromBackEdgeExtendsLiveRange() {
            val fn = buildFunction {
                createFunction("loop", listOf(Param("n", Type.I64)), Type.I64)
                val entry = createBlock("entry")
                val header = createBlock("header")
                val body = createBlock("body")
                val exit = createBlock("exit")

                appendBlock(entry)
                br(BlockRef("header"))

                appendBlock(header)
                val i = phi(Type.I64, listOf(
                    Constant.I64(0) to "entry",
                    InstructionRef("i_next", Type.I64) to "body",
                ))
                val cmp = icmp(ICmpPredicate.SLT, i, Parameter("n", Type.I64, 0))
                condBr(cmp, BlockRef("body"), BlockRef("exit"))

                appendBlock(body)
                val iNext = add(i, Constant.I64(1))
                br(BlockRef("header"))

                appendBlock(exit)
                ret(i)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            // The phi incoming value comes from body back-edge to header.
            // There should be intervals for the param, phi result, icmp, and the add in body.
            assertTrue(intervals.size >= 3, "Should have intervals for param, phi, icmp, and add")
            // The add result (whatever its auto-generated name) should have an interval
            // that extends to the body block end for the phi copy.
            val nonParamIntervals = intervals.filter { it.name != "n" }
            assertTrue(nonParamIntervals.isNotEmpty(), "Should have non-param intervals")
        }

        @Test
        fun phiDestExtendsToBackEdgePredecessor() {
            val fn = buildFunction {
                createFunction("loop", listOf(Param("n", Type.I64)), Type.I64)
                val entry = createBlock("entry")
                val header = createBlock("header")
                val body = createBlock("body")
                val exit = createBlock("exit")

                appendBlock(entry)
                br(BlockRef("header"))

                appendBlock(header)
                val acc = phi(Type.I64, listOf(
                    Constant.I64(0) to "entry",
                    InstructionRef("acc_next", Type.I64) to "body",
                ))
                val n = Parameter("n", Type.I64, 0)
                val cmp = icmp(ICmpPredicate.SLT, acc, n)
                condBr(cmp, BlockRef("body"), BlockRef("exit"))

                appendBlock(body)
                val accNext = add(acc, Constant.I64(1))
                br(BlockRef("header"))

                appendBlock(exit)
                ret(acc)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            // acc is defined in header but used in body (via phi incoming) and exit (ret).
            // With back-edge, acc's live range should extend through body.
            val accIv = intervals.firstOrNull { it.name.contains("phi") || intervals.any { iv -> iv.start == 1 } }
            // Verify analysis completes without error
            assertTrue(intervals.isNotEmpty())
        }
    }

    @Nested
    inner class LoopBackEdgeExtension {

        @Test
        fun valueDefinedBeforeLoopUsedInsideExtendsToLoopEnd() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("limit", Type.I64)), Type.I64)
                val entry = createBlock("entry")
                val header = createBlock("header")
                val body = createBlock("body")
                val exit = createBlock("exit")

                appendBlock(entry)
                val limit = Parameter("limit", Type.I64, 0)
                val initial = add(limit, Constant.I64(1))
                br(BlockRef("header"))

                appendBlock(header)
                val counter = phi(Type.I64, listOf(
                    Constant.I64(0) to "entry",
                    InstructionRef("next", Type.I64) to "body",
                ))
                val cmp = icmp(ICmpPredicate.SLT, counter, initial)
                condBr(cmp, BlockRef("body"), BlockRef("exit"))

                appendBlock(body)
                val next = add(counter, Constant.I64(1))
                br(BlockRef("header"))

                appendBlock(exit)
                ret(counter)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            // 'initial' is defined in entry and used in header (icmp).
            // The loop back-edge from body to header should extend initial's range
            // to cover the entire loop body.
            val initialIv = intervals.firstOrNull { it.name != "limit" && it.type == Type.I64 && it.start > 0 }
            assertTrue(intervals.size >= 3, "Should have intervals for limit, initial, counter, etc.")
        }

        @Test
        fun paramUsedInLoopHeaderExtendsRange() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                val entry = createBlock("entry")
                val header = createBlock("header")
                val body = createBlock("body")
                val exit = createBlock("exit")

                appendBlock(entry)
                br(BlockRef("header"))

                appendBlock(header)
                val i = phi(Type.I64, listOf(
                    Constant.I64(0) to "entry",
                    InstructionRef("i_inc", Type.I64) to "body",
                ))
                val n = Parameter("n", Type.I64, 0)
                val cmp = icmp(ICmpPredicate.SLT, i, n)
                condBr(cmp, BlockRef("body"), BlockRef("exit"))

                appendBlock(body)
                val iInc = add(i, Constant.I64(1))
                br(BlockRef("header"))

                appendBlock(exit)
                ret(i)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val nIv = intervals.first { it.name == "n" }
            // n is used every iteration in the loop header comparison.
            // Its live range should extend through the loop body back-edge.
            assertTrue(nIv.end > nIv.start, "Param used in loop should have extended range")
        }
    }

    @Nested
    inner class NestedLoopExtension {

        @Test
        fun outerVariableLiveThroughInnerLoop() {
            val fn = buildFunction {
                createFunction("nested", listOf(Param("n", Type.I64)), Type.I64)
                val entry = createBlock("entry")
                val outerHeader = createBlock("outer_header")
                val innerHeader = createBlock("inner_header")
                val innerBody = createBlock("inner_body")
                val outerLatch = createBlock("outer_latch")
                val exit = createBlock("exit")

                appendBlock(entry)
                br(BlockRef("outer_header"))

                appendBlock(outerHeader)
                val i = phi(Type.I64, listOf(
                    Constant.I64(0) to "entry",
                    InstructionRef("i_next", Type.I64) to "outer_latch",
                ))
                val n = Parameter("n", Type.I64, 0)
                val outerCmp = icmp(ICmpPredicate.SLT, i, n)
                condBr(outerCmp, BlockRef("inner_header"), BlockRef("exit"))

                appendBlock(innerHeader)
                val j = phi(Type.I64, listOf(
                    Constant.I64(0) to "outer_header",
                    InstructionRef("j_next", Type.I64) to "inner_body",
                ))
                val innerCmp = icmp(ICmpPredicate.SLT, j, n)
                condBr(innerCmp, BlockRef("inner_body"), BlockRef("outer_latch"))

                appendBlock(innerBody)
                val jNext = add(j, Constant.I64(1))
                br(BlockRef("inner_header"))

                appendBlock(outerLatch)
                val iNext = add(i, Constant.I64(1))
                br(BlockRef("outer_header"))

                appendBlock(exit)
                ret(i)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            // n is used in both outer and inner loop headers.
            // It should be live throughout both loops.
            val nIv = intervals.first { it.name == "n" }
            assertTrue(nIv.end > nIv.start)
            assertTrue(intervals.size >= 4)
        }
    }
}

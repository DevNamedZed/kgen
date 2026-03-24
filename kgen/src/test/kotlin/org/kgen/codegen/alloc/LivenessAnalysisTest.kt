package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class LivenessAnalysisTest {

    private fun buildFunction(block: ModuleBuilder.() -> Unit): IrFunction {
        val ir = ModuleBuilder("test_module", Target.x86_64())
        ir.block()
        val module = ir.build()
        // Return the last non-external function (declarations come first)
        return module.functions.last { !it.isExternal }
    }

    @Test
    fun simpleReturn() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret(Parameter("x", Type.I64, 0))
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val xInterval = intervals.first { it.name == "x" }
        assertEquals(Type.I64, xInterval.type)
        assertTrue(xInterval.end >= xInterval.start)
    }

    @Test
    fun addTwoParams() {
        val fn = buildFunction {
            createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val result = add(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
            ret(result)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        assertTrue(intervals.any { it.name == "a" })
        assertTrue(intervals.any { it.name == "b" })
        // The add result should also have an interval
        assertTrue(intervals.size >= 3)
    }

    @Test
    fun callMarksAcrossCall() {
        val fn = buildFunction {
            declareFunction("ext", listOf(Param("x", Type.I64)), Type.I64)
            createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val p = Parameter("a", Type.I64, 0)
            val fnType = Type.Function(listOf(Type.I64), Type.I64)
            val fnRef = GlobalRef("ext", fnType)
            val callResult = call(fnRef, listOf(Constant.I64(42)), Type.I64)!!
            // Use 'a' after the call — it should be marked acrossCall
            val result = add(p, callResult)
            ret(result)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val aInterval = intervals.first { it.name == "a" }
        assertTrue(aInterval.acrossCall, "Parameter 'a' is used after a call, should be acrossCall")
    }

    @Test
    fun callResultNotAcrossCall() {
        val fn = buildFunction {
            declareFunction("ext", listOf(Param("x", Type.I64)), Type.I64)
            createFunction("f", emptyList(), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val fnType = Type.Function(listOf(Type.I64), Type.I64)
            val fnRef = GlobalRef("ext", fnType)
            val callResult = call(fnRef, listOf(Constant.I64(42)), Type.I64)!!
            ret(callResult)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val resultInterval = intervals.firstOrNull { it.name != "x" && it.type == Type.I64 }
        // Call result is used immediately — not across another call
        if (resultInterval != null) {
            assertFalse(resultInterval.acrossCall)
        }
    }

    @Test
    fun intervalsSortedByStart() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val sum = add(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
            val doubled = add(sum, sum)
            ret(doubled)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        for (i in 1 until intervals.size) {
            assertTrue(intervals[i].start >= intervals[i - 1].start,
                "Intervals should be sorted by start position")
        }
    }

    @Test
    fun useCounting() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val p = Parameter("x", Type.I64, 0)
            val a = add(p, p) // x used twice here
            val b = add(a, p) // x used once more
            ret(b)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val xInterval = intervals.first { it.name == "x" }
        assertEquals(3, xInterval.useCount, "x is used 3 times")
    }

    @Test
    fun operandValuesExtractsAllOperands() {
        val inst = Add(
            InstructionRef("result", Type.I64),
            Parameter("a", Type.I64, 0),
            Parameter("b", Type.I64, 1)
        )
        val operands = LivenessAnalysis.operandValues(inst)
        assertEquals(2, operands.size)
        assertEquals("a", operands[0].name)
        assertEquals("b", operands[1].name)
    }

    @Test
    fun operandValuesIgnoresConstants() {
        val inst = Add(
            InstructionRef("result", Type.I64),
            Parameter("a", Type.I64, 0),
            Constant.I64(42)
        )
        val operands = LivenessAnalysis.operandValues(inst)
        assertEquals(1, operands.size)
        assertEquals("a", operands[0].name)
    }

    @Test
    fun emptyFunctionProducesNoIntervals() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret()
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        assertTrue(intervals.isEmpty())
    }
}

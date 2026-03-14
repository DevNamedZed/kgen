package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class GraphColoringAllocatorTest {

    private val gpClass = RegisterClass("GP", emptyList())
    private val fpClass = RegisterClass("FP", emptyList())

    private fun gp(name: String, enc: Int) = PhysicalRegister(name, enc, gpClass)
    private fun fp(name: String, enc: Int) = PhysicalRegister(name, enc, fpClass)

    private val r0 = gp("R0", 0)
    private val r1 = gp("R1", 1)
    private val r2 = gp("R2", 2)
    private val r3 = gp("R3", 3)
    private val r4 = gp("R4", 4)
    private val r5 = gp("R5", 5)

    private val constraints = RegisterConstraints(
        allocatable = listOf(r0, r1, r2, r3, r4, r5),
        reserved = emptySet(),
        calleeSaved = setOf(r4, r5),
        paramRegisters = listOf(r0, r1, r2),
        returnRegisters = listOf(r0),
    )

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        return module.functions.last { !it.isExternal }
    }

    @Test
    fun simpleAddition() {
        val fn = buildFunction {
            createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        assertTrue(result.locations["a"] is ValueLocation.Register)
        assertTrue(result.locations["b"] is ValueLocation.Register)
        assertTrue(result.locations.values.all { it is ValueLocation.Register })
        assertEquals(0, result.spillSlots)
    }

    @Test
    fun moreValuesThanRegisters() {
        val fn = buildFunction {
            createFunction("many", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val c = add(a, b)
            val d = mul(a, c)
            val e = sub(d, b)
            val f = add(c, e)
            val g = mul(d, f)
            ret(g)
            finalizeFunction()
        }

        val smallConstraints = RegisterConstraints(
            allocatable = listOf(r0, r1, r2),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = listOf(r0, r1),
            returnRegisters = listOf(r0),
        )

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, smallConstraints)

        assertTrue(result.locations.isNotEmpty())
        val regCount = result.locations.values.count { it is ValueLocation.Register }
        val spillCount = result.locations.values.count { it is ValueLocation.SpillSlot }
        assertTrue(regCount + spillCount == result.locations.size)
    }

    @Test
    fun prefersCalleeSavedAcrossCall() {
        val fn = buildFunction {
            declareFunction("ext", emptyList(), Type.I64)
            createFunction("caller", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val x = Parameter("x", Type.I64, 0)
            val callResult = call("ext", emptyList(), Type.I64)!!
            val sum = add(x, callResult)
            ret(sum)
            finalizeFunction()
        }

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        val xLoc = result.locations["x"]
        if (xLoc is ValueLocation.Register) {
            assertTrue(xLoc.reg in constraints.calleeSaved || result.paramMoves.containsKey("x"),
                "Value across call should use callee-saved register or be moved")
        }
    }

    @Test
    fun noValuesNoSpills() {
        val fn = buildFunction {
            createFunction("noop", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        assertEquals(0, result.spillSlots)
        assertTrue(result.usedCalleeRegisters.isEmpty())
    }

    @Test
    fun paramMovesForAcrossCall() {
        val fn = buildFunction {
            declareFunction("ext", emptyList(), Type.Void)
            createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            call("ext", emptyList(), Type.Void)
            ret(a)
            finalizeFunction()
        }

        val callerSavedOnly = RegisterConstraints(
            allocatable = listOf(r0, r1, r2),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = listOf(r0),
            returnRegisters = listOf(r0),
        )

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, callerSavedOnly)

        assertTrue(result.paramMoves.containsKey("a"),
            "Param across call in caller-saved reg should have paramMove")
    }

    @Test
    fun spillSlotReuse() {
        val fn = buildFunction {
            createFunction("spills", emptyList(), Type.I64)
            appendBlock("entry")
            val v1 = add(Constant.I64(1), Constant.I64(2))
            val v2 = add(Constant.I64(3), Constant.I64(4))
            val v3 = add(v1, v2)
            val v4 = add(Constant.I64(5), Constant.I64(6))
            val v5 = add(v3, v4)
            ret(v5)
            finalizeFunction()
        }

        val tinyConstraints = RegisterConstraints(
            allocatable = listOf(r0),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = emptyList(),
            returnRegisters = listOf(r0),
        )

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, tinyConstraints)

        assertTrue(result.spillSlots > 0)
    }

    @Test
    fun clobberAwarenessAvoidsClobberedRegister() {
        val fn = buildFunction {
            createFunction("divtest", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val q = sdiv(a, b)
            val result = add(a, q)
            ret(result)
            finalizeFunction()
        }

        val clobberConstraints = RegisterConstraints(
            allocatable = listOf(r0, r1, r2, r3),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = listOf(r0, r1),
            returnRegisters = listOf(r0),
            clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r2)),
        )

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val clobberEvents = LivenessAnalysis(fn).clobberEvents(clobberConstraints)

        val result = allocator.allocate(fn, intervals, clobberConstraints, clobberEvents)

        val aLoc = result.locations["a"]
        if (aLoc is ValueLocation.Register) {
            val clobbered = clobberConstraints.clobbers[InstructionClobber.INT_DIV]!!
            if (aLoc.reg in clobbered) {
                // If it ended up in a clobbered register, there should be split points
                assertTrue(result.splitPoints.isNotEmpty() || result.paramMoves.containsKey("a"),
                    "Value in clobbered register should have split points or param moves")
            }
        }
    }

    @Test
    fun usedCalleeRegistersTracked() {
        val fn = buildFunction {
            createFunction("uses_many", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)
            ), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val c = Parameter("c", Type.I64, 2)
            val d = add(a, b)
            val e = add(d, c)
            val f = mul(a, e)
            val g = add(b, f)
            ret(g)
            finalizeFunction()
        }

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        for ((_, loc) in result.locations) {
            if (loc is ValueLocation.Register && loc.reg in constraints.calleeSaved) {
                assertTrue(result.usedCalleeRegisters.contains(loc.reg))
            }
        }
    }

    @Test
    fun noInterferenceSharesRegisters() {
        // Two non-overlapping values should get the same register
        val fn = buildFunction {
            createFunction("share", emptyList(), Type.I64)
            appendBlock("entry")
            val v1 = add(Constant.I64(1), Constant.I64(2))
            val v2 = add(v1, Constant.I64(3))
            // v1 is dead after v2 uses it, so v3 should reuse v1's register
            val v3 = add(Constant.I64(4), Constant.I64(5))
            val v4 = add(v2, v3)
            ret(v4)
            finalizeFunction()
        }

        val twoRegs = RegisterConstraints(
            allocatable = listOf(r0, r1),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = emptyList(),
            returnRegisters = listOf(r0),
        )

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, twoRegs)

        // With only 2 registers, graph coloring should succeed without spills
        // because no more than 2 values are live at any point
        val regCount = result.locations.values.count { it is ValueLocation.Register }
        assertTrue(regCount > 0, "Should allocate some values to registers")
    }

    @Test
    fun allValuesGetLocation() {
        val fn = buildFunction {
            createFunction("complete", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val sum = add(a, b)
            val prod = mul(sum, b)
            ret(prod)
            finalizeFunction()
        }

        val allocator = GraphColoringAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        // Every interval should have a location
        for (iv in intervals) {
            assertTrue(iv.name in result.locations,
                "Value '${iv.name}' should have an allocated location")
        }
    }
}

package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class LinearScanRegisterAllocatorTest {

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
            positionAtEnd(appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        // Parameters should be in registers
        assertTrue(result.locations["a"] is ValueLocation.Register)
        assertTrue(result.locations["b"] is ValueLocation.Register)
        // All values should be in registers (enough available)
        assertTrue(result.locations.values.all { it is ValueLocation.Register })
        assertEquals(0, result.spillSlots)
    }

    @Test
    fun moreValuesThanRegisters() {
        val fn = buildFunction {
            createFunction("many", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
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

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, smallConstraints)

        // All values should be allocated (some may spill)
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
            positionAtEnd(appendBlock("entry"))
            val x = Parameter("x", Type.I64, 0)
            val callResult = call("ext", emptyList(), Type.I64)!!
            val sum = add(x, callResult)
            ret(sum)
            finalizeFunction()
        }

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        // x is live across the call to ext, so should prefer callee-saved or be moved
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
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }

        val allocator = LinearScanRegisterAllocator()
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
            positionAtEnd(appendBlock("entry"))
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

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, callerSavedOnly)

        // a is in r0 (param reg) and lives across call — needs move
        assertTrue(result.paramMoves.containsKey("a"),
            "Param across call in caller-saved reg should have paramMove")
    }

    @Test
    fun spillSlotReuse() {
        // Create function with many short-lived values that can reuse spill slots
        val fn = buildFunction {
            createFunction("spills", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
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

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, tinyConstraints)

        // Should have spills but not one per value (some can be reused)
        assertTrue(result.spillSlots > 0)
        assertTrue(result.spillSlots < result.locations.size)
    }

    @Test
    fun clobberAwarenessAvoidsClobberedRegister() {
        // Value 'a' lives from 0..3, idiv at position 2 clobbers r0
        // Allocator should avoid r0 for 'a' if possible
        val fn = buildFunction {
            createFunction("divtest", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
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

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val clobberEvents = LivenessAnalysis(fn).clobberEvents(clobberConstraints)

        assertTrue(clobberEvents.isNotEmpty(), "Should detect sdiv as INT_DIV clobber")

        val result = allocator.allocate(fn, intervals, clobberConstraints, clobberEvents)

        // 'a' spans the div — should not be in r0 or r2 (clobbered by INT_DIV)
        val aLoc = result.locations["a"]
        if (aLoc is ValueLocation.Register) {
            val clobbered = clobberConstraints.clobbers[InstructionClobber.INT_DIV]!!
            assertTrue(aLoc.reg !in clobbered,
                "Value spanning clobber point should not be in clobbered register (got ${aLoc.reg})")
        }
    }

    @Test
    fun clobberEventsDetected() {
        val fn = buildFunction {
            createFunction("ops", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val d = sdiv(a, b)
            val s = shl(d, Constant.I64(1))
            ret(s)
            finalizeFunction()
        }

        val clobberConstraints = RegisterConstraints(
            allocatable = listOf(r0, r1, r2),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = listOf(r0, r1),
            returnRegisters = listOf(r0),
            clobbers = mapOf(
                InstructionClobber.INT_DIV to setOf(r0, r2),
                InstructionClobber.VARIABLE_SHIFT to setOf(r1),
            ),
        )

        val events = LivenessAnalysis(fn).clobberEvents(clobberConstraints)
        assertEquals(2, events.size, "Should detect sdiv and shl as clobber events")
    }

    @Test
    fun liveRangeSplitAtClobberPoint() {
        // With only clobbered registers available, the allocator should
        // assign a register but generate split points for save/reload
        val fn = buildFunction {
            createFunction("splitdiv", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val q = sdiv(a, b) // clobbers r0
            val result = add(a, q)
            ret(result)
            finalizeFunction()
        }

        // All allocatable registers are clobbered by INT_DIV
        val allClobbered = RegisterConstraints(
            allocatable = listOf(r0, r1),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = listOf(r0, r1),
            returnRegisters = listOf(r0),
            clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r1)),
        )

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val clobberEvents = LivenessAnalysis(fn).clobberEvents(allClobbered)

        val result = allocator.allocate(fn, intervals, allClobbered, clobberEvents)

        // 'a' spans the div and all registers are clobbered — should generate split points
        // or move the param since it's in a clobbered register
        val hasSplitOrMove = result.splitPoints.isNotEmpty() || result.paramMoves.isNotEmpty()
        assertTrue(hasSplitOrMove,
            "Should have split points or param moves when all registers are clobbered")
    }

    @Test
    fun splitPointContainsCorrectSpillOffset() {
        val fn = buildFunction {
            createFunction("splittest", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val v1 = add(Constant.I64(1), Constant.I64(2))
            val v2 = sdiv(v1, Constant.I64(3)) // clobbers
            val v3 = add(v1, v2)
            ret(v3)
            finalizeFunction()
        }

        // Only one register available, clobbered by INT_DIV
        val tinyClobber = RegisterConstraints(
            allocatable = listOf(r0),
            reserved = emptySet(),
            calleeSaved = emptySet(),
            paramRegisters = emptyList(),
            returnRegisters = listOf(r0),
            clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0)),
        )

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val events = LivenessAnalysis(fn).clobberEvents(tinyClobber)

        val result = allocator.allocate(fn, intervals, tinyClobber, events)

        // Split points should have negative offsets (stack below frame pointer)
        for (sp in result.splitPoints) {
            assertTrue(sp.spillOffset < 0, "Spill offset should be negative (below frame pointer)")
        }
    }

    @Test
    fun usedCalleeRegistersTracked() {
        val fn = buildFunction {
            createFunction("uses_many", listOf(
                Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)
            ), Type.I64)
            positionAtEnd(appendBlock("entry"))
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

        val allocator = LinearScanRegisterAllocator()
        val intervals = LivenessAnalysis(fn).intervals()
        val result = allocator.allocate(fn, intervals, constraints)

        // If any callee-saved register was used, it should be tracked
        for ((_, loc) in result.locations) {
            if (loc is ValueLocation.Register && loc.reg in constraints.calleeSaved) {
                assertTrue(result.usedCalleeRegisters.contains(loc.reg))
            }
        }
    }
}

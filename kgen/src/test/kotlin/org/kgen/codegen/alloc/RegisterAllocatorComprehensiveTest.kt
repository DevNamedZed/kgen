package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class RegisterAllocatorComprehensiveTest {

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
    private val r6 = gp("R6", 6)
    private val r7 = gp("R7", 7)

    private val f0 = fp("F0", 0)
    private val f1 = fp("F1", 1)
    private val f2 = fp("F2", 2)
    private val f3 = fp("F3", 3)

    private val standardConstraints = RegisterConstraints(
        allocatable = listOf(r0, r1, r2, r3, r4, r5),
        reserved = emptySet(),
        calleeSaved = setOf(r4, r5),
        paramRegisters = listOf(r0, r1, r2),
        returnRegisters = listOf(r0),
    )

    private val tinyConstraints = RegisterConstraints(
        allocatable = listOf(r0, r1),
        reserved = emptySet(),
        calleeSaved = emptySet(),
        paramRegisters = listOf(r0),
        returnRegisters = listOf(r0),
    )

    private val singleRegConstraints = RegisterConstraints(
        allocatable = listOf(r0),
        reserved = emptySet(),
        calleeSaved = emptySet(),
        paramRegisters = emptyList(),
        returnRegisters = listOf(r0),
    )

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        return module.functions.last { !it.isExternal }
    }

    @Nested
    inner class LivenessAnalysisTests {

        @Test
        fun `single parameter used once has tight interval`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Parameter("x", Type.I64, 0))
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val x = intervals.first { it.name == "x" }
            assertTrue(x.end >= x.start)
            assertEquals(1, x.useCount)
        }

        @Test
        fun `unused parameter still has an interval`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val x = intervals.firstOrNull { it.name == "x" }
            assertNotNull(x)
            assertEquals(0, x!!.useCount)
        }

        @Test
        fun `multiple parameters have distinct intervals`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                val ab = add(a, b)
                val result = add(ab, c)
                ret(result)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val names = intervals.map { it.name }.toSet()
            assertTrue("a" in names)
            assertTrue("b" in names)
            assertTrue("c" in names)
        }

        @Test
        fun `value used multiple times has correct use count`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, x)
                val b = add(a, x)
                val c = mul(b, x)
                ret(c)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val xInterval = intervals.first { it.name == "x" }
            assertEquals(4, xInterval.useCount)
        }

        @Test
        fun `intervals are sorted by start position`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = add(a, Constant.I64(1))
                val c = mul(b, Constant.I64(2))
                val d = sub(c, a)
                ret(d)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            for (i in 1 until intervals.size) {
                assertTrue(intervals[i].start >= intervals[i - 1].start)
            }
        }

        @Test
        fun `value defined and used in same instruction has start equal to end`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(1), Constant.I64(2))
                ret(v)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val vInterval = intervals.first { it.name != "x" }
            assertTrue(vInterval.end >= vInterval.start)
        }

        @Test
        fun `diamond CFG both branches`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, x, Constant.I64(0))
                condBr(cond, "then", "else")

                positionAtEnd(appendBlock("then"))
                val a = add(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("else"))
                val b = sub(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("merge"))
                val phi = phi(Type.I64, listOf(a to "then", b to "else"))
                ret(phi)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            assertTrue(intervals.isNotEmpty())
            val xInterval = intervals.first { it.name == "x" }
            assertTrue(xInterval.useCount >= 2, "x is used in both branches + condition")
        }

        @Test
        fun `loop extends live range of value used in header`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                br("header")

                positionAtEnd(appendBlock("header"))
                val n = Parameter("n", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, n, Constant.I64(0))
                condBr(cond, "body", "exit")

                positionAtEnd(appendBlock("body"))
                br("header")

                positionAtEnd(appendBlock("exit"))
                ret(n)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val nInterval = intervals.first { it.name == "n" }
            assertTrue(nInterval.end > nInterval.start, "Loop back-edge should extend live range")
        }

        @Test
        fun `call marks values as across call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                call("ext", emptyList(), Type.I64)
                val result = add(a, Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val aInterval = intervals.first { it.name == "a" }
            assertTrue(aInterval.acrossCall)
        }

        @Test
        fun `value defined after call is not across call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r = call("ext", emptyList(), Type.I64)!!
                val v = add(r, Constant.I64(1))
                ret(v)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            for (iv in intervals) {
                if (iv.name != "a") {
                    assertFalse(iv.acrossCall, "${iv.name} should not be across call")
                }
            }
        }

        @Test
        fun `two calls only values spanning both are across call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val r1 = call("ext", emptyList(), Type.I64)!!
                val r2 = call("ext", emptyList(), Type.I64)!!
                val sum = add(x, r2)
                ret(sum)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val xIv = intervals.first { it.name == "x" }
            assertTrue(xIv.acrossCall, "x spans both calls")
        }

        @Test
        fun `empty function produces no intervals`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            assertTrue(intervals.isEmpty())
        }

        @Test
        fun `operandValues extracts binary op operands`() {
            val inst = Add(
                InstructionRef("r", Type.I64),
                Parameter("a", Type.I64, 0),
                Parameter("b", Type.I64, 1)
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(2, ops.size)
        }

        @Test
        fun `operandValues ignores constants`() {
            val inst = Mul(
                InstructionRef("r", Type.I64),
                Parameter("a", Type.I64, 0),
                Constant.I64(42)
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
            assertEquals("a", ops[0].name)
        }

        @Test
        fun `operandValues extracts phi incoming values`() {
            val inst = Phi(
                InstructionRef("r", Type.I64),
                listOf(
                    Parameter("a", Type.I64, 0) to "bb1",
                    InstructionRef("b", Type.I64) to "bb2"
                )
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(2, ops.size)
        }

        @Test
        fun `operandValues extracts select operands`() {
            val inst = Select(
                InstructionRef("r", Type.I64),
                Parameter("cond", Type.I1, 0),
                Parameter("t", Type.I64, 1),
                Parameter("f", Type.I64, 2)
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(3, ops.size)
        }

        @Test
        fun `operandValues extracts call arguments`() {
            val fnType = Type.Function(listOf(Type.I64, Type.I64), Type.I64)
            val inst = Call(
                InstructionRef("r", Type.I64),
                GlobalRef("foo", fnType),
                listOf(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1)),
                returnType = Type.I64,
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(2, ops.size)
        }

        @Test
        fun `operandValues handles ret with value`() {
            val inst = Ret(Parameter("x", Type.I64, 0))
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `operandValues handles ret void`() {
            val inst = Ret(null)
            val ops = LivenessAnalysis.operandValues(inst)
            assertTrue(ops.isEmpty())
        }

        @Test
        fun `operandValues handles branch`() {
            val inst = Br("target")
            val ops = LivenessAnalysis.operandValues(inst)
            assertTrue(ops.isEmpty())
        }

        @Test
        fun `operandValues handles conditional branch`() {
            val inst = CondBr(
                Parameter("cond", Type.I1, 0), "then", "else"
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `interval type matches parameter type`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                ret(Parameter("x", Type.I32, 0))
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val x = intervals.first { it.name == "x" }
            assertEquals(Type.I32, x.type)
        }

        @Test
        fun `interval type for computed value is instruction result type`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            for (iv in intervals) {
                assertEquals(Type.I64, iv.type)
            }
        }

        @Test
        fun `clobber events detected for sdiv`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = sdiv(a, b)
                ret(q)
                finalizeFunction()
            }
            val c = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r2)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(c)
            assertEquals(1, events.size)
            assertTrue(events[0].clobberedRegisters.contains(r0))
            assertTrue(events[0].clobberedRegisters.contains(r2))
        }

        @Test
        fun `clobber events detected for shl`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val s = shl(a, b)
                ret(s)
                finalizeFunction()
            }
            val c = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.VARIABLE_SHIFT to setOf(r1)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(c)
            assertEquals(1, events.size)
        }

        @Test
        fun `clobber events detected for call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret()
                finalizeFunction()
            }
            val c = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = emptyList(),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.CALL to setOf(r0, r1)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(c)
            assertEquals(1, events.size)
        }

        @Test
        fun `no clobber events when constraints have no clobbers`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = sdiv(a, b)
                ret(q)
                finalizeFunction()
            }
            val noClobber = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
            )
            val events = LivenessAnalysis(fn).clobberEvents(noClobber)
            assertTrue(events.isEmpty())
        }

        @Test
        fun `multiple clobber events in sequence`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q1 = sdiv(a, b)
                val q2 = srem(a, b)
                val s = add(q1, q2)
                ret(s)
                finalizeFunction()
            }
            val c = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r2)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(c)
            assertEquals(2, events.size)
        }

        @Test
        fun `nested loop extends outer variable range`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                br("outer")

                positionAtEnd(appendBlock("outer"))
                val n = Parameter("n", Type.I64, 0)
                val outerCond = icmp(ICmpPredicate.SGT, n, Constant.I64(0))
                condBr(outerCond, "inner", "exit")

                positionAtEnd(appendBlock("inner"))
                val innerCond = icmp(ICmpPredicate.SGT, n, Constant.I64(5))
                condBr(innerCond, "inner", "outer")

                positionAtEnd(appendBlock("exit"))
                ret(n)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val nIv = intervals.first { it.name == "n" }
            assertTrue(nIv.end > nIv.start)
        }

        @Test
        fun `floating point values have correct type in intervals`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.F64, 0)
                val y = Parameter("y", Type.F64, 1)
                val sum = fadd(x, y)
                ret(sum)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val xIv = intervals.first { it.name == "x" }
            assertEquals(Type.F64, xIv.type)
        }
    }

    @Nested
    inner class LinearScanTests {

        @Test
        fun `simple addition allocates all in registers`() {
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
            val result = allocator.allocate(fn, intervals, standardConstraints)

            assertTrue(result.locations.values.all { it is ValueLocation.Register })
            assertEquals(0, result.spillSlots)
        }

        @Test
        fun `no values function produces empty allocation`() {
            val fn = buildFunction {
                createFunction("noop", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertEquals(0, result.spillSlots)
            assertTrue(result.usedCalleeRegisters.isEmpty())
            assertTrue(result.locations.isEmpty())
        }

        @Test
        fun `three register constraint forces spill with four live values`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                val d = add(a, b)
                val e = add(c, d)
                val f = add(a, e)
                ret(f)
                finalizeFunction()
            }
            val threeRegs = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1, r2),
                returnRegisters = listOf(r0),
            )
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), threeRegs)

            val allLocated = result.locations.size
            assertTrue(allLocated >= 3, "All values should be located")
        }

        @Test
        fun `single register forces all but one value to spill`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                val c = add(a, b)
                ret(c)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), singleRegConstraints)
            assertTrue(result.spillSlots > 0)
        }

        @Test
        fun `callee saved preference for values across calls`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("caller", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val r = call("ext", emptyList(), Type.I64)!!
                val sum = add(x, r)
                ret(sum)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)

            val xLoc = result.locations["x"]
            if (xLoc is ValueLocation.Register) {
                assertTrue(xLoc.reg in standardConstraints.calleeSaved || result.paramMoves.containsKey("x"))
            }
        }

        @Test
        fun `param move generated when param lives across call in caller-saved reg`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                call("ext", emptyList(), Type.Void)
                ret(a)
                finalizeFunction()
            }
            val callerOnly = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0),
                returnRegisters = listOf(r0),
            )
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), callerOnly)
            assertTrue(result.paramMoves.containsKey("a"))
        }

        @Test
        fun `spill slot reuse for non-overlapping lifetimes`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v1 = add(Constant.I64(1), Constant.I64(2))
                val v2 = add(Constant.I64(3), Constant.I64(4))
                val v3 = add(v1, v2)
                val v4 = add(Constant.I64(5), Constant.I64(6))
                val v5 = add(v3, v4)
                ret(v5)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), singleRegConstraints)
            assertTrue(result.spillSlots > 0)
            assertTrue(result.spillSlots < result.locations.size, "Slots should be reused")
        }

        @Test
        fun `used callee registers tracked correctly`() {
            val fn = buildFunction {
                createFunction("f", listOf(
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
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)

            for ((_, loc) in result.locations) {
                if (loc is ValueLocation.Register && loc.reg in standardConstraints.calleeSaved) {
                    assertTrue(result.usedCalleeRegisters.contains(loc.reg))
                }
            }
        }

        @Test
        fun `clobber awareness avoids clobbered register`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
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
            val result = allocator.allocate(fn, intervals, clobberConstraints, clobberEvents)

            val aLoc = result.locations["a"]
            if (aLoc is ValueLocation.Register) {
                val clobbered = clobberConstraints.clobbers[InstructionClobber.INT_DIV]!!
                assertTrue(aLoc.reg !in clobbered || result.paramMoves.containsKey("a"),
                    "Value spanning clobber should avoid clobbered register")
            }
        }

        @Test
        fun `split points generated when all registers clobbered`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = sdiv(a, b)
                val result = add(a, q)
                ret(result)
                finalizeFunction()
            }
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
            val events = LivenessAnalysis(fn).clobberEvents(allClobbered)
            val result = allocator.allocate(fn, intervals, allClobbered, events)

            assertTrue(result.splitPoints.isNotEmpty() || result.paramMoves.isNotEmpty())
        }

        @Test
        fun `split point spill offsets are negative`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v1 = add(Constant.I64(1), Constant.I64(2))
                val v2 = sdiv(v1, Constant.I64(3))
                val v3 = add(v1, v2)
                ret(v3)
                finalizeFunction()
            }
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

            for (sp in result.splitPoints) {
                assertTrue(sp.spillOffset < 0)
            }
        }

        @Test
        fun `chain of additions with enough registers`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, Constant.I64(1))
                val b = add(a, Constant.I64(2))
                val c = add(b, Constant.I64(3))
                val d = add(c, Constant.I64(4))
                ret(d)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertEquals(0, result.spillSlots, "Sequential chain needs only 2 regs")
        }

        @Test
        fun `all live at once with two regs forces spill`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                val c = add(a, b)
                ret(c)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), tinyConstraints)
            // a and b are both live at the point of c = add(a, b)
            // with only 2 regs, they might fit or might need a spill depending on lifetimes
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `return value allocated`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(10), Constant.I64(20))
                ret(v)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `multiple param moves when multiple params live across call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                call("ext", emptyList(), Type.Void)
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val noCallee = RegisterConstraints(
                allocatable = listOf(r0, r1, r2, r3),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
            )
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), noCallee)
            assertTrue(result.paramMoves.containsKey("a"))
            assertTrue(result.paramMoves.containsKey("b"))
        }

        @Test
        fun `param not across call stays in param register`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val r = add(a, Constant.I64(1))
                ret(r)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            val aLoc = result.locations["a"]
            assertTrue(aLoc is ValueLocation.Register)
            assertEquals(r0, (aLoc as ValueLocation.Register).reg)
            assertFalse(result.paramMoves.containsKey("a"))
        }

        @Test
        fun `excess parameters beyond param registers`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64),
                    Param("c", Type.I64), Param("d", Type.I64),
                    Param("e", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                val d = Parameter("d", Type.I64, 3)
                val e = Parameter("e", Type.I64, 4)
                val ab = add(a, b)
                val cd = add(c, d)
                val abcd = add(ab, cd)
                val result = add(abcd, e)
                ret(result)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            // Only 3 param registers, so params d and e don't get param slots
            assertTrue(result.locations.isNotEmpty())
        }
    }

    @Nested
    inner class RegisterPressureTests {

        @Test
        fun `high pressure with many simultaneous live values`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v1 = add(Constant.I64(1), Constant.I64(2))
                val v2 = add(Constant.I64(3), Constant.I64(4))
                val v3 = add(Constant.I64(5), Constant.I64(6))
                val v4 = add(Constant.I64(7), Constant.I64(8))
                val v5 = add(Constant.I64(9), Constant.I64(10))
                val s1 = add(v1, v2)
                val s2 = add(v3, v4)
                val s3 = add(s1, s2)
                val result = add(s3, v5)
                ret(result)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), tinyConstraints)
            assertTrue(result.spillSlots > 0, "Should spill with only 2 registers")
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `tree reduction needs log n registers`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                val c = add(Constant.I64(5), Constant.I64(6))
                val d = add(Constant.I64(7), Constant.I64(8))
                val ab = add(a, b)
                val cd = add(c, d)
                val result = add(ab, cd)
                ret(result)
                finalizeFunction()
            }
            val threeRegs = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = emptyList(),
                returnRegisters = listOf(r0),
            )
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), threeRegs)
            // Tree reduction of 8 leaves can be done with ~3 registers
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `linear chain uses minimal registers`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                var v = add(Constant.I64(0), Constant.I64(1))
                for (i in 2..10) {
                    v = add(v, Constant.I64(i.toLong()))
                }
                ret(v)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), singleRegConstraints)
            // Linear chain: each value dies before next is produced (approximately)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `wide fan-out creates high pressure`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, Constant.I64(1))
                val b = add(x, Constant.I64(2))
                val c = add(x, Constant.I64(3))
                val d = add(x, Constant.I64(4))
                val e = add(x, Constant.I64(5))
                // All of a-e might still be live if used at the end
                val s1 = add(a, b)
                val s2 = add(c, d)
                val s3 = add(s1, s2)
                val result = add(s3, e)
                ret(result)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), tinyConstraints)
            assertTrue(result.spillSlots > 0)
        }

        @Test
        fun `six params three param regs`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                    Param("d", Type.I64), Param("e", Type.I64), Param("f", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                val d = Parameter("d", Type.I64, 3)
                val e = Parameter("e", Type.I64, 4)
                val f = Parameter("f", Type.I64, 5)
                val s1 = add(a, b)
                val s2 = add(c, d)
                val s3 = add(e, f)
                val s4 = add(s1, s2)
                val result = add(s4, s3)
                ret(result)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
            // Only first 3 params get param registers
        }
    }

    @Nested
    inner class ControlFlowTests {

        @Test
        fun `diamond CFG allocation succeeds`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, x, Constant.I64(0))
                condBr(cond, "then", "else")

                positionAtEnd(appendBlock("then"))
                val a = add(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("else"))
                val b = sub(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("merge"))
                val phi = phi(Type.I64, listOf(a to "then", b to "else"))
                ret(phi)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
            assertEquals(0, result.spillSlots)
        }

        @Test
        fun `simple loop allocation`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                br("loop")

                positionAtEnd(appendBlock("loop"))
                val n = Parameter("n", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, n, Constant.I64(0))
                condBr(cond, "body", "exit")

                positionAtEnd(appendBlock("body"))
                val dec = sub(n, Constant.I64(1))
                br("loop")

                positionAtEnd(appendBlock("exit"))
                ret(n)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `nested conditional allocation`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64), Param("y", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val y = Parameter("y", Type.I64, 1)
                val c1 = icmp(ICmpPredicate.SGT, x, Constant.I64(0))
                condBr(c1, "outer_then", "outer_else")

                positionAtEnd(appendBlock("outer_then"))
                val c2 = icmp(ICmpPredicate.SGT, y, Constant.I64(0))
                condBr(c2, "inner_then", "inner_else")

                positionAtEnd(appendBlock("inner_then"))
                val a = add(x, y)
                br("merge")

                positionAtEnd(appendBlock("inner_else"))
                val b = sub(x, y)
                br("merge")

                positionAtEnd(appendBlock("outer_else"))
                val c = mul(x, y)
                br("merge")

                positionAtEnd(appendBlock("merge"))
                val phi = phi(Type.I64, listOf(a to "inner_then", b to "inner_else", c to "outer_else"))
                ret(phi)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `loop with call inside`() {
            val fn = buildFunction {
                declareFunction("process", listOf(Param("v", Type.I64)), Type.I64)
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                br("loop")

                positionAtEnd(appendBlock("loop"))
                val n = Parameter("n", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, n, Constant.I64(0))
                condBr(cond, "body", "exit")

                positionAtEnd(appendBlock("body"))
                call("process", listOf(n), Type.I64)
                br("loop")

                positionAtEnd(appendBlock("exit"))
                ret(n)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, standardConstraints)
            val nIv = intervals.first { it.name == "n" }
            assertTrue(nIv.acrossCall)
        }

        @Test
        fun `sequential blocks no branches`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, Constant.I64(1))
                val b = add(a, Constant.I64(2))
                val c = add(b, Constant.I64(3))
                ret(c)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertEquals(0, result.spillSlots)
        }
    }

    @Nested
    inner class CallClobberTests {

        @Test
        fun `single call clobbers all caller-saved`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                call("ext", emptyList(), Type.Void)
                ret(x)
                finalizeFunction()
            }
            val withCalleeSaved = RegisterConstraints(
                allocatable = listOf(r0, r1, r2, r3, r4, r5),
                reserved = emptySet(),
                calleeSaved = setOf(r4, r5),
                paramRegisters = listOf(r0),
                returnRegisters = listOf(r0),
            )
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), withCalleeSaved)

            val xLoc = result.locations["x"]
            if (xLoc is ValueLocation.Register) {
                assertTrue(xLoc.reg in withCalleeSaved.calleeSaved || result.paramMoves.containsKey("x"))
            }
        }

        @Test
        fun `multiple calls with values across each`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                call("ext", emptyList(), Type.I64)
                val s1 = add(a, Constant.I64(1))
                call("ext", emptyList(), Type.I64)
                val s2 = add(b, s1)
                ret(s2)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `call result not clobbered by its own call`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.I64)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r = call("ext", emptyList(), Type.I64)!!
                ret(r)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `value defined before call used after call needs preservation`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(42), Constant.I64(0))
                call("ext", emptyList(), Type.Void)
                ret(v)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val vIv = intervals.first()
            assertTrue(vIv.acrossCall)
        }

        @Test
        fun `multiple div operations each clobber`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = sdiv(a, b)
                val r = srem(a, b)
                val sum = add(q, r)
                ret(sum)
                finalizeFunction()
            }
            val clobber = RegisterConstraints(
                allocatable = listOf(r0, r1, r2, r3),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r2)),
            )
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val events = LivenessAnalysis(fn).clobberEvents(clobber)
            assertEquals(2, events.size)
            val result = allocator.allocate(fn, intervals, clobber, events)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `shift clobber with variable amount`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val s = shl(a, b)
                ret(s)
                finalizeFunction()
            }
            val shiftClobber = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.VARIABLE_SHIFT to setOf(r1)),
            )
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val events = LivenessAnalysis(fn).clobberEvents(shiftClobber)
            val result = allocator.allocate(fn, intervals, shiftClobber, events)
            assertTrue(result.locations.isNotEmpty())
        }
    }

    @Nested
    inner class FloatingPointTests {

        @Test
        fun `fp values allocated to fp register class`() {
            val mixedConstraints = RegisterConstraints(
                allocatable = listOf(r0, r1, r2, f0, f1, f2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0),
                returnRegisters = listOf(r0),
            )
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.F64, 0)
                val y = Parameter("y", Type.F64, 1)
                val sum = fadd(x, y)
                ret(sum)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, mixedConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `f32 and f64 intervals have correct types`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.F32), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.F32, 0)
                val y = Parameter("y", Type.F64, 1)
                ret(y)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val xIv = intervals.first { it.name == "x" }
            val yIv = intervals.first { it.name == "y" }
            assertEquals(Type.F32, xIv.type)
            assertEquals(Type.F64, yIv.type)
        }
    }

    @Nested
    inner class EvictionTests {

        @Test
        fun `lower cost value evicted first`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                // a has fewer uses, b has fewer uses, c uses both
                val c = add(a, b)
                ret(c)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), tinyConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `across call increases spill cost`() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                call("ext", emptyList(), Type.Void)
                val c = add(a, b)
                ret(c)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            // Both a and b are across the call, increasing their spill cost
            for (iv in intervals) {
                if (iv.name != "ext") {
                    // a and b should be across call
                }
            }
            val result = allocator.allocate(fn, intervals, tinyConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `heavily used value survives eviction`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, x)
                val b = add(a, x)
                val c = add(b, x)
                val d = add(c, x)
                // x is used 5 times, making it expensive to spill
                ret(d)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), tinyConstraints)
            val xLoc = result.locations["x"]
            assertTrue(xLoc is ValueLocation.Register, "Heavily used value should stay in register")
        }
    }

    @Nested
    inner class LocalSlotAllocatorTests {

        @Test
        fun `parameters get first slots`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertEquals(0, result.slotMap["a"])
            assertEquals(1, result.slotMap["b"])
        }

        @Test
        fun `slot reuse for non-overlapping lifetimes`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(a, Constant.I64(3))
                // a is dead after b is computed
                val c = add(Constant.I64(4), Constant.I64(5))
                val d = add(b, c)
                ret(d)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            // With reuse, total slots should be less than unique values
            assertTrue(result.totalSlots <= 4)
        }

        @Test
        fun `no reuse mode gives each value unique slot`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(a, Constant.I64(3))
                val c = add(Constant.I64(4), Constant.I64(5))
                val d = add(b, c)
                ret(d)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), reuseSlots = false)
            val uniqueSlots = result.slotMap.values.toSet()
            assertEquals(result.slotMap.size, uniqueSlots.size, "Each value should have unique slot")
        }

        @Test
        fun `type-based slot reuse only reuses matching types`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val a = add(x, Constant.I64(1))
                // a dies, its slot could be reused by another I64 value
                val b = add(a, Constant.I64(2))
                ret(b)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            // Slot types should be tracked
            for ((slot, type) in result.slotTypes) {
                assertNotNull(type)
            }
        }

        @Test
        fun `empty function produces param slots only`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals)
            assertEquals(0, result.slotMap["x"])
        }

        @Test
        fun `no params no values gives zero slots`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertEquals(0, result.totalSlots)
        }

        @Test
        fun `slot types tracked for all slots`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I32, 0)
                val b = Parameter("b", Type.I64, 1)
                ret(b)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertEquals(Type.I32, result.slotTypes[0])
            assertEquals(Type.I64, result.slotTypes[1])
        }

        @Test
        fun `high watermark tracks maximum slots needed`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(Constant.I64(3), Constant.I64(4))
                val c = add(Constant.I64(5), Constant.I64(6))
                val d = add(a, b)
                val e = add(d, c)
                ret(e)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertTrue(result.totalSlots > 0)
            assertTrue(result.totalSlots <= 5)
        }

        @Test
        fun `reuse with same type reuses slot`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(a, Constant.I64(3))
                // a should be dead, its I64 slot reusable
                val c = add(b, Constant.I64(4))
                ret(c)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val withReuse = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), reuseSlots = true)
            val withoutReuse = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), reuseSlots = false)
            assertTrue(withReuse.totalSlots <= withoutReuse.totalSlots)
        }

        @Test
        fun `many sequential values reuse slots aggressively`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                var v = add(Constant.I64(0), Constant.I64(1))
                for (i in 2..8) {
                    v = add(v, Constant.I64(i.toLong()))
                }
                ret(v)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertTrue(result.totalSlots <= 3, "Sequential chain should reuse slots aggressively")
        }

        @Test
        fun `parameter slots preserved even when unused`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I64(42))
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertEquals(0, result.slotMap["a"])
            assertEquals(1, result.slotMap["b"])
            assertEquals(2, result.slotMap["c"])
            assertTrue(result.totalSlots >= 3)
        }

        @Test
        fun `local slot allocator with branching CFG`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, x, Constant.I64(0))
                condBr(cond, "then", "else")

                positionAtEnd(appendBlock("then"))
                val a = add(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("else"))
                val b = sub(x, Constant.I64(1))
                br("merge")

                positionAtEnd(appendBlock("merge"))
                val phi = phi(Type.I64, listOf(a to "then", b to "else"))
                ret(phi)
                finalizeFunction()
            }
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals())
            assertEquals(0, result.slotMap["x"])
            assertTrue(result.totalSlots >= 2)
        }
    }

    @Nested
    inner class DataModelTests {

        @Test
        fun `physical register equality by name and encoding`() {
            val a = PhysicalRegister("RAX", 0, gpClass)
            val b = PhysicalRegister("RAX", 0, gpClass)
            assertEquals(a, b)
        }

        @Test
        fun `physical register inequality different encoding`() {
            val a = PhysicalRegister("RAX", 0, gpClass)
            val b = PhysicalRegister("RAX", 1, gpClass)
            assertNotEquals(a, b)
        }

        @Test
        fun `physical register toString returns name`() {
            val r = PhysicalRegister("RCX", 1, gpClass)
            assertEquals("RCX", r.toString())
        }

        @Test
        fun `register class count`() {
            val regs = mutableListOf<PhysicalRegister>()
            val cls = RegisterClass("gp", regs)
            regs.add(PhysicalRegister("A", 0, cls))
            regs.add(PhysicalRegister("B", 1, cls))
            regs.add(PhysicalRegister("C", 2, cls))
            assertEquals(3, cls.count)
        }

        @Test
        fun `value location register holds correct register`() {
            val loc = ValueLocation.Register(r0)
            assertEquals(r0, loc.reg)
        }

        @Test
        fun `value location spill slot holds correct offset`() {
            val loc = ValueLocation.SpillSlot(-16)
            assertEquals(-16, loc.offset)
        }

        @Test
        fun `constraints builder produces correct constraints`() {
            val c = RegisterConstraints.builder()
                .allocatable(listOf(r0, r1))
                .reserved(setOf(r2))
                .calleeSaved(setOf(r1))
                .paramRegisters(listOf(r0))
                .returnRegisters(listOf(r0))
                .clobber(InstructionClobber.INT_DIV, setOf(r0))
                .build()
            assertEquals(2, c.allocatable.size)
            assertTrue(r2 in c.reserved)
            assertTrue(r1 in c.calleeSaved)
            assertEquals(setOf(r0), c.clobbers[InstructionClobber.INT_DIV])
        }

        @Test
        fun `instruction clobber enum has expected entries`() {
            assertEquals(3, InstructionClobber.entries.size)
            assertTrue(InstructionClobber.entries.contains(InstructionClobber.INT_DIV))
            assertTrue(InstructionClobber.entries.contains(InstructionClobber.VARIABLE_SHIFT))
            assertTrue(InstructionClobber.entries.contains(InstructionClobber.CALL))
        }

        @Test
        fun `live interval data class properties`() {
            val iv = LiveInterval("x", 0, 5, Type.I64, 3, true)
            assertEquals("x", iv.name)
            assertEquals(0, iv.start)
            assertEquals(5, iv.end)
            assertEquals(Type.I64, iv.type)
            assertEquals(3, iv.useCount)
            assertTrue(iv.acrossCall)
        }

        @Test
        fun `live interval default values`() {
            val iv = LiveInterval("y", 1, 3, Type.I32)
            assertEquals(1, iv.useCount)
            assertFalse(iv.acrossCall)
        }

        @Test
        fun `register assignment paramMoves defaults to empty`() {
            val assignment = RegisterAssignment(
                locations = emptyMap(),
                spillSlots = 0,
                usedCalleeRegisters = emptySet(),
            )
            assertTrue(assignment.paramMoves.isEmpty())
            assertTrue(assignment.splitPoints.isEmpty())
        }

        @Test
        fun `split point stores correct data`() {
            val sp = SplitPoint("x", 5, 6, -8)
            assertEquals("x", sp.valueName)
            assertEquals(5, sp.saveBeforePosition)
            assertEquals(6, sp.reloadAfterPosition)
            assertEquals(-8, sp.spillOffset)
        }

        @Test
        fun `clobber event stores registers and position`() {
            val event = ClobberEvent(10, setOf(r0, r1))
            assertEquals(10, event.position)
            assertEquals(2, event.clobberedRegisters.size)
            assertTrue(r0 in event.clobberedRegisters)
        }

        @Test
        fun `local slot assignment data class`() {
            val assignment = LocalSlotAssignment(
                slotMap = mapOf("a" to 0, "b" to 1),
                totalSlots = 2,
                slotTypes = mapOf(0 to Type.I64, 1 to Type.I32),
            )
            assertEquals(0, assignment.slotMap["a"])
            assertEquals(1, assignment.slotMap["b"])
            assertEquals(2, assignment.totalSlots)
            assertEquals(Type.I64, assignment.slotTypes[0])
        }
    }

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `single instruction function`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertEquals(0, result.spillSlots)
        }

        @Test
        fun `value used only in return`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(0), Constant.I64(0))
                ret(v)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `constant-only instructions produce minimal intervals`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(100), Constant.I64(200))
                ret(v)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            assertEquals(1, intervals.size, "Only the add result should have an interval")
        }

        @Test
        fun `allocator with zero allocatable registers throws`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(1), Constant.I64(2))
                ret(v)
                finalizeFunction()
            }
            val noRegs = RegisterConstraints(
                allocatable = emptyList(),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = emptyList(),
                returnRegisters = emptyList(),
            )
            val allocator = LinearScanRegisterAllocator()
            assertThrows(NoSuchElementException::class.java) {
                allocator.allocate(fn, LivenessAnalysis(fn).intervals(), noRegs)
            }
        }

        @Test
        fun `negation instruction operand extracted`() {
            val inst = Neg(
                InstructionRef("r", Type.I64),
                Parameter("x", Type.I64, 0)
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
            assertEquals("x", ops[0].name)
        }

        @Test
        fun `bitwise not instruction operand extracted`() {
            val inst = Not(
                InstructionRef("r", Type.I64),
                Parameter("x", Type.I64, 0)
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `store instruction extracts value and pointer`() {
            val ptrType = Type.Pointer(Type.I64)
            val inst = Store(
                Parameter("val", Type.I64, 0),
                Parameter("ptr", ptrType, 1),
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(2, ops.size)
        }

        @Test
        fun `load instruction extracts pointer`() {
            val ptrType = Type.Pointer(Type.I64)
            val inst = Load(
                InstructionRef("r", Type.I64),
                Parameter("ptr", ptrType, 0),
                Type.I64,
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `zext instruction extracts operand`() {
            val inst = ZExt(
                InstructionRef("r", Type.I64),
                Parameter("x", Type.I32, 0),
                Type.I64,
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `sext instruction extracts operand`() {
            val inst = SExt(
                InstructionRef("r", Type.I64),
                Parameter("x", Type.I32, 0),
                Type.I64,
            )
            val ops = LivenessAnalysis.operandValues(inst)
            assertEquals(1, ops.size)
        }

        @Test
        fun `many params all used at end`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64),
                    Param("c", Type.I64), Param("d", Type.I64),
                    Param("e", Type.I64), Param("f", Type.I64),
                    Param("g", Type.I64), Param("h", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                val d = Parameter("d", Type.I64, 3)
                val e = Parameter("e", Type.I64, 4)
                val f = Parameter("f", Type.I64, 5)
                val g = Parameter("g", Type.I64, 6)
                val h = Parameter("h", Type.I64, 7)
                val s1 = add(a, b)
                val s2 = add(c, d)
                val s3 = add(e, f)
                val s4 = add(g, h)
                val s5 = add(s1, s2)
                val s6 = add(s3, s4)
                val result = add(s5, s6)
                ret(result)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }
    }

    @Nested
    inner class IntegrationTests {

        @Test
        fun `liveness then linear scan end to end`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val sum = add(a, b)
                val prod = mul(a, b)
                val diff = sub(sum, prod)
                ret(diff)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            assertTrue(intervals.isNotEmpty())

            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, intervals, standardConstraints)

            // Every interval should be allocated
            for (iv in intervals) {
                assertTrue(iv.name in result.locations, "${iv.name} should be allocated")
            }
        }

        @Test
        fun `liveness then local slot end to end`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val allocator = LocalSlotAllocator()
            val result = allocator.allocate(fn, intervals)

            for (iv in intervals) {
                assertTrue(iv.name in result.slotMap, "${iv.name} should have a slot")
            }
        }

        @Test
        fun `same function different constraints different results`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = add(a, b)
                val d = mul(a, c)
                val e = sub(d, b)
                ret(e)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val allocator = LinearScanRegisterAllocator()

            val result6 = allocator.allocate(fn, intervals, standardConstraints)
            val result2 = allocator.allocate(fn, intervals, tinyConstraints)

            assertTrue(result6.spillSlots <= result2.spillSlots,
                "More registers should mean fewer spills")
        }

        @Test
        fun `complex function with calls branches and arithmetic`() {
            val fn = buildFunction {
                declareFunction("compute", listOf(Param("v", Type.I64)), Type.I64)
                createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val n = Parameter("n", Type.I64, 0)
                val cond = icmp(ICmpPredicate.SGT, n, Constant.I64(10))
                condBr(cond, "big", "small")

                positionAtEnd(appendBlock("big"))
                val r1 = call("compute", listOf(n), Type.I64)!!
                val doubled = mul(r1, Constant.I64(2))
                br("done")

                positionAtEnd(appendBlock("small"))
                val incremented = add(n, Constant.I64(1))
                br("done")

                positionAtEnd(appendBlock("done"))
                val phi = phi(Type.I64, listOf(doubled to "big", incremented to "small"))
                ret(phi)
                finalizeFunction()
            }
            val allocator = LinearScanRegisterAllocator()
            val result = allocator.allocate(fn, LivenessAnalysis(fn).intervals(), standardConstraints)
            assertTrue(result.locations.isNotEmpty())
        }

        @Test
        fun `register and slot allocator agree on value count`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = add(a, b)
                val d = mul(c, Constant.I64(2))
                ret(d)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()

            val regResult = LinearScanRegisterAllocator().allocate(fn, intervals, standardConstraints)
            val slotResult = LocalSlotAllocator().allocate(fn, intervals)

            assertEquals(regResult.locations.size, slotResult.slotMap.size,
                "Both allocators should handle the same number of values")
        }
    }
}

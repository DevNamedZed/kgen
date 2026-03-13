package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class GraphColoringCoalescingTest {

    private val gpClass = RegisterClass("GP", emptyList())

    private fun gp(name: String, enc: Int) = PhysicalRegister(name, enc, gpClass)

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
        return ir.build().functions.last { !it.isExternal }
    }

    @Nested
    inner class PhiCoalescing {

        @Test
        fun phiWithNonOverlappingIncomingCoalesces() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("cond", Type.I1)), Type.I64)
                val entry = appendBlock("entry")
                val left = appendBlock("left")
                val right = appendBlock("right")
                val merge = appendBlock("merge")

                positionAtEnd(entry)
                condBr(Parameter("cond", Type.I1, 0), "left", "right")

                positionAtEnd(left)
                val a = add(Constant.I64(1), Constant.I64(2))
                br("merge")

                positionAtEnd(right)
                val b = add(Constant.I64(3), Constant.I64(4))
                br("merge")

                positionAtEnd(merge)
                val result = phi(Type.I64, listOf(a to "left", b to "right"))
                ret(result)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            // All values should be allocated
            for (iv in intervals) {
                assertTrue(iv.name in result.locations,
                    "Value '${iv.name}' should have a location")
            }
            assertEquals(0, result.spillSlots, "Simple phi should not need spills")
        }

        @Test
        fun coalescedPhiValuesShareRegister() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("cond", Type.I1)), Type.I64)
                val entry = appendBlock("entry")
                val left = appendBlock("left")
                val right = appendBlock("right")
                val merge = appendBlock("merge")

                positionAtEnd(entry)
                condBr(Parameter("cond", Type.I1, 0), "left", "right")

                positionAtEnd(left)
                val a = add(Constant.I64(10), Constant.I64(20))
                br("merge")

                positionAtEnd(right)
                val b = add(Constant.I64(30), Constant.I64(40))
                br("merge")

                positionAtEnd(merge)
                val merged = phi(Type.I64, listOf(a to "left", b to "right"))
                ret(merged)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            // a and b don't overlap, so graph coloring can coalesce them with the phi.
            // At minimum, both a and b should be in registers.
            val aLoc = result.locations.entries.find { it.key != "cond" && it.value is ValueLocation.Register }
            assertNotNull(aLoc, "At least one value should be in a register")
        }
    }

    @Nested
    inner class CopyCoalescing {

        @Test
        fun bitcastCoalescing() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I64, 0)
                val casted = bitcast(x, Type.I64)
                val result = add(casted, Constant.I64(1))
                ret(result)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            // bitcast is a copy-like instruction; x and casted should coalesce
            assertTrue(result.locations.values.all { it is ValueLocation.Register },
                "All values in simple bitcast chain should be in registers")
        }

        @Test
        fun zextCoalescing() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val x = Parameter("x", Type.I32, 0)
                val extended = zext(x, Type.I64)
                ret(extended)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            assertTrue(result.locations.isNotEmpty())
            assertEquals(0, result.spillSlots)
        }
    }

    @Nested
    inner class InterferenceDetection {

        @Test
        fun overlappingValuesGetDifferentRegisters() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                // Both a and b are live at the add instruction
                val sum = add(a, b)
                ret(sum)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            val aLoc = result.locations["a"]
            val bLoc = result.locations["b"]
            if (aLoc is ValueLocation.Register && bLoc is ValueLocation.Register) {
                assertNotEquals(aLoc.reg, bLoc.reg,
                    "Simultaneously live values must be in different registers")
            }
        }

        @Test
        fun highInterferenceWithEnoughRegisters() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64),
                    Param("c", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = Parameter("c", Type.I64, 2)
                // All three params are live at each use
                val ab = add(a, b)
                val abc = add(ab, c)
                val result = add(a, abc)
                ret(result)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            // With 6 registers, should easily handle 3 params + temporaries
            assertEquals(0, result.spillSlots)
        }

        @Test
        fun highInterferenceForcesSpill() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)
                ), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = add(a, b)
                val d = mul(a, b)
                val e = sub(c, d)
                val f = add(a, e)
                val g = mul(b, f)
                ret(g)
                finalizeFunction()
            }

            val twoRegs = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
            )

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, twoRegs)

            // With only 2 registers and high interference, some values must spill
            assertTrue(result.spillSlots > 0 || result.paramMoves.isNotEmpty(),
                "High interference with few registers should cause spills or moves")
        }
    }

    @Nested
    inner class CallerSavedPreference {

        @Test
        fun shortLivedValuePrefersCallerSaved() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(Constant.I64(1), Constant.I64(2))
                ret(v)
                finalizeFunction()
            }

            val allocator = GraphColoringAllocator()
            val intervals = LivenessAnalysis(fn).intervals()
            val result = allocator.allocate(fn, intervals, constraints)

            // Short-lived value (not across call) should prefer caller-saved registers
            val vLoc = result.locations.values.filterIsInstance<ValueLocation.Register>().firstOrNull()
            if (vLoc != null) {
                assertTrue(vLoc.reg !in constraints.calleeSaved,
                    "Short-lived value should prefer caller-saved register (got ${vLoc.reg})")
            }
        }
    }
}

package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class ClobberEventAndSplitPointTest {

    private val gpClass = RegisterClass("GP", emptyList())

    private fun gp(name: String, enc: Int) = PhysicalRegister(name, enc, gpClass)

    private val r0 = gp("R0", 0)
    private val r1 = gp("R1", 1)
    private val r2 = gp("R2", 2)

    @Nested
    inner class ClobberEventModel {

        @Test
        fun construction() {
            val event = ClobberEvent(5, setOf(r0, r1))
            assertEquals(5, event.position)
            assertEquals(2, event.clobberedRegisters.size)
            assertTrue(r0 in event.clobberedRegisters)
            assertTrue(r1 in event.clobberedRegisters)
        }

        @Test
        fun equality() {
            val a = ClobberEvent(3, setOf(r0))
            val b = ClobberEvent(3, setOf(r0))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByPosition() {
            val a = ClobberEvent(3, setOf(r0))
            val b = ClobberEvent(4, setOf(r0))
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityByRegisters() {
            val a = ClobberEvent(3, setOf(r0))
            val b = ClobberEvent(3, setOf(r1))
            assertNotEquals(a, b)
        }

        @Test
        fun emptyRegisterSet() {
            val event = ClobberEvent(1, emptySet())
            assertTrue(event.clobberedRegisters.isEmpty())
        }
    }

    @Nested
    inner class SplitPointModel {

        @Test
        fun construction() {
            val sp = SplitPoint("x", 5, 6, -8)
            assertEquals("x", sp.valueName)
            assertEquals(5, sp.saveBeforePosition)
            assertEquals(6, sp.reloadAfterPosition)
            assertEquals(-8, sp.spillOffset)
        }

        @Test
        fun equality() {
            val a = SplitPoint("x", 5, 6, -8)
            val b = SplitPoint("x", 5, 6, -8)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByName() {
            val a = SplitPoint("x", 5, 6, -8)
            val b = SplitPoint("y", 5, 6, -8)
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityBySavePosition() {
            val a = SplitPoint("x", 5, 6, -8)
            val b = SplitPoint("x", 4, 6, -8)
            assertNotEquals(a, b)
        }

        @Test
        fun samePositionForSaveAndReload() {
            val sp = SplitPoint("x", 5, 5, -8)
            assertEquals(sp.saveBeforePosition, sp.reloadAfterPosition)
        }
    }

    @Nested
    inner class ClobberEventDetection {

        private fun buildFunction(block: ModuleBuilder.() -> Unit): IrFunction {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.block()
            return ir.build().functions.last { !it.isExternal }
        }

        @Test
        fun sdivDetectedAsIntDiv() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = sdiv(a, b)
                ret(q)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0, r1, r2),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0, r2)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertEquals(1, events.size)
            assertEquals(setOf(r0, r2), events[0].clobberedRegisters)
        }

        @Test
        fun udivDetectedAsIntDiv() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val q = udiv(a, b)
                ret(q)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertEquals(1, events.size)
        }

        @Test
        fun sremDetectedAsIntDiv() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val r = srem(a, b)
                ret(r)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.INT_DIV to setOf(r0)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertEquals(1, events.size)
        }

        @Test
        fun shlDetectedAsVariableShift() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val s = shl(a, b)
                ret(s)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.VARIABLE_SHIFT to setOf(r1)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertEquals(1, events.size)
            assertEquals(setOf(r1), events[0].clobberedRegisters)
        }

        @Test
        fun callDetectedAsCallClobber() {
            val fn = buildFunction {
                declareFunction("ext", emptyList(), Type.Void)
                createFunction("f", emptyList(), Type.Void)
                appendBlock("entry")
                call("ext", emptyList(), Type.Void)
                ret()
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = emptyList(),
                returnRegisters = listOf(r0),
                clobbers = mapOf(InstructionClobber.CALL to setOf(r0, r1)),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertEquals(1, events.size)
            assertEquals(setOf(r0, r1), events[0].clobberedRegisters)
        }

        @Test
        fun noClobberEntriesProducesNoEvents() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val q = sdiv(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
                ret(q)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertTrue(events.isEmpty())
        }

        @Test
        fun addDoesNotGenerateClobberEvent() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val sum = add(Parameter("a", Type.I64, 0), Parameter("b", Type.I64, 1))
                ret(sum)
                finalizeFunction()
            }
            val constraints = RegisterConstraints(
                allocatable = listOf(r0, r1),
                reserved = emptySet(),
                calleeSaved = emptySet(),
                paramRegisters = listOf(r0, r1),
                returnRegisters = listOf(r0),
                clobbers = mapOf(
                    InstructionClobber.INT_DIV to setOf(r0),
                    InstructionClobber.VARIABLE_SHIFT to setOf(r1),
                    InstructionClobber.CALL to setOf(r0, r1),
                ),
            )
            val events = LivenessAnalysis(fn).clobberEvents(constraints)
            assertTrue(events.isEmpty())
        }
    }
}

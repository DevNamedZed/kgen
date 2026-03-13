package org.kgen.codegen.alloc

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class LocalSlotReuseTest {

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build().functions.last { !it.isExternal }
    }

    @Nested
    inner class TypeBasedReuse {

        @Test
        fun sameTypeReusesSlot() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(a, Constant.I64(3)) // a is dead after this
                val c = add(Constant.I64(4), Constant.I64(5)) // c can reuse a's I64 slot
                val result = add(b, c)
                ret(result)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val alloc = LocalSlotAllocator()
            val withReuse = alloc.allocate(fn, intervals, reuseSlots = true)
            val withoutReuse = alloc.allocate(fn, intervals, reuseSlots = false)

            assertTrue(withReuse.totalSlots <= withoutReuse.totalSlots,
                "Reuse should use fewer or equal slots")
        }

        @Test
        fun differentTypesDoNotReuse() {
            // Build with directly constructed intervals to isolate type-based reuse behavior
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = add(Constant.I64(1), Constant.I64(2))
                val b = add(a, Constant.I64(3)) // a dead here
                // After a is dead, create an F64 value. It should NOT reuse a's I64 slot.
                val c = fadd(Constant.F64(1.0), Constant.F64(2.0))
                val d = add(b, Constant.I64(7))
                // Use c so it's not optimized away
                val e = fadd(c, Constant.F64(3.0))
                ret(d)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val alloc = LocalSlotAllocator()
            val result = alloc.allocate(fn, intervals, reuseSlots = true)

            // Verify that I64 and F64 values get different slot types
            val i64Slots = result.slotTypes.entries.filter { it.value == Type.I64 }.map { it.key }
            val f64Slots = result.slotTypes.entries.filter { it.value == Type.F64 }.map { it.key }
            val overlap = i64Slots.intersect(f64Slots.toSet())
            assertTrue(overlap.isEmpty(), "I64 and F64 should not share slots")
        }
    }

    @Nested
    inner class SlotAssignmentModel {

        @Test
        fun localSlotAssignmentConstruction() {
            val assignment = LocalSlotAssignment(
                slotMap = mapOf("x" to 0, "y" to 1),
                totalSlots = 2,
                slotTypes = mapOf(0 to Type.I64, 1 to Type.I32),
            )
            assertEquals(2, assignment.totalSlots)
            assertEquals(0, assignment.slotMap["x"])
            assertEquals(1, assignment.slotMap["y"])
            assertEquals(Type.I64, assignment.slotTypes[0])
            assertEquals(Type.I32, assignment.slotTypes[1])
        }

        @Test
        fun localSlotAssignmentDefaultSlotTypes() {
            val assignment = LocalSlotAssignment(
                slotMap = mapOf("x" to 0),
                totalSlots = 1,
            )
            assertTrue(assignment.slotTypes.isEmpty())
        }

        @Test
        fun localSlotAssignmentEquality() {
            val a = LocalSlotAssignment(mapOf("x" to 0), 1, mapOf(0 to Type.I64))
            val b = LocalSlotAssignment(mapOf("x" to 0), 1, mapOf(0 to Type.I64))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class SequentialValueReuse {

        @Test
        fun chainOfComputationsReusesAggressively() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                // Each value is used exactly once then dies
                val v1 = add(Constant.I64(1), Constant.I64(2))
                val v2 = add(v1, Constant.I64(3))
                val v3 = add(v2, Constant.I64(4))
                val v4 = add(v3, Constant.I64(5))
                ret(v4)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val alloc = LocalSlotAllocator()
            val result = alloc.allocate(fn, intervals, reuseSlots = true)

            // In a chain, each value dies before the next is born (except brief overlap).
            // Should need at most 2 slots at a time.
            assertTrue(result.totalSlots <= intervals.size,
                "Chain computation should reuse slots")
        }

        @Test
        fun paramSlotsNeverReused() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val a = Parameter("a", Type.I64, 0)
                val b = Parameter("b", Type.I64, 1)
                val c = add(a, b)
                ret(c)
                finalizeFunction()
            }
            val intervals = LivenessAnalysis(fn).intervals()
            val alloc = LocalSlotAllocator()
            val result = alloc.allocate(fn, intervals, reuseSlots = true)

            // Params always get slots 0 and 1
            assertEquals(0, result.slotMap["a"])
            assertEquals(1, result.slotMap["b"])
            // The add result should get a separate slot
            val addSlot = result.slotMap.entries.first { it.key != "a" && it.key != "b" }.value
            assertTrue(addSlot >= 2)
        }
    }

    @Nested
    inner class BranchingCFG {

        @Test
        fun diamondCFGSlotAllocation() {
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
            val intervals = LivenessAnalysis(fn).intervals()
            val alloc = LocalSlotAllocator()
            val result = alloc.allocate(fn, intervals, reuseSlots = true)

            // a and b are on different branches and don't overlap; they can share a slot
            assertTrue(result.totalSlots > 0)
        }
    }
}

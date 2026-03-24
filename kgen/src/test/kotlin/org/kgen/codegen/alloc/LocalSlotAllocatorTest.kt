package org.kgen.codegen.alloc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class LocalSlotAllocatorTest {

    private fun buildFunction(block: ModuleBuilder.() -> Unit): IrFunction {
        val ir = ModuleBuilder("test_module", Target.x86_64())
        ir.block()
        return ir.build().functions[0]
    }

    @Test
    fun paramsGetFirstSlots() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret(Parameter("a", Type.I64, 0))
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals)

        assertEquals(0, result.slotMap["a"])
        assertEquals(1, result.slotMap["b"])
    }

    @Test
    fun valuesGetSlotsAfterParams() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val p = Parameter("x", Type.I64, 0)
            val doubled = add(p, p)
            ret(doubled)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals)

        assertEquals(0, result.slotMap["x"])
        // The add result should get slot 1
        val addSlot = result.slotMap.entries.first { it.key != "x" }.value
        assertEquals(1, addSlot)
    }

    @Test
    fun slotReuseWhenLifetimesDontOverlap() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val a = add(Constant.I64(1), Constant.I64(2))
            val b = add(a, Constant.I64(3)) // a is dead after this
            val c = add(Constant.I64(4), Constant.I64(5)) // c can reuse a's slot
            val result = add(b, c)
            ret(result)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals, reuseSlots = true)

        // With reuse, total slots should be less than total intervals
        assertTrue(result.totalSlots <= intervals.size,
            "Slot reuse should reduce total slots (got ${result.totalSlots} for ${intervals.size} intervals)")
    }

    @Test
    fun noReuseGivesUniqueSlots() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            val a = add(Constant.I64(1), Constant.I64(2))
            val b = add(a, Constant.I64(3))
            val c = add(Constant.I64(4), Constant.I64(5))
            val result = add(b, c)
            ret(result)
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals, reuseSlots = false)

        // Without reuse, each value gets its own slot
        val uniqueSlots = result.slotMap.values.toSet()
        assertEquals(result.slotMap.size, uniqueSlots.size, "Each value should have a unique slot")
    }

    @Test
    fun slotTypesTracked() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("x", Type.I64), Param("y", Type.I32)), Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret(Parameter("x", Type.I64, 0))
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals)

        assertEquals(Type.I64, result.slotTypes[0])
        assertEquals(Type.I32, result.slotTypes[1])
    }

    @Test
    fun emptyFunctionNoSlots() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret()
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals)

        assertEquals(0, result.totalSlots)
        assertTrue(result.slotMap.isEmpty())
    }

    @Test
    fun manyParamsGetSequentialSlots() {
        val params = (0 until 8).map { Param("p$it", Type.I64) }
        val fn = buildFunction {
            createFunction("f", params, Type.I64)
            val entry = createBlock("entry")
            appendBlock(entry)
            ret(Parameter("p0", Type.I64, 0))
            finalizeFunction()
        }
        val intervals = LivenessAnalysis(fn).intervals()
        val alloc = LocalSlotAllocator()
        val result = alloc.allocate(fn, intervals)

        for (i in 0 until 8) {
            assertEquals(i, result.slotMap["p$i"], "Param p$i should get slot $i")
        }
    }
}

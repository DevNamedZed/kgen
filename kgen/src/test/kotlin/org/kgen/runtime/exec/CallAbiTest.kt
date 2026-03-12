package org.kgen.runtime.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CallAbiTest {

    @Test
    fun argTypeValues() {
        assertEquals(5, ArgType.entries.size)
        assertNotNull(ArgType.INTEGER)
        assertNotNull(ArgType.FLOAT)
        assertNotNull(ArgType.POINTER)
        assertNotNull(ArgType.STRUCT_SMALL)
        assertNotNull(ArgType.STRUCT_LARGE)
    }

    @Test
    fun argLocationRegister() {
        val loc = ArgLocation.Register(0)
        assertEquals(0, loc.registerIndex)
    }

    @Test
    fun argLocationFloatRegister() {
        val loc = ArgLocation.FloatRegister(3)
        assertEquals(3, loc.registerIndex)
    }

    @Test
    fun argLocationStack() {
        val loc = ArgLocation.Stack(16)
        assertEquals(16, loc.offset)
    }

    @Test
    fun argLocationIndirect() {
        val loc = ArgLocation.Indirect(2)
        assertEquals(2, loc.registerIndex)
    }

    @Test
    fun argLocationSealedVariants() {
        val locations: List<ArgLocation> = listOf(
            ArgLocation.Register(0),
            ArgLocation.FloatRegister(0),
            ArgLocation.Stack(0),
            ArgLocation.Indirect(0),
        )
        assertEquals(4, locations.size)
    }

    @Test
    fun argLocationEquality() {
        assertEquals(ArgLocation.Register(0), ArgLocation.Register(0))
        assertNotEquals(ArgLocation.Register(0), ArgLocation.Register(1))
        assertNotEquals(ArgLocation.Register(0), ArgLocation.FloatRegister(0))
    }

    @Test
    fun vtableEquality() {
        val v1 = VTable(1, longArrayOf(100, 200))
        val v2 = VTable(1, longArrayOf(300, 400))
        assertEquals(v1, v2) // equality is by typeId only
        assertEquals(v1.hashCode(), v2.hashCode())
    }

    @Test
    fun vtableDifferentTypeIds() {
        val v1 = VTable(1, longArrayOf(100))
        val v2 = VTable(2, longArrayOf(100))
        assertNotEquals(v1, v2)
    }

    @Test
    fun itableEquality() {
        val i1 = ITable(10, 1, longArrayOf(100))
        val i2 = ITable(10, 1, longArrayOf(200))
        assertEquals(i1, i2) // equality by interfaceId + typeId
    }

    @Test
    fun itableDifferentIds() {
        val i1 = ITable(10, 1, longArrayOf(100))
        val i2 = ITable(10, 2, longArrayOf(100))
        assertNotEquals(i1, i2)
    }

    @Test
    fun stackFrameData() {
        val frame = StackFrame("main", 0x1234, 0)
        assertEquals("main", frame.functionName)
        assertEquals(0x1234L, frame.returnAddress)
        assertEquals(0, frame.depth)
    }

    @Test
    fun stackFrameEquality() {
        val f1 = StackFrame("main", 0, 0)
        val f2 = StackFrame("main", 0, 0)
        assertEquals(f1, f2)
    }

    @Test
    fun stackFrameCopy() {
        val f = StackFrame("main", 100, 0)
        val f2 = f.copy(depth = 5)
        assertEquals("main", f2.functionName)
        assertEquals(100L, f2.returnAddress)
        assertEquals(5, f2.depth)
    }
}

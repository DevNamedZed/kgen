package org.kgen.ir

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GCStrategyTest {

    @Test
    fun allStrategies() {
        assertEquals(4, GCStrategy.entries.size)
    }

    @Test
    fun strategyIds() {
        assertEquals("none", GCStrategy.NONE.id)
        assertEquals("shadow-stack", GCStrategy.SHADOW_STACK.id)
        assertEquals("statepoint", GCStrategy.STATEPOINT.id)
        assertEquals("refcount", GCStrategy.REFERENCE_COUNTING.id)
    }

    @Test
    fun fromId() {
        assertEquals(GCStrategy.NONE, GCStrategy.fromId("none"))
        assertEquals(GCStrategy.SHADOW_STACK, GCStrategy.fromId("shadow-stack"))
        assertEquals(GCStrategy.STATEPOINT, GCStrategy.fromId("statepoint"))
        assertEquals(GCStrategy.REFERENCE_COUNTING, GCStrategy.fromId("refcount"))
    }

    @Test
    fun fromIdUnknownThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            GCStrategy.fromId("bogus")
        }
    }

    @Test
    fun functionGcField() {
        val fn = IrFunction(
            name = "f",
            params = emptyList(),
            returnType = Type.Void,
            blocks = emptyList(),
            gc = "statepoint",
        )
        assertEquals("statepoint", fn.gc)
        assertEquals(GCStrategy.STATEPOINT, GCStrategy.fromId(fn.gc!!))
    }

    @Test
    fun stackMapEntry() {
        val entry = StackMapEntry(
            instructionOffset = 0x10,
            locations = listOf(
                StackMapLocation.Register(0),
                StackMapLocation.Stack(-8),
                StackMapLocation.Constant(0),
            ),
        )
        assertEquals(0x10L, entry.instructionOffset)
        assertEquals(3, entry.locations.size)
        assertTrue(entry.locations[0] is StackMapLocation.Register)
        assertTrue(entry.locations[1] is StackMapLocation.Stack)
        assertTrue(entry.locations[2] is StackMapLocation.Constant)
    }

    @Test
    fun stackMap() {
        val map = StackMap(
            functionName = "f",
            entries = listOf(
                StackMapEntry(0x10, listOf(StackMapLocation.Register(0))),
                StackMapEntry(0x20, listOf(StackMapLocation.Stack(-16))),
            ),
        )
        assertEquals("f", map.functionName)
        assertEquals(2, map.entries.size)
    }

    @Test
    fun stackMapLocationEquality() {
        assertEquals(StackMapLocation.Register(0), StackMapLocation.Register(0))
        assertNotEquals(StackMapLocation.Register(0), StackMapLocation.Register(1))
        assertEquals(StackMapLocation.Stack(-8), StackMapLocation.Stack(-8))
        assertEquals(StackMapLocation.Constant(0), StackMapLocation.Constant(0))
    }
}

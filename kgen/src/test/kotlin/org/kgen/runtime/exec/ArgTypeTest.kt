package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ArgTypeTest {

    @Test
    fun allEntriesPresent() {
        val entries = ArgType.entries
        assertEquals(5, entries.size)
    }

    @Test
    fun integerExists() {
        assertEquals("INTEGER", ArgType.INTEGER.name)
    }

    @Test
    fun floatExists() {
        assertEquals("FLOAT", ArgType.FLOAT.name)
    }

    @Test
    fun pointerExists() {
        assertEquals("POINTER", ArgType.POINTER.name)
    }

    @Test
    fun structSmallExists() {
        assertEquals("STRUCT_SMALL", ArgType.STRUCT_SMALL.name)
    }

    @Test
    fun structLargeExists() {
        assertEquals("STRUCT_LARGE", ArgType.STRUCT_LARGE.name)
    }

    @Test
    fun valueOfRoundTrips() {
        for (entry in ArgType.entries) {
            assertEquals(entry, ArgType.valueOf(entry.name))
        }
    }

    @Test
    fun ordinalValues() {
        assertEquals(0, ArgType.INTEGER.ordinal)
        assertEquals(1, ArgType.FLOAT.ordinal)
        assertEquals(2, ArgType.POINTER.ordinal)
        assertEquals(3, ArgType.STRUCT_SMALL.ordinal)
        assertEquals(4, ArgType.STRUCT_LARGE.ordinal)
    }
}

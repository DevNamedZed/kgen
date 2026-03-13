package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ArgLocationTest {

    @Nested
    inner class Register {

        @Test
        fun construction() {
            val loc = ArgLocation.Register(5)
            assertEquals(5, loc.registerIndex)
        }

        @Test
        fun equality() {
            assertEquals(ArgLocation.Register(0), ArgLocation.Register(0))
            assertNotEquals(ArgLocation.Register(0), ArgLocation.Register(1))
        }

        @Test
        fun hashCodeConsistent() {
            assertEquals(ArgLocation.Register(3).hashCode(), ArgLocation.Register(3).hashCode())
        }

        @Test
        fun isArgLocation() {
            val loc: ArgLocation = ArgLocation.Register(0)
            assertTrue(loc is ArgLocation.Register)
        }

        @Test
        fun copy() {
            val loc = ArgLocation.Register(5)
            val copy = loc.copy(registerIndex = 10)
            assertEquals(10, copy.registerIndex)
        }
    }

    @Nested
    inner class FloatRegister {

        @Test
        fun construction() {
            val loc = ArgLocation.FloatRegister(3)
            assertEquals(3, loc.registerIndex)
        }

        @Test
        fun equality() {
            assertEquals(ArgLocation.FloatRegister(0), ArgLocation.FloatRegister(0))
            assertNotEquals(ArgLocation.FloatRegister(0), ArgLocation.FloatRegister(1))
        }

        @Test
        fun notEqualToRegister() {
            assertNotEquals(
                ArgLocation.Register(0) as ArgLocation,
                ArgLocation.FloatRegister(0) as ArgLocation
            )
        }

        @Test
        fun copy() {
            val loc = ArgLocation.FloatRegister(5)
            val copy = loc.copy(registerIndex = 7)
            assertEquals(7, copy.registerIndex)
        }
    }

    @Nested
    inner class Stack {

        @Test
        fun construction() {
            val loc = ArgLocation.Stack(16)
            assertEquals(16, loc.offset)
        }

        @Test
        fun equality() {
            assertEquals(ArgLocation.Stack(8), ArgLocation.Stack(8))
            assertNotEquals(ArgLocation.Stack(8), ArgLocation.Stack(16))
        }

        @Test
        fun zeroOffset() {
            val loc = ArgLocation.Stack(0)
            assertEquals(0, loc.offset)
        }

        @Test
        fun negativeOffset() {
            val loc = ArgLocation.Stack(-8)
            assertEquals(-8, loc.offset)
        }

        @Test
        fun copy() {
            val loc = ArgLocation.Stack(16)
            val copy = loc.copy(offset = 24)
            assertEquals(24, copy.offset)
        }
    }

    @Nested
    inner class Indirect {

        @Test
        fun construction() {
            val loc = ArgLocation.Indirect(2)
            assertEquals(2, loc.registerIndex)
        }

        @Test
        fun equality() {
            assertEquals(ArgLocation.Indirect(1), ArgLocation.Indirect(1))
            assertNotEquals(ArgLocation.Indirect(1), ArgLocation.Indirect(2))
        }

        @Test
        fun notEqualToRegister() {
            assertNotEquals(
                ArgLocation.Register(2) as ArgLocation,
                ArgLocation.Indirect(2) as ArgLocation
            )
        }

        @Test
        fun copy() {
            val loc = ArgLocation.Indirect(2)
            val copy = loc.copy(registerIndex = 4)
            assertEquals(4, copy.registerIndex)
        }
    }

    @Nested
    inner class SealedExhaustiveness {

        @Test
        fun whenCoverageIsExhaustive() {
            val locations = listOf(
                ArgLocation.Register(0),
                ArgLocation.FloatRegister(0),
                ArgLocation.Stack(0),
                ArgLocation.Indirect(0),
            )
            for (loc in locations) {
                val description = when (loc) {
                    is ArgLocation.Register -> "register"
                    is ArgLocation.FloatRegister -> "float_register"
                    is ArgLocation.Stack -> "stack"
                    is ArgLocation.Indirect -> "indirect"
                }
                assertTrue(description.isNotEmpty())
            }
        }

        @Test
        fun allVariantsAreArgLocation() {
            val locations: List<ArgLocation> = listOf(
                ArgLocation.Register(0),
                ArgLocation.FloatRegister(1),
                ArgLocation.Stack(8),
                ArgLocation.Indirect(3),
            )
            assertEquals(4, locations.size)
        }
    }
}

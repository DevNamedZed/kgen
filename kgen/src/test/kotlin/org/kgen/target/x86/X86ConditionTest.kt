package org.kgen.target.x86

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86ConditionTest {

    @Test
    fun invertPairs() {
        assertEquals(X86Condition.NOT_OVERFLOW, X86Condition.OVERFLOW.invert())
        assertEquals(X86Condition.OVERFLOW, X86Condition.NOT_OVERFLOW.invert())
        assertEquals(X86Condition.ABOVE_EQUAL, X86Condition.BELOW.invert())
        assertEquals(X86Condition.BELOW, X86Condition.ABOVE_EQUAL.invert())
        assertEquals(X86Condition.NOT_EQUAL, X86Condition.EQUAL.invert())
        assertEquals(X86Condition.EQUAL, X86Condition.NOT_EQUAL.invert())
        assertEquals(X86Condition.ABOVE, X86Condition.BELOW_EQUAL.invert())
        assertEquals(X86Condition.BELOW_EQUAL, X86Condition.ABOVE.invert())
        assertEquals(X86Condition.NOT_SIGN, X86Condition.SIGN.invert())
        assertEquals(X86Condition.SIGN, X86Condition.NOT_SIGN.invert())
        assertEquals(X86Condition.NOT_PARITY, X86Condition.PARITY.invert())
        assertEquals(X86Condition.PARITY, X86Condition.NOT_PARITY.invert())
        assertEquals(X86Condition.GREATER_EQUAL, X86Condition.LESS.invert())
        assertEquals(X86Condition.LESS, X86Condition.GREATER_EQUAL.invert())
        assertEquals(X86Condition.GREATER, X86Condition.LESS_EQUAL.invert())
        assertEquals(X86Condition.LESS_EQUAL, X86Condition.GREATER.invert())
    }

    @Test
    fun doubleInvertIsIdentity() {
        for (condition in X86Condition.entries) {
            assertEquals(condition, condition.invert().invert(), "double invert for $condition")
        }
    }

    @Test
    fun ordinalMatchesX86ConditionCode() {
        assertEquals(0, X86Condition.OVERFLOW.ordinal)
        assertEquals(1, X86Condition.NOT_OVERFLOW.ordinal)
        assertEquals(2, X86Condition.BELOW.ordinal)
        assertEquals(3, X86Condition.ABOVE_EQUAL.ordinal)
        assertEquals(4, X86Condition.EQUAL.ordinal)
        assertEquals(5, X86Condition.NOT_EQUAL.ordinal)
        assertEquals(6, X86Condition.BELOW_EQUAL.ordinal)
        assertEquals(7, X86Condition.ABOVE.ordinal)
        assertEquals(8, X86Condition.SIGN.ordinal)
        assertEquals(9, X86Condition.NOT_SIGN.ordinal)
        assertEquals(10, X86Condition.PARITY.ordinal)
        assertEquals(11, X86Condition.NOT_PARITY.ordinal)
        assertEquals(12, X86Condition.LESS.ordinal)
        assertEquals(13, X86Condition.GREATER_EQUAL.ordinal)
        assertEquals(14, X86Condition.LESS_EQUAL.ordinal)
        assertEquals(15, X86Condition.GREATER.ordinal)
    }

    @Test
    fun entryCount() {
        assertEquals(16, X86Condition.entries.size)
    }

    @Test
    fun invertXorOddEvenPairing() {
        for (condition in X86Condition.entries) {
            val inverted = condition.invert()
            assertEquals(condition.ordinal xor 1, inverted.ordinal,
                "$condition.ordinal xor 1 should equal ${inverted}.ordinal")
        }
    }
}

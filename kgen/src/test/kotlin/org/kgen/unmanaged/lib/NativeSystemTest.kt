package org.kgen.unmanaged.lib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeSystemTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Test
    fun currentTimeMillisReturnsReasonableValue() {
        val time = NativeSystem.currentTimeMillis()
        assertTrue(time > 1_000_000_000_000L, "currentTimeMillis should return epoch millis")
    }

    @Test
    fun nanoTimeReturnsPositive() {
        val time = NativeSystem.nanoTime()
        assertTrue(time > 0, "nanoTime should return a positive value")
    }

    @Test
    fun nanoTimeIsMonotonic() {
        val first = NativeSystem.nanoTime()
        val second = NativeSystem.nanoTime()
        assertTrue(second >= first, "nanoTime should be monotonically increasing")
    }

    @Test
    fun identityHashCodeDifferentAddresses() {
        val hash1 = NativeSystem.identityHashCode(0x1000L)
        val hash2 = NativeSystem.identityHashCode(0x2000L)
        assertTrue(hash1 != hash2, "Different addresses should have different hash codes")
    }

    @Test
    fun identityHashCodeConsistent() {
        val hash1 = NativeSystem.identityHashCode(0x12345678L)
        val hash2 = NativeSystem.identityHashCode(0x12345678L)
        assertEquals(hash1, hash2)
    }

    @Test
    fun arraycopyForward() {
        val source = Kgen.malloc(40)
        val destination = Kgen.malloc(40)
        for (index in 0 until 5) {
            Kgen.storeLong(Kgen.offset(source, index * 8), (index + 1).toLong())
        }

        NativeSystem.arraycopy(source, 0, destination, 0, 5)

        for (index in 0 until 5) {
            assertEquals(
                (index + 1).toLong(),
                Kgen.loadLong(Kgen.offset(destination, index * 8))
            )
        }
    }

    @Test
    fun arraycopyWithOffset() {
        val source = Kgen.malloc(40)
        val destination = Kgen.malloc(40)
        for (index in 0 until 5) {
            Kgen.storeLong(Kgen.offset(source, index * 8), (index * 10).toLong())
        }

        NativeSystem.arraycopy(source, 1, destination, 2, 3)

        assertEquals(10L, Kgen.loadLong(Kgen.offset(destination, 2 * 8)))
        assertEquals(20L, Kgen.loadLong(Kgen.offset(destination, 3 * 8)))
        assertEquals(30L, Kgen.loadLong(Kgen.offset(destination, 4 * 8)))
    }

    @Test
    fun arraycopyOverlappingBackward() {
        val buffer = Kgen.malloc(48)
        for (index in 0 until 6) {
            Kgen.storeLong(Kgen.offset(buffer, index * 8), (index + 1).toLong())
        }

        NativeSystem.arraycopy(buffer, 0, buffer, 2, 4)

        assertEquals(1L, Kgen.loadLong(Kgen.offset(buffer, 0 * 8)))
        assertEquals(2L, Kgen.loadLong(Kgen.offset(buffer, 1 * 8)))
        assertEquals(1L, Kgen.loadLong(Kgen.offset(buffer, 2 * 8)))
        assertEquals(2L, Kgen.loadLong(Kgen.offset(buffer, 3 * 8)))
        assertEquals(3L, Kgen.loadLong(Kgen.offset(buffer, 4 * 8)))
        assertEquals(4L, Kgen.loadLong(Kgen.offset(buffer, 5 * 8)))
    }
}

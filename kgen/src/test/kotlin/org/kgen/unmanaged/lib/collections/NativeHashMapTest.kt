package org.kgen.unmanaged.lib.collections

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeHashMapTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Test
    fun newMapIsEmpty() {
        val map = NativeHashMap()
        assertEquals(0, map.size())
        assertTrue(map.isEmpty())
    }

    @Test
    fun putAndGet() {
        val map = NativeHashMap()
        map.put(1, 100)
        map.put(2, 200)
        map.put(3, 300)

        assertEquals(100, map.get(1))
        assertEquals(200, map.get(2))
        assertEquals(300, map.get(3))
        assertEquals(3, map.size())
    }

    @Test
    fun getMissingKeyReturnsZero() {
        val map = NativeHashMap()
        map.put(1, 100)
        assertEquals(0, map.get(999))
    }

    @Test
    fun putOverwritesValue() {
        val map = NativeHashMap()
        map.put(1, 100)
        val oldValue = map.put(1, 999)

        assertEquals(100, oldValue)
        assertEquals(999, map.get(1))
        assertEquals(1, map.size())
    }

    @Test
    fun containsKey() {
        val map = NativeHashMap()
        map.put(42, 1)

        assertTrue(map.containsKey(42))
        assertFalse(map.containsKey(99))
    }

    @Test
    fun remove() {
        val map = NativeHashMap()
        map.put(1, 100)
        map.put(2, 200)

        val removed = map.remove(1)
        assertEquals(100, removed)
        assertEquals(1, map.size())
        assertFalse(map.containsKey(1))
        assertTrue(map.containsKey(2))
    }

    @Test
    fun removeNonExistent() {
        val map = NativeHashMap()
        map.put(1, 100)

        val removed = map.remove(999)
        assertEquals(0, removed)
        assertEquals(1, map.size())
    }

    @Test
    fun clear() {
        val map = NativeHashMap()
        map.put(1, 100)
        map.put(2, 200)
        map.put(3, 300)

        map.clear()
        assertEquals(0, map.size())
        assertTrue(map.isEmpty())
        assertFalse(map.containsKey(1))
    }

    @Test
    fun manyEntries() {
        val map = NativeHashMap()
        for (i in 0L until 100L) {
            map.put(i, i * 10)
        }
        assertEquals(100, map.size())
        for (i in 0L until 100L) {
            assertEquals(i * 10, map.get(i))
        }
    }

    @Test
    fun negativeKeys() {
        val map = NativeHashMap()
        map.put(-1, 100)
        map.put(-999, 200)

        assertEquals(100, map.get(-1))
        assertEquals(200, map.get(-999))
    }

    @Test
    fun zeroKey() {
        val map = NativeHashMap()
        map.put(0, 42)

        assertEquals(42, map.get(0))
        assertTrue(map.containsKey(0))
    }

    @Test
    fun putAfterClear() {
        val map = NativeHashMap()
        map.put(1, 100)
        map.clear()
        map.put(2, 200)

        assertEquals(1, map.size())
        assertEquals(200, map.get(2))
        assertFalse(map.containsKey(1))
    }

    @Test
    fun destroy() {
        val map = NativeHashMap()
        map.put(1, 100)
        map.destroy()
        assertEquals(0, map.buckets)
    }
}

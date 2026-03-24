package org.kgen.unmanaged.lib.collections

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeHashSetTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Test
    fun newSetIsEmpty() {
        val set = NativeHashSet()
        assertEquals(0, set.size())
        assertTrue(set.isEmpty())
    }

    @Test
    fun addElement() {
        val set = NativeHashSet()
        val added = set.add(42)

        assertTrue(added)
        assertEquals(1, set.size())
        assertTrue(set.contains(42))
    }

    @Test
    fun addDuplicateReturnsFalse() {
        val set = NativeHashSet()
        set.add(42)
        val addedAgain = set.add(42)

        assertFalse(addedAgain)
        assertEquals(1, set.size())
    }

    @Test
    fun containsMissingElement() {
        val set = NativeHashSet()
        set.add(42)

        assertFalse(set.contains(99))
    }

    @Test
    fun removeExistingElement() {
        val set = NativeHashSet()
        set.add(42)
        set.add(99)

        val removed = set.remove(42)
        assertTrue(removed)
        assertEquals(1, set.size())
        assertFalse(set.contains(42))
        assertTrue(set.contains(99))
    }

    @Test
    fun removeNonExistentElement() {
        val set = NativeHashSet()
        set.add(42)

        val removed = set.remove(999)
        assertFalse(removed)
        assertEquals(1, set.size())
    }

    @Test
    fun clear() {
        val set = NativeHashSet()
        set.add(1)
        set.add(2)
        set.add(3)

        set.clear()
        assertEquals(0, set.size())
        assertTrue(set.isEmpty())
        assertFalse(set.contains(1))
    }

    @Test
    fun manyElements() {
        val set = NativeHashSet()
        for (element in 0L until 100L) {
            assertTrue(set.add(element))
        }
        assertEquals(100, set.size())
        for (element in 0L until 100L) {
            assertTrue(set.contains(element))
        }
    }

    @Test
    fun addAfterClear() {
        val set = NativeHashSet()
        set.add(1)
        set.add(2)
        set.clear()
        set.add(99)

        assertEquals(1, set.size())
        assertTrue(set.contains(99))
        assertFalse(set.contains(1))
    }

    @Test
    fun negativeElements() {
        val set = NativeHashSet()
        set.add(-1)
        set.add(-999)

        assertTrue(set.contains(-1))
        assertTrue(set.contains(-999))
        assertEquals(2, set.size())
    }

    @Test
    fun zeroElement() {
        val set = NativeHashSet()
        set.add(0)

        assertTrue(set.contains(0))
        assertEquals(1, set.size())
    }

    @Test
    fun notEmptyAfterAdd() {
        val set = NativeHashSet()
        set.add(1)
        assertFalse(set.isEmpty())
    }

    @Test
    fun destroy() {
        val set = NativeHashSet()
        set.add(1)
        set.add(2)
        set.destroy()
        assertEquals(0, set.map.buckets)
    }
}

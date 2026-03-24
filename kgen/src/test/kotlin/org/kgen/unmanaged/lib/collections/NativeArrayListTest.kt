package org.kgen.unmanaged.lib.collections

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeArrayListTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Test
    fun newListIsEmpty() {
        val list = NativeArrayList()
        assertEquals(0, list.size())
        assertTrue(list.isEmpty())
    }

    @Test
    fun addAndGet() {
        val list = NativeArrayList()
        list.add(42)
        list.add(99)
        list.add(7)

        assertEquals(3, list.size())
        assertEquals(42, list.get(0))
        assertEquals(99, list.get(1))
        assertEquals(7, list.get(2))
    }

    @Test
    fun setElement() {
        val list = NativeArrayList()
        list.add(10)
        list.add(20)

        list.set(1, 99)
        assertEquals(99, list.get(1))
        assertEquals(10, list.get(0))
    }

    @Test
    fun removeAt() {
        val list = NativeArrayList()
        list.add(10)
        list.add(20)
        list.add(30)

        val removed = list.removeAt(1)
        assertEquals(20, removed)
        assertEquals(2, list.size())
        assertEquals(10, list.get(0))
        assertEquals(30, list.get(1))
    }

    @Test
    fun removeFirst() {
        val list = NativeArrayList()
        list.add(10)
        list.add(20)
        list.add(30)

        list.removeAt(0)
        assertEquals(2, list.size())
        assertEquals(20, list.get(0))
        assertEquals(30, list.get(1))
    }

    @Test
    fun removeLast() {
        val list = NativeArrayList()
        list.add(10)
        list.add(20)

        val removed = list.removeAt(1)
        assertEquals(20, removed)
        assertEquals(1, list.size())
        assertEquals(10, list.get(0))
    }

    @Test
    fun contains() {
        val list = NativeArrayList()
        list.add(42)
        list.add(99)

        assertTrue(list.contains(42))
        assertTrue(list.contains(99))
        assertFalse(list.contains(7))
    }

    @Test
    fun indexOf() {
        val list = NativeArrayList()
        list.add(10)
        list.add(20)
        list.add(30)

        assertEquals(0, list.indexOf(10))
        assertEquals(1, list.indexOf(20))
        assertEquals(2, list.indexOf(30))
        assertEquals(-1, list.indexOf(99))
    }

    @Test
    fun clear() {
        val list = NativeArrayList()
        list.add(1)
        list.add(2)
        list.add(3)

        list.clear()
        assertEquals(0, list.size())
        assertTrue(list.isEmpty())
    }

    @Test
    fun growsBeyondInitialCapacity() {
        val list = NativeArrayList()
        for (i in 0 until 100) {
            list.add(i.toLong())
        }
        assertEquals(100, list.size())
        for (i in 0 until 100) {
            assertEquals(i.toLong(), list.get(i))
        }
    }

    @Test
    fun addAfterClear() {
        val list = NativeArrayList()
        list.add(1)
        list.add(2)
        list.clear()
        list.add(99)

        assertEquals(1, list.size())
        assertEquals(99, list.get(0))
    }

    @Test
    fun destroyFreesMemory() {
        val list = NativeArrayList()
        list.add(1)
        list.destroy()
        assertEquals(0, list.data)
    }

    @Test
    fun notEmptyAfterAdd() {
        val list = NativeArrayList()
        list.add(1)
        assertFalse(list.isEmpty())
    }
}

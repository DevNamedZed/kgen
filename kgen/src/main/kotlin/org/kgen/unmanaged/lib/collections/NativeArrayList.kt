package org.kgen.unmanaged.lib.collections

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenDestructor
import org.kgen.unmanaged.KgenNative

/**
 * Native dynamic array. Works on JVM (for testing) and compiles to native machine code.
 *
 * Elements are i64 values (pointers or boxed primitives). Growth factor 2x.
 * Backed by malloc/realloc/free.
 *
 * ```kotlin
 * val list = NativeArrayList()
 * list.add(42)
 * list.add(99)
 * assertEquals(2, list.size())
 * assertEquals(42, list.get(0))
 * ```
 */
@KgenNative
class NativeArrayList : AutoCloseable {
    var data: Long = 0
    var size: Int = 0
    var capacity: Int = 0

    fun add(element: Long) {
        if (size >= capacity) {
            grow()
        }
        Kgen.storeLong(Kgen.offset(data, size * ELEMENT_SIZE), element)
        size++
    }

    fun get(index: Int): Long {
        return Kgen.loadLong(Kgen.offset(data, index * ELEMENT_SIZE))
    }

    fun set(index: Int, element: Long) {
        Kgen.storeLong(Kgen.offset(data, index * ELEMENT_SIZE), element)
    }

    fun removeAt(index: Int): Long {
        val removed = get(index)
        var i = index
        while (i < size - 1) {
            set(i, get(i + 1))
            i++
        }
        size--
        return removed
    }

    fun size(): Int = size

    fun isEmpty(): Boolean = size == 0

    fun contains(element: Long): Boolean {
        var i = 0
        while (i < size) {
            if (get(i) == element) {
                return true
            }
            i++
        }
        return false
    }

    fun indexOf(element: Long): Int {
        var i = 0
        while (i < size) {
            if (get(i) == element) {
                return i
            }
            i++
        }
        return -1
    }

    fun clear() {
        size = 0
    }

    @KgenDestructor
    fun destroy() {
        if (data != 0L) {
            Kgen.free(data)
            data = 0
        }
    }

    override fun close() {
        destroy()
    }

    private fun grow() {
        val newCapacity = if (capacity == 0) INITIAL_CAPACITY else capacity * 2
        data = Kgen.realloc(data, newCapacity.toLong() * ELEMENT_SIZE)
        capacity = newCapacity
    }

    companion object {
        private const val ELEMENT_SIZE = 8
        private const val INITIAL_CAPACITY = 8
    }
}

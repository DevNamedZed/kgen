package org.kgen.unmanaged.lib.collections

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenDestructor
import org.kgen.unmanaged.KgenNative

/**
 * Native hash set. Thin wrapper around [NativeHashMap] with dummy values.
 *
 * ```kotlin
 * val set = NativeHashSet()
 * set.add(42)
 * set.add(99)
 * assertTrue(set.contains(42))
 * assertEquals(2, set.size())
 * ```
 */
@KgenNative
class NativeHashSet : AutoCloseable {
    var map: NativeHashMap = NativeHashMap()

    fun add(element: Long): Boolean {
        if (map.containsKey(element)) {
            return false
        }
        map.put(element, 1)
        return true
    }

    fun contains(element: Long): Boolean = map.containsKey(element)

    fun remove(element: Long): Boolean {
        if (!map.containsKey(element)) {
            return false
        }
        map.remove(element)
        return true
    }

    fun size(): Int = map.size()

    fun isEmpty(): Boolean = map.isEmpty()

    fun clear() {
        map.clear()
    }

    @KgenDestructor
    fun destroy() {
        map.destroy()
    }

    override fun close() {
        destroy()
    }
}

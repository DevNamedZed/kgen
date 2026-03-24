package org.kgen.unmanaged.lib.collections

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenDestructor
import org.kgen.unmanaged.KgenNative

/**
 * Native hash map. Works on JVM (for testing) and compiles to native machine code.
 *
 * Keys and values are i64. Separate chaining with auto-rehash at 0.75 load factor.
 *
 * ```kotlin
 * val map = NativeHashMap()
 * map.put(1, 100)
 * map.put(2, 200)
 * assertEquals(100, map.get(1))
 * assertEquals(2, map.size())
 * ```
 */
@KgenNative
class NativeHashMap : AutoCloseable {
    var buckets: Long = 0
    var bucketCount: Int = 0
    var size: Int = 0

    init {
        bucketCount = INITIAL_BUCKETS
        buckets = Kgen.malloc(bucketCount.toLong() * BUCKET_SIZE)
        var i = 0
        while (i < bucketCount) {
            Kgen.storeLong(Kgen.offset(buckets, i * BUCKET_SIZE), 0)
            i++
        }
    }

    fun put(key: Long, value: Long): Long {
        val hash = hashKey(key)
        val bucketIndex = (hash and 0x7FFFFFFF) % bucketCount
        val bucketPtr = Kgen.offset(buckets, bucketIndex * BUCKET_SIZE)

        var entry = Kgen.loadLong(bucketPtr)
        while (entry != 0L) {
            if (Kgen.loadLong(entry) == key) {
                val oldValue = Kgen.loadLong(Kgen.offset(entry, VALUE_OFFSET))
                Kgen.storeLong(Kgen.offset(entry, VALUE_OFFSET), value)
                return oldValue
            }
            entry = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
        }

        val newEntry = Kgen.malloc(ENTRY_SIZE.toLong())
        Kgen.storeLong(newEntry, key)
        Kgen.storeLong(Kgen.offset(newEntry, VALUE_OFFSET), value)
        Kgen.storeInt(Kgen.offset(newEntry, HASH_OFFSET), hash)
        Kgen.storeLong(Kgen.offset(newEntry, NEXT_OFFSET), Kgen.loadLong(bucketPtr))
        Kgen.storeLong(bucketPtr, newEntry)

        size++

        if (size * 100 > bucketCount * LOAD_FACTOR_PERCENT) {
            rehash()
        }

        return 0
    }

    fun get(key: Long): Long {
        val hash = hashKey(key)
        val bucketIndex = (hash and 0x7FFFFFFF) % bucketCount
        var entry = Kgen.loadLong(Kgen.offset(buckets, bucketIndex * BUCKET_SIZE))

        while (entry != 0L) {
            if (Kgen.loadLong(entry) == key) {
                return Kgen.loadLong(Kgen.offset(entry, VALUE_OFFSET))
            }
            entry = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
        }
        return 0
    }

    fun containsKey(key: Long): Boolean {
        val hash = hashKey(key)
        val bucketIndex = (hash and 0x7FFFFFFF) % bucketCount
        var entry = Kgen.loadLong(Kgen.offset(buckets, bucketIndex * BUCKET_SIZE))

        while (entry != 0L) {
            if (Kgen.loadLong(entry) == key) {
                return true
            }
            entry = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
        }
        return false
    }

    fun remove(key: Long): Long {
        val hash = hashKey(key)
        val bucketIndex = (hash and 0x7FFFFFFF) % bucketCount
        val bucketPtr = Kgen.offset(buckets, bucketIndex * BUCKET_SIZE)

        var prev = 0L
        var entry = Kgen.loadLong(bucketPtr)
        while (entry != 0L) {
            if (Kgen.loadLong(entry) == key) {
                val value = Kgen.loadLong(Kgen.offset(entry, VALUE_OFFSET))
                val next = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
                if (prev == 0L) {
                    Kgen.storeLong(bucketPtr, next)
                } else {
                    Kgen.storeLong(Kgen.offset(prev, NEXT_OFFSET), next)
                }
                Kgen.free(entry)
                size--
                return value
            }
            prev = entry
            entry = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
        }
        return 0
    }

    fun size(): Int = size

    fun isEmpty(): Boolean = size == 0

    fun clear() {
        var i = 0
        while (i < bucketCount) {
            var entry = Kgen.loadLong(Kgen.offset(buckets, i * BUCKET_SIZE))
            while (entry != 0L) {
                val next = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
                Kgen.free(entry)
                entry = next
            }
            Kgen.storeLong(Kgen.offset(buckets, i * BUCKET_SIZE), 0)
            i++
        }
        size = 0
    }

    @KgenDestructor
    fun destroy() {
        clear()
        if (buckets != 0L) {
            Kgen.free(buckets)
            buckets = 0
        }
    }

    override fun close() {
        destroy()
    }

    private fun hashKey(key: Long): Int {
        val h = (key xor (key ushr 32)).toInt()
        return h xor (h ushr 16)
    }

    private fun rehash() {
        val oldBuckets = buckets
        val oldBucketCount = bucketCount
        val newBucketCount = oldBucketCount * 2
        val newBuckets = Kgen.malloc(newBucketCount.toLong() * BUCKET_SIZE)

        var i = 0
        while (i < newBucketCount) {
            Kgen.storeLong(Kgen.offset(newBuckets, i * BUCKET_SIZE), 0)
            i++
        }

        i = 0
        while (i < oldBucketCount) {
            var entry = Kgen.loadLong(Kgen.offset(oldBuckets, i * BUCKET_SIZE))
            while (entry != 0L) {
                val next = Kgen.loadLong(Kgen.offset(entry, NEXT_OFFSET))
                val hash = Kgen.loadInt(Kgen.offset(entry, HASH_OFFSET))
                val newIndex = (hash and 0x7FFFFFFF) % newBucketCount
                val newBucketPtr = Kgen.offset(newBuckets, newIndex * BUCKET_SIZE)
                Kgen.storeLong(Kgen.offset(entry, NEXT_OFFSET), Kgen.loadLong(newBucketPtr))
                Kgen.storeLong(newBucketPtr, entry)
                entry = next
            }
            i++
        }

        Kgen.free(oldBuckets)
        buckets = newBuckets
        bucketCount = newBucketCount
    }

    companion object {
        private const val BUCKET_SIZE = 8
        private const val INITIAL_BUCKETS = 16
        private const val LOAD_FACTOR_PERCENT = 75

        private const val VALUE_OFFSET = 8
        private const val HASH_OFFSET = 16
        private const val NEXT_OFFSET = 24
        private const val ENTRY_SIZE = 32
    }
}

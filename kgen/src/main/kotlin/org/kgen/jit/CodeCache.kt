package org.kgen.jit

/**
 * Manages executable memory for JIT-compiled code with a size limit and LRU eviction.
 *
 * When the total allocated code exceeds [maxSize], the least-recently-used modules
 * are evicted until enough space is freed. Eviction removes the module from the
 * engine's global symbol table and frees its executable memory.
 *
 * ```java
 * var cache = new CodeCache(64 * 1024 * 1024); // 64 MB
 * var jit = new JitEngine(generator);
 * jit.setCodeCache(cache);
 *
 * jit.addModule(module1); // tracked by cache
 * jit.addModule(module2); // may evict module1 if cache is full
 *
 * System.out.println(cache.totalSize());    // bytes used
 * System.out.println(cache.entryCount());   // modules in cache
 * ```
 */
class CodeCache(
    /** Maximum total code size in bytes. 0 means unlimited. */
    val maxSize: Long = 0,
) {
    private val entries = LinkedHashMap<String, CacheEntry>(16, 0.75f, true) // access-ordered

    internal data class CacheEntry(
        val moduleName: String,
        val size: Long,
        val module: JitModule,
    )

    /** Total bytes currently in the cache. */
    fun totalSize(): Long = entries.values.sumOf { it.size }

    /** Number of modules in the cache. */
    fun entryCount(): Int = entries.size

    /** Whether a module with the given name exists in the cache. */
    fun contains(name: String): Boolean = entries.containsKey(name)

    /**
     * Record a module in the cache. If adding this module would exceed [maxSize],
     * evicts least-recently-used modules first. Returns the list of evicted module names.
     */
    internal fun track(module: JitModule): List<String> {
        val size = module.memory.size
        val evicted = mutableListOf<String>()

        if (maxSize > 0) {
            val needed = totalSize() + size - maxSize
            if (needed > 0) {
                evicted.addAll(evictBytes(needed, exclude = module.name))
            }
        }

        entries[module.name] = CacheEntry(module.name, size, module)
        return evicted
    }

    /**
     * Record an access to a module (updates LRU order).
     */
    internal fun touch(name: String) {
        entries[name] // LinkedHashMap with accessOrder=true updates on get
    }

    /**
     * Remove a module from the cache (called on explicit removal, not eviction).
     */
    internal fun remove(name: String) {
        entries.remove(name)
    }

    /**
     * Evict enough entries to free at least [bytes] bytes.
     * Returns the names of evicted modules.
     */
    private fun evictBytes(bytes: Long, exclude: String): List<String> {
        val evicted = mutableListOf<String>()
        var freed = 0L
        val iterator = entries.iterator()
        while (iterator.hasNext() && freed < bytes) {
            val entry = iterator.next()
            if (entry.key == exclude) continue
            freed += entry.value.size
            evicted.add(entry.key)
            iterator.remove()
        }
        return evicted
    }

    /** Clear the cache. Does NOT free module memory — the engine handles that. */
    internal fun clear() {
        entries.clear()
    }
}

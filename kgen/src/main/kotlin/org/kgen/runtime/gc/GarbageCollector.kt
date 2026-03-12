package org.kgen.runtime.gc

import org.kgen.ir.StackMap

/**
 * Garbage collector interface. Implementations trace live objects from roots,
 * reclaim dead objects, and optionally compact the heap.
 *
 * ```java
 * GarbageCollector gc = GarbageCollector.markSweep(heap);
 * gc.addRootProvider(stack::liveReferences);
 * gc.collect(); // stop-the-world collection
 * ```
 */
interface GarbageCollector {

    /** Register a root provider. Called during collection to find GC roots. */
    fun addRootProvider(provider: RootProvider)

    /** Register stack maps for a compiled function. */
    fun registerStackMap(stackMap: StackMap)

    /** Trigger a garbage collection. */
    fun collect()

    /** Number of collections performed so far. */
    fun collectionCount(): Long

    /** Total bytes reclaimed by all collections. */
    fun bytesReclaimed(): Long

    /** Write barrier: notifies the GC that a reference field was written. */
    fun writeBarrier(obj: Long, fieldIndex: Int, value: Long) {}

    /** Read barrier: allows the GC to intercept/relocate a reference read. Returns the forwarded address. */
    fun readBarrier(ref: Long): Long = ref
}

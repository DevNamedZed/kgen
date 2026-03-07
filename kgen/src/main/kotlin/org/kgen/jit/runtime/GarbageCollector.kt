package org.kgen.jit.runtime

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
}

/**
 * Provides GC roots — live object references that the collector must not reclaim.
 * Roots come from the stack, globals, and pinned handles.
 */
fun interface RootProvider {
    /** Enumerate all live references. The visitor receives each root address. */
    fun visitRoots(visitor: RootVisitor)
}

/**
 * Callback for visiting GC root addresses.
 */
fun interface RootVisitor {
    fun visitRoot(address: Long)
}

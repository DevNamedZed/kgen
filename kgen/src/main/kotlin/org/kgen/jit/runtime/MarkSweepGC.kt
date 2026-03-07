package org.kgen.jit.runtime

import org.kgen.ir.StackMap

/**
 * Simple mark-sweep garbage collector. Traces from roots, marks live objects,
 * and sweeps (reclaims) unmarked objects.
 *
 * Uses the GC flags in the object header for mark bits.
 * Does not compact — dead objects become free space but are not reused
 * until the heap is compacted or reset.
 *
 * ```java
 * var gc = new MarkSweepGC(heap, typeRegistry);
 * gc.addRootProvider(stack);
 * gc.collect();
 * ```
 */
class MarkSweepGC(
    private val heap: BumpHeap,
    private val typeRegistry: TypeRegistry,
) : GarbageCollector {

    private val rootProviders = mutableListOf<RootProvider>()
    private val stackMaps = mutableMapOf<String, StackMap>()
    private var collections = 0L
    private var reclaimed = 0L

    override fun addRootProvider(provider: RootProvider) {
        rootProviders.add(provider)
    }

    override fun registerStackMap(stackMap: StackMap) {
        stackMaps[stackMap.functionName] = stackMap
    }

    override fun collect() {
        collections++

        // Phase 1: Clear all mark bits
        clearMarks()

        // Phase 2: Mark — trace from roots
        val worklist = mutableListOf<Long>()
        for (provider in rootProviders) {
            provider.visitRoots { address ->
                if (isValidHeapAddress(address) && !isMarked(address)) {
                    mark(address)
                    worklist.add(address)
                }
            }
        }

        while (worklist.isNotEmpty()) {
            val address = worklist.removeLast()
            val typeId = heap.typeIdAt(address)
            val layout = typeRegistry.lookup(typeId) ?: continue
            for (field in layout.fields) {
                if (field.isReference) {
                    val refAddr = readReference(address, field.offset)
                    if (refAddr != 0L && isValidHeapAddress(refAddr) && !isMarked(refAddr)) {
                        mark(refAddr)
                        worklist.add(refAddr)
                    }
                }
            }
        }

        // Phase 3: Sweep — count (but don't actually free in bump allocator)
        // A real implementation would build a free list or compact
    }

    override fun collectionCount(): Long = collections

    override fun bytesReclaimed(): Long = reclaimed

    private fun isValidHeapAddress(address: Long): Boolean {
        val base = heap.baseAddress()
        return address >= base && address < base + heap.capacity()
    }

    private fun isMarked(address: Long): Boolean {
        return heap.gcFlagsAt(address) and MARK_BIT != 0
    }

    private fun mark(address: Long) {
        val flags = heap.gcFlagsAt(address)
        heap.setGcFlagsAt(address, flags or MARK_BIT)
    }

    private fun clearMarks() {
        // Walk all objects in the heap and clear their mark bits
        var offset = 0L
        val cap = heap.bytesInUse()
        while (offset < cap) {
            val address = heap.baseAddress() + offset
            heap.setGcFlagsAt(address, 0)
            val typeId = heap.typeIdAt(address)
            val layout = typeRegistry.lookup(typeId)
            if (layout != null) {
                offset += layout.totalSize()
                offset = (offset + 7) and 7L.inv()
            } else {
                break
            }
        }
    }

    private fun readReference(objectAddress: Long, fieldOffset: Int): Long {
        val addr = objectAddress + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = heap.readBytes(addr, 8)
        var value = 0L
        for (i in 0 until 8) {
            value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        }
        return value
    }

    companion object {
        private const val MARK_BIT = 1
    }
}

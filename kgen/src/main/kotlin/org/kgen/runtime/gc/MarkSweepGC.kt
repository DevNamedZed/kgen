package org.kgen.runtime.gc

import org.kgen.runtime.exec.ExecutionContext
import org.kgen.ir.StackMap
import org.kgen.ir.StackMapEntry
import org.kgen.ir.StackMapLocation

/**
 * Simple mark-sweep garbage collector. Traces from roots, marks live objects,
 * and sweeps (reclaims) unmarked objects.
 *
 * Uses the GC flags in the object header for mark bits.
 * Sweep phase adds dead objects to the heap's free list for reuse.
 * Does not compact — free blocks may be fragmented.
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
    private val executionContexts = mutableListOf<ExecutionContext>()
    private val finalizers = mutableMapOf<Int, Finalizer>()
    private var collections = 0L
    private var reclaimed = 0L

    override fun addRootProvider(provider: RootProvider) {
        rootProviders.add(provider)
    }

    override fun registerStackMap(stackMap: StackMap) {
        stackMaps[stackMap.functionName] = stackMap
    }

    /**
     * Register an execution context for stack-map-based root scanning.
     * During collection, the GC walks each context's stack frames and uses
     * the corresponding stack maps to find GC roots in registers and stack slots.
     */
    fun addExecutionContext(context: ExecutionContext) {
        executionContexts.add(context)
    }

    /**
     * Remove a previously registered execution context.
     */
    fun removeExecutionContext(context: ExecutionContext) {
        executionContexts.remove(context)
    }

    /** All registered stack maps (for testing/inspection). */
    fun stackMaps(): Map<String, StackMap> = stackMaps.toMap()

    /** Number of registered execution contexts. */
    fun executionContextCount(): Int = executionContexts.size

    override fun collect() {
        collections++
        markPhase()
        sweep()
    }

    /**
     * Collect and compact: mark live objects, compact them forward, and update
     * all root and field references. Returns the forwarding map (old → new address).
     */
    fun compactAndForward(mutableRoots: MutableList<Long>): Map<Long, Long> {
        collections++
        markPhase()

        val forwarding = heap.compact(typeRegistry)
        if (forwarding.isEmpty()) return forwarding

        updateRoots(mutableRoots, forwarding)
        updateFieldReferences(forwarding)

        return forwarding
    }

    private fun markPhase() {
        clearMarks()

        val worklist = mutableListOf<Long>()

        for (provider in rootProviders) {
            provider.visitRoots { address ->
                if (isValidHeapAddress(address) && !isMarked(address)) {
                    mark(address)
                    worklist.add(address)
                }
            }
        }

        for (ctx in executionContexts) {
            scanStackRoots(ctx) { address ->
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
    }

    private fun updateRoots(mutableRoots: MutableList<Long>, forwarding: Map<Long, Long>) {
        for (i in mutableRoots.indices) {
            val forwarded = forwarding[mutableRoots[i]]
            if (forwarded != null) {
                mutableRoots[i] = forwarded
            }
        }
    }

    private fun updateFieldReferences(forwarding: Map<Long, Long>) {
        var offset = 0L
        val cap = heap.bytesInUse()
        while (offset < cap) {
            val address = heap.baseAddress() + offset
            val typeId = heap.typeIdAt(address)
            if (typeId == 0) {
                offset += 8
                continue
            }
            val layout = typeRegistry.lookup(typeId) ?: break
            for (field in layout.fields) {
                if (field.isReference) {
                    val refAddr = readReference(address, field.offset)
                    if (refAddr != 0L) {
                        val forwarded = forwarding[refAddr]
                        if (forwarded != null) {
                            writeReference(address, field.offset, forwarded)
                        }
                    }
                }
            }
            val totalSize = layout.totalSize().toLong()
            offset += (totalSize + 7) and 7L.inv()
        }
    }

    override fun collectionCount(): Long = collections

    override fun bytesReclaimed(): Long = reclaimed

    override fun registerFinalizer(typeId: Int, finalizer: Finalizer) {
        finalizers[typeId] = finalizer
    }

    override fun writeBarrier(obj: Long, fieldIndex: Int, value: Long) {
        // Mark-sweep does not need an incremental write barrier.
        // A generational or concurrent collector would record the reference here.
    }

    override fun readBarrier(ref: Long): Long {
        // Mark-sweep does not relocate objects, so the address is always valid.
        return ref
    }

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
            val typeId = heap.typeIdAt(address)
            if (typeId == 0) {
                // Dead object from previous sweep — skip past it
                // We don't know the size, so advance by minimum alignment
                offset += 8
                continue
            }
            heap.setGcFlagsAt(address, 0)
            val layout = typeRegistry.lookup(typeId)
            if (layout != null) {
                offset += layout.totalSize()
                offset = (offset + 7) and 7L.inv()
            } else {
                break
            }
        }
    }

    private fun sweep() {
        var offset = 0L
        val cap = heap.bytesInUse()
        while (offset < cap) {
            val address = heap.baseAddress() + offset
            val typeId = heap.typeIdAt(address)
            if (typeId == 0) {
                // Already dead from previous sweep — skip
                offset += 8
                continue
            }
            val layout = typeRegistry.lookup(typeId) ?: break
            val totalSize = layout.totalSize().toLong()
            val alignedSize = (totalSize + 7) and 7L.inv()

            if (!isMarked(address)) {
                val finalizer = finalizers[typeId]
                if (finalizer != null) {
                    finalizer.finalize(address)
                }
                heap.addFreeBlock(address, alignedSize)
                reclaimed += alignedSize
            }

            offset += totalSize
            offset = (offset + 7) and 7L.inv()
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

    private fun writeReference(objectAddress: Long, fieldOffset: Int, value: Long) {
        val addr = objectAddress + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = ByteArray(8)
        for (i in 0 until 8) bytes[i] = (value shr (i * 8)).toByte()
        heap.writeBytes(addr, bytes)
    }

    /**
     * Walk an execution context's stack frames and use registered stack maps
     * to find GC root locations. For each frame, if we have a stack map for
     * that function, we report all locations from its entries as potential roots.
     *
     * In a real implementation, we would match the return address against
     * specific safepoint offsets in the stack map. Here we conservatively
     * report all locations from all entries in the function's stack map.
     */
    private fun scanStackRoots(context: ExecutionContext, visitor: (Long) -> Unit) {
        context.walkStack { frame ->
            val stackMap = stackMaps[frame.functionName] ?: return@walkStack
            for (entry in stackMap.entries) {
                for (location in entry.locations) {
                    when (location) {
                        is StackMapLocation.Constant -> {
                            if (location.value != 0L) {
                                visitor(location.value)
                            }
                        }
                        is StackMapLocation.Register -> {
                            // Read the register value from the safepoint register save area.
                            // The safepoint stub saves all GP registers into a buffer;
                            // registerSaveArea points to that buffer. Each slot is 8 bytes.
                            if (frame.registerSaveArea != 0L) {
                                val value = readNativeWord(
                                    frame.registerSaveArea + location.registerIndex.toLong() * 8
                                )
                                if (value != 0L) visitor(value)
                            }
                        }
                        is StackMapLocation.Stack -> {
                            // Read from the native stack using the frame's base pointer.
                            // rbpOffset is relative to RBP (frame pointer).
                            if (frame.basePointer != 0L) {
                                val value = readNativeWord(
                                    frame.basePointer + location.rbpOffset.toLong()
                                )
                                if (value != 0L) visitor(value)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Read a 64-bit word from native memory at the given address.
     * Uses FFM MemorySegment for safe, bounds-checked access.
     */
    private fun readNativeWord(address: Long): Long {
        return try {
            val segment = java.lang.foreign.MemorySegment.ofAddress(address)
                .reinterpret(8)
            segment.get(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, 0)
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val MARK_BIT = 1
    }
}

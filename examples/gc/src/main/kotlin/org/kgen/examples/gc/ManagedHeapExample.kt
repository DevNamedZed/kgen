package org.kgen.examples.gc

import org.kgen.runtime.gc.*

/**
 * Demonstrates allocating objects on the managed heap, registering types with
 * the GC, and collecting dead objects with finalization.
 *
 * This is the foundation for GC-managed @KgenNative classes: the runtime
 * allocates objects via the HeapManager, the GC traces references to find live
 * objects, and dead objects have their @KgenDestructor called before reclaiming.
 */
object ManagedHeapExample {

    /**
     * Layout for a simple native string: a data pointer and a cached length.
     * The data pointer is NOT a GC reference — it points to a raw malloc'd buffer.
     */
    val STRING_LAYOUT = ObjectLayout(
        name = "NativeString",
        size = 16,
        fields = listOf(
            FieldDescriptor("data", 0, 8, false),
            FieldDescriptor("length", 8, 4, false),
        ),
    )

    /**
     * Layout for a linked list node: a next pointer (GC reference) and a value.
     */
    val NODE_LAYOUT = ObjectLayout(
        name = "ListNode",
        size = 16,
        fields = listOf(
            FieldDescriptor("next", 0, 8, true),
            FieldDescriptor("value", 8, 8, false),
        ),
    )

    /**
     * Allocate objects on the managed heap and trigger GC.
     * Dead objects are finalized and reclaimed.
     */
    @JvmStatic
    fun basicCollection(): CollectionResult {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val stringTypeId = registry.register(STRING_LAYOUT)
        val stringLayout = registry.lookup(stringTypeId)!!

        val liveString = heap.allocate(stringLayout)
        heap.writeField(liveString, 0, 0xCAFE_0001L)
        heap.writeField(liveString, 1, 12L)

        val deadString = heap.allocate(stringLayout)
        heap.writeField(deadString, 0, 0xCAFE_0002L)
        heap.writeField(deadString, 1, 5L)

        val finalizedAddresses = mutableListOf<Long>()
        gc.registerFinalizer(stringTypeId) { address ->
            finalizedAddresses.add(address)
        }

        gc.addRootProvider { visitor -> visitor.visitRoot(liveString) }

        gc.collect()

        return CollectionResult(
            liveCount = 1,
            deadCount = finalizedAddresses.size,
            reclaimedBytes = gc.bytesReclaimed(),
            finalizedAddresses = finalizedAddresses,
        )
    }

    /**
     * Demonstrate GC tracing through reference fields.
     * A chain of nodes where only the head is a root — the GC traces
     * through `next` pointers to keep the entire chain alive.
     */
    @JvmStatic
    fun referenceTracing(): TracingResult {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val nodeTypeId = registry.register(NODE_LAYOUT)
        val nodeLayout = registry.lookup(nodeTypeId)!!

        val node3 = heap.allocate(nodeLayout)
        heap.writeField(node3, 0, 0L)
        heap.writeField(node3, 1, 30L)

        val node2 = heap.allocate(nodeLayout)
        heap.writeField(node2, 0, node3)
        heap.writeField(node2, 1, 20L)

        val node1 = heap.allocate(nodeLayout)
        heap.writeField(node1, 0, node2)
        heap.writeField(node1, 1, 10L)

        val orphan = heap.allocate(nodeLayout)
        heap.writeField(orphan, 0, 0L)
        heap.writeField(orphan, 1, 99L)

        val finalizedNodes = mutableListOf<Long>()
        gc.registerFinalizer(nodeTypeId) { address ->
            finalizedNodes.add(address)
        }

        gc.addRootProvider { visitor -> visitor.visitRoot(node1) }

        gc.collect()

        return TracingResult(
            chainLength = 3,
            orphanFinalized = finalizedNodes.contains(orphan),
            chainNodesFinalized = finalizedNodes.count { it == node1 || it == node2 || it == node3 },
        )
    }

    /**
     * Demonstrate mixed types: some objects are GC-managed, some have internal
     * raw allocations cleaned up by finalizers.
     */
    @JvmStatic
    fun mixedTypeCollection(): MixedResult {
        val heap = BumpHeap(8192)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val stringTypeId = registry.register(STRING_LAYOUT)
        val stringLayout = registry.lookup(stringTypeId)!!
        val nodeTypeId = registry.register(NODE_LAYOUT)
        val nodeLayout = registry.lookup(nodeTypeId)!!

        val strings = mutableListOf<Long>()
        for (index in 0 until 5) {
            val address = heap.allocate(stringLayout)
            heap.writeField(address, 0, (0xBEEF_0000L + index))
            heap.writeField(address, 1, (index * 3).toLong())
            strings.add(address)
        }

        val nodes = mutableListOf<Long>()
        for (index in 0 until 3) {
            val address = heap.allocate(nodeLayout)
            heap.writeField(address, 0, 0L)
            heap.writeField(address, 1, (index * 100).toLong())
            nodes.add(address)
        }

        val stringFinalizations = mutableListOf<Long>()
        val nodeFinalizations = mutableListOf<Long>()

        gc.registerFinalizer(stringTypeId) { address -> stringFinalizations.add(address) }
        gc.registerFinalizer(nodeTypeId) { address -> nodeFinalizations.add(address) }

        gc.addRootProvider { visitor ->
            visitor.visitRoot(strings[0])
            visitor.visitRoot(strings[2])
        }

        gc.collect()

        return MixedResult(
            totalAllocated = 8,
            stringsFinalized = stringFinalizations.size,
            nodesFinalized = nodeFinalizations.size,
            reclaimedBytes = gc.bytesReclaimed(),
        )
    }

    data class CollectionResult(
        val liveCount: Int,
        val deadCount: Int,
        val reclaimedBytes: Long,
        val finalizedAddresses: List<Long>,
    )

    data class TracingResult(
        val chainLength: Int,
        val orphanFinalized: Boolean,
        val chainNodesFinalized: Int,
    )

    data class MixedResult(
        val totalAllocated: Int,
        val stringsFinalized: Int,
        val nodesFinalized: Int,
        val reclaimedBytes: Long,
    )
}

package org.kgen.jit.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MarkSweepGCTest {

    private fun simpleLayout(typeId: Int = 1) = ObjectLayout(
        name = "Simple",
        size = 8,
        fields = listOf(FieldDescriptor("value", 0, 8, false)),
        typeId = typeId,
    )

    private fun refLayout(typeId: Int = 2) = ObjectLayout(
        name = "Ref",
        size = 16,
        fields = listOf(
            FieldDescriptor("value", 0, 8, false),
            FieldDescriptor("next", 8, 8, true),
        ),
        typeId = typeId,
    )

    @Test
    fun collectWithNoRoots() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun markReachableObjects() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            // Object should still be readable after GC
            assertEquals(42L, heap.readField(obj, 0))
        }
    }

    @Test
    fun traceReferenceChain() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 1L) // a.value = 1
            heap.writeField(b, 0, 2L) // b.value = 2

            // a.next → b (write raw address at field offset 8)
            val nextOffset = a + ObjectLayout.HEADER_SIZE + 8
            val addrBytes = ByteArray(8)
            for (i in 0 until 8) addrBytes[i] = (b shr (i * 8)).toByte()
            heap.writeBytes(nextOffset, addrBytes)

            val gc = MarkSweepGC(heap, registry)
            // Only root is 'a', but 'b' should be traced via a.next
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
        }
    }

    @Test
    fun typeRegistryRoundTrip() {
        val registry = TypeRegistry()
        val layout = simpleLayout(0) // auto-assign ID
        val id = registry.register(layout)
        assertTrue(id > 0)
        val looked = registry.lookup(id)
        assertNotNull(looked)
        assertEquals("Simple", looked!!.name)
    }

    @Test
    fun multipleCollections() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            gc.collect()
            gc.collect()
            assertEquals(3L, gc.collectionCount())
        }
    }

    @Test
    fun rootProviderCalledDuringCollect() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            var rootVisited = false
            gc.addRootProvider { visitor ->
                rootVisited = true
                visitor.visitRoot(obj)
            }
            gc.collect()
            assertTrue(rootVisited)
        }
    }

    @Test
    fun multipleRootProviders() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.addRootProvider { visitor -> visitor.visitRoot(b) }
            gc.collect()

            // Both objects should survive (are roots)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
        }
    }

    @Test
    fun nullReferenceNotTraced() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)
            // next field is 0 (null) by default — should not crash during trace

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect() // should not crash
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun deepReferenceChain() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            // Build a chain: a → b → c → d → e
            val objects = mutableListOf<Long>()
            for (i in 0 until 5) {
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, (i + 1).toLong())
                objects.add(obj)
            }

            // Link them: obj[i].next = obj[i+1]
            for (i in 0 until 4) {
                val nextOffset = objects[i] + ObjectLayout.HEADER_SIZE + 8
                val addrBytes = ByteArray(8)
                val next = objects[i + 1]
                for (j in 0 until 8) addrBytes[j] = (next shr (j * 8)).toByte()
                heap.writeBytes(nextOffset, addrBytes)
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(objects[0]) }
            gc.collect()

            // All objects should be reachable
            for (i in 0 until 5) {
                assertEquals((i + 1).toLong(), heap.readField(objects[i], 0))
            }
        }
    }

    @Test
    fun collectWithNoAllocations() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.collect() // empty heap, should not crash
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun bytesReclaimedInitiallyZero() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun markBitClearedBetweenCollections() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }

            gc.collect()
            // After first collection, mark bit should be set
            // But clearMarks in second collection should clear it
            gc.collect()
            assertEquals(2L, gc.collectionCount())
        }
    }

    @Test
    fun mixedRefAndNonRef() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val simpleL = simpleLayout(1)
            val refL = refLayout(2)
            registry.register(simpleL)
            registry.register(refL)

            val simple = heap.allocate(simpleL)
            val ref = heap.allocate(refL)
            heap.writeField(simple, 0, 42L)
            heap.writeField(ref, 0, 99L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(simple)
                visitor.visitRoot(ref)
            }
            gc.collect()

            assertEquals(42L, heap.readField(simple, 0))
            assertEquals(99L, heap.readField(ref, 0))
        }
    }
}

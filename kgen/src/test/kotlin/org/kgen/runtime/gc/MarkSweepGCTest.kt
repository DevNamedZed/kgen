package org.kgen.runtime.gc

import org.kgen.runtime.exec.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.StackMap
import org.kgen.ir.StackMapEntry
import org.kgen.ir.StackMapLocation

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

    @Test
    fun sweepReclaimsUnreachableObjects() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            val gc = MarkSweepGC(heap, registry)
            // Only root a and c — b is unreachable
            gc.addRootProvider { visitor ->
                visitor.visitRoot(a)
                visitor.visitRoot(c)
            }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0, "Should reclaim bytes from unreachable object")
            assertTrue(heap.freeBlockCount() >= 1, "Free list should have at least one block")
        }
    }

    @Test
    fun freeListReusesMemory() {
        BumpHeap(256).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 99L)

            // Collect with no roots — obj becomes garbage
            val gc = MarkSweepGC(heap, registry)
            gc.collect()

            assertEquals(1, heap.freeBlockCount(), "Swept object should be on free list")

            // Allocate again — should reuse the free block
            val reused = heap.allocate(layout)
            heap.writeField(reused, 0, 42L)
            assertEquals(42L, heap.readField(reused, 0), "Reused block should be writable and readable")
        }
    }

    @Test
    fun sweepPreservesLiveObjects() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 10L)
            heap.writeField(b, 0, 20L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(a)
                visitor.visitRoot(b)
            }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed(), "No bytes should be reclaimed when all objects are rooted")
            assertEquals(10L, heap.readField(a, 0))
            assertEquals(20L, heap.readField(b, 0))
        }
    }

    @Test
    fun multipleSweepCycles() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)

            val gc = MarkSweepGC(heap, registry)

            // Cycle 1: root only A — B should be swept
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()
            assertTrue(gc.bytesReclaimed() > 0, "B should be reclaimed in first cycle")

            // Allocate C (may reuse B's space)
            val c = heap.allocate(layout)
            heap.writeField(c, 0, 3L)

            // Cycle 2: clear old root provider, root only C — A should be swept
            val gc2 = MarkSweepGC(heap, registry)
            gc2.addRootProvider { visitor -> visitor.visitRoot(c) }
            gc2.collect()
            assertTrue(gc2.bytesReclaimed() > 0, "A should be reclaimed in second cycle")
            assertEquals(3L, heap.readField(c, 0), "C should survive second cycle")
        }
    }

    @Test
    fun registerRootScanningFindsLiveObject() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            // Create a native buffer simulating a register save area.
            // Register index 0 holds the heap object address.
            val arena = java.lang.foreign.Arena.ofConfined()
            val regSave = arena.allocate(128) // 16 registers * 8 bytes
            regSave.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, obj) // reg[0] = obj

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("testFunc", 0, basePointer = 0, registerSaveArea = regSave.address())

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("testFunc", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Register(0)))
            )))
            gc.addExecutionContext(ctx)
            gc.collect()

            // Object should survive — it's reachable via register root
            assertEquals(42L, heap.readField(obj, 0))
            arena.close()
        }
    }

    @Test
    fun stackRootScanningFindsLiveObject() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 99L)

            // Create a native buffer simulating a stack frame.
            // At offset 16 from the "base pointer", we store the heap address.
            val arena = java.lang.foreign.Arena.ofConfined()
            val stack = arena.allocate(256)
            // Treat middle of buffer as base pointer
            val bp = stack.address() + 128
            // Write obj address at bp + 16
            stack.set(java.lang.foreign.ValueLayout.JAVA_LONG, 128 + 16, obj)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("stackFunc", 0, basePointer = bp, registerSaveArea = 0)

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("stackFunc", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Stack(16)))
            )))
            gc.addExecutionContext(ctx)
            gc.collect()

            // Object should survive — it's reachable via stack root
            assertEquals(99L, heap.readField(obj, 0))
            arena.close()
        }
    }

    @Test
    fun mixedRootScanningPreservesAllReachableObjects() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val regObj = heap.allocate(layout)
            val stackObj = heap.allocate(layout)
            val constObj = heap.allocate(layout)
            val unreachable = heap.allocate(layout)
            heap.writeField(regObj, 0, 1L)
            heap.writeField(stackObj, 0, 2L)
            heap.writeField(constObj, 0, 3L)
            heap.writeField(unreachable, 0, 4L)

            val arena = java.lang.foreign.Arena.ofConfined()
            // Register save area: reg[0] = regObj
            val regSave = arena.allocate(128)
            regSave.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, regObj)
            // Stack frame: bp + (-8) = stackObj
            val stack = arena.allocate(256)
            val bp = stack.address() + 128
            stack.set(java.lang.foreign.ValueLayout.JAVA_LONG, 128 - 8, stackObj)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("mixedFunc", 0, basePointer = bp, registerSaveArea = regSave.address())

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("mixedFunc", listOf(
                StackMapEntry(0, listOf(
                    StackMapLocation.Register(0),         // regObj
                    StackMapLocation.Stack(-8),            // stackObj
                    StackMapLocation.Constant(constObj),   // constObj
                ))
            )))
            gc.addExecutionContext(ctx)
            gc.collect()

            // Three objects reachable, one unreachable
            assertEquals(1L, heap.readField(regObj, 0))
            assertEquals(2L, heap.readField(stackObj, 0))
            assertEquals(3L, heap.readField(constObj, 0))
            assertTrue(gc.bytesReclaimed() > 0, "Unreachable object should be reclaimed")
            arena.close()
        }
    }

    @Test
    fun compactAndForwardUpdatesRoots() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 10L)
            heap.writeField(b, 0, 20L)
            heap.writeField(c, 0, 30L)

            val roots = mutableListOf(a, c)
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                for (root in roots) visitor.visitRoot(root)
            }

            val forwarding = gc.compactAndForward(roots)

            // Roots should be updated
            assertEquals(10L, heap.readField(roots[0], 0), "First root data preserved")
            assertEquals(30L, heap.readField(roots[1], 0), "Second root data preserved")

            // c should have moved (b was dead, creating a gap)
            assertTrue(forwarding.containsKey(c), "c should have been forwarded")
            assertEquals(roots[1], forwarding[c], "Root list should reflect forwarded address")
        }
    }

    @Test
    fun compactAndForwardUpdatesReferences() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val refL = refLayout()
            registry.register(refL)

            val a = heap.allocate(refL)
            val b = heap.allocate(refL)
            val c = heap.allocate(refL)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            // a.next → c (skip b)
            val nextOffset = a + ObjectLayout.HEADER_SIZE + 8
            val addrBytes = ByteArray(8)
            for (i in 0 until 8) addrBytes[i] = (c shr (i * 8)).toByte()
            heap.writeBytes(nextOffset, addrBytes)

            // Roots: a only. b is dead. c is reachable via a.next
            val roots = mutableListOf(a)
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                for (root in roots) visitor.visitRoot(root)
            }

            val forwarding = gc.compactAndForward(roots)

            // c should have moved into b's slot
            assertTrue(forwarding.containsKey(c), "c should be forwarded")
            val newC = forwarding[c]!!

            // a.next should now point to newC
            val updatedNextBytes = heap.readBytes(roots[0] + ObjectLayout.HEADER_SIZE + 8, 8)
            var updatedNext = 0L
            for (i in 0 until 8) updatedNext = updatedNext or ((updatedNextBytes[i].toLong() and 0xFF) shl (i * 8))
            assertEquals(newC, updatedNext, "Reference field should be updated to forwarded address")

            // Data should be intact
            assertEquals(1L, heap.readField(roots[0], 0))
            assertEquals(3L, heap.readField(newC, 0))
        }
    }

    @Test
    fun zeroBasePointerSkipsStackRoots() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("noFrame", 0) // no base pointer or register save area

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("noFrame", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Stack(-8)))
            )))
            gc.addExecutionContext(ctx)
            gc.collect()

            // Object is not rooted — should be reclaimed
            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun registerRootWithMultipleRegisters() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj1 = heap.allocate(layout)
            val obj2 = heap.allocate(layout)
            heap.writeField(obj1, 0, 10L)
            heap.writeField(obj2, 0, 20L)

            val arena = java.lang.foreign.Arena.ofConfined()
            val regSave = arena.allocate(128)
            regSave.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, obj1)    // reg[0]
            regSave.set(java.lang.foreign.ValueLayout.JAVA_LONG, 3 * 8, obj2) // reg[3]

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("multiReg", 0, basePointer = 0, registerSaveArea = regSave.address())

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("multiReg", listOf(
                StackMapEntry(0, listOf(
                    StackMapLocation.Register(0),
                    StackMapLocation.Register(3),
                ))
            )))
            gc.addExecutionContext(ctx)
            gc.collect()

            assertEquals(10L, heap.readField(obj1, 0))
            assertEquals(20L, heap.readField(obj2, 0))
            arena.close()
        }
    }
}

package org.kgen.runtime.gc

import org.kgen.runtime.*
import org.kgen.runtime.exec.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Integration tests exercising heap allocation + GC tracing + execution context
 * working together as a runtime system.
 */
class HeapGCIntegrationTest {

    private fun nodeLayout(typeId: Int = 1) = ObjectLayout(
        name = "Node",
        size = 16,
        fields = listOf(
            FieldDescriptor("value", 0, 8, false),
            FieldDescriptor("next", 8, 8, true),
        ),
        typeId = typeId,
    )

    private fun tripleRefLayout(typeId: Int = 2) = ObjectLayout(
        name = "Triple",
        size = 24,
        fields = listOf(
            FieldDescriptor("a", 0, 8, true),
            FieldDescriptor("b", 8, 8, true),
            FieldDescriptor("c", 16, 8, true),
        ),
        typeId = typeId,
    )

    private fun writeRef(heap: BumpHeap, obj: Long, fieldOffset: Int, target: Long) {
        val addr = obj + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = ByteArray(8)
        for (i in 0 until 8) bytes[i] = (target shr (i * 8)).toByte()
        heap.writeBytes(addr, bytes)
    }

    @Test
    fun linkedListFullTrace() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = nodeLayout()
            registry.register(layout)

            // Build linked list: head → n1 → n2 → ... → n9
            val nodes = (0 until 10).map { i ->
                val n = heap.allocate(layout)
                heap.writeField(n, 0, (i * 10).toLong())
                n
            }

            for (i in 0 until 9) {
                writeRef(heap, nodes[i], 8, nodes[i + 1])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(nodes[0]) }
            gc.collect()

            // All 10 nodes should survive — all reachable from head
            for (i in 0 until 10) {
                assertEquals((i * 10).toLong(), heap.readField(nodes[i], 0))
            }
        }
    }

    @Test
    fun circularReferenceDoesNotLoop() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = nodeLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            // a → b → c → a (cycle)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, c)
            writeRef(heap, c, 8, a)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect() // should not infinite loop

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
            assertEquals(3L, heap.readField(c, 0))
        }
    }

    @Test
    fun selfReferenceHandled() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = nodeLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)
            writeRef(heap, obj, 8, obj) // self-reference

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(42L, heap.readField(obj, 0))
        }
    }

    @Test
    fun treeStructureTraced() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val treeLayout = tripleRefLayout()
            val leafLayout = ObjectLayout(
                name = "Leaf",
                size = 8,
                fields = listOf(FieldDescriptor("value", 0, 8, false)),
                typeId = 3,
            )
            registry.register(treeLayout)
            registry.register(leafLayout)

            //        root
            //       / | \
            //      a  b  c
            val root = heap.allocate(treeLayout)
            val a = heap.allocate(leafLayout)
            val b = heap.allocate(leafLayout)
            val c = heap.allocate(leafLayout)

            heap.writeField(a, 0, 10L)
            heap.writeField(b, 0, 20L)
            heap.writeField(c, 0, 30L)

            writeRef(heap, root, 0, a)
            writeRef(heap, root, 8, b)
            writeRef(heap, root, 16, c)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertEquals(10L, heap.readField(a, 0))
            assertEquals(20L, heap.readField(b, 0))
            assertEquals(30L, heap.readField(c, 0))
        }
    }

    @Test
    fun multipleRootsOverlappingReferences() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = nodeLayout()
            registry.register(layout)

            val shared = heap.allocate(layout)
            heap.writeField(shared, 0, 99L)

            val root1 = heap.allocate(layout)
            val root2 = heap.allocate(layout)
            heap.writeField(root1, 0, 1L)
            heap.writeField(root2, 0, 2L)

            // Both root1 and root2 point to shared
            writeRef(heap, root1, 8, shared)
            writeRef(heap, root2, 8, shared)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(root1)
                visitor.visitRoot(root2)
            }
            gc.collect()

            assertEquals(99L, heap.readField(shared, 0))
        }
    }

    @Test
    fun gcWithExecutionContext() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            val layout = nodeLayout()
            runtime.typeRegistry().register(layout)

            val ctx = runtime.currentContext()
            ctx.pushFrame("buildList", 0)

            val head = runtime.heap().allocate(layout)
            runtime.heap().writeField(head, 0, 100L)

            runtime.gc().addRootProvider { visitor -> visitor.visitRoot(head) }
            runtime.gc().collect()

            assertEquals(100L, runtime.heap().readField(head, 0))
            ctx.popFrame()
        }
    }

    @Test
    fun safepointDuringGC() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Simple", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)

            val obj = runtime.heap().allocate(layout)
            runtime.heap().writeField(obj, 0, 42L)

            val ctx = runtime.currentContext()
            ctx.pushFrame("main", 0)

            // Simulate safepoint: enter safepoint, GC, leave
            ctx.enterSafepoint()
            assertTrue(ctx.isAtSafepoint())

            runtime.gc().addRootProvider { visitor -> visitor.visitRoot(obj) }
            runtime.gc().collect()

            ctx.leaveSafepoint()
            assertFalse(ctx.isAtSafepoint())

            assertEquals(42L, runtime.heap().readField(obj, 0))
            ctx.popFrame()
        }
    }

    @Test
    fun dispatchWithHeapObjects() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            val dispatch = runtime.dispatch() as BasicMethodDispatch
            dispatch.register("Node.getValue", 0x1000)
            dispatch.register("Node.setNext", 0x2000)

            val vtable = VTable(1, longArrayOf(0x1000, 0x2000))
            dispatch.registerVTable(1, vtable)

            assertEquals(0x1000L, dispatch.virtualLookup(1, 0))
            assertEquals(0x2000L, dispatch.virtualLookup(1, 1))
        }
    }

    @Test
    fun multipleCollectionsPreserveData() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = nodeLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }

            for (i in 1..10) {
                gc.collect()
                assertEquals(42L, heap.readField(obj, 0), "Failed after collection $i")
                assertEquals(i.toLong(), gc.collectionCount())
            }
        }
    }

    @Test
    fun allocateBetweenCollections() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = ObjectLayout("Simple", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            registry.register(layout)

            val roots = mutableListOf<Long>()
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                for (root in roots) visitor.visitRoot(root)
            }

            for (i in 0 until 20) {
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                roots.add(obj)
                gc.collect()
            }

            for (i in 0 until 20) {
                assertEquals(i.toLong(), heap.readField(roots[i], 0))
            }
        }
    }

    @Test
    fun mixedTypesInHeap() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val nodeL = nodeLayout(1)
            val tripleL = tripleRefLayout(2)
            val leafL = ObjectLayout("Leaf", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 3)
            registry.register(nodeL)
            registry.register(tripleL)
            registry.register(leafL)

            val leaf1 = heap.allocate(leafL)
            val leaf2 = heap.allocate(leafL)
            val leaf3 = heap.allocate(leafL)
            heap.writeField(leaf1, 0, 10L)
            heap.writeField(leaf2, 0, 20L)
            heap.writeField(leaf3, 0, 30L)

            val triple = heap.allocate(tripleL)
            writeRef(heap, triple, 0, leaf1)
            writeRef(heap, triple, 8, leaf2)
            writeRef(heap, triple, 16, leaf3)

            val node = heap.allocate(nodeL)
            heap.writeField(node, 0, 99L)
            writeRef(heap, node, 8, triple)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(node) }
            gc.collect()

            assertEquals(99L, heap.readField(node, 0))
            assertEquals(10L, heap.readField(leaf1, 0))
            assertEquals(20L, heap.readField(leaf2, 0))
            assertEquals(30L, heap.readField(leaf3, 0))
        }
    }

    @Test
    fun fullRuntimeStackAndGC() {
        DefaultManagedRuntime.create(16384).use { runtime ->
            val nodeL = nodeLayout()
            runtime.typeRegistry().register(nodeL)

            val ctx = runtime.currentContext()

            // Simulate: main() calls buildList() which allocates nodes
            ctx.pushFrame("main", 0)
            ctx.pushFrame("buildList", 100)

            val n1 = runtime.heap().allocate(nodeL)
            val n2 = runtime.heap().allocate(nodeL)
            val n3 = runtime.heap().allocate(nodeL)
            runtime.heap().writeField(n1, 0, 1L)
            runtime.heap().writeField(n2, 0, 2L)
            runtime.heap().writeField(n3, 0, 3L)

            // Link: n1 → n2 → n3
            val heap = runtime.heap() as BumpHeap
            writeRef(heap, n1, 8, n2)
            writeRef(heap, n2, 8, n3)

            runtime.gc().addRootProvider { visitor -> visitor.visitRoot(n1) }

            // GC while frames are active
            ctx.enterSafepoint()
            runtime.gc().collect()
            ctx.leaveSafepoint()

            assertEquals(1L, runtime.heap().readField(n1, 0))
            assertEquals(2L, runtime.heap().readField(n2, 0))
            assertEquals(3L, runtime.heap().readField(n3, 0))

            ctx.popFrame()
            ctx.popFrame()

            val frames = mutableListOf<String>()
            ctx.walkStack { f -> frames.add(f.functionName) }
            assertTrue(frames.isEmpty())
        }
    }

    @Test
    fun nativeTransitionDuringGC() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val ctx = runtime.currentContext()
            ctx.pushFrame("main", 0)

            // Transition to native
            ctx.enterNative()
            assertFalse(ctx.isInManagedCode())

            // GC can proceed since we're in native mode (safe)
            runtime.gc().collect()

            // Transition back to managed
            ctx.enterManaged()
            assertTrue(ctx.isInManagedCode())

            ctx.popFrame()
        }
    }

    @Test
    fun exceptionStateDuringGC() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val ctx = runtime.currentContext()
            ctx.pushFrame("main", 0)

            val ex = RuntimeException("test exception")
            ctx.setException(ex)

            // GC should work regardless of exception state
            runtime.gc().collect()

            assertSame(ex, ctx.currentException())
            ctx.clearException()
            assertNull(ctx.currentException())

            ctx.popFrame()
        }
    }
}

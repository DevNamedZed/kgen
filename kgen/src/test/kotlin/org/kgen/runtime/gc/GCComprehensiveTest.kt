package org.kgen.runtime.gc

import org.kgen.runtime.exec.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.StackMap
import org.kgen.ir.StackMapEntry
import org.kgen.ir.StackMapLocation

class GCComprehensiveTest {

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

    private fun twoRefLayout(typeId: Int = 3) = ObjectLayout(
        name = "TwoRef",
        size = 24,
        fields = listOf(
            FieldDescriptor("value", 0, 8, false),
            FieldDescriptor("left", 8, 8, true),
            FieldDescriptor("right", 16, 8, true),
        ),
        typeId = typeId,
    )

    private fun tripleRefLayout(typeId: Int = 4) = ObjectLayout(
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

    private fun readRef(heap: BumpHeap, obj: Long, fieldOffset: Int): Long {
        val addr = obj + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = heap.readBytes(addr, 8)
        var value = 0L
        for (i in 0 until 8) value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        return value
    }

    // Mark phase correctness

    @Test
    fun `mark phase sets gc flag on root object`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            // Object survived: we can still read/write
            heap.writeField(obj, 0, 123L)
            assertEquals(123L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `mark phase marks all objects in a chain`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, c)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
            assertEquals(3L, heap.readField(c, 0))
        }
    }

    @Test
    fun `mark phase handles multiple roots`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 5).map { heap.allocate(layout) }
            objs.forEachIndexed { i, obj -> heap.writeField(obj, 0, (i + 1).toLong()) }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                objs.forEach { visitor.visitRoot(it) }
            }
            gc.collect()

            objs.forEachIndexed { i, obj ->
                assertEquals((i + 1).toLong(), heap.readField(obj, 0))
            }
        }
    }

    @Test
    fun `mark phase marks tree structure`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val nodeLayout = twoRefLayout()
            val leafLayout = simpleLayout()
            registry.register(nodeLayout)
            registry.register(leafLayout)

            val root = heap.allocate(nodeLayout)
            val left = heap.allocate(nodeLayout)
            val right = heap.allocate(nodeLayout)
            val ll = heap.allocate(leafLayout)
            val lr = heap.allocate(leafLayout)
            val rl = heap.allocate(leafLayout)
            val rr = heap.allocate(leafLayout)

            heap.writeField(root, 0, 1L)
            heap.writeField(ll, 0, 10L)
            heap.writeField(lr, 0, 20L)
            heap.writeField(rl, 0, 30L)
            heap.writeField(rr, 0, 40L)

            writeRef(heap, root, 8, left)
            writeRef(heap, root, 16, right)
            writeRef(heap, left, 8, ll)
            writeRef(heap, left, 16, lr)
            writeRef(heap, right, 8, rl)
            writeRef(heap, right, 16, rr)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertEquals(10L, heap.readField(ll, 0))
            assertEquals(20L, heap.readField(lr, 0))
            assertEquals(30L, heap.readField(rl, 0))
            assertEquals(40L, heap.readField(rr, 0))
        }
    }

    @Test
    fun `mark phase marks DAG with shared child`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val shared = heap.allocate(layout)
            heap.writeField(shared, 0, 42L)

            writeRef(heap, a, 8, shared)
            writeRef(heap, b, 8, shared)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(a)
                visitor.visitRoot(b)
            }
            gc.collect()

            assertEquals(42L, heap.readField(shared, 0))
        }
    }

    // Sweep phase

    @Test
    fun `sweep reclaims single unreachable object`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `sweep reclaims middle object in sequence`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            heap.allocate(layout) // unreachable
            val c = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(a)
                visitor.visitRoot(c)
            }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertEquals(1, heap.freeBlockCount())
        }
    }

    @Test
    fun `sweep reclaims multiple unreachable objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val root = heap.allocate(layout)
            for (i in 0 until 5) heap.allocate(layout) // all unreachable

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertTrue(heap.freeBlockCount() >= 1)
        }
    }

    @Test
    fun `sweep populates free list`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            for (i in 0 until 3) heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect() // no roots, all dead

            assertTrue(heap.freeBlockCount() >= 1)
            assertTrue(heap.freeBytes() > 0)
        }
    }

    @Test
    fun `sweep preserves all rooted objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 10).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, (i * 100).toLong())
                obj
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> objs.forEach { visitor.visitRoot(it) } }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
            objs.forEachIndexed { i, obj ->
                assertEquals((i * 100).toLong(), heap.readField(obj, 0))
            }
        }
    }

    // Reference tracing through object graphs

    @Test
    fun `trace single reference`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val parent = heap.allocate(layout)
            val child = heap.allocate(layout)
            heap.writeField(child, 0, 77L)
            writeRef(heap, parent, 8, child)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(parent) }
            gc.collect()

            assertEquals(77L, heap.readField(child, 0))
        }
    }

    @Test
    fun `trace long chain of 20 objects`() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val objects = (0 until 20).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                obj
            }
            for (i in 0 until 19) {
                writeRef(heap, objects[i], 8, objects[i + 1])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(objects[0]) }
            gc.collect()

            for (i in 0 until 20) {
                assertEquals(i.toLong(), heap.readField(objects[i], 0))
            }
        }
    }

    @Test
    fun `trace binary tree depth 4`() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            registry.register(layout)

            // Build a complete binary tree of depth 4 (15 nodes)
            val nodes = (0 until 15).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                obj
            }
            for (i in 0 until 7) {
                writeRef(heap, nodes[i], 8, nodes[2 * i + 1])
                writeRef(heap, nodes[i], 16, nodes[2 * i + 2])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(nodes[0]) }
            gc.collect()

            for (i in 0 until 15) {
                assertEquals(i.toLong(), heap.readField(nodes[i], 0))
            }
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `trace cycle of two objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, a)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
        }
    }

    @Test
    fun `trace cycle of three objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, c)
            writeRef(heap, c, 8, a)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()

            assertEquals(1L, gc.collectionCount())
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `trace self-referencing object`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 99L)
            writeRef(heap, obj, 8, obj)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(99L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `trace DAG does not double-process shared nodes`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            registry.register(layout)

            //     root
            //    /    \
            //   a      b
            //    \    /
            //     shared
            val root = heap.allocate(layout)
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val shared = heap.allocate(layout)
            heap.writeField(shared, 0, 42L)

            writeRef(heap, root, 8, a)
            writeRef(heap, root, 16, b)
            writeRef(heap, a, 16, shared)
            writeRef(heap, b, 8, shared)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertEquals(42L, heap.readField(shared, 0))
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `trace with null references does not crash`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 55L)
            // left and right are null (0) by default

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(55L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `trace with mixed null and non-null references`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            val leafLayout = simpleLayout()
            registry.register(layout)
            registry.register(leafLayout)

            val node = heap.allocate(layout)
            val child = heap.allocate(leafLayout)
            heap.writeField(child, 0, 88L)
            writeRef(heap, node, 8, child) // left = child
            // right is null

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(node) }
            gc.collect()

            assertEquals(88L, heap.readField(child, 0))
        }
    }

    // Multiple collection cycles

    @Test
    fun `two collections increment count`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            gc.collect()
            assertEquals(2L, gc.collectionCount())
        }
    }

    @Test
    fun `ten collections on empty heap`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            for (i in 1..10) gc.collect()
            assertEquals(10L, gc.collectionCount())
        }
    }

    @Test
    fun `collection count starts at zero`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertEquals(0L, gc.collectionCount())
        }
    }

    @Test
    fun `data survives multiple collections`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 12345L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }

            for (i in 1..20) {
                gc.collect()
                assertEquals(12345L, heap.readField(obj, 0), "Failed at collection $i")
            }
        }
    }

    @Test
    fun `allocate between collections with growing root set`() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val roots = mutableListOf<Long>()
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> roots.forEach { visitor.visitRoot(it) } }

            for (i in 0 until 30) {
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                roots.add(obj)
                gc.collect()
            }

            for (i in 0 until 30) {
                assertEquals(i.toLong(), heap.readField(roots[i], 0))
            }
        }
    }

    @Test
    fun `remove roots between collections causes reclaim`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)

            val roots = mutableSetOf(a, b)
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> roots.forEach { visitor.visitRoot(it) } }

            gc.collect()
            assertEquals(0L, gc.bytesReclaimed())

            // Remove b from roots
            roots.remove(b)
            val gc2 = MarkSweepGC(heap, registry)
            gc2.addRootProvider { visitor -> roots.forEach { visitor.visitRoot(it) } }
            gc2.collect()
            assertTrue(gc2.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `marks cleared between collections`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            // If marks weren't cleared, the second collection would behave incorrectly
            gc.collect()
            assertEquals(2L, gc.collectionCount())
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    // Free list reuse after sweep

    @Test
    fun `free list reuse allocates in reclaimed space`() {
        BumpHeap(512).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            val gc = MarkSweepGC(heap, registry)
            gc.collect() // no roots, obj is dead

            assertEquals(1, heap.freeBlockCount())
            val reused = heap.allocate(layout)
            heap.writeField(reused, 0, 77L)
            assertEquals(77L, heap.readField(reused, 0))
        }
    }

    @Test
    fun `free list reuse for multiple dead objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            for (i in 0 until 5) heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect() // all dead

            assertTrue(heap.freeBlockCount() >= 1)

            // Allocate new objects, should reuse free blocks
            for (i in 0 until 5) {
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, (i * 10).toLong())
            }
        }
    }

    @Test
    fun `collect then allocate then collect preserves new objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            heap.allocate(layout) // will be collected

            val gc = MarkSweepGC(heap, registry)
            gc.collect()

            val newObj = heap.allocate(layout)
            heap.writeField(newObj, 0, 999L)

            val gc2 = MarkSweepGC(heap, registry)
            gc2.addRootProvider { visitor -> visitor.visitRoot(newObj) }
            gc2.collect()

            assertEquals(999L, heap.readField(newObj, 0))
        }
    }

    // Stack map registration and lookup

    @Test
    fun `register stack map and retrieve it`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("myFunc", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Register(0)))
            ))
            gc.registerStackMap(stackMap)

            val maps = gc.stackMaps()
            assertEquals(1, maps.size)
            assertNotNull(maps["myFunc"])
        }
    }

    @Test
    fun `register multiple stack maps`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            gc.registerStackMap(StackMap("func1", listOf()))
            gc.registerStackMap(StackMap("func2", listOf()))
            gc.registerStackMap(StackMap("func3", listOf()))

            assertEquals(3, gc.stackMaps().size)
        }
    }

    @Test
    fun `stack map with constant location provides root during collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("myFunc", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Constant(obj)))
            ))
            gc.registerStackMap(stackMap)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("myFunc", 0)
            gc.addExecutionContext(ctx)

            gc.collect()

            assertEquals(42L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `stack map lookup returns null for unregistered function`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertTrue(gc.stackMaps().isEmpty())
        }
    }

    @Test
    fun `stack map with register location does not crash`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("func", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Register(3)))
            ))
            gc.registerStackMap(stackMap)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("func", 0)
            gc.addExecutionContext(ctx)

            gc.collect() // should not crash
        }
    }

    @Test
    fun `stack map with stack location does not crash`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("func", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Stack(-16)))
            ))
            gc.registerStackMap(stackMap)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("func", 0)
            gc.addExecutionContext(ctx)

            gc.collect()
        }
    }

    @Test
    fun `stack map with zero constant is skipped`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("func", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Constant(0L)))
            ))
            gc.registerStackMap(stackMap)

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("func", 0)
            gc.addExecutionContext(ctx)

            gc.collect() // should not crash on null constant
        }
    }

    // Root provider registration

    @Test
    fun `single root provider called during collect`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            var called = false
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { called = true }
            gc.collect()
            assertTrue(called)
        }
    }

    @Test
    fun `multiple root providers all called`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            var count = 0
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { count++ }
            gc.addRootProvider { count++ }
            gc.addRootProvider { count++ }
            gc.collect()
            assertEquals(3, count)
        }
    }

    @Test
    fun `root provider called on every collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            var count = 0
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { count++ }
            gc.collect()
            gc.collect()
            gc.collect()
            assertEquals(3, count)
        }
    }

    @Test
    fun `root provider with dynamic root set`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val roots = mutableListOf<Long>()
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> roots.forEach { visitor.visitRoot(it) } }

            val obj1 = heap.allocate(layout)
            roots.add(obj1)
            gc.collect()
            assertEquals(0L, gc.bytesReclaimed())

            val obj2 = heap.allocate(layout)
            roots.add(obj2)
            gc.collect()
        }
    }

    // Execution context registration

    @Test
    fun `add execution context increments count`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            gc.addExecutionContext(ctx)
            assertEquals(1, gc.executionContextCount())
        }
    }

    @Test
    fun `remove execution context decrements count`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            gc.addExecutionContext(ctx)
            gc.removeExecutionContext(ctx)
            assertEquals(0, gc.executionContextCount())
        }
    }

    @Test
    fun `multiple execution contexts`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx1 = ThreadExecutionContext()
            val ctx2 = ThreadExecutionContext()
            val ctx3 = ThreadExecutionContext()
            gc.addExecutionContext(ctx1)
            gc.addExecutionContext(ctx2)
            gc.addExecutionContext(ctx3)
            assertEquals(3, gc.executionContextCount())
        }
    }

    @Test
    fun `execution context with empty stack does not crash during collect`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            gc.addExecutionContext(ctx)
            gc.collect()
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun `execution context with frames but no matching stack map`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("unknownFunc", 0)
            gc.addExecutionContext(ctx)
            gc.collect() // should gracefully skip unknown functions
        }
    }

    @Test
    fun `execution context scan with multiple frames`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj1 = heap.allocate(layout)
            val obj2 = heap.allocate(layout)
            heap.writeField(obj1, 0, 10L)
            heap.writeField(obj2, 0, 20L)

            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("main", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Constant(obj1)))
            )))
            gc.registerStackMap(StackMap("helper", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Constant(obj2)))
            )))

            val ctx = ThreadExecutionContext()
            ctx.pushFrame("main", 0)
            ctx.pushFrame("helper", 100)
            gc.addExecutionContext(ctx)

            gc.collect()

            assertEquals(10L, heap.readField(obj1, 0))
            assertEquals(20L, heap.readField(obj2, 0))
        }
    }

    // Edge cases

    @Test
    fun `collect on empty heap`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            assertEquals(1L, gc.collectionCount())
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `collect with single object no roots`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `collect with single object as root`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
            assertEquals(42L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `all objects dead`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            for (i in 0 until 10) heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `all objects live`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            val objs = (0 until 10).map { heap.allocate(layout) }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> objs.forEach { visitor.visitRoot(it) } }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `bytes reclaimed initially zero`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `bytes reclaimed accumulates across collections`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            heap.allocate(layout)
            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            val first = gc.bytesReclaimed()
            assertTrue(first > 0)

            // Allocate more dead objects
            heap.allocate(layout)
            heap.allocate(layout)
            gc.collect()
            assertTrue(gc.bytesReclaimed() >= first)
        }
    }

    // Large object graphs

    @Test
    fun `trace graph of 50 objects in a chain`() {
        BumpHeap(65536).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val objects = (0 until 50).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                obj
            }
            for (i in 0 until 49) {
                writeRef(heap, objects[i], 8, objects[i + 1])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(objects[0]) }
            gc.collect()

            for (i in 0 until 50) {
                assertEquals(i.toLong(), heap.readField(objects[i], 0))
            }
        }
    }

    @Test
    fun `100 independent rooted objects`() {
        BumpHeap(65536).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 100).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                obj
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> objs.forEach { visitor.visitRoot(it) } }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
            for (i in 0 until 100) {
                assertEquals(i.toLong(), heap.readField(objs[i], 0))
            }
        }
    }

    @Test
    fun `wide fan-out with triple ref`() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val tripleLayout = tripleRefLayout()
            val leafLayout = simpleLayout()
            registry.register(tripleLayout)
            registry.register(leafLayout)

            val root = heap.allocate(tripleLayout)
            val children = (0 until 3).map { i ->
                val child = heap.allocate(tripleLayout)
                val leaves = (0 until 3).map { j ->
                    val leaf = heap.allocate(leafLayout)
                    heap.writeField(leaf, 0, (i * 10 + j).toLong())
                    leaf
                }
                writeRef(heap, child, 0, leaves[0])
                writeRef(heap, child, 8, leaves[1])
                writeRef(heap, child, 16, leaves[2])
                child
            }
            writeRef(heap, root, 0, children[0])
            writeRef(heap, root, 8, children[1])
            writeRef(heap, root, 16, children[2])

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `large graph with some dead subtrees`() {
        BumpHeap(16384).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            val leafLayout = simpleLayout()
            registry.register(layout)
            registry.register(leafLayout)

            val root = heap.allocate(layout)
            val liveChild = heap.allocate(leafLayout)
            heap.writeField(liveChild, 0, 100L)
            writeRef(heap, root, 8, liveChild)
            // right child is null (dead subtree)

            // Allocate dead objects not connected to root
            for (i in 0 until 10) {
                val dead = heap.allocate(leafLayout)
                heap.writeField(dead, 0, -1L)
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertEquals(100L, heap.readField(liveChild, 0))
        }
    }

    // Fragmentation scenarios

    @Test
    fun `alternating live and dead creates fragmentation`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 6).map { heap.allocate(layout) }
            // Keep even-indexed, kill odd-indexed
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                for (i in objs.indices step 2) visitor.visitRoot(objs[i])
            }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertTrue(heap.freeBlockCount() >= 1)
        }
    }

    @Test
    fun `fragmented free list can serve allocations`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 6).map { heap.allocate(layout) }
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                for (i in objs.indices step 2) visitor.visitRoot(objs[i])
            }
            gc.collect()

            // Should be able to allocate from free list
            val newObj = heap.allocate(layout)
            heap.writeField(newObj, 0, 321L)
            assertEquals(321L, heap.readField(newObj, 0))
        }
    }

    @Test
    fun `collect-allocate-collect cycle with fragmentation`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            // Round 1: allocate 4, keep 2
            val round1 = (0 until 4).map { heap.allocate(layout) }
            val gc1 = MarkSweepGC(heap, registry)
            gc1.addRootProvider { visitor ->
                visitor.visitRoot(round1[0])
                visitor.visitRoot(round1[2])
            }
            gc1.collect()

            // Round 2: allocate 2 more (may use free list), keep them
            val round2a = heap.allocate(layout)
            val round2b = heap.allocate(layout)
            heap.writeField(round2a, 0, 111L)
            heap.writeField(round2b, 0, 222L)

            val gc2 = MarkSweepGC(heap, registry)
            gc2.addRootProvider { visitor ->
                visitor.visitRoot(round1[0])
                visitor.visitRoot(round1[2])
                visitor.visitRoot(round2a)
                visitor.visitRoot(round2b)
            }
            gc2.collect()

            assertEquals(111L, heap.readField(round2a, 0))
            assertEquals(222L, heap.readField(round2b, 0))
        }
    }

    // Collection statistics

    @Test
    fun `collection count after one collect`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.collect()
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun `collection count after fifty collects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            for (i in 1..50) gc.collect()
            assertEquals(50L, gc.collectionCount())
        }
    }

    @Test
    fun `bytes reclaimed reflects object size`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            heap.allocate(layout) // 16 bytes total

            val gc = MarkSweepGC(heap, registry)
            gc.collect()

            // Reclaimed amount should be at least the object total size (aligned)
            assertTrue(gc.bytesReclaimed() >= layout.totalSize())
        }
    }

    @Test
    fun `bytes reclaimed for multiple dead objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)
            for (i in 0 until 5) heap.allocate(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect()

            assertTrue(gc.bytesReclaimed() >= layout.totalSize() * 5)
        }
    }

    @Test
    fun `bytes reclaimed is zero when all live`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val objs = (0 until 5).map { heap.allocate(layout) }
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> objs.forEach { visitor.visitRoot(it) } }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    // Mixed type scenarios

    @Test
    fun `mixed types with references between them`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val refL = refLayout(2)
            val simpleL = simpleLayout(1)
            registry.register(refL)
            registry.register(simpleL)

            val leaf = heap.allocate(simpleL)
            heap.writeField(leaf, 0, 42L)
            val node = heap.allocate(refL)
            heap.writeField(node, 0, 1L)
            writeRef(heap, node, 8, leaf)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(node) }
            gc.collect()

            assertEquals(42L, heap.readField(leaf, 0))
            assertEquals(1L, heap.readField(node, 0))
        }
    }

    @Test
    fun `different sized types survive collection`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val small = ObjectLayout("Small", 8, listOf(
                FieldDescriptor("v", 0, 8, false)
            ), typeId = 10)
            val medium = ObjectLayout("Medium", 16, listOf(
                FieldDescriptor("a", 0, 8, false),
                FieldDescriptor("b", 8, 8, false),
            ), typeId = 11)
            val large = ObjectLayout("Large", 32, listOf(
                FieldDescriptor("a", 0, 8, false),
                FieldDescriptor("b", 8, 8, false),
                FieldDescriptor("c", 16, 8, false),
                FieldDescriptor("d", 24, 8, false),
            ), typeId = 12)
            registry.register(small)
            registry.register(medium)
            registry.register(large)

            val s = heap.allocate(small)
            val m = heap.allocate(medium)
            val l = heap.allocate(large)
            heap.writeField(s, 0, 1L)
            heap.writeField(m, 0, 2L)
            heap.writeField(l, 0, 3L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(s)
                visitor.visitRoot(m)
                visitor.visitRoot(l)
            }
            gc.collect()

            assertEquals(1L, heap.readField(s, 0))
            assertEquals(2L, heap.readField(m, 0))
            assertEquals(3L, heap.readField(l, 0))
        }
    }

    @Test
    fun `object with all reference fields`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val tripleLayout = tripleRefLayout()
            val leafLayout = simpleLayout()
            registry.register(tripleLayout)
            registry.register(leafLayout)

            val a = heap.allocate(leafLayout)
            val b = heap.allocate(leafLayout)
            val c = heap.allocate(leafLayout)
            heap.writeField(a, 0, 10L)
            heap.writeField(b, 0, 20L)
            heap.writeField(c, 0, 30L)

            val container = heap.allocate(tripleLayout)
            writeRef(heap, container, 0, a)
            writeRef(heap, container, 8, b)
            writeRef(heap, container, 16, c)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(container) }
            gc.collect()

            assertEquals(10L, heap.readField(a, 0))
            assertEquals(20L, heap.readField(b, 0))
            assertEquals(30L, heap.readField(c, 0))
        }
    }

    @Test
    fun `object with no reference fields is not traced further`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 0xDEADBEEFL)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(0xDEADBEEFL, heap.readField(obj, 0))
        }
    }

    // Complex graph scenarios

    @Test
    fun `diamond reference pattern`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            val leafLayout = simpleLayout()
            registry.register(layout)
            registry.register(leafLayout)

            //     root
            //    /    \
            //   a      b
            //    \    /
            //     leaf
            val root = heap.allocate(layout)
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val leaf = heap.allocate(leafLayout)
            heap.writeField(leaf, 0, 77L)

            writeRef(heap, root, 8, a)
            writeRef(heap, root, 16, b)
            writeRef(heap, a, 16, leaf)
            writeRef(heap, b, 8, leaf)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertEquals(77L, heap.readField(leaf, 0))
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `cycle with dead tail`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val dead = heap.allocate(layout)

            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, a) // cycle

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(a) }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0) // dead should be reclaimed
        }
    }

    @Test
    fun `unreachable cycle is collected`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            // Create a cycle that is not rooted
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, a)

            // Root a different object
            val root = heap.allocate(layout)
            heap.writeField(root, 0, 99L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertEquals(99L, heap.readField(root, 0))
        }
    }

    @Test
    fun `newly allocated object after gc is writable`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val gc = MarkSweepGC(heap, registry)
            gc.collect() // empty collection

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, Long.MAX_VALUE)
            assertEquals(Long.MAX_VALUE, heap.readField(obj, 0))
        }
    }

    @Test
    fun `root provider that provides invalid address is safe`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            // Provide an address outside heap bounds
            gc.addRootProvider { visitor -> visitor.visitRoot(0xDEADL) }
            gc.collect() // should not crash
        }
    }

    @Test
    fun `root provider providing zero address is safe`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(0L) }
            gc.collect()
        }
    }

    @Test
    fun `collect with only dead reference-containing objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            writeRef(heap, a, 8, b)

            val gc = MarkSweepGC(heap, registry)
            gc.collect() // no roots, both dead
            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `gc flag is reset after collection for live objects`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }

            gc.collect()
            // After first collect, mark bit is set
            // clearMarks in second collect clears it before re-marking
            gc.collect()
            // If marks weren't properly managed, this would fail
            assertEquals(2L, gc.collectionCount())
        }
    }

    @Test
    fun `collect with reference to object of unregistered type`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout(1)
            registry.register(layout)

            val obj = heap.allocate(layout)
            // The typeId is registered so this works
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun `multiple root providers with overlapping roots`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) } // same object
            gc.collect()

            assertEquals(42L, heap.readField(obj, 0))
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `stack map overwrite replaces previous`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            gc.registerStackMap(StackMap("func", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Register(0)))
            )))
            gc.registerStackMap(StackMap("func", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Register(1), StackMapLocation.Register(2)))
            )))

            val maps = gc.stackMaps()
            assertEquals(1, maps.size)
            assertEquals(2, maps["func"]!!.entries[0].locations.size)
        }
    }

    @Test
    fun `collect preserves reference integrity across cycles`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val head = heap.allocate(layout)
            val mid = heap.allocate(layout)
            val tail = heap.allocate(layout)
            heap.writeField(head, 0, 1L)
            heap.writeField(mid, 0, 2L)
            heap.writeField(tail, 0, 3L)
            writeRef(heap, head, 8, mid)
            writeRef(heap, mid, 8, tail)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(head) }

            for (i in 1..5) {
                gc.collect()
                // Verify reference chain is intact
                assertEquals(1L, heap.readField(head, 0))
                assertEquals(2L, heap.readField(mid, 0))
                assertEquals(3L, heap.readField(tail, 0))
                assertEquals(mid, readRef(heap, head, 8))
                assertEquals(tail, readRef(heap, mid, 8))
            }
        }
    }

    @Test
    fun `stack map with multiple entries and locations`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)

            val stackMap = StackMap("complexFunc", listOf(
                StackMapEntry(0, listOf(
                    StackMapLocation.Register(0),
                    StackMapLocation.Stack(-8),
                    StackMapLocation.Constant(0L),
                )),
                StackMapEntry(16, listOf(
                    StackMapLocation.Register(1),
                    StackMapLocation.Stack(-16),
                )),
                StackMapEntry(32, listOf(
                    StackMapLocation.Constant(0L),
                )),
            ))
            gc.registerStackMap(stackMap)

            assertEquals(3, gc.stackMaps()["complexFunc"]!!.entries.size)
        }
    }

    @Test
    fun `execution context count is zero initially`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertEquals(0, gc.executionContextCount())
        }
    }

    @Test
    fun `removing non-existent context does not crash`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            gc.removeExecutionContext(ctx) // not added, should be no-op
            assertEquals(0, gc.executionContextCount())
        }
    }

    @Test
    fun `gc with both root providers and execution contexts`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj1 = heap.allocate(layout)
            val obj2 = heap.allocate(layout)
            heap.writeField(obj1, 0, 10L)
            heap.writeField(obj2, 0, 20L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj1) }

            gc.registerStackMap(StackMap("main", listOf(
                StackMapEntry(0, listOf(StackMapLocation.Constant(obj2)))
            )))
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("main", 0)
            gc.addExecutionContext(ctx)

            gc.collect()

            assertEquals(10L, heap.readField(obj1, 0))
            assertEquals(20L, heap.readField(obj2, 0))
        }
    }

    @Test
    fun `deep chain of 100 objects all reachable`() {
        BumpHeap(131072).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val objects = (0 until 100).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, i.toLong())
                obj
            }
            for (i in 0 until 99) {
                writeRef(heap, objects[i], 8, objects[i + 1])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(objects[0]) }
            gc.collect()

            for (i in 0 until 100) {
                assertEquals(i.toLong(), heap.readField(objects[i], 0))
            }
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `collect reclaims detached subtree`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            val leafLayout = simpleLayout()
            registry.register(layout)
            registry.register(leafLayout)

            val root = heap.allocate(layout)
            val liveLeaf = heap.allocate(leafLayout)
            heap.writeField(liveLeaf, 0, 1L)
            writeRef(heap, root, 8, liveLeaf)

            // Dead subtree
            val deadNode = heap.allocate(layout)
            val deadLeaf = heap.allocate(leafLayout)
            writeRef(heap, deadNode, 8, deadLeaf)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(root) }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
            assertEquals(1L, heap.readField(liveLeaf, 0))
        }
    }

    @Test
    fun `negative field values survive collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, -123456L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(-123456L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `max long value survives collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, Long.MAX_VALUE)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(Long.MAX_VALUE, heap.readField(obj, 0))
        }
    }

    @Test
    fun `min long value survives collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, Long.MIN_VALUE)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(Long.MIN_VALUE, heap.readField(obj, 0))
        }
    }

    @Test
    fun `zero value survives collection`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 0L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(0L, heap.readField(obj, 0))
        }
    }

    @Test
    fun `collect with large ref layout objects`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val objs = (0 until 20).map { i ->
                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, (i * 7).toLong())
                obj
            }
            // Chain first 10, leave last 10 disconnected
            for (i in 0 until 9) writeRef(heap, objs[i], 8, objs[i + 1])

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(objs[0]) }
            gc.collect()

            // First 10 survive, last 10 reclaimed
            for (i in 0 until 10) {
                assertEquals((i * 7).toLong(), heap.readField(objs[i], 0))
            }
            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `ten root providers each with one root`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val gc = MarkSweepGC(heap, registry)
            val objs = (0 until 10).map { heap.allocate(layout) }
            objs.forEach { obj ->
                gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            }
            gc.collect()
            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `stack maps empty initially`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            assertTrue(gc.stackMaps().isEmpty())
        }
    }

    @Test
    fun `collect after adding and removing execution context`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val ctx = ThreadExecutionContext()
            gc.addExecutionContext(ctx)
            gc.removeExecutionContext(ctx)
            gc.collect()
            assertEquals(1L, gc.collectionCount())
        }
    }

    @Test
    fun `half live half dead with ref layout`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val live = (0 until 5).map { heap.allocate(layout) }
            val dead = (0 until 5).map { heap.allocate(layout) }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> live.forEach { visitor.visitRoot(it) } }
            gc.collect()

            assertTrue(gc.bytesReclaimed() > 0)
        }
    }

    @Test
    fun `chain where middle is also a root`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            writeRef(heap, a, 8, b)
            writeRef(heap, b, 8, c)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(a)
                visitor.visitRoot(b) // redundant but valid
            }
            gc.collect()

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
            assertEquals(3L, heap.readField(c, 0))
        }
    }

    @Test
    fun `two separate chains from two roots`() {
        BumpHeap(8192).use { heap ->
            val registry = TypeRegistry()
            val layout = refLayout()
            registry.register(layout)

            val chain1 = (0 until 5).map { heap.allocate(layout) }
            val chain2 = (0 until 5).map { heap.allocate(layout) }
            for (i in 0 until 4) {
                writeRef(heap, chain1[i], 8, chain1[i + 1])
                writeRef(heap, chain2[i], 8, chain2[i + 1])
            }

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor ->
                visitor.visitRoot(chain1[0])
                visitor.visitRoot(chain2[0])
            }
            gc.collect()

            assertEquals(0L, gc.bytesReclaimed())
        }
    }

    @Test
    fun `repeated collections do not leak reclaimed count`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = simpleLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }

            for (i in 1..10) {
                gc.collect()
                assertEquals(0L, gc.bytesReclaimed())
            }
        }
    }

    @Test
    fun `stack map with empty entries list`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            gc.registerStackMap(StackMap("emptyFunc", listOf()))
            assertEquals(0, gc.stackMaps()["emptyFunc"]!!.entries.size)
        }
    }

    @Test
    fun `collect with two-ref layout all fields null`() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = twoRefLayout()
            registry.register(layout)

            val obj = heap.allocate(layout)
            heap.writeField(obj, 0, 42L)

            val gc = MarkSweepGC(heap, registry)
            gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
            gc.collect()

            assertEquals(42L, heap.readField(obj, 0))
            assertEquals(0L, gc.bytesReclaimed())
        }
    }
}

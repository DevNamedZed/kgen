package org.kgen.runtime

import org.kgen.runtime.gc.*
import org.kgen.runtime.exec.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DefaultManagedRuntimeTest {

    @Test
    fun createAndClose() {
        val runtime = DefaultManagedRuntime.create(4096)
        assertNotNull(runtime.heap())
        assertNotNull(runtime.gc())
        assertNotNull(runtime.safepoints())
        assertNotNull(runtime.dispatch())
        runtime.close()
    }

    @Test
    fun heapAllocation() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Simple", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)
            val addr = runtime.heap().allocate(layout)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun heapReadWrite() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Point", 16, listOf(
                FieldDescriptor("x", 0, 8, false),
                FieldDescriptor("y", 8, 8, false),
            ), typeId = 1)
            runtime.typeRegistry().register(layout)
            val addr = runtime.heap().allocate(layout)
            runtime.heap().writeField(addr, 0, 42L)
            runtime.heap().writeField(addr, 1, 99L)
            assertEquals(42L, runtime.heap().readField(addr, 0))
            assertEquals(99L, runtime.heap().readField(addr, 1))
        }
    }

    @Test
    fun gcCollectionWorks() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Simple", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)
            runtime.heap().allocate(layout)
            runtime.gc().collect()
            assertEquals(1L, runtime.gc().collectionCount())
        }
    }

    @Test
    fun currentContextReturnsNonNull() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val ctx = runtime.currentContext()
            assertNotNull(ctx)
        }
    }

    @Test
    fun currentContextSameThread() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val ctx1 = runtime.currentContext()
            val ctx2 = runtime.currentContext()
            assertSame(ctx1, ctx2)
        }
    }

    @Test
    fun dispatchRegisterAndResolve() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val dispatch = runtime.dispatch() as BasicMethodDispatch
            dispatch.register("main", 0x1000)
            assertEquals(0x1000L, dispatch.resolve("main"))
        }
    }

    @Test
    fun fullLifecycle() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            val layout = ObjectLayout("Node", 16, listOf(
                FieldDescriptor("value", 0, 8, false),
                FieldDescriptor("next", 8, 8, true),
            ), typeId = 1)
            runtime.typeRegistry().register(layout)

            // Allocate objects
            val a = runtime.heap().allocate(layout)
            val b = runtime.heap().allocate(layout)
            runtime.heap().writeField(a, 0, 1L)
            runtime.heap().writeField(b, 0, 2L)

            // Push frames
            val ctx = runtime.currentContext()
            ctx.pushFrame("main", 0)
            ctx.pushFrame("build_list", 100)

            // GC
            runtime.gc().addRootProvider { visitor -> visitor.visitRoot(a) }
            runtime.gc().collect()

            // Pop frames
            ctx.popFrame()
            ctx.popFrame()
            assertEquals(0, ctx.depth())
        }
    }

    @Test
    fun multipleCollections() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Simple", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)
            runtime.heap().allocate(layout)
            runtime.gc().collect()
            runtime.gc().collect()
            runtime.gc().collect()
            assertEquals(3L, runtime.gc().collectionCount())
        }
    }

    @Test
    fun runtimeAllocCreatesObject() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val addr = runtime.runtimeAlloc(1, 16)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun runtimeGcCollectIncrementsCount() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            runtime.runtimeGcCollect()
            runtime.runtimeGcCollect()
            assertEquals(2L, runtime.gc().collectionCount())
        }
    }

    @Test
    fun runtimeSafepointPollDoesNotThrow() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            runtime.runtimeSafepointPoll()
        }
    }

    @Test
    fun runtimeWriteBarrierDoesNotThrow() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Obj", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)
            val addr = runtime.runtimeAlloc(1, 8)
            runtime.runtimeWriteBarrier(addr, 0, 42L)
        }
    }

    @Test
    fun runtimeReadBarrierReturnsRef() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            val layout = ObjectLayout("Obj", 8, listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
            runtime.typeRegistry().register(layout)
            val addr = runtime.runtimeAlloc(1, 8)
            assertEquals(addr, runtime.runtimeReadBarrier(addr))
        }
    }

    @Test
    fun writeBarrierThenCollect() {
        DefaultManagedRuntime.create(8192).use { runtime ->
            val layout = ObjectLayout("Node", 16, listOf(
                FieldDescriptor("value", 0, 8, false),
                FieldDescriptor("next", 8, 8, true),
            ), typeId = 1)
            runtime.typeRegistry().register(layout)

            val a = runtime.runtimeAlloc(1, 16)
            val b = runtime.runtimeAlloc(1, 16)

            // Write a reference from a.next -> b via the barrier
            runtime.runtimeWriteBarrier(a, 1, b)
            runtime.heap().writeField(a, 1, b)

            // Root only 'a' — 'b' should survive because a.next references it
            runtime.gc().addRootProvider { visitor -> visitor.visitRoot(a) }
            runtime.runtimeGcCollect()

            assertEquals(1L, runtime.gc().collectionCount())
        }
    }

    @Test
    fun readBarrierIdentityForMarkSweep() {
        DefaultManagedRuntime.create(4096).use { runtime ->
            // Mark-sweep doesn't relocate, so read barrier is identity
            assertEquals(0L, runtime.runtimeReadBarrier(0L))
            assertEquals(12345L, runtime.runtimeReadBarrier(12345L))
        }
    }
}

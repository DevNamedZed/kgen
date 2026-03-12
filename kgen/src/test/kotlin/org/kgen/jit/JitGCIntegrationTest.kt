package org.kgen.jit

import org.kgen.codegen.CompiledCode
import org.kgen.ir.StackMap
import org.kgen.ir.StackMapEntry
import org.kgen.ir.StackMapLocation
import org.kgen.runtime.*
import org.kgen.runtime.gc.*
import org.kgen.runtime.exec.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class JitGCIntegrationTest {

    @Test
    fun setGarbageCollectorRegistersStackMaps() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val stackMap = StackMap("myFunc", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Register(0))),
            StackMapEntry(16, listOf(StackMapLocation.Stack(-8))),
        ))

        gc.registerStackMap(stackMap)

        val maps = gc.stackMaps()
        assertEquals(1, maps.size)
        assertNotNull(maps["myFunc"])
        assertEquals(2, maps["myFunc"]!!.entries.size)

        heap.close()
    }

    @Test
    fun setRuntimeWiresGCAndSafepoints() {
        val runtime = DefaultManagedRuntime.create(4096)
        val gen = StubCodeGenerator()
        val jit = JitEngine(gen)

        jit.setRuntime(runtime)
        assertSame(runtime.gc(), jit.gc())
        assertSame(runtime.safepoints(), jit.safepointManager())

        jit.close()
        runtime.close()
    }

    @Test
    fun loadCompiledCodeRegistersStackMapsWithGC() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val gen = StubCodeGenerator()
        val jit = JitEngine(gen)
        jit.setGarbageCollector(gc)

        val stackMap = StackMap("add", listOf(
            StackMapEntry(4, listOf(
                StackMapLocation.Register(0),
                StackMapLocation.Stack(-16),
            )),
        ))

        val code = CompiledCode(
            textBytes = byteArrayOf(
                0x48, 0x89.toByte(), 0xF8.toByte(), // mov rax, rdi
                0x48, 0x01, 0xF0.toByte(),           // add rax, rsi
                0xC3.toByte(),                        // ret
            ),
            symbols = listOf(CompiledCode.CodeSymbol("add", 0)),
            stackMaps = listOf(stackMap),
        )

        // Use reflection to call loadCompiledCode since it's private
        val method = JitEngine::class.java.getDeclaredMethod(
            "loadCompiledCode", String::class.java, CompiledCode::class.java)
        method.isAccessible = true
        method.invoke(jit, "test", code)

        // Verify stack map was registered
        val maps = gc.stackMaps()
        assertEquals(1, maps.size)
        assertNotNull(maps["add"])

        jit.close()
        heap.close()
    }

    @Test
    fun gcCollectsWithStackMapRoots() {
        val heap = BumpHeap(8192)
        val registry = TypeRegistry()
        val layout = ObjectLayout("Obj", 8,
            listOf(FieldDescriptor("value", 0, 8, false)), typeId = 1)
        registry.register(layout)

        val gc = MarkSweepGC(heap, registry)
        val obj = heap.allocate(layout)
        heap.writeField(obj, 0, 42L)

        // Register a stack map that has a Constant location pointing to the object
        val stackMap = StackMap("main", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Constant(obj))),
        ))
        gc.registerStackMap(stackMap)

        // Create an execution context with a "main" frame
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("main", 0)
        gc.addExecutionContext(ctx)

        // Collect — the object should be found as a root via stack map constant
        gc.collect()
        assertEquals(1L, gc.collectionCount())
        assertEquals(42L, heap.readField(obj, 0))

        ctx.popFrame()
        heap.close()
    }

    @Test
    fun gcSkipsFramesWithoutStackMaps() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val ctx = ThreadExecutionContext()
        ctx.pushFrame("unknownFunc", 0)
        gc.addExecutionContext(ctx)

        // Should not crash even though no stack map exists for "unknownFunc"
        gc.collect()
        assertEquals(1L, gc.collectionCount())

        ctx.popFrame()
        heap.close()
    }

    @Test
    fun multipleStackMapsRegistered() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        gc.registerStackMap(StackMap("func1", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Register(0))),
        )))
        gc.registerStackMap(StackMap("func2", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Stack(-8))),
            StackMapEntry(12, listOf(StackMapLocation.Stack(-16))),
        )))
        gc.registerStackMap(StackMap("func3", emptyList()))

        val maps = gc.stackMaps()
        assertEquals(3, maps.size)
        assertEquals(1, maps["func1"]!!.entries.size)
        assertEquals(2, maps["func2"]!!.entries.size)
        assertEquals(0, maps["func3"]!!.entries.size)

        heap.close()
    }

    @Test
    fun executionContextManagement() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val ctx1 = ThreadExecutionContext()
        val ctx2 = ThreadExecutionContext()

        gc.addExecutionContext(ctx1)
        assertEquals(1, gc.executionContextCount())

        gc.addExecutionContext(ctx2)
        assertEquals(2, gc.executionContextCount())

        gc.removeExecutionContext(ctx1)
        assertEquals(1, gc.executionContextCount())

        gc.removeExecutionContext(ctx2)
        assertEquals(0, gc.executionContextCount())

        heap.close()
    }

    @Test
    fun stackMapConstantRootsTraceReferences() {
        val heap = BumpHeap(8192)
        val registry = TypeRegistry()
        val refLayout = ObjectLayout("Ref", 16, listOf(
            FieldDescriptor("value", 0, 8, false),
            FieldDescriptor("next", 8, 8, true),
        ), typeId = 1)
        registry.register(refLayout)

        val gc = MarkSweepGC(heap, registry)

        // Allocate a chain: a → b
        val a = heap.allocate(refLayout)
        val b = heap.allocate(refLayout)
        heap.writeField(a, 0, 10L)
        heap.writeField(b, 0, 20L)

        // Write reference: a.next = b
        val nextAddr = a + ObjectLayout.HEADER_SIZE + 8
        val addrBytes = ByteArray(8)
        for (i in 0 until 8) addrBytes[i] = (b shr (i * 8)).toByte()
        heap.writeBytes(nextAddr, addrBytes)

        // Stack map with constant pointing to 'a'
        val stackMap = StackMap("buildChain", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Constant(a))),
        ))
        gc.registerStackMap(stackMap)

        val ctx = ThreadExecutionContext()
        ctx.pushFrame("buildChain", 0)
        gc.addExecutionContext(ctx)

        gc.collect()

        // Both a and b should survive — b is reachable through a.next
        assertEquals(10L, heap.readField(a, 0))
        assertEquals(20L, heap.readField(b, 0))

        ctx.popFrame()
        heap.close()
    }

    @Test
    fun nullConstantLocationSkipped() {
        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        // Stack map with null constant (value = 0)
        gc.registerStackMap(StackMap("func", listOf(
            StackMapEntry(0, listOf(StackMapLocation.Constant(0L))),
        )))

        val ctx = ThreadExecutionContext()
        ctx.pushFrame("func", 0)
        gc.addExecutionContext(ctx)

        // Should not crash — null constants are skipped
        gc.collect()
        assertEquals(1L, gc.collectionCount())

        ctx.popFrame()
        heap.close()
    }

    @Test
    fun closeJitEngineNullsGCAndSafepoints() {
        val runtime = DefaultManagedRuntime.create(4096)
        val gen = StubCodeGenerator()
        val jit = JitEngine(gen)

        jit.setRuntime(runtime)
        assertNotNull(jit.gc())
        assertNotNull(jit.safepointManager())

        jit.close()
        assertNull(jit.gc())
        assertNull(jit.safepointManager())

        runtime.close()
    }

    @Test
    fun safepointCoordinationDuringGC() {
        val runtime = DefaultManagedRuntime.create(8192)
        val layout = ObjectLayout("Obj", 8,
            listOf(FieldDescriptor("x", 0, 8, false)), typeId = 1)
        runtime.typeRegistry().register(layout)

        val ctx = runtime.currentContext()
        ctx.pushFrame("worker", 0)

        val obj = runtime.heap().allocate(layout)
        runtime.heap().writeField(obj, 0, 99L)
        runtime.gc().addRootProvider { visitor -> visitor.visitRoot(obj) }

        // Simulate safepoint-coordinated GC
        val safepoints = runtime.safepoints()
        ctx.enterSafepoint()
        assertTrue(ctx.isAtSafepoint())

        runtime.gc().collect()

        ctx.leaveSafepoint()
        assertFalse(ctx.isAtSafepoint())

        assertEquals(99L, runtime.heap().readField(obj, 0))

        ctx.popFrame()
        runtime.close()
    }
}

/**
 * Stub code generator for testing — throws UnsupportedOperationException
 * so JitEngine falls through to the ObjectFile path or fails gracefully.
 */
private class StubCodeGenerator : org.kgen.codegen.CodeGenerator {
    override val targetName: String = "x86_64"
    override fun generate(module: org.kgen.ir.Module, options: org.kgen.codegen.CodeGenOptions): ByteArray {
        throw UnsupportedOperationException("Stub")
    }
}

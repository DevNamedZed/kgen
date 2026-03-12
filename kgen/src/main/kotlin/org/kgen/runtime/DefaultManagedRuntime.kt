package org.kgen.runtime

import org.kgen.runtime.exec.*
import org.kgen.runtime.gc.*
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Default implementation of [ManagedRuntime] wiring all subsystems together.
 *
 * ```java
 * var runtime = DefaultManagedRuntime.create(heapSize);
 * long obj = runtime.heap().allocate(layout);
 * runtime.gc().collect();
 * runtime.close();
 * ```
 */
class DefaultManagedRuntime private constructor(
    private val heap: BumpHeap,
    private val typeRegistry: TypeRegistry,
    private val gc: MarkSweepGC,
    private val safepoints: BasicSafepointManager,
    private val dispatch: BasicMethodDispatch,
) : ManagedRuntime {

    private val threadContexts = ThreadLocal.withInitial { ThreadExecutionContext() }
    private val upcallArena = Arena.ofShared()

    override fun heap(): HeapManager = heap

    override fun gc(): GarbageCollector = gc

    override fun safepoints(): SafepointManager = safepoints

    override fun dispatch(): MethodDispatch = dispatch

    override fun currentContext(): ExecutionContext {
        val ctx = threadContexts.get()
        safepoints.registerThread(ctx)
        return ctx
    }

    /** The type registry used by the GC. */
    fun typeRegistry(): TypeRegistry = typeRegistry

    override fun installInto(symbolResolver: (String, Long) -> Unit) {
        val linker = Linker.nativeLinker()
        val lookup = MethodHandles.lookup()

        // kgen_alloc(typeId: int, size: int) -> long (address of allocated object)
        val allocHandle = lookup.bind(this, "runtimeAlloc",
            MethodType.methodType(Long::class.java, Int::class.java, Int::class.java))
        val allocStub = linker.upcallStub(allocHandle,
            FunctionDescriptor.of(JAVA_LONG, JAVA_INT, JAVA_INT), upcallArena)
        symbolResolver("kgen_alloc", allocStub.address())

        // kgen_gc_collect() -> void
        val gcHandle = lookup.bind(this, "runtimeGcCollect",
            MethodType.methodType(Void.TYPE))
        val gcStub = linker.upcallStub(gcHandle,
            FunctionDescriptor.ofVoid(), upcallArena)
        symbolResolver("kgen_gc_collect", gcStub.address())

        // kgen_safepoint_poll() -> void
        val pollHandle = lookup.bind(this, "runtimeSafepointPoll",
            MethodType.methodType(Void.TYPE))
        val pollStub = linker.upcallStub(pollHandle,
            FunctionDescriptor.ofVoid(), upcallArena)
        symbolResolver("kgen_safepoint_poll", pollStub.address())

        // __kgen_rt_write_barrier(obj: long, fieldIndex: long, value: long) -> void
        val wbHandle = lookup.bind(this, "runtimeWriteBarrier",
            MethodType.methodType(Void.TYPE, Long::class.java, Long::class.java, Long::class.java))
        val wbStub = linker.upcallStub(wbHandle,
            FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG, JAVA_LONG), upcallArena)
        symbolResolver("__kgen_rt_write_barrier", wbStub.address())

        // __kgen_rt_read_barrier(ref: long) -> long
        val rbHandle = lookup.bind(this, "runtimeReadBarrier",
            MethodType.methodType(Long::class.java, Long::class.java))
        val rbStub = linker.upcallStub(rbHandle,
            FunctionDescriptor.of(JAVA_LONG, JAVA_LONG), upcallArena)
        symbolResolver("__kgen_rt_read_barrier", rbStub.address())
    }

    /** Called from JIT'd code via upcall: allocate an object with the given type and size. */
    fun runtimeAlloc(typeId: Int, size: Int): Long {
        val layout = ObjectLayout("jit_type_$typeId", size, emptyList(), typeId)
        return heap.allocate(layout)
    }

    /** Called from JIT'd code via upcall: trigger a GC collection. */
    fun runtimeGcCollect() {
        gc.collect()
    }

    /** Called from JIT'd code via upcall: check the safepoint. */
    fun runtimeSafepointPoll() {
        safepoints.poll()
    }

    /** Called from JIT'd code via upcall: notify GC that a reference field was written. */
    fun runtimeWriteBarrier(obj: Long, fieldIndex: Long, value: Long) {
        gc.writeBarrier(obj, fieldIndex.toInt(), value)
    }

    /** Called from JIT'd code via upcall: read barrier for concurrent/relocating GC. */
    fun runtimeReadBarrier(ref: Long): Long {
        return gc.readBarrier(ref)
    }

    override fun close() {
        upcallArena.close()
        heap.close()
    }

    companion object {
        /** Create a runtime with the given heap size. */
        @JvmStatic
        fun create(heapSize: Long): DefaultManagedRuntime {
            val heap = BumpHeap(heapSize)
            val registry = TypeRegistry()
            val gc = MarkSweepGC(heap, registry)
            val safepoints = BasicSafepointManager()
            val dispatch = BasicMethodDispatch()
            return DefaultManagedRuntime(heap, registry, gc, safepoints, dispatch)
        }
    }
}

package org.kgen.jit.runtime

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
        // Runtime allocation function — JIT'd code calls this to allocate objects
        // For now, expose the heap base address so code can call into runtime
    }

    override fun close() {
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

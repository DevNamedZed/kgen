package org.kgen.jit.runtime

/**
 * Top-level managed runtime for JIT-compiled code. Owns all subsystems
 * and provides a unified entry point for language implementations.
 *
 * ```java
 * var runtime = ManagedRuntime.create(config);
 * runtime.heap().allocate(layout);
 * runtime.gc().collect();
 * runtime.dispatch().resolve("main");
 * runtime.close();
 * ```
 *
 * Subsystem responsibilities:
 * - [HeapManager]: object allocation and field access
 * - [GarbageCollector]: tracing, reclaiming, and compacting
 * - [SafepointManager]: coordinating stop-the-world pauses
 * - [MethodDispatch]: function resolution, vtable/itable dispatch
 * - [ExecutionContext]: per-thread frames, exceptions, safepoints
 */
interface ManagedRuntime : AutoCloseable {

    /** The heap for object allocation. */
    fun heap(): HeapManager

    /** The garbage collector. */
    fun gc(): GarbageCollector

    /** The safepoint coordinator. */
    fun safepoints(): SafepointManager

    /** Method dispatch (direct, virtual, interface). */
    fun dispatch(): MethodDispatch

    /** Get or create the execution context for the current thread. */
    fun currentContext(): ExecutionContext

    /** Install this runtime's symbols into a JIT engine (runtime function stubs). */
    fun installInto(symbolResolver: (String, Long) -> Unit)
}

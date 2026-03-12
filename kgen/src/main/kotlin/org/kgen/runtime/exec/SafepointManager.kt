package org.kgen.runtime.exec

/**
 * Coordinates safepoints across all threads running compiled code.
 * A safepoint is a location in generated code where the thread is guaranteed
 * to be in a consistent state — the GC can safely inspect the stack and registers.
 *
 * ```java
 * SafepointManager safepoints = runtime.safepoints();
 * safepoints.requestStop();      // ask all threads to reach a safepoint
 * safepoints.waitForAllStopped(); // block until all threads are stopped
 * gc.collect();                   // safe to collect now
 * safepoints.resumeAll();         // let threads continue
 * ```
 */
interface SafepointManager {

    /** Request all threads to stop at their next safepoint. */
    fun requestStop()

    /** Wait until all registered threads have reached a safepoint. */
    fun waitForAllStopped()

    /** Resume all stopped threads. */
    fun resumeAll()

    /** Register a thread with the safepoint manager. */
    fun registerThread(context: ExecutionContext)

    /** Unregister a thread. */
    fun unregisterThread(context: ExecutionContext)

    /** Check if a stop-the-world pause is currently requested. */
    fun isStopRequested(): Boolean

    /** Called by compiled code at a safepoint — polls and blocks if a stop is requested. */
    fun poll()

    /** The address of the safepoint poll page. Compiled code loads from this address;
     *  when a stop is requested, the page is protected to trigger a fault. */
    fun pollPageAddress(): Long
}

package org.kgen.runtime.exec

import org.kgen.reflect.NativeMemory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Safepoint manager using page-fault-based polling.
 *
 * Allocates a single memory page (the "poll page") that compiled code loads from
 * at every safepoint. When a stop-the-world pause is requested, the poll page is
 * mprotected to PROT_NONE, causing any safepoint load to trigger a SIGSEGV (Unix)
 * or ACCESS_VIOLATION (Windows). The runtime's signal handler catches the fault,
 * identifies it as a safepoint trap, and enters the safepoint slow path.
 *
 * **Advantages over cooperative polling:**
 * - Zero overhead in the fast path (a single load instruction, no branch)
 * - No cache pollution from checking a volatile flag
 * - Works even if a thread is in a tight loop with no call sites
 *
 * **Signal handler setup:**
 * The signal handler must be installed separately by the native runtime or JIT.
 * When a SIGSEGV occurs at `pollPageAddress()`, the handler should call [enterSafepoint]
 * on the faulting thread's context, then block until [resumeAll] restores the page.
 *
 * ```java
 * var sp = new PageFaultSafepointManager();
 * long pollAddr = sp.pollPageAddress(); // pass to code generator / stub generator
 * sp.registerThread(ctx);
 * sp.requestStop();                     // arms the page — next poll load faults
 * sp.waitForAllStopped();
 * gc.collect();
 * sp.resumeAll();                       // disarms the page — threads resume
 * sp.close();                           // free the poll page
 * ```
 */
class PageFaultSafepointManager : SafepointManager, AutoCloseable {

    private val threads = CopyOnWriteArrayList<ExecutionContext>()
    private val lock = ReentrantLock()
    private val allStopped = lock.newCondition()
    private val resumed = lock.newCondition()
    @Volatile private var stopRequested = false
    @Volatile private var armed = false

    // Must use allocateExecutable (mmap/VirtualAlloc) — not arena.allocate — because
    // mprotect only works on OS-level memory mappings, not JVM-managed allocations.
    private val pollPage: NativeMemory = NativeMemory.allocateExecutable(PAGE_SIZE)

    override fun requestStop() {
        lock.withLock {
            stopRequested = true
            arm()
        }
    }

    override fun waitForAllStopped() {
        lock.withLock {
            val deadline = System.nanoTime() + safepointTimeoutNanos
            while (!allThreadsStopped()) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    throw IllegalStateException(
                        "Safepoint timeout: not all threads reached safepoint within ${safepointTimeoutNanos / 1_000_000}ms"
                    )
                }
                allStopped.await(remaining, java.util.concurrent.TimeUnit.NANOSECONDS)
            }
        }
    }

    /** Safepoint timeout in nanoseconds. Default 5 seconds. */
    var safepointTimeoutNanos: Long = 5_000_000_000L

    override fun resumeAll() {
        lock.withLock {
            disarm()
            stopRequested = false
            resumed.signalAll()
        }
    }

    override fun registerThread(context: ExecutionContext) {
        threads.add(context)
    }

    override fun unregisterThread(context: ExecutionContext) {
        threads.remove(context)
    }

    override fun isStopRequested(): Boolean = stopRequested

    override fun poll() {
        if (!stopRequested) return
        lock.withLock {
            allStopped.signalAll()
            while (stopRequested) {
                resumed.await()
            }
        }
    }

    override fun pollPageAddress(): Long = pollPage.address

    /**
     * Called by the signal handler when a thread faults on the poll page.
     * Enters the safepoint slow path: signals the collector and blocks until resume.
     *
     * The signal handler should:
     * 1. Verify the fault address is within `[pollPageAddress, pollPageAddress + PAGE_SIZE)`
     * 2. Call this method on the faulting thread
     * 3. After this method returns, retry the faulting instruction (the page is now readable)
     */
    fun handleFault() {
        lock.withLock {
            allStopped.signalAll()
            while (stopRequested) {
                resumed.await()
            }
        }
    }

    /** Whether the poll page is currently armed (protected). */
    fun isArmed(): Boolean = armed

    /** Number of registered threads. */
    fun threadCount(): Int = threads.size

    /**
     * Arm the poll page by removing all access permissions.
     * Any load from the page will trigger a fault.
     */
    private fun arm() {
        if (!armed) {
            NativeMemory.mprotect(pollPage.address, PAGE_SIZE, 0) // PROT_NONE
            armed = true
        }
    }

    /**
     * Disarm the poll page by restoring read permission.
     * Loads from the page will succeed without faulting.
     */
    private fun disarm() {
        if (armed) {
            NativeMemory.mprotect(pollPage.address, PAGE_SIZE, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE)
            armed = false
        }
    }

    override fun close() {
        if (armed) {
            disarm()
        }
        pollPage.close()
    }

    private fun allThreadsStopped(): Boolean {
        for (ctx in threads) {
            if (ctx.isInManagedCode() && !ctx.isAtSafepoint()) {
                return false
            }
        }
        return true
    }

    companion object {
        /** Page size used for the poll page. Standard 4KB page. */
        const val PAGE_SIZE = 4096L
    }
}

package org.kgen.jit.runtime

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Basic safepoint manager using cooperative polling. Threads check a flag
 * at safepoint locations and block until the collector is done.
 *
 * For production use, this could be extended with page-fault-based polling
 * (mprotect the poll page to trigger a SIGSEGV at safepoints).
 *
 * ```java
 * var sp = new BasicSafepointManager();
 * sp.registerThread(ctx);
 * sp.requestStop();
 * sp.waitForAllStopped();
 * gc.collect();
 * sp.resumeAll();
 * ```
 */
class BasicSafepointManager : SafepointManager {

    private val threads = CopyOnWriteArrayList<ExecutionContext>()
    private val lock = ReentrantLock()
    private val allStopped = lock.newCondition()
    private val resumed = lock.newCondition()
    @Volatile private var stopRequested = false

    override fun requestStop() {
        lock.withLock {
            stopRequested = true
        }
    }

    override fun waitForAllStopped() {
        lock.withLock {
            while (!allThreadsStopped()) {
                allStopped.await()
            }
        }
    }

    override fun resumeAll() {
        lock.withLock {
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
            // Signal that we've reached a safepoint
            allStopped.signalAll()
            // Wait until the pause is over
            while (stopRequested) {
                resumed.await()
            }
        }
    }

    override fun pollPageAddress(): Long = 0L // Not using page-fault polling yet

    /** Number of registered threads. */
    fun threadCount(): Int = threads.size

    private fun allThreadsStopped(): Boolean {
        for (ctx in threads) {
            if (ctx.isInManagedCode() && !ctx.isAtSafepoint()) return false
        }
        return true
    }
}

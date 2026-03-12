package org.kgen.runtime.exec

import org.kgen.runtime.*
import org.kgen.runtime.gc.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyTest {

    @Test
    fun threadLocalExecutionContexts() {
        val runtime = DefaultManagedRuntime.create(4096)
        val barrier = CyclicBarrier(3)
        val errors = AtomicInteger(0)

        val threads = (0 until 3).map { threadId ->
            Thread {
                try {
                    val ctx = runtime.currentContext()
                    ctx.pushFrame("thread_${threadId}_main", 0)
                    barrier.await()

                    // Each thread should have its own context
                    assertEquals(1, ctx.depth())
                    ctx.pushFrame("thread_${threadId}_inner", 100)
                    assertEquals(2, ctx.depth())

                    ctx.popFrame()
                    ctx.popFrame()
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
        assertEquals(0, errors.get())
        runtime.close()
    }

    @Test
    fun safepointManagerMultipleThreads() {
        val sp = BasicSafepointManager()
        val contexts = (0 until 4).map { ThreadExecutionContext() }
        contexts.forEach { sp.registerThread(it) }

        assertEquals(4, sp.threadCount())

        // All threads enter safepoint
        contexts.forEach { it.enterSafepoint() }

        sp.requestStop()
        sp.waitForAllStopped() // all at safepoint, should complete
        sp.resumeAll()

        contexts.forEach { it.leaveSafepoint() }
        assertFalse(sp.isStopRequested())
    }

    @Test
    fun safepointWithNativeThreads() {
        val sp = BasicSafepointManager()
        val managed = ThreadExecutionContext()
        val native1 = ThreadExecutionContext()
        val native2 = ThreadExecutionContext()

        native1.enterNative()
        native2.enterNative()
        managed.enterSafepoint()

        sp.registerThread(managed)
        sp.registerThread(native1)
        sp.registerThread(native2)

        sp.requestStop()
        sp.waitForAllStopped() // managed is at safepoint, natives are in native mode
        sp.resumeAll()
    }

    @Test
    fun concurrentSafepointPolling() {
        val sp = BasicSafepointManager()
        val latch = CountDownLatch(1)
        val polled = AtomicInteger(0)

        // When not stopped, poll should return immediately
        val threads = (0 until 5).map {
            Thread {
                latch.await()
                sp.poll()
                polled.incrementAndGet()
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }
        assertEquals(5, polled.get())
    }

    @Test
    fun concurrentMethodDispatch() {
        val dispatch = BasicMethodDispatch()
        for (i in 0 until 100) {
            dispatch.register("method_$i", (i * 0x100).toLong())
        }

        val errors = AtomicInteger(0)
        val barrier = CyclicBarrier(4)

        val threads = (0 until 4).map { threadId ->
            Thread {
                try {
                    barrier.await()
                    for (i in 0 until 100) {
                        val addr = dispatch.resolve("method_$i")
                        assertEquals((i * 0x100).toLong(), addr)
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
        assertEquals(0, errors.get())
    }

    @Test
    fun executionContextIndependentPerThread() {
        val errors = AtomicInteger(0)
        val latch = CountDownLatch(1)

        val threads = (0 until 5).map { threadId ->
            Thread {
                latch.await()
                try {
                    val ctx = ThreadExecutionContext()
                    for (i in 0 until 50) {
                        ctx.pushFrame("t${threadId}_f$i", i.toLong())
                    }
                    assertEquals(50, ctx.depth())
                    for (i in 0 until 50) {
                        ctx.popFrame()
                    }
                    assertEquals(0, ctx.depth())
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }

        threads.forEach { it.start() }
        latch.countDown()
        threads.forEach { it.join(5000) }
        assertEquals(0, errors.get())
    }
}

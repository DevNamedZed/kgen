package org.kgen.jit.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BasicSafepointManagerTest {

    @Test
    fun initiallyNotStopped() {
        val sp = BasicSafepointManager()
        assertFalse(sp.isStopRequested())
    }

    @Test
    fun requestStopSetsFlag() {
        val sp = BasicSafepointManager()
        sp.requestStop()
        assertTrue(sp.isStopRequested())
    }

    @Test
    fun resumeAllClearsFlag() {
        val sp = BasicSafepointManager()
        sp.requestStop()
        sp.resumeAll()
        assertFalse(sp.isStopRequested())
    }

    @Test
    fun registerAndUnregisterThread() {
        val sp = BasicSafepointManager()
        val ctx = ThreadExecutionContext()
        sp.registerThread(ctx)
        assertEquals(1, sp.threadCount())
        sp.unregisterThread(ctx)
        assertEquals(0, sp.threadCount())
    }

    @Test
    fun multipleThreadRegistration() {
        val sp = BasicSafepointManager()
        val ctx1 = ThreadExecutionContext()
        val ctx2 = ThreadExecutionContext()
        sp.registerThread(ctx1)
        sp.registerThread(ctx2)
        assertEquals(2, sp.threadCount())
    }

    @Test
    fun pollWhenNotStoppedDoesNothing() {
        val sp = BasicSafepointManager()
        sp.poll() // should return immediately
        assertFalse(sp.isStopRequested())
    }

    @Test
    fun waitWithNoThreadsCompletes() {
        val sp = BasicSafepointManager()
        sp.requestStop()
        sp.waitForAllStopped() // no threads, so instantly done
        sp.resumeAll()
    }

    @Test
    fun waitWithSafepointedThreadCompletes() {
        val sp = BasicSafepointManager()
        val ctx = ThreadExecutionContext()
        ctx.enterSafepoint()
        sp.registerThread(ctx)
        sp.requestStop()
        sp.waitForAllStopped() // thread is at safepoint, so done
        sp.resumeAll()
    }

    @Test
    fun waitWithNativeThreadCompletes() {
        val sp = BasicSafepointManager()
        val ctx = ThreadExecutionContext()
        ctx.enterNative()
        sp.registerThread(ctx)
        sp.requestStop()
        sp.waitForAllStopped() // thread is in native code, safe
        sp.resumeAll()
    }

    @Test
    fun pollPageAddressReturnsZero() {
        val sp = BasicSafepointManager()
        assertEquals(0L, sp.pollPageAddress())
    }
}

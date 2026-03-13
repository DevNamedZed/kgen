package org.kgen.runtime.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.NativeMemory
class PageFaultSafepointManagerTest {

    @Test
    fun pollPageAddressIsNonZero() {
        PageFaultSafepointManager().use { sp ->
            assertTrue(sp.pollPageAddress() != 0L, "Poll page should have a valid address")
        }
    }

    @Test
    fun pollPageIsReadableInitially() {
        PageFaultSafepointManager().use { sp ->
            // Should be able to read from the poll page without faulting
            val bytes = NativeMemory.readBytes(sp.pollPageAddress(), 4)
            assertNotNull(bytes)
        }
    }

    @Test
    fun initiallyNotArmed() {
        PageFaultSafepointManager().use { sp ->
            assertFalse(sp.isArmed())
            assertFalse(sp.isStopRequested())
        }
    }

    @Test
    fun requestStopArmsPage() {
        PageFaultSafepointManager().use { sp ->
            sp.requestStop()
            assertTrue(sp.isArmed())
            assertTrue(sp.isStopRequested())
            sp.resumeAll() // disarm before close
        }
    }

    @Test
    fun resumeAllDisarmsPage() {
        PageFaultSafepointManager().use { sp ->
            sp.requestStop()
            assertTrue(sp.isArmed())
            sp.resumeAll()
            assertFalse(sp.isArmed())
            assertFalse(sp.isStopRequested())
        }
    }

    @Test
    fun resumeAllRestoresReadability() {
        PageFaultSafepointManager().use { sp ->
            sp.requestStop()
            sp.resumeAll()
            // Should be readable again
            val bytes = NativeMemory.readBytes(sp.pollPageAddress(), 4)
            assertNotNull(bytes)
        }
    }

    @Test
    fun registerAndUnregisterThread() {
        PageFaultSafepointManager().use { sp ->
            val ctx = ThreadExecutionContext()
            sp.registerThread(ctx)
            assertEquals(1, sp.threadCount())
            sp.unregisterThread(ctx)
            assertEquals(0, sp.threadCount())
        }
    }

    @Test
    fun pollWhenNotStoppedReturnsImmediately() {
        PageFaultSafepointManager().use { sp ->
            sp.poll() // should return immediately
            assertFalse(sp.isStopRequested())
        }
    }

    @Test
    fun waitWithNoThreadsCompletes() {
        PageFaultSafepointManager().use { sp ->
            sp.requestStop()
            sp.waitForAllStopped() // no threads — instant
            sp.resumeAll()
        }
    }

    @Test
    fun waitWithSafepointedThreadCompletes() {
        PageFaultSafepointManager().use { sp ->
            val ctx = ThreadExecutionContext()
            ctx.enterSafepoint()
            sp.registerThread(ctx)
            sp.requestStop()
            sp.waitForAllStopped()
            sp.resumeAll()
        }
    }

    @Test
    fun waitWithNativeThreadCompletes() {
        PageFaultSafepointManager().use { sp ->
            val ctx = ThreadExecutionContext()
            ctx.enterNative()
            sp.registerThread(ctx)
            sp.requestStop()
            sp.waitForAllStopped()
            sp.resumeAll()
        }
    }

    @Test
    fun timeoutWhenThreadNeverReachesSafepoint() {
        PageFaultSafepointManager().use { sp ->
            sp.safepointTimeoutNanos = 1_000_000L // 1ms
            val ctx = ThreadExecutionContext()
            sp.registerThread(ctx)
            sp.requestStop()
            assertThrows(IllegalStateException::class.java) {
                sp.waitForAllStopped()
            }
            sp.resumeAll() // disarm
        }
    }

    @Test
    fun handleFaultBlocksAndResumes() {
        PageFaultSafepointManager().use { sp ->
            sp.requestStop()

            // Simulate signal handler calling handleFault on another thread
            val thread = Thread {
                sp.handleFault()
            }
            thread.start()

            // Give the thread time to enter handleFault and block
            Thread.sleep(50)

            sp.resumeAll()
            thread.join(1000)
            assertFalse(thread.isAlive, "Thread should have been released by resumeAll")
        }
    }

    @Test
    fun multipleArmDisarmCycles() {
        PageFaultSafepointManager().use { sp ->
            for (i in 0 until 5) {
                sp.requestStop()
                assertTrue(sp.isArmed())
                sp.resumeAll()
                assertFalse(sp.isArmed())
                // Verify page is readable after disarm
                val bytes = NativeMemory.readBytes(sp.pollPageAddress(), 4)
                assertNotNull(bytes)
            }
        }
    }

    @Test
    fun closeDisarmsIfArmed() {
        val sp = PageFaultSafepointManager()
        sp.requestStop()
        assertTrue(sp.isArmed())
        sp.close() // should disarm and free without error
    }

    @Test
    fun pollPageAddressUsableByStubGenerator() {
        PageFaultSafepointManager().use { sp ->
            val addr = sp.pollPageAddress()
            // Address should be page-aligned (from allocateReadWrite)
            assertTrue(addr != 0L)
            // Should be usable as the pollAddress parameter to stub generators
            assertTrue(addr > 0)
        }
    }

    @Test
    fun cooperativePollStillWorksWhenArmed() {
        PageFaultSafepointManager().use { sp ->
            val ctx = ThreadExecutionContext()
            sp.registerThread(ctx)
            sp.requestStop()

            // Simulate a thread that cooperatively polls (Java-side)
            val thread = Thread {
                sp.poll()
            }
            thread.start()
            Thread.sleep(50)

            sp.resumeAll()
            thread.join(1000)
            assertFalse(thread.isAlive)
        }
    }
}

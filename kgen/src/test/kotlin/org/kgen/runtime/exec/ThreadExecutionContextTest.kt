package org.kgen.runtime.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ThreadExecutionContextTest {

    @Test
    fun emptyStackHasZeroDepth() {
        val ctx = ThreadExecutionContext()
        assertEquals(0, ctx.depth())
    }

    @Test
    fun pushIncrementsDepth() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("main", 0)
        assertEquals(1, ctx.depth())
        ctx.pushFrame("foo", 100)
        assertEquals(2, ctx.depth())
    }

    @Test
    fun popDecrementsDepth() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("main", 0)
        ctx.pushFrame("foo", 100)
        ctx.popFrame()
        assertEquals(1, ctx.depth())
    }

    @Test
    fun popOnEmptyThrows() {
        val ctx = ThreadExecutionContext()
        assertThrows(IllegalStateException::class.java) {
            ctx.popFrame()
        }
    }

    @Test
    fun walkStackVisitsTopFirst() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("main", 0)
        ctx.pushFrame("foo", 100)
        ctx.pushFrame("bar", 200)

        val names = mutableListOf<String>()
        ctx.walkStack { frame -> names.add(frame.functionName) }
        assertEquals(listOf("bar", "foo", "main"), names)
    }

    @Test
    fun walkEmptyStackVisitsNothing() {
        val ctx = ThreadExecutionContext()
        var visited = false
        ctx.walkStack { visited = true }
        assertFalse(visited)
    }

    @Test
    fun topFrameReturnsLastPush() {
        val ctx = ThreadExecutionContext()
        assertNull(ctx.topFrame())
        ctx.pushFrame("main", 0)
        assertEquals("main", ctx.topFrame()!!.functionName)
        ctx.pushFrame("inner", 42)
        assertEquals("inner", ctx.topFrame()!!.functionName)
    }

    @Test
    fun allFramesReturnsTopFirst() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("a", 0)
        ctx.pushFrame("b", 1)
        ctx.pushFrame("c", 2)
        val names = ctx.allFrames().map { it.functionName }
        assertEquals(listOf("c", "b", "a"), names)
    }

    @Test
    fun exceptionStateInitiallyNull() {
        val ctx = ThreadExecutionContext()
        assertNull(ctx.currentException())
    }

    @Test
    fun setAndClearException() {
        val ctx = ThreadExecutionContext()
        val ex = RuntimeException("test")
        ctx.setException(ex)
        assertSame(ex, ctx.currentException())
        ctx.clearException()
        assertNull(ctx.currentException())
    }

    @Test
    fun safepointStateInitiallyFalse() {
        val ctx = ThreadExecutionContext()
        assertFalse(ctx.isAtSafepoint())
    }

    @Test
    fun enterAndLeaveSafepoint() {
        val ctx = ThreadExecutionContext()
        ctx.enterSafepoint()
        assertTrue(ctx.isAtSafepoint())
        ctx.leaveSafepoint()
        assertFalse(ctx.isAtSafepoint())
    }

    @Test
    fun managedStateInitiallyTrue() {
        val ctx = ThreadExecutionContext()
        assertTrue(ctx.isInManagedCode())
    }

    @Test
    fun enterNativeAndBackToManaged() {
        val ctx = ThreadExecutionContext()
        ctx.enterNative()
        assertFalse(ctx.isInManagedCode())
        ctx.enterManaged()
        assertTrue(ctx.isInManagedCode())
    }

    @Test
    fun frameDepthIsCorrect() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("a", 0)
        ctx.pushFrame("b", 1)

        val depths = mutableListOf<Int>()
        ctx.walkStack { frame -> depths.add(frame.depth) }
        assertEquals(listOf(1, 0), depths) // top first: b=1, a=0
    }

    @Test
    fun returnAddressPreserved() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("main", 0x1234L)
        ctx.pushFrame("inner", 0x5678L)

        val addresses = mutableListOf<Long>()
        ctx.walkStack { frame -> addresses.add(frame.returnAddress) }
        assertEquals(listOf(0x5678L, 0x1234L), addresses)
    }

    @Test
    fun pushPopPushWorks() {
        val ctx = ThreadExecutionContext()
        ctx.pushFrame("a", 0)
        ctx.pushFrame("b", 1)
        ctx.popFrame()
        ctx.pushFrame("c", 2)
        assertEquals(2, ctx.depth())
        assertEquals("c", ctx.topFrame()!!.functionName)
    }

    @Test
    fun manyFrames() {
        val ctx = ThreadExecutionContext()
        for (i in 0 until 100) {
            ctx.pushFrame("frame_$i", i.toLong())
        }
        assertEquals(100, ctx.depth())
        for (i in 99 downTo 0) {
            assertEquals("frame_$i", ctx.topFrame()!!.functionName)
            ctx.popFrame()
        }
        assertEquals(0, ctx.depth())
    }

    @Test
    fun multipleExceptions() {
        val ctx = ThreadExecutionContext()
        val ex1 = RuntimeException("first")
        val ex2 = RuntimeException("second")
        ctx.setException(ex1)
        assertSame(ex1, ctx.currentException())
        ctx.setException(ex2)
        assertSame(ex2, ctx.currentException())
    }
}

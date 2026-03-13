package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NativeFrameTest {

    @Nested
    inner class PushFrameWithNativeInfo {

        @Test
        fun pushWithBasePointerAndRegisterSaveArea() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("func", 0x1000, 0x7FFF0000, 0x7FFF1000)
            assertEquals(1, ctx.depth())
            val frame = ctx.topFrame()!!
            assertEquals("func", frame.functionName)
            assertEquals(0x1000L, frame.returnAddress)
            assertEquals(0x7FFF0000L, frame.basePointer)
            assertEquals(0x7FFF1000L, frame.registerSaveArea)
        }

        @Test
        fun frameDepthSetCorrectly() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("a", 0)
            ctx.pushFrame("b", 100, 0x5000, 0x6000)
            val frame = ctx.topFrame()!!
            assertEquals(1, frame.depth)
        }

        @Test
        fun walkStackIncludesNativeFrameInfo() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("main", 0)
            ctx.pushFrame("native_call", 0x2000, 0xBEEF, 0xCAFE)

            val basePointers = mutableListOf<Long>()
            ctx.walkStack { frame ->
                basePointers.add(frame.basePointer)
            }
            // top first: native_call has bp, main has 0
            assertEquals(0xBEEFL, basePointers[0])
            assertEquals(0L, basePointers[1])
        }

        @Test
        fun mixSimpleAndNativeFrames() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("simple", 0x100)
            ctx.pushFrame("native", 0x200, 0xAAAA, 0xBBBB)
            ctx.pushFrame("simple2", 0x300)

            val frames = ctx.allFrames()
            assertEquals(3, frames.size)
            assertEquals("simple2", frames[0].functionName)
            assertEquals(0L, frames[0].basePointer)
            assertEquals("native", frames[1].functionName)
            assertEquals(0xAAAAL, frames[1].basePointer)
            assertEquals(0xBBBBL, frames[1].registerSaveArea)
            assertEquals("simple", frames[2].functionName)
            assertEquals(0L, frames[2].basePointer)
        }

        @Test
        fun popNativeFrame() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("native", 0x1000, 0xBA5E, 0x5AFE)
            ctx.popFrame()
            assertEquals(0, ctx.depth())
            assertNull(ctx.topFrame())
        }

        @Test
        fun zeroBasePointerAndSaveArea() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("func", 0x1000, 0, 0)
            val frame = ctx.topFrame()!!
            assertEquals(0L, frame.basePointer)
            assertEquals(0L, frame.registerSaveArea)
        }
    }

    @Nested
    inner class FrameVisitorInterface {

        @Test
        fun lambdaAsFrameVisitor() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("test", 0x100)

            val names = mutableListOf<String>()
            val visitor = FrameVisitor { frame -> names.add(frame.functionName) }
            ctx.walkStack(visitor)
            assertEquals(listOf("test"), names)
        }

        @Test
        fun anonymousObjectAsFrameVisitor() {
            val ctx = ThreadExecutionContext()
            ctx.pushFrame("test", 0x100)

            val names = mutableListOf<String>()
            val visitor = object : FrameVisitor {
                override fun visitFrame(frame: StackFrame) {
                    names.add(frame.functionName)
                }
            }
            ctx.walkStack(visitor)
            assertEquals(listOf("test"), names)
        }
    }
}

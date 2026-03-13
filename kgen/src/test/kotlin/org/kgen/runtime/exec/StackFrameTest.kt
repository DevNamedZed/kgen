package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StackFrameTest {

    @Nested
    inner class Construction {

        @Test
        fun basicConstruction() {
            val frame = StackFrame("main", 0x1000, 0)
            assertEquals("main", frame.functionName)
            assertEquals(0x1000L, frame.returnAddress)
            assertEquals(0, frame.depth)
        }

        @Test
        fun defaultBasePointerIsZero() {
            val frame = StackFrame("main", 0x1000, 0)
            assertEquals(0L, frame.basePointer)
        }

        @Test
        fun defaultRegisterSaveAreaIsZero() {
            val frame = StackFrame("main", 0x1000, 0)
            assertEquals(0L, frame.registerSaveArea)
        }

        @Test
        fun constructionWithBasePointer() {
            val frame = StackFrame("func", 0x2000, 3, basePointer = 0x7FFF0000)
            assertEquals(0x7FFF0000L, frame.basePointer)
            assertEquals(0L, frame.registerSaveArea)
        }

        @Test
        fun constructionWithAllFields() {
            val frame = StackFrame("func", 0x2000, 3, 0x7FFF0000, 0x7FFF1000)
            assertEquals("func", frame.functionName)
            assertEquals(0x2000L, frame.returnAddress)
            assertEquals(3, frame.depth)
            assertEquals(0x7FFF0000L, frame.basePointer)
            assertEquals(0x7FFF1000L, frame.registerSaveArea)
        }

        @Test
        fun zeroReturnAddress() {
            val frame = StackFrame("entry", 0, 0)
            assertEquals(0L, frame.returnAddress)
        }
    }

    @Nested
    inner class Equality {

        @Test
        fun equalFrames() {
            val f1 = StackFrame("main", 100, 0)
            val f2 = StackFrame("main", 100, 0)
            assertEquals(f1, f2)
        }

        @Test
        fun differentFunctionName() {
            val f1 = StackFrame("main", 100, 0)
            val f2 = StackFrame("other", 100, 0)
            assertNotEquals(f1, f2)
        }

        @Test
        fun differentReturnAddress() {
            val f1 = StackFrame("main", 100, 0)
            val f2 = StackFrame("main", 200, 0)
            assertNotEquals(f1, f2)
        }

        @Test
        fun differentDepth() {
            val f1 = StackFrame("main", 100, 0)
            val f2 = StackFrame("main", 100, 1)
            assertNotEquals(f1, f2)
        }

        @Test
        fun differentBasePointer() {
            val f1 = StackFrame("main", 100, 0, basePointer = 0x1000)
            val f2 = StackFrame("main", 100, 0, basePointer = 0x2000)
            assertNotEquals(f1, f2)
        }

        @Test
        fun differentRegisterSaveArea() {
            val f1 = StackFrame("main", 100, 0, registerSaveArea = 0x1000)
            val f2 = StackFrame("main", 100, 0, registerSaveArea = 0x2000)
            assertNotEquals(f1, f2)
        }

        @Test
        fun equalWithAllFields() {
            val f1 = StackFrame("func", 0x1000, 5, 0x7000, 0x8000)
            val f2 = StackFrame("func", 0x1000, 5, 0x7000, 0x8000)
            assertEquals(f1, f2)
            assertEquals(f1.hashCode(), f2.hashCode())
        }
    }

    @Nested
    inner class Copy {

        @Test
        fun copyChangingDepth() {
            val original = StackFrame("main", 100, 0, 0x5000, 0x6000)
            val copy = original.copy(depth = 5)
            assertEquals("main", copy.functionName)
            assertEquals(100L, copy.returnAddress)
            assertEquals(5, copy.depth)
            assertEquals(0x5000L, copy.basePointer)
            assertEquals(0x6000L, copy.registerSaveArea)
        }

        @Test
        fun copyChangingBasePointer() {
            val original = StackFrame("main", 100, 0)
            val copy = original.copy(basePointer = 0xABCD)
            assertEquals(0xABCDL, copy.basePointer)
            assertEquals(0, copy.depth)
        }

        @Test
        fun copyChangingRegisterSaveArea() {
            val original = StackFrame("main", 100, 0)
            val copy = original.copy(registerSaveArea = 0x9999)
            assertEquals(0x9999L, copy.registerSaveArea)
        }
    }

    @Nested
    inner class Destructuring {

        @Test
        fun destructure() {
            val frame = StackFrame("func", 0x2000, 3, 0x7000, 0x8000)
            val (name, ret, depth, bp, rsa) = frame
            assertEquals("func", name)
            assertEquals(0x2000L, ret)
            assertEquals(3, depth)
            assertEquals(0x7000L, bp)
            assertEquals(0x8000L, rsa)
        }
    }
}

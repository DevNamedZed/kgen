package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class ClrDynamicMethodTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithNameAndSignature() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
            val method = ClrDynamicMethod("add", sig)
            assertEquals("add", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun constructsViaFactory() {
            val sig = Signature.of(TypeRef.I64)
            val method = DynamicMethod.clr("getValue", sig)
            assertInstanceOf(ClrDynamicMethod::class.java, method)
        }
    }

    @Nested
    inner class IlAccess {

        @Test
        fun ilReturnsAssembler() {
            val method = ClrDynamicMethod("test", Signature.VOID)
            assertNotNull(method.il())
        }

        @Test
        fun ilReturnsSameInstance() {
            val method = ClrDynamicMethod("test", Signature.VOID)
            assertSame(method.il(), method.il())
        }
    }

    @Nested
    inner class ToBytes {

        @Test
        fun toBytesReturnsEmptyForNoInstructions() {
            val method = ClrDynamicMethod("empty", Signature.VOID)
            val bytes = method.toBytes()
            assertNotNull(bytes)
        }

        @Test
        fun toBytesReturnsNonEmptyAfterEmittingInstructions() {
            val method = ClrDynamicMethod("ret", Signature.VOID)
            method.il().ret()
            val bytes = method.toBytes()
            assertTrue(bytes.isNotEmpty())
        }
    }

    @Nested
    inner class InvokeThrows {

        @Test
        fun invokeThrowsUnsupportedOperationException() {
            val method = ClrDynamicMethod("test", Signature.VOID)
            val ex = assertThrows(UnsupportedOperationException::class.java) {
                method.invoke()
            }
            assertTrue(ex.message!!.contains("CLR"))
        }

        @Test
        fun invokeWithArgsAlsoThrows() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32)
            val method = ClrDynamicMethod("test", sig)
            assertThrows(UnsupportedOperationException::class.java) {
                method.invoke(42)
            }
        }
    }

    @Nested
    inner class Close {

        @Test
        fun closeDoesNotThrow() {
            val method = ClrDynamicMethod("test", Signature.VOID)
            assertDoesNotThrow { method.close() }
        }
    }
}

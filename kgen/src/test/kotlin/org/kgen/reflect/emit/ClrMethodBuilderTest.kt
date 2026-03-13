package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class ClrMethodBuilderTest {

    private fun createMethodBuilder(
        name: String = "TestMethod",
        signature: Signature = Signature.VOID,
        flags: Set<MethodFlag> = setOf(MethodFlag.PUBLIC, MethodFlag.STATIC),
        index: Int = 0,
    ): ClrMethodBuilder {
        return ClrMethodBuilder(name, signature, flags, index)
    }

    @Nested
    inner class Properties {

        @Test
        fun nameIsPreserved() {
            val mb = createMethodBuilder(name = "Calculate")
            assertEquals("Calculate", mb.name)
        }

        @Test
        fun signatureIsPreserved() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I32, TypeRef.I32)
            val mb = createMethodBuilder(signature = sig)
            assertEquals(sig, mb.signature)
        }

        @Test
        fun flagsArePreserved() {
            val flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL)
            val mb = createMethodBuilder(flags = flags)
            assertEquals(flags, mb.flags)
        }
    }

    @Nested
    inner class IlAccess {

        @Test
        fun ilReturnsAssembler() {
            val mb = createMethodBuilder()
            assertNotNull(mb.il())
        }

        @Test
        fun ilReturnsSameInstance() {
            val mb = createMethodBuilder()
            assertSame(mb.il(), mb.il())
        }
    }

    @Nested
    inner class Token {

        @Test
        fun tokenReturnsMethodDefToken() {
            val mb = createMethodBuilder(index = 0)
            val token = mb.token()
            assertTrue(token.isMethodDef)
            assertEquals(1, token.rowIndex)
        }

        @Test
        fun tokenReflectsIndex() {
            val mb = createMethodBuilder(index = 2)
            val token = mb.token()
            assertEquals(3, token.rowIndex)
        }
    }
}

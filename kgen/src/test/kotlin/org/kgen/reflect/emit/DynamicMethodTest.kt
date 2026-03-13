package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class DynamicMethodTest {

    @Nested
    inner class FactoryMethods {

        @Test
        fun nativeFactoryCreatesNativeDynamicMethod() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val method = DynamicMethod.native_("test", sig)
            assertInstanceOf(NativeDynamicMethod::class.java, method)
            assertEquals("test", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun nativeFactoryWithTargetCreatesNativeDynamicMethod() {
            val sig = Signature.of(TypeRef.I64)
            val target = Target.x86_64()
            val method = DynamicMethod.native_("compute", sig, target)
            assertInstanceOf(NativeDynamicMethod::class.java, method)
            assertEquals("compute", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun jvmFactoryCreatesJvmDynamicMethod() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
            val method = DynamicMethod.jvm("add", sig)
            assertInstanceOf(JvmDynamicMethod::class.java, method)
            assertEquals("add", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun clrFactoryCreatesClrDynamicMethod() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32)
            val method = DynamicMethod.clr("negate", sig)
            assertInstanceOf(ClrDynamicMethod::class.java, method)
            assertEquals("negate", method.name)
            assertEquals(sig, method.signature)
        }
    }

    @Nested
    inner class Properties {

        @Test
        fun nameIsPreserved() {
            val method = DynamicMethod.jvm("myFunc", Signature.VOID)
            assertEquals("myFunc", method.name)
        }

        @Test
        fun signatureIsPreserved() {
            val sig = Signature.of(TypeRef.F64, TypeRef.F32, TypeRef.F32)
            val method = DynamicMethod.jvm("convert", sig)
            assertEquals(sig, method.signature)
        }

        @Test
        fun voidSignature() {
            val method = DynamicMethod.jvm("noop", Signature.VOID)
            assertEquals(Signature.VOID, method.signature)
        }
    }

    @Nested
    inner class AutoCloseable {

        @Test
        fun closeDoesNotThrowByDefault() {
            val method = DynamicMethod.jvm("test", Signature.VOID)
            assertDoesNotThrow { method.close() }
        }

        @Test
        fun closeMultipleTimesDoesNotThrow() {
            val method = DynamicMethod.jvm("test", Signature.VOID)
            method.close()
            assertDoesNotThrow { method.close() }
        }
    }
}

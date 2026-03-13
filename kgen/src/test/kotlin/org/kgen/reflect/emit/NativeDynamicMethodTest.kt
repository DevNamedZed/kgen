package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class NativeDynamicMethodTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithNameSignatureAndTarget() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64)
            val target = Target.x86_64()
            val method = NativeDynamicMethod("add", sig, target)
            assertEquals("add", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun constructsViaFactory() {
            val sig = Signature.of(TypeRef.I64)
            val method = DynamicMethod.native_("getValue", sig)
            assertInstanceOf(NativeDynamicMethod::class.java, method)
        }

        @Test
        fun constructsViaFactoryWithTarget() {
            val sig = Signature.of(TypeRef.I64)
            val target = Target.arm64()
            val method = DynamicMethod.native_("getValue", sig, target)
            assertInstanceOf(NativeDynamicMethod::class.java, method)
        }
    }

    @Nested
    inner class Body {

        @Test
        fun bodyReturnsSelf() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            val method = NativeDynamicMethod("test", sig, Target.x86_64())
            val result = method.body { ir, params ->
                ir.ret(params[0])
            }
            assertSame(method, result)
        }
    }

    @Nested
    inner class Close {

        @Test
        fun closeBeforeInvokeDoesNotThrow() {
            val sig = Signature.of(TypeRef.I64)
            val method = NativeDynamicMethod("test", sig, Target.x86_64())
            assertDoesNotThrow { method.close() }
        }

        @Test
        fun closeMultipleTimesDoesNotThrow() {
            val sig = Signature.of(TypeRef.I64)
            val method = NativeDynamicMethod("test", sig, Target.x86_64())
            method.close()
            assertDoesNotThrow { method.close() }
        }
    }
}

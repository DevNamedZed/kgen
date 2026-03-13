package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class JvmDynamicMethodTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithNameAndSignature() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
            val method = JvmDynamicMethod("add", sig)
            assertEquals("add", method.name)
            assertEquals(sig, method.signature)
        }

        @Test
        fun constructsViaFactory() {
            val sig = Signature.of(TypeRef.I64)
            val method = DynamicMethod.jvm("getValue", sig)
            assertInstanceOf(JvmDynamicMethod::class.java, method)
        }
    }

    @Nested
    inner class JvmBody {

        @Test
        fun jvmBodyReturnsSelf() {
            val method = JvmDynamicMethod("test", Signature.of(TypeRef.I32))
            val result = method.jvmBody { code ->
                code.iconst(0)
                code.ireturn()
            }
            assertSame(method, result)
        }
    }

    @Nested
    inner class InvokeNoBody {

        @Test
        fun invokeWithoutBodyThrowsIllegalState() {
            val method = JvmDynamicMethod("test", Signature.of(TypeRef.I32))
            assertThrows(IllegalStateException::class.java) {
                method.invoke()
            }
        }
    }

    @Nested
    inner class JvmDescriptor {

        @Test
        fun voidNoParams() {
            val sig = Signature.VOID
            assertEquals("()V", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun intReturnNoParams() {
            val sig = Signature.of(TypeRef.I32)
            assertEquals("()I", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun intReturnTwoIntParams() {
            val sig = Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32)
            assertEquals("(II)I", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun longReturnLongParam() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I64)
            assertEquals("(J)J", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun floatTypes() {
            val sig = Signature.of(TypeRef.F64, TypeRef.F32)
            assertEquals("(F)D", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun boolParam() {
            val sig = Signature.of(TypeRef.BOOL, TypeRef.BOOL)
            assertEquals("(Z)Z", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun byteParam() {
            val sig = Signature.of(TypeRef.I8, TypeRef.I8)
            assertEquals("(B)B", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun shortParam() {
            val sig = Signature.of(TypeRef.I16, TypeRef.I16)
            assertEquals("(S)S", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun unsignedTypesMapToSameDescriptors() {
            val sig = Signature.of(TypeRef.U32, TypeRef.U8, TypeRef.U16, TypeRef.U64)
            assertEquals("(BSJ)I", JvmDynamicMethod.toJvmDescriptor(sig))
        }

        @Test
        fun manyParams() {
            val sig = Signature.of(TypeRef.VOID, TypeRef.I32, TypeRef.I64, TypeRef.F32, TypeRef.F64)
            assertEquals("(IJFD)V", JvmDynamicMethod.toJvmDescriptor(sig))
        }
    }

    @Nested
    inner class Execution {

        @Test
        fun invokeReturnsCorrectResult() {
            val method = JvmDynamicMethod("constant", Signature.of(TypeRef.I32))
            method.jvmBody { code ->
                code.iconst(99)
                code.ireturn()
            }
            assertEquals(99, method.invoke())
        }

        @Test
        fun invokeIsCached() {
            val method = JvmDynamicMethod("cached", Signature.of(TypeRef.I32))
            method.jvmBody { code ->
                code.iconst(7)
                code.ireturn()
            }
            val first = method.invoke()
            val second = method.invoke()
            assertEquals(first, second)
        }

        @Test
        fun multipleMethodsGetUniqueClasses() {
            val m1 = JvmDynamicMethod("a", Signature.of(TypeRef.I32))
            m1.jvmBody { code ->
                code.iconst(1)
                code.ireturn()
            }

            val m2 = JvmDynamicMethod("b", Signature.of(TypeRef.I32))
            m2.jvmBody { code ->
                code.iconst(2)
                code.ireturn()
            }

            assertEquals(1, m1.invoke())
            assertEquals(2, m2.invoke())
        }
    }
}

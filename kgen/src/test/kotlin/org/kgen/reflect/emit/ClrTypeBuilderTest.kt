package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.FieldFlag
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

class ClrTypeBuilderTest {

    private fun createTypeBuilder(): ClrTypeBuilder {
        val mod = ClrModuleBuilder("TestLib")
        return mod.defineType("TestNs.TestClass")
    }

    @Nested
    inner class DefineMethod {

        @Test
        fun defineMethodReturnsMethodBuilder() {
            val tb = createTypeBuilder()
            val mb = tb.defineMethod("Run", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            assertNotNull(mb)
        }

        @Test
        fun defineMethodPreservesName() {
            val tb = createTypeBuilder()
            val mb = tb.defineMethod("Calculate", Signature.of(TypeRef.I32, TypeRef.I32), MethodFlag.PUBLIC)
            assertEquals("Calculate", mb.name)
        }

        @Test
        fun defineMethodPreservesSignature() {
            val sig = Signature.of(TypeRef.I64, TypeRef.I32, TypeRef.I32)
            val tb = createTypeBuilder()
            val mb = tb.defineMethod("Add", sig, MethodFlag.PUBLIC, MethodFlag.STATIC)
            assertEquals(sig, mb.signature)
        }

        @Test
        fun defineMethodPreservesFlags() {
            val tb = createTypeBuilder()
            val mb = tb.defineMethod("Test", Signature.VOID, MethodFlag.PRIVATE, MethodFlag.STATIC)
            assertTrue(mb.flags.contains(MethodFlag.PRIVATE))
            assertTrue(mb.flags.contains(MethodFlag.STATIC))
        }

        @Test
        fun defineMultipleMethods() {
            val tb = createTypeBuilder()
            val m1 = tb.defineMethod("First", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            val m2 = tb.defineMethod("Second", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            assertNotSame(m1, m2)
            assertEquals("First", m1.name)
            assertEquals("Second", m2.name)
        }
    }

    @Nested
    inner class DefineField {

        @Test
        fun defineFieldReturnsCilToken() {
            val tb = createTypeBuilder()
            val token = tb.defineField("count", TypeRef.I32, FieldFlag.PRIVATE)
            assertNotNull(token)
            assertTrue(token.isField)
        }

        @Test
        fun defineFieldWithMultipleFlags() {
            val tb = createTypeBuilder()
            val token = tb.defineField("MAX", TypeRef.I32, FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST)
            assertNotNull(token)
        }

        @Test
        fun defineMultipleFields() {
            val tb = createTypeBuilder()
            val f1 = tb.defineField("x", TypeRef.I32, FieldFlag.PRIVATE)
            val f2 = tb.defineField("y", TypeRef.I32, FieldFlag.PRIVATE)
            assertNotEquals(f1.value, f2.value)
        }
    }

    @Nested
    inner class MethodIndex {

        @Test
        fun methodIndexReturnsCorrectIndex() {
            val tb = createTypeBuilder()
            val m0 = tb.defineMethod("First", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            val m1 = tb.defineMethod("Second", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            assertEquals(0, tb.methodIndex(m0))
            assertEquals(1, tb.methodIndex(m1))
        }
    }

    @Nested
    inner class TypeRefAndMemberRef {

        @Test
        fun addTypeRefReturnsIncrementingIndex() {
            val tb = createTypeBuilder()
            val idx1 = tb.addTypeRef(1, "Console", "System")
            val idx2 = tb.addTypeRef(1, "Math", "System")
            assertEquals(idx1 + 1, idx2)
        }

        @Test
        fun addMemberRefReturnsToken() {
            val tb = createTypeBuilder()
            val token = tb.addMemberRef(1, "WriteLine", Signature.ofVoid(TypeRef.I32), false)
            assertNotNull(token)
            assertTrue(token.isMemberRef)
        }

        @Test
        fun addMemberRefInstanceMethod() {
            val tb = createTypeBuilder()
            val token = tb.addMemberRef(1, "ToString", Signature.of(TypeRef.I32), true)
            assertNotNull(token)
        }
    }
}

package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeFlag
import org.kgen.reflect.TypeRef

class ClrModuleBuilderTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithName() {
            val mod = ClrModuleBuilder("MyLib")
            assertEquals("MyLib", mod.name)
        }

        @Test
        fun constructsViaFactory() {
            val mod = ModuleBuilder.clr("TestAssembly")
            assertInstanceOf(ClrModuleBuilder::class.java, mod)
            assertEquals("TestAssembly", mod.name)
        }
    }

    @Nested
    inner class DefineType {

        @Test
        fun defineTypeReturnsTypeBuilder() {
            val mod = ClrModuleBuilder("TestLib")
            val tb = mod.defineType("MyNamespace.MyClass")
            assertNotNull(tb)
        }

        @Test
        fun defineTypeWithFlags() {
            val mod = ClrModuleBuilder("TestLib")
            val tb = mod.defineType("MyNamespace.MyClass", TypeFlag.PUBLIC, TypeFlag.SEALED)
            assertNotNull(tb)
        }

        @Test
        fun defineTypeOnlyAllowedOnce() {
            val mod = ClrModuleBuilder("TestLib")
            mod.defineType("First.Type")
            assertThrows(IllegalArgumentException::class.java) {
                mod.defineType("Second.Type")
            }
        }
    }

    @Nested
    inner class SetEntryPoint {

        @Test
        fun setEntryPointReturnsSelf() {
            val mod = ClrModuleBuilder("TestLib")
            val tb = mod.defineType("App.Program")
            val method = tb.defineMethod("Main", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC)
            method.il().ret()
            val result = mod.setEntryPoint(method)
            assertSame(mod, result)
        }
    }

    @Nested
    inner class ToBytes {

        @Test
        fun toBytesRequiresTypeDefined() {
            val mod = ClrModuleBuilder("TestLib")
            assertThrows(IllegalStateException::class.java) {
                mod.toBytes()
            }
        }

        @Test
        fun toBytesReturnsNonEmpty() {
            val mod = ClrModuleBuilder("TestLib")
            val tb = mod.defineType("TestNs.TestClass")
            tb.defineMethod("Run", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC).il().ret()
            val bytes = mod.toBytes()
            assertTrue(bytes.isNotEmpty())
        }

        @Test
        fun toBytesProducesValidBinary() {
            val mod = ClrModuleBuilder("TestLib")
            val tb = mod.defineType("TestNs.TestClass")
            tb.defineMethod("Run", Signature.VOID, MethodFlag.PUBLIC, MethodFlag.STATIC).il().ret()
            val bytes = mod.toBytes()
            assertTrue(bytes.size > 100, "CLR assembly should be a non-trivial binary")
        }
    }

    @Nested
    inner class AddTypeRef {

        @Test
        fun addTypeRefRequiresTypeDefinedFirst() {
            val mod = ClrModuleBuilder("TestLib")
            assertThrows(IllegalStateException::class.java) {
                mod.addTypeRef(1, "Console", "System")
            }
        }

        @Test
        fun addTypeRefReturnsIndex() {
            val mod = ClrModuleBuilder("TestLib")
            mod.defineType("TestNs.TestClass")
            val idx = mod.addTypeRef(1, "Console", "System")
            assertTrue(idx > 0)
        }
    }

    @Nested
    inner class AddMemberRef {

        @Test
        fun addMemberRefRequiresTypeDefinedFirst() {
            val mod = ClrModuleBuilder("TestLib")
            assertThrows(IllegalStateException::class.java) {
                mod.addMemberRef(1, "WriteLine", Signature.ofVoid(TypeRef.I32))
            }
        }

        @Test
        fun addMemberRefReturnsCilToken() {
            val mod = ClrModuleBuilder("TestLib")
            mod.defineType("TestNs.TestClass")
            val token = mod.addMemberRef(1, "DoWork", Signature.VOID)
            assertNotNull(token)
            assertTrue(token.isMemberRef)
        }
    }
}

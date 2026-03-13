package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ModuleBuilderTest {

    @Nested
    inner class FactoryMethods {

        @Test
        fun clrFactoryCreatesClrModuleBuilder() {
            val mod = ModuleBuilder.clr("MyLib")
            assertInstanceOf(ClrModuleBuilder::class.java, mod)
            assertEquals("MyLib", mod.name)
        }

        @Test
        fun jvmFactoryCreatesJvmModuleBuilder() {
            val mod = ModuleBuilder.jvm("com/example/MyClass")
            assertInstanceOf(JvmModuleBuilder::class.java, mod)
            assertEquals("com/example/MyClass", mod.name)
        }

        @Test
        fun wasmFactoryCreatesWasmModuleBuilder() {
            val mod = ModuleBuilder.wasm("myModule")
            assertInstanceOf(WasmModuleBuilder::class.java, mod)
            assertEquals("myModule", mod.name)
        }

        @Test
        fun nativeFactoryCreatesNativeModuleBuilder() {
            val mod = ModuleBuilder.native_("myNative")
            assertInstanceOf(NativeModuleBuilder::class.java, mod)
            assertEquals("myNative", mod.name)
        }

        @Test
        fun nativeFactoryWithCodegenCreatesNativeModuleBuilder() {
            val codegen = NativeModuleBuilder.hostCodeGenerator()
            val mod = ModuleBuilder.native_("myNative", codegen)
            assertInstanceOf(NativeModuleBuilder::class.java, mod)
            assertEquals("myNative", mod.name)
        }
    }

    @Nested
    inner class NameProperty {

        @Test
        fun nameIsPreserved() {
            assertEquals("Test", ModuleBuilder.clr("Test").name)
            assertEquals("com/Foo", ModuleBuilder.jvm("com/Foo").name)
            assertEquals("wmod", ModuleBuilder.wasm("wmod").name)
        }
    }
}

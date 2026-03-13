package org.kgen.reflect.emit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.jvm.AccessFlags

class JvmModuleBuilderTest {

    @Nested
    inner class Construction {

        @Test
        fun constructsWithClassName() {
            val mod = JvmModuleBuilder("com/example/MyClass")
            assertEquals("com/example/MyClass", mod.name)
        }

        @Test
        fun constructsViaFactory() {
            val mod = ModuleBuilder.jvm("org/test/Foo")
            assertInstanceOf(JvmModuleBuilder::class.java, mod)
            assertEquals("org/test/Foo", mod.name)
        }
    }

    @Nested
    inner class ClassBuilder {

        @Test
        fun classBuilderIsNotNull() {
            val mod = JvmModuleBuilder("com/example/Test")
            assertNotNull(mod.classBuilder())
        }

        @Test
        fun classBuilderReturnsSameInstance() {
            val mod = JvmModuleBuilder("com/example/Test")
            assertSame(mod.classBuilder(), mod.classBuilder())
        }
    }

    @Nested
    inner class ToBytes {

        @Test
        fun toBytesReturnsClassFileBytes() {
            val mod = JvmModuleBuilder("com/example/Empty")
            val bytes = mod.toBytes()
            assertTrue(bytes.isNotEmpty())
            assertEquals(0xCA.toByte(), bytes[0])
            assertEquals(0xFE.toByte(), bytes[1])
            assertEquals(0xBA.toByte(), bytes[2])
            assertEquals(0xBE.toByte(), bytes[3])
        }

        @Test
        fun toBytesWithMethod() {
            val mod = JvmModuleBuilder("com/example/WithMethod")
            mod.classBuilder().method("answer", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
            val bytes = mod.toBytes()
            assertTrue(bytes.size > 4)
        }
    }

    @Nested
    inner class Load {

        @Test
        fun loadReturnsClass() {
            val mod = JvmModuleBuilder("org/kgen/test/LoadTest")
            mod.classBuilder().method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            val cls = mod.load()
            assertEquals("org.kgen.test.LoadTest", cls.name)
        }

        @Test
        fun loadIsCached() {
            val mod = JvmModuleBuilder("org/kgen/test/CacheTest")
            mod.classBuilder().method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            val first = mod.load()
            val second = mod.load()
            assertSame(first, second)
        }

        @Test
        fun loadWithCustomClassLoader() {
            val mod = JvmModuleBuilder("org/kgen/test/CustomLoader")
            mod.classBuilder().method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            val cls = mod.load(Thread.currentThread().contextClassLoader)
            assertNotNull(cls)
        }
    }

    @Nested
    inner class Invoke {

        @Test
        fun invokeStaticMethod() {
            val mod = JvmModuleBuilder("org/kgen/test/InvokeTest")
            mod.classBuilder().method("double", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(0)
                code.iadd()
                code.ireturn()
            }
            assertEquals(10, mod.invoke("double", 5))
        }

        @Test
        fun invokeMethodNotFoundThrows() {
            val mod = JvmModuleBuilder("org/kgen/test/NoMethod")
            mod.classBuilder().method("exists", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            assertThrows(NoSuchMethodException::class.java) {
                mod.invoke("doesNotExist")
            }
        }
    }
}

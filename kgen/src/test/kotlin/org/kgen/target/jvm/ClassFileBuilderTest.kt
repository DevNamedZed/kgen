package org.kgen.target.jvm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ClassFileBuilderTest {

    private class ByteArrayClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun defineClass(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private fun loadClass(bytes: ByteArray, name: String): Class<*> {
        val loader = ByteArrayClassLoader(this::class.java.classLoader)
        return loader.defineClass(name, bytes)
    }

    @Test
    fun buildEmptyClass() {
        val bytes = ClassFileBuilder("EmptyClass").toBytes()
        assertTrue(bytes.isNotEmpty())
        assertEquals(0xCA.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(0xBE.toByte(), bytes[3])
    }

    @Test
    fun buildAndLoadEmptyClass() {
        val bytes = ClassFileBuilder("EmptyTest").toBytes()
        val clazz = loadClass(bytes, "EmptyTest")
        assertEquals("EmptyTest", clazz.name)
    }

    @Test
    fun returnConstant() {
        val bytes = ClassFileBuilder("ConstReturn")
            .method("getFortyTwo", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "ConstReturn")
        val result = clazz.getMethod("getFortyTwo").invoke(null) as Int
        assertEquals(42, result)
    }

    @Test
    fun addTwoInts() {
        val bytes = ClassFileBuilder("AddTwo")
            .method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "AddTwo")
        val method = clazz.getMethod("add", Int::class.java, Int::class.java)
        assertEquals(7, method.invoke(null, 3, 4))
        assertEquals(-3, method.invoke(null, -1, -2))
    }

    @Test
    fun returnLong() {
        val bytes = ClassFileBuilder("LongReturn")
            .method("getBig", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(999999999999L)
                code.lreturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "LongReturn")
        assertEquals(999999999999L, clazz.getMethod("getBig").invoke(null))
    }

    @Test
    fun returnString() {
        val bytes = ClassFileBuilder("StringReturn")
            .method("greet", "()Ljava/lang/String;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.ldc("Hello, World!")
                code.areturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "StringReturn")
        assertEquals("Hello, World!", clazz.getMethod("greet").invoke(null))
    }

    @Test
    fun voidMethod() {
        val bytes = ClassFileBuilder("VoidMethod")
            .method("doNothing", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
            .toBytes()

        val clazz = loadClass(bytes, "VoidMethod")
        assertNull(clazz.getMethod("doNothing").invoke(null))
    }

    @Test
    fun multipleMethods() {
        val bytes = ClassFileBuilder("Multi")
            .method("one", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            .method("two", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(2)
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "Multi")
        assertEquals(1, clazz.getMethod("one").invoke(null))
        assertEquals(2, clazz.getMethod("two").invoke(null))
    }

    @Test
    fun conditionalBranch() {
        val bytes = ClassFileBuilder("Branch")
            .method("abs", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ifge("positive")
                code.iload(0)
                code.ineg()
                code.ireturn()
                code.label("positive")
                code.iload(0)
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "Branch")
        val method = clazz.getMethod("abs", Int::class.java)
        assertEquals(5, method.invoke(null, 5))
        assertEquals(3, method.invoke(null, -3))
        assertEquals(0, method.invoke(null, 0))
    }

    @Test
    fun staticMethodCall() {
        val bytes = ClassFileBuilder("Caller")
            .method("double_", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.iload(0)
                code.iadd()
                code.ireturn()
            }
            .method("quadruple", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.invokestatic("Caller", "double_", "(I)I")
                code.invokestatic("Caller", "double_", "(I)I")
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "Caller")
        assertEquals(40, clazz.getMethod("quadruple", Int::class.java).invoke(null, 10))
    }

    @Test
    fun getstatic() {
        // Call System.lineSeparator() which is a static method returning String
        val bytes = ClassFileBuilder("GetStatic")
            .method("lineSep", "()Ljava/lang/String;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("java/lang/System", "lineSeparator", "()Ljava/lang/String;")
                code.areturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "GetStatic")
        assertEquals(System.lineSeparator(), clazz.getMethod("lineSep").invoke(null))
    }

    @Test
    fun loopWithIinc() {
        // sum 1..n
        val bytes = ClassFileBuilder("Loop")
            .method("sum", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 3
                // int result = 0 (slot 1)
                code.iconst(0)
                code.istore(1)
                // int i = 1 (slot 2)
                code.iconst(1)
                code.istore(2)
                // loop:
                code.label("loop")
                code.iload(2)
                code.iload(0)
                code.ifIcmpgt("done")
                // result += i
                code.iload(1)
                code.iload(2)
                code.iadd()
                code.istore(1)
                // i++
                code.iinc(2, 1)
                code.goto("loop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "Loop")
        val method = clazz.getMethod("sum", Int::class.java)
        assertEquals(55, method.invoke(null, 10))
        assertEquals(0, method.invoke(null, 0))
        assertEquals(5050, method.invoke(null, 100))
    }

    @Test
    fun doubleArithmetic() {
        val bytes = ClassFileBuilder("DoubleOps")
            .method("avg", "(DD)D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 4
                code.dload(0)
                code.dload(2)
                code.dadd()
                code.dconst(2.0)
                code.ddiv()
                code.dreturn()
            }
            .toBytes()

        val clazz = loadClass(bytes, "DoubleOps")
        val method = clazz.getMethod("avg", Double::class.java, Double::class.java)
        assertEquals(5.0, method.invoke(null, 3.0, 7.0))
    }

    @Test
    fun classFileModel() {
        val cf = ClassFileBuilder("ModelTest")
            .superClass("java/lang/Object")
            .version(50, 0)
            .method("test", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
            .build()

        assertEquals("ModelTest", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
        assertEquals(50, cf.majorVersion)
        assertEquals(1, cf.methods.size)
    }

    @Test
    fun roundTripWithReader() {
        val bytes = ClassFileBuilder("RoundTrip")
            .field("x", "I", AccessFlags.PRIVATE)
            .method("getX", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
            .toBytes()

        val cf = JvmClassReader.read(bytes)
        assertEquals("RoundTrip", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
        assertEquals(1, cf.fields.size)
        assertEquals(1, cf.methods.size)
    }
}

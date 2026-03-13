package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeEmitterTest {

    private fun buildAndLoad(name: String, descriptor: String, flags: Int, body: (ClassFileBuilder.CodeEmitter) -> Unit): Class<*> {
        val bytes = ClassFileBuilder(name)
            .method("test", descriptor, flags, body)
            .toBytes()
        return ByteArrayTestClassLoader(this::class.java.classLoader).defineClass(name, bytes)
    }

    private class ByteArrayTestClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun defineClass(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    @Nested
    inner class Constants {

        @Test
        fun iconstMinusOne() {
            val clazz = buildAndLoad("IConstM1", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(-1)
                code.ireturn()
            }
            assertEquals(-1, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun iconstZeroThroughFive() {
            for (i in 0..5) {
                val clazz = buildAndLoad("IConst$i", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(i)
                    code.ireturn()
                }
                assertEquals(i, clazz.getMethod("test").invoke(null))
            }
        }

        @Test
        fun iconstBipushRange() {
            val clazz = buildAndLoad("IBipush", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(100)
                code.ireturn()
            }
            assertEquals(100, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun iconstNegativeBipush() {
            val clazz = buildAndLoad("INegBipush", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(-50)
                code.ireturn()
            }
            assertEquals(-50, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun iconstSipushRange() {
            val clazz = buildAndLoad("ISipush", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(10000)
                code.ireturn()
            }
            assertEquals(10000, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun aconstNull() {
            val clazz = buildAndLoad("ANull", "()Ljava/lang/Object;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.areturn()
            }
            assertEquals(null, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun lconstZero() {
            val clazz = buildAndLoad("LConst0", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(0L)
                code.lreturn()
            }
            assertEquals(0L, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun lconstOne() {
            val clazz = buildAndLoad("LConst1", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(1L)
                code.lreturn()
            }
            assertEquals(1L, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun fconstZero() {
            val clazz = buildAndLoad("FConst0", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(0.0f)
                code.freturn()
            }
            assertEquals(0.0f, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun fconstOne() {
            val clazz = buildAndLoad("FConst1", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(1.0f)
                code.freturn()
            }
            assertEquals(1.0f, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun fconstTwo() {
            val clazz = buildAndLoad("FConst2", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(2.0f)
                code.freturn()
            }
            assertEquals(2.0f, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun fconstArbitrary() {
            val clazz = buildAndLoad("FConstPi", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(3.14f)
                code.freturn()
            }
            assertEquals(3.14f, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun dconstZero() {
            val clazz = buildAndLoad("DConst0", "()D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.dconst(0.0)
                code.dreturn()
            }
            assertEquals(0.0, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun dconstOne() {
            val clazz = buildAndLoad("DConst1", "()D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.dconst(1.0)
                code.dreturn()
            }
            assertEquals(1.0, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun dconstArbitrary() {
            val clazz = buildAndLoad("DConstE", "()D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.dconst(2.718)
                code.dreturn()
            }
            assertEquals(2.718, clazz.getMethod("test").invoke(null))
        }
    }

    @Nested
    inner class Arithmetic {

        @Test
        fun intSubtract() {
            val clazz = buildAndLoad("ISub", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.isub()
                code.ireturn()
            }
            assertEquals(3, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 7, 4))
        }

        @Test
        fun intMultiply() {
            val clazz = buildAndLoad("IMul", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.imul()
                code.ireturn()
            }
            assertEquals(12, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 3, 4))
        }

        @Test
        fun intDivide() {
            val clazz = buildAndLoad("IDiv", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.idiv()
                code.ireturn()
            }
            assertEquals(5, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 10, 2))
        }

        @Test
        fun intRemainder() {
            val clazz = buildAndLoad("IRem", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.irem()
                code.ireturn()
            }
            assertEquals(1, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 7, 3))
        }

        @Test
        fun intNegate() {
            val clazz = buildAndLoad("INeg", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ineg()
                code.ireturn()
            }
            assertEquals(-5, clazz.getMethod("test", Int::class.java).invoke(null, 5))
        }

        @Test
        fun longAdd() {
            val clazz = buildAndLoad("LAdd", "(JJ)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 4
                code.lload(0)
                code.lload(2)
                code.ladd()
                code.lreturn()
            }
            assertEquals(30L, clazz.getMethod("test", Long::class.java, Long::class.java).invoke(null, 10L, 20L))
        }

        @Test
        fun floatAdd() {
            val clazz = buildAndLoad("FAdd", "(FF)F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.fload(0)
                code.fload(1)
                code.fadd()
                code.freturn()
            }
            assertEquals(3.0f, clazz.getMethod("test", Float::class.java, Float::class.java).invoke(null, 1.0f, 2.0f))
        }
    }

    @Nested
    inner class Bitwise {

        @Test
        fun intAnd() {
            val clazz = buildAndLoad("IAnd", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.iand()
                code.ireturn()
            }
            assertEquals(0x0A, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 0x0F, 0x0A))
        }

        @Test
        fun intOr() {
            val clazz = buildAndLoad("IOr", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ior()
                code.ireturn()
            }
            assertEquals(0x0F, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 0x0A, 0x05))
        }

        @Test
        fun intXor() {
            val clazz = buildAndLoad("IXor", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ixor()
                code.ireturn()
            }
            assertEquals(0x05, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 0x0F, 0x0A))
        }

        @Test
        fun intShiftLeft() {
            val clazz = buildAndLoad("IShl", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ishl()
                code.ireturn()
            }
            assertEquals(8, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 1, 3))
        }

        @Test
        fun intShiftRight() {
            val clazz = buildAndLoad("IShr", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ishr()
                code.ireturn()
            }
            assertEquals(2, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 8, 2))
        }

        @Test
        fun intUnsignedShiftRight() {
            val clazz = buildAndLoad("IUshr", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.iushr()
                code.ireturn()
            }
            val result = clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, -1, 28) as Int
            assertEquals(15, result)
        }
    }

    @Nested
    inner class Conversions {

        @Test
        fun intToLong() {
            val clazz = buildAndLoad("I2L", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2l()
                code.lreturn()
            }
            assertEquals(42L, clazz.getMethod("test", Int::class.java).invoke(null, 42))
        }

        @Test
        fun intToFloat() {
            val clazz = buildAndLoad("I2F", "(I)F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2f()
                code.freturn()
            }
            assertEquals(10.0f, clazz.getMethod("test", Int::class.java).invoke(null, 10))
        }

        @Test
        fun intToDouble() {
            val clazz = buildAndLoad("I2D", "(I)D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2d()
                code.dreturn()
            }
            assertEquals(10.0, clazz.getMethod("test", Int::class.java).invoke(null, 10))
        }

        @Test
        fun longToInt() {
            val clazz = buildAndLoad("L2I", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.lload(0)
                code.l2i()
                code.ireturn()
            }
            assertEquals(42, clazz.getMethod("test", Long::class.java).invoke(null, 42L))
        }

        @Test
        fun intToByte() {
            val clazz = buildAndLoad("I2B", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2b()
                code.ireturn()
            }
            assertEquals(-1, clazz.getMethod("test", Int::class.java).invoke(null, 255))
        }

        @Test
        fun intToChar() {
            val clazz = buildAndLoad("I2C", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2c()
                code.ireturn()
            }
            assertEquals(65, clazz.getMethod("test", Int::class.java).invoke(null, 65))
        }

        @Test
        fun intToShort() {
            val clazz = buildAndLoad("I2S", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2s()
                code.ireturn()
            }
            assertEquals(-1, clazz.getMethod("test", Int::class.java).invoke(null, 65535))
        }
    }

    @Nested
    inner class StackOps {

        @Test
        fun dupAndPop() {
            val clazz = buildAndLoad("DupPop", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.dup()
                code.pop()
                code.ireturn()
            }
            assertEquals(42, clazz.getMethod("test").invoke(null))
        }

        @Test
        fun swap() {
            val clazz = buildAndLoad("Swap", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.swap()
                code.isub()
                code.ireturn()
            }
            // swap makes it (b - a) instead of (a - b)
            assertEquals(3, clazz.getMethod("test", Int::class.java, Int::class.java).invoke(null, 2, 5))
        }
    }

    @Nested
    inner class Branches {

        @Test
        fun ifnull() {
            val clazz = buildAndLoad("IfNull", "(Ljava/lang/Object;)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.ifnull("isNull")
                code.iconst(1)
                code.ireturn()
                code.label("isNull")
                code.iconst(0)
                code.ireturn()
            }
            assertEquals(0, clazz.getMethod("test", Any::class.java).invoke(null, null as Any?))
            assertEquals(1, clazz.getMethod("test", Any::class.java).invoke(null, "hello"))
        }

        @Test
        fun ifnonnull() {
            val clazz = buildAndLoad("IfNonNull", "(Ljava/lang/Object;)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.ifnonnull("notNull")
                code.iconst(0)
                code.ireturn()
                code.label("notNull")
                code.iconst(1)
                code.ireturn()
            }
            assertEquals(1, clazz.getMethod("test", Any::class.java).invoke(null, "hello"))
            assertEquals(0, clazz.getMethod("test", Any::class.java).invoke(null, null as Any?))
        }

        @Test
        fun allIntComparisons() {
            // Just verify they compile and produce bytecode (no runtime execution needed)
            val bytes = ClassFileBuilder("AllCmp")
                .method("test", "(II)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.maxLocals = 2
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmpeq("end")
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmpne("end")
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmplt("end")
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmpge("end")
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmpgt("end")
                    code.iload(0)
                    code.iload(1)
                    code.ifIcmple("end")
                    code.label("end")
                    code.return_()
                }
                .toBytes()
            assertTrue(bytes.isNotEmpty())
        }
    }

    @Nested
    inner class MaxStackTracking {

        @Test
        fun maxStackTracked() {
            val cp = ConstantPoolBuilder()
            val emitter = ClassFileBuilder.CodeEmitter(cp)
            assertEquals(0, emitter.maxStack)
            emitter.iconst(1)
            assertEquals(1, emitter.maxStack)
            emitter.iconst(2)
            assertEquals(2, emitter.maxStack)
            emitter.iadd()
            assertEquals(2, emitter.maxStack) // max was 2
        }

        @Test
        fun maxLocalsTracked() {
            val cp = ConstantPoolBuilder()
            val emitter = ClassFileBuilder.CodeEmitter(cp)
            assertEquals(0, emitter.maxLocals)
            emitter.iload(3)
            assertEquals(4, emitter.maxLocals) // slot 3 + size 1
            emitter.dload(5)
            assertEquals(7, emitter.maxLocals) // slot 5 + size 2
        }
    }

    @Nested
    inner class ObjectCreation {

        @Test
        fun newObject() {
            val clazz = buildAndLoad("NewObj", "()Ljava/lang/Object;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.new_("java/lang/Object")
                code.dup()
                code.invokespecial("java/lang/Object", "<init>", "()V")
                code.areturn()
            }
            val result = clazz.getMethod("test").invoke(null)
            assertTrue(result is Any)
        }

        @Test
        fun checkcast() {
            val clazz = buildAndLoad("CheckCast", "(Ljava/lang/Object;)Ljava/lang/String;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.checkcast("java/lang/String")
                code.areturn()
            }
            assertEquals("hello", clazz.getMethod("test", Any::class.java).invoke(null, "hello"))
        }

        @Test
        fun instanceofCheck() {
            val clazz = buildAndLoad("InstanceOf", "(Ljava/lang/Object;)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.instanceof_("java/lang/String")
                code.ireturn()
            }
            assertEquals(1, clazz.getMethod("test", Any::class.java).invoke(null, "hello"))
            assertEquals(0, clazz.getMethod("test", Any::class.java).invoke(null, 42))
        }
    }

    @Nested
    inner class ClassFileBuilderApi {

        @Test
        fun fluentApi() {
            val builder = ClassFileBuilder("FluentTest")
                .superClass("java/lang/Object")
                .version(50, 0)
                .flags(AccessFlags.PUBLIC or AccessFlags.SUPER)
                .implement("java/io/Serializable")
                .field("x", "I", AccessFlags.PRIVATE)
                .method("getX", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(0)
                    code.ireturn()
                }

            val cf = builder.build()
            assertEquals("FluentTest", cf.thisClassName)
            assertEquals("java/lang/Object", cf.superClassName)
            assertEquals(50, cf.majorVersion)
            assertEquals(0, cf.minorVersion)
            assertEquals(1, cf.fields.size)
            assertEquals(1, cf.methods.size)
            assertEquals(1, cf.interfaces.size)
        }

        @Test
        fun abstractMethodNoBody() {
            val cf = ClassFileBuilder("AbstractTest")
                .flags(AccessFlags.PUBLIC or AccessFlags.ABSTRACT or AccessFlags.SUPER)
                .method("doSomething", "()V", AccessFlags.PUBLIC or AccessFlags.ABSTRACT)
                .build()

            assertEquals(1, cf.methods.size)
            assertTrue(cf.methods[0].attributes.isEmpty())
        }

        @Test
        fun defaultVersion() {
            val cf = ClassFileBuilder("DefaultVer").build()
            assertEquals(50, cf.majorVersion) // Java 6 default
            assertEquals(0, cf.minorVersion)
        }
    }
}

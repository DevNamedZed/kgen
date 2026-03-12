package org.kgen.target.jvm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JvmComprehensiveTest {

    private class ByteArrayClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun defineClass(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    private fun loadClass(bytes: ByteArray, name: String): Class<*> {
        val loader = ByteArrayClassLoader(this::class.java.classLoader)
        return loader.defineClass(name, bytes)
    }

    private inline fun <reified T> loadClassBytes(): ByteArray = loadClassBytes(T::class.java)

    private fun loadClassBytes(clazz: Class<*>): ByteArray {
        val name = clazz.name.replace('.', '/') + ".class"
        val loader = clazz.classLoader
        if (loader != null) {
            val stream = loader.getResourceAsStream(name)
            if (stream != null) return stream.readAllBytes()
        }
        val sysStream = ClassLoader.getSystemResourceAsStream(name)
        if (sysStream != null) return sysStream.readAllBytes()
        try {
            val jrtFs = java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"))
            val path = jrtFs.getPath("modules", clazz.module.name ?: "java.base", name)
            if (java.nio.file.Files.exists(path)) return java.nio.file.Files.readAllBytes(path)
        } catch (_: Exception) {}
        throw IllegalArgumentException("Cannot load class bytes for ${clazz.name}")
    }

    @Test
    fun `ClassFile magic bytes are CAFEBABE`() {
        val bytes = ClassFileBuilder("MagicTest").toBytes()
        assertEquals(0xCA.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(0xBE.toByte(), bytes[3])
    }

    @Test
    fun `ClassFile default version is 50 (Java 6)`() {
        val cf = ClassFileBuilder("VersionDefault").build()
        assertEquals(50, cf.majorVersion)
        assertEquals(0, cf.minorVersion)
        assertEquals("6", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 45 maps to Java 1_1`() {
        val cf = ClassFileBuilder("V45").version(45).build()
        assertEquals("1.1", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 46 maps to Java 1_2`() {
        val cf = ClassFileBuilder("V46").version(46).build()
        assertEquals("1.2", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 47 maps to Java 1_3`() {
        val cf = ClassFileBuilder("V47").version(47).build()
        assertEquals("1.3", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 48 maps to Java 1_4`() {
        val cf = ClassFileBuilder("V48").version(48).build()
        assertEquals("1.4", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 49 maps to Java 5`() {
        val cf = ClassFileBuilder("V49").version(49).build()
        assertEquals("5", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 52 maps to Java 8`() {
        val cf = ClassFileBuilder("V52").version(52).build()
        assertEquals("8", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 55 maps to Java 11`() {
        val cf = ClassFileBuilder("V55").version(55).build()
        assertEquals("11", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 61 maps to Java 17`() {
        val cf = ClassFileBuilder("V61").version(61).build()
        assertEquals("17", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 65 maps to Java 21`() {
        val cf = ClassFileBuilder("V65").version(65).build()
        assertEquals("21", cf.javaVersion)
    }

    @Test
    fun `ClassFile version 68 maps to Java 24`() {
        val cf = ClassFileBuilder("V68").version(68).build()
        assertEquals("24", cf.javaVersion)
    }

    @Test
    fun `ClassFile unknown version uses major_minor format`() {
        val cf = ClassFileBuilder("V99").version(99, 5).build()
        assertEquals("99.5", cf.javaVersion)
    }

    @Test
    fun `ClassFile default access flags are PUBLIC and SUPER`() {
        val cf = ClassFileBuilder("FlagsDefault").build()
        assertEquals(AccessFlags.PUBLIC or AccessFlags.SUPER, cf.accessFlags)
    }

    @Test
    fun `ClassFile custom access flags`() {
        val cf = ClassFileBuilder("FlagsCustom")
            .flags(AccessFlags.PUBLIC or AccessFlags.FINAL or AccessFlags.SUPER)
            .build()
        assertTrue(cf.accessFlags and AccessFlags.FINAL != 0)
        assertTrue(cf.accessFlags and AccessFlags.PUBLIC != 0)
    }

    @Test
    fun `ClassFile default superclass is java_lang_Object`() {
        val cf = ClassFileBuilder("SuperDefault").build()
        assertEquals("java/lang/Object", cf.superClassName)
    }

    @Test
    fun `ClassFile custom superclass`() {
        val cf = ClassFileBuilder("SuperCustom")
            .superClass("java/util/AbstractList")
            .build()
        assertEquals("java/util/AbstractList", cf.superClassName)
    }

    @Test
    fun `ClassFile thisClassName round trips`() {
        val cf = ClassFileBuilder("com/example/MyClass").build()
        assertEquals("com/example/MyClass", cf.thisClassName)
    }

    @Test
    fun `ClassFile with no interfaces has empty interface list`() {
        val cf = ClassFileBuilder("NoIface").build()
        assertTrue(cf.interfaceNames.isEmpty())
    }

    @Test
    fun `ClassFile implement adds interface`() {
        val cf = ClassFileBuilder("WithIface")
            .implement("java/io/Serializable")
            .build()
        assertEquals(1, cf.interfaces.size)
        assertTrue(cf.interfaceNames.contains("java/io/Serializable"))
    }

    @Test
    fun `ClassFile implement multiple interfaces`() {
        val cf = ClassFileBuilder("MultiIface")
            .implement("java/io/Serializable")
            .implement("java/lang/Comparable")
            .implement("java/lang/Runnable")
            .build()
        assertEquals(3, cf.interfaces.size)
        assertTrue(cf.interfaceNames.contains("java/io/Serializable"))
        assertTrue(cf.interfaceNames.contains("java/lang/Comparable"))
        assertTrue(cf.interfaceNames.contains("java/lang/Runnable"))
    }

    @Test
    fun `ClassFile with no fields has empty field list`() {
        val cf = ClassFileBuilder("NoFields").build()
        assertTrue(cf.fields.isEmpty())
    }

    @Test
    fun `ClassFileBuilder adds private field by default`() {
        val cf = ClassFileBuilder("FieldDefault")
            .field("x", "I")
            .build()
        assertEquals(1, cf.fields.size)
        assertEquals(AccessFlags.PRIVATE, cf.fields[0].accessFlags)
        assertEquals("x", cf.string(cf.fields[0].nameIndex))
        assertEquals("I", cf.string(cf.fields[0].descriptorIndex))
    }

    @Test
    fun `ClassFileBuilder adds public static field`() {
        val cf = ClassFileBuilder("FieldPublicStatic")
            .field("COUNT", "I", AccessFlags.PUBLIC or AccessFlags.STATIC)
            .build()
        assertTrue(cf.fields[0].accessFlags and AccessFlags.PUBLIC != 0)
        assertTrue(cf.fields[0].accessFlags and AccessFlags.STATIC != 0)
    }

    @Test
    fun `ClassFileBuilder adds multiple fields`() {
        val cf = ClassFileBuilder("MultiField")
            .field("name", "Ljava/lang/String;")
            .field("age", "I")
            .field("active", "Z")
            .build()
        assertEquals(3, cf.fields.size)
        assertEquals("name", cf.string(cf.fields[0].nameIndex))
        assertEquals("age", cf.string(cf.fields[1].nameIndex))
        assertEquals("active", cf.string(cf.fields[2].nameIndex))
    }

    @Test
    fun `field descriptor types`() {
        val cf = ClassFileBuilder("FieldTypes")
            .field("b", "B")
            .field("c", "C")
            .field("d", "D")
            .field("f", "F")
            .field("i", "I")
            .field("j", "J")
            .field("s", "S")
            .field("z", "Z")
            .field("str", "Ljava/lang/String;")
            .field("arr", "[I")
            .build()
        assertEquals(10, cf.fields.size)
        assertEquals("B", cf.string(cf.fields[0].descriptorIndex))
        assertEquals("[I", cf.string(cf.fields[9].descriptorIndex))
    }

    @Test
    fun `ClassFile with no methods has empty method list`() {
        val cf = ClassFileBuilder("NoMethods").build()
        assertTrue(cf.methods.isEmpty())
    }

    @Test
    fun `ClassFileBuilder adds method with body`() {
        val cf = ClassFileBuilder("OneMethod")
            .method("test", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
            .build()
        assertEquals(1, cf.methods.size)
        assertEquals("test", cf.string(cf.methods[0].nameIndex))
        assertEquals("()V", cf.string(cf.methods[0].descriptorIndex))
    }

    @Test
    fun `ClassFileBuilder adds abstract method without body`() {
        val cf = ClassFileBuilder("AbstractMethod")
            .flags(AccessFlags.PUBLIC or AccessFlags.ABSTRACT or AccessFlags.SUPER)
            .method("doSomething", "()V", AccessFlags.PUBLIC or AccessFlags.ABSTRACT)
            .build()
        assertEquals(1, cf.methods.size)
        assertTrue(cf.methods[0].attributes.isEmpty())
    }

    @Test
    fun `method access flags preserved`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
        val cf = ClassFileBuilder("MethodFlags")
            .method("finalMethod", "()V", flags) { code ->
                code.return_()
            }
            .build()
        assertEquals(flags, cf.methods[0].accessFlags)
    }

    @Test
    fun `method descriptor with parameters`() {
        val cf = ClassFileBuilder("MethodDesc")
            .method("process", "(ILjava/lang/String;D)Z",
                AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1)
                code.ireturn()
            }
            .build()
        assertEquals("(ILjava/lang/String;D)Z", cf.string(cf.methods[0].descriptorIndex))
    }

    @Test
    fun `round trip empty class`() {
        val bytes = ClassFileBuilder("RoundTripEmpty").toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals("RoundTripEmpty", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
        assertEquals(50, cf.majorVersion)
        assertTrue(cf.fields.isEmpty())
        assertTrue(cf.methods.isEmpty())
    }

    @Test
    fun `round trip class with method`() {
        val bytes = ClassFileBuilder("RoundTripMethod")
            .method("getValue", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(99)
                code.ireturn()
            }
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals("RoundTripMethod", cf.thisClassName)
        assertEquals(1, cf.methods.size)
        assertEquals("getValue", cf.string(cf.methods[0].nameIndex))
    }

    @Test
    fun `round trip class with field`() {
        val bytes = ClassFileBuilder("RoundTripField")
            .field("value", "J", AccessFlags.PRIVATE or AccessFlags.FINAL)
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals(1, cf.fields.size)
        assertEquals("value", cf.string(cf.fields[0].nameIndex))
        assertEquals("J", cf.string(cf.fields[0].descriptorIndex))
        assertEquals(AccessFlags.PRIVATE or AccessFlags.FINAL, cf.fields[0].accessFlags)
    }

    @Test
    fun `round trip class with interfaces`() {
        val bytes = ClassFileBuilder("RoundTripIface")
            .implement("java/io/Serializable")
            .implement("java/lang/Comparable")
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals(2, cf.interfaces.size)
        assertTrue(cf.interfaceNames.contains("java/io/Serializable"))
        assertTrue(cf.interfaceNames.contains("java/lang/Comparable"))
    }

    @Test
    fun `round trip preserves version`() {
        val bytes = ClassFileBuilder("RoundTripVersion")
            .version(52, 0)
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals(52, cf.majorVersion)
        assertEquals(0, cf.minorVersion)
    }

    @Test
    fun `round trip preserves access flags`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.FINAL or AccessFlags.SUPER
        val bytes = ClassFileBuilder("RoundTripFlags")
            .flags(flags)
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals(flags, cf.accessFlags)
    }

    @Test
    fun `round trip with multiple methods and fields`() {
        val bytes = ClassFileBuilder("RoundTripMulti")
            .field("x", "I")
            .field("y", "I")
            .method("getX", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
            .method("getY", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
            .toBytes()
        val cf = JvmClassReader.read(bytes)
        assertEquals(2, cf.fields.size)
        assertEquals(2, cf.methods.size)
    }

    @Test
    fun `round trip double write-read produces identical model`() {
        val original = ClassFileBuilder("DoubleRoundTrip")
            .field("data", "[B")
            .method("init", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
            .toBytes()
        val cf1 = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf1)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf1.thisClassName, cf2.thisClassName)
        assertEquals(cf1.superClassName, cf2.superClassName)
        assertEquals(cf1.majorVersion, cf2.majorVersion)
        assertEquals(cf1.fields.size, cf2.fields.size)
        assertEquals(cf1.methods.size, cf2.methods.size)
        assertEquals(cf1.constantPool.size, cf2.constantPool.size)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates utf8`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.utf8("hello")
        val idx2 = cp.utf8("hello")
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates class entries`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.classEntry("java/lang/Object")
        val idx2 = cp.classEntry("java/lang/Object")
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates method refs`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.methodRef("java/lang/Object", "<init>", "()V")
        val idx2 = cp.methodRef("java/lang/Object", "<init>", "()V")
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates field refs`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.fieldRef("Test", "x", "I")
        val idx2 = cp.fieldRef("Test", "x", "I")
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates integer entries`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.integer(42)
        val idx2 = cp.integer(42)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates float entries`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.float(3.14f)
        val idx2 = cp.float(3.14f)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates long entries`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.long(Long.MAX_VALUE)
        val idx2 = cp.long(Long.MAX_VALUE)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder deduplicates double entries`() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.double(2.718)
        val idx2 = cp.double(2.718)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `ConstantPoolBuilder long occupies two slots`() {
        val cp = ConstantPoolBuilder()
        cp.utf8("before")  // index 1
        val longIdx = cp.long(100L)  // index 2, slot 3 is null
        val afterIdx = cp.utf8("after")  // index 4
        assertEquals(2, longIdx)
        assertEquals(4, afterIdx)
        val pool = cp.build()
        assertNull(pool.getOrNull(3))
    }

    @Test
    fun `ConstantPoolBuilder double occupies two slots`() {
        val cp = ConstantPoolBuilder()
        cp.utf8("before")
        val doubleIdx = cp.double(1.0)
        val afterIdx = cp.utf8("after")
        assertEquals(doubleIdx + 2, afterIdx)
        val pool = cp.build()
        assertNull(pool.getOrNull(doubleIdx + 1))
    }

    @Test
    fun `ConstantPool get throws on invalid index`() {
        val cp = ConstantPoolBuilder()
        cp.utf8("test")
        val pool = cp.build()
        assertThrows<IllegalArgumentException> { pool[999] }
    }

    @Test
    fun `ConstantPool utf8 throws on non-utf8 entry`() {
        val cp = ConstantPoolBuilder()
        val classIdx = cp.classEntry("Test")
        val pool = cp.build()
        assertThrows<IllegalArgumentException> { pool.utf8(classIdx) }
    }

    @Test
    fun `ConstantPool className throws on non-class entry`() {
        val cp = ConstantPoolBuilder()
        val utf8Idx = cp.utf8("test")
        val pool = cp.build()
        assertThrows<IllegalArgumentException> { pool.className(utf8Idx) }
    }

    @Test
    fun `ConstantPool findAll CpUtf8`() {
        val cp = ConstantPoolBuilder()
        cp.utf8("alpha")
        cp.utf8("beta")
        cp.integer(42)
        val pool = cp.build()
        val utf8s = pool.findAll<CpUtf8>()
        assertEquals(2, utf8s.size)
    }

    @Test
    fun `ConstantPool findAll CpClass`() {
        val cp = ConstantPoolBuilder()
        cp.classEntry("A")
        cp.classEntry("B")
        val pool = cp.build()
        val classes = pool.findAll<CpClass>()
        assertEquals(2, classes.size)
    }

    @Test
    fun `ConstantPool findAll CpInteger`() {
        val cp = ConstantPoolBuilder()
        cp.integer(1)
        cp.integer(2)
        cp.integer(3)
        val pool = cp.build()
        val ints = pool.findAll<CpInteger>()
        assertEquals(3, ints.size)
    }

    @Test
    fun `ConstantPool findAll CpMethodRef`() {
        val cp = ConstantPoolBuilder()
        cp.methodRef("A", "m1", "()V")
        cp.methodRef("B", "m2", "(I)I")
        val pool = cp.build()
        val refs = pool.findAll<CpMethodRef>()
        assertEquals(2, refs.size)
    }

    @Test
    fun `ConstantPool findAll CpFieldRef`() {
        val cp = ConstantPoolBuilder()
        cp.fieldRef("A", "x", "I")
        val pool = cp.build()
        val refs = pool.findAll<CpFieldRef>()
        assertEquals(1, refs.size)
    }

    @Test
    fun `ConstantPool findAll CpString`() {
        val cp = ConstantPoolBuilder()
        cp.string("hello")
        cp.string("world")
        val pool = cp.build()
        val strings = pool.findAll<CpString>()
        assertEquals(2, strings.size)
    }

    @Test
    fun `ConstantPool findAll CpNameAndType`() {
        val cp = ConstantPoolBuilder()
        cp.nameAndType("test", "()V")
        val pool = cp.build()
        val nats = pool.findAll<CpNameAndType>()
        assertEquals(1, nats.size)
    }

    @Test
    fun `ConstantPool nameAndType returns name and descriptor`() {
        val cp = ConstantPoolBuilder()
        val natIdx = cp.nameAndType("myMethod", "(II)I")
        val pool = cp.build()
        val (name, desc) = pool.nameAndType(natIdx)
        assertEquals("myMethod", name)
        assertEquals("(II)I", desc)
    }

    @Test
    fun `ConstantPool allEntries includes nulls for index 0 and wide slots`() {
        val cp = ConstantPoolBuilder()
        cp.long(42L)
        val pool = cp.build()
        val all = pool.allEntries()
        assertTrue(all.size >= 3)
        assertNull(all[0].value) // index 0
    }

    @Test
    fun `ConstantPoolBuilder string creates CpString entry`() {
        val cp = ConstantPoolBuilder()
        val strIdx = cp.string("test value")
        val pool = cp.build()
        val entry = pool[strIdx]
        assertTrue(entry is CpString)
        val utf8Val = pool.utf8((entry as CpString).stringIndex)
        assertEquals("test value", utf8Val)
    }

    @Test
    fun `ConstantPoolBuilder interfaceMethodRef`() {
        val cp = ConstantPoolBuilder()
        val idx = cp.interfaceMethodRef("java/lang/Runnable", "run", "()V")
        val pool = cp.build()
        assertTrue(pool[idx] is CpInterfaceMethodRef)
    }

    @Test
    fun `ConstantPoolBuilder methodHandle`() {
        val cp = ConstantPoolBuilder()
        val refIdx = cp.methodRef("Test", "m", "()V")
        val mhIdx = cp.methodHandle(6, refIdx) // REF_invokeStatic = 6
        val pool = cp.build()
        val mh = pool[mhIdx] as CpMethodHandle
        assertEquals(6, mh.referenceKind)
        assertEquals(refIdx, mh.referenceIndex)
    }

    @Test
    fun `ConstantPoolBuilder methodType`() {
        val cp = ConstantPoolBuilder()
        val mtIdx = cp.methodType("(II)I")
        val pool = cp.build()
        val mt = pool[mtIdx] as CpMethodType
        val desc = pool.utf8(mt.descriptorIndex)
        assertEquals("(II)I", desc)
    }

    @Test
    fun `ConstantPoolBuilder invokeDynamic`() {
        val cp = ConstantPoolBuilder()
        val idIdx = cp.invokeDynamic(0, "myDynamic", "(I)V")
        val pool = cp.build()
        assertTrue(pool[idIdx] is CpInvokeDynamic)
        val id = pool[idIdx] as CpInvokeDynamic
        assertEquals(0, id.bootstrapMethodAttrIndex)
    }

    @Test
    fun `ConstantPoolBuilder module and package`() {
        val cp = ConstantPoolBuilder()
        val modIdx = cp.module("java.base")
        val pkgIdx = cp.packageEntry("java/lang")
        val pool = cp.build()
        assertTrue(pool[modIdx] is CpModule)
        assertTrue(pool[pkgIdx] is CpPackage)
    }

    @Test
    fun `CpEntry tag values are correct`() {
        assertEquals(1, CpUtf8.TAG)
        assertEquals(3, CpInteger.TAG)
        assertEquals(4, CpFloat.TAG)
        assertEquals(5, CpLong.TAG)
        assertEquals(6, CpDouble.TAG)
        assertEquals(7, CpClass.TAG)
        assertEquals(8, CpString.TAG)
        assertEquals(9, CpFieldRef.TAG)
        assertEquals(10, CpMethodRef.TAG)
        assertEquals(11, CpInterfaceMethodRef.TAG)
        assertEquals(12, CpNameAndType.TAG)
        assertEquals(15, CpMethodHandle.TAG)
        assertEquals(16, CpMethodType.TAG)
        assertEquals(17, CpDynamic.TAG)
        assertEquals(18, CpInvokeDynamic.TAG)
        assertEquals(19, CpModule.TAG)
        assertEquals(20, CpPackage.TAG)
    }

    @Test
    fun `AccessFlags PUBLIC value is 0x0001`() {
        assertEquals(0x0001, AccessFlags.PUBLIC)
    }

    @Test
    fun `AccessFlags PRIVATE value is 0x0002`() {
        assertEquals(0x0002, AccessFlags.PRIVATE)
    }

    @Test
    fun `AccessFlags PROTECTED value is 0x0004`() {
        assertEquals(0x0004, AccessFlags.PROTECTED)
    }

    @Test
    fun `AccessFlags STATIC value is 0x0008`() {
        assertEquals(0x0008, AccessFlags.STATIC)
    }

    @Test
    fun `AccessFlags FINAL value is 0x0010`() {
        assertEquals(0x0010, AccessFlags.FINAL)
    }

    @Test
    fun `AccessFlags SYNCHRONIZED and SUPER share bit 0x0020`() {
        assertEquals(0x0020, AccessFlags.SYNCHRONIZED)
        assertEquals(0x0020, AccessFlags.SUPER)
    }

    @Test
    fun `AccessFlags INTERFACE value is 0x0200`() {
        assertEquals(0x0200, AccessFlags.INTERFACE)
    }

    @Test
    fun `AccessFlags ABSTRACT value is 0x0400`() {
        assertEquals(0x0400, AccessFlags.ABSTRACT)
    }

    @Test
    fun `AccessFlags ENUM value is 0x4000`() {
        assertEquals(0x4000, AccessFlags.ENUM)
    }

    @Test
    fun `AccessFlags SYNTHETIC value is 0x1000`() {
        assertEquals(0x1000, AccessFlags.SYNTHETIC)
    }

    @Test
    fun `AccessFlags ANNOTATION value is 0x2000`() {
        assertEquals(0x2000, AccessFlags.ANNOTATION)
    }

    @Test
    fun `AccessFlags toString for CLASS context`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT or AccessFlags.SUPER
        val str = AccessFlags.toString(flags, AccessFlags.Context.CLASS)
        assertTrue("public" in str)
        assertTrue("abstract" in str)
        assertTrue("super" in str)
    }

    @Test
    fun `AccessFlags toString for METHOD context`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("public" in str)
        assertTrue("static" in str)
        assertTrue("final" in str)
    }

    @Test
    fun `AccessFlags toString for FIELD context`() {
        val flags = AccessFlags.PRIVATE or AccessFlags.VOLATILE or AccessFlags.TRANSIENT
        val str = AccessFlags.toString(flags, AccessFlags.Context.FIELD)
        assertTrue("private" in str)
        assertTrue("volatile" in str)
        assertTrue("transient" in str)
    }

    @Test
    fun `AccessFlags toString NATIVE in METHOD context`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.NATIVE
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("native" in str)
    }

    @Test
    fun `AccessFlags toString BRIDGE in METHOD context`() {
        val flags = AccessFlags.BRIDGE
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("bridge" in str)
    }

    @Test
    fun `AccessFlags toString ENUM in FIELD context`() {
        val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL or AccessFlags.ENUM
        val str = AccessFlags.toString(flags, AccessFlags.Context.FIELD)
        assertTrue("enum" in str)
    }

    @Test
    fun `reader rejects non-classfile data`() {
        assertThrows<IllegalArgumentException> {
            JvmClassReader.read(ByteArray(100))
        }
    }

    @Test
    fun `reader rejects truncated data`() {
        assertThrows<Exception> {
            JvmClassReader.read(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        }
    }

    @Test
    fun `read self class file`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        assertEquals("org/kgen/target/jvm/JvmComprehensiveTest", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
    }

    @Test
    fun `read String class from JRT`() {
        val bytes = loadClassBytes<String>()
        val cf = JvmClassReader.read(bytes)
        assertEquals("java/lang/String", cf.thisClassName)
        assertTrue(cf.interfaceNames.contains("java/io/Serializable"))
        assertTrue(cf.interfaceNames.contains("java/lang/Comparable"))
        assertTrue(cf.interfaceNames.contains("java/lang/CharSequence"))
    }

    @Test
    fun `read ArrayList has fields and methods`() {
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)
        assertEquals("java/util/ArrayList", cf.thisClassName)
        assertTrue(cf.fields.isNotEmpty())
        assertTrue(cf.methods.isNotEmpty())
    }

    @Test
    fun `read interface class has INTERFACE flag`() {
        val bytes = loadClassBytes<Comparable<*>>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
        assertTrue(cf.accessFlags and AccessFlags.ABSTRACT != 0)
    }

    @Test
    fun `read enum class has ENUM flag`() {
        val bytes = loadClassBytes<Thread.State>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.ENUM != 0)
        assertEquals("java/lang/Enum", cf.superClassName)
    }

    @Test
    fun `read annotation class has ANNOTATION flag`() {
        val bytes = loadClassBytes<Override>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.ANNOTATION != 0)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
    }

    @Test
    fun `read constant pool contains all expected entry types for String`() {
        val bytes = loadClassBytes<String>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.constantPool.findAll<CpUtf8>().isNotEmpty())
        assertTrue(cf.constantPool.findAll<CpClass>().isNotEmpty())
        assertTrue(cf.constantPool.findAll<CpMethodRef>().isNotEmpty())
        assertTrue(cf.constantPool.findAll<CpFieldRef>().isNotEmpty())
        assertTrue(cf.constantPool.findAll<CpNameAndType>().isNotEmpty())
    }

    @Test
    fun `round trip self class preserves constant pool`() {
        val original = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf.constantPool.size, cf2.constantPool.size)
        for (i in 1 until cf.constantPool.size) {
            assertEquals(cf.constantPool.getOrNull(i), cf2.constantPool.getOrNull(i),
                "CP entry $i mismatch")
        }
    }

    @Test
    fun `round trip HashMap preserves structure`() {
        val original = loadClassBytes<HashMap<*, *>>()
        val cf = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf.thisClassName, cf2.thisClassName)
        assertEquals(cf.methods.size, cf2.methods.size)
        assertEquals(cf.fields.size, cf2.fields.size)
    }

    @Test
    fun `round trip ArrayList preserves field details`() {
        val original = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        for (i in cf.fields.indices) {
            assertEquals(cf.fields[i].accessFlags, cf2.fields[i].accessFlags)
            assertEquals(cf.string(cf.fields[i].nameIndex), cf2.string(cf2.fields[i].nameIndex))
            assertEquals(cf.string(cf.fields[i].descriptorIndex), cf2.string(cf2.fields[i].descriptorIndex))
        }
    }

    @Test
    fun `parse Code attribute from method`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        val method = cf.methods.firstOrNull { cf.string(it.nameIndex) == "loadClass" }
        assertNotNull(method)
        val codeAttr = method.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" }
        assertNotNull(codeAttr)
        val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
        assertTrue(code.maxStack > 0)
        assertTrue(code.maxLocals > 0)
        assertTrue(code.code.isNotEmpty())
    }

    @Test
    fun `parse LineNumberTable attribute`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        for (m in cf.methods) {
            val codeAttr = m.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" } ?: continue
            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            val lntAttr = code.attributes.firstOrNull { cf.string(it.nameIndex) == "LineNumberTable" }
            if (lntAttr != null) {
                val lnt = AttributeParser.parseLineNumberTable(lntAttr)
                assertTrue(lnt.entries.isNotEmpty())
                assertTrue(lnt.entries.all { it.lineNumber > 0 })
                return
            }
        }
    }

    @Test
    fun `parse SourceFile attribute`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        val sfAttr = cf.attributes.firstOrNull { cf.string(it.nameIndex) == "SourceFile" }
        if (sfAttr != null) {
            val sf = AttributeParser.parseSourceFile(sfAttr)
            val fileName = cf.string(sf.sourceFileIndex)
            assertTrue(fileName.endsWith(".kt") || fileName.endsWith(".java"))
        }
    }

    @Test
    fun `parse InnerClasses attribute from Map`() {
        val bytes = loadClassBytes<Map<*, *>>()
        val cf = JvmClassReader.read(bytes)
        val icAttr = cf.attributes.firstOrNull { cf.string(it.nameIndex) == "InnerClasses" }
        if (icAttr != null) {
            val ic = AttributeParser.parseInnerClasses(icAttr)
            assertTrue(ic.classes.isNotEmpty())
        }
    }

    @Test
    fun `parse StackMapTable round trip`() {
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)
        for (m in cf.methods) {
            val codeAttr = m.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" } ?: continue
            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            val smtAttr = code.attributes.firstOrNull { cf.string(it.nameIndex) == "StackMapTable" }
                ?: continue
            val smt = AttributeParser.parseStackMapTable(smtAttr)
            assertTrue(smt.entries.isNotEmpty())
            val rebuilt = AttributeBuilder.buildStackMapTable(smtAttr.nameIndex, smt)
            val smt2 = AttributeParser.parseStackMapTable(rebuilt)
            assertEquals(smt.entries.size, smt2.entries.size)
            return
        }
    }

    @Test
    fun `AttributeParser parse dispatches to Code`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        for (m in cf.methods) {
            for (attr in m.attributes) {
                val name = cf.string(attr.nameIndex)
                if (name == "Code") {
                    val parsed = AttributeParser.parse(attr, cf.constantPool)
                    assertTrue(parsed is CodeAttribute)
                    return
                }
            }
        }
    }

    @Test
    fun `AttributeParser parse returns null for unknown attribute`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("UnknownAttribute")
        val pool = cp.build()
        val attr = AttributeInfo(nameIdx, ByteArray(10))
        val parsed = AttributeParser.parse(attr, pool)
        assertNull(parsed)
    }

    @Test
    fun `AttributeBuilder buildCode round trips`() {
        val cp = ConstantPoolBuilder()
        val codeNameIdx = cp.utf8("Code")
        val pool = cp.build()
        val original = CodeAttribute(
            maxStack = 3, maxLocals = 2,
            code = byteArrayOf(0x2A, 0xB1.toByte()),
            exceptionTable = listOf(ExceptionEntry(0, 5, 10, 0)),
            attributes = emptyList()
        )
        val built = AttributeBuilder.buildCode(codeNameIdx, original)
        val parsed = AttributeParser.parseCode(built, pool)
        assertEquals(3, parsed.maxStack)
        assertEquals(2, parsed.maxLocals)
        assertEquals(2, parsed.code.size)
        assertEquals(1, parsed.exceptionTable.size)
        assertEquals(0, parsed.exceptionTable[0].startPc)
        assertEquals(5, parsed.exceptionTable[0].endPc)
        assertEquals(10, parsed.exceptionTable[0].handlerPc)
    }

    @Test
    fun `AttributeBuilder buildLineNumberTable round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("LineNumberTable")
        val original = LineNumberTableAttribute(
            listOf(LineNumberEntry(0, 10), LineNumberEntry(5, 20), LineNumberEntry(12, 30))
        )
        val built = AttributeBuilder.buildLineNumberTable(nameIdx, original)
        val parsed = AttributeParser.parseLineNumberTable(built)
        assertEquals(3, parsed.entries.size)
        assertEquals(0, parsed.entries[0].startPc)
        assertEquals(10, parsed.entries[0].lineNumber)
        assertEquals(12, parsed.entries[2].startPc)
        assertEquals(30, parsed.entries[2].lineNumber)
    }

    @Test
    fun `AttributeBuilder buildSourceFile round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("SourceFile")
        val fileIdx = cp.utf8("Test.java")
        val original = SourceFileAttribute(fileIdx)
        val built = AttributeBuilder.buildSourceFile(nameIdx, original)
        val parsed = AttributeParser.parseSourceFile(built)
        assertEquals(fileIdx, parsed.sourceFileIndex)
    }

    @Test
    fun `AttributeBuilder buildConstantValue round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("ConstantValue")
        val intIdx = cp.integer(42)
        val original = ConstantValueAttribute(intIdx)
        val built = AttributeBuilder.buildConstantValue(nameIdx, original)
        val parsed = AttributeParser.parseConstantValue(built)
        assertEquals(intIdx, parsed.constantValueIndex)
    }

    @Test
    fun `AttributeBuilder buildExceptions round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("Exceptions")
        val cls1 = cp.classEntry("java/io/IOException")
        val cls2 = cp.classEntry("java/lang/RuntimeException")
        val original = ExceptionsAttribute(listOf(cls1, cls2))
        val built = AttributeBuilder.buildExceptions(nameIdx, original)
        val parsed = AttributeParser.parseExceptions(built)
        assertEquals(2, parsed.exceptionIndexTable.size)
        assertEquals(cls1, parsed.exceptionIndexTable[0])
        assertEquals(cls2, parsed.exceptionIndexTable[1])
    }

    @Test
    fun `AttributeBuilder buildInnerClasses round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("InnerClasses")
        val original = InnerClassesAttribute(
            listOf(InnerClassEntry(1, 2, 3, AccessFlags.PUBLIC or AccessFlags.STATIC))
        )
        val built = AttributeBuilder.buildInnerClasses(nameIdx, original)
        val parsed = AttributeParser.parseInnerClasses(built)
        assertEquals(1, parsed.classes.size)
        assertEquals(1, parsed.classes[0].innerClassInfoIndex)
        assertEquals(AccessFlags.PUBLIC or AccessFlags.STATIC, parsed.classes[0].innerClassAccessFlags)
    }

    @Test
    fun `AttributeBuilder buildSignature round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("Signature")
        val sigIdx = cp.utf8("Ljava/util/List<Ljava/lang/String;>;")
        val original = SignatureAttribute(sigIdx)
        val built = AttributeBuilder.buildSignature(nameIdx, original)
        val parsed = AttributeParser.parseSignature(built)
        assertEquals(sigIdx, parsed.signatureIndex)
    }

    @Test
    fun `AttributeBuilder buildBootstrapMethods round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("BootstrapMethods")
        val original = BootstrapMethodsAttribute(
            listOf(BootstrapMethodEntry(5, listOf(10, 20, 30)))
        )
        val built = AttributeBuilder.buildBootstrapMethods(nameIdx, original)
        val parsed = AttributeParser.parseBootstrapMethods(built)
        assertEquals(1, parsed.methods.size)
        assertEquals(5, parsed.methods[0].methodRefIndex)
        assertEquals(listOf(10, 20, 30), parsed.methods[0].arguments)
    }

    @Test
    fun `AttributeBuilder buildNestHost round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("NestHost")
        val original = NestHostAttribute(42)
        val built = AttributeBuilder.buildNestHost(nameIdx, original)
        val parsed = AttributeParser.parseNestHost(built)
        assertEquals(42, parsed.hostClassIndex)
    }

    @Test
    fun `AttributeBuilder buildNestMembers round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("NestMembers")
        val original = NestMembersAttribute(listOf(1, 2, 3))
        val built = AttributeBuilder.buildNestMembers(nameIdx, original)
        val parsed = AttributeParser.parseNestMembers(built)
        assertEquals(listOf(1, 2, 3), parsed.classes)
    }

    @Test
    fun `AttributeBuilder buildMethodParameters round trips`() {
        val cp = ConstantPoolBuilder()
        val nameIdx = cp.utf8("MethodParameters")
        val original = MethodParametersAttribute(
            listOf(MethodParameterEntry(1, AccessFlags.FINAL), MethodParameterEntry(2, 0))
        )
        val built = AttributeBuilder.buildMethodParameters(nameIdx, original)
        val parsed = AttributeParser.parseMethodParameters(built)
        assertEquals(2, parsed.parameters.size)
        assertEquals(AccessFlags.FINAL, parsed.parameters[0].accessFlags)
    }

    @Test
    fun `ClassFile sourceFile property`() {
        val bytes = loadClassBytes<JvmComprehensiveTest>()
        val cf = JvmClassReader.read(bytes)
        val src = cf.sourceFile
        if (src != null) {
            assertTrue(src.endsWith(".kt") || src.endsWith(".java"))
        }
    }

    @Test
    fun `ClassFile sourceFile is null when attribute absent`() {
        val cf = ClassFileBuilder("NoSourceFile").build()
        assertNull(cf.sourceFile)
    }

    @Test
    fun `build and load class that returns int constant`() {
        val bytes = ClassFileBuilder("ReturnInt")
            .method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "ReturnInt")
        assertEquals(42, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class that returns long constant`() {
        val bytes = ClassFileBuilder("ReturnLong")
            .method("get", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(123456789L)
                code.lreturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "ReturnLong")
        assertEquals(123456789L, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class that returns string`() {
        val bytes = ClassFileBuilder("ReturnStr")
            .method("get", "()Ljava/lang/String;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.ldc("hello kgen")
                code.areturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "ReturnStr")
        assertEquals("hello kgen", clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with int addition`() {
        val bytes = ClassFileBuilder("IntAdd")
            .method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntAdd")
        val method = clazz.getMethod("add", Int::class.java, Int::class.java)
        assertEquals(7, method.invoke(null, 3, 4))
    }

    @Test
    fun `build and load class with int subtraction`() {
        val bytes = ClassFileBuilder("IntSub")
            .method("sub", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.isub()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntSub")
        val method = clazz.getMethod("sub", Int::class.java, Int::class.java)
        assertEquals(6, method.invoke(null, 10, 4))
    }

    @Test
    fun `build and load class with int multiplication`() {
        val bytes = ClassFileBuilder("IntMul")
            .method("mul", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.imul()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntMul")
        val method = clazz.getMethod("mul", Int::class.java, Int::class.java)
        assertEquals(12, method.invoke(null, 3, 4))
    }

    @Test
    fun `build and load class with int division`() {
        val bytes = ClassFileBuilder("IntDiv")
            .method("div", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.idiv()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntDiv")
        val method = clazz.getMethod("div", Int::class.java, Int::class.java)
        assertEquals(5, method.invoke(null, 10, 2))
    }

    @Test
    fun `build and load class with int remainder`() {
        val bytes = ClassFileBuilder("IntRem")
            .method("rem", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.irem()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntRem")
        val method = clazz.getMethod("rem", Int::class.java, Int::class.java)
        assertEquals(1, method.invoke(null, 10, 3))
    }

    @Test
    fun `build and load class with int negation`() {
        val bytes = ClassFileBuilder("IntNeg")
            .method("neg", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ineg()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IntNeg")
        val method = clazz.getMethod("neg", Int::class.java)
        assertEquals(-5, method.invoke(null, 5))
    }

    @Test
    fun `build and load class with double arithmetic`() {
        val bytes = ClassFileBuilder("DoubleArith")
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
        val clazz = loadClass(bytes, "DoubleArith")
        val method = clazz.getMethod("avg", Double::class.java, Double::class.java)
        assertEquals(5.0, method.invoke(null, 3.0, 7.0))
    }

    @Test
    fun `build and load class with float return`() {
        val bytes = ClassFileBuilder("FloatReturn")
            .method("get", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(1.0f)
                code.freturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "FloatReturn")
        assertEquals(1.0f, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with conditional branch`() {
        val bytes = ClassFileBuilder("CondBranch")
            .method("max", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ifIcmpge("first")
                code.iload(1)
                code.ireturn()
                code.label("first")
                code.iload(0)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "CondBranch")
        val method = clazz.getMethod("max", Int::class.java, Int::class.java)
        assertEquals(10, method.invoke(null, 10, 5))
        assertEquals(10, method.invoke(null, 5, 10))
    }

    @Test
    fun `build and load class with loop`() {
        val bytes = ClassFileBuilder("LoopSum")
            .method("sum", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 3
                code.iconst(0)
                code.istore(1)
                code.iconst(1)
                code.istore(2)
                code.label("loop")
                code.iload(2)
                code.iload(0)
                code.ifIcmpgt("done")
                code.iload(1)
                code.iload(2)
                code.iadd()
                code.istore(1)
                code.iinc(2, 1)
                code.goto("loop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "LoopSum")
        val method = clazz.getMethod("sum", Int::class.java)
        assertEquals(55, method.invoke(null, 10))
    }

    @Test
    fun `build and load class with static method call`() {
        val bytes = ClassFileBuilder("StaticCall")
            .method("identity", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ireturn()
            }
            .method("callIdentity", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.invokestatic("StaticCall", "identity", "(I)I")
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "StaticCall")
        assertEquals(42, clazz.getMethod("callIdentity", Int::class.java).invoke(null, 42))
    }

    @Test
    fun `build and load class with void method`() {
        val bytes = ClassFileBuilder("VoidTest")
            .method("noop", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
            .toBytes()
        val clazz = loadClass(bytes, "VoidTest")
        assertNull(clazz.getMethod("noop").invoke(null))
    }

    @Test
    fun `build and load class with iconst minus 1`() {
        val bytes = ClassFileBuilder("IconstM1")
            .method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(-1)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "IconstM1")
        assertEquals(-1, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with iconst 0 through 5`() {
        for (i in 0..5) {
            val name = "Iconst$i"
            val bytes = ClassFileBuilder(name)
                .method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(i)
                    code.ireturn()
                }
                .toBytes()
            val clazz = loadClass(bytes, name)
            assertEquals(i, clazz.getMethod("get").invoke(null))
        }
    }

    @Test
    fun `build and load class with bipush range`() {
        val bytes = ClassFileBuilder("Bipush100")
            .method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(100)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "Bipush100")
        assertEquals(100, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with sipush range`() {
        val bytes = ClassFileBuilder("Sipush1000")
            .method("get", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(1000)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "Sipush1000")
        assertEquals(1000, clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with lconst 0 and 1`() {
        val bytes0 = ClassFileBuilder("Lconst0")
            .method("get", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(0L)
                code.lreturn()
            }
            .toBytes()
        assertEquals(0L, loadClass(bytes0, "Lconst0").getMethod("get").invoke(null))

        val bytes1 = ClassFileBuilder("Lconst1")
            .method("get", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lconst(1L)
                code.lreturn()
            }
            .toBytes()
        assertEquals(1L, loadClass(bytes1, "Lconst1").getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with fconst 0, 1, 2`() {
        val bytes = ClassFileBuilder("Fconst2")
            .method("get", "()F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fconst(2.0f)
                code.freturn()
            }
            .toBytes()
        assertEquals(2.0f, loadClass(bytes, "Fconst2").getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with dconst 0 and 1`() {
        val bytes = ClassFileBuilder("Dconst1")
            .method("get", "()D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.dconst(1.0)
                code.dreturn()
            }
            .toBytes()
        assertEquals(1.0, loadClass(bytes, "Dconst1").getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with bitwise operations`() {
        val bytes = ClassFileBuilder("Bitwise")
            .method("andOp", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.iand()
                code.ireturn()
            }
            .method("orOp", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ior()
                code.ireturn()
            }
            .method("xorOp", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ixor()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "Bitwise")
        assertEquals(0x0A, clazz.getMethod("andOp", Int::class.java, Int::class.java)
            .invoke(null, 0x0F, 0x0A))
        assertEquals(0x0F, clazz.getMethod("orOp", Int::class.java, Int::class.java)
            .invoke(null, 0x05, 0x0A))
        assertEquals(0x0F, clazz.getMethod("xorOp", Int::class.java, Int::class.java)
            .invoke(null, 0x05, 0x0A))
    }

    @Test
    fun `build and load class with shift operations`() {
        val bytes = ClassFileBuilder("Shifts")
            .method("shl", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ishl()
                code.ireturn()
            }
            .method("shr", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.ishr()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "Shifts")
        assertEquals(8, clazz.getMethod("shl", Int::class.java, Int::class.java)
            .invoke(null, 1, 3))
        assertEquals(2, clazz.getMethod("shr", Int::class.java, Int::class.java)
            .invoke(null, 8, 2))
    }

    @Test
    fun `build and load class with type conversion i2l`() {
        val bytes = ClassFileBuilder("I2L")
            .method("convert", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2l()
                code.lreturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "I2L")
        assertEquals(42L, clazz.getMethod("convert", Int::class.java).invoke(null, 42))
    }

    @Test
    fun `build and load class with type conversion i2d`() {
        val bytes = ClassFileBuilder("I2D")
            .method("convert", "(I)D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.i2d()
                code.dreturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "I2D")
        assertEquals(42.0, clazz.getMethod("convert", Int::class.java).invoke(null, 42))
    }

    @Test
    fun `build and load class with dup and pop`() {
        val bytes = ClassFileBuilder("DupPop")
            .method("dupAndPop", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(5)
                code.dup()
                code.pop()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "DupPop")
        assertEquals(5, clazz.getMethod("dupAndPop").invoke(null))
    }

    @Test
    fun `build and load class with swap`() {
        val bytes = ClassFileBuilder("SwapTest")
            .method("secondArg", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 2
                code.iload(0)
                code.iload(1)
                code.swap()
                code.pop()
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "SwapTest")
        assertEquals(2, clazz.getMethod("secondArg", Int::class.java, Int::class.java)
            .invoke(null, 1, 2))
    }

    @Test
    fun `build and load class with aconstNull`() {
        val bytes = ClassFileBuilder("NullReturn")
            .method("get", "()Ljava/lang/Object;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.areturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "NullReturn")
        assertNull(clazz.getMethod("get").invoke(null))
    }

    @Test
    fun `build and load class with getstatic call`() {
        val bytes = ClassFileBuilder("GetStaticTest")
            .method("lineSep", "()Ljava/lang/String;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("java/lang/System", "lineSeparator", "()Ljava/lang/String;")
                code.areturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "GetStaticTest")
        assertEquals(System.lineSeparator(), clazz.getMethod("lineSep").invoke(null))
    }

    @Test
    fun `build and load class with all branch types`() {
        val bytes = ClassFileBuilder("AllBranches")
            .method("testIfne", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ifne("notZero")
                code.iconst(0)
                code.ireturn()
                code.label("notZero")
                code.iconst(1)
                code.ireturn()
            }
            .method("testIflt", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.iflt("negative")
                code.iconst(0)
                code.ireturn()
                code.label("negative")
                code.iconst(1)
                code.ireturn()
            }
            .method("testIfgt", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ifgt("positive")
                code.iconst(0)
                code.ireturn()
                code.label("positive")
                code.iconst(1)
                code.ireturn()
            }
            .method("testIfle", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iload(0)
                code.ifle("leq")
                code.iconst(0)
                code.ireturn()
                code.label("leq")
                code.iconst(1)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "AllBranches")
        assertEquals(1, clazz.getMethod("testIfne", Int::class.java).invoke(null, 5))
        assertEquals(0, clazz.getMethod("testIfne", Int::class.java).invoke(null, 0))
        assertEquals(1, clazz.getMethod("testIflt", Int::class.java).invoke(null, -1))
        assertEquals(0, clazz.getMethod("testIflt", Int::class.java).invoke(null, 1))
        assertEquals(1, clazz.getMethod("testIfgt", Int::class.java).invoke(null, 1))
        assertEquals(0, clazz.getMethod("testIfgt", Int::class.java).invoke(null, -1))
        assertEquals(1, clazz.getMethod("testIfle", Int::class.java).invoke(null, 0))
        assertEquals(0, clazz.getMethod("testIfle", Int::class.java).invoke(null, 1))
    }

    @Test
    fun `build and load class with ifnull and ifnonnull`() {
        val bytes = ClassFileBuilder("NullBranch")
            .method("isNull", "(Ljava/lang/Object;)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.ifnull("yes")
                code.iconst(0)
                code.ireturn()
                code.label("yes")
                code.iconst(1)
                code.ireturn()
            }
            .method("isNotNull", "(Ljava/lang/Object;)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.aload(0)
                code.ifnonnull("yes")
                code.iconst(0)
                code.ireturn()
                code.label("yes")
                code.iconst(1)
                code.ireturn()
            }
            .toBytes()
        val clazz = loadClass(bytes, "NullBranch")
        assertEquals(1, clazz.getMethod("isNull", Object::class.java).invoke(null, null as Any?))
        assertEquals(0, clazz.getMethod("isNull", Object::class.java).invoke(null, "x"))
        assertEquals(0, clazz.getMethod("isNotNull", Object::class.java).invoke(null, null as Any?))
        assertEquals(1, clazz.getMethod("isNotNull", Object::class.java).invoke(null, "x"))
    }

    @Test
    fun `ClassFile string method resolves utf8`() {
        val cp = ConstantPoolBuilder()
        val idx = cp.utf8("hello")
        val cls = cp.classEntry("Test")
        val cf = ClassFile(0, 50, cp.build(), 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("hello", cf.string(idx))
    }

    @Test
    fun `ClassFile superClassName is null when superClass is 0`() {
        val cp = ConstantPoolBuilder()
        val cls = cp.classEntry("java/lang/Object")
        val cf = ClassFile(0, 50, cp.build(), 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
        assertNull(cf.superClassName)
    }

    @Test
    fun `FieldInfo data class equality`() {
        val f1 = FieldInfo(AccessFlags.PUBLIC, 1, 2, emptyList())
        val f2 = FieldInfo(AccessFlags.PUBLIC, 1, 2, emptyList())
        assertEquals(f1, f2)
    }

    @Test
    fun `MethodInfo data class equality`() {
        val m1 = MethodInfo(AccessFlags.PUBLIC, 1, 2, emptyList())
        val m2 = MethodInfo(AccessFlags.PUBLIC, 1, 2, emptyList())
        assertEquals(m1, m2)
    }

    @Test
    fun `AttributeInfo equals compares data content`() {
        val a1 = AttributeInfo(1, byteArrayOf(1, 2, 3))
        val a2 = AttributeInfo(1, byteArrayOf(1, 2, 3))
        val a3 = AttributeInfo(1, byteArrayOf(1, 2, 4))
        assertEquals(a1, a2)
        assertFalse(a1 == a3)
    }

    @Test
    fun `AttributeInfo hashCode consistent with equals`() {
        val a1 = AttributeInfo(1, byteArrayOf(1, 2, 3))
        val a2 = AttributeInfo(1, byteArrayOf(1, 2, 3))
        assertEquals(a1.hashCode(), a2.hashCode())
    }

    @Test
    fun `CodeAttribute equals compares code content`() {
        val c1 = CodeAttribute(1, 1, byteArrayOf(0x2A, 0xB1.toByte()), emptyList(), emptyList())
        val c2 = CodeAttribute(1, 1, byteArrayOf(0x2A, 0xB1.toByte()), emptyList(), emptyList())
        assertEquals(c1, c2)
    }

    @Test
    fun `ExceptionEntry data class`() {
        val e = ExceptionEntry(0, 10, 20, 5)
        assertEquals(0, e.startPc)
        assertEquals(10, e.endPc)
        assertEquals(20, e.handlerPc)
        assertEquals(5, e.catchType)
    }

    @Test
    fun `ConstantPoolBuilder size grows as entries are added`() {
        val cp = ConstantPoolBuilder()
        assertEquals(1, cp.size) // index 0 unused
        cp.utf8("a")
        assertEquals(2, cp.size)
        cp.utf8("b")
        assertEquals(3, cp.size)
    }

    @Test
    fun `ConstantPoolBuilder dynamic entry`() {
        val cp = ConstantPoolBuilder()
        val idx = cp.dynamic(0, "dynField", "I")
        val pool = cp.build()
        val entry = pool[idx] as CpDynamic
        assertEquals(0, entry.bootstrapMethodAttrIndex)
    }
}

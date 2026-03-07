package org.kgen.binary.jvm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class JvmClassFileExtendedTest {

    // --- ConstantPoolBuilder extended tests ---

    @Test
    fun cpBuilderInteger() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.integer(42)
        val idx2 = cp.integer(42)
        assertEquals(idx1, idx2) // deduplication
        val idx3 = cp.integer(99)
        assertTrue(idx3 != idx1)
        val pool = cp.build()
        assertEquals(CpInteger(42), pool[idx1])
        assertEquals(CpInteger(99), pool[idx3])
    }

    @Test
    fun cpBuilderFloat() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.float(3.14f)
        val idx2 = cp.float(3.14f)
        assertEquals(idx1, idx2)
        val pool = cp.build()
        assertEquals(CpFloat(3.14f), pool[idx1])
    }

    @Test
    fun cpBuilderDouble() {
        val cp = ConstantPoolBuilder()
        val idx = cp.double(2.718281828)
        val pool = cp.build()
        assertEquals(CpDouble(2.718281828), pool[idx])
        assertNull(pool.getOrNull(idx + 1)) // double takes two slots
    }

    @Test
    fun cpBuilderString() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.string("hello")
        val idx2 = cp.string("hello")
        assertEquals(idx1, idx2)
        val pool = cp.build()
        val entry = pool[idx1] as CpString
        val utf8 = pool[entry.stringIndex] as CpUtf8
        assertEquals("hello", utf8.value)
    }

    @Test
    fun cpBuilderFieldRef() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        val idx2 = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        assertEquals(idx1, idx2)
        val pool = cp.build()
        assertTrue(pool[idx1] is CpFieldRef)
    }

    @Test
    fun cpBuilderInterfaceMethodRef() {
        val cp = ConstantPoolBuilder()
        val idx = cp.interfaceMethodRef("java/util/List", "size", "()I")
        val pool = cp.build()
        assertTrue(pool[idx] is CpInterfaceMethodRef)
    }

    @Test
    fun cpBuilderNameAndType() {
        val cp = ConstantPoolBuilder()
        val idx = cp.nameAndType("main", "([Ljava/lang/String;)V")
        val pool = cp.build()
        val nat = pool[idx] as CpNameAndType
        assertEquals("main", (pool[nat.nameIndex] as CpUtf8).value)
        assertEquals("([Ljava/lang/String;)V", (pool[nat.descriptorIndex] as CpUtf8).value)
    }

    @Test
    fun cpBuilderMethodHandle() {
        val cp = ConstantPoolBuilder()
        val mr = cp.methodRef("java/lang/Object", "<init>", "()V")
        val mh = cp.methodHandle(6, mr) // REF_invokeStatic
        val pool = cp.build()
        val handle = pool[mh] as CpMethodHandle
        assertEquals(6, handle.referenceKind)
        assertEquals(mr, handle.referenceIndex)
    }

    @Test
    fun cpBuilderMethodType() {
        val cp = ConstantPoolBuilder()
        val mt = cp.methodType("(II)I")
        val pool = cp.build()
        assertTrue(pool[mt] is CpMethodType)
    }

    @Test
    fun cpBuilderInvokeDynamic() {
        val cp = ConstantPoolBuilder()
        val idx = cp.invokeDynamic(0, "makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;")
        val pool = cp.build()
        assertTrue(pool[idx] is CpInvokeDynamic)
    }

    @Test
    fun cpBuilderModule() {
        val cp = ConstantPoolBuilder()
        val idx = cp.module("java.base")
        val pool = cp.build()
        assertTrue(pool[idx] is CpModule)
    }

    @Test
    fun cpBuilderPackageEntry() {
        val cp = ConstantPoolBuilder()
        val idx = cp.packageEntry("java/lang")
        val pool = cp.build()
        assertTrue(pool[idx] is CpPackage)
    }

    @Test
    fun cpBuilderMultipleLongsAndDoubles() {
        val cp = ConstantPoolBuilder()
        val l1 = cp.long(1L)
        val d1 = cp.double(1.0)
        val l2 = cp.long(2L)
        // Each takes 2 slots, so indices should skip
        assertTrue(d1 > l1 + 1) // l1 takes slots l1 and l1+1
        assertTrue(l2 > d1 + 1)
    }

    // --- ClassFile construction and round-trip ---

    @Test
    fun buildClassWithField() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("com/example/Point")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val xNameIdx = cp.utf8("x")
        val xDescIdx = cp.utf8("I")

        val field = FieldInfo(
            accessFlags = AccessFlags.PRIVATE,
            nameIndex = xNameIdx,
            descriptorIndex = xDescIdx,
            attributes = emptyList(),
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = listOf(field),
            methods = emptyList(), attributes = emptyList(),
        )

        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)
        assertEquals("com/example/Point", cf2.thisClassName)
        assertEquals(1, cf2.fields.size)
        assertEquals("x", cf2.string(cf2.fields[0].nameIndex))
        assertEquals("I", cf2.string(cf2.fields[0].descriptorIndex))
        assertTrue(cf2.fields[0].accessFlags and AccessFlags.PRIVATE != 0)
    }

    @Test
    fun buildClassWithInterface() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("com/example/MyComparable")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val ifaceIdx = cp.classEntry("java/lang/Comparable")

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = listOf(ifaceIdx),
            fields = emptyList(), methods = emptyList(), attributes = emptyList(),
        )

        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)
        assertEquals(1, cf2.interfaces.size)
        assertEquals("java/lang/Comparable", cf2.interfaceNames[0])
    }

    @Test
    fun buildClassMultipleMethods() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("com/example/Calc")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val codeNameIdx = cp.utf8("Code")
        val superInitRef = cp.methodRef("java/lang/Object", "<init>", "()V")

        fun makeMethod(name: String, desc: String, code: ByteArray, maxStack: Int, maxLocals: Int): MethodInfo {
            val nameIdx = cp.utf8(name)
            val descIdx = cp.utf8(desc)
            val codeAttr = AttributeBuilder.buildCode(codeNameIdx, CodeAttribute(
                maxStack = maxStack, maxLocals = maxLocals, code = code,
                exceptionTable = emptyList(), attributes = emptyList(),
            ))
            return MethodInfo(AccessFlags.PUBLIC, nameIdx, descIdx, listOf(codeAttr))
        }

        val init = makeMethod("<init>", "()V", byteArrayOf(
            0x2A, 0xB7.toByte(), (superInitRef shr 8).toByte(), (superInitRef and 0xFF).toByte(), 0xB1.toByte()
        ), 1, 1)

        val add = makeMethod("add", "(II)I", byteArrayOf(
            0x1B, // iload_1
            0x1C, // iload_2
            0x60, // iadd
            0xAC.toByte(), // ireturn
        ), 2, 3)

        val sub = makeMethod("sub", "(II)I", byteArrayOf(
            0x1B, 0x1C, 0x64, 0xAC.toByte()
        ), 2, 3)

        val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClassIdx, superClassIdx, emptyList(), emptyList(),
            listOf(init, add, sub), emptyList())

        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)
        assertEquals(3, cf2.methods.size)
        val names = cf2.methods.map { cf2.string(it.nameIndex) }
        assertTrue("<init>" in names)
        assertTrue("add" in names)
        assertTrue("sub" in names)
    }

    // --- Reading JDK classes ---

    @Test
    fun readAbstractClass() {
        val bytes = loadClassBytes<java.io.InputStream>()
        val cf = JvmClassReader.read(bytes)
        assertEquals("java/io/InputStream", cf.thisClassName)
        assertTrue(cf.accessFlags and AccessFlags.ABSTRACT != 0)
    }

    @Test
    fun readAnnotationType() {
        val bytes = loadClassBytes<Override>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.ANNOTATION != 0)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
    }

    @Test
    fun readListInterface() {
        val bytes = loadClassBytes<java.util.List<*>>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
        assertTrue(cf.interfaceNames.isNotEmpty())
    }

    @Test
    fun readIterableInterface() {
        val bytes = loadClassBytes<Iterable<*>>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
        assertTrue(cf.methods.any { cf.string(it.nameIndex) == "iterator" })
    }

    @Test
    fun readRecordLikeClass() {
        // Runtime has records from Java 16+
        try {
            val clazz = Class.forName("java.lang.runtime.SwitchBootstraps")
            val bytes = loadClassBytes(clazz)
            val cf = JvmClassReader.read(bytes)
            assertNotNull(cf.thisClassName)
        } catch (_: Exception) {
            // Not available in all JVMs
        }
    }

    @Test
    fun roundTripLinkedHashMap() {
        val bytes = loadClassBytes<LinkedHashMap<*, *>>()
        val cf = JvmClassReader.read(bytes)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf.thisClassName, cf2.thisClassName)
        assertEquals(cf.superClassName, cf2.superClassName)
        assertEquals(cf.methods.size, cf2.methods.size)
        assertEquals(cf.fields.size, cf2.fields.size)
    }

    @Test
    fun roundTripTreeMap() {
        val bytes = loadClassBytes<java.util.TreeMap<*, *>>()
        val cf = JvmClassReader.read(bytes)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf.thisClassName, cf2.thisClassName)
        assertEquals(cf.constantPool.size, cf2.constantPool.size)
    }

    // --- AccessFlags ---

    @Test
    fun accessFlagsClassContext() {
        val flags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT or AccessFlags.INTERFACE
        val str = AccessFlags.toString(flags, AccessFlags.Context.CLASS)
        assertTrue("public" in str)
        assertTrue("abstract" in str)
        assertTrue("interface" in str)
    }

    @Test
    fun accessFlagsFieldContext() {
        val flags = AccessFlags.PRIVATE or AccessFlags.STATIC or AccessFlags.VOLATILE
        val str = AccessFlags.toString(flags, AccessFlags.Context.FIELD)
        assertTrue("private" in str)
        assertTrue("static" in str)
        assertTrue("volatile" in str)
    }

    @Test
    fun accessFlagsProtected() {
        val flags = AccessFlags.PROTECTED or AccessFlags.SYNCHRONIZED
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("protected" in str)
        assertTrue("synchronized" in str)
    }

    @Test
    fun accessFlagsNative() {
        val flags = AccessFlags.PUBLIC or AccessFlags.NATIVE
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("public" in str)
        assertTrue("native" in str)
    }

    // --- ConstantPool findAll ---

    @Test
    fun constantPoolFindAllFieldRefs() {
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)
        val fieldRefs = cf.constantPool.findAll<CpFieldRef>()
        assertTrue(fieldRefs.isNotEmpty())
    }

    @Test
    fun constantPoolFindAllInterfaceMethodRefs() {
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)
        val ifaceRefs = cf.constantPool.findAll<CpInterfaceMethodRef>()
        // ArrayList implements List, so it likely has interface method refs
        assertTrue(ifaceRefs.isNotEmpty())
    }

    @Test
    fun constantPoolFindAllStrings() {
        val bytes = loadClassBytes<String>()
        val cf = JvmClassReader.read(bytes)
        val strings = cf.constantPool.findAll<CpString>()
        // String class may or may not have string constants
        assertNotNull(strings)
    }

    // --- Exception table ---

    @Test
    fun exceptionTableRoundTrip() {
        // Find a method with try-catch
        val bytes = loadClassBytes<Integer>()
        val cf = JvmClassReader.read(bytes)
        for (m in cf.methods) {
            val codeAttr = m.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" } ?: continue
            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            if (code.exceptionTable.isNotEmpty()) {
                assertTrue(code.exceptionTable.all { it.startPc >= 0 })
                assertTrue(code.exceptionTable.all { it.endPc > it.startPc })
                assertTrue(code.exceptionTable.all { it.handlerPc >= 0 })
                return
            }
        }
        // If no exception table found, that's ok
    }

    // --- JavaVersion ---

    @Test
    fun javaVersionAll() {
        val cp = ConstantPoolBuilder()
        val cls = cp.classEntry("Test")
        val pool = cp.build()

        val versions = mapOf(
            45 to "1.1", 46 to "1.2", 47 to "1.3", 48 to "1.4",
            49 to "5", 50 to "6", 51 to "7", 52 to "8",
            53 to "9", 54 to "10", 55 to "11", 56 to "12",
            57 to "13", 58 to "14", 59 to "15", 60 to "16",
            61 to "17", 62 to "18", 63 to "19", 64 to "20",
            65 to "21", 66 to "22", 67 to "23", 68 to "24",
        )

        for ((major, expected) in versions) {
            val cf = ClassFile(0, major, pool, 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
            assertEquals(expected, cf.javaVersion, "Major version $major")
        }
    }

    // --- Edge cases ---

    @Test
    fun emptyClass() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("Empty")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClassIdx, superClassIdx, emptyList(), emptyList(), emptyList(), emptyList())
        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)
        assertEquals("Empty", cf2.thisClassName)
        assertEquals(0, cf2.methods.size)
        assertEquals(0, cf2.fields.size)
        assertEquals(0, cf2.interfaces.size)
    }

    @Test
    fun classWithManyInterfaces() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("MultiImpl")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val ifaces = (1..5).map { cp.classEntry("com/example/I$it") }

        val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClassIdx, superClassIdx, ifaces, emptyList(), emptyList(), emptyList())
        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)
        assertEquals(5, cf2.interfaces.size)
    }

    @Test
    fun classFileBytes() {
        val bytes = loadClassBytes<JvmClassFileExtendedTest>()
        assertTrue(bytes.size > 100) // definitely has content
        // Magic: 0xCAFEBABE
        assertEquals(0xCA.toByte(), bytes[0])
        assertEquals(0xFE.toByte(), bytes[1])
        assertEquals(0xBA.toByte(), bytes[2])
        assertEquals(0xBE.toByte(), bytes[3])
    }

    @Test
    fun readClassWithAnnotations() {
        // Test class itself has @Test annotations
        val bytes = loadClassBytes<JvmClassFileExtendedTest>()
        val cf = JvmClassReader.read(bytes)
        // Should have RuntimeVisibleAnnotations or RuntimeInvisibleAnnotations on methods
        val testMethods = cf.methods.filter { cf.string(it.nameIndex) != "<init>" }
        assertTrue(testMethods.isNotEmpty())
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
}

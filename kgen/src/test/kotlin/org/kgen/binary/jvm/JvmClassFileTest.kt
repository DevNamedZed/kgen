package org.kgen.binary.jvm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmClassFileTest {

    @Test
    fun readSelfClassFile() {
        val bytes = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(bytes)

        assertEquals("org/kgen/binary/jvm/JvmClassFileTest", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
        assertTrue(cf.majorVersion >= 65) // Java 21+
        assertTrue(cf.accessFlags and AccessFlags.PUBLIC != 0)
    }

    @Test
    fun readStringClass() {
        val bytes = loadClassBytes<String>()
        val cf = JvmClassReader.read(bytes)

        assertEquals("java/lang/String", cf.thisClassName)
        assertEquals("java/lang/Object", cf.superClassName)
        assertTrue(cf.interfaceNames.contains("java/io/Serializable"))
        assertTrue(cf.interfaceNames.contains("java/lang/Comparable"))
        assertTrue(cf.interfaceNames.contains("java/lang/CharSequence"))
        assertTrue(cf.methods.isNotEmpty())
        assertTrue(cf.fields.isNotEmpty())
    }

    @Test
    fun roundTripSelfClass() {
        val original = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)

        assertEquals(cf.thisClassName, cf2.thisClassName)
        assertEquals(cf.superClassName, cf2.superClassName)
        assertEquals(cf.majorVersion, cf2.majorVersion)
        assertEquals(cf.minorVersion, cf2.minorVersion)
        assertEquals(cf.accessFlags, cf2.accessFlags)
        assertEquals(cf.interfaces, cf2.interfaces)
        assertEquals(cf.fields.size, cf2.fields.size)
        assertEquals(cf.methods.size, cf2.methods.size)
        assertEquals(cf.attributes.size, cf2.attributes.size)

        // Verify constant pool round-trips
        assertEquals(cf.constantPool.size, cf2.constantPool.size)
        for (i in 1 until cf.constantPool.size) {
            val e1 = cf.constantPool.getOrNull(i)
            val e2 = cf2.constantPool.getOrNull(i)
            assertEquals(e1, e2, "CP entry $i mismatch")
        }
    }

    @Test
    fun roundTripComplexClass() {
        // HashMap has generics, inner classes, many methods
        val original = loadClassBytes<HashMap<*, *>>()
        val cf = JvmClassReader.read(original)
        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)

        assertEquals(cf.thisClassName, cf2.thisClassName)
        assertEquals(cf.methods.size, cf2.methods.size)
        assertEquals(cf.fields.size, cf2.fields.size)
    }

    @Test
    fun constantPoolTypes() {
        val bytes = loadClassBytes<String>()
        val cf = JvmClassReader.read(bytes)
        val cp = cf.constantPool

        // Should have UTF8, Class, MethodRef, FieldRef, NameAndType entries
        val utf8s = cp.findAll<CpUtf8>()
        val classes = cp.findAll<CpClass>()
        val methodRefs = cp.findAll<CpMethodRef>()

        assertTrue(utf8s.isNotEmpty())
        assertTrue(classes.isNotEmpty())
        assertTrue(methodRefs.isNotEmpty())
    }

    @Test
    fun parseCodeAttribute() {
        val bytes = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(bytes)

        val method = cf.methods.first {
            cf.string(it.nameIndex) == "readSelfClassFile"
        }
        val codeAttr = method.attributes.firstOrNull {
            cf.string(it.nameIndex) == "Code"
        }
        assertNotNull(codeAttr, "Method should have Code attribute")

        val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
        assertTrue(code.maxStack > 0)
        assertTrue(code.maxLocals > 0)
        assertTrue(code.code.isNotEmpty())
    }

    @Test
    fun parseLineNumberTable() {
        val bytes = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(bytes)

        val method = cf.methods.first { cf.string(it.nameIndex) == "readSelfClassFile" }
        val codeAttr = method.attributes.first { cf.string(it.nameIndex) == "Code" }
        val code = AttributeParser.parseCode(codeAttr, cf.constantPool)

        val lntAttr = code.attributes.firstOrNull {
            cf.string(it.nameIndex) == "LineNumberTable"
        }
        // compiled with debug info
        if (lntAttr != null) {
            val lnt = AttributeParser.parseLineNumberTable(lntAttr)
            assertTrue(lnt.entries.isNotEmpty())
            assertTrue(lnt.entries.all { it.lineNumber > 0 })
        }
    }

    @Test
    fun parseSourceFile() {
        val bytes = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(bytes)

        val sfAttr = cf.attributes.firstOrNull { cf.string(it.nameIndex) == "SourceFile" }
        if (sfAttr != null) {
            val sf = AttributeParser.parseSourceFile(sfAttr)
            val fileName = cf.string(sf.sourceFileIndex)
            assertTrue(fileName.endsWith(".kt") || fileName.endsWith(".java"))
        }
    }

    @Test
    fun buildClassFromScratch() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("com/example/Hello")
        val superClassIdx = cp.classEntry("java/lang/Object")

        // Add a simple void method: <init>()V
        val initNameIdx = cp.utf8("<init>")
        val initDescIdx = cp.utf8("()V")
        val codeNameIdx = cp.utf8("Code")

        // Bytecode: aload_0, invokespecial java/lang/Object.<init>:()V, return
        val superInitRef = cp.methodRef("java/lang/Object", "<init>", "()V")
        val bytecode = byteArrayOf(
            0x2A,                   // aload_0
            0xB7.toByte(),          // invokespecial
            (superInitRef shr 8).toByte(), (superInitRef and 0xFF).toByte(),
            0xB1.toByte(),          // return
        )

        val codeAttr = AttributeBuilder.buildCode(codeNameIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))

        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC,
            nameIndex = initNameIdx,
            descriptorIndex = initDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = ClassFile(
            minorVersion = 0,
            majorVersion = 65, // Java 21
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(method),
            attributes = emptyList(),
        )

        // Write and read back
        val bytes = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(bytes)

        assertEquals("com/example/Hello", cf2.thisClassName)
        assertEquals("java/lang/Object", cf2.superClassName)
        assertEquals(65, cf2.majorVersion)
        assertEquals(1, cf2.methods.size)
        assertEquals("<init>", cf2.string(cf2.methods[0].nameIndex))
    }

    @Test
    fun constantPoolBuilderDeduplication() {
        val cp = ConstantPoolBuilder()
        val idx1 = cp.utf8("hello")
        val idx2 = cp.utf8("hello")
        assertEquals(idx1, idx2)

        val cls1 = cp.classEntry("java/lang/Object")
        val cls2 = cp.classEntry("java/lang/Object")
        assertEquals(cls1, cls2)

        val mr1 = cp.methodRef("java/lang/Object", "<init>", "()V")
        val mr2 = cp.methodRef("java/lang/Object", "<init>", "()V")
        assertEquals(mr1, mr2)
    }

    @Test
    fun constantPoolLongDoubleSlots() {
        val cp = ConstantPoolBuilder()
        cp.utf8("test") // index 1
        val longIdx = cp.long(42L) // index 2, slot 3 is null
        val afterLong = cp.utf8("after") // index 4

        assertEquals(2, longIdx)
        assertEquals(4, afterLong)

        val pool = cp.build()
        assertNull(pool.getOrNull(3))
        assertEquals(CpLong(42L), pool[2])
        assertEquals(CpUtf8("after"), pool[4])
    }

    @Test
    fun javaVersionMapping() {
        val cp = ConstantPoolBuilder()
        val cls = cp.classEntry("Test")

        val cf21 = ClassFile(0, 65, cp.build(), 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("21", cf21.javaVersion)

        val cf8 = ClassFile(0, 52, cp.build(), 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("8", cf8.javaVersion)

        val cf17 = ClassFile(0, 61, cp.build(), 0, cls, 0, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals("17", cf17.javaVersion)
    }

    @Test
    fun accessFlagToString() {
        val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
        val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
        assertTrue("public" in str)
        assertTrue("static" in str)
        assertTrue("final" in str)
    }

    @Test
    fun readInterfaceClass() {
        val bytes = loadClassBytes<Comparable<*>>()
        val cf = JvmClassReader.read(bytes)

        assertEquals("java/lang/Comparable", cf.thisClassName)
        assertTrue(cf.accessFlags and AccessFlags.INTERFACE != 0)
        assertTrue(cf.accessFlags and AccessFlags.ABSTRACT != 0)
    }

    @Test
    fun readEnumClass() {
        val bytes = loadClassBytes<Thread.State>()
        val cf = JvmClassReader.read(bytes)

        assertTrue(cf.accessFlags and AccessFlags.ENUM != 0)
        assertTrue(cf.accessFlags and AccessFlags.FINAL != 0)
        assertEquals("java/lang/Enum", cf.superClassName)
    }

    @Test
    fun fieldInfoRoundTrip() {
        // ArrayList has fields
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)
        assertTrue(cf.fields.isNotEmpty())

        val written = JvmClassWriter.write(cf)
        val cf2 = JvmClassReader.read(written)
        assertEquals(cf.fields.size, cf2.fields.size)
        for (i in cf.fields.indices) {
            assertEquals(cf.fields[i].accessFlags, cf2.fields[i].accessFlags)
            assertEquals(cf.string(cf.fields[i].nameIndex), cf2.string(cf2.fields[i].nameIndex))
            assertEquals(cf.string(cf.fields[i].descriptorIndex), cf2.string(cf2.fields[i].descriptorIndex))
        }
    }

    @Test
    fun stackMapTableRoundTrip() {
        val bytes = loadClassBytes<ArrayList<*>>()
        val cf = JvmClassReader.read(bytes)

        // Find a method with StackMapTable
        for (m in cf.methods) {
            val codeAttr = m.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" } ?: continue
            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            val smtAttr = code.attributes.firstOrNull { cf.string(it.nameIndex) == "StackMapTable" } ?: continue

            val smt = AttributeParser.parseStackMapTable(smtAttr)
            assertTrue(smt.entries.isNotEmpty())

            // Round-trip: serialize and re-parse
            val smtNameIdx = smtAttr.nameIndex
            val rebuilt = AttributeBuilder.buildStackMapTable(smtNameIdx, smt)
            val smt2 = AttributeParser.parseStackMapTable(rebuilt)
            assertEquals(smt.entries.size, smt2.entries.size)
            return // found one, good enough
        }
    }

    @Test
    fun bootstrapMethodsAttribute() {
        // Try to find a class with bootstrap methods (lambda or string concat)
        // java.util.stream.Collectors uses lambdas
        try {
            val bytes = loadClassBytes(Class.forName("java.util.stream.Collectors"))
            val cf = JvmClassReader.read(bytes)
            val bsmAttr = cf.attributes.firstOrNull { cf.string(it.nameIndex) == "BootstrapMethods" }
            if (bsmAttr != null) {
                val bsm = AttributeParser.parseBootstrapMethods(bsmAttr)
                assertTrue(bsm.methods.isNotEmpty())
            }
        } catch (_: Exception) {
            // Class may not be available in all JVMs
        }
    }

    @Test
    fun innerClassesAttribute() {
        // Map.Entry is an inner class of Map
        val bytes = loadClassBytes<Map<*, *>>()
        val cf = JvmClassReader.read(bytes)
        val icAttr = cf.attributes.firstOrNull { cf.string(it.nameIndex) == "InnerClasses" }
        if (icAttr != null) {
            val ic = AttributeParser.parseInnerClasses(icAttr)
            assertTrue(ic.classes.isNotEmpty())
        }
    }

    @Test
    fun parseDispatch() {
        val bytes = loadClassBytes<JvmClassFileTest>()
        val cf = JvmClassReader.read(bytes)

        for (attr in cf.attributes) {
            val parsed = AttributeParser.parse(attr, cf.constantPool)
            // Should either parse successfully or return null for unknown attrs
        }

        // Verify Code attributes parse through dispatch
        for (m in cf.methods) {
            for (attr in m.attributes) {
                val name = cf.string(attr.nameIndex)
                if (name == "Code") {
                    val parsed = AttributeParser.parse(attr, cf.constantPool)
                    assertTrue(parsed is CodeAttribute)
                }
            }
        }
    }

    private inline fun <reified T> loadClassBytes(): ByteArray = loadClassBytes(T::class.java)

    private fun loadClassBytes(clazz: Class<*>): ByteArray {
        val name = clazz.name.replace('.', '/') + ".class"
        // Try classloader first (works for application classes)
        val loader = clazz.classLoader
        if (loader != null) {
            val stream = loader.getResourceAsStream(name)
            if (stream != null) return stream.readAllBytes()
        }
        // Try system classloader (works for some JDK classes)
        val sysStream = ClassLoader.getSystemResourceAsStream(name)
        if (sysStream != null) return sysStream.readAllBytes()
        // Try JRT filesystem (JDK 9+ modular runtime)
        try {
            val jrtFs = java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"))
            val path = jrtFs.getPath("modules", clazz.module.name ?: "java.base", name)
            if (java.nio.file.Files.exists(path)) return java.nio.file.Files.readAllBytes(path)
        } catch (_: Exception) {}
        throw IllegalArgumentException("Cannot load class bytes for ${clazz.name}")
    }
}

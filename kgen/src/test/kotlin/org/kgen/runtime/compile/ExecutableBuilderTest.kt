package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class ExecutableBuilderTest {

    private fun buildMainClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Main")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("main")
        val descIdx = cp.utf8("()I")
        val codeIdx = cp.utf8("Code")
        val bytecode = byteArrayOf(0x10, 42, 0xAC.toByte()) // bipush 42, ireturn
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))
    }

    private fun buildAddClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/AddOps")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("add")
        val descIdx = cp.utf8("(II)I")
        val codeIdx = cp.utf8("Code")
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()) // iload_0, iload_1, iadd, ireturn
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 2, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))
    }

    @Test
    fun `build executable with metadata`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(buildMainClass())
            .setModuleName("test-app")
            .setVersion("1.0.0")
            .build()
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF) // ELF magic
    }

    @Test
    fun `build executable with resources`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(buildMainClass())
            .addResource("config.txt", "key=value".toByteArray())
            .addResource("data.bin", byteArrayOf(1, 2, 3, 4))
            .build()
        assertTrue(exe.isNotEmpty())
    }

    @Test
    fun `build executable with custom metadata`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(buildMainClass())
            .setMetadata("author", "test")
            .setMetadata("license", "MIT")
            .build()
        assertTrue(exe.isNotEmpty())
    }

    @Test
    fun `build with multiple classes`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFiles(listOf(buildMainClass(), buildAddClass()))
            .setMainClass("org/kgen/test/Main")
            .build()
        assertTrue(exe.isNotEmpty())
    }

    @Test
    fun `build fails with no class files`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutableBuilder(Target.x86_64()).build()
        }
    }

    @Test
    fun `build PE executable`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.WINDOWS)
            .addClassFile(buildMainClass())
            .setModuleName("test")
            .build()
        assertTrue(exe.isNotEmpty())
        assertEquals('M'.code, exe[0].toInt())
        assertEquals('Z'.code, exe[1].toInt())
    }
}

class ExecutableReaderTest {

    @Test
    fun `round-trip metadata`() {
        val meta = buildMeta("myapp", "2.0.0", "x86_64-linux-gnu",
            listOf("main", "add"), mapOf("author" to "test"))
        val reader = ExecutableReader.read(meta)
        assertEquals("myapp", reader.moduleName)
        assertEquals("2.0.0", reader.version)
        assertEquals("x86_64-linux-gnu", reader.targetTriple)
        assertEquals(listOf("main", "add"), reader.exports)
        assertEquals("test", reader.metadata["author"])
    }

    @Test
    fun `round-trip types`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val types = buildTypes(listOf(
            Triple("add", "I32", listOf("a" to "I32", "b" to "I32")),
        ))
        val reader = ExecutableReader.read(meta, types)
        assertEquals(1, reader.types.size)
        assertEquals("add", reader.types[0].name)
        assertEquals("I32", reader.types[0].returnType)
        assertEquals(2, reader.types[0].params.size)
    }

    @Test
    fun `round-trip resources`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val res = buildResources(mapOf(
            "config.txt" to "hello".toByteArray(),
            "data.bin" to byteArrayOf(0xCA.toByte(), 0xFE.toByte()),
        ))
        val reader = ExecutableReader.read(meta, null, res)
        assertEquals(2, reader.resources.size)
        assertArrayEquals("hello".toByteArray(), reader.resource("config.txt"))
        assertArrayEquals(byteArrayOf(0xCA.toByte(), 0xFE.toByte()), reader.resource("data.bin"))
    }

    @Test
    fun `bad magic throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutableReader.read(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        }
    }

    @Test
    fun `null types and resources`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val reader = ExecutableReader.read(meta)
        assertTrue(reader.types.isEmpty())
        assertTrue(reader.resources.isEmpty())
    }

    @Test
    fun `empty types section`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val reader = ExecutableReader.read(meta, ByteArray(0))
        assertTrue(reader.types.isEmpty())
    }

    @Test
    fun `round-trip debug section`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val debug = KgenDebugSection.write(KgenDebugSection(
            sourceFiles = listOf("Main.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "main",
                    linkageName = "Main_main",
                    sourceFileIndex = 0,
                    startLine = 5,
                    endLine = 12,
                    nativeOffset = 0,
                    nativeSize = 48,
                    lineMappings = listOf(
                        KgenLineMapping(0, 5, 0),
                        KgenLineMapping(16, 8, 0),
                        KgenLineMapping(32, 12, 0),
                    ),
                ),
            ),
        ))
        val reader = ExecutableReader.read(meta, null, null, debug)
        assertNotNull(reader.debugInfo)
        assertEquals(1, reader.debugInfo!!.sourceFiles.size)
        assertEquals("Main.java", reader.debugInfo!!.sourceFiles[0])
        assertEquals(1, reader.debugInfo!!.methods.size)
        assertEquals("Main_main", reader.debugInfo!!.methods[0].linkageName)
        assertEquals(3, reader.debugInfo!!.methods[0].lineMappings.size)
    }

    @Test
    fun `null debug section`() {
        val meta = buildMeta("app", "1.0", "x86_64", emptyList(), emptyMap())
        val reader = ExecutableReader.read(meta)
        assertNull(reader.debugInfo)
    }

    // Helpers to build section bytes for testing the reader

    private fun buildMeta(
        name: String, version: String, triple: String,
        exports: List<String>, metadata: Map<String, String>
    ): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        buf.write("KGEN".toByteArray())
        writeU16(buf, 1) // format version
        writeU16(buf, 0) // flags
        writeString(buf, name)
        writeString(buf, version)
        writeString(buf, triple)
        writeU32(buf, exports.size)
        for (e in exports) writeString(buf, e)
        writeU32(buf, metadata.size)
        for ((k, v) in metadata) { writeString(buf, k); writeString(buf, v) }
        return buf.toByteArray()
    }

    private fun buildTypes(fns: List<Triple<String, String, List<Pair<String, String>>>>): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        writeU32(buf, fns.size)
        for ((name, ret, params) in fns) {
            writeString(buf, name)
            writeString(buf, ret)
            writeU16(buf, params.size)
            for ((pn, pt) in params) { writeString(buf, pn); writeString(buf, pt) }
        }
        return buf.toByteArray()
    }

    private fun buildResources(resources: Map<String, ByteArray>): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        writeU32(buf, resources.size)
        for ((name, data) in resources) {
            writeString(buf, name)
            writeU32(buf, data.size)
            buf.write(data)
        }
        return buf.toByteArray()
    }

    private fun writeU16(buf: java.io.ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
    }

    private fun writeU32(buf: java.io.ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
        buf.write((value shr 16) and 0xFF)
        buf.write((value shr 24) and 0xFF)
    }

    private fun writeString(buf: java.io.ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeU16(buf, bytes.size)
        buf.write(bytes)
    }
}

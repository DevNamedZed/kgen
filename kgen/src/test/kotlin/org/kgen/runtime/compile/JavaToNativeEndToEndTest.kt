package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.elf.ElfReader
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * End-to-end tests for Java-to-native compilation.
 * Builds classfiles from bytecode, compiles to native, and verifies the output.
 */
class JavaToNativeEndToEndTest {

    // --- Helper: build classfiles using ClassFileBuilder ---

    private fun buildStaticClass(name: String, block: ClassFileBuilder.() -> Unit): ByteArray {
        return ClassFileBuilder(name).apply(block).build().let { JvmClassWriter.write(it) }
    }

    // --- Simple function tests ---

    @Test
    fun `compile to native ELF with main`() {
        val classBytes = buildStaticClass("org/kgen/test/Arith") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elf = compiler.compile(listOf(classBytes))

        // Verify it's a valid ELF
        assertEquals(0x7F, elf[0].toInt() and 0xFF)
        assertEquals('E'.code, elf[1].toInt())
        assertEquals('L'.code, elf[2].toInt())
        assertEquals('F'.code, elf[3].toInt())
    }

    @Test
    fun `compile fibonacci to native object`() {
        val classBytes = buildStaticClass("org/kgen/test/Fib") {
            method("fib", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // if (n <= 1) return n
                code.iload(0)
                code.iconst(1)
                code.ifIcmple("base")
                // return fib(n-1) + fib(n-2)
                code.iload(0)
                code.iconst(1)
                code.isub()
                code.invokestatic("org/kgen/test/Fib", "fib", "(I)I")
                code.iload(0)
                code.iconst(2)
                code.isub()
                code.invokestatic("org/kgen/test/Fib", "fib", "(I)I")
                code.iadd()
                code.ireturn()
                code.label("base")
                code.iload(0)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "fib" })
    }

    @Test
    fun `compile multi-class project`() {
        val mathClass = buildStaticClass("org/kgen/test/MathOps") {
            method("square", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(0)
                code.imul()
                code.ireturn()
            }
        }

        val mainClass = buildStaticClass("org/kgen/test/Main") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(5)
                code.invokestatic("org/kgen/test/MathOps", "square", "(I)I")
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(mathClass, mainClass))
        assertTrue(obj.symbols.any { it.name == "square" })
        assertTrue(obj.symbols.any { it.name == "main" })
    }

    @Test
    fun `compile with export annotations`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Exported")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotations = cp.utf8("RuntimeVisibleAnnotations")
        val exportTypeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")
        val nameIdx = cp.utf8("add")
        val descIdx = cp.utf8("(II)I")

        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, bytecode, emptyList(), emptyList()))
        val annotationData = byteArrayOf(
            0x00, 0x01,
            ((exportTypeIdx shr 8) and 0xFF).toByte(), (exportTypeIdx and 0xFF).toByte(),
            0x00, 0x00,
        )
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, nameIdx, descIdx,
            listOf(codeAttr, AttributeInfo(rtAnnotations, annotationData)))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    // --- Static field tests ---

    @Test
    fun `compile class with static field to native`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Counter")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val countField = cp.fieldRef("org/kgen/test/Counter", "count", "I")
        // static int getCount() { return count; }
        val getCountName = cp.utf8("getCount")
        val getCountDesc = cp.utf8("()I")
        val getCountBytecode = byteArrayOf(
            0xB2.toByte(),
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0xAC.toByte(),
        )
        val getCountCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(1, 0, getCountBytecode, emptyList(), emptyList()))
        val getCountMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, getCountName, getCountDesc, listOf(getCountCode))

        // static void setCount(int v) { count = v; }
        val setCountName = cp.utf8("setCount")
        val setCountDesc = cp.utf8("(I)V")
        val setCountBytecode = byteArrayOf(
            0x1A,
            0xB3.toByte(),
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0xB1.toByte(),
        )
        val setCountCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(1, 1, setCountBytecode, emptyList(), emptyList()))
        val setCountMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, setCountName, setCountDesc, listOf(setCountCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(getCountMethod, setCountMethod), emptyList()))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "getCount" })
        assertTrue(obj.symbols.any { it.name == "setCount" })
    }

    // --- Instance method tests ---

    @Test
    fun `compile class with instance methods to native`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Point")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val xField = cp.fieldRef("org/kgen/test/Point", "x", "I")
        val yField = cp.fieldRef("org/kgen/test/Point", "y", "I")
        // int getX() { return this.x; }
        val getXName = cp.utf8("getX")
        val getXDesc = cp.utf8("()I")
        val getXBytecode = byteArrayOf(
            0x2A, 0xB4.toByte(),
            ((xField shr 8) and 0xFF).toByte(), (xField and 0xFF).toByte(),
            0xAC.toByte())
        val getXCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 1, getXBytecode, emptyList(), emptyList()))
        val getXMethod = MethodInfo(AccessFlags.PUBLIC, getXName, getXDesc, listOf(getXCode))

        // int sum() { return this.x + this.y; }
        val sumName = cp.utf8("sum")
        val sumDesc = cp.utf8("()I")
        val sumBytecode = byteArrayOf(
            0x2A, 0xB4.toByte(),
            ((xField shr 8) and 0xFF).toByte(), (xField and 0xFF).toByte(),
            0x2A, 0xB4.toByte(),
            ((yField shr 8) and 0xFF).toByte(), (yField and 0xFF).toByte(),
            0x60, 0xAC.toByte())
        val sumCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 1, sumBytecode, emptyList(), emptyList()))
        val sumMethod = MethodInfo(AccessFlags.PUBLIC, sumName, sumDesc, listOf(sumCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(getXMethod, sumMethod), emptyList()))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        // Instance methods are mangled: ClassName_methodName
        assertTrue(obj.symbols.any { it.name == "org_kgen_test_Point_getX" })
        assertTrue(obj.symbols.any { it.name == "org_kgen_test_Point_sum" })
    }

    // --- Shared library compilation ---

    @Test
    fun `compile to shared library`() {
        val classBytes = buildStaticClass("org/kgen/test/Lib") {
            method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
        }

        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val so = compiler.compile(listOf(classBytes))
        assertTrue(so.isNotEmpty())
        // Should be valid ELF
        assertEquals(0x7F, so[0].toInt() and 0xFF)
    }

    @Test
    fun `generate C header for shared library`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Math")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotations = cp.utf8("RuntimeVisibleAnnotations")
        val exportTypeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")

        val nameIdx = cp.utf8("add")
        val descIdx = cp.utf8("(II)I")
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, bytecode, emptyList(), emptyList()))
        val annotationData = byteArrayOf(
            0x00, 0x01,
            ((exportTypeIdx shr 8) and 0xFF).toByte(), (exportTypeIdx and 0xFF).toByte(),
            0x00, 0x00,
        )
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, nameIdx, descIdx,
            listOf(codeAttr, AttributeInfo(rtAnnotations, annotationData)))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))

        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val header = compiler.generateHeader(listOf(classBytes))
        assertTrue(header.contains("int32_t add("), "Header should contain 'add' function")
        assertTrue(header.contains("#ifndef"), "Header should have include guard")
    }

    // --- ExecutableBuilder with metadata ---

    @Test
    fun `package executable with kgen metadata`() {
        val classBytes = buildStaticClass("org/kgen/test/App") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(42)
                code.ireturn()
            }
        }

        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(classBytes)
        builder.setMainClass("org/kgen/test/App")
        builder.setModuleName("test-app")
        builder.setVersion("1.0.0")
        builder.setMetadata("author", "test")

        val exe = builder.build()
        assertTrue(exe.isNotEmpty())

        // Read back the metadata
        val elf = ElfReader.read(exe)
        val metaSection = elf.sectionByName(".kgen.meta")
        assertNotNull(metaSection, "Should have .kgen.meta section")

        val typesSection = elf.sectionByName(".kgen.types")
        val meta = ExecutableReader.read(
            metaSection!!.data,
            typesSection?.data,
        )
        assertEquals("test-app", meta.moduleName)
        assertEquals("1.0.0", meta.version)
        assertEquals("test", meta.metadata["author"])
    }

    @Test
    fun `package with embedded resources`() {
        val classBytes = buildStaticClass("org/kgen/test/App") {
            method("main", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
        }

        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(classBytes)
        builder.setMainClass("org/kgen/test/App")
        builder.addResource("config.txt", "key=value".toByteArray())
        builder.addResource("data.bin", byteArrayOf(1, 2, 3, 4))

        val exe = builder.build()
        val elf = ElfReader.read(exe)

        val metaSection = elf.sectionByName(".kgen.meta")!!
        val resourcesSection = elf.sectionByName(".kgen.resources")
        assertNotNull(resourcesSection)

        val meta = ExecutableReader.read(metaSection.data, null, resourcesSection!!.data)
        assertEquals("key=value", String(meta.resource("config.txt")!!))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), meta.resource("data.bin"))
    }

    // --- Cross-compilation targets ---

    @Test
    fun `compile to Windows PE`() {
        val classBytes = buildStaticClass("org/kgen/test/WinApp") {
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val pe = compiler.compile(listOf(classBytes))
        assertTrue(pe.isNotEmpty())
        // PE starts with MZ
        assertEquals('M'.code, pe[0].toInt() and 0xFF)
        assertEquals('Z'.code, pe[1].toInt() and 0xFF)
    }

    @Test
    fun `compile to ARM64 object`() {
        val classBytes = buildStaticClass("org/kgen/test/Arith") {
            method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "add" })
    }
}

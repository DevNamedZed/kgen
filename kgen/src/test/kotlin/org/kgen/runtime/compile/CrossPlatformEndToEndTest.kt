package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.elf.ElfMachine
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * End-to-end tests for cross-platform compilation from Java to multiple targets.
 * Demonstrates: single IR → x86 ELF + ARM64 ELF + PE + RISC-V from one build.
 */
class CrossPlatformEndToEndTest {

    private fun buildFibonacciClass(): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/Fibonacci").apply {
                method("fib", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iconst(1)
                    code.ifIcmple("base")
                    code.iload(0)
                    code.iconst(1)
                    code.isub()
                    code.invokestatic("org/kgen/test/Fibonacci", "fib", "(I)I")
                    code.iload(0)
                    code.iconst(2)
                    code.isub()
                    code.invokestatic("org/kgen/test/Fibonacci", "fib", "(I)I")
                    code.iadd()
                    code.ireturn()
                    code.label("base")
                    code.iload(0)
                    code.ireturn()
                }
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(10)
                    code.invokestatic("org/kgen/test/Fibonacci", "fib", "(I)I")
                    code.ireturn()
                }
            }.build()
        )
    }

    @Test
    fun `same fibonacci class compiles to all native targets`() {
        val classBytes = buildFibonacciClass()

        val targets = listOf(
            Target.x86_64() to "x86_64",
            Target.arm64() to "arm64",
            Target.riscv64() to "riscv64",
        )

        for ((target, name) in targets) {
            val compiler = NativeCompiler(target, OutputPlatform.LINUX)
            val obj = compiler.compileToObject(listOf(classBytes))
            assertTrue(obj.symbols.any { it.name == "fib" },
                "Expected 'fib' in $name object: ${obj.symbols.map { it.name }}")
            assertTrue(obj.symbols.any { it.name == "main" },
                "Expected 'main' in $name object")
        }
    }

    @Test
    fun `fibonacci compiles to both ELF and PE executables`() {
        val classBytes = buildFibonacciClass()

        // ELF
        val elfCompiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elf = elfCompiler.compile(listOf(classBytes))
        assertEquals(0x7F, elf[0].toInt() and 0xFF) // ELF magic
        assertTrue(elf.size > 200)

        // PE
        val peCompiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val pe = peCompiler.compile(listOf(classBytes))
        assertEquals('M'.code, pe[0].toInt() and 0xFF) // MZ magic
        assertTrue(pe.size > 200)
    }

    @Test
    fun `multi-class project compiles to all architectures`() {
        val mathClass = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/MathOps").apply {
                method("square", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(0)
                    code.imul()
                    code.ireturn()
                }
                method("cube", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(0)
                    code.imul()
                    code.iload(0)
                    code.imul()
                    code.ireturn()
                }
            }.build()
        )

        val mainClass = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/MathMain").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(5)
                    code.invokestatic("org/kgen/test/MathOps", "square", "(I)I")
                    code.ireturn()
                }
            }.build()
        )

        for ((target, name) in listOf(Target.x86_64() to "x86", Target.arm64() to "arm64", Target.riscv64() to "riscv")) {
            val compiler = NativeCompiler(target, OutputPlatform.LINUX)
            val obj = compiler.compileToObject(listOf(mathClass, mainClass))
            assertTrue(obj.symbols.any { it.name == "square" }, "Missing 'square' in $name")
            assertTrue(obj.symbols.any { it.name == "cube" }, "Missing 'cube' in $name")
            assertTrue(obj.symbols.any { it.name == "main" }, "Missing 'main' in $name")
        }
    }

    @Test
    fun `shared library from same class for ELF and PE`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/SharedLib").apply {
                method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(1)
                    code.iadd()
                    code.ireturn()
                }
            }.build()
        )

        val elfCompiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val so = elfCompiler.compile(listOf(classBytes))
        assertTrue(so.isNotEmpty())
        assertEquals(0x7F, so[0].toInt() and 0xFF)

        val peCompiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val dll = peCompiler.compile(listOf(classBytes))
        assertTrue(dll.isNotEmpty())
        assertEquals('M'.code, dll[0].toInt() and 0xFF)
    }

    @Test
    fun `ELF and PE executables are parseable`() {
        val classBytes = buildFibonacciClass()

        val elfBytes = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
            .compile(listOf(classBytes))
        assertTrue(elfBytes.size > 100, "ELF should have meaningful size")
        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF) // ELF magic
        val elf = ElfReader.read(elfBytes)
        // Linked ELF should be parseable
        assertNotNull(elf)

        val peBytes = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
            .compile(listOf(classBytes))
        assertTrue(peBytes.size > 100, "PE should have meaningful size")
        assertEquals('M'.code, peBytes[0].toInt() and 0xFF) // MZ magic
        val pe = PeReader.read(peBytes)
        assertNotNull(pe)
    }

    @Test
    fun `packaged executable with resources works across formats`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/CrossApp").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(0)
                    code.ireturn()
                }
            }.build()
        )

        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(classBytes)
        builder.setMainClass("org/kgen/test/CrossApp")
        builder.setModuleName("cross-app")
        builder.setVersion("2.0.0")
        builder.addResource("config.json", """{"key": "value"}""".toByteArray())

        val exe = builder.build()
        val elf = ElfReader.read(exe)

        val metaSection = elf.sectionByName(".kgen.meta")
        assertNotNull(metaSection)

        val resourcesSection = elf.sectionByName(".kgen.resources")
        assertNotNull(resourcesSection)

        val meta = ExecutableReader.read(
            metaSection!!.data, null, resourcesSection!!.data)
        assertEquals("cross-app", meta.moduleName)
        assertEquals("2.0.0", meta.version)
        val config = meta.resource("config.json")
        assertNotNull(config)
        assertEquals("""{"key": "value"}""", String(config!!))
    }

    @Test
    fun `C header generation from exported functions`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/ExportedLib")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotations = cp.utf8("RuntimeVisibleAnnotations")
        val exportTypeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")

        // add(int, int) -> int with @KgenExport
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

        // mul(int, int) -> int with @KgenExport
        val mulNameIdx = cp.utf8("mul")
        val mulDescIdx = cp.utf8("(II)I")
        val mulBytecode = byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte())
        val mulCodeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, mulBytecode, emptyList(), emptyList()))
        val mulAnnotation = byteArrayOf(
            0x00, 0x01,
            ((exportTypeIdx shr 8) and 0xFF).toByte(), (exportTypeIdx and 0xFF).toByte(),
            0x00, 0x00,
        )
        val mulMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, mulNameIdx, mulDescIdx,
            listOf(mulCodeAttr, AttributeInfo(rtAnnotations, mulAnnotation)))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method, mulMethod), emptyList()))

        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val header = compiler.generateHeader(listOf(classBytes))
        assertTrue(header.contains("int32_t add("), "Header should contain 'add': $header")
        assertTrue(header.contains("int32_t mul("), "Header should contain 'mul': $header")
        assertTrue(header.contains("#ifndef"), "Header should have include guard")
    }

    @Test
    fun `cross-compile for different target than host`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/CrossTarget").apply {
                method("compute", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iconst(2)
                    code.imul()
                    code.iconst(1)
                    code.iadd()
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // Verify symbols are present
        assertTrue(obj.symbols.any { it.name == "compute" },
            "Expected 'compute' symbol in ARM64 object: ${obj.symbols.map { it.name }}")

        // Write to ELF object and verify format
        val elfBytes = org.kgen.binary.elf.ElfObjectWriter(ElfMachine.AARCH64.code).write(obj)
        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF, "ELF magic byte 0")
        val elf = ElfReader.read(elfBytes)
        assertNotNull(elf)
        assertEquals(ElfMachine.AARCH64, elf.header.machine,
            "Cross-compiled ELF should target AARCH64")
    }

    @Test
    fun `cross-compile to RISC-V Linux from any host`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/RiscVTarget").apply {
                method("negate", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.ineg()
                    code.ireturn()
                }
                method("identity", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.riscv64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(classBytes))

        // Write to ELF object and verify format
        val elfBytes = org.kgen.binary.elf.ElfObjectWriter(ElfMachine.RISCV.code).write(obj)
        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF, "ELF magic byte 0")
        val elf = ElfReader.read(elfBytes)
        assertNotNull(elf)
        assertEquals(ElfMachine.RISCV, elf.header.machine,
            "Cross-compiled ELF should target RISC-V")
        assertTrue(obj.symbols.any { it.name == "negate" },
            "Expected 'negate' symbol in RISC-V object: ${obj.symbols.map { it.name }}")
        assertTrue(obj.symbols.any { it.name == "identity" },
            "Expected 'identity' symbol in RISC-V object: ${obj.symbols.map { it.name }}")
    }
}

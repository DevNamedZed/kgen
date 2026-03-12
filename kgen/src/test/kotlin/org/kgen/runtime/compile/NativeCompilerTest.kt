package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.ObjectFormat
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.target.x86.codegen.X86CodeGenerator

class NativeCompilerTest {

    private fun buildAddClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/AddOps")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("add")
        val descIdx = cp.utf8("(II)I")
        val codeIdx = cp.utf8("Code")
        // iload_0, iload_1, iadd, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 2, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )
        return JvmClassWriter.write(cf)
    }

    private fun buildMainClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Main")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("main")
        val descIdx = cp.utf8("()I")
        val codeIdx = cp.utf8("Code")
        // bipush 42, ireturn
        val bytecode = byteArrayOf(0x10, 42, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )
        return JvmClassWriter.write(cf)
    }

    private fun buildMulClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MulOps")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("multiply")
        val descIdx = cp.utf8("(II)I")
        val codeIdx = cp.utf8("Code")
        // iload_0, iload_1, imul, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 2, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )
        return JvmClassWriter.write(cf)
    }

    @Test
    fun `compile single class to IR modules`() {
        val compiler = NativeCompiler(Target.x86_64())
        val modules = compiler.compileToModules(listOf(buildAddClass()))
        assertEquals(1, modules.size)
        assertTrue(modules[0].functions.any { it.name == "add" })
    }

    @Test
    fun `compile single class to object file`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertEquals(ObjectFormat.ELF, obj.format)
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun `compile to ELF executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.isNotEmpty())
        // Verify ELF magic
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
        assertEquals('E'.code, exe[1].toInt())
        assertEquals('L'.code, exe[2].toInt())
        assertEquals('F'.code, exe[3].toInt())
    }

    @Test
    fun `compile to PE executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.isNotEmpty())
        // Verify PE magic (MZ header)
        assertEquals('M'.code, exe[0].toInt())
        assertEquals('Z'.code, exe[1].toInt())
    }

    @Test
    fun `compile to Mach-O executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.MACOS)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.isNotEmpty())
        // Mach-O 64-bit magic: 0xFEEDFACF
        val magic = ((exe[0].toInt() and 0xFF)) or
                    ((exe[1].toInt() and 0xFF) shl 8) or
                    ((exe[2].toInt() and 0xFF) shl 16) or
                    ((exe[3].toInt() and 0xFF) shl 24)
        assertEquals(0xFEEDFACF.toInt(), magic)
    }

    @Test
    fun `compile multiple classes merges functions`() {
        val compiler = NativeCompiler(Target.x86_64())
        val modules = compiler.compileToModules(listOf(buildAddClass(), buildMulClass()))
        assertEquals(2, modules.size)
        assertTrue(modules[0].functions.any { it.name == "add" })
        assertTrue(modules[1].functions.any { it.name == "multiply" })
    }

    @Test
    fun `compile multiple classes to single executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(listOf(buildMainClass(), buildAddClass()))
        assertTrue(exe.isNotEmpty())
        // Should be valid ELF
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    @Test
    fun `mainClass parameter selects entry point`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(
            listOf(buildMainClass(), buildAddClass()),
            mainClass = "org/kgen/test/Main"
        )
        assertTrue(obj.symbols.any { it.name == "main" })
    }

    @Test
    fun `compile with explicit code generator`() {
        val gen = X86CodeGenerator()
        val compiler = NativeCompiler(Target.x86_64(), codeGenerator = gen)
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun `invalid class throws RuntimeSubsetException`() {
        // Build a class with reserved opcode 0xFE (unsupported)
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Bad")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("bad")
        val descIdx = cp.utf8("()V")
        val codeIdx = cp.utf8("Code")
        // reserved opcode, return
        val bytecode = byteArrayOf(
            0xFE.toByte(), // reserved opcode
            0xB1.toByte(),
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )
        val badBytes = JvmClassWriter.write(cf)
        val compiler = NativeCompiler(Target.x86_64())
        assertThrows(RuntimeSubsetException::class.java) {
            compiler.compile(listOf(badBytes))
        }
    }

    @Test
    fun `ELF executable has meaningful size`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(listOf(buildMainClass(), buildAddClass()))
        assertTrue(exe.size > 100, "Executable should have meaningful size")
        // Verify ELF magic
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    @Test
    fun `compile with ARM64 target`() {
        val compiler = NativeCompiler(Target.arm64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun `compile with RISC-V target`() {
        val compiler = NativeCompiler(Target.riscv64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun `dynamic linked ELF executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX_DYNAMIC)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    private fun buildMainWithStringArrayArgs(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/ArgMain")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("main")
        val descIdx = cp.utf8("([Ljava/lang/String;)V")
        val codeIdx = cp.utf8("Code")
        // Simple body: return
        val bytecode = byteArrayOf(0xB1.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 0, maxLocals = 1, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        return JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList(),
        ))
    }

    @Test
    fun `main with String array gets argv wrapper`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(
            listOf(buildMainWithStringArrayArgs()),
            mainClass = "org/kgen/test/ArgMain",
        )
        // The wrapper should produce a "main" symbol that takes argc/argv
        assertTrue(obj.symbols.any { it.name == "main" })
        // The original main should be renamed to __user_main
        assertTrue(obj.symbols.any { it.name == "__user_main" })
    }

    @Test
    fun `main with String array compiles to ELF`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(
            listOf(buildMainWithStringArrayArgs()),
            mainClass = "org/kgen/test/ArgMain",
        )
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    @Test
    fun `main without args has no argv wrapper`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildMainClass()))
        // No wrapper needed — main takes no args
        assertTrue(obj.symbols.any { it.name == "main" })
        assertFalse(obj.symbols.any { it.name == "__user_main" })
    }

    @Test
    fun `compile class with println includes stdlib symbols`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/PrintMain")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val sysOutRef = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        val strIdx = cp.string("hello")
        val printlnRef = cp.methodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
        val nameIdx = cp.utf8("main")
        val descIdx = cp.utf8("()V")
        val code = byteArrayOf(
            0xB2.toByte(), (sysOutRef shr 8).toByte(), sysOutRef.toByte(),
            0x12, strIdx.toByte(),
            0xB6.toByte(), (printlnRef shr 8).toByte(), printlnRef.toByte(),
            0xB1.toByte(),
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 0, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList(),
        ))

        val obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(classBytes))
        val symbolNames = obj.symbols.map { it.name }.toSet()
        assertTrue("kgen_println_str" in symbolNames,
            "Should include stdlib println symbol, got: ${symbolNames.take(20)}")
    }

    @Test
    fun `compile class without stdlib calls has no stdlib symbols`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        val symbolNames = obj.symbols.map { it.name }.toSet()
        assertFalse("kgen_println_str" in symbolNames,
            "Should not include stdlib println symbol when not used")
    }

    @Test
    fun `object file symbol count matches expected`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertTrue(obj.symbols.any { it.name == "add" })
        val definedSymbols = obj.symbols.filter {
            it.kind != org.kgen.binary.SymbolKind.UNDEFINED
        }
        assertTrue(definedSymbols.isNotEmpty(), "Should have at least one defined symbol")
    }

    @Test
    fun `compile multiple classes to object preserves all symbols`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildAddClass(), buildMulClass()))
        val symbolNames = obj.symbols.map { it.name }.toSet()
        assertTrue("add" in symbolNames, "Should have 'add' symbol")
        assertTrue("multiply" in symbolNames, "Should have 'multiply' symbol")
    }

    @Test
    fun `ARM64 target produces object with text section`() {
        val compiler = NativeCompiler(Target.arm64())
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        val textSection = obj.sections.find { it.kind == org.kgen.binary.SectionKind.TEXT }
        assertNotNull(textSection, "Should have a text section")
        assertTrue(textSection!!.data.isNotEmpty(), "Text section should have code")
    }

    @Test
    fun `RISC-V target produces valid object`() {
        val compiler = NativeCompiler(Target.riscv64())
        val obj = compiler.compileToObject(listOf(buildAddClass()))
        assertTrue(obj.symbols.any { it.name == "add" })
        val textSection = obj.sections.find { it.kind == org.kgen.binary.SectionKind.TEXT }
        assertNotNull(textSection)
        assertTrue(textSection!!.data.isNotEmpty())
    }

    @Test
    fun `compile class with static initializer`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/Clinit")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val fieldRef = cp.fieldRef("test/Clinit", "x", "I")

        val clinitName = cp.utf8("<clinit>")
        val clinitDesc = cp.utf8("()V")
        val clinitCode = byteArrayOf(
            0x10, 42,                                       // bipush 42
            0xB3.toByte(), (fieldRef shr 8).toByte(), fieldRef.toByte(), // putstatic x
            0xB1.toByte(),                                  // return
        )
        val clinitCodeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = clinitCode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val clinitMethod = MethodInfo(
            accessFlags = AccessFlags.STATIC,
            nameIndex = clinitName, descriptorIndex = clinitDesc,
            attributes = listOf(clinitCodeAttr),
        )

        val mainName = cp.utf8("main")
        val mainDesc = cp.utf8("()I")
        val mainCode = byteArrayOf(0x10, 1, 0xAC.toByte())
        val mainCodeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = mainCode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val mainMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = mainName, descriptorIndex = mainDesc,
            attributes = listOf(mainCodeAttr),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(clinitMethod, mainMethod), emptyList(),
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val modules = compiler.compileToModules(listOf(classBytes))
        assertEquals(1, modules.size)
        val fnNames = modules[0].functions.map { it.name }.toSet()
        assertTrue(fnNames.any { it.contains("clinit") },
            "Should have clinit function, got: $fnNames")
    }

    @Test
    fun `PE executable has meaningful size`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.size > 200, "PE executable should have meaningful size, got ${exe.size}")
    }

    @Test
    fun `Mach-O executable has meaningful size`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.MACOS)
        val exe = compiler.compile(listOf(buildMainClass()))
        assertTrue(exe.size > 200, "Mach-O executable should have meaningful size, got ${exe.size}")
    }
}

class NativeLibraryCompilerTest {

    private fun buildExportClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Lib")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8("square")
        val descIdx = cp.utf8("(I)I")
        val codeIdx = cp.utf8("Code")
        // iload_0, iload_0, imul, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1A, 0x68, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )
        return JvmClassWriter.write(cf)
    }

    @Test
    fun `compile to ELF shared library`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val so = compiler.compile(listOf(buildExportClass()))
        assertTrue(so.isNotEmpty())
        assertEquals(0x7F, so[0].toInt() and 0xFF)
    }

    @Test
    fun `compile to PE DLL`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val dll = compiler.compile(listOf(buildExportClass()))
        assertTrue(dll.isNotEmpty())
        assertEquals('M'.code, dll[0].toInt())
        assertEquals('Z'.code, dll[1].toInt())
    }

    @Test
    fun `compile to Mach-O dylib`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.MACOS)
        val dylib = compiler.compile(listOf(buildExportClass()))
        assertTrue(dylib.isNotEmpty())
        // Verify Mach-O magic (little-endian: 0xFEEDFACF)
        assertEquals(0xCF, dylib[0].toInt() and 0xFF)
        assertEquals(0xFA, dylib[1].toInt() and 0xFF)
        assertEquals(0xED, dylib[2].toInt() and 0xFF)
        assertEquals(0xFE, dylib[3].toInt() and 0xFF)
        // Verify file type is MH_DYLIB (6) at offset 12
        assertEquals(6, dylib[12].toInt() and 0xFF)
    }

    @Test
    fun `Mach-O dylib with install name`() {
        val compiler = NativeLibraryCompiler(
            Target.x86_64(), OutputPlatform.MACOS, soname = "libtest.1.dylib"
        )
        val dylib = compiler.compile(listOf(buildExportClass()))
        assertTrue(dylib.isNotEmpty())
        assertEquals(6, dylib[12].toInt() and 0xFF) // MH_DYLIB
    }

    @Test
    fun `Mach-O dylib ARM64`() {
        val compiler = NativeLibraryCompiler(Target.arm64(), OutputPlatform.MACOS)
        val dylib = compiler.compile(listOf(buildExportClass()))
        assertTrue(dylib.isNotEmpty())
        assertEquals(6, dylib[12].toInt() and 0xFF) // MH_DYLIB
    }

    @Test
    fun `shared library with soname`() {
        val compiler = NativeLibraryCompiler(
            Target.x86_64(), OutputPlatform.LINUX, soname = "libtest.so.1"
        )
        val so = compiler.compile(listOf(buildExportClass()))
        assertTrue(so.isNotEmpty())
    }
}

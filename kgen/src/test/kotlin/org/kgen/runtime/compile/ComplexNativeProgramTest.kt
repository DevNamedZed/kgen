package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Tests for complex native programs: globals, string constants, multi-function,
 * static initializers, and cross-class calls.
 */
class ComplexNativeProgramTest {

    @Test
    fun `program with static fields and initializer`() {
        // Counter class with static field and clinit
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Counter")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val countField = cp.fieldRef("org/kgen/test/Counter", "count", "I")

        // static { count = 0; } → <clinit>
        val clinitName = cp.utf8("<clinit>")
        val clinitDesc = cp.utf8("()V")
        val clinitBytecode = byteArrayOf(
            0x03,               // iconst_0
            0xB3.toByte(),      // putstatic count
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0xB1.toByte(),      // return
        )
        val clinitCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(1, 0, clinitBytecode, emptyList(), emptyList()))
        val clinitMethod = MethodInfo(AccessFlags.STATIC, clinitName, clinitDesc, listOf(clinitCode))

        // static int getCount() { return count; }
        val getCountName = cp.utf8("getCount")
        val getCountDesc = cp.utf8("()I")
        val getCountBytecode = byteArrayOf(
            0xB2.toByte(),
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0xAC.toByte(),
        )
        val getCountCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(1, 0, getCountBytecode, emptyList(), emptyList()))
        val getCountMethod = MethodInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, getCountName, getCountDesc, listOf(getCountCode))

        // static void increment() { count = count + 1; }
        val incName = cp.utf8("increment")
        val incDesc = cp.utf8("()V")
        val incBytecode = byteArrayOf(
            0xB2.toByte(),      // getstatic count
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0x04,               // iconst_1
            0x60,               // iadd
            0xB3.toByte(),      // putstatic count
            ((countField shr 8) and 0xFF).toByte(), (countField and 0xFF).toByte(),
            0xB1.toByte(),      // return
        )
        val incCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 0, incBytecode, emptyList(), emptyList()))
        val incMethod = MethodInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, incName, incDesc, listOf(incCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(clinitMethod, getCountMethod, incMethod), emptyList()))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "getCount" })
        assertTrue(obj.symbols.any { it.name == "increment" })
    }

    @Test
    fun `program with string constants`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/StringConst").apply {
                method("getMessage", "()J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    // Returns pointer to string constant "hello"
                    code.ldc("hello world")
                    code.areturn()
                }
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(0)
                    code.ireturn()
                }
            }.build()
        )

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        // Should have a global for the string constant
        assertTrue(module.globals.any { it.name.startsWith(".str.") },
            "Expected string constant global: ${module.globals.map { it.name }}")
    }

    @Test
    fun `multi-class program with cross-class calls`() {
        val mathClass = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/MathLib").apply {
                method("square", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iload(0)
                    code.imul()
                    code.ireturn()
                }
                method("twice", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iload(0)
                    code.iconst(2)
                    code.imul()
                    code.ireturn()
                }
            }.build()
        )

        val appClass = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/App").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(5)
                    code.invokestatic("org/kgen/test/MathLib", "square", "(I)I")
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val obj = compiler.compileToObject(listOf(mathClass, appClass))
        assertTrue(obj.symbols.any { it.name == "square" })
        assertTrue(obj.symbols.any { it.name == "twice" })
        assertTrue(obj.symbols.any { it.name == "main" })

        // Should also compile to linked ELF
        val elf = compiler.compile(listOf(mathClass, appClass))
        assertTrue(elf.size > 100)
        assertEquals(0x7F, elf[0].toInt() and 0xFF)
    }

    @Test
    fun `program with instance methods and field access`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/Point").apply {
                method("create", "(II)Lorg/kgen/test/Point;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.new_("org/kgen/test/Point")
                    code.dup()
                    code.invokespecial("java/lang/Object", "<init>", "()V")
                    code.areturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "create" })
    }

    @Test
    fun `program with pointer-based array sum using intrinsics`() {
        // int sum(long ptr, int len) — sum array via loadInt intrinsic
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/ArrayOps").apply {
                method("sum", "(JI)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    // int sum(long ptr, int len)
                    code.iconst(0)
                    code.istore(3) // s = 0
                    code.iconst(0)
                    code.istore(4) // i = 0
                    code.label("loop")
                    code.iload(4) // i
                    code.iload(2) // len
                    code.ifIcmpge("end")
                    code.iload(3) // s
                    code.lload(0) // ptr
                    code.iload(4) // i
                    code.iconst(4)
                    code.imul()
                    code.invokestatic("org/kgen/unmanaged/Kgen", "offset", "(JI)J")
                    code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                    code.iadd()
                    code.istore(3) // s += loadInt(ptr + i*4)
                    code.iinc(4, 1)
                    code.goto("loop")
                    code.label("end")
                    code.iload(3)
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "sum" })

        // Verify IR has loop structure
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "sum" }
        assertTrue(fn.blocks.size > 1, "Expected multiple blocks for loop")
    }

    @Test
    fun `program with long arithmetic`() {
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/LongMath").apply {
                method("factorial", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    // long factorial(int n) { long r = 1; for (int i = 2; i <= n; i++) r *= i; return r; }
                    code.lconst(1)
                    code.lstore(1) // r = 1L
                    code.iconst(2)
                    code.istore(3) // i = 2
                    code.label("loop")
                    code.iload(3) // i
                    code.iload(0) // n
                    code.ifIcmpgt("end") // if i > n goto end
                    code.lload(1) // r
                    code.iload(3) // i
                    code.i2l()
                    code.lmul()
                    code.lstore(1) // r = r * i
                    code.iinc(3, 1) // i++
                    code.goto("loop")
                    code.label("end")
                    code.lload(1)
                    code.lreturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "factorial" })

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "factorial" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Mul }, "Expected multiplication")
    }

    @Test
    fun `shared library with multiple exports and header`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MathExport")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotations = cp.utf8("RuntimeVisibleAnnotations")
        val exportTypeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")

        fun makeExportedMethod(name: String, desc: String, bytecode: ByteArray): MethodInfo {
            val nameIdx = cp.utf8(name)
            val descIdx = cp.utf8(desc)
            val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, bytecode, emptyList(), emptyList()))
            val annotation = byteArrayOf(
                0x00, 0x01,
                ((exportTypeIdx shr 8) and 0xFF).toByte(), (exportTypeIdx and 0xFF).toByte(),
                0x00, 0x00,
            )
            return MethodInfo(
                AccessFlags.PUBLIC or AccessFlags.STATIC, nameIdx, descIdx,
                listOf(codeAttr, AttributeInfo(rtAnnotations, annotation)))
        }

        val addMethod = makeExportedMethod("add", "(II)I", byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()))
        val subMethod = makeExportedMethod("sub", "(II)I", byteArrayOf(0x1A, 0x1B, 0x64, 0xAC.toByte()))
        val mulMethod = makeExportedMethod("mul", "(II)I", byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte()))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(addMethod, subMethod, mulMethod), emptyList()))

        // Compile to shared library
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val so = compiler.compile(listOf(classBytes))
        assertTrue(so.isNotEmpty())

        // Generate C header
        val header = compiler.generateHeader(listOf(classBytes))
        assertTrue(header.contains("add"))
        assertTrue(header.contains("sub"))
        assertTrue(header.contains("mul"))
        assertTrue(header.contains("#ifdef __cplusplus") || header.contains("#ifndef"),
            "Header should have guards: $header")
    }

    @Test
    fun `executable with embedded resources round-trips`() {
        val mainClass = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/ResourceApp").apply {
                method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    code.iconst(0)
                    code.ireturn()
                }
            }.build()
        )

        val builder = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
        builder.addClassFile(mainClass)
        builder.setMainClass("org/kgen/test/ResourceApp")
        builder.setModuleName("resource-app")
        builder.setVersion("3.1.4")
        builder.setMetadata("author", "kgen")
        builder.setMetadata("license", "MIT")
        builder.addResource("data.txt", "hello world".toByteArray())
        builder.addResource("config.bin", byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))

        val exe = builder.build()
        assertTrue(exe.isNotEmpty())

        // Read back
        val elf = org.kgen.binary.elf.ElfReader.read(exe)
        val metaSection = elf.sectionByName(".kgen.meta")!!
        val resourcesSection = elf.sectionByName(".kgen.resources")!!

        val meta = ExecutableReader.read(metaSection.data, null, resourcesSection.data)
        assertEquals("resource-app", meta.moduleName)
        assertEquals("3.1.4", meta.version)
        assertEquals("kgen", meta.metadata["author"])
        assertEquals("MIT", meta.metadata["license"])
        assertEquals("hello world", String(meta.resource("data.txt")!!))
        assertArrayEquals(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()),
            meta.resource("config.bin"))
    }
}

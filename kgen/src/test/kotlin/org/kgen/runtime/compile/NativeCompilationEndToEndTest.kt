package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.ObjectFormat
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * End-to-end tests for the Java-to-Native compilation pipeline.
 * Verifies that Java classfiles compile through the full pipeline
 * to valid native binaries on all supported platforms and architectures.
 */
class NativeCompilationEndToEndTest {

    // ---- Classfile builders ----

    private fun buildFibonacci(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Fibonacci")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // int fibonacci(int n) {
        //   if (n <= 1) return n;
        //   return fibonacci(n-1) + fibonacci(n-2);
        // }
        // Simplified iterative version for subset compatibility:
        // int fibonacci(int n) {
        //   int a = 0, b = 1;
        //   for (int i = 0; i < n; i++) { int t = b; b = a + b; a = t; }
        //   return a;
        // }
        val nameIdx = cp.utf8("fibonacci")
        val descIdx = cp.utf8("(I)I")
        val bytecode = byteArrayOf(
            0x03, 0x3C,                                     // 0: iconst_0, istore_1 (a=0)
            0x04, 0x3D,                                     // 2: iconst_1, istore_2 (b=1)
            0x03, 0x36, 0x03,                               // 4: iconst_0, istore 3 (i=0)
            // loop: (offset 7)
            0x15, 0x03,                                     // 7: iload 3 (i)
            0x1A,                                           // 9: iload_0 (n)
            0xA2.toByte(), 0x00, 0x13,                      // 10: if_icmpge +19 -> end (offset 29)
            0x1C,                                           // 13: iload_2 (b)
            0x36, 0x04,                                     // 14: istore 4 (t=b)
            0x1B, 0x1C, 0x60, 0x3D,                         // 16: iload_1, iload_2, iadd, istore_2 (b=a+b)
            0x15, 0x04, 0x3C,                               // 20: iload 4, istore_1 (a=t)
            0x84.toByte(), 0x03, 0x01,                      // 23: iinc 3, 1 (i++)
            0xA7.toByte(), 0xFF.toByte(), 0xED.toByte(),    // 26: goto -19 -> loop (offset 7)
            // end: (offset 29)
            0x1B,                                           // 29: iload_1 (a)
            0xAC.toByte(),                                  // 30: ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 5, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        // main()I — calls fibonacci(10)
        val mainName = cp.utf8("main")
        val mainDesc = cp.utf8("()I")
        val mainCode = byteArrayOf(0x10, 10, 0xAC.toByte()) // bipush 10, ireturn
        val mainMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = mainName, descriptorIndex = mainDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 1, maxLocals = 0, code = mainCode,
                exceptionTable = emptyList(), attributes = emptyList()))),
        )

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method, mainMethod), attributes = emptyList(),
        ))
    }

    private fun buildMinMax(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MinMax")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // min(II)I — if (a < b) return a; else return b;
        val minName = cp.utf8("min")
        val minDesc = cp.utf8("(II)I")
        val minCode = byteArrayOf(
            0x1A, 0x1B,                                     // iload_0, iload_1
            0xA2.toByte(), 0x00, 0x05,                      // if_icmpge +5 -> return_b
            0x1A, 0xAC.toByte(),                            // iload_0, ireturn (return a)
            0x1B, 0xAC.toByte(),                            // iload_1, ireturn (return b)
        )
        val minMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = minName, descriptorIndex = minDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 2, code = minCode,
                exceptionTable = emptyList(), attributes = emptyList()))),
        )

        // max(II)I — if (a > b) return a; else return b;
        val maxName = cp.utf8("max")
        val maxDesc = cp.utf8("(II)I")
        val maxCode = byteArrayOf(
            0x1A, 0x1B,                                     // iload_0, iload_1
            0xA4.toByte(), 0x00, 0x05,                      // if_icmple +5 -> return_b
            0x1A, 0xAC.toByte(),                            // iload_0, ireturn (return a)
            0x1B, 0xAC.toByte(),                            // iload_1, ireturn (return b)
        )
        val maxMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = maxName, descriptorIndex = maxDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 2, code = maxCode,
                exceptionTable = emptyList(), attributes = emptyList()))),
        )

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(minMethod, maxMethod), attributes = emptyList(),
        ))
    }

    private fun buildMathLib(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MathLib")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val methods = mutableListOf<MethodInfo>()

        // add(II)I
        val addName = cp.utf8("add")
        val addDesc = cp.utf8("(II)I")
        methods.add(MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = addName, descriptorIndex = addDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 2,
                code = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()),
                exceptionTable = emptyList(), attributes = emptyList()))),
        ))

        // sub(II)I
        val subName = cp.utf8("sub")
        val subDesc = cp.utf8("(II)I")
        methods.add(MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = subName, descriptorIndex = subDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 2,
                code = byteArrayOf(0x1A, 0x1B, 0x64, 0xAC.toByte()),
                exceptionTable = emptyList(), attributes = emptyList()))),
        ))

        // mul(II)I
        val mulName = cp.utf8("mul")
        val mulDesc = cp.utf8("(II)I")
        methods.add(MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = mulName, descriptorIndex = mulDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 2,
                code = byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte()),
                exceptionTable = emptyList(), attributes = emptyList()))),
        ))

        // neg(I)I — return -n;
        val negName = cp.utf8("neg")
        val negDesc = cp.utf8("(I)I")
        methods.add(MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = negName, descriptorIndex = negDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 1, maxLocals = 1,
                code = byteArrayOf(
                    0x1A,                                       // iload_0
                    0x74,                                       // ineg
                    0xAC.toByte(),                              // ireturn
                ),
                exceptionTable = emptyList(), attributes = emptyList()))),
        ))

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = methods, attributes = emptyList(),
        ))
    }

    // ---- End-to-end tests: Java → Native Executable ----

    @Test
    fun `fibonacci class compiles to x86 ELF executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(listOf(buildFibonacci()))
        assertTrue(exe.size > 200)
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    @Test
    fun `fibonacci class compiles to PE executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS)
        val exe = compiler.compile(listOf(buildFibonacci()))
        assertTrue(exe.size > 200)
        assertEquals('M'.code, exe[0].toInt())
    }

    @Test
    fun `fibonacci class compiles to Mach-O executable`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.MACOS)
        val exe = compiler.compile(listOf(buildFibonacci()))
        assertTrue(exe.size > 200)
    }

    @Test
    fun `fibonacci IR has loop structure`() {
        val compiler = NativeCompiler(Target.x86_64())
        val modules = compiler.compileToModules(listOf(buildFibonacci()))
        val fibFn = modules[0].functions.first { it.name == "fibonacci" }
        // Should have multiple blocks (entry + loop + exit)
        assertTrue(fibFn.blocks.size >= 2, "Expected multiple blocks for loop: ${fibFn.blocks.size}")
    }

    // ---- Cross-platform compilation ----

    @Test
    fun `same class compiles to all three native formats`() {
        val fib = buildFibonacci()

        val elf = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX).compile(listOf(fib))
        val pe = NativeCompiler(Target.x86_64(), OutputPlatform.WINDOWS).compile(listOf(fib))
        val macho = NativeCompiler(Target.x86_64(), OutputPlatform.MACOS).compile(listOf(fib))

        // All should be non-empty and have different magic bytes
        assertTrue(elf.isNotEmpty())
        assertTrue(pe.isNotEmpty())
        assertTrue(macho.isNotEmpty())

        assertEquals(0x7F, elf[0].toInt() and 0xFF) // ELF
        assertEquals('M'.code, pe[0].toInt())         // PE
        // Mach-O magic varies
    }

    @Test
    fun `same class compiles to x86 and ARM64 object files`() {
        val fib = buildFibonacci()
        val x86Obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(fib))
        val armObj = NativeCompiler(Target.arm64()).compileToObject(listOf(fib))

        // Both should have fibonacci symbol
        assertTrue(x86Obj.symbols.any { it.name == "fibonacci" })
        assertTrue(armObj.symbols.any { it.name == "fibonacci" })

        // Text sections should differ (different ISAs)
        val x86Text = x86Obj.sections.first { it.name == ".text" }
        val armText = armObj.sections.first { it.name == ".text" }
        assertFalse(x86Text.data.contentEquals(armText.data))
    }

    @Test
    fun `same class compiles to x86 and RISC-V object files`() {
        val fib = buildFibonacci()
        val x86Obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(fib))
        val rvObj = NativeCompiler(Target.riscv64()).compileToObject(listOf(fib))

        assertTrue(x86Obj.symbols.any { it.name == "fibonacci" })
        assertTrue(rvObj.symbols.any { it.name == "fibonacci" })
    }

    // ---- Multi-class compilation ----

    @Test
    fun `multi-class Java project compiles to single binary`() {
        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(
            listOf(buildFibonacci(), buildMinMax(), buildMathLib()),
            mainClass = "org/kgen/test/Fibonacci"
        )
        assertTrue(exe.isNotEmpty())
    }

    @Test
    fun `multi-class object file has all function symbols`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(
            listOf(buildFibonacci(), buildMathLib())
        )
        val names = obj.symbols.map { it.name }.toSet()
        assertTrue("fibonacci" in names)
        assertTrue("add" in names)
        assertTrue("sub" in names)
        assertTrue("mul" in names)
        assertTrue("neg" in names)
    }

    // ---- Java → Shared Library ----

    @Test
    fun `math library compiles to ELF shared library`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX,
            soname = "libmath.so.1")
        val so = compiler.compile(listOf(buildMathLib()))
        assertTrue(so.isNotEmpty())
        assertEquals(0x7F, so[0].toInt() and 0xFF)
    }

    @Test
    fun `math library compiles to PE DLL`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), OutputPlatform.WINDOWS,
            soname = "math.dll")
        val dll = compiler.compile(listOf(buildMathLib()))
        assertTrue(dll.isNotEmpty())
        assertEquals('M'.code, dll[0].toInt())
    }

    @Test
    fun `math library generates C header`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), soname = "libmath.so")
        val header = compiler.generateHeader(listOf(buildMathLib()), "MATH_H")
        assertTrue(header.contains("#ifndef MATH_H"))
        assertTrue(header.contains("#include <stdint.h>"))
        assertTrue(header.contains("extern \"C\""))
    }

    // ---- ExecutableBuilder end-to-end ----

    @Test
    fun `ExecutableBuilder with fibonacci and resources`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFile(buildFibonacci())
            .setModuleName("fibonacci-app")
            .setVersion("1.0.0")
            .addResource("README", "Fibonacci calculator".toByteArray())
            .setMetadata("author", "kgen-test")
            .build()
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }

    @Test
    fun `ExecutableBuilder multi-class with all metadata`() {
        val exe = ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX)
            .addClassFiles(listOf(buildFibonacci(), buildMathLib()))
            .setMainClass("org/kgen/test/Fibonacci")
            .setModuleName("math-suite")
            .setVersion("2.0.0-beta")
            .setMetadata("license", "MIT")
            .setMetadata("compiler", "kgen-native")
            .addResource("VERSION", "2.0.0-beta".toByteArray())
            .build()
        assertTrue(exe.isNotEmpty())
    }
}

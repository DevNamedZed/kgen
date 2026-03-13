package org.kgen.runtime.compile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Tests for @KgenImport: declaring external C functions callable from @KgenNative code.
 *
 * Covers: declaration, custom names, type mapping, calling imported functions,
 * combining imports with exports, multiple imports, vararg-style patterns,
 * and cross-architecture compilation.
 */
class KgenImportTest {

    private fun compile(classBytes: ByteArray, target: Target = Target.x86_64()): Module {
        return RuntimeCompiler(target).compile(classBytes)
    }

    private fun buildAnnotationBytes(cp: ConstantPoolBuilder, className: String): ByteArray {
        val typeIdx = cp.utf8("L${className};")
        return byteArrayOf(
            0x00, 0x01,
            ((typeIdx shr 8) and 0xFF).toByte(),
            (typeIdx and 0xFF).toByte(),
            0x00, 0x00,
        )
    }

    private fun buildAnnotationWithStringValue(
        cp: ConstantPoolBuilder, className: String, elementName: String, value: String,
    ): ByteArray {
        val typeIdx = cp.utf8("L${className};")
        val nameIdx = cp.utf8(elementName)
        val valueIdx = cp.utf8(value)
        return byteArrayOf(
            0x00, 0x01,
            ((typeIdx shr 8) and 0xFF).toByte(),
            (typeIdx and 0xFF).toByte(),
            0x00, 0x01,
            ((nameIdx shr 8) and 0xFF).toByte(),
            (nameIdx and 0xFF).toByte(),
            's'.code.toByte(),
            ((valueIdx shr 8) and 0xFF).toByte(),
            (valueIdx and 0xFF).toByte(),
        )
    }

    /** Build a class with import declarations and method bodies. */
    private fun buildClass(
        className: String,
        block: ClassBuilder.() -> Unit,
    ): ByteArray {
        val b = ClassBuilder(className)
        b.block()
        return b.build()
    }

    class ClassBuilder(private val className: String) {
        private val cp = ConstantPoolBuilder()
        private val methods = mutableListOf<MethodInfo>()
        private val thisClassIdx by lazy { cp.classEntry(className) }
        private val superClassIdx by lazy { cp.classEntry("java/lang/Object") }
        private val codeNameIdx by lazy { cp.utf8("Code") }
        private val rtAnnotationsName by lazy { cp.utf8("RuntimeVisibleAnnotations") }

        fun import(name: String, desc: String, nativeName: String? = null) {
            val nameIdx = cp.utf8(name)
            val descIdx = cp.utf8(desc)
            val annData = if (nativeName != null) {
                val typeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenImport;")
                val elemIdx = cp.utf8("value")
                val valIdx = cp.utf8(nativeName)
                byteArrayOf(
                    0x00, 0x01,
                    ((typeIdx shr 8) and 0xFF).toByte(), (typeIdx and 0xFF).toByte(),
                    0x00, 0x01,
                    ((elemIdx shr 8) and 0xFF).toByte(), (elemIdx and 0xFF).toByte(),
                    's'.code.toByte(),
                    ((valIdx shr 8) and 0xFF).toByte(), (valIdx and 0xFF).toByte(),
                )
            } else {
                val typeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenImport;")
                byteArrayOf(
                    0x00, 0x01,
                    ((typeIdx shr 8) and 0xFF).toByte(), (typeIdx and 0xFF).toByte(),
                    0x00, 0x00,
                )
            }
            methods.add(MethodInfo(
                accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.NATIVE,
                nameIndex = nameIdx, descriptorIndex = descIdx,
                attributes = listOf(AttributeInfo(rtAnnotationsName, annData)),
            ))
        }

        fun method(
            name: String, desc: String,
            flags: Int = AccessFlags.PUBLIC or AccessFlags.STATIC,
            export: Boolean = false, exportName: String? = null,
            body: (CodeEmitter) -> Unit,
        ) {
            val nameIdx = cp.utf8(name)
            val descIdx = cp.utf8(desc)
            val emitter = CodeEmitter(cp, className)
            body(emitter)
            val codeBytes = emitter.toByteArray()
            val codeData = buildCodeAttr(codeBytes, emitter.maxStack, emitter.maxLocals)
            val attrs = mutableListOf(AttributeInfo(codeNameIdx, codeData))
            if (export) {
                val annData = if (exportName != null) {
                    val typeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")
                    val elemIdx = cp.utf8("value")
                    val valIdx = cp.utf8(exportName)
                    byteArrayOf(
                        0x00, 0x01,
                        ((typeIdx shr 8) and 0xFF).toByte(), (typeIdx and 0xFF).toByte(),
                        0x00, 0x01,
                        ((elemIdx shr 8) and 0xFF).toByte(), (elemIdx and 0xFF).toByte(),
                        's'.code.toByte(),
                        ((valIdx shr 8) and 0xFF).toByte(), (valIdx and 0xFF).toByte(),
                    )
                } else {
                    val typeIdx = cp.utf8("Lorg/kgen/unmanaged/KgenExport;")
                    byteArrayOf(
                        0x00, 0x01,
                        ((typeIdx shr 8) and 0xFF).toByte(), (typeIdx and 0xFF).toByte(),
                        0x00, 0x00,
                    )
                }
                attrs.add(AttributeInfo(rtAnnotationsName, annData))
            }
            methods.add(MethodInfo(flags, nameIdx, descIdx, attrs))
        }

        private fun buildCodeAttr(code: ByteArray, maxStack: Int, maxLocals: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val dos = java.io.DataOutputStream(out)
            dos.writeShort(maxStack)
            dos.writeShort(maxLocals)
            dos.writeInt(code.size)
            dos.write(code)
            dos.writeShort(0)
            dos.writeShort(0)
            dos.flush()
            return out.toByteArray()
        }

        fun build(): ByteArray {
            thisClassIdx; superClassIdx
            return JvmClassWriter.write(ClassFile(
                minorVersion = 0, majorVersion = 50,
                constantPool = cp.build(),
                accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
                thisClass = thisClassIdx, superClass = superClassIdx,
                interfaces = emptyList(), fields = emptyList(),
                methods = methods, attributes = emptyList(),
            ))
        }
    }

    /** Simple bytecode emitter using raw bytes + method refs from CP. */
    class CodeEmitter(private val cp: ConstantPoolBuilder, private val ownerClass: String) {
        val bytes = java.io.ByteArrayOutputStream()
        var maxStack = 4
        var maxLocals = 4

        fun lload(idx: Int) { bytes.write(0x16); bytes.write(idx) }  // lload
        fun lload0() { bytes.write(0x1E) }
        fun lload2() { bytes.write(0x20) }
        fun iload(idx: Int) { bytes.write(0x15); bytes.write(idx) }
        fun iload0() { bytes.write(0x1A) }
        fun iload1() { bytes.write(0x1B) }
        fun iconst(v: Int) { bytes.write(0x03 + v) } // iconst_0..iconst_5
        fun pop() { bytes.write(0x57) }
        fun returnVoid() { bytes.write(0xB1) }
        fun ireturn() { bytes.write(0xAC) }
        fun lreturn() { bytes.write(0xAD) }
        fun iadd() { bytes.write(0x60) }
        fun i2l() { bytes.write(0x85) }

        fun invokestatic(owner: String, name: String, desc: String) {
            val ref = cp.methodRef(owner, name, desc)
            bytes.write(0xB8)
            bytes.write((ref shr 8) and 0xFF)
            bytes.write(ref and 0xFF)
        }

        fun invokestaticSelf(name: String, desc: String) {
            invokestatic(ownerClass, name, desc)
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    // ---- Basic declaration tests ----

    @Test
    fun importDeclaresExternalFunction() {
        val classBytes = buildClass("org/kgen/test/Imp1") {
            import("puts", "(J)I")
        }
        val module = compile(classBytes)
        val puts = module.functions.find { it.name == "puts" }
        assertNotNull(puts)
        assertTrue(puts!!.blocks.isEmpty(), "Import should be declaration only")
        assertEquals(Linkage.EXTERNAL, puts.linkage)
        assertEquals(Type.I32, puts.returnType)
        assertEquals(1, puts.params.size)
        assertEquals(Type.I64, puts.params[0].type)
    }

    @Test
    fun importCustomName() {
        val classBytes = buildClass("org/kgen/test/Imp2") {
            import("print", "(J)I", nativeName = "puts")
        }
        val module = compile(classBytes)
        assertNotNull(module.functions.find { it.name == "puts" })
        assertNull(module.functions.find { it.name == "print" })
    }

    @Test
    fun importVoidReturn() {
        val classBytes = buildClass("org/kgen/test/Imp3") {
            import("free", "(J)V")
        }
        val module = compile(classBytes)
        val free = module.functions.find { it.name == "free" }!!
        assertEquals(Type.Void, free.returnType)
    }

    @Test
    fun importPointerParams() {
        val classBytes = buildClass("org/kgen/test/Imp4") {
            import("memcpy", "(Ljava/lang/Object;Ljava/lang/Object;J)Ljava/lang/Object;")
        }
        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "memcpy" }!!
        assertEquals(Type.OpaquePointer, fn.returnType)
        assertEquals(listOf(Type.OpaquePointer, Type.OpaquePointer, Type.I64), fn.params.map { it.type })
    }

    @Test
    fun importDoubleParam() {
        val classBytes = buildClass("org/kgen/test/Imp5") {
            import("sqrt", "(D)D")
        }
        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "sqrt" }!!
        assertEquals(Type.F64, fn.returnType)
        assertEquals(1, fn.params.size)
        assertEquals(Type.F64, fn.params[0].type)
    }

    @Test
    fun importFloatParam() {
        val classBytes = buildClass("org/kgen/test/Imp6") {
            import("sqrtf", "(F)F")
        }
        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "sqrtf" }!!
        assertEquals(Type.F32, fn.returnType)
        assertEquals(Type.F32, fn.params[0].type)
    }

    // ---- Multiple imports ----

    @Test
    fun multipleImportsAllDeclared() {
        val classBytes = buildClass("org/kgen/test/Multi") {
            import("puts", "(J)I")
            import("malloc", "(J)J")
            import("free", "(J)V")
            import("strlen", "(J)J")
            import("printf", "(J)I")
        }
        val module = compile(classBytes)
        val names = module.functions.map { it.name }.toSet()
        assertEquals(setOf("puts", "malloc", "free", "strlen", "printf"), names)
    }

    // ---- Import + Export pattern ----

    @Test
    fun importCalledFromExportedMethod() {
        val classBytes = buildClass("org/kgen/test/PrintLib") {
            import("puts", "(J)I")

            method("printlnStr", "(J)V", export = true, exportName = "kgen_println_str") { code ->
                code.maxStack = 2
                code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes)

        val puts = module.functions.find { it.name == "puts" }!!
        assertTrue(puts.blocks.isEmpty())

        val println = module.functions.find { it.name == "kgen_println_str" }!!
        assertTrue(println.blocks.isNotEmpty())
        assertEquals(Linkage.EXTERNAL, println.linkage)

        val calls = println.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "puts" },
            "Should call puts: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun importWithCustomNameCalledCorrectly() {
        val classBytes = buildClass("org/kgen/test/Remap") {
            import("myPuts", "(J)I", nativeName = "__libc_puts")

            method("hello", "(J)V") { code ->
                code.maxStack = 2
                code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("myPuts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes)

        assertNotNull(module.functions.find { it.name == "__libc_puts" })

        val hello = module.functions.find { it.name == "hello" }!!
        val calls = hello.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "__libc_puts" },
            "Should call __libc_puts: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun fullStdlibPattern() {
        // Demonstrates the P/Invoke pattern: import libc functions, export kgen_ wrappers
        val classBytes = buildClass("org/kgen/test/StdlibImpl") {
            import("puts", "(J)I")
            import("strlen", "(J)J")

            // kgen_println_str(long s) → puts(s)
            method("printlnStr", "(J)V", export = true, exportName = "kgen_println_str") { code ->
                code.maxStack = 2
                code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }

            // kgen_string_length(long s) → (int) strlen(s)
            method("stringLength", "(J)I", export = true, exportName = "kgen_string_length") { code ->
                code.maxStack = 2
                code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("strlen", "(J)J")
                // l2i to truncate long to int
                code.bytes.write(0x88) // l2i
                code.ireturn()
            }
        }
        val module = compile(classBytes)

        // Imports are declarations
        assertTrue(module.functions.find { it.name == "puts" }!!.blocks.isEmpty())
        assertTrue(module.functions.find { it.name == "strlen" }!!.blocks.isEmpty())

        // Exports have bodies and external linkage
        val println = module.functions.find { it.name == "kgen_println_str" }!!
        assertEquals(Linkage.EXTERNAL, println.linkage)
        assertTrue(println.blocks.isNotEmpty())

        val strLen = module.functions.find { it.name == "kgen_string_length" }!!
        assertEquals(Linkage.EXTERNAL, strLen.linkage)
        assertTrue(strLen.blocks.isNotEmpty())
        assertEquals(Type.I32, strLen.returnType)
    }

    @Test
    fun importMixedWithIntrinsics() {
        // Import + Kgen intrinsics in the same class
        val classBytes = buildClass("org/kgen/test/Mixed") {
            import("malloc", "(J)J")

            method("allocAndInit", "(I)J") { code ->
                code.maxStack = 4
                code.maxLocals = 4
                // long ptr = malloc(size)
                code.iload0()
                code.i2l()
                code.invokestaticSelf("malloc", "(J)J")
                // Kgen.storeInt(ptr, 42)
                code.bytes.write(0x59) // dup2 — duplicate the long
                // Actually, long takes 2 slots. Let's store in local first.
                code.bytes.write(0x37); code.bytes.write(1) // lstore 1
                code.bytes.write(0x16); code.bytes.write(1) // lload 1
                code.bytes.write(0x10); code.bytes.write(42) // bipush 42
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.bytes.write(0x16); code.bytes.write(1) // lload 1
                code.lreturn()
            }
        }
        val module = compile(classBytes)

        assertNotNull(module.functions.find { it.name == "malloc" })
        val fn = module.functions.find { it.name == "allocAndInit" }!!
        assertTrue(fn.blocks.isNotEmpty())

        // Should have both a call to malloc and a store instruction (from Kgen.storeInt)
        val instrs = fn.blocks.flatMap { it.instructions }
        val calls = instrs.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "malloc" })
        val stores = instrs.filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "Should have store from Kgen.storeInt")
    }

    // ---- Cross-architecture ----

    @Test
    fun importCompilesOnArm64() {
        val classBytes = buildClass("org/kgen/test/ArmImport") {
            import("puts", "(J)I")
            method("hello", "(J)V") { code ->
                code.maxStack = 2; code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes, Target.arm64())
        assertNotNull(module.functions.find { it.name == "puts" })
        assertNotNull(module.functions.find { it.name == "hello" })
    }

    @Test
    fun importCompilesOnRiscV() {
        val classBytes = buildClass("org/kgen/test/RvImport") {
            import("puts", "(J)I")
            method("hello", "(J)V") { code ->
                code.maxStack = 2; code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes, Target.riscv64())
        assertNotNull(module.functions.find { it.name == "puts" })
        assertNotNull(module.functions.find { it.name == "hello" })
    }

    // ---- Codegen ----

    @Test
    fun importProducesNativeCode() {
        val classBytes = buildClass("org/kgen/test/NativeImport") {
            import("puts", "(J)I")
            method("hello", "(J)V", export = true) { code ->
                code.maxStack = 2; code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes)
        val gen = org.kgen.target.x86.codegen.X86CodeGenerator()
        val compiled = gen.generateCode(module)
        assertTrue(compiled.textBytes.isNotEmpty(), "Should produce native code")
    }

    @Test
    fun importProducesObjectFile() {
        val classBytes = buildClass("org/kgen/test/ObjImport") {
            import("puts", "(J)I")
            method("hello", "(J)V", export = true) { code ->
                code.maxStack = 2; code.maxLocals = 2
                code.lload0()
                code.invokestaticSelf("puts", "(J)I")
                code.pop()
                code.returnVoid()
            }
        }
        val module = compile(classBytes)
        val gen = org.kgen.target.x86.codegen.X86CodeGenerator()
        val compiled = gen.generateCode(module)
        val obj = compiled.toObjectFile(
            org.kgen.binary.ObjectFormat.ELF,
            org.kgen.binary.Architecture.X86_64_LINUX,
        )
        val symbols = obj.symbols.map { it.name }.toSet()
        assertTrue("hello" in symbols, "Should export hello: $symbols")
        // puts should appear as an undefined symbol (external reference)
        val putsSymbol = obj.symbols.find { it.name == "puts" }
        assertNotNull(putsSymbol, "Should reference puts: $symbols")
    }
}

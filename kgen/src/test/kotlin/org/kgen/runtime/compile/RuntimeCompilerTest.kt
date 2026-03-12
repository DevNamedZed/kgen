package org.kgen.runtime.compile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

/**
 * Unit tests for RuntimeCompiler: annotation detection, name mangling,
 * linkage, function attributes, and static initializer handling.
 */
class RuntimeCompilerTest {

    private fun compile(classBytes: ByteArray, target: Target = Target.x86_64()): Module {
        return RuntimeCompiler(target).compile(classBytes)
    }

    // -- Helpers --

    /** Build a classfile with a single method, optionally annotated. */
    private fun buildAnnotatedClass(
        className: String,
        methodName: String,
        desc: String,
        code: ByteArray,
        maxStack: Int,
        maxLocals: Int,
        flags: Int = AccessFlags.PUBLIC or AccessFlags.STATIC,
        annotationClass: String? = null,
        annotationValue: Pair<String, String>? = null, // elementName → value
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry(className)
        val superClassIdx = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8(methodName)
        val descIdx = cp.utf8(desc)
        val codeNameIdx = cp.utf8("Code")

        val codeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(maxStack, maxLocals, code, emptyList(), emptyList()),
        )

        val attrs = mutableListOf(codeAttr)
        if (annotationClass != null) {
            val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")
            val annotationData = if (annotationValue != null) {
                buildAnnotationWithStringValue(cp, annotationClass, annotationValue.first, annotationValue.second)
            } else {
                buildAnnotationBytes(cp, annotationClass)
            }
            attrs.add(AttributeInfo(rtAnnotationsName, annotationData))
        }

        val method = MethodInfo(
            accessFlags = flags,
            nameIndex = nameIdx,
            descriptorIndex = descIdx,
            attributes = attrs,
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        return JvmClassWriter.write(cf)
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

    /** Build a simple class with no annotations. */
    private fun buildSimpleClass(
        className: String,
        methodName: String,
        desc: String,
        code: ByteArray,
        maxStack: Int,
        maxLocals: Int,
        flags: Int = AccessFlags.PUBLIC or AccessFlags.STATIC,
    ): ByteArray = buildAnnotatedClass(className, methodName, desc, code, maxStack, maxLocals, flags)

    // add(int, int) → iload_0, iload_1, iadd, ireturn
    private val addCode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
    // identity(int) → iload_0, ireturn
    private val identityCode = byteArrayOf(0x1A, 0xAC.toByte())
    // void no-op → return
    private val voidReturnCode = byteArrayOf(0xB1.toByte())

    // ---- Name mangling tests ----

    @Test
    fun staticMethodUsesMethodName() {
        val classBytes = buildSimpleClass("org/kgen/test/Foo", "add", "(II)I", addCode, 2, 2)
        val module = compile(classBytes)
        assertTrue(module.functions.any { it.name == "add" }, "Static method should use method name")
    }

    @Test
    fun instanceMethodUsesPrefixedName() {
        val classBytes = buildSimpleClass(
            "org/kgen/test/Bar", "getValue", "(I)I", identityCode, 2, 2,
            flags = AccessFlags.PUBLIC, // NOT static
        )
        val module = compile(classBytes)
        assertTrue(
            module.functions.any { it.name == "org_kgen_test_Bar_getValue" },
            "Instance method should be prefixed: ${module.functions.map { it.name }}",
        )
    }

    @Test
    fun clinitMangledToDoubleUnderscorePrefix() {
        val classBytes = buildSimpleClass(
            "org/kgen/test/Init", "<clinit>", "()V", voidReturnCode, 1, 0,
            flags = AccessFlags.STATIC,
        )
        val module = compile(classBytes)
        assertTrue(
            module.functions.any { it.name == "__clinit_org_kgen_test_Init" },
            "clinit should be mangled: ${module.functions.map { it.name }}",
        )
        assertTrue(
            module.globalCtors.any { it.function == "__clinit_org_kgen_test_Init" },
            "clinit should be registered as global ctor",
        )
    }

    @Test
    fun initMangledToClassNameSuffix() {
        val classBytes = buildSimpleClass(
            "org/kgen/test/Obj", "<init>", "()V", voidReturnCode, 1, 1,
            flags = AccessFlags.PUBLIC, // instance
        )
        val module = compile(classBytes)
        assertTrue(
            module.functions.any { it.name == "org_kgen_test_Obj_init" },
            "init should be mangled: ${module.functions.map { it.name }}",
        )
    }

    // ---- @KgenExport tests ----

    @Test
    fun kgenExportSetsExternalLinkage() {
        val classBytes = buildAnnotatedClass(
            "org/kgen/test/Exported", "compute", "(I)I", identityCode, 1, 1,
            annotationClass = "org/kgen/unmanaged/KgenExport",
        )
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "compute" }
        assertEquals(Linkage.EXTERNAL, fn.linkage, "Exported function should have EXTERNAL linkage")
    }

    @Test
    fun kgenExportCustomNameUsed() {
        val classBytes = buildAnnotatedClass(
            "org/kgen/test/CustomExport", "internalName", "(I)I", identityCode, 1, 1,
            annotationClass = "org/kgen/unmanaged/KgenExport",
            annotationValue = "value" to "my_custom_fn",
        )
        val module = compile(classBytes)
        assertTrue(
            module.functions.any { it.name == "my_custom_fn" },
            "Custom export name should be used: ${module.functions.map { it.name }}",
        )
    }

    @Test
    fun nonExportedMethodHasInternalLinkage() {
        val classBytes = buildSimpleClass("org/kgen/test/Internal", "helper", "(I)I", identityCode, 1, 1)
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "helper" }
        assertEquals(Linkage.INTERNAL, fn.linkage, "Non-exported function should have INTERNAL linkage")
    }

    // ---- @KgenInline tests ----

    @Test
    fun kgenInlineSetsAlwaysInline() {
        val classBytes = buildAnnotatedClass(
            "org/kgen/test/Inlined", "doubleIt", "(I)I",
            byteArrayOf(0x1A, 0x1A, 0x60, 0xAC.toByte()), 2, 1,
            annotationClass = "org/kgen/unmanaged/KgenInline",
        )
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "doubleIt" }
        assertTrue(fn.attributes.contains(FnAttribute.ALWAYSINLINE), "Should have ALWAYSINLINE")
    }

    // ---- @KgenLeaf tests ----

    @Test
    fun kgenLeafSetsNounwind() {
        val classBytes = buildAnnotatedClass(
            "org/kgen/test/Leaf", "fastAdd", "(II)I", addCode, 2, 2,
            annotationClass = "org/kgen/unmanaged/KgenLeaf",
        )
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "fastAdd" }
        assertTrue(fn.attributes.contains(FnAttribute.NOUNWIND), "Leaf should have NOUNWIND")
    }

    // ---- Multiple methods in one class ----

    @Test
    fun multipleMethodsCompileCorrectly() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/test/Multi")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val codeNameIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        // Method 1: publicApi — @KgenExport
        val name1 = cp.utf8("publicApi")
        val desc1 = cp.utf8("(II)I")
        val code1 = AttributeBuilder.buildCode(codeNameIdx, CodeAttribute(2, 2, addCode, emptyList(), emptyList()))
        val ann1 = buildAnnotationBytes(cp, "org/kgen/unmanaged/KgenExport")
        val m1 = MethodInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, name1, desc1,
            listOf(code1, AttributeInfo(rtAnnotationsName, ann1)))

        // Method 2: helper — @KgenInline
        val name2 = cp.utf8("helper")
        val desc2 = cp.utf8("(I)I")
        val code2 = AttributeBuilder.buildCode(codeNameIdx, CodeAttribute(1, 1, identityCode, emptyList(), emptyList()))
        val ann2 = buildAnnotationBytes(cp, "org/kgen/unmanaged/KgenInline")
        val m2 = MethodInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, name2, desc2,
            listOf(code2, AttributeInfo(rtAnnotationsName, ann2)))

        // Method 3: internal — no annotations
        val name3 = cp.utf8("internal")
        val desc3 = cp.utf8("(I)I")
        val code3 = AttributeBuilder.buildCode(codeNameIdx, CodeAttribute(1, 1, identityCode, emptyList(), emptyList()))
        val m3 = MethodInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, name3, desc3, listOf(code3))

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(m1, m2, m3), attributes = emptyList(),
        )

        val module = compile(JvmClassWriter.write(cf))

        val publicFn = module.functions.first { it.name == "publicApi" }
        assertEquals(Linkage.EXTERNAL, publicFn.linkage)

        val helperFn = module.functions.first { it.name == "helper" }
        assertTrue(helperFn.attributes.contains(FnAttribute.ALWAYSINLINE))

        val internalFn = module.functions.first { it.name == "internal" }
        assertEquals(Linkage.INTERNAL, internalFn.linkage)
        assertFalse(internalFn.attributes.contains(FnAttribute.ALWAYSINLINE))
    }

    // ---- Mem2Reg and Inlining passes applied ----

    @Test
    fun mem2RegRemovesAllocas() {
        val classBytes = buildSimpleClass("org/kgen/test/Passes", "identity", "(I)I", identityCode, 1, 1)
        val module = compile(classBytes)
        val fn = module.functions.first()
        val allocas = fn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Alloca>()
        assertTrue(allocas.isEmpty(), "Mem2Reg should remove allocas: found ${allocas.size}")
    }

    // ---- Cross-target compilation ----

    @Test
    fun compilesToArm64Module() {
        val classBytes = buildSimpleClass(
            "org/kgen/test/Arm", "inc", "(I)I",
            byteArrayOf(0x1A, 0x04, 0x60, 0xAC.toByte()), 2, 1,
        )
        val module = compile(classBytes, Target.arm64())
        assertEquals(1, module.functions.size)
        assertEquals("inc", module.functions.first().name)
    }

    @Test
    fun compilesToRiscVModule() {
        val classBytes = buildSimpleClass(
            "org/kgen/test/Rv", "dec", "(I)I",
            byteArrayOf(0x1A, 0x04, 0x64, 0xAC.toByte()), 2, 1,
        )
        val module = compile(classBytes, Target.riscv64())
        assertEquals(1, module.functions.size)
        assertEquals("dec", module.functions.first().name)
    }

    // ---- Subset validation ----

    @Test
    fun validCodePassesSubsetValidation() {
        val classBytes = buildSimpleClass("org/kgen/test/Valid", "add", "(II)I", addCode, 2, 2)
        val module = compile(classBytes)
        assertNotNull(module)
    }

    // ---- @KgenImport tests ----

    /** Build a class with @KgenImport native methods + calling methods. */
    private fun buildClassWithImports(
        className: String,
        imports: List<ImportDecl>,
        methods: List<MethodDecl>,
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry(className)
        val superClassIdx = cp.classEntry("java/lang/Object")
        val codeNameIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        val allMethods = mutableListOf<MethodInfo>()

        // Import declarations (native methods with @KgenImport)
        for (imp in imports) {
            val nameIdx = cp.utf8(imp.javaName)
            val descIdx = cp.utf8(imp.desc)
            val annData = if (imp.nativeName != null) {
                buildAnnotationWithStringValue(cp, "org/kgen/unmanaged/KgenImport", "value", imp.nativeName)
            } else {
                buildAnnotationBytes(cp, "org/kgen/unmanaged/KgenImport")
            }
            allMethods.add(MethodInfo(
                accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.NATIVE,
                nameIndex = nameIdx, descriptorIndex = descIdx,
                attributes = listOf(AttributeInfo(rtAnnotationsName, annData)),
            ))
        }

        // Regular methods with code
        for (m in methods) {
            val nameIdx = cp.utf8(m.name)
            val descIdx = cp.utf8(m.desc)
            val codeAttr = AttributeBuilder.buildCode(
                codeNameIdx,
                CodeAttribute(m.maxStack, m.maxLocals, m.code, emptyList(), emptyList()),
            )
            val attrs = mutableListOf<AttributeInfo>(codeAttr)
            if (m.annotationClass != null) {
                val annData = buildAnnotationBytes(cp, m.annotationClass)
                attrs.add(AttributeInfo(rtAnnotationsName, annData))
            }
            allMethods.add(MethodInfo(
                accessFlags = m.flags,
                nameIndex = nameIdx, descriptorIndex = descIdx,
                attributes = attrs,
            ))
        }

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = allMethods, attributes = emptyList(),
        ))
    }

    data class ImportDecl(val javaName: String, val desc: String, val nativeName: String? = null)
    data class MethodDecl(
        val name: String, val desc: String, val code: ByteArray, val maxStack: Int, val maxLocals: Int,
        val flags: Int = AccessFlags.PUBLIC or AccessFlags.STATIC,
        val annotationClass: String? = null,
    )

    @Test
    fun kgenImportDeclaresExternalFunction() {
        val classBytes = buildClassWithImports(
            "org/kgen/test/WithImport",
            imports = listOf(ImportDecl("puts", "(J)I")),
            methods = emptyList(),
        )
        val module = compile(classBytes)
        val puts = module.functions.find { it.name == "puts" }
        assertNotNull(puts, "Should have 'puts' declaration: ${module.functions.map { it.name }}")
        assertTrue(puts!!.blocks.isEmpty(), "Import should have no body")
        assertEquals(Linkage.EXTERNAL, puts.linkage)
        assertEquals(Type.I32, puts.returnType)
        assertEquals(1, puts.params.size)
        assertEquals(Type.I64, puts.params[0].type)
    }

    @Test
    fun kgenImportCustomNameUsed() {
        val classBytes = buildClassWithImports(
            "org/kgen/test/CustomImport",
            imports = listOf(ImportDecl("print", "(J)I", nativeName = "puts")),
            methods = emptyList(),
        )
        val module = compile(classBytes)
        val puts = module.functions.find { it.name == "puts" }
        assertNotNull(puts, "Should use custom name 'puts': ${module.functions.map { it.name }}")
        assertNull(module.functions.find { it.name == "print" }, "Should not have java name 'print'")
    }

    @Test
    fun kgenImportCalledFromMethod() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/test/Caller")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val codeNameIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        // @KgenImport native int puts(long s);
        val putsName = cp.utf8("puts")
        val putsDesc = cp.utf8("(J)I")
        val putsAnn = buildAnnotationBytes(cp, "org/kgen/unmanaged/KgenImport")
        val putsMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.NATIVE,
            nameIndex = putsName, descriptorIndex = putsDesc,
            attributes = listOf(AttributeInfo(rtAnnotationsName, putsAnn)),
        )

        // hello(long s) → puts(s); return;
        val putsRef = cp.methodRef("org/kgen/test/Caller", "puts", "(J)I")
        val helloName = cp.utf8("hello")
        val helloDesc = cp.utf8("(J)V")
        val helloCode = byteArrayOf(
            0x1E,                                                        // lload_0
            0xB8.toByte(), (putsRef shr 8).toByte(), putsRef.toByte(),   // invokestatic puts
            0x57,                                                        // pop (discard int result)
            0xB1.toByte(),                                               // return
        )
        val helloCodeAttr = AttributeBuilder.buildCode(
            codeNameIdx, CodeAttribute(2, 2, helloCode, emptyList(), emptyList()),
        )
        val helloMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = helloName, descriptorIndex = helloDesc,
            attributes = listOf(helloCodeAttr),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(putsMethod, helloMethod), attributes = emptyList(),
        ))

        val module = compile(classBytes)

        // Should have both: puts (declaration) and hello (definition)
        val putsFn = module.functions.find { it.name == "puts" }
        assertNotNull(putsFn, "Should have puts declaration")
        assertTrue(putsFn!!.blocks.isEmpty(), "puts should be a declaration (no body)")

        val helloFn = module.functions.find { it.name == "hello" }
        assertNotNull(helloFn, "Should have hello function")
        assertTrue(helloFn!!.blocks.isNotEmpty(), "hello should have a body")

        // hello should call puts
        val calls = helloFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "puts" },
            "hello should call puts: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun kgenImportCustomNameCalledCorrectly() {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/test/Remap")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val codeNameIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        // @KgenImport("__libc_puts") native int myPuts(long s);
        val myPutsName = cp.utf8("myPuts")
        val myPutsDesc = cp.utf8("(J)I")
        val myPutsAnn = buildAnnotationWithStringValue(cp, "org/kgen/unmanaged/KgenImport", "value", "__libc_puts")
        val myPutsMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.NATIVE,
            nameIndex = myPutsName, descriptorIndex = myPutsDesc,
            attributes = listOf(AttributeInfo(rtAnnotationsName, myPutsAnn)),
        )

        // caller(long s) → myPuts(s); return;
        val myPutsRef = cp.methodRef("org/kgen/test/Remap", "myPuts", "(J)I")
        val callerName = cp.utf8("caller")
        val callerDesc = cp.utf8("(J)V")
        val callerCode = byteArrayOf(
            0x1E,                                                              // lload_0
            0xB8.toByte(), (myPutsRef shr 8).toByte(), myPutsRef.toByte(),     // invokestatic myPuts
            0x57,                                                              // pop
            0xB1.toByte(),                                                     // return
        )
        val callerCodeAttr = AttributeBuilder.buildCode(
            codeNameIdx, CodeAttribute(2, 2, callerCode, emptyList(), emptyList()),
        )
        val callerMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = callerName, descriptorIndex = callerDesc,
            attributes = listOf(callerCodeAttr),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(myPutsMethod, callerMethod), attributes = emptyList(),
        ))

        val module = compile(classBytes)

        // Should declare __libc_puts, NOT myPuts
        assertNotNull(module.functions.find { it.name == "__libc_puts" },
            "Should declare __libc_puts: ${module.functions.map { it.name }}")

        // caller should call __libc_puts (not myPuts)
        val callerFn = module.functions.find { it.name == "caller" }!!
        val calls = callerFn.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "__libc_puts" },
            "caller should call __libc_puts: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun kgenImportMultipleDeclarations() {
        val classBytes = buildClassWithImports(
            "org/kgen/test/MultiImport",
            imports = listOf(
                ImportDecl("puts", "(J)I"),
                ImportDecl("malloc", "(J)J"),
                ImportDecl("free", "(J)V"),
                ImportDecl("strlen", "(J)J"),
            ),
            methods = emptyList(),
        )
        val module = compile(classBytes)
        val names = module.functions.map { it.name }.toSet()
        assertTrue("puts" in names, "Should have puts")
        assertTrue("malloc" in names, "Should have malloc")
        assertTrue("free" in names, "Should have free")
        assertTrue("strlen" in names, "Should have strlen")

        val mallocFn = module.functions.find { it.name == "malloc" }!!
        assertEquals(Type.I64, mallocFn.returnType)

        val freeFn = module.functions.find { it.name == "free" }!!
        assertEquals(Type.Void, freeFn.returnType)
    }

    @Test
    fun kgenImportWithPointerTypes() {
        val classBytes = buildClassWithImports(
            "org/kgen/test/PtrImport",
            imports = listOf(
                ImportDecl("memcpy", "(Ljava/lang/Object;Ljava/lang/Object;J)Ljava/lang/Object;"),
            ),
            methods = emptyList(),
        )
        val module = compile(classBytes)
        val memcpy = module.functions.find { it.name == "memcpy" }!!
        assertEquals(Type.OpaquePointer, memcpy.returnType)
        assertEquals(3, memcpy.params.size)
        assertEquals(Type.OpaquePointer, memcpy.params[0].type)
        assertEquals(Type.OpaquePointer, memcpy.params[1].type)
        assertEquals(Type.I64, memcpy.params[2].type)
    }
}

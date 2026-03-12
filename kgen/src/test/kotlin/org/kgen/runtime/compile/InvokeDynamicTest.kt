package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class InvokeDynamicTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildClassWithInvokeDynamic(
        className: String,
        methodName: String,
        methodDesc: String,
        methodBytecode: ByteArray,
        maxStack: Int,
        maxLocals: Int,
        bootstrapMethods: List<BootstrapMethodEntry>,
        cpSetup: (ConstantPoolBuilder) -> Unit = {},
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry(className)
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        cpSetup(cp)

        val nameIdx = cp.utf8(methodName)
        val descIdx = cp.utf8(methodDesc)
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = maxStack, maxLocals = maxLocals, code = methodBytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(bootstrapMethods))

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))
    }

    @Test
    fun stringConcatWithConstants() {
        // Simulates: return "Hello, " + name + "!"
        // which Java 9+ compiles to invokedynamic StringConcatFactory
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/Concat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        // Build the bootstrap method handle for StringConcatFactory.makeConcatWithConstants
        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef) // REF_invokeStatic
        val recipe = cp.string("Hello, \u0001!")

        // The invokedynamic call: takes one String arg, returns String
        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;")

        val nameIdx = cp.utf8("greet")
        val descIdx = cp.utf8("(Ljava/lang/String;)Ljava/lang/String;")

        // aload_0 (name param), invokedynamic, areturn
        val code = byteArrayOf(
            0x2A.toByte(),                              // aload_0
            0xBA.toByte(),                              // invokedynamic
            (indyIdx shr 8).toByte(), indyIdx.toByte(), // cp index
            0x00, 0x00,                                 // reserved bytes
            0xB0.toByte(),                              // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "greet" }
        assertNotNull(fn, "Expected function 'greet'")

        // Should have calls to string concat helpers
        val calls = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_strconcat_begin" },
            "Should call kgen_strconcat_begin")
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_strconcat_str" },
            "Should call kgen_strconcat_str for literal parts")
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_strconcat_finish" },
            "Should call kgen_strconcat_finish")

        // Should have string constant globals for "Hello, " and "!"
        val strGlobals = module.globals.filter { it.initializer is Constant.StringConst }
        assertTrue(strGlobals.any { (it.initializer as Constant.StringConst).value == "Hello, " },
            "Should have 'Hello, ' string constant")
        assertTrue(strGlobals.any { (it.initializer as Constant.StringConst).value == "!" },
            "Should have '!' string constant")
    }

    @Test
    fun stringConcatWithIntArg() {
        // Simulates: "value=" + intVal
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/IntConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("value=\u0001")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(I)Ljava/lang/String;")

        val nameIdx = cp.utf8("intToStr")
        val descIdx = cp.utf8("(I)Ljava/lang/String;")

        // iload_0, invokedynamic, areturn
        val code = byteArrayOf(
            0x1A.toByte(),                              // iload_0
            0xBA.toByte(),                              // invokedynamic
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),                              // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "intToStr" }
        assertNotNull(fn, "Expected function 'intToStr'")

        // Should call kgen_strconcat_int for the int argument
        val calls = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_strconcat_int" },
            "Should call kgen_strconcat_int for int argument")
    }

    @Test
    fun stringConcatWithLongArg() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/LongConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("count=\u0001")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(J)Ljava/lang/String;")

        val nameIdx = cp.utf8("longToStr")
        val descIdx = cp.utf8("(J)Ljava/lang/String;")

        // lload_0, invokedynamic, areturn
        val code = byteArrayOf(
            0x1E.toByte(),                              // lload_0
            0xBA.toByte(),                              // invokedynamic
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),                              // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 3, maxLocals = 2, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "longToStr" }
        assertNotNull(fn)

        val calls = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_strconcat_long" },
            "Should call kgen_strconcat_long for long argument")
    }

    @Test
    fun lambdaMetafactoryNoCaptures() {
        // Simulates: Runnable r = () -> doSomething();
        // The invokedynamic creates a Runnable from a static method ref
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/Lambda")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        // Target method: test/Lambda.doWork()V
        val targetMethodRef = cp.methodRef("test/Lambda", "doWork", "()V")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef) // REF_invokeStatic

        // BSM static args: [0] erased type, [1] target method handle, [2] specialized type
        val erasedType = cp.methodType("()V")
        val targetHandle = cp.methodHandle(6, targetMethodRef) // REF_invokeStatic
        val specializedType = cp.methodType("()V")

        // invokedynamic: "run" ()Ljava/lang/Runnable;
        val indyIdx = cp.invokeDynamic(0, "run", "()Ljava/lang/Runnable;")

        val nameIdx = cp.utf8("makeLambda")
        val descIdx = cp.utf8("()Ljava/lang/Runnable;")

        // invokedynamic, areturn
        val code = byteArrayOf(
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(), // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(erasedType, targetHandle, specializedType))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "makeLambda" }
        assertNotNull(fn, "Expected function 'makeLambda'")

        // The return value should be a GlobalRef to the target method "doWork"
        val ret = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Ret>()
            .firstOrNull()
        assertNotNull(ret, "Should have a return instruction")
        // The returned value should reference the target function
        val retVal = ret!!.value
        assertTrue(retVal is GlobalRef, "Lambda with no captures should return a GlobalRef, got: $retVal")
        assertEquals("doWork", (retVal as GlobalRef).name)
    }

    @Test
    fun lambdaMetafactoryWithCapture() {
        // Simulates: int x = 42; Supplier<Integer> s = () -> x;
        // invokedynamic captures `x` (one int capture)
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/CaptureLambda")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val targetMethodRef = cp.methodRef("test/CaptureLambda", "lambda\$main\$0", "(I)I")
        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)

        val erasedType = cp.methodType("()I")
        val targetHandle = cp.methodHandle(6, targetMethodRef)
        val specializedType = cp.methodType("()I")

        // invokedynamic: "get" (I)Ljava/util/function/Supplier; — captures one int
        val indyIdx = cp.invokeDynamic(0, "get", "(I)Ljava/util/function/Supplier;")

        val nameIdx = cp.utf8("captureLambda")
        val descIdx = cp.utf8("(I)Ljava/util/function/Supplier;")

        // iload_0 (captured value), invokedynamic, areturn
        val code = byteArrayOf(
            0x1A.toByte(),                              // iload_0
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(), // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(erasedType, targetHandle, specializedType))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "captureLambda" }
        assertNotNull(fn, "Expected function 'captureLambda'")
        // Should produce a return value (the captured value, since single capture)
        assertTrue(fn!!.blocks.flatMap { it.instructions }.any { it is Instruction.Ret })
    }

    @Test
    fun subsetValidatorAllowsInvokeDynamic() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/ValidIndy")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val indyIdx = cp.invokeDynamic(0, "concat", "(I)Ljava/lang/String;")

        val nameIdx = cp.utf8("test")
        val descIdx = cp.utf8("(I)Ljava/lang/String;")

        val code = byteArrayOf(
            0x1A.toByte(),                              // iload_0
            0xBA.toByte(),                              // invokedynamic
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),                              // areturn
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "invokedynamic should be allowed, but got errors: $errors")
    }

    @Test
    fun stringConcatProducesCorrectGlobalOrder() {
        // Test concat with multiple string parts: "a" + x + "b" + y + "c"
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MultiConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("a\u0001b\u0001c")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")

        val nameIdx = cp.utf8("multiConcat")
        val descIdx = cp.utf8("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")

        // aload_0, aload_1, invokedynamic, areturn
        val code = byteArrayOf(
            0x2A.toByte(),                              // aload_0
            0x2B.toByte(),                              // aload_1
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 3, maxLocals = 2, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "multiConcat" }
        assertNotNull(fn)

        // Should have 3 string constants: "a", "b", "c"
        val strGlobals = module.globals.filter { it.initializer is Constant.StringConst }
            .map { (it.initializer as Constant.StringConst).value }
        assertTrue("a" in strGlobals, "Should have 'a' constant, got: $strGlobals")
        assertTrue("b" in strGlobals, "Should have 'b' constant, got: $strGlobals")
        assertTrue("c" in strGlobals, "Should have 'c' constant, got: $strGlobals")

        // Should have 2 str concat calls (for the 2 dynamic args)
        val calls = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Call>()
        val strConcatCalls = calls.filter { (it.function as? GlobalRef)?.name == "kgen_strconcat_str" }
        // 3 literal parts + 2 dynamic args = 5 str calls
        assertEquals(5, strConcatCalls.size,
            "Should have 5 kgen_strconcat_str calls (3 literals + 2 dynamic args)")
    }

    @Test
    fun stringConcatWithMultipleStringArgs() {
        // "prefix" + str1 + "middle" + str2 + "suffix"
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MultiStr")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("prefix\u0001middle\u0001suffix")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")

        val nameIdx = cp.utf8("twoStrConcat")
        val descIdx = cp.utf8("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")

        val code = byteArrayOf(
            0x2A.toByte(),                              // aload_0
            0x2B.toByte(),                              // aload_1
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 3, maxLocals = 2, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "twoStrConcat" }
        assertNotNull(fn)

        val strGlobals = module.globals.filter { it.initializer is Constant.StringConst }
            .map { (it.initializer as Constant.StringConst).value }
        assertTrue("prefix" in strGlobals)
        assertTrue("middle" in strGlobals)
        assertTrue("suffix" in strGlobals)
    }

    @Test
    fun stringConcatWithDoubleArg() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/DoubleConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("val=\u0001")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(D)Ljava/lang/String;")

        val nameIdx = cp.utf8("doubleToStr")
        val descIdx = cp.utf8("(D)Ljava/lang/String;")

        val code = byteArrayOf(
            0x26.toByte(),                              // dload_0
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 3, maxLocals = 2, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "doubleToStr" }
        assertNotNull(fn, "Expected function 'doubleToStr'")
        val calls = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Call>()
        assertTrue(calls.any {
            val name = (it.function as? GlobalRef)?.name
            name == "kgen_strconcat_double" || name == "kgen_strconcat_str"
        }, "Should call a strconcat function for double")
    }

    @Test
    fun stringConcatWithBooleanArgMappedToInt() {
        // boolean is mapped to int (Z -> I) in JVM
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/BoolConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("flag=\u0001")

        // boolean is Z which maps to int at invokedynamic level
        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(Z)Ljava/lang/String;")

        val nameIdx = cp.utf8("boolToStr")
        val descIdx = cp.utf8("(Z)Ljava/lang/String;")

        val code = byteArrayOf(
            0x1A.toByte(),                              // iload_0 (boolean is int)
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "boolToStr" }
        assertNotNull(fn, "Expected function 'boolToStr'")
        // Should compile without error; boolean is treated as int
        assertTrue(fn!!.blocks.flatMap { it.instructions }.any { it is Instruction.Ret })
    }

    @Test
    fun lambdaWithIntParameter() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/IntLambda")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val targetMethodRef = cp.methodRef("test/IntLambda", "lambda\$0", "(I)I")
        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)

        val erasedType = cp.methodType("(I)I")
        val targetHandle = cp.methodHandle(6, targetMethodRef)
        val specializedType = cp.methodType("(I)I")

        val indyIdx = cp.invokeDynamic(0, "apply", "()Ljava/util/function/IntUnaryOperator;")

        val nameIdx = cp.utf8("makeFn")
        val descIdx = cp.utf8("()Ljava/util/function/IntUnaryOperator;")

        val code = byteArrayOf(
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(erasedType, targetHandle, specializedType))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        val module = compile(classBytes)
        val fn = module.functions.find { it.name == "makeFn" }
        assertNotNull(fn, "Expected function 'makeFn'")
        val ret = fn!!.blocks.flatMap { it.instructions }
            .filterIsInstance<Instruction.Ret>().firstOrNull()
        assertNotNull(ret)
        // Should return a GlobalRef to the lambda target
        assertTrue(ret!!.value is GlobalRef,
            "Lambda should return GlobalRef, got: ${ret.value}")
    }

    @Test
    fun stringConcatCompilesToNative() {
        // Verify that string concat lowers all the way to native code
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/NativeConcat")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val bsmAttrIdx = cp.utf8("BootstrapMethods")

        val bsmMethodRef = cp.methodRef(
            "java/lang/invoke/StringConcatFactory",
            "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
        val bsmHandle = cp.methodHandle(6, bsmMethodRef)
        val recipe = cp.string("x=\u0001")

        val indyIdx = cp.invokeDynamic(0, "makeConcatWithConstants", "(I)Ljava/lang/String;")

        val nameIdx = cp.utf8("format")
        val descIdx = cp.utf8("(I)Ljava/lang/String;")

        val code = byteArrayOf(
            0x1A.toByte(),
            0xBA.toByte(),
            (indyIdx shr 8).toByte(), indyIdx.toByte(),
            0x00, 0x00,
            0xB0.toByte(),
        )

        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )

        val bsmEntry = BootstrapMethodEntry(bsmHandle, listOf(recipe))
        val bsmAttr = AttributeBuilder.buildBootstrapMethods(
            bsmAttrIdx, BootstrapMethodsAttribute(listOf(bsmEntry)))

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 55,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = listOf(bsmAttr),
        ))

        // Should compile to native object without error
        val obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(classBytes))
        val textSection = obj.sections.find { it.name == ".text" }
        assertNotNull(textSection, "Should have .text section")
        assertTrue(textSection!!.data.isNotEmpty(), "Should have generated code")
    }
}

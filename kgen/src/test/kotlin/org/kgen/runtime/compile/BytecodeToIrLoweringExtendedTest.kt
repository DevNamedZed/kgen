package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class BytecodeToIrLoweringExtendedTest {

    private data class MethodSpec(
        val name: String,
        val descriptor: String,
        val bytecode: ByteArray,
        val maxStack: Int,
        val maxLocals: Int,
        val accessFlags: Int = AccessFlags.PUBLIC or AccessFlags.STATIC,
    )

    private fun buildClass(
        className: String,
        methods: List<MethodSpec>,
        fields: List<FieldInfo> = emptyList(),
        cpSetup: (ConstantPoolBuilder) -> Unit = {},
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry(className)
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        cpSetup(cp)

        val jvmMethods = methods.map { spec ->
            val nameIdx = cp.utf8(spec.name)
            val descIdx = cp.utf8(spec.descriptor)
            val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = spec.maxStack, maxLocals = spec.maxLocals, code = spec.bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            ))
            MethodInfo(
                accessFlags = spec.accessFlags,
                nameIndex = nameIdx, descriptorIndex = descIdx,
                attributes = listOf(codeAttr),
            )
        }

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = fields,
            methods = jvmMethods, attributes = emptyList(),
        ))
    }

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun allInstructions(fn: IrFunction): List<Instruction> {
        return fn.blocks.flatMap { it.instructions }
    }

    @Test
    fun `anewarray compiles to malloc call`() {
        // bipush 10, anewarray <classref>, areturn
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/ANewArray")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val elementClass = cp.classEntry("java/lang/Object")

        val nameIdx = cp.utf8("makeArray")
        val descIdx = cp.utf8("()[Ljava/lang/Object;")
        val code = byteArrayOf(
            0x10, 10,                                       // bipush 10
            0xBD.toByte(),                                  // anewarray
            (elementClass shr 8).toByte(), elementClass.toByte(),
            0xB0.toByte(),                                  // areturn
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
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "makeArray" }
        val instructions = allInstructions(fn)
        val calls = instructions.filterIsInstance<Instruction.Call>()
        val callNames = calls.mapNotNull {
            when (val f = it.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> null
            }
        }
        assertTrue("malloc" in callNames,
            "anewarray should call malloc, got: $callNames")
    }

    @Test
    fun `multianewarray compiles to malloc call`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MultiArray")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val arrayClass = cp.classEntry("[[I")

        val nameIdx = cp.utf8("make2d")
        val descIdx = cp.utf8("()[[I")
        // bipush 3, bipush 4, multianewarray <classref> 2, areturn
        val code = byteArrayOf(
            0x10, 3,                                        // bipush 3
            0x10, 4,                                        // bipush 4
            0xC5.toByte(),                                  // multianewarray
            (arrayClass shr 8).toByte(), arrayClass.toByte(),
            2,                                              // dimensions
            0xB0.toByte(),                                  // areturn
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
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "make2d" }
        val instructions = allInstructions(fn)
        val calls = instructions.filterIsInstance<Instruction.Call>()
        val callNames = calls.mapNotNull {
            when (val f = it.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> null
            }
        }
        assertTrue("malloc" in callNames,
            "multianewarray should call malloc, got: $callNames")
    }

    @Test
    fun `monitorenter compiles as no-op`() {
        // aload_0, monitorenter, return
        val classBytes = buildClass("test/Monitor", listOf(
            MethodSpec("enter", "(Ljava/lang/Object;)V",
                byteArrayOf(0x2A, 0xC2.toByte(), 0xB1.toByte()),
                maxStack = 1, maxLocals = 1),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "enter" }
        val instructions = allInstructions(fn)
        // Should have a Ret but no special instruction for monitorenter
        assertTrue(instructions.any { it is Instruction.Ret })
        // Should not crash
    }

    @Test
    fun `monitorexit compiles as no-op`() {
        // aload_0, monitorexit, return
        val classBytes = buildClass("test/Monitor", listOf(
            MethodSpec("exit", "(Ljava/lang/Object;)V",
                byteArrayOf(0x2A, 0xC3.toByte(), 0xB1.toByte()),
                maxStack = 1, maxLocals = 1),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "exit" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Instruction.Ret })
    }

    @Test
    fun `checkcast compiles as no-op passthrough`() {
        // aload_0, checkcast <Object>, areturn
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/Cast")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val castClass = cp.classEntry("java/lang/String")

        val nameIdx = cp.utf8("cast")
        val descIdx = cp.utf8("(Ljava/lang/Object;)Ljava/lang/String;")
        val code = byteArrayOf(
            0x2A,                                           // aload_0
            0xC0.toByte(),                                  // checkcast
            (castClass shr 8).toByte(), castClass.toByte(),
            0xB0.toByte(),                                  // areturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "cast" }
        val instructions = allInstructions(fn)
        val ret = instructions.filterIsInstance<Instruction.Ret>().first()
        // Return value should be the same parameter (pass-through)
        assertNotNull(ret.value, "checkcast should pass the value through")
    }

    @Test
    fun `instanceof compiles to constant 1`() {
        // aload_0, instanceof <String>, ireturn
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/InstanceOf")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val checkClass = cp.classEntry("java/lang/String")

        val nameIdx = cp.utf8("check")
        val descIdx = cp.utf8("(Ljava/lang/Object;)I")
        val code = byteArrayOf(
            0x2A,                                           // aload_0
            0xC1.toByte(),                                  // instanceof
            (checkClass shr 8).toByte(), checkClass.toByte(),
            0xAC.toByte(),                                  // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "check" }
        val instructions = allInstructions(fn)
        val ret = instructions.filterIsInstance<Instruction.Ret>().first()
        assertTrue(ret.value is Constant.I32 && (ret.value as Constant.I32).value == 1,
            "instanceof should return constant 1 in subset, got ${ret.value}")
    }

    @Test
    fun `getstatic System out produces null placeholder`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/SysOut")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val sysOutRef = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        val printlnRef = cp.methodRef("java/io/PrintStream", "println", "()V")

        val nameIdx = cp.utf8("test")
        val descIdx = cp.utf8("()V")
        val code = byteArrayOf(
            0xB2.toByte(), (sysOutRef shr 8).toByte(), sysOutRef.toByte(),
            0xB6.toByte(), (printlnRef shr 8).toByte(), printlnRef.toByte(),
            0xB1.toByte(),
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
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "test" }
        val calls = allInstructions(fn).filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_println_void" },
            "System.out.println() should redirect to kgen_println_void")
    }

    @Test
    fun `System out println string redirected to kgen_println_str`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/PrintStr")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val sysOutRef = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        val strIdx = cp.string("hello")
        val printlnRef = cp.methodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")

        val nameIdx = cp.utf8("printHello")
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
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "printHello" }
        val calls = allInstructions(fn).filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_println_str" },
            "Should redirect to kgen_println_str")
    }

    @Test
    fun `Math abs int redirected to kgen_math_abs_int`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MathAbs")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val absRef = cp.methodRef("java/lang/Math", "abs", "(I)I")

        val nameIdx = cp.utf8("absVal")
        val descIdx = cp.utf8("(I)I")
        val code = byteArrayOf(
            0x1A, // iload_0
            0xB8.toByte(), (absRef shr 8).toByte(), absRef.toByte(),
            0xAC.toByte(),
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "absVal" }
        val calls = allInstructions(fn).filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_math_abs_int" })
    }

    @Test
    fun `String length redirected to kgen_string_length`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/StrLen2")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val lengthRef = cp.methodRef("java/lang/String", "length", "()I")

        val nameIdx = cp.utf8("len")
        val descIdx = cp.utf8("(Ljava/lang/String;)I")
        val code = byteArrayOf(
            0x2A,
            0xB6.toByte(), (lengthRef shr 8).toByte(), lengthRef.toByte(),
            0xAC.toByte(),
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "len" }
        val calls = allInstructions(fn).filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_string_length" })
    }

    @Test
    fun `multiple methods in one class compile correctly`() {
        val classBytes = buildClass("test/Multi2", listOf(
            MethodSpec("add", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
            MethodSpec("sub", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x64, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
            MethodSpec("noop", "()V",
                byteArrayOf(0xB1.toByte()),
                maxStack = 0, maxLocals = 0),
        ))
        val module = compile(classBytes)
        assertEquals(3, module.functions.size)
        val names = module.functions.map { it.name }.toSet()
        assertTrue("add" in names)
        assertTrue("sub" in names)
        assertTrue("noop" in names)
    }

    @Test
    fun `static field access generates global variable`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/StaticField")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val fieldRef = cp.fieldRef("test/StaticField", "counter", "I")

        val nameIdx = cp.utf8("getCounter")
        val descIdx = cp.utf8("()I")
        // getstatic test/StaticField.counter:I, ireturn
        val code = byteArrayOf(
            0xB2.toByte(), (fieldRef shr 8).toByte(), fieldRef.toByte(),
            0xAC.toByte(),
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
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        assertTrue(module.globals.any { it.name.contains("counter") },
            "Should have a global for static field 'counter'")
    }

    @Test
    fun `instance field access generates GEP`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/FieldAccess")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val fieldRef = cp.fieldRef("test/FieldAccess", "value", "I")

        val nameIdx = cp.utf8("getValue")
        val descIdx = cp.utf8("(Ljava/lang/Object;)I")
        // aload_0, getfield test/FieldAccess.value:I, ireturn
        val code = byteArrayOf(
            0x2A,
            0xB4.toByte(), (fieldRef shr 8).toByte(), fieldRef.toByte(),
            0xAC.toByte(),
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "getValue" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Instruction.GetElementPtr },
            "getfield should produce a GEP instruction")
        assertTrue(instructions.any { it is Instruction.Load },
            "getfield should produce a Load instruction")
    }

    @Test
    fun `constructor compiles with mangled name`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MyClass")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("<init>")
        val descIdx = cp.utf8("()V")
        // aload_0, return
        val code = byteArrayOf(0x2A, 0xB1.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val module = compile(classBytes)
        assertTrue(module.functions.any { it.name == "test_MyClass_init" },
            "Constructor should compile with mangled name test_MyClass_init, got: ${module.functions.map { it.name }}")
    }

    @Test
    fun `division produces div instruction`() {
        val classBytes = buildClass("test/Div", listOf(
            MethodSpec("div", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x6C, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "div" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Instruction.SDiv }, "Expected SDiv instruction")
    }

    @Test
    fun `bitwise and produces and instruction`() {
        val classBytes = buildClass("test/BitAnd", listOf(
            MethodSpec("bitand", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x7E, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "bitand" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Instruction.And }, "Expected And instruction")
    }

    @Test
    fun `bitwise or produces or instruction`() {
        val classBytes = buildClass("test/BitOr", listOf(
            MethodSpec("bitor", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x80.toByte(), 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "bitor" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Instruction.Or }, "Expected Or instruction")
    }

    @Test
    fun `float return type maps to F32`() {
        val classBytes = buildClass("test/FloatType", listOf(
            MethodSpec("identity", "(F)F",
                byteArrayOf(0x22, 0xAE.toByte()), // fload_0, freturn
                maxStack = 1, maxLocals = 1),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "identity" }
        assertEquals(Type.F32, fn.returnType)
        assertEquals(1, fn.params.size)
        assertEquals(Type.F32, fn.params[0].type)
    }

    @Test
    fun `double return type maps to F64`() {
        val classBytes = buildClass("test/DoubleType", listOf(
            MethodSpec("identity", "(D)D",
                byteArrayOf(0x26, 0xAF.toByte()), // dload_0, dreturn
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "identity" }
        assertEquals(Type.F64, fn.returnType)
        assertEquals(1, fn.params.size)
        assertEquals(Type.F64, fn.params[0].type)
    }
}

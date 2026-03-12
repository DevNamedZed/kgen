package org.kgen.unmanaged.lib

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.RuntimeCompiler
import org.kgen.target.jvm.*

class StdlibProviderTest {

    @Test
    fun generateProducesValidModule() {
        val module = StdlibProvider.generate(Target.x86_64())
        assertTrue(module.functions.isNotEmpty(), "Stdlib should have functions")
        assertTrue(module.globals.isNotEmpty(), "Stdlib should have format string globals")
    }

    @Test
    fun containsPrintlnFunctions() {
        val module = StdlibProvider.generate(Target.x86_64())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("kgen_println_str" in names)
        assertTrue("kgen_println_int" in names)
        assertTrue("kgen_println_long" in names)
        assertTrue("kgen_println_double" in names)
        assertTrue("kgen_println_void" in names)
    }

    @Test
    fun containsStringOps() {
        val module = StdlibProvider.generate(Target.x86_64())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("kgen_string_length" in names)
        assertTrue("kgen_string_equals" in names)
        assertTrue("kgen_string_charAt" in names)
    }

    @Test
    fun containsMathOps() {
        val module = StdlibProvider.generate(Target.x86_64())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("kgen_math_abs_int" in names)
        assertTrue("kgen_math_abs_long" in names)
        assertTrue("kgen_math_min_int" in names)
        assertTrue("kgen_math_max_int" in names)
        assertTrue("kgen_math_sqrt" in names)
        assertTrue("kgen_math_pow" in names)
    }

    @Test
    fun containsStrConcatHelpers() {
        val module = StdlibProvider.generate(Target.x86_64())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("kgen_strconcat_begin" in names)
        assertTrue("kgen_strconcat_str" in names)
        assertTrue("kgen_strconcat_int" in names)
        assertTrue("kgen_strconcat_long" in names)
        assertTrue("kgen_strconcat_finish" in names)
    }

    @Test
    fun containsToStringFunctions() {
        val module = StdlibProvider.generate(Target.x86_64())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("kgen_int_to_string" in names)
        assertTrue("kgen_long_to_string" in names)
    }

    @Test
    fun nativeNameMapsCorrectly() {
        assertEquals("kgen_println_str",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(Ljava/lang/String;)V"))
        assertEquals("kgen_println_int",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(I)V"))
        assertEquals("kgen_string_length",
            StdlibProvider.nativeName("java/lang/String", "length", "()I"))
        assertEquals("kgen_math_sqrt",
            StdlibProvider.nativeName("java/lang/Math", "sqrt", "(D)D"))
        assertNull(StdlibProvider.nativeName("com/example/Foo", "bar", "()V"))
    }

    @Test
    fun systemOutPrintlnCompilesFromBytecode() {
        // Build a class that calls System.out.println("hello")
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/Hello")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // System.out field ref
        val sysOutRef = cp.fieldRef("java/lang/System", "out", "Ljava/io/PrintStream;")
        // String constant
        val strIdx = cp.string("hello")
        // PrintStream.println(String)V
        val printlnRef = cp.methodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V")

        val nameIdx = cp.utf8("main")
        val descIdx = cp.utf8("()V")

        // getstatic System.out, ldc "hello", invokevirtual println, return
        val code = byteArrayOf(
            0xB2.toByte(), (sysOutRef shr 8).toByte(), sysOutRef.toByte(),  // getstatic System.out
            0x12, strIdx.toByte(),                                           // ldc "hello"
            0xB6.toByte(), (printlnRef shr 8).toByte(), printlnRef.toByte(), // invokevirtual println
            0xB1.toByte(),                                                   // return
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

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.find { it.name == "main" }
        assertNotNull(fn, "Should have 'main' function")

        // Should call kgen_println_str instead of PrintStream.println
        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_println_str" },
            "Should call kgen_println_str, got calls: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun mathAbsCompilesFromBytecode() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/MathTest")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val absRef = cp.methodRef("java/lang/Math", "abs", "(I)I")

        val nameIdx = cp.utf8("absVal")
        val descIdx = cp.utf8("(I)I")

        // iload_0, invokestatic Math.abs, ireturn
        val code = byteArrayOf(
            0x1A.toByte(),                                        // iload_0
            0xB8.toByte(), (absRef shr 8).toByte(), absRef.toByte(), // invokestatic Math.abs
            0xAC.toByte(),                                        // ireturn
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

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.find { it.name == "absVal" }
        assertNotNull(fn)

        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_math_abs_int" },
            "Should call kgen_math_abs_int")
    }

    @Test
    fun stringLengthCompilesFromBytecode() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/StrLen")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val lengthRef = cp.methodRef("java/lang/String", "length", "()I")

        val nameIdx = cp.utf8("len")
        val descIdx = cp.utf8("(Ljava/lang/String;)I")

        // aload_0, invokevirtual String.length, ireturn
        val code = byteArrayOf(
            0x2A.toByte(),                                              // aload_0
            0xB6.toByte(), (lengthRef shr 8).toByte(), lengthRef.toByte(), // invokevirtual String.length
            0xAC.toByte(),                                              // ireturn
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

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.find { it.name == "len" }
        assertNotNull(fn)

        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_string_length" },
            "Should call kgen_string_length")
    }

    @Test
    fun stdlibCompilesNatively() {
        // Verify the entire stdlib compiles to native code
        val module = StdlibProvider.generate(Target.x86_64())
        val gen = org.kgen.target.x86.codegen.X86CodeGenerator()
        val code = gen.generateCode(module)
        assertTrue(code.textBytes.isNotEmpty(), "Stdlib should produce native code")
    }

    @Test
    fun nativeNameReturnsNullForUnknownMethods() {
        assertNull(StdlibProvider.nativeName("com/example/Foo", "bar", "()V"))
        assertNull(StdlibProvider.nativeName("java/util/List", "add", "(Ljava/lang/Object;)Z"))
        assertNull(StdlibProvider.nativeName("java/lang/Object", "hashCode", "()I"))
    }

    @Test
    fun nativeNameHandlesAllPrintlnOverloads() {
        assertEquals("kgen_println_str",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(Ljava/lang/String;)V"))
        assertEquals("kgen_println_int",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(I)V"))
        assertEquals("kgen_println_long",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(J)V"))
        assertEquals("kgen_println_double",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "(D)V"))
        assertEquals("kgen_println_void",
            StdlibProvider.nativeName("java/io/PrintStream", "println", "()V"))
    }

    @Test
    fun nativeNameHandlesAllMathMethods() {
        assertEquals("kgen_math_abs_int",
            StdlibProvider.nativeName("java/lang/Math", "abs", "(I)I"))
        assertEquals("kgen_math_abs_long",
            StdlibProvider.nativeName("java/lang/Math", "abs", "(J)J"))
        assertEquals("kgen_math_abs_double",
            StdlibProvider.nativeName("java/lang/Math", "abs", "(D)D"))
        assertEquals("kgen_math_min_int",
            StdlibProvider.nativeName("java/lang/Math", "min", "(II)I"))
        assertEquals("kgen_math_max_int",
            StdlibProvider.nativeName("java/lang/Math", "max", "(II)I"))
        assertEquals("kgen_math_sqrt",
            StdlibProvider.nativeName("java/lang/Math", "sqrt", "(D)D"))
        assertEquals("kgen_math_pow",
            StdlibProvider.nativeName("java/lang/Math", "pow", "(DD)D"))
    }

    @Test
    fun generateProducesCorrectNumberOfFunctions() {
        val module = StdlibProvider.generate(Target.x86_64())
        // Should have at least println_str, println_int, println_long, println_double,
        // println_void, print_str, print_int, print_long, string_length, string_equals,
        // string_charAt, math_abs_int, math_abs_long, math_min_int, math_max_int,
        // math_sqrt, math_pow, int_to_string, long_to_string, strconcat_*
        assertTrue(module.functions.size >= 15,
            "Should have at least 15 stdlib functions, got ${module.functions.size}")
    }

    @Test
    fun generateProducesCorrectNumberOfGlobals() {
        val module = StdlibProvider.generate(Target.x86_64())
        // Format string globals
        assertTrue(module.globals.isNotEmpty(), "Should have format string globals")
        assertTrue(module.globals.size >= 5,
            "Should have at least 5 format string globals, got ${module.globals.size}")
    }

    @Test
    fun printlnStrCallsPuts() {
        val module = StdlibProvider.generate(Target.x86_64())
        val fn = module.functions.find { it.name == "kgen_println_str" }
        assertNotNull(fn, "Should have kgen_println_str function")
        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        val callNames = calls.map { it.function }.mapNotNull {
            when (it) {
                is FunctionRef -> it.name
                is GlobalRef -> it.name
                else -> null
            }
        }
        assertTrue("puts" in callNames,
            "kgen_println_str should call puts, got: $callNames")
    }

    @Test
    fun printlnIntCallsPrintf() {
        val module = StdlibProvider.generate(Target.x86_64())
        val fn = module.functions.find { it.name == "kgen_println_int" }
        assertNotNull(fn, "Should have kgen_println_int function")
        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        val callNames = calls.map { it.function }.mapNotNull {
            when (it) {
                is FunctionRef -> it.name
                is GlobalRef -> it.name
                else -> null
            }
        }
        assertTrue("printf" in callNames,
            "kgen_println_int should call printf, got: $callNames")
    }

    @Test
    fun mathSqrtCallsSqrt() {
        val module = StdlibProvider.generate(Target.x86_64())
        val fn = module.functions.find { it.name == "kgen_math_sqrt" }
        assertNotNull(fn, "Should have kgen_math_sqrt function")
        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        val callNames = calls.map { it.function }.mapNotNull {
            when (it) {
                is FunctionRef -> it.name
                is GlobalRef -> it.name
                else -> null
            }
        }
        assertTrue("sqrt" in callNames,
            "kgen_math_sqrt should call sqrt, got: $callNames")
    }

    @Test
    fun strconcatBeginCallsMalloc() {
        val module = StdlibProvider.generate(Target.x86_64())
        val fn = module.functions.find { it.name == "kgen_strconcat_begin" }
        assertNotNull(fn, "Should have kgen_strconcat_begin function")
        val calls = fn!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        val callNames = calls.map { it.function }.mapNotNull {
            when (it) {
                is FunctionRef -> it.name
                is GlobalRef -> it.name
                else -> null
            }
        }
        assertTrue("malloc" in callNames,
            "kgen_strconcat_begin should call malloc, got: $callNames")
    }

    @Test
    fun allGeneratedFunctionsHaveCorrectParameterTypes() {
        val module = StdlibProvider.generate(Target.x86_64())
        val printlnStr = module.functions.find { it.name == "kgen_println_str" }!!
        assertEquals(1, printlnStr.params.size)
        // In @KgenNative, pointers are represented as long (I64)
        assertEquals(Type.I64, printlnStr.params[0].type)

        val printlnInt = module.functions.find { it.name == "kgen_println_int" }!!
        assertEquals(1, printlnInt.params.size)
        assertEquals(Type.I32, printlnInt.params[0].type)

        val mathSqrt = module.functions.find { it.name == "kgen_math_sqrt" }!!
        assertEquals(1, mathSqrt.params.size)
        assertEquals(Type.F64, mathSqrt.params[0].type)
    }

    @Test
    fun nativeCompilerIncludesStdlib() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("test/WithStdlib")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val absRef = cp.methodRef("java/lang/Math", "abs", "(I)I")
        val nameIdx = cp.utf8("test")
        val descIdx = cp.utf8("(I)I")

        val code = byteArrayOf(
            0x1A.toByte(),
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

        // Should compile to object file with stdlib functions
        val obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(classBytes))
        val symbols = obj.symbols.map { it.name }.toSet()
        assertTrue("kgen_math_abs_int" in symbols,
            "Object file should contain stdlib symbol kgen_math_abs_int, got: ${obj.symbols.map { it.name }.take(20)}")
    }
}

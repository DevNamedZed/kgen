package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

class BytecodeToIrLoweringTest {

    private data class MethodSpec(
        val name: String,
        val descriptor: String,
        val bytecode: ByteArray,
        val maxStack: Int,
        val maxLocals: Int,
    )

    private fun buildClass(className: String, methods: List<MethodSpec>): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry(className)
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val jvmMethods = methods.map { spec ->
            val nameIdx = cp.utf8(spec.name)
            val descIdx = cp.utf8(spec.descriptor)
            val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = spec.maxStack, maxLocals = spec.maxLocals, code = spec.bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            ))
            MethodInfo(
                accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
                nameIndex = nameIdx, descriptorIndex = descIdx,
                attributes = listOf(codeAttr),
            )
        }

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
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
    fun `simple add function produces add instruction`() {
        val classBytes = buildClass("org/kgen/test/Arith", listOf(
            MethodSpec("add", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "add" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Add }, "Expected Add instruction")
    }

    @Test
    fun `multiply function produces mul instruction`() {
        val classBytes = buildClass("org/kgen/test/Arith", listOf(
            MethodSpec("mul", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "mul" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Mul }, "Expected Mul instruction")
    }

    @Test
    fun `subtraction function produces sub instruction`() {
        val classBytes = buildClass("org/kgen/test/Arith", listOf(
            MethodSpec("sub", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x64, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "sub" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Sub }, "Expected Sub instruction")
    }

    @Test
    fun `negation function produces neg instruction`() {
        val classBytes = buildClass("org/kgen/test/Arith", listOf(
            MethodSpec("neg", "(I)I",
                byteArrayOf(0x1A, 0x74, 0xAC.toByte()),
                maxStack = 1, maxLocals = 1),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "neg" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Neg }, "Expected Neg instruction")
    }

    @Test
    fun `constant return compiles correctly`() {
        val classBytes = buildClass("org/kgen/test/Const", listOf(
            MethodSpec("answer", "()I",
                byteArrayOf(0x10, 42, 0xAC.toByte()),
                maxStack = 1, maxLocals = 0),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "answer" }
        val instructions = allInstructions(fn)
        val ret = instructions.filterIsInstance<Ret>().first()
        val retVal = ret.value
        assertTrue(retVal is Constant.I32 && retVal.value == 42,
            "Expected return of constant 42, got $retVal")
    }

    @Test
    fun `void function compiles to void return type`() {
        val classBytes = buildClass("org/kgen/test/Void", listOf(
            MethodSpec("noop", "()V",
                byteArrayOf(0xB1.toByte()),
                maxStack = 0, maxLocals = 0),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "noop" }
        assertEquals(Type.Void, fn.returnType)
    }

    @Test
    fun `if-else branch produces condBr`() {
        // max(II)I: iload_0, iload_1, if_icmple +5 -> offset 7, iload_0, ireturn, iload_1, ireturn
        val classBytes = buildClass("org/kgen/test/Branch", listOf(
            MethodSpec("max", "(II)I",
                byteArrayOf(
                    0x1A, 0x1B,                                 // iload_0, iload_1
                    0xA4.toByte(), 0x00, 0x05,                  // if_icmple +5 -> offset 7
                    0x1A, 0xAC.toByte(),                        // iload_0, ireturn
                    0x1B, 0xAC.toByte(),                        // iload_1, ireturn
                ),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "max" }
        assertTrue(fn.blocks.size >= 2, "Expected multiple blocks, got ${fn.blocks.size}")
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is CondBr }, "Expected CondBr instruction")
    }

    @Test
    fun `loop produces back edge`() {
        // Iterative sum: int sum = 0; for (int i = 0; i < n; i++) sum += i; return sum;
        val classBytes = buildClass("org/kgen/test/Loop", listOf(
            MethodSpec("sum", "(I)I",
                byteArrayOf(
                    0x03, 0x3C,                                     // 0: iconst_0, istore_1 (sum=0)
                    0x03, 0x3D,                                     // 2: iconst_0, istore_2 (i=0)
                    // loop: (offset 4)
                    0x1C,                                           // 4: iload_2 (i)
                    0x1A,                                           // 5: iload_0 (n)
                    0xA2.toByte(), 0x00, 0x0D,                      // 6: if_icmpge +13 -> end (offset 19)
                    0x1B, 0x1C, 0x60, 0x3C,                         // 9: iload_1, iload_2, iadd, istore_1 (sum+=i)
                    0x84.toByte(), 0x02, 0x01,                      // 13: iinc 2, 1 (i++)
                    0xA7.toByte(), 0xFF.toByte(), 0xF1.toByte(),    // 16: goto -15 -> loop (offset 4)
                    // end: (offset 19)
                    0x1B,                                           // 19: iload_1 (sum)
                    0xAC.toByte(),                                  // 20: ireturn
                ),
                maxStack = 2, maxLocals = 3),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "sum" }
        assertTrue(fn.blocks.size >= 3, "Expected at least 3 blocks for loop, got ${fn.blocks.size}")
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Phi }, "Expected Phi nodes after Mem2Reg")
    }

    @Test
    fun `multiple methods all compiled`() {
        val classBytes = buildClass("org/kgen/test/Multi", listOf(
            MethodSpec("foo", "()I",
                byteArrayOf(0x03, 0xAC.toByte()),  // iconst_0, ireturn
                maxStack = 1, maxLocals = 0),
            MethodSpec("bar", "()I",
                byteArrayOf(0x04, 0xAC.toByte()),  // iconst_1, ireturn
                maxStack = 1, maxLocals = 0),
            MethodSpec("baz", "()I",
                byteArrayOf(0x05, 0xAC.toByte()),  // iconst_2, ireturn
                maxStack = 1, maxLocals = 0),
        ))
        val module = compile(classBytes)
        val names = module.functions.map { it.name }.toSet()
        assertTrue("foo" in names, "Expected foo")
        assertTrue("bar" in names, "Expected bar")
        assertTrue("baz" in names, "Expected baz")
        assertEquals(3, module.functions.size)
    }

    @Test
    fun `parameter count matches descriptor`() {
        val classBytes = buildClass("org/kgen/test/Params", listOf(
            MethodSpec("add", "(II)I",
                byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()),
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "add" }
        assertEquals(2, fn.params.size, "Expected 2 parameters")
        assertTrue(fn.params.all { it.type == Type.I32 }, "Expected all params to be I32")
    }

    @Test
    fun `long return type maps to I64`() {
        val classBytes = buildClass("org/kgen/test/LongType", listOf(
            MethodSpec("identity", "(J)J",
                byteArrayOf(0x1E, 0xAD.toByte()),  // lload_0, lreturn
                maxStack = 2, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "identity" }
        assertEquals(Type.I64, fn.returnType)
        assertEquals(1, fn.params.size, "Expected 1 parameter (long)")
        assertEquals(Type.I64, fn.params[0].type)
    }

    @Test
    fun `iinc compiles to add of constant`() {
        val classBytes = buildClass("org/kgen/test/Iinc", listOf(
            MethodSpec("inc", "(I)I",
                byteArrayOf(
                    0x1A,                           // iload_0
                    0x3C,                           // istore_1
                    0x84.toByte(), 0x01, 0x05,      // iinc 1, 5
                    0x1B,                           // iload_1
                    0xAC.toByte(),                  // ireturn
                ),
                maxStack = 1, maxLocals = 2),
        ))
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "inc" }
        val instructions = allInstructions(fn)
        val adds = instructions.filterIsInstance<Add>()
        assertTrue(adds.isNotEmpty(), "Expected Add instruction from iinc")
        val hasConstant5 = adds.any { add ->
            (add.rhs is Constant.I32 && (add.rhs as Constant.I32).value == 5) ||
            (add.lhs is Constant.I32 && (add.lhs as Constant.I32).value == 5)
        }
        assertTrue(hasConstant5, "Expected Add with constant 5 from iinc")
    }

    @Test
    fun `try-catch lowers to invoke and landing pad`() {
        // Build a class with an exception table:
        // static int tryCatch() {
        //   try { mayThrow(); return 0; } catch (Exception e) { return 1; }
        // }
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/TryCatch")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val exceptionClass = cp.classEntry("java/lang/Exception")

        // mayThrow is a static method we declare externally
        val mayThrowRef = cp.methodRef("org/kgen/test/TryCatch", "mayThrow", "()V")

        // Bytecodes:
        // 0: invokestatic #mayThrowRef  (3 bytes: B8, high, low)
        // 3: iconst_0                   (1 byte: 03)
        // 4: ireturn                    (1 byte: AC)
        // 5: astore_0                   (1 byte: 4B) — handler start
        // 6: iconst_1                   (1 byte: 04)
        // 7: ireturn                    (1 byte: AC)
        val bytecode = byteArrayOf(
            0xB8.toByte(), (mayThrowRef shr 8).toByte(), (mayThrowRef and 0xFF).toByte(), // invokestatic
            0x03,                       // iconst_0
            0xAC.toByte(),              // ireturn
            0x4B,                       // astore_0 (catch handler)
            0x04,                       // iconst_1
            0xAC.toByte(),              // ireturn
        )

        val exceptionTable = listOf(
            ExceptionEntry(startPc = 0, endPc = 5, handlerPc = 5, catchType = exceptionClass)
        )

        val nameIdx = cp.utf8("tryCatch")
        val descIdx = cp.utf8("()I")
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 1, code = bytecode,
            exceptionTable = exceptionTable, attributes = emptyList(),
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

        val module = RuntimeCompiler(org.kgen.ir.target.Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "tryCatch" }
        val instructions = allInstructions(fn)

        // Should have Invoke (not just Call) for the try-region call
        assertTrue(instructions.any { it is Invoke },
            "Expected Invoke instruction for call inside try block")

        // Should have a LandingPad in a catch block
        assertTrue(instructions.any { it is LandingPad },
            "Expected LandingPad instruction for catch handler")
    }

    @Test
    fun `no exception table produces regular call`() {
        // Same bytecode but without exception table — should use Call not Invoke
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/NoTry")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val mayThrowRef = cp.methodRef("org/kgen/test/NoTry", "doStuff", "()V")

        val bytecode = byteArrayOf(
            0xB8.toByte(), (mayThrowRef shr 8).toByte(), (mayThrowRef and 0xFF).toByte(),
            0x03,                       // iconst_0
            0xAC.toByte(),              // ireturn
        )

        val nameIdx = cp.utf8("noTry")
        val descIdx = cp.utf8("()I")
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = bytecode,
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

        val module = RuntimeCompiler(org.kgen.ir.target.Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "noTry" }
        val instructions = allInstructions(fn)

        // Should have Call, NOT Invoke (no exception table)
        assertTrue(instructions.any { it is Call },
            "Expected regular Call instruction without try block")
        assertFalse(instructions.any { it is Invoke },
            "Should NOT have Invoke without try block")
    }
}

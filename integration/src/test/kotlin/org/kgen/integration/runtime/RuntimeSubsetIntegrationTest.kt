package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.jit.JitEngine
import org.kgen.target.jvm.*
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout.*

/**
 * End-to-end tests: Java classfile bytes → RuntimeCompiler → JitEngine → native execution.
 */
@EnabledOnOs(OS.LINUX, OS.WINDOWS)
class RuntimeSubsetIntegrationTest {

    private fun callI32(jit: JitEngine, name: String, vararg args: Int): Int {
        val descriptor = FunctionDescriptor.of(
            JAVA_INT, *Array(args.size) { JAVA_INT }
        )
        val handle = jit.handle(name, descriptor)
        return when (args.size) {
            0 -> handle.invoke() as Int
            1 -> handle.invoke(args[0]) as Int
            2 -> handle.invoke(args[0], args[1]) as Int
            else -> handle.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Int
        }
    }

    @Test
    fun addTwoInts() {
        val classBytes = buildAddClass()
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(30, callI32(jit, "add", 10, 20))
        }
    }

    @Test
    fun addTwoIntsVariousValues() {
        val classBytes = buildAddClass()
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(0, callI32(jit, "add", 0, 0))
            assertEquals(-1, callI32(jit, "add", -3, 2))
            assertEquals(200, callI32(jit, "add", 100, 100))
        }
    }

    @Test
    fun multiplyInts() {
        val classBytes = buildMultiplyClass()
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(12, callI32(jit, "multiply", 3, 4))
            assertEquals(0, callI32(jit, "multiply", 0, 99))
            assertEquals(-15, callI32(jit, "multiply", -3, 5))
        }
    }

    @Test
    fun maxFunction() {
        val classBytes = buildMaxClass()
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(5, callI32(jit, "max", 3, 5))
            assertEquals(5, callI32(jit, "max", 5, 3))
            assertEquals(3, callI32(jit, "max", 3, 3))
        }
    }

    @Test
    fun runtimeMethodsCallableFromJitCode() {
        val classBytes = buildAddClass()
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)

            val sym = jit.lookup("add")
            assertNotNull(sym)
            assertTrue(sym!!.address != 0L)
        }
    }

    @Test
    fun multipleRuntimeClassesSameEngine() {
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(buildAddClass())
            jit.addRuntimeClass(buildMultiplyClass())

            assertEquals(30, callI32(jit, "add", 10, 20))
            assertEquals(12, callI32(jit, "multiply", 3, 4))
        }
    }

    @Test
    fun jitCodeCallsRuntimeMethod() {
        // Build a runtime method, then build IR that calls it
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(buildAddClass())

            // Build IR module that calls the runtime "add" method
            val ir = org.kgen.ir.build.IrBuilder("caller", org.kgen.ir.target.Target.x86_64())
            ir.declareFunction("add", listOf(
                org.kgen.ir.Param("a", org.kgen.ir.Type.I32),
                org.kgen.ir.Param("b", org.kgen.ir.Type.I32),
            ), org.kgen.ir.Type.I32)

            ir.createFunction("addThree", listOf(
                org.kgen.ir.Param("x", org.kgen.ir.Type.I32),
                org.kgen.ir.Param("y", org.kgen.ir.Type.I32),
                org.kgen.ir.Param("z", org.kgen.ir.Type.I32),
            ), org.kgen.ir.Type.I32)
            ir.appendBlock("entry")
            val x = org.kgen.ir.Parameter("x", org.kgen.ir.Type.I32, 0)
            val y = org.kgen.ir.Parameter("y", org.kgen.ir.Type.I32, 1)
            val z = org.kgen.ir.Parameter("z", org.kgen.ir.Type.I32, 2)
            val sum1 = ir.call("add", listOf(x, y), org.kgen.ir.Type.I32)!!
            val sum2 = ir.call("add", listOf(sum1, z), org.kgen.ir.Type.I32)!!
            ir.ret(sum2)
            ir.finalizeFunction()

            jit.addModule(ir.build())

            // Verify symbols are available and resolvable
            assertNotNull(jit.lookup("add"))
            assertNotNull(jit.lookup("addThree"))
        }
    }

    @Test
    fun loopInRuntimeMethod() {
        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(buildSumLoopClass())
            assertEquals(10, callI32(jit, "sumLoop", 5))
            assertEquals(45, callI32(jit, "sumLoop", 10))
        }
    }

    // ---- Classfile builders ----

    private fun buildAddClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/AddOps")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("add")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // iload_0, iload_1, iadd, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())

        val method = buildStaticMethod(
            methodNameIdx, methodDescIdx, codeNameIdx,
            bytecode, maxStack = 2, maxLocals = 2,
        )

        return writeClassFile(thisClassIdx, superClassIdx, cp, listOf(method))
    }

    private fun buildMultiplyClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/MulOps")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("multiply")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // iload_0, iload_1, imul, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x68, 0xAC.toByte())

        val method = buildStaticMethod(
            methodNameIdx, methodDescIdx, codeNameIdx,
            bytecode, maxStack = 2, maxLocals = 2,
        )

        return writeClassFile(thisClassIdx, superClassIdx, cp, listOf(method))
    }

    private fun buildMaxClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/MaxOps")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("max")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // if (a > b) return a; else return b;
        // 0: iload_0
        // 1: iload_1
        // 2: if_icmple -> 7
        // 5: iload_0
        // 6: ireturn
        // 7: iload_1
        // 8: ireturn
        val bytecode = byteArrayOf(
            0x1A, 0x1B,
            0xA4.toByte(), 0x00, 0x05, // if_icmple +5 → pc=7
            0x1A, 0xAC.toByte(),       // iload_0, ireturn
            0x1B, 0xAC.toByte(),       // iload_1, ireturn
        )

        val method = buildStaticMethod(
            methodNameIdx, methodDescIdx, codeNameIdx,
            bytecode, maxStack = 2, maxLocals = 2,
        )

        return writeClassFile(thisClassIdx, superClassIdx, cp, listOf(method))
    }

    private fun buildSumLoopClass(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/SumLoop")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("sumLoop")
        val methodDescIdx = cp.utf8("(I)I")
        val codeNameIdx = cp.utf8("Code")

        val bytecode = byteArrayOf(
            0x03, 0x3C,                                     // iconst_0, istore_1
            0x03, 0x3D,                                     // iconst_0, istore_2
            0x1C, 0x1A,                                     // iload_2, iload_0
            0xA2.toByte(), 0x00, 0x0D,                      // if_icmpge +13 -> 19
            0x1B, 0x1C, 0x60, 0x3C,                         // iload_1, iload_2, iadd, istore_1
            0x84.toByte(), 0x02, 0x01,                      // iinc 2, 1
            0xA7.toByte(), 0xFF.toByte(), 0xF4.toByte(),    // goto -12 -> 4
            0x1B,                                           // iload_1
            0xAC.toByte(),                                  // ireturn
        )

        val method = buildStaticMethod(
            methodNameIdx, methodDescIdx, codeNameIdx,
            bytecode, maxStack = 2, maxLocals = 3,
        )
        return writeClassFile(thisClassIdx, superClassIdx, cp, listOf(method))
    }

    // ---- Classfile helpers ----

    private fun buildStaticMethod(
        nameIdx: Int, descIdx: Int, codeNameIdx: Int,
        bytecode: ByteArray, maxStack: Int, maxLocals: Int,
    ): MethodInfo {
        val codeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(
                maxStack = maxStack, maxLocals = maxLocals, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )
        return MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
    }

    private fun writeClassFile(
        thisClassIdx: Int, superClassIdx: Int,
        cp: ConstantPoolBuilder, methods: List<MethodInfo>,
    ): ByteArray {
        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = methods, attributes = emptyList(),
        )
        return JvmClassWriter.write(cf)
    }

}

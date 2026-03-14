package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Tests for exception handling support in BytecodeToIrLowering.
 *
 * athrow is lowered to a call to kgen_throw (which calls _Unwind_RaiseException),
 * followed by an unreachable instruction.
 */
class ExceptionHandlingTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildClass(block: ClassFileBuilder.() -> Unit): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/ExnTest").apply(block).build()
        )
    }

    @Test
    fun `athrow compiles to kgen_throw call`() {
        val classBytes = buildClass {
            method("abort", "(Ljava/lang/Object;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aload(0)
                code.athrow()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "abort" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Call && (it as Call).function.let { f ->
            f is GlobalRef && f.name == "kgen_throw"
        }}, "Expected call to kgen_throw from athrow, got: ${instructions.map { it::class.simpleName }}")
        assertTrue(instructions.any { it is Unreachable },
            "Expected Unreachable after kgen_throw")
    }

    @Test
    fun `athrow declares kgen_throw as external function`() {
        val classBytes = buildClass {
            method("abort", "(Ljava/lang/Object;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aload(0)
                code.athrow()
            }
        }

        val module = compile(classBytes)
        val throwFn = module.functions.firstOrNull { it.name == "kgen_throw" }
        assertNotNull(throwFn, "Module should declare kgen_throw")
        assertTrue(throwFn!!.blocks.isEmpty(), "kgen_throw should be an external declaration (no body)")
    }

    @Test
    fun `athrow after condition compiles correctly`() {
        val classBytes = buildClass {
            method("check", "(I)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iconst(0)
                code.ifIcmpge("ok")
                code.aconstNull()
                code.athrow()
                code.label("ok")
                code.return_()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "check" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Call },
            "Expected call (kgen_throw) in throw branch")
        assertTrue(instructions.any { it is Ret },
            "Expected Ret in ok branch")
    }

    @Test
    fun `athrow compiles to native object`() {
        val classBytes = buildClass {
            method("abort", "(Ljava/lang/Object;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aload(0)
                code.athrow()
            }
            method("main", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(0)
                code.ireturn()
            }
        }

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "abort" })
        assertTrue(obj.symbols.any { it.name == "main" })
    }

    @Test
    fun `guard function with athrow on invalid input`() {
        val classBytes = buildClass {
            method("safeDivide", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // if (b == 0) throw null
                code.iload(1)
                code.iconst(0)
                code.ifIcmpne("safe")
                code.aconstNull()
                code.athrow()
                code.label("safe")
                code.iload(0)
                code.iload(1)
                code.idiv()
                code.ireturn()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "safeDivide" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Call },
            "Expected call (kgen_throw) in throw branch")
        assertTrue(instructions.any { it is SDiv || it is UDiv })
    }

    @Test
    fun `SubsetValidator allows athrow`() {
        val cf = ClassFileBuilder("org/kgen/test/ExnValidation").apply {
            method("fail", "(Ljava/lang/Object;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aload(0)
                code.athrow()
            }
        }.build()

        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "athrow should be allowed: $errors")
    }

    @Test
    fun `SubsetValidator allows invokeinterface`() {
        // Build raw classfile with invokeinterface bytecode
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/IfaceTest")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val ifaceMethod = cp.interfaceMethodRef("java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I")
        val nameIdx = cp.utf8("cmp")
        val descIdx = cp.utf8("(Ljava/lang/Comparable;Ljava/lang/Object;)I")

        // aload_0, aload_1, invokeinterface #idx 2 0, ireturn
        val bytecode = byteArrayOf(
            0x2A,             // aload_0
            0x2B,             // aload_1
            0xB9.toByte(),    // invokeinterface
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            0x02,             // count
            0x00,             // zero
            0xAC.toByte(),    // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, nameIdx, descIdx, listOf(codeAttr))

        val cf = ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList())

        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "invokeinterface should be allowed: $errors")
    }
}

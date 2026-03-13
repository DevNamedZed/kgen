package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Tests for interface dispatch (invokeinterface bytecode) support.
 *
 * In kgen native compilation, invokeinterface is lowered to a direct call
 * with the interface method name mangled as InterfaceName_methodName.
 * The object reference is passed as the first argument.
 */
class InterfaceDispatchTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildInterfaceCallClass(
        ifaceName: String,
        methodName: String,
        methodDesc: String,
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/IfaceUser")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val ifaceMethod = cp.interfaceMethodRef(ifaceName, methodName, methodDesc)

        // For compareTo(Object)I: receiver + 1 arg = count 2
        val paramCount = methodDesc.substring(1, methodDesc.indexOf(')')).let { paramStr ->
            var count = 1 // receiver
            var i = 0
            while (i < paramStr.length) {
                when (paramStr[i]) {
                    'J', 'D' -> { count += 2; i++ }
                    'L' -> { count++; i = paramStr.indexOf(';', i) + 1 }
                    '[' -> { count++; i++; if (i < paramStr.length && paramStr[i] == 'L') i = paramStr.indexOf(';', i) + 1 else i++ }
                    else -> { count++; i++ }
                }
            }
            count
        }

        // callIface(Object receiver, Object arg) -> int
        // Loads both receiver and arg, calls interface method
        val bytecode = byteArrayOf(
            0x2A,             // aload_0 (receiver)
            0x2B,             // aload_1 (arg)
            0xB9.toByte(),    // invokeinterface
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            paramCount.toByte(),
            0x00,             // zero
            0xAC.toByte(),    // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(3, 2, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            cp.utf8("callIface"), cp.utf8("(Ljava/lang/Object;Ljava/lang/Object;)I"),
            listOf(codeAttr))

        return JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))
    }

    @Test
    fun `invokeinterface produces Call with mangled name`() {
        val classBytes = buildInterfaceCallClass(
            "java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I"
        )

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "callIface" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "java_lang_Comparable_compareTo" },
            "Expected call to java_lang_Comparable_compareTo, got: ${calls.map { (it.function as? GlobalRef)?.name }}")
    }

    @Test
    fun `invokeinterface passes this as first argument`() {
        val classBytes = buildInterfaceCallClass(
            "java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I"
        )

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "callIface" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        val call = calls.first { (it.function as? GlobalRef)?.name == "java_lang_Comparable_compareTo" }
        // Should have 2 args: this + the Object parameter
        assertEquals(2, call.args.size, "Expected 2 args (this + param)")
    }

    @Test
    fun `invokeinterface void method produces call without result`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/IfaceVoid")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val ifaceMethod = cp.interfaceMethodRef("java/lang/Runnable", "run", "()V")

        val bytecode = byteArrayOf(
            0x2A,             // aload_0
            0xB9.toByte(),    // invokeinterface
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            0x01,             // count
            0x00,             // zero
            0xB1.toByte(),    // return
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(1, 1, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, cp.utf8("runIt"), cp.utf8("(Ljava/lang/Object;)V"),
            listOf(codeAttr))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "runIt" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "java_lang_Runnable_run" })
    }

    @Test
    fun `invokeinterface compiles to native object`() {
        // Build class with both a main (for linking) and an interface call
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/IfaceNative")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val ifaceMethod = cp.interfaceMethodRef("java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I")

        // callIface method
        val callBytecode = byteArrayOf(
            0x2A, 0x2B,
            0xB9.toByte(),
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            0x02, 0x00,
            0xAC.toByte(),
        )
        val callCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, callBytecode, emptyList(), emptyList()))
        val callMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            cp.utf8("callIface"), cp.utf8("(Ljava/lang/Object;Ljava/lang/Object;)I"),
            listOf(callCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(callMethod), emptyList()))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "callIface" },
            "Expected callIface symbol in object: ${obj.symbols.map { it.name }}")
    }

    @Test
    fun `mixed interface and virtual calls compile together`() {
        // A class that uses both invokevirtual and invokeinterface
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MixedCalls")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val virtualMethod = cp.methodRef("java/lang/Object", "hashCode", "()I")
        val ifaceMethod = cp.interfaceMethodRef("java/lang/Comparable", "compareTo", "(Ljava/lang/Object;)I")

        // method: int mixedCall(Object a, Object b)
        // return a.hashCode() + ((Comparable)a).compareTo(b)
        val bytecode = byteArrayOf(
            0x2A,             // aload_0 (a)
            0xB6.toByte(),    // invokevirtual hashCode
            ((virtualMethod shr 8) and 0xFF).toByte(),
            (virtualMethod and 0xFF).toByte(),
            0x2A,             // aload_0 (a)
            0x2B,             // aload_1 (b)
            0xB9.toByte(),    // invokeinterface compareTo
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            0x02,             // count
            0x00,             // zero
            0x60,             // iadd
            0xAC.toByte(),    // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(3, 2, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            cp.utf8("mixedCall"), cp.utf8("(Ljava/lang/Object;Ljava/lang/Object;)I"),
            listOf(codeAttr))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "mixedCall" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "java_lang_Object_hashCode" })
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "java_lang_Comparable_compareTo" })
    }

    @Test
    fun `invokeinterface with multiple parameters`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MultiParam")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // Custom interface method with (int, int) -> int
        val ifaceMethod = cp.interfaceMethodRef("org/kgen/test/Adder", "add", "(II)I")

        val bytecode = byteArrayOf(
            0x2A,             // aload_0 (receiver)
            0x1B,             // iload_1
            0x1C,             // iload_2
            0xB9.toByte(),    // invokeinterface
            ((ifaceMethod shr 8) and 0xFF).toByte(),
            (ifaceMethod and 0xFF).toByte(),
            0x03,             // count (1 + 2 for int params)
            0x00,
            0xAC.toByte(),    // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(3, 3, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            cp.utf8("callAdder"), cp.utf8("(Ljava/lang/Object;II)I"),
            listOf(codeAttr))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList()))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "callAdder" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        val addCall = calls.first { (it.function as? GlobalRef)?.name == "org_kgen_test_Adder_add" }
        // Should have 3 args: this + int + int
        assertEquals(3, addCall.args.size, "Expected 3 args (this + 2 ints)")
    }
}

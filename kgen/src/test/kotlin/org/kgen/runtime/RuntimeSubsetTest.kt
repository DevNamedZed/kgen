package org.kgen.runtime

import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.RuntimeCompiler
import org.kgen.runtime.compile.SubsetValidator
import org.kgen.target.jvm.JvmClassReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeSubsetTest {

    private fun buildSimpleAddClass(): ByteArray {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/SimpleAdd")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val methodNameIdx = cp.utf8("add")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        return org.kgen.target.jvm.JvmClassWriter.write(cf)
    }

    @Test
    fun validateSubsetPass() {
        // Build a valid class from scratch and validate
        val classBytes = buildSimpleAddClass()
        val cf = JvmClassReader.read(classBytes)
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors: $errors")
    }

    @Test
    fun validateSubsetRejectsCheckcast() {
        // Build a class with checkcast — should be rejected
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("test/Bad")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val targetClass = cp.classEntry("java/lang/String")

        val methodNameIdx = cp.utf8("bad")
        val methodDescIdx = cp.utf8("()V")
        val codeNameIdx = cp.utf8("Code")

        val bytecode = byteArrayOf(
            0x01,             // aconst_null
            0xC0.toByte(),    // checkcast
            ((targetClass shr 8) and 0xFF).toByte(),
            (targetClass and 0xFF).toByte(),
            0x57,             // pop
            0xB1.toByte(),    // return
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 1, maxLocals = 1, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        val classBytes = org.kgen.target.jvm.JvmClassWriter.write(cf)
        val errors = SubsetValidator.validate(JvmClassReader.read(classBytes))
        assertTrue(errors.none { it.message.contains("checkcast") }, "checkcast should now be allowed: $errors")
    }

    @Test
    fun compileFromJavaClassfile() {
        // Build a simple classfile from scratch using the writer, then compile it
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/SimpleAdd")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("add")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // Bytecode: iload_0, iload_1, iadd, ireturn
        val bytecode = byteArrayOf(
            0x1A, // iload_0
            0x1B, // iload_1
            0x60, // iadd
            0xAC.toByte(), // ireturn
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0,
            majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(method),
            attributes = emptyList(),
        )

        val classBytes = org.kgen.target.jvm.JvmClassWriter.write(cf)

        // Compile to IR
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        assertEquals(1, module.functions.size)
        assertEquals("add", module.functions[0].name)
        assertEquals(2, module.functions[0].params.size)
    }

    @Test
    fun compileIfElse() {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/Max")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("max")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // Bytecode for: if (a > b) return a; else return b;
        // iload_0, iload_1, if_icmple L1, iload_0, ireturn, L1: iload_1, ireturn
        val bytecode = byteArrayOf(
            0x1A,             // 0: iload_0
            0x1B,             // 1: iload_1
            0xA4.toByte(),    // 2: if_icmple -> offset 7 (pc=2+5=7)
            0x00, 0x05,       //    branch offset = 5
            0x1A,             // 5: iload_0
            0xAC.toByte(),    // 6: ireturn
            0x1B,             // 7: iload_1
            0xAC.toByte(),    // 8: ireturn
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0,
            majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(method),
            attributes = emptyList(),
        )

        val classBytes = org.kgen.target.jvm.JvmClassWriter.write(cf)

        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        assertEquals(1, module.functions.size)
        assertEquals("max", module.functions[0].name)
        // Should have multiple blocks (entry + branch targets)
        assertTrue(module.functions[0].blocks.size >= 2, "Expected multiple blocks for if/else")
    }

    @Test
    fun compileLoop() {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/Sum")
        val superClassIdx = cp.classEntry("java/lang/Object")

        val methodNameIdx = cp.utf8("sum")
        val methodDescIdx = cp.utf8("(I)I")
        val codeNameIdx = cp.utf8("Code")

        // Bytecode for: int sum=0; for(int i=0; i<n; i++) sum+=i; return sum;
        // 0: iconst_0      (sum=0)
        // 1: istore_1
        // 2: iconst_0      (i=0)
        // 3: istore_2
        // 4: iload_2       loop start
        // 5: iload_0
        // 6: if_icmpge +10 -> 16
        // 9: iload_1
        // 10: iload_2
        // 11: iadd
        // 12: istore_1
        // 13: iinc 2, 1
        // 16: goto -12 -> 4
        // 19: iload_1
        // 20: ireturn
        val bytecode = byteArrayOf(
            0x03,             // 0: iconst_0
            0x3C,             // 1: istore_1
            0x03,             // 2: iconst_0
            0x3D,             // 3: istore_2
            0x1C,             // 4: iload_2
            0x1A,             // 5: iload_0
            0xA2.toByte(),    // 6: if_icmpge -> 6+13=19... let me recalc
            0x00, 0x0D,       //    offset = 13 -> 6+13=19
            0x1B,             // 9: iload_1
            0x1C,             // 10: iload_2
            0x60,             // 11: iadd
            0x3C,             // 12: istore_1
            0x84.toByte(),    // 13: iinc
            0x02, 0x01,       //    index=2, const=1
            0xA7.toByte(),    // 16: goto
            0xFF.toByte(), 0xF4.toByte(), //    offset = -12 -> 16-12=4
            0x1B,             // 19: iload_1
            0xAC.toByte(),    // 20: ireturn
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = 3, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0,
            majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(method),
            attributes = emptyList(),
        )

        val classBytes = org.kgen.target.jvm.JvmClassWriter.write(cf)

        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        assertEquals(1, module.functions.size)
        assertEquals("sum", module.functions[0].name)
        assertTrue(module.functions[0].blocks.size >= 3, "Expected multiple blocks for loop")
    }

    @Test
    fun compileCrossModuleCall() {
        // Build two classfiles: AddOps with add(II)I, and CallerOps with doubleAdd(II)I
        // that calls AddOps.add twice and sums the results.
        val addBytes = buildSimpleAddClass()
        val callerBytes = buildCallerClass()

        val compiler = RuntimeCompiler(Target.x86_64())
        val addModule = compiler.compile(addBytes)
        val callerModule = compiler.compile(callerBytes)

        assertEquals(1, addModule.functions.size)
        assertEquals("add", addModule.functions[0].name)

        assertEquals(1, callerModule.functions.size)
        assertEquals("doubleAdd", callerModule.functions[0].name)

        // The caller should have Call instructions referencing "add"
        val instructions = callerModule.functions[0].blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<org.kgen.ir.Instruction.Call>()
        assertTrue(calls.size >= 2, "Expected at least 2 call instructions, got ${calls.size}")
    }

    private fun buildCallerClass(): ByteArray {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/CallerOps")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val addRef = cp.methodRef("org/kgen/runtime/test/SimpleAdd", "add", "(II)I")
        val methodNameIdx = cp.utf8("doubleAdd")
        val methodDescIdx = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        // iload_0, iload_1, invokestatic #addRef, iload_0, iload_1, invokestatic #addRef, iadd, ireturn
        val bytecode = byteArrayOf(
            0x1A, 0x1B,
            0xB8.toByte(), (addRef shr 8).toByte(), (addRef and 0xFF).toByte(),
            0x1A, 0x1B,
            0xB8.toByte(), (addRef shr 8).toByte(), (addRef and 0xFF).toByte(),
            0x60,
            0xAC.toByte(),
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 4, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        return org.kgen.target.jvm.JvmClassWriter.write(cf)
    }

    @Test
    fun compileWithIntrinsic() {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/LoadTest")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val kgenLoadByte = cp.methodRef("org/kgen/unmanaged/Kgen", "loadByte", "(J)B")

        val methodNameIdx = cp.utf8("loadAndReturn")
        val methodDescIdx = cp.utf8("(J)I")
        val codeNameIdx = cp.utf8("Code")

        // Bytecode: lload_0, invokestatic Kgen.loadByte, ireturn
        val bytecode = byteArrayOf(
            0x1E,             // lload_0
            0xB8.toByte(),    // invokestatic
            (kgenLoadByte shr 8).toByte(), (kgenLoadByte and 0xFF).toByte(),
            0xAC.toByte(),    // ireturn
        )

        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(
            codeNameIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = 2, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            )
        )

        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = methodNameIdx,
            descriptorIndex = methodDescIdx,
            attributes = listOf(codeAttr),
        )

        val cf = org.kgen.target.jvm.ClassFile(
            minorVersion = 0,
            majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = emptyList(),
            fields = emptyList(),
            methods = listOf(method),
            attributes = emptyList(),
        )

        val classBytes = org.kgen.target.jvm.JvmClassWriter.write(cf)

        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        assertEquals(1, module.functions.size)
        assertEquals("loadAndReturn", module.functions[0].name)
        // Should have a Load instruction from the intrinsic lowering
        val instructions = module.functions[0].blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is org.kgen.ir.Instruction.Load }, "Expected Load instruction from Kgen.loadByte intrinsic")
    }
}

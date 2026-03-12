package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class InstanceMethodTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildClassWithInstanceMethods(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Counter")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val countField = cp.fieldRef("org/kgen/test/Counter", "count", "I")

        // Instance method: int getCount() { return this.count; }
        // bytecode: aload_0, getfield count, ireturn
        val getCountName = cp.utf8("getCount")
        val getCountDesc = cp.utf8("()I")
        val getCountBytecode = byteArrayOf(
            0x2A,                                    // aload_0 (this)
            0xB4.toByte(),                           // getfield count
            ((countField shr 8) and 0xFF).toByte(),
            (countField and 0xFF).toByte(),
            0xAC.toByte(),                           // ireturn
        )
        val getCountCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            2, 1, getCountBytecode, emptyList(), emptyList()))
        val getCountMethod = MethodInfo(
            AccessFlags.PUBLIC, // NOT static — instance method
            getCountName, getCountDesc, listOf(getCountCode))

        // Instance method: void setCount(int val) { this.count = val; }
        // bytecode: aload_0, iload_1, putfield count, return
        val setCountName = cp.utf8("setCount")
        val setCountDesc = cp.utf8("(I)V")
        val setCountBytecode = byteArrayOf(
            0x2A,                                    // aload_0 (this)
            0x1B,                                    // iload_1
            0xB5.toByte(),                           // putfield count
            ((countField shr 8) and 0xFF).toByte(),
            (countField and 0xFF).toByte(),
            0xB1.toByte(),                           // return
        )
        val setCountCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            2, 2, setCountBytecode, emptyList(), emptyList()))
        val setCountMethod = MethodInfo(
            AccessFlags.PUBLIC, // NOT static
            setCountName, setCountDesc, listOf(setCountCode))

        // Static helper: static int getCountOf(Counter c) { return c.count; }
        val helperName = cp.utf8("getCountOf")
        val helperDesc = cp.utf8("(Lorg/kgen/test/Counter;)I")
        val helperBytecode = byteArrayOf(
            0x2A,                                    // aload_0
            0xB4.toByte(),                           // getfield count
            ((countField shr 8) and 0xFF).toByte(),
            (countField and 0xFF).toByte(),
            0xAC.toByte(),                           // ireturn
        )
        val helperCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            2, 1, helperBytecode, emptyList(), emptyList()))
        val helperMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            helperName, helperDesc, listOf(helperCode))

        return JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(getCountMethod, setCountMethod, helperMethod),
            emptyList(),
        ))
    }

    @Test
    fun `instance method is compiled with this parameter`() {
        val module = compile(buildClassWithInstanceMethods())
        val fn = module.functions.firstOrNull { it.name == "org_kgen_test_Counter_getCount" }
        assertNotNull(fn, "Expected instance method 'org_kgen_test_Counter_getCount'")
        // Should have 1 param: this (OpaquePointer)
        assertEquals(1, fn!!.params.size, "Instance method should have 'this' param")
        assertEquals(Type.OpaquePointer, fn.params[0].type, "'this' should be OpaquePointer")
        assertEquals(Type.I32, fn.returnType)
    }

    @Test
    fun `instance method with args gets this plus args`() {
        val module = compile(buildClassWithInstanceMethods())
        val fn = module.functions.firstOrNull { it.name == "org_kgen_test_Counter_setCount" }
        assertNotNull(fn, "Expected 'org_kgen_test_Counter_setCount'")
        // Should have 2 params: this + int
        assertEquals(2, fn!!.params.size)
        assertEquals(Type.OpaquePointer, fn.params[0].type, "First param should be 'this'")
        assertEquals(Type.I32, fn.params[1].type, "Second param should be I32")
        assertEquals(Type.Void, fn.returnType)
    }

    @Test
    fun `static method alongside instance methods`() {
        val module = compile(buildClassWithInstanceMethods())
        val staticFn = module.functions.firstOrNull { it.name == "getCountOf" }
        assertNotNull(staticFn, "Expected static method 'getCountOf'")
        assertEquals(1, staticFn!!.params.size)
        assertEquals(Type.OpaquePointer, staticFn.params[0].type)
    }

    @Test
    fun `instance method body accesses fields via this`() {
        val module = compile(buildClassWithInstanceMethods())
        val fn = module.functions.first { it.name == "org_kgen_test_Counter_getCount" }
        val instructions = fn.blocks.flatMap { it.instructions }
        // Should have GEP (field offset) + Load (read field)
        assertTrue(instructions.any { it is Instruction.GetElementPtr }, "Expected GEP for field access")
        assertTrue(instructions.any { it is Instruction.Load }, "Expected Load for field read")
    }

    @Test
    fun `init methods are compiled with mangled name`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Foo")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val superInit = cp.methodRef("java/lang/Object", "<init>", "()V")

        // <init> method: aload_0, invokespecial Object.<init>, return
        val initName = cp.utf8("<init>")
        val initDesc = cp.utf8("()V")
        val initBytecode = byteArrayOf(
            0x2A,                                    // aload_0
            0xB7.toByte(),                           // invokespecial
            ((superInit shr 8) and 0xFF).toByte(),
            (superInit and 0xFF).toByte(),
            0xB1.toByte(),                           // return
        )
        val initCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            1, 1, initBytecode, emptyList(), emptyList()))
        val initMethod = MethodInfo(AccessFlags.PUBLIC, initName, initDesc, listOf(initCode))

        // A static method too
        val addName = cp.utf8("add")
        val addDesc = cp.utf8("(II)I")
        val addBytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val addCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, addBytecode, emptyList(), emptyList()))
        val addMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            addName, addDesc, listOf(addCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(initMethod, addMethod),
            emptyList(),
        ))

        val module = compile(classBytes)
        // <init> is compiled with mangled name ClassName_init
        assertNotNull(module.functions.firstOrNull { it.name.contains("init") },
            "Constructor should be compiled")
        // Static method should still be compiled
        assertNotNull(module.functions.firstOrNull { it.name == "add" })
    }

    @Test
    fun `new object produces malloc call`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Factory")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val pointClass = cp.classEntry("org/kgen/test/Point")

        // static Point create() { return new Point(); }
        // We skip invokespecial <init> (Object.<init> is a no-op),
        // so just: new Point, areturn
        val createName = cp.utf8("create")
        val createDesc = cp.utf8("()Lorg/kgen/test/Point;")
        val createBytecode = byteArrayOf(
            0xBB.toByte(),                           // new Point
            ((pointClass shr 8) and 0xFF).toByte(),
            (pointClass and 0xFF).toByte(),
            0x59,                                    // dup
            0x57,                                    // pop (simulate invokespecial <init> being skipped)
            0xB0.toByte(),                           // areturn
        )
        val createCode = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            2, 0, createBytecode, emptyList(), emptyList()))
        val createMethod = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC,
            createName, createDesc, listOf(createCode))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(createMethod), emptyList(),
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "create" }
        assertEquals(Type.OpaquePointer, fn.returnType)
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Instruction.Call && (it as Instruction.Call).function.let { f -> f is GlobalRef && f.name == "malloc" } },
            "Expected malloc call for new")
    }
}

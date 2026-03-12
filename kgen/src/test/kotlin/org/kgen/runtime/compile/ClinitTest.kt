package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class ClinitTest {

    private fun buildClassWithClinit(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/WithInit")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // <clinit> — just returns void (iconst_0, pop, return)
        val clinitName = cp.utf8("<clinit>")
        val clinitDesc = cp.utf8("()V")
        val clinitCode = byteArrayOf(0x03, 0x57, 0xB1.toByte()) // iconst_0, pop, return
        val clinitAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 1, maxLocals = 0, code = clinitCode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val clinitMethod = MethodInfo(
            accessFlags = AccessFlags.STATIC,
            nameIndex = clinitName, descriptorIndex = clinitDesc,
            attributes = listOf(clinitAttr),
        )

        // Regular method: add(II)I
        val addName = cp.utf8("add")
        val addDesc = cp.utf8("(II)I")
        val addCode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val addAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 2, code = addCode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val addMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = addName, descriptorIndex = addDesc,
            attributes = listOf(addAttr),
        )

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(clinitMethod, addMethod), attributes = emptyList(),
        ))
    }

    @Test
    fun `clinit is compiled to IR function`() {
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(buildClassWithClinit())
        val clinitFn = module.functions.firstOrNull {
            it.name.contains("clinit")
        }
        assertNotNull(clinitFn, "Expected clinit function in module")
    }

    @Test
    fun `clinit has safe IR name`() {
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(buildClassWithClinit())
        val clinitFn = module.functions.first { it.name.contains("clinit") }
        assertEquals("__clinit_org_kgen_test_WithInit", clinitFn.name)
    }

    @Test
    fun `clinit registered as global constructor`() {
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(buildClassWithClinit())
        assertTrue(module.globalCtors.isNotEmpty(), "Expected global constructors")
        assertEquals("__clinit_org_kgen_test_WithInit", module.globalCtors[0].function)
    }

    @Test
    fun `regular methods still compiled alongside clinit`() {
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(buildClassWithClinit())
        assertTrue(module.functions.any { it.name == "add" })
    }

    @Test
    fun `clinit and regular method both present`() {
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(buildClassWithClinit())
        val names = module.functions.map { it.name }.toSet()
        assertTrue("add" in names)
        assertTrue(names.any { it.contains("clinit") })
    }

    @Test
    fun `class without clinit has no global constructors`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/NoClinit")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val nameIdx = cp.utf8("foo")
        val descIdx = cp.utf8("()V")
        val code = byteArrayOf(0xB1.toByte()) // return
        val attr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 0, maxLocals = 0, code = code,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))
        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(attr),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))

        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)
        assertTrue(module.globalCtors.isEmpty())
    }

    @Test
    fun `clinit object file has clinit symbol`() {
        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(buildClassWithClinit()))
        assertTrue(obj.symbols.any { it.name.contains("clinit") })
    }

    @Test
    fun `clinit compiles to native executable with main`() {
        // Need a main function for the linker to create an entry point
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/WithMain")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        val clinitName = cp.utf8("<clinit>")
        val clinitDesc = cp.utf8("()V")
        val clinitCode = byteArrayOf(0xB1.toByte())
        val clinitMethod = MethodInfo(
            accessFlags = AccessFlags.STATIC,
            nameIndex = clinitName, descriptorIndex = clinitDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 0, maxLocals = 0, code = clinitCode,
                exceptionTable = emptyList(), attributes = emptyList()))),
        )

        val mainName = cp.utf8("main")
        val mainDesc = cp.utf8("()I")
        val mainCode = byteArrayOf(0x10, 42, 0xAC.toByte()) // bipush 42, ireturn
        val mainMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = mainName, descriptorIndex = mainDesc,
            attributes = listOf(AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 1, maxLocals = 0, code = mainCode,
                exceptionTable = emptyList(), attributes = emptyList()))),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(clinitMethod, mainMethod), attributes = emptyList(),
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val exe = compiler.compile(listOf(classBytes))
        assertTrue(exe.isNotEmpty())
        assertEquals(0x7F, exe[0].toInt() and 0xFF)
    }
}

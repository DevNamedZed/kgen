package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class StringConstantTest {

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

    private data class MethodSpec(
        val name: String,
        val descriptor: String,
        val bytecode: ByteArray,
        val maxStack: Int,
        val maxLocals: Int,
    )

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    @Test
    fun `ldc string creates global constant`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/StringTest")
        val superClass = cp.classEntry("java/lang/Object")
        val strIdx = cp.string("hello")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("loadString")
        val descIdx = cp.utf8("()V")
        // ldc strIdx, pop, return
        val code = byteArrayOf(
            0x12, strIdx.toByte(),  // ldc
            0x57,                   // pop
            0xB1.toByte(),          // return (void)
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
        val global = module.globals.find { it.name == ".str.0" }
        assertNotNull(global, "Expected a global named '.str.0', found: ${module.globals.map { it.name }}")
        assertTrue(global!!.initializer is Constant.StringConst,
            "Expected StringConst initializer, got: ${global.initializer}")
        assertEquals("hello", (global.initializer as Constant.StringConst).value)
    }

    @Test
    fun `multiple string constants get unique names`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MultiString")
        val superClass = cp.classEntry("java/lang/Object")
        val strIdx1 = cp.string("alpha")
        val strIdx2 = cp.string("beta")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("loadStrings")
        val descIdx = cp.utf8("()V")
        // ldc "alpha", pop, ldc "beta", pop, return
        val code = byteArrayOf(
            0x12, strIdx1.toByte(), // ldc "alpha"
            0x57,                   // pop
            0x12, strIdx2.toByte(), // ldc "beta"
            0x57,                   // pop
            0xB1.toByte(),          // return (void)
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
        val globalNames = module.globals.map { it.name }.toSet()
        assertTrue(".str.0" in globalNames,
            "Expected global '.str.0', found: $globalNames")
        assertTrue(".str.1" in globalNames,
            "Expected global '.str.1', found: $globalNames")
        assertEquals(2, module.globals.size,
            "Expected exactly 2 string globals, found: $globalNames")
    }

    @Test
    fun `string constant is marked as internal linkage`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/LinkageTest")
        val superClass = cp.classEntry("java/lang/Object")
        val strIdx = cp.string("internal_str")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("check")
        val descIdx = cp.utf8("()V")
        val code = byteArrayOf(
            0x12, strIdx.toByte(),  // ldc
            0x57,                   // pop
            0xB1.toByte(),          // return (void)
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
        val global = module.globals.find { it.name == ".str.0" }
        assertNotNull(global, "Expected a global named '.str.0', found: ${module.globals.map { it.name }}")
        assertEquals(Linkage.INTERNAL, global!!.linkage,
            "String constant global should have INTERNAL linkage")
        assertTrue(global.isConstant, "String constant global should be marked as constant")
    }

    @Test
    fun `string constant compiles to native object`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/NativeStr")
        val superClass = cp.classEntry("java/lang/Object")
        val strIdx = cp.string("hello")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("nativeStr")
        val descIdx = cp.utf8("()V")
        val code = byteArrayOf(
            0x12, strIdx.toByte(),  // ldc "hello"
            0x57,                   // pop
            0xB1.toByte(),          // return (void)
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

        val obj = NativeCompiler(Target.x86_64()).compileToObject(listOf(classBytes))
        val dataSection = obj.sections.find {
            it.name.contains("data") || it.name.contains("rodata")
        }
        assertNotNull(dataSection,
            "Expected a data or rodata section, found sections: ${obj.sections.map { it.name }}")
        val sectionBytes = dataSection!!.data
        val helloBytes = "hello".toByteArray(Charsets.UTF_8)
        assertTrue(containsSubarray(sectionBytes, helloBytes),
            "Expected data section to contain 'hello' bytes")
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}

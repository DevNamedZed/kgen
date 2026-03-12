package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*

class KgenExportNamingTest {

    private fun buildAnnotation(cp: ConstantPoolBuilder, className: String): ByteArray {
        val typeIdx = cp.utf8("L${className};")
        return byteArrayOf(
            0x00, 0x01, // 1 annotation
            ((typeIdx shr 8) and 0xFF).toByte(),
            (typeIdx and 0xFF).toByte(),
            0x00, 0x00, // 0 element-value pairs
        )
    }

    private fun buildAnnotationWithStringValue(
        cp: ConstantPoolBuilder, className: String, elementName: String, value: String
    ): ByteArray {
        val typeIdx = cp.utf8("L${className};")
        val nameIdx = cp.utf8(elementName)
        val valueIdx = cp.utf8(value)
        return byteArrayOf(
            0x00, 0x01, // 1 annotation
            ((typeIdx shr 8) and 0xFF).toByte(),
            (typeIdx and 0xFF).toByte(),
            0x00, 0x01, // 1 element-value pair
            ((nameIdx shr 8) and 0xFF).toByte(),
            (nameIdx and 0xFF).toByte(),
            's'.code.toByte(), // tag: string
            ((valueIdx shr 8) and 0xFF).toByte(),
            (valueIdx and 0xFF).toByte(),
        )
    }

    private fun buildClassWithExport(
        methodName: String, exportValue: String?
    ): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Exported")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        val nameIdx = cp.utf8(methodName)
        val descIdx = cp.utf8("(II)I")

        // iload_0, iload_1, iadd, ireturn
        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 2, code = bytecode,
            exceptionTable = emptyList(), attributes = emptyList(),
        ))

        val annotationData = if (exportValue != null && exportValue.isNotEmpty()) {
            buildAnnotationWithStringValue(cp, "org/kgen/unmanaged/KgenExport", "value", exportValue)
        } else {
            buildAnnotation(cp, "org/kgen/unmanaged/KgenExport")
        }

        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr, AttributeInfo(rtAnnotationsName, annotationData)),
        )

        return JvmClassWriter.write(ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))
    }

    @Test
    fun `default export uses method name`() {
        val classBytes = buildClassWithExport("add", null)
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        assertNotNull(module.functions.firstOrNull { it.name == "add" },
            "Expected function named 'add'")
    }

    @Test
    fun `custom export name overrides method name`() {
        val classBytes = buildClassWithExport("add", "my_add")
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        assertNotNull(module.functions.firstOrNull { it.name == "my_add" },
            "Expected function named 'my_add'")
        assertNull(module.functions.firstOrNull { it.name == "add" },
            "Should not have function named 'add'")
    }

    @Test
    fun `empty export value uses method name`() {
        val classBytes = buildClassWithExport("add", "")
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        assertNotNull(module.functions.firstOrNull { it.name == "add" },
            "Expected function named 'add' when export value is empty")
    }

    @Test
    fun `exported function has external linkage`() {
        val classBytes = buildClassWithExport("add", "native_add")
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "native_add" }
        assertEquals(org.kgen.ir.Linkage.EXTERNAL, fn.linkage)
    }

    @Test
    fun `custom name preserves function signature`() {
        val classBytes = buildClassWithExport("add", "custom_add")
        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "custom_add" }
        assertEquals(2, fn.params.size, "Should have 2 parameters")
        assertEquals(org.kgen.ir.Type.I32, fn.returnType, "Should return I32")
    }

    @Test
    fun `multiple methods with different export names`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Multi")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        val bytecode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())

        // Method 1: add → "math_add"
        val name1 = cp.utf8("add")
        val desc1 = cp.utf8("(II)I")
        val code1 = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, bytecode, emptyList(), emptyList()))
        val ann1 = buildAnnotationWithStringValue(cp, "org/kgen/unmanaged/KgenExport", "value", "math_add")
        val method1 = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, name1, desc1,
            listOf(code1, AttributeInfo(rtAnnotationsName, ann1)),
        )

        // Method 2: sub → "math_sub" (isub = 0x64)
        val name2 = cp.utf8("sub")
        val desc2 = cp.utf8("(II)I")
        val subBytecode = byteArrayOf(0x1A, 0x1B, 0x64, 0xAC.toByte())
        val code2 = AttributeBuilder.buildCode(codeIdx, CodeAttribute(2, 2, subBytecode, emptyList(), emptyList()))
        val ann2 = buildAnnotationWithStringValue(cp, "org/kgen/unmanaged/KgenExport", "value", "math_sub")
        val method2 = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, name2, desc2,
            listOf(code2, AttributeInfo(rtAnnotationsName, ann2)),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(),
            listOf(method1, method2), emptyList(),
        ))

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        assertNotNull(module.functions.firstOrNull { it.name == "math_add" })
        assertNotNull(module.functions.firstOrNull { it.name == "math_sub" })
        assertNull(module.functions.firstOrNull { it.name == "add" })
        assertNull(module.functions.firstOrNull { it.name == "sub" })
    }
}

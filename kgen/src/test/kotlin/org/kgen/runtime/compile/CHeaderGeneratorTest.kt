package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class CHeaderGeneratorTest {

    private fun moduleWith(vararg fns: Triple<String, Type, List<Param>>): Module {
        val builder = IrBuilder("test", Target.x86_64())
        for ((name, retType, params) in fns) {
            builder.createFunction(name, params, retType)
            builder.positionAtEnd(builder.appendBlock("entry"))
            if (retType == Type.Void) {
                builder.ret()
            } else {
                builder.ret(Constant.I32(0))
            }
            builder.finalizeFunction()
        }
        return builder.build()
    }

    @Test
    fun `generates include guard`() {
        val module = moduleWith()
        val header = CHeaderGenerator.generate(module, "MY_LIB_H")
        assertTrue(header.contains("#ifndef MY_LIB_H"))
        assertTrue(header.contains("#define MY_LIB_H"))
        assertTrue(header.contains("#endif /* MY_LIB_H */"))
    }

    @Test
    fun `includes stdint header`() {
        val module = moduleWith()
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("#include <stdint.h>"))
    }

    @Test
    fun `generates extern C block`() {
        val module = moduleWith()
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("#ifdef __cplusplus"))
        assertTrue(header.contains("extern \"C\" {"))
        assertTrue(header.contains("#endif"))
    }

    @Test
    fun `void function no params`() {
        val module = moduleWith(Triple("init", Type.Void, emptyList()))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("void init(void);"))
    }

    @Test
    fun `int function with int params`() {
        val module = moduleWith(Triple("add", Type.I32, listOf(
            Param("a", Type.I32), Param("b", Type.I32)
        )))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("int32_t add(int32_t a, int32_t b);"))
    }

    @Test
    fun `long return type`() {
        val module = moduleWith(Triple("getTime", Type.I64, emptyList()))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("int64_t getTime(void);"))
    }

    @Test
    fun `float and double types`() {
        val module = moduleWith(Triple("compute", Type.F64, listOf(
            Param("x", Type.F32), Param("y", Type.F64)
        )))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("double compute(float x, double y);"))
    }

    @Test
    fun `multiple functions`() {
        val module = moduleWith(
            Triple("add", Type.I32, listOf(Param("a", Type.I32), Param("b", Type.I32))),
            Triple("sub", Type.I32, listOf(Param("a", Type.I32), Param("b", Type.I32))),
            Triple("init", Type.Void, emptyList()),
        )
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("int32_t add(int32_t a, int32_t b);"))
        assertTrue(header.contains("int32_t sub(int32_t a, int32_t b);"))
        assertTrue(header.contains("void init(void);"))
    }

    @Test
    fun `byte and short types`() {
        val module = moduleWith(Triple("process", Type.I8, listOf(
            Param("x", Type.I16)
        )))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("int8_t process(int16_t x);"))
    }

    @Test
    fun `pointer type maps to void star`() {
        val module = moduleWith(Triple("alloc", Type.Pointer(Type.I8), listOf(
            Param("size", Type.I64)
        )))
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("void* alloc(int64_t size);"))
    }

    @Test
    fun `external declarations excluded`() {
        val builder = IrBuilder("test", Target.x86_64())
        // Declare (external) — should NOT appear
        builder.declareFunction("external_fn", listOf(
            Param("x", Type.I32)
        ), Type.I32)
        // Define — should appear
        builder.createFunction("my_fn", listOf(Param("x", Type.I32)), Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(Constant.I32(0))
        builder.finalizeFunction()
        val module = builder.build()

        val header = CHeaderGenerator.generate(module)
        assertFalse(header.contains("external_fn"))
        assertTrue(header.contains("int32_t my_fn(int32_t x);"))
    }

    @Test
    fun `default guard name`() {
        val module = moduleWith()
        val header = CHeaderGenerator.generate(module)
        assertTrue(header.contains("KGEN_EXPORTS_H"))
    }

    @Test
    fun `NativeLibraryCompiler generateHeader includes exported functions only`() {
        val compiler = NativeLibraryCompiler(Target.x86_64())
        // Without @KgenExport, functions have INTERNAL linkage — not in header
        val classBytes = buildSimpleClass("square", "(I)I",
            byteArrayOf(0x1A, 0x1A, 0x68, 0xAC.toByte()))
        val header = compiler.generateHeader(listOf(classBytes))
        assertTrue(header.contains("#ifndef"))
        // square is not @KgenExport, so it should NOT appear in the header
        assertFalse(header.contains("square("))
    }

    @Test
    fun `NativeLibraryCompiler generateHeader with guard name`() {
        val compiler = NativeLibraryCompiler(Target.x86_64(), soname = "libmath.so")
        val classBytes = buildSimpleClass("add", "(II)I",
            byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte()))
        val header = compiler.generateHeader(listOf(classBytes), "MATH_H")
        assertTrue(header.contains("#ifndef MATH_H"))
        assertTrue(header.contains("#define MATH_H"))
    }

    private fun buildSimpleClass(methodName: String, descriptor: String, bytecode: ByteArray): ByteArray {
        val cp = org.kgen.target.jvm.ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Lib")
        val superClass = cp.classEntry("java/lang/Object")
        val nameIdx = cp.utf8(methodName)
        val descIdx = cp.utf8(descriptor)
        val codeIdx = cp.utf8("Code")
        val maxLocals = descriptor.count { it == 'I' || it == 'J' || it == 'F' || it == 'D' }
        val codeAttr = org.kgen.target.jvm.AttributeBuilder.buildCode(codeIdx,
            org.kgen.target.jvm.CodeAttribute(
                maxStack = 2, maxLocals = maxLocals, code = bytecode,
                exceptionTable = emptyList(), attributes = emptyList(),
            ))
        val method = org.kgen.target.jvm.MethodInfo(
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.STATIC,
            nameIndex = nameIdx, descriptorIndex = descIdx,
            attributes = listOf(codeAttr),
        )
        return org.kgen.target.jvm.JvmClassWriter.write(org.kgen.target.jvm.ClassFile(
            minorVersion = 0, majorVersion = 50,
            constantPool = cp.build(),
            accessFlags = org.kgen.target.jvm.AccessFlags.PUBLIC or org.kgen.target.jvm.AccessFlags.SUPER,
            thisClass = thisClass, superClass = superClass,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        ))
    }
}

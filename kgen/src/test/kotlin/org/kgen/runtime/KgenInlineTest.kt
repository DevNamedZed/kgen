package org.kgen.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.FnAttribute
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.RuntimeCompiler
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

class KgenInlineTest {

    /**
     * Build a classfile with two methods:
     * - `doubleIt(int): int` — annotated with @KgenInline, returns x + x
     * - `quadruple(int): int` — calls doubleIt twice
     */
    private fun buildClassWithInline(): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/InlineTest")
        val superClassIdx = cp.classEntry("java/lang/Object")

        // doubleIt method
        val doubleItName = cp.utf8("doubleIt")
        val doubleItDesc = cp.utf8("(I)I")
        val codeNameIdx = cp.utf8("Code")

        // doubleIt bytecode: iload_0, iload_0, iadd, ireturn
        val doubleItCode = byteArrayOf(0x1A, 0x1A, 0x60, 0xAC.toByte())
        val doubleItCodeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(2, 1, doubleItCode, emptyList(), emptyList())
        )

        // Add @KgenInline annotation
        val inlineAnnotation = buildAnnotation(cp, "org/kgen/unmanaged/KgenInline")
        val rtAnnotationsName = cp.utf8("RuntimeVisibleAnnotations")

        val doubleItMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = doubleItName,
            descriptorIndex = doubleItDesc,
            attributes = listOf(doubleItCodeAttr, AttributeInfo(rtAnnotationsName, inlineAnnotation)),
        )

        // quadruple method: calls doubleIt(doubleIt(x))
        val quadName = cp.utf8("quadruple")
        val quadDesc = cp.utf8("(I)I")
        val doubleItRef = cp.methodRef(
            "org/kgen/runtime/test/InlineTest", "doubleIt", "(I)I")
        // quadruple bytecode: iload_0, invokestatic doubleIt, invokestatic doubleIt, ireturn
        val quadCode = byteArrayOf(
            0x1A,                     // iload_0
            0xB8.toByte(),            // invokestatic
            ((doubleItRef shr 8) and 0xFF).toByte(),
            (doubleItRef and 0xFF).toByte(),
            0xB8.toByte(),            // invokestatic
            ((doubleItRef shr 8) and 0xFF).toByte(),
            (doubleItRef and 0xFF).toByte(),
            0xAC.toByte(),            // ireturn
        )
        val quadCodeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(2, 1, quadCode, emptyList(), emptyList())
        )

        // Add @KgenExport annotation
        val exportAnnotation = buildAnnotation(cp, "org/kgen/unmanaged/KgenExport")
        val quadMethod = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = quadName,
            descriptorIndex = quadDesc,
            attributes = listOf(quadCodeAttr, AttributeInfo(rtAnnotationsName, exportAnnotation)),
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(doubleItMethod, quadMethod),
            attributes = emptyList(),
        )

        return JvmClassWriter.write(cf)
    }

    private fun buildAnnotation(cp: ConstantPoolBuilder, className: String): ByteArray {
        val typeIdx = cp.utf8("L${className};")
        // RuntimeVisibleAnnotations: num_annotations(u2)=1, type_index(u2), num_element_value_pairs(u2)=0
        return byteArrayOf(
            0x00, 0x01, // 1 annotation
            ((typeIdx shr 8) and 0xFF).toByte(),
            (typeIdx and 0xFF).toByte(),
            0x00, 0x00, // 0 element-value pairs
        )
    }

    @Test
    fun kgenInlineSetsAlwaysInlineAttribute() {
        val classBytes = buildClassWithInline()
        val cf = JvmClassReader.read(classBytes)
        val compiler = RuntimeCompiler(Target.x86_64())

        // Build module but check IR before inlining by using the builder directly
        val builder = org.kgen.ir.build.IrBuilder("test", Target.x86_64())
        val method = cf.methods[0] // doubleIt
        val name = cf.string(method.nameIndex)
        val desc = cf.string(method.descriptorIndex)
        val codeAttr = method.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" }!!
        val code = AttributeParser.parseCode(codeAttr, cf.constantPool)

        val attrs = mutableSetOf<FnAttribute>()
        attrs.add(FnAttribute.ALWAYSINLINE)

        org.kgen.runtime.compile.BytecodeToIrLowering(
            builder, cf, name, desc, code, false, attrs
        ).lower()

        val module = builder.build()
        val func = module.functions.first()
        assertTrue(func.attributes.contains(FnAttribute.ALWAYSINLINE))
    }

    @Test
    fun inlinedFunctionCallsReplaced() {
        val classBytes = buildClassWithInline()
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        // After inlining, the quadruple function should not have Call instructions
        // to doubleIt (they should be inlined)
        val quadFunc = module.functions.firstOrNull { it.name == "quadruple" }
        assertNotNull(quadFunc, "quadruple function should exist")

        val calls = quadFunc!!.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Call>()
        }.filter { call ->
            val funcName = when (val f = call.function) {
                is org.kgen.ir.FunctionRef -> f.name
                else -> f.name
            }
            funcName == "doubleIt"
        }

        assertTrue(calls.isEmpty(), "doubleIt calls should be inlined, but found ${calls.size}")
    }

    @Test
    fun inlinedResultIsCorrect() {
        val classBytes = buildClassWithInline()
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        // quadruple should have Add instructions (from inlined doubleIt)
        val quadFunc = module.functions.firstOrNull { it.name == "quadruple" }
        assertNotNull(quadFunc)

        val adds = quadFunc!!.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Add>()
        }

        // doubleIt does x + x, called twice → should have 2 Add instructions
        assertTrue(adds.size >= 2, "Expected at least 2 Add instructions from inlined doubleIt, got ${adds.size}")
    }

    @Test
    fun nonInlineFunctionsPreserved() {
        // Build class without @KgenInline
        val cp = ConstantPoolBuilder()
        val thisClassIdx = cp.classEntry("org/kgen/runtime/test/NoInline")
        val superClassIdx = cp.classEntry("java/lang/Object")
        val addName = cp.utf8("add")
        val addDesc = cp.utf8("(II)I")
        val codeNameIdx = cp.utf8("Code")

        val addCode = byteArrayOf(0x1A, 0x1B, 0x60, 0xAC.toByte())
        val codeAttr = AttributeBuilder.buildCode(
            codeNameIdx,
            CodeAttribute(2, 2, addCode, emptyList(), emptyList())
        )

        val method = MethodInfo(
            accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
            nameIndex = addName, descriptorIndex = addDesc,
            attributes = listOf(codeAttr),
        )

        val cf = ClassFile(
            minorVersion = 0, majorVersion = 65,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisClassIdx, superClass = superClassIdx,
            interfaces = emptyList(), fields = emptyList(),
            methods = listOf(method), attributes = emptyList(),
        )

        val classBytes = JvmClassWriter.write(cf)
        val compiler = RuntimeCompiler(Target.x86_64())
        val module = compiler.compile(classBytes)

        val func = module.functions.first()
        assertFalse(func.attributes.contains(FnAttribute.ALWAYSINLINE))
    }
}

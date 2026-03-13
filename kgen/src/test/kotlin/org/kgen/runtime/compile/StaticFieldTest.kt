package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

class StaticFieldTest {

    private data class MethodSpec(
        val name: String,
        val descriptor: String,
        val bytecode: ByteArray,
        val maxStack: Int,
        val maxLocals: Int,
    )

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

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun allInstructions(fn: IrFunction): List<Instruction> {
        return fn.blocks.flatMap { it.instructions }
    }

    @Test
    fun `putstatic and getstatic produce global variable`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Counter")
        val superClass = cp.classEntry("java/lang/Object")
        val fieldIdx = cp.fieldRef("org/kgen/test/Counter", "count", "I")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("storeAndLoad")
        val descIdx = cp.utf8("()I")
        // bipush 42, putstatic fieldIdx, getstatic fieldIdx, ireturn
        val code = byteArrayOf(
            0x10, 42,                                                           // bipush 42
            0xB3.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // putstatic
            0xB2.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // getstatic
            0xAC.toByte(),                                                      // ireturn
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
        val global = module.globals.find { it.name.contains("count") }
        assertNotNull(global, "Expected a global variable for static field 'count'")
        assertEquals(Type.I32, global!!.type)

        val fn = module.functions.first { it.name == "storeAndLoad" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Store }, "Expected Store instruction for putstatic")
        assertTrue(instructions.any { it is Load }, "Expected Load instruction for getstatic")
    }

    @Test
    fun `static field maps to correct type`() {
        data class FieldCase(val fieldName: String, val descriptor: String, val expectedType: Type)

        val cases = listOf(
            FieldCase("intField", "I", Type.I32),
            FieldCase("longField", "J", Type.I64),
            FieldCase("floatField", "F", Type.F32),
            FieldCase("doubleField", "D", Type.F64),
        )

        for (case in cases) {
            val cp = ConstantPoolBuilder()
            val thisClass = cp.classEntry("org/kgen/test/Types")
            val superClass = cp.classEntry("java/lang/Object")
            val fieldIdx = cp.fieldRef("org/kgen/test/Types", case.fieldName, case.descriptor)
            val codeIdx = cp.utf8("Code")

            val nameIdx = cp.utf8("get_${case.fieldName}")
            // Return type depends on field type
            val retDesc = when (case.descriptor) {
                "I" -> "()I"
                "J" -> "()J"
                "F" -> "()F"
                "D" -> "()D"
                else -> "()I"
            }
            val descIdx = cp.utf8(retDesc)
            // Return opcode depends on type
            val retOpcode = when (case.descriptor) {
                "I" -> 0xAC.toByte()   // ireturn
                "J" -> 0xAD.toByte()   // lreturn
                "F" -> 0xAE.toByte()   // freturn
                "D" -> 0xAF.toByte()   // dreturn
                else -> 0xAC.toByte()
            }
            val code = byteArrayOf(
                0xB2.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // getstatic
                retOpcode,
            )
            val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = 2, maxLocals = 0, code = code,
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
            val global = module.globals.find { it.name.contains(case.fieldName) }
            assertNotNull(global, "Expected global for field '${case.fieldName}'")
            assertEquals(case.expectedType, global!!.type,
                "Field '${case.fieldName}' with descriptor '${case.descriptor}' should map to ${case.expectedType}")
        }
    }

    @Test
    fun `static field global name includes class and field name`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/MyClass")
        val superClass = cp.classEntry("java/lang/Object")
        val fieldIdx = cp.fieldRef("org/kgen/test/MyClass", "value", "I")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("getValue")
        val descIdx = cp.utf8("()I")
        val code = byteArrayOf(
            0xB2.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // getstatic
            0xAC.toByte(),                                                          // ireturn
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
        val global = module.globals.find { it.name == "org_kgen_test_MyClass__value" }
        assertNotNull(global, "Expected global named 'org_kgen_test_MyClass__value', " +
            "found: ${module.globals.map { it.name }}")
    }

    @Test
    fun `static field compiles to native object`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Holder")
        val superClass = cp.classEntry("java/lang/Object")
        val fieldIdx = cp.fieldRef("org/kgen/test/Holder", "data", "I")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("getData")
        val descIdx = cp.utf8("()I")
        val code = byteArrayOf(
            0xB2.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // getstatic
            0xAC.toByte(),                                                          // ireturn
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
        val globalSymbol = obj.symbols.find { it.name.contains("data") }
        assertNotNull(globalSymbol, "Expected global symbol for static field 'data' in object file, " +
            "found symbols: ${obj.symbols.map { it.name }}")
    }

    @Test
    fun `multiple static fields produce separate globals`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Multi")
        val superClass = cp.classEntry("java/lang/Object")
        val fieldA = cp.fieldRef("org/kgen/test/Multi", "alpha", "I")
        val fieldB = cp.fieldRef("org/kgen/test/Multi", "beta", "I")
        val fieldC = cp.fieldRef("org/kgen/test/Multi", "gamma", "I")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("readAll")
        val descIdx = cp.utf8("()I")
        // getstatic alpha, getstatic beta, iadd, getstatic gamma, iadd, ireturn
        val code = byteArrayOf(
            0xB2.toByte(), (fieldA shr 8).toByte(), (fieldA and 0xFF).toByte(), // getstatic alpha
            0xB2.toByte(), (fieldB shr 8).toByte(), (fieldB and 0xFF).toByte(), // getstatic beta
            0x60,                                                                // iadd
            0xB2.toByte(), (fieldC shr 8).toByte(), (fieldC and 0xFF).toByte(), // getstatic gamma
            0x60,                                                                // iadd
            0xAC.toByte(),                                                       // ireturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 0, code = code,
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
        assertTrue(globalNames.any { it.contains("alpha") },
            "Expected global for 'alpha', found: $globalNames")
        assertTrue(globalNames.any { it.contains("beta") },
            "Expected global for 'beta', found: $globalNames")
        assertTrue(globalNames.any { it.contains("gamma") },
            "Expected global for 'gamma', found: $globalNames")
        assertEquals(3, module.globals.size,
            "Expected exactly 3 globals, found: $globalNames")
    }

    @Test
    fun `static field accumulator pattern`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Accumulator")
        val superClass = cp.classEntry("java/lang/Object")
        val fieldIdx = cp.fieldRef("org/kgen/test/Accumulator", "total", "I")
        val codeIdx = cp.utf8("Code")

        val nameIdx = cp.utf8("accumulate")
        val descIdx = cp.utf8("(I)V")
        // getstatic total, iload_0, iadd, putstatic total, return
        val code = byteArrayOf(
            0xB2.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // getstatic total
            0x1A,                                                                    // iload_0
            0x60,                                                                    // iadd
            0xB3.toByte(), (fieldIdx shr 8).toByte(), (fieldIdx and 0xFF).toByte(), // putstatic total
            0xB1.toByte(),                                                           // return (void)
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            maxStack = 2, maxLocals = 1, code = code,
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
        val fn = module.functions.first { it.name == "accumulate" }
        val instructions = allInstructions(fn)

        assertTrue(instructions.any { it is Load }, "Expected Load for getstatic")
        assertTrue(instructions.any { it is Add }, "Expected Add for iadd")
        assertTrue(instructions.any { it is Store }, "Expected Store for putstatic")

        val global = module.globals.find { it.name.contains("total") }
        assertNotNull(global, "Expected global for 'total'")
        assertEquals(Type.I32, global!!.type)
    }
}

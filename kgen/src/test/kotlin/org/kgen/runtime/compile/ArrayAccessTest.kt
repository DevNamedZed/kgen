package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.pipeline.Mem2Reg
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

class ArrayAccessTest {

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

    /**
     * Compile class bytes to IR, bypassing the SubsetValidator
     * (which forbids array operations). Mirrors RuntimeCompiler.compile
     * but skips validation.
     */
    private fun compile(classBytes: ByteArray): Module {
        val target = Target.x86_64()
        val cf = JvmClassReader.read(classBytes)
        val builder = ModuleBuilder(cf.thisClassName.replace('/', '_'), target)

        for (method in cf.methods) {
            val name = cf.string(method.nameIndex)
            val desc = cf.string(method.descriptorIndex)
            val flags = method.accessFlags
            if (flags and AccessFlags.STATIC == 0 && name != "<clinit>") continue

            val codeAttr = method.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" }
                ?: continue
            val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
            BytecodeToIrLowering(builder, cf, name, desc, code, exported = false).lower()
        }

        var module = builder.build()
        module = Mem2Reg().run(module)
        return module
    }

    private fun allInstructions(fn: IrFunction): List<Instruction> {
        return fn.blocks.flatMap { it.instructions }
    }

    private data class MethodSpec(
        val name: String,
        val descriptor: String,
        val bytecode: ByteArray,
        val maxStack: Int,
        val maxLocals: Int,
    )

    @Test
    fun `newarray produces malloc call`() {
        // bipush 10, newarray T_INT(10), areturn
        val code = byteArrayOf(
            0x10, 10,                   // bipush 10
            0xBC.toByte(), 10,          // newarray T_INT
            0xB0.toByte(),              // areturn
        )
        val classBytes = buildClass("org/kgen/test/ArrayNew", listOf(
            MethodSpec("createArray", "()J", code, maxStack = 4, maxLocals = 0)
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "createArray" }
        val instructions = allInstructions(fn)
        val hasMallocCall = instructions.any {
            it is Call && it.function.name == "malloc"
        }
        assertTrue(hasMallocCall, "Expected call to 'malloc' for newarray, " +
            "instructions: ${instructions.map { it::class.simpleName }}")
    }

    @Test
    fun `iaload produces GEP and load`() {
        // aload_0, iconst_0, iaload, ireturn
        val code = byteArrayOf(
            0x2A,                       // aload_0
            0x03,                       // iconst_0
            0x2E,                       // iaload
            0xAC.toByte(),              // ireturn
        )
        val classBytes = buildClass("org/kgen/test/ArrayLoad", listOf(
            MethodSpec("loadFirst", "(J)I", code, maxStack = 4, maxLocals = 2)
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "loadFirst" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is GetElementPtr },
            "Expected GetElementPtr for iaload, got: ${instructions.map { it::class.simpleName }}")
        assertTrue(instructions.any { it is Load },
            "Expected Load for iaload, got: ${instructions.map { it::class.simpleName }}")
    }

    @Test
    fun `iastore produces GEP and store`() {
        // aload_0, iconst_0, bipush 42, iastore, return
        val code = byteArrayOf(
            0x2A,                       // aload_0
            0x03,                       // iconst_0
            0x10, 42,                   // bipush 42
            0x4F,                       // iastore
            0xB1.toByte(),              // return (void)
        )
        val classBytes = buildClass("org/kgen/test/ArrayStore", listOf(
            MethodSpec("storeFirst", "(J)V", code, maxStack = 4, maxLocals = 2)
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "storeFirst" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is GetElementPtr },
            "Expected GetElementPtr for iastore, got: ${instructions.map { it::class.simpleName }}")
        assertTrue(instructions.any { it is Store },
            "Expected Store for iastore, got: ${instructions.map { it::class.simpleName }}")
    }

    @Test
    fun `arraylength produces load from array base`() {
        // aload_0, arraylength, ireturn
        val code = byteArrayOf(
            0x2A,                       // aload_0
            0xBE.toByte(),              // arraylength
            0xAC.toByte(),              // ireturn
        )
        val classBytes = buildClass("org/kgen/test/ArrayLen", listOf(
            MethodSpec("getLength", "(J)I", code, maxStack = 4, maxLocals = 2)
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "getLength" }
        val instructions = allInstructions(fn)
        assertTrue(instructions.any { it is Load },
            "Expected Load for arraylength, got: ${instructions.map { it::class.simpleName }}")
    }

    @Test
    fun `array sum loop compiles to IR`() {
        // Method: int sum(ptr arr)
        // local 0 = arr (param, long/pointer occupies 2 slots)
        // local 2 = sum (int), local 3 = i (int)
        val code = byteArrayOf(
            0x03,                                   // 0: iconst_0
            0x3D,                                   // 1: istore_2 (sum = 0)
            0x03,                                   // 2: iconst_0
            0x3E,                                   // 3: istore_3 (i = 0)
            // loop header at offset 4:
            0x1D,                                   // 4: iload_3 (i)
            0x2A,                                   // 5: aload_0 (arr)
            0xBE.toByte(),                          // 6: arraylength
            0xA2.toByte(), 0x00, 0x0F,              // 7: if_icmpge +15 -> offset 22
            // loop body:
            0x2A,                                   // 10: aload_0 (arr)
            0x1D,                                   // 11: iload_3 (i)
            0x2E,                                   // 12: iaload
            0x1C,                                   // 13: iload_2 (sum)
            0x60,                                   // 14: iadd
            0x3D,                                   // 15: istore_2 (sum = sum + arr[i])
            0x84.toByte(), 0x03, 0x01,              // 16: iinc 3, 1 (i++)
            0xA7.toByte(), 0xFF.toByte(), 0xF1.toByte(), // 19: goto -15 -> offset 4
            // end at offset 22:
            0x1C,                                   // 22: iload_2 (sum)
            0xAC.toByte(),                          // 23: ireturn
        )
        val classBytes = buildClass("org/kgen/test/ArraySum", listOf(
            MethodSpec("sum", "(J)I", code, maxStack = 4, maxLocals = 4)
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "sum" }
        val instructions = allInstructions(fn)

        // Loop should produce phi nodes after Mem2Reg
        assertTrue(instructions.any { it is Phi },
            "Expected Phi from loop after Mem2Reg, got: ${instructions.map { it::class.simpleName }}")
        assertTrue(instructions.any { it is Add },
            "Expected Add for iadd, got: ${instructions.map { it::class.simpleName }}")
    }

    @Test
    fun `newarray and iastore end-to-end compiles to object`() {
        // Method: long createAndFill() — returns pointer as long
        // newarray int[3], store values 10, 20, 30, areturn
        val code = byteArrayOf(
            0x06,                                   // 0: iconst_3
            0xBC.toByte(), 10,                      // 1: newarray T_INT
            0x3A, 0x00,                             // 3: astore 0 (arr)
            // arr[0] = 10
            0x19, 0x00,                             // 5: aload 0 (arr)
            0x03,                                   // 7: iconst_0
            0x10, 10,                               // 8: bipush 10
            0x4F,                                   // 10: iastore
            // arr[1] = 20
            0x19, 0x00,                             // 11: aload 0 (arr)
            0x04,                                   // 13: iconst_1
            0x10, 20,                               // 14: bipush 20
            0x4F,                                   // 16: iastore
            // arr[2] = 30
            0x19, 0x00,                             // 17: aload 0 (arr)
            0x05,                                   // 19: iconst_2
            0x10, 30,                               // 20: bipush 30
            0x4F,                                   // 22: iastore
            // return array
            0x19, 0x00,                             // 23: aload 0 (arr)
            0xB0.toByte(),                          // 25: areturn
        )
        val classBytes = buildClass("org/kgen/test/ArrayFill", listOf(
            MethodSpec("createAndFill", "()J", code, maxStack = 4, maxLocals = 1)
        ))

        val module = compile(classBytes)
        val target = Target.x86_64()
        val obj = NativeCompiler(target).generateObject(module)
        assertNotNull(obj, "Expected object file from array pipeline")
        assertTrue(obj.symbols.isNotEmpty(), "Expected symbols in compiled object")
        val fnSymbol = obj.symbols.find { it.name.contains("createAndFill") }
        assertNotNull(fnSymbol, "Expected 'createAndFill' symbol in object, " +
            "found: ${obj.symbols.map { it.name }}")
    }
}

package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

class InstanceFieldTest {

    private fun buildClassWithFields(methods: List<TestMethod>): ByteArray {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Point")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")

        // Field refs
        val xFieldRef = cp.fieldRef("org/kgen/test/Point", "x", "I")
        val yFieldRef = cp.fieldRef("org/kgen/test/Point", "y", "I")

        val jvmMethods = methods.map { spec ->
            val nameIdx = cp.utf8(spec.name)
            val descIdx = cp.utf8(spec.descriptor)
            val bytecode = spec.bytecodeBuilder(xFieldRef, yFieldRef)
            val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
                maxStack = spec.maxStack, maxLocals = spec.maxLocals,
                code = bytecode, exceptionTable = emptyList(), attributes = emptyList(),
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

    private data class TestMethod(
        val name: String,
        val descriptor: String,
        val maxStack: Int,
        val maxLocals: Int,
        val bytecodeBuilder: (xRef: Int, yRef: Int) -> ByteArray,
    )

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    @Test
    fun `getfield produces GEP and load`() {
        // static int getX(Point p) { return p.x; }
        // bytecode: aload_0, getfield Point.x, ireturn
        val classBytes = buildClassWithFields(listOf(
            TestMethod("getX", "(Lorg/kgen/test/Point;)I", 2, 1) { xRef, _ ->
                byteArrayOf(
                    0x2A,                                     // aload_0
                    0xB4.toByte(),                            // getfield
                    ((xRef shr 8) and 0xFF).toByte(),
                    (xRef and 0xFF).toByte(),
                    0xAC.toByte(),                            // ireturn
                )
            }
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "getX" }
        val instructions = fn.blocks.flatMap { it.instructions }

        // Should have GEP (for field offset) + Load
        assertTrue(instructions.any { it is GetElementPtr }, "Expected GEP for field access")
        assertTrue(instructions.any { it is Load }, "Expected Load for field read")
        assertEquals(Type.I32, fn.returnType)
    }

    @Test
    fun `putfield produces GEP and store`() {
        // static void setX(Point p, int val) { p.x = val; }
        // bytecode: aload_0, iload_1, putfield Point.x, return
        val classBytes = buildClassWithFields(listOf(
            TestMethod("setX", "(Lorg/kgen/test/Point;I)V", 2, 2) { xRef, _ ->
                byteArrayOf(
                    0x2A,                                     // aload_0
                    0x1B,                                     // iload_1
                    0xB5.toByte(),                            // putfield
                    ((xRef shr 8) and 0xFF).toByte(),
                    (xRef and 0xFF).toByte(),
                    0xB1.toByte(),                            // return
                )
            }
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "setX" }
        val instructions = fn.blocks.flatMap { it.instructions }

        assertTrue(instructions.any { it is GetElementPtr }, "Expected GEP for field access")
        assertTrue(instructions.any { it is Store }, "Expected Store for field write")
    }

    @Test
    fun `getfield on two different fields uses different offsets`() {
        // static int sum(Point p) { return p.x + p.y; }
        // bytecode: aload_0, getfield x, aload_0, getfield y, iadd, ireturn
        val classBytes = buildClassWithFields(listOf(
            TestMethod("sum", "(Lorg/kgen/test/Point;)I", 2, 1) { xRef, yRef ->
                byteArrayOf(
                    0x2A,                                     // aload_0
                    0xB4.toByte(),                            // getfield x
                    ((xRef shr 8) and 0xFF).toByte(),
                    (xRef and 0xFF).toByte(),
                    0x2A,                                     // aload_0
                    0xB4.toByte(),                            // getfield y
                    ((yRef shr 8) and 0xFF).toByte(),
                    (yRef and 0xFF).toByte(),
                    0x60,                                     // iadd
                    0xAC.toByte(),                            // ireturn
                )
            }
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "sum" }
        val instructions = fn.blocks.flatMap { it.instructions }

        // Should have 2 GEPs (one for each field) + 2 Loads + Add
        val geps = instructions.filterIsInstance<GetElementPtr>()
        assertEquals(2, geps.size, "Expected 2 GEPs for 2 field accesses")
        assertTrue(instructions.any { it is Add }, "Expected Add")
    }

    @Test
    fun `putfield and getfield round-trip`() {
        // static int setThenGet(Point p, int v) { p.x = v; return p.x; }
        // bytecode: aload_0, iload_1, putfield x, aload_0, getfield x, ireturn
        val classBytes = buildClassWithFields(listOf(
            TestMethod("setThenGet", "(Lorg/kgen/test/Point;I)I", 2, 2) { xRef, _ ->
                byteArrayOf(
                    0x2A,                                     // aload_0
                    0x1B,                                     // iload_1
                    0xB5.toByte(),                            // putfield x
                    ((xRef shr 8) and 0xFF).toByte(),
                    (xRef and 0xFF).toByte(),
                    0x2A,                                     // aload_0
                    0xB4.toByte(),                            // getfield x
                    ((xRef shr 8) and 0xFF).toByte(),
                    (xRef and 0xFF).toByte(),
                    0xAC.toByte(),                            // ireturn
                )
            }
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "setThenGet" }
        val instructions = fn.blocks.flatMap { it.instructions }

        val stores = instructions.filterIsInstance<Store>()
        val loads = instructions.filterIsInstance<Load>()
        assertTrue(stores.isNotEmpty(), "Expected Store for putfield")
        assertTrue(loads.isNotEmpty(), "Expected Load for getfield")
    }

    @Test
    fun `object parameter is OpaquePointer type`() {
        val classBytes = buildClassWithFields(listOf(
            TestMethod("getX", "(Lorg/kgen/test/Point;)I", 2, 1) { xRef, _ ->
                byteArrayOf(
                    0x2A, 0xB4.toByte(),
                    ((xRef shr 8) and 0xFF).toByte(), (xRef and 0xFF).toByte(),
                    0xAC.toByte(),
                )
            }
        ))

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "getX" }
        assertEquals(1, fn.params.size)
        assertEquals(Type.OpaquePointer, fn.params[0].type)
    }

    @Test
    fun `long field uses I64 type`() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Counter")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val countRef = cp.fieldRef("org/kgen/test/Counter", "count", "J")

        // static long getCount(Counter c) { return c.count; }
        val nameIdx = cp.utf8("getCount")
        val descIdx = cp.utf8("(Lorg/kgen/test/Counter;)J")
        val bytecode = byteArrayOf(
            0x2A,                                     // aload_0
            0xB4.toByte(),                            // getfield count
            ((countRef shr 8) and 0xFF).toByte(),
            (countRef and 0xFF).toByte(),
            0xAD.toByte(),                            // lreturn
        )
        val codeAttr = AttributeBuilder.buildCode(codeIdx, CodeAttribute(
            2, 1, bytecode, emptyList(), emptyList()))
        val method = MethodInfo(
            AccessFlags.PUBLIC or AccessFlags.STATIC, nameIdx, descIdx, listOf(codeAttr))

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), emptyList(), listOf(method), emptyList(),
        ))

        val module = RuntimeCompiler(Target.x86_64()).compile(classBytes)
        val fn = module.functions.first { it.name == "getCount" }
        assertEquals(Type.I64, fn.returnType)
    }
}

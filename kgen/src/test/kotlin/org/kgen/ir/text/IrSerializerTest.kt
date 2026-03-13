package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrSerializerTest {

    private val serializer = IrSerializer()

    @Test
    fun `round-trip empty module`() {
        val mod = module("empty") {}
        val bytes = serializer.serialize(mod)
        val restored = serializer.deserialize(bytes)
        assertEquals(mod.name, restored.name)
        assertEquals(mod.functions.size, restored.functions.size)
    }

    @Test
    fun `round-trip simple function`() {
        val mod = module("test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1))
                    ret(result)
                }
            }
        }

        val bytes = serializer.serialize(mod)
        val restored = serializer.deserialize(bytes)

        assertEquals(mod.name, restored.name)
        assertEquals(1, restored.functions.size)
        val fn = restored.functions[0]
        assertEquals("add", fn.name)
        assertEquals(2, fn.params.size)
        assertEquals(Type.I32, fn.returnType)
        assertEquals(1, fn.blocks.size)
        assertEquals(2, fn.blocks[0].instructions.size)
    }

    @Test
    fun `round-trip globals`() {
        val mod = module("globals") {
            global("x", Type.I32, i32(42), isConstant = true)
            global("y", Type.F64, f64(3.14))
        }

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals(2, restored.globals.size)
        assertEquals("x", restored.globals[0].name)
        assertTrue(restored.globals[0].isConstant)
        assertEquals(42, (restored.globals[0].initializer as Constant.I32).value)
    }

    @Test
    fun `round-trip all primitive types`() {
        val types = listOf(
            Type.I1, Type.I8, Type.I16, Type.I32, Type.I64, Type.I128, Type.IntN(24),
            Type.F16, Type.BF16, Type.F32, Type.F64, Type.F80, Type.F128,
            Type.Void, Type.Label, Type.Metadata, Type.Token,
            Type.OpaquePointer, Type.Pointer(Type.I32), Type.Pointer(Type.I64, 1),
            Type.Reference(Type.I32, true), Type.Reference(Type.I32, false),
            Type.WeakReference(Type.I32),
            Type.Array(Type.I8, 100),
            Type.Vector(Type.F32, 4), Type.Vector(Type.F32, 4, scalable = true),
            Type.Struct(null, listOf(Type.I32, Type.F64)),
            Type.Struct("Point", listOf(Type.F64, Type.F64), packed = true),
            Type.Function(listOf(Type.I32), Type.I64),
            Type.ClassRef("MyClass"),
            Type.InterfaceRef("MyInterface"),
            Type.Nullable(Type.I32),
        )

        for (type in types) {
            val mod = module("types") { global("g", type) }
            val restored = serializer.deserialize(serializer.serialize(mod))
            assertEquals(type, restored.globals[0].type, "Type round-trip failed for $type")
        }
    }

    @Test
    fun `round-trip module metadata`() {
        val mod = module("meta") {
            targetTriple("x86_64-unknown-linux-gnu")
            dataLayout("e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128")
            sourceFile("test.c")
            targetFeature("+sse4.2")
        }

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals("x86_64-unknown-linux-gnu", restored.targetTriple)
        assertNotNull(restored.dataLayout)
        assertEquals("test.c", restored.sourceFile)
        assertTrue(restored.targetFeatures.contains("+sse4.2"))
    }

    @Test
    fun `round-trip structs`() {
        val mod = module("structs") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
            struct("Packed", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)
        }

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals(2, restored.structs.size)
        assertEquals("Point", restored.structs[0].name)
        assertTrue(restored.structs[1].packed)
    }

    @Test
    fun `round-trip complex function`() {
        val mod = module("complex") {
            function("fib", listOf(Param("n", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SLE, param(0), i32(1))
                    condBr(cmp, "base", "recurse")
                }
                block("base") {
                    ret(param(0))
                }
                block("recurse") {
                    val n1 = sub(param(0), i32(1))
                    val n2 = sub(param(0), i32(2))
                    val f1 = call("fib", listOf(n1), Type.I32)
                    val f2 = call("fib", listOf(n2), Type.I32)
                    val result = add(f1!!, f2!!)
                    ret(result)
                }
            }
        }

        val bytes = serializer.serialize(mod)
        val restored = serializer.deserialize(bytes)

        assertEquals(1, restored.functions.size)
        val fn = restored.functions[0]
        assertEquals(3, fn.blocks.size)
        assertEquals("entry", fn.blocks[0].label)
        assertEquals("base", fn.blocks[1].label)
        assertEquals("recurse", fn.blocks[2].label)
    }

    @Test
    fun `round-trip constants`() {
        val mod = module("consts") {
            global("a", Type.I32, i32(42))
            global("b", Type.I64, i64(-1L))
            global("c", Type.F32, f32(3.14f))
            global("d", Type.F64, f64(2.718))
            global("e", Type.Array(Type.I8, 5), Constant.StringConst("test"))
        }

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals(5, restored.globals.size)
        val str = restored.globals[4].initializer as Constant.StringConst
        assertEquals("test", str.value)
    }

    @Test
    fun `invalid magic throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            serializer.deserialize(byteArrayOf(0, 0, 0, 0))
        }
    }

    @Test
    fun `round-trip preserves printer output`() {
        val mod = module("roundtrip") {
            function("id", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") { ret(param(0)) }
            }
        }

        val original = IrPrinter.print(mod)
        val restored = serializer.deserialize(serializer.serialize(mod))
        val restoredText = IrPrinter.print(restored)
        assertEquals(original, restoredText)
    }

    @Test
    fun `round-trip module constraints`() {
        val mod = Module(
            name = "constrained",
            constraints = IrConstraints.NATIVE,
            functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
            ),
        )

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertNotNull(restored.constraints)
        assertEquals(mod.constraints, restored.constraints)
        assertTrue(IrCategory.ARITHMETIC in restored.constraints!!)
        assertTrue(IrCategory.MEMORY in restored.constraints!!)
        assertFalse(IrCategory.OBJECT in restored.constraints!!)
    }

    @Test
    fun `round-trip null constraints`() {
        val mod = Module(name = "unconstrained")
        val restored = serializer.deserialize(serializer.serialize(mod))
        assertNull(restored.constraints)
    }

    @Test
    fun `round-trip submodules`() {
        val mod = Module(
            name = "with_submodules",
            functions = listOf(
                IrFunction("native_fn", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
                IrFunction("managed_fn", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
            ),
            globals = listOf(Global("g", Type.I32)),
            submodules = listOf(
                Submodule("native_part", IrConstraints.NATIVE, listOf("native_fn"), listOf("g")),
                Submodule("managed_part", IrConstraints.MANAGED_VM, listOf("managed_fn"), emptyList()),
            ),
        )

        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals(2, restored.submodules.size)

        val native = restored.submodules[0]
        assertEquals("native_part", native.name)
        assertEquals(IrConstraints.NATIVE, native.constraints)
        assertEquals(listOf("native_fn"), native.functions)
        assertEquals(listOf("g"), native.globals)

        val managed = restored.submodules[1]
        assertEquals("managed_part", managed.name)
        assertEquals(IrConstraints.MANAGED_VM, managed.constraints)
        assertEquals(listOf("managed_fn"), managed.functions)
        assertTrue(managed.globals.isEmpty())
    }

    @Test
    fun `round-trip constraints and submodules preserve printer output`() {
        val mod = Module(
            name = "full",
            constraints = IrConstraints.MIXED,
            functions = listOf(
                IrFunction("f", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null))))),
            ),
            submodules = listOf(
                Submodule("sub", IrConstraints.NATIVE, listOf("f"), emptyList()),
            ),
        )

        val original = IrPrinter.print(mod)
        val restored = serializer.deserialize(serializer.serialize(mod))
        assertEquals(original, IrPrinter.print(restored))
    }
}

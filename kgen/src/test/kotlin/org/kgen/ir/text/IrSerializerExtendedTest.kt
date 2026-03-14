package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrSerializerExtendedTest {

    private val serializer = IrSerializer()

    private fun roundTrip(mod: Module): Module {
        val bytes = serializer.serialize(mod)
        return serializer.deserialize(bytes)
    }

    @Test
    fun `round-trip module name`() {
        val mod = module("myModule") {}
        val restored = roundTrip(mod)
        assertEquals("myModule", restored.name)
    }

    @Test
    fun `round-trip module name with special chars`() {
        val mod = module("test-module_v3.0") {}
        val restored = roundTrip(mod)
        assertEquals("test-module_v3.0", restored.name)
    }

    @Test
    fun `round-trip empty function list`() {
        val mod = module("empty") {}
        val restored = roundTrip(mod)
        assertTrue(restored.functions.isEmpty())
        assertTrue(restored.globals.isEmpty())
        assertTrue(restored.structs.isEmpty())
    }

    @Test
    fun `round-trip external function`() {
        val mod = module("test") {
            function("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32, isExternal = true)
        }
        val restored = roundTrip(mod)
        assertEquals(1, restored.functions.size)
        assertTrue(restored.functions[0].isExternal)
        assertEquals("puts", restored.functions[0].name)
        assertEquals(Type.I32, restored.functions[0].returnType)
    }

    @Test
    fun `round-trip multiple external functions`() {
        val mod = module("test") {
            function("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer, isExternal = true)
            function("free", listOf(Param("ptr", Type.OpaquePointer)), Type.Void, isExternal = true)
        }
        val restored = roundTrip(mod)
        assertEquals(2, restored.functions.size)
        assertEquals("malloc", restored.functions[0].name)
        assertEquals("free", restored.functions[1].name)
    }

    @Test
    fun `round-trip function with sub instruction`() {
        val mod = module("test") {
            function("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = sub(param(0), param(1))
                    ret(result)
                }
            }
        }
        val restored = roundTrip(mod)
        val fn = restored.functions[0]
        assertEquals(1, fn.blocks.size)
        assertEquals(2, fn.blocks[0].instructions.size)
    }

    @Test
    fun `round-trip function with mul`() {
        val mod = module("test") {
            function("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64) {
                block("entry") {
                    val result = mul(param(0), param(1))
                    ret(result)
                }
            }
        }
        val restored = roundTrip(mod)
        assertEquals(Type.I64, restored.functions[0].returnType)
        assertEquals(2, restored.functions[0].params.size)
    }

    @Test
    fun `round-trip function with multiple blocks`() {
        val mod = module("test") {
            function("branch", listOf(Param("c", Type.I1)), Type.I32) {
                block("entry") {
                    condBr(param(0), BlockRef("yes"), BlockRef("no"))
                }
                block("yes") {
                    ret(i32(1))
                }
                block("no") {
                    ret(i32(0))
                }
            }
        }
        val restored = roundTrip(mod)
        val fn = restored.functions[0]
        assertEquals(3, fn.blocks.size)
        assertEquals("entry", fn.blocks[0].label)
        assertEquals("yes", fn.blocks[1].label)
        assertEquals("no", fn.blocks[2].label)
    }

    @Test
    fun `round-trip void function`() {
        val mod = module("test") {
            function("noop", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        val restored = roundTrip(mod)
        assertEquals(Type.Void, restored.functions[0].returnType)
        assertTrue(restored.functions[0].params.isEmpty())
    }

    @Test
    fun `round-trip global without initializer`() {
        val mod = module("test") {
            global("buf", Type.Array(Type.I8, 512))
        }
        val restored = roundTrip(mod)
        assertEquals(1, restored.globals.size)
        assertEquals("buf", restored.globals[0].name)
        assertNull(restored.globals[0].initializer)
    }

    @Test
    fun `round-trip constant f64 global`() {
        val mod = module("test") {
            global("pi", Type.F64, f64(3.14159), isConstant = true)
        }
        val restored = roundTrip(mod)
        assertTrue(restored.globals[0].isConstant)
        val init = restored.globals[0].initializer as Constant.F64
        assertEquals(3.14159, init.value, 0.0001)
    }

    @Test
    fun `round-trip multiple globals`() {
        val mod = module("test") {
            global("a", Type.I32, i32(10))
            global("b", Type.I64, i64(20))
            global("c", Type.F32, f32(1.5f))
            global("d", Type.F64, f64(2.5))
        }
        val restored = roundTrip(mod)
        assertEquals(4, restored.globals.size)
        assertEquals("a", restored.globals[0].name)
        assertEquals("d", restored.globals[3].name)
    }

    @Test
    fun `round-trip struct definitions`() {
        val mod = module("test") {
            struct("Vec2", listOf(Param("x", Type.F32), Param("y", Type.F32)))
        }
        val restored = roundTrip(mod)
        assertEquals(1, restored.structs.size)
        assertEquals("Vec2", restored.structs[0].name)
        assertEquals(2, restored.structs[0].fields.size)
    }

    @Test
    fun `round-trip packed struct`() {
        val mod = module("test") {
            struct("Packed", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)
        }
        val restored = roundTrip(mod)
        assertTrue(restored.structs[0].packed)
    }

    @Test
    fun `round-trip i8 constants`() {
        val mod = module("test") {
            global("b", Type.I8, i8(127))
        }
        val restored = roundTrip(mod)
        assertEquals(127.toByte(), (restored.globals[0].initializer as Constant.I8).value)
    }

    @Test
    fun `round-trip i16 constants`() {
        val mod = module("test") {
            global("s", Type.I16, i16(32000))
        }
        val restored = roundTrip(mod)
        assertEquals(32000.toShort(), (restored.globals[0].initializer as Constant.I16).value)
    }

    @Test
    fun `round-trip negative constants`() {
        val mod = module("test") {
            global("neg", Type.I32, i32(-42))
        }
        val restored = roundTrip(mod)
        assertEquals(-42, (restored.globals[0].initializer as Constant.I32).value)
    }

    @Test
    fun `round-trip i128 constant`() {
        val mod = module("test") {
            global("big", Type.I128, i128(Long.MAX_VALUE))
        }
        val restored = roundTrip(mod)
        assertEquals(Long.MAX_VALUE, (restored.globals[0].initializer as Constant.I128).value)
    }

    @Test
    fun `round-trip target features`() {
        val mod = module("test") {
            targetFeature("+sse4.2")
            targetFeature("+avx")
        }
        val restored = roundTrip(mod)
        assertTrue(restored.targetFeatures.contains("+sse4.2"))
        assertTrue(restored.targetFeatures.contains("+avx"))
    }

    @Test
    fun `round-trip function with call`() {
        val mod = module("test") {
            function("caller", emptyList(), Type.I32) {
                block("entry") {
                    val r = call("helper", listOf(i32(42)), Type.I32)
                    ret(r!!)
                }
            }
        }
        val restored = roundTrip(mod)
        val fn = restored.functions[0]
        assertEquals(2, fn.blocks[0].instructions.size)
    }

    @Test
    fun `round-trip function with memory ops`() {
        val mod = module("test") {
            function("mem", emptyList(), Type.I32) {
                block("entry") {
                    val p = alloca(Type.I32, align = 4)
                    store(i32(99), p, align = 4)
                    val v = load(Type.I32, p, align = 4)
                    ret(v)
                }
            }
        }
        val restored = roundTrip(mod)
        assertEquals(4, restored.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun `round-trip function with phi and branch`() {
        val mod = module("test") {
            function("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), param(1))
                    condBr(cmp, BlockRef("then"), BlockRef("else"))
                }
                block("then") { br(BlockRef("merge")) }
                block("else") { br(BlockRef("merge")) }
                block("merge") {
                    val r = phi(Type.I32, listOf(param(0) to BlockRef("then"), param(1) to BlockRef("else")))
                    ret(r)
                }
            }
        }
        val restored = roundTrip(mod)
        assertEquals(4, restored.functions[0].blocks.size)
    }

    @Test
    fun `serialized bytes are non-empty`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        val bytes = serializer.serialize(mod)
        assertTrue(bytes.size > 8, "Should have magic + version + data")
    }

    @Test
    fun `round-trip preserves printer output for complex module`() {
        val mod = module("complex") {
            targetTriple("aarch64-unknown-linux-gnu")
            sourceFile("test.c")
            global("x", Type.I32, i32(0))
            function("inc", listOf(Param("n", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), i32(1))
                    ret(result)
                }
            }
        }
        val original = IrPrinter.print(mod)
        val restored = roundTrip(mod)
        val restoredText = IrPrinter.print(restored)
        assertEquals(original, restoredText)
    }

    @Test
    fun `truncated bytes throw`() {
        val mod = module("test") {}
        val bytes = serializer.serialize(mod)
        val truncated = bytes.copyOfRange(0, 6) // only magic + partial version
        assertThrows(Exception::class.java) {
            serializer.deserialize(truncated)
        }
    }

    @Test
    fun `round-trip string constant`() {
        val mod = module("test") {
            global("msg", Type.Array(Type.I8, 6), Constant.StringConst("hello"))
        }
        val restored = roundTrip(mod)
        val str = restored.globals[0].initializer as Constant.StringConst
        assertEquals("hello", str.value)
    }

    @Test
    fun `round-trip type alias`() {
        val mod = module("test") {
            typeAlias("MyInt", Type.I32)
        }
        val restored = roundTrip(mod)
        assertEquals(1, restored.aliases.size)
        assertEquals("MyInt", restored.aliases[0].name)
        assertEquals(Type.I32, restored.aliases[0].type)
    }

    @Test
    fun `round-trip metadata`() {
        val mod = module("test") {
            metadata("debug", MetadataValue.StringMD("test-debug"))
        }
        val restored = roundTrip(mod)
        val md = restored.metadata["debug"]
        assertNotNull(md)
        assertTrue(md is MetadataValue.StringMD)
        assertEquals("test-debug", (md as MetadataValue.StringMD).value)
    }
}

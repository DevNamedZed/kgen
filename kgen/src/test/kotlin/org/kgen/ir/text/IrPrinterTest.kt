package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrPrinterTest {

    @Test
    fun `print simple add function`() {
        val mod = module("test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1))
                    ret(result)
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("module \"test\""))
        assertTrue(text.contains("define i32 @add(i32 %a, i32 %b)"))
        assertTrue(text.contains("= add i32 %a, %b"))
        assertTrue(text.contains("ret i32"))
    }

    @Test
    fun `print globals`() {
        val mod = module("globals") {
            global("x", Type.I32, i32(42), isConstant = true)
            global("buf", Type.Array(Type.I8, 256))
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("@x = constant i32 = 42"))
        assertTrue(text.contains("@buf = global [256 x i8]"))
    }

    @Test
    fun `print structs`() {
        val mod = module("structs") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("struct %Point { x: f64, y: f64 }"))
    }

    @Test
    fun `print branch and phi`() {
        val mod = module("phi") {
            function("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), param(1))
                    condBr(cmp, BlockRef("then"), BlockRef("else"))
                }
                block("then") {
                    br(BlockRef("merge"))
                }
                block("else") {
                    br(BlockRef("merge"))
                }
                block("merge") {
                    val result = phi(Type.I32, listOf(param(0) to BlockRef("then"), param(1) to BlockRef("else")))
                    ret(result)
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("icmp sgt"))
        assertTrue(text.contains("br i1"))
        assertTrue(text.contains("phi i32"))
        assertTrue(text.contains("[%a, %then]"))
    }

    @Test
    fun `print external function`() {
        val mod = module("ext") {
            function("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32, isExternal = true)
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("declare i32 @puts(ptr %s)"))
    }

    @Test
    fun `print memory operations`() {
        val mod = module("mem") {
            function("mem_ops", emptyList(), Type.Void) {
                block("entry") {
                    val p = alloca(Type.I32, align = 4)
                    store(i32(42), p, align = 4)
                    val v = load(Type.I32, p, align = 4)
                    ret()
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("alloca i32, align 4"))
        assertTrue(text.contains("store i32 42, ptr"))
        assertTrue(text.contains("load i32, ptr"))
    }

    @Test
    fun `print conversions`() {
        val mod = module("conv") {
            function("convert", listOf(Param("x", Type.I32)), Type.I64) {
                block("entry") {
                    val ext = sext(param(0), Type.I64)
                    ret(ext)
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("sext i32 %x to i64"))
    }

    @Test
    fun `print call`() {
        val mod = module("calls") {
            function("caller", emptyList(), Type.I32) {
                block("entry") {
                    val result = call("callee", listOf(i32(10), i32(20)), Type.I32)
                    ret(result!!)
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("call i32 @callee(i32 10, i32 20)"))
    }

    @Test
    fun `print type strings`() {
        assertEquals("i32", IrPrinter.typeStr(Type.I32))
        assertEquals("f64", IrPrinter.typeStr(Type.F64))
        assertEquals("void", IrPrinter.typeStr(Type.Void))
        assertEquals("ptr", IrPrinter.typeStr(Type.OpaquePointer))
        assertEquals("[10 x i32]", IrPrinter.typeStr(Type.Array(Type.I32, 10)))
        assertEquals("<4 x f32>", IrPrinter.typeStr(Type.Vector(Type.F32, 4)))
        assertEquals("{ i32, f64 }", IrPrinter.typeStr(Type.Struct(null, listOf(Type.I32, Type.F64))))
        assertEquals("%Point", IrPrinter.typeStr(Type.Struct("Point", listOf(Type.F64, Type.F64))))
        assertEquals("i32 (i32, i32)", IrPrinter.typeStr(Type.Function(listOf(Type.I32, Type.I32), Type.I32)))
        assertEquals("ref<i32>?", IrPrinter.typeStr(Type.Reference(Type.I32, nullable = true)))
        assertEquals("ref<i32>", IrPrinter.typeStr(Type.Reference(Type.I32, nullable = false)))
    }

    @Test
    fun `print fast math flags`() {
        val mod = module("fm") {
            function("fast_add", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val result = fadd(param(0), param(1), FastMathFlags.FAST)
                    ret(result)
                }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fadd nnan ninf nsz arcp contract afn reassoc"))
    }

    @Test
    fun `print switch`() {
        val mod = module("sw") {
            function("dispatch", listOf(Param("x", Type.I32)), Type.Void) {
                block("entry") {
                    switch(param(0), BlockRef("default"), listOf(i32(0) to BlockRef("case0"), i32(1) to BlockRef("case1")))
                }
                block("case0") { ret() }
                block("case1") { ret() }
                block("default") { ret() }
            }
        }

        val text = IrPrinter.print(mod)
        assertTrue(text.contains("switch i32 %x, label %default"))
    }

    @Test
    fun `prints module constraints`() {
        val mod = Module(name = "test", constraints = IrConstraints.NATIVE)
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("constraints = {"), text)
        assertTrue(text.contains("ARITHMETIC"), text)
        assertTrue(text.contains("MEMORY"), text)
    }

    @Test
    fun `prints submodules`() {
        val mod = Module(
            name = "test",
            submodules = listOf(
                Submodule("native_part", IrConstraints.NATIVE, listOf("foo"), listOf("bar")),
            ),
        )
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("submodule \"native_part\""), text)
        assertTrue(text.contains("function @foo"), text)
        assertTrue(text.contains("global @bar"), text)
    }
}

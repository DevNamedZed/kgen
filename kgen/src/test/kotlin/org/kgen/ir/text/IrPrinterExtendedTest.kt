package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrPrinterExtendedTest {

    @Test
    fun `print void function with no params`() {
        val mod = module("test") {
            function("noop", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("define void @noop()"))
        assertTrue(text.contains("ret void"))
    }

    @Test
    fun `print sub instruction`() {
        val mod = module("test") {
            function("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = sub(param(0), param(1))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= sub i32 %a, %b"))
    }

    @Test
    fun `print mul instruction`() {
        val mod = module("test") {
            function("mul", listOf(Param("x", Type.I64), Param("y", Type.I64)), Type.I64) {
                block("entry") {
                    val result = mul(param(0), param(1))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= mul i64 %x, %y"))
    }

    @Test
    fun `print udiv and sdiv`() {
        val mod = module("test") {
            function("divs", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val u = udiv(param(0), param(1))
                    val s = sdiv(param(0), param(1))
                    ret(s)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= udiv i32 %a, %b"))
        assertTrue(text.contains("= sdiv i32 %a, %b"))
    }

    @Test
    fun `print urem and srem`() {
        val mod = module("test") {
            function("rems", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val u = urem(param(0), param(1))
                    val s = srem(param(0), param(1))
                    ret(s)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= urem i32"))
        assertTrue(text.contains("= srem i32"))
    }

    @Test
    fun `print neg instruction`() {
        val mod = module("test") {
            function("negate", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val result = neg(param(0))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= neg i32 %x"))
    }

    @Test
    fun `print bitwise and or xor`() {
        val mod = module("test") {
            function("bits", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r1 = and(param(0), param(1))
                    val r2 = or(param(0), param(1))
                    val r3 = xor(param(0), param(1))
                    ret(r3)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= and i32 %a, %b"))
        assertTrue(text.contains("= or i32 %a, %b"))
        assertTrue(text.contains("= xor i32 %a, %b"))
    }

    @Test
    fun `print shift instructions`() {
        val mod = module("test") {
            function("shifts", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r1 = shl(param(0), param(1))
                    val r2 = lshr(param(0), param(1))
                    val r3 = ashr(param(0), param(1))
                    ret(r3)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= shl i32 %a, %b"))
        assertTrue(text.contains("= lshr i32 %a, %b"))
        assertTrue(text.contains("= ashr i32 %a, %b"))
    }

    @Test
    fun `print nuw nsw flags on add`() {
        val mod = module("test") {
            function("flagged", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1), nuw = true, nsw = true)
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= add nuw nsw i32 %a, %b"))
    }

    @Test
    fun `print exact sdiv`() {
        val mod = module("test") {
            function("exact", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = sdiv(param(0), param(1), exact = true)
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= sdiv exact i32 %a, %b"))
    }

    @Test
    fun `print float arithmetic`() {
        val mod = module("test") {
            function("floats", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32) {
                block("entry") {
                    val r1 = fadd(param(0), param(1))
                    val r2 = fsub(param(0), param(1))
                    val r3 = fmul(param(0), param(1))
                    val r4 = fdiv(param(0), param(1))
                    ret(r4)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= fadd f32"))
        assertTrue(text.contains("= fsub f32"))
        assertTrue(text.contains("= fmul f32"))
        assertTrue(text.contains("= fdiv f32"))
    }

    @Test
    fun `print fcmp instruction`() {
        val mod = module("test") {
            function("compare", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1) {
                block("entry") {
                    val result = fcmp(FCmpPredicate.OLT, param(0), param(1))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fcmp olt f64"))
    }

    @Test
    fun `print icmp predicates`() {
        val mod = module("test") {
            function("cmps", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1) {
                block("entry") {
                    val r1 = icmp(ICmpPredicate.EQ, param(0), param(1))
                    val r2 = icmp(ICmpPredicate.NE, param(0), param(1))
                    val r3 = icmp(ICmpPredicate.ULT, param(0), param(1))
                    ret(r3)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("icmp eq i32"))
        assertTrue(text.contains("icmp ne i32"))
        assertTrue(text.contains("icmp ult i32"))
    }

    @Test
    fun `print zext conversion`() {
        val mod = module("test") {
            function("extend", listOf(Param("x", Type.I8)), Type.I32) {
                block("entry") {
                    val ext = zext(param(0), Type.I32)
                    ret(ext)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("zext i8 %x to i32"))
    }

    @Test
    fun `print trunc conversion`() {
        val mod = module("test") {
            function("truncate", listOf(Param("x", Type.I64)), Type.I8) {
                block("entry") {
                    val t = trunc(param(0), Type.I8)
                    ret(t)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("inttrunc i64 %x to i8"))
    }

    @Test
    fun `print fpext and fptrunc`() {
        val mod = module("test") {
            function("fp", listOf(Param("x", Type.F32)), Type.F32) {
                block("entry") {
                    val ext = fpext(param(0), Type.F64)
                    val tr = fptrunc(ext, Type.F32)
                    ret(tr)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("fpext f32 %x to f64"))
        assertTrue(text.contains("fptrunc f64"))
    }

    @Test
    fun `print select instruction`() {
        val mod = module("test") {
            function("sel", listOf(Param("c", Type.I1), Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = select(param(0), param(1), param(2))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("select i1 %c, i32 %a, i32 %b"))
    }

    @Test
    fun `print unreachable instruction`() {
        val mod = module("test") {
            function("never", emptyList(), Type.Void) {
                block("entry") {
                    unreachable()
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("unreachable"))
    }

    @Test
    fun `print target triple and datalayout`() {
        val mod = module("meta") {
            targetTriple("x86_64-unknown-linux-gnu")
            dataLayout("e-m:e-p:64:64")
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("target triple = \"x86_64-unknown-linux-gnu\""))
        assertTrue(text.contains("target datalayout = \"e-m:e-p:64:64\""))
    }

    @Test
    fun `print source file`() {
        val mod = module("src") {
            sourceFile("main.c")
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("source_file \"main.c\""))
    }

    @Test
    fun `print global with alignment`() {
        val mod = module("test") {
            global("aligned", Type.I64, i64(0), align = 8)
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("@aligned = global i64 = 0, align 8"))
    }

    @Test
    fun `print constant global`() {
        val mod = module("test") {
            global("pi", Type.F64, f64(3.14159), isConstant = true)
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("@pi = constant f64 = 3.14159"))
    }

    @Test
    fun `print gep instruction`() {
        val mod = module("test") {
            function("gep_test", listOf(Param("p", Type.OpaquePointer)), Type.OpaquePointer) {
                block("entry") {
                    val result = gep(Type.I32, param(0), i32(0))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("getelementptr inbounds i32, ptr %p"))
    }

    @Test
    fun `print not instruction`() {
        val mod = module("test") {
            function("bitnot", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val result = not(param(0))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= not i32 %x"))
    }

    @Test
    fun `print multiple basic blocks`() {
        val mod = module("test") {
            function("multi", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val c = icmp(ICmpPredicate.SGT, param(0), i32(10))
                    condBr(c, "big", "small")
                }
                block("big") {
                    ret(i32(1))
                }
                block("small") {
                    ret(i32(0))
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("entry:"))
        assertTrue(text.contains("big:"))
        assertTrue(text.contains("small:"))
        assertTrue(text.contains("br i1"))
    }

    @Test
    fun `print void call`() {
        val mod = module("test") {
            function("caller", emptyList(), Type.Void) {
                block("entry") {
                    call("sideEffect", emptyList(), Type.Void)
                    ret()
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("call void @sideEffect()"))
    }

    @Test
    fun `print i64 type in function`() {
        val mod = module("test") {
            function("identity", listOf(Param("x", Type.I64)), Type.I64) {
                block("entry") {
                    ret(param(0))
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("define i64 @identity(i64 %x)"))
        assertTrue(text.contains("ret i64 %x"))
    }

    @Test
    fun `print struct type string with name`() {
        assertEquals("%MyStruct", IrPrinter.typeStr(Type.Struct("MyStruct", listOf(Type.I32, Type.I64))))
    }

    @Test
    fun `print packed struct type`() {
        val text = IrPrinter.typeStr(Type.Struct(null, listOf(Type.I8, Type.I32), packed = true))
        assertEquals("<{ i8, i32 }>", text)
    }

    @Test
    fun `print function type`() {
        val text = IrPrinter.typeStr(Type.Function(listOf(Type.I32, Type.F64), Type.Void))
        assertEquals("void (i32, f64)", text)
    }

    @Test
    fun `print nested array type`() {
        val text = IrPrinter.typeStr(Type.Array(Type.Array(Type.I32, 3), 4))
        assertEquals("[4 x [3 x i32]]", text)
    }

    @Test
    fun `print struct definition`() {
        val mod = module("test") {
            struct("Color", listOf(Param("r", Type.I8), Param("g", Type.I8), Param("b", Type.I8)))
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("struct %Color { r: i8, g: i8, b: i8 }"))
    }

    @Test
    fun `print packed struct definition`() {
        val mod = module("test") {
            struct("Packed", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("struct %Packed packed { a: i8, b: i32 }"))
    }

    @Test
    fun `print all icmp predicates`() {
        val preds = ICmpPredicate.entries
        for (pred in preds) {
            val mod = module("test") {
                function("cmp", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1) {
                    block("entry") {
                        val r = icmp(pred, param(0), param(1))
                        ret(r)
                    }
                }
            }
            val text = IrPrinter.print(mod)
            assertTrue(text.contains("icmp ${pred.name.lowercase()} i32"), "Missing pred $pred")
        }
    }

    @Test
    fun `print multiple globals`() {
        val mod = module("test") {
            global("a", Type.I32, i32(1))
            global("b", Type.I64, i64(2))
            global("c", Type.F32, f32(3.0f))
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("@a = global i32 = 1"))
        assertTrue(text.contains("@b = global i64 = 2"))
        assertTrue(text.contains("@c = global f32 = 3.0"))
    }

    @Test
    fun `print module name with special characters`() {
        val mod = module("my-module_v2.0") {}
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("module \"my-module_v2.0\""))
    }

    @Test
    fun `print constant valStr`() {
        assertEquals("42", IrPrinter.constStr(Constant.I32(42)))
        assertEquals("-1", IrPrinter.constStr(Constant.I32(-1)))
        assertEquals("3.14", IrPrinter.constStr(Constant.F64(3.14)))
        assertEquals("null", IrPrinter.constStr(Constant.NullPtr))
    }

    @Test
    fun `print valStr for parameter`() {
        val p = Parameter("myParam", Type.I32, 0)
        assertEquals("%myParam", IrPrinter.valStr(p))
    }

    @Test
    fun `print valStr for global ref`() {
        val g = GlobalRef("myGlobal", Type.I32)
        assertEquals("@myGlobal", IrPrinter.valStr(g))
    }

    @Test
    fun `print frem instruction`() {
        val mod = module("test") {
            function("remainder", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val result = frem(param(0), param(1))
                    ret(result)
                }
            }
        }
        val text = IrPrinter.print(mod)
        assertTrue(text.contains("= frem f64"))
    }
}

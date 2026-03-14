package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.*

class IrParserExtendedTest {

    private fun roundTrip(mod: Module): String {
        val printed1 = IrPrinter.print(mod)
        val parsed = IrParser.parse(printed1)
        val printed2 = IrPrinter.print(parsed)
        assertEquals(printed1, printed2, "Round-trip mismatch")
        return printed1
    }

    @Test
    fun `round-trip empty module`() {
        val mod = module("empty") {}
        roundTrip(mod)
    }

    @Test
    fun `round-trip module name with special chars`() {
        val mod = module("my-module_v2.0") {}
        roundTrip(mod)
    }

    @Test
    fun `round-trip target triple`() {
        val mod = module("triple") {
            targetTriple("x86_64-unknown-linux-gnu")
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip datalayout`() {
        val mod = module("layout") {
            dataLayout("e-m:e-p270:32:32-i64:64-f80:128-n8:16:32:64-S128")
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip source file`() {
        val mod = module("src") {
            sourceFile("hello.c")
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip target features`() {
        val mod = module("feat") {
            targetFeature("+sse4.2")
            targetFeature("+avx2")
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip identity function`() {
        val mod = module("test") {
            function("id", listOf(Param("x", Type.I64)), Type.I64) {
                block("entry") {
                    ret(param(0))
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip sub instruction`() {
        val mod = module("test") {
            function("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = sub(param(0), param(1))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip mul instruction`() {
        val mod = module("test") {
            function("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = mul(param(0), param(1))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip udiv and sdiv`() {
        val mod = module("test") {
            function("divs", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val u = udiv(param(0), param(1))
                    val s = sdiv(param(0), param(1))
                    ret(s)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip urem and srem`() {
        val mod = module("test") {
            function("rems", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val u = urem(param(0), param(1))
                    val s = srem(param(0), param(1))
                    ret(s)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip neg instruction`() {
        val mod = module("test") {
            function("negate", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val result = neg(param(0))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip bitwise ops`() {
        val mod = module("test") {
            function("bits", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r1 = and(param(0), param(1))
                    val r2 = or(r1, param(1))
                    val r3 = xor(r2, param(0))
                    ret(r3)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip shift instructions`() {
        val mod = module("test") {
            function("shifts", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val r1 = shl(param(0), param(1))
                    val r2 = lshr(r1, param(1))
                    val r3 = ashr(r2, param(1))
                    ret(r3)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip nuw nsw flags`() {
        val mod = module("test") {
            function("flagged", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1), nuw = true, nsw = true)
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip float arithmetic`() {
        val mod = module("test") {
            function("fp", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val r1 = fadd(param(0), param(1))
                    val r2 = fsub(r1, param(1))
                    val r3 = fmul(r2, param(0))
                    val r4 = fdiv(r3, param(1))
                    ret(r4)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip fcmp instruction`() {
        val mod = module("test") {
            function("compare", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1) {
                block("entry") {
                    val result = fcmp(FCmpPredicate.OLT, param(0), param(1))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip zext conversion`() {
        val mod = module("test") {
            function("extend", listOf(Param("x", Type.I8)), Type.I32) {
                block("entry") {
                    val ext = zext(param(0), Type.I32)
                    ret(ext)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip trunc conversion`() {
        val mod = module("test") {
            function("truncate", listOf(Param("x", Type.I64)), Type.I8) {
                block("entry") {
                    val t = trunc(param(0), Type.I8)
                    ret(t)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip fpext and fptrunc`() {
        val mod = module("test") {
            function("fp", listOf(Param("x", Type.F32)), Type.F32) {
                block("entry") {
                    val ext = fpext(param(0), Type.F64)
                    val tr = fptrunc(ext, Type.F32)
                    ret(tr)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip unreachable`() {
        val mod = module("test") {
            function("die", emptyList(), Type.Void) {
                block("entry") {
                    unreachable()
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip global without initializer`() {
        val mod = module("test") {
            global("buf", Type.Array(Type.I8, 1024))
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip constant global with f64`() {
        val mod = module("test") {
            global("euler", Type.F64, f64(2.718), isConstant = true)
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip multiple external functions`() {
        val mod = module("test") {
            function("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer, isExternal = true)
            function("free", listOf(Param("ptr", Type.OpaquePointer)), Type.Void, isExternal = true)
            function("memcpy", listOf(
                Param("dst", Type.OpaquePointer),
                Param("src", Type.OpaquePointer),
                Param("n", Type.I64)
            ), Type.OpaquePointer, isExternal = true)
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip not instruction`() {
        val mod = module("test") {
            function("bitnot", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val result = not(param(0))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @org.junit.jupiter.api.Disabled("Parser does not yet support struct definitions")
    @Test
    fun `round-trip struct definition`() {
        val mod = module("test") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
            struct("Rect", listOf(Param("w", Type.I32), Param("h", Type.I32)))
        }
        roundTrip(mod)
    }

    @org.junit.jupiter.api.Disabled("Parser does not yet support struct definitions")
    @Test
    fun `round-trip packed struct`() {
        val mod = module("test") {
            struct("Compact", listOf(Param("flag", Type.I8), Param("value", Type.I32)), packed = true)
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip chain of arithmetic`() {
        val mod = module("test") {
            function("chain", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val a = add(param(0), i32(1))
                    val b = mul(a, i32(2))
                    val c = sub(b, i32(3))
                    val d = sdiv(c, i32(4))
                    ret(d)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip complex control flow`() {
        val mod = module("test") {
            function("abs", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val neg = icmp(ICmpPredicate.SLT, param(0), i32(0))
                    condBr(neg, BlockRef("negate"), BlockRef("done"))
                }
                block("negate") {
                    val negated = sub(i32(0), param(0))
                    br(BlockRef("done"))
                }
                block("done") {
                    val result = phi(Type.I32, listOf(param(0) to BlockRef("entry"), i32(0) to BlockRef("negate")))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip i64 type`() {
        val mod = module("test") {
            function("widen", listOf(Param("a", Type.I32)), Type.I64) {
                block("entry") {
                    val ext = sext(param(0), Type.I64)
                    ret(ext)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip all icmp predicates`() {
        for (pred in ICmpPredicate.entries) {
            val mod = module("test") {
                function("cmp", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1) {
                    block("entry") {
                        val r = icmp(pred, param(0), param(1))
                        ret(r)
                    }
                }
            }
            roundTrip(mod)
        }
    }

    @Test
    fun `round-trip void call`() {
        val mod = module("test") {
            function("caller", emptyList(), Type.Void) {
                block("entry") {
                    call("sideEffect", emptyList(), Type.Void)
                    ret()
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip globals and functions together`() {
        val mod = module("combined") {
            global("counter", Type.I32, i32(0))
            function("increment", emptyList(), Type.Void, isExternal = true)
            function("getCounter", emptyList(), Type.I32) {
                block("entry") {
                    val result = call("increment", emptyList(), Type.Void)
                    ret(i32(0))
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip exact sdiv`() {
        val mod = module("test") {
            function("exact", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = sdiv(param(0), param(1), exact = true)
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip fast math flags`() {
        val mod = module("test") {
            function("fast", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64) {
                block("entry") {
                    val result = fadd(param(0), param(1), FastMathFlags.FAST)
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }
}

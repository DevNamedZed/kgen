package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IrParserTest {

    private fun roundTrip(mod: Module): String {
        val printed1 = IrPrinter.print(mod)
        val parsed = IrParser.parse(printed1)
        val printed2 = IrPrinter.print(parsed)
        assertEquals(printed1, printed2, "Round-trip mismatch")
        return printed1
    }

    @Test
    fun `round-trip simple add function`() {
        val mod = module("add_test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = add(param(0), param(1))
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip control flow with branches`() {
        val mod = module("cfg_test") {
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
        roundTrip(mod)
    }

    @Test
    fun `round-trip global variables`() {
        val mod = module("globals_test") {
            global("counter", Type.I32, i32(0))
            global("pi", Type.F64, f64(3.14), isConstant = true)
            global("buffer", Type.Array(Type.I8, 256))
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip external function declarations`() {
        val mod = module("extern_test") {
            function("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32, isExternal = true)
            function("exit", listOf(Param("code", Type.I32)), Type.Void, isExternal = true)
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip void function`() {
        val mod = module("void_test") {
            function("noop", emptyList(), Type.Void) {
                block("entry") {
                    ret()
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip multiple functions`() {
        val mod = module("multi_test") {
            function("helper", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val doubled = mul(param(0), i32(2))
                    ret(doubled)
                }
            }
            function("caller", listOf(Param("n", Type.I32)), Type.I32) {
                block("entry") {
                    val result = call("helper", listOf(param(0)), Type.I32)
                    ret(result!!)
                }
            }
            function("unused", emptyList(), Type.Void, isExternal = true)
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip memory operations`() {
        val mod = module("mem_test") {
            function("mem_ops", emptyList(), Type.Void) {
                block("entry") {
                    val p = alloca(Type.I32, align = 4)
                    store(i32(42), p, align = 4)
                    val v = load(Type.I32, p, align = 4)
                    ret()
                }
            }
        }
        roundTrip(mod)
    }

    @Test
    fun `round-trip comparison and select`() {
        val mod = module("cmp_select_test") {
            function("clamp", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val tooLow = icmp(ICmpPredicate.SLT, param(0), i32(0))
                    val clamped0 = select(tooLow, i32(0), param(0))
                    val tooHigh = icmp(ICmpPredicate.SGT, clamped0, i32(100))
                    val result = select(tooHigh, i32(100), clamped0)
                    ret(result)
                }
            }
        }
        roundTrip(mod)
    }
}

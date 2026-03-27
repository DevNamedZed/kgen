package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmComparisonTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class I32Comparison {

        @Test
        fun `i32 eq true`() {
            val bytes = buildWasmBytes {
                function("eq", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Eq()
                }
            }
            assertBothEqual(1L, bytes, "eq", 5L, 5L)
        }

        @Test
        fun `i32 eq false`() {
            val bytes = buildWasmBytes {
                function("eq", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Eq()
                }
            }
            assertBothEqual(0L, bytes, "eq", 5L, 6L)
        }

        @Test
        fun `i32 ne`() {
            val bytes = buildWasmBytes {
                function("ne", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Ne()
                }
            }
            assertBothEqual(1L, bytes, "ne", 5L, 6L)
            assertBothEqual(0L, bytes, "ne", 5L, 5L)
        }

        @Test
        fun `i32 eqz`() {
            val bytes = buildWasmBytes {
                function("eqz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Eqz()
                }
            }
            assertBothEqual(1L, bytes, "eqz", 0L)
            assertBothEqual(0L, bytes, "eqz", 42L)
        }

        @Test
        fun `i32 lt_s`() {
            val bytes = buildWasmBytes {
                function("lts", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LtS()
                }
            }
            assertBothEqual(1L, bytes, "lts", 3L, 5L)
            assertBothEqual(0L, bytes, "lts", 5L, 3L)
        }

        @Test
        fun `i32 lt_s with negative`() {
            val bytes = buildWasmBytes {
                function("lts", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LtS()
                }
            }
            val negOne = (-1L and 0xFFFFFFFFL)
            assertBothEqual(1L, bytes, "lts", negOne, 0L)
        }

        @Test
        fun `i32 le_s`() {
            val bytes = buildWasmBytes {
                function("les", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LeS()
                }
            }
            assertBothEqual(1L, bytes, "les", 5L, 5L)
            assertBothEqual(1L, bytes, "les", 3L, 5L)
            assertBothEqual(0L, bytes, "les", 6L, 5L)
        }

        @Test
        fun `i32 gt_s`() {
            val bytes = buildWasmBytes {
                function("gts", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32GtS()
                }
            }
            assertBothEqual(1L, bytes, "gts", 5L, 3L)
            assertBothEqual(0L, bytes, "gts", 3L, 5L)
        }

        @Test
        fun `i32 ge_s`() {
            val bytes = buildWasmBytes {
                function("ges", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32GeS()
                }
            }
            assertBothEqual(1L, bytes, "ges", 5L, 5L)
            assertBothEqual(1L, bytes, "ges", 6L, 5L)
            assertBothEqual(0L, bytes, "ges", 3L, 5L)
        }

        @Test
        fun `i32 lt_u with large unsigned`() {
            val bytes = buildWasmBytes {
                function("ltu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LtU()
                }
            }
            assertBothEqual(1L, bytes, "ltu", 5L, 0xFFFFFFFFL)
        }

        @Test
        fun `i32 le_u`() {
            val bytes = buildWasmBytes {
                function("leu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LeU()
                }
            }
            assertBothEqual(1L, bytes, "leu", 5L, 5L)
        }

        @Test
        fun `i32 gt_u`() {
            val bytes = buildWasmBytes {
                function("gtu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32GtU()
                }
            }
            assertBothEqual(1L, bytes, "gtu", 0xFFFFFFFFL, 5L)
        }

        @Test
        fun `i32 ge_u`() {
            val bytes = buildWasmBytes {
                function("geu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32GeU()
                }
            }
            assertBothEqual(1L, bytes, "geu", 0xFFFFFFFFL, 0xFFFFFFFFL)
        }
    }

    @Nested
    inner class I64Comparison {

        @Test
        fun `i64 eq`() {
            val bytes = buildWasmBytes {
                function("eq64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Eq()
                }
            }
            assertBothEqual(1L, bytes, "eq64", 100L, 100L)
            assertBothEqual(0L, bytes, "eq64", 100L, 200L)
        }

        @Test
        fun `i64 ne`() {
            val bytes = buildWasmBytes {
                function("ne64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Ne()
                }
            }
            assertBothEqual(1L, bytes, "ne64", 100L, 200L)
            assertBothEqual(0L, bytes, "ne64", 100L, 100L)
        }

        @Test
        fun `i64 eqz`() {
            val bytes = buildWasmBytes {
                function("eqz64", listOf(WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Eqz()
                }
            }
            assertBothEqual(1L, bytes, "eqz64", 0L)
            assertBothEqual(0L, bytes, "eqz64", 1L)
        }

        @Test
        fun `i64 lt_s`() {
            val bytes = buildWasmBytes {
                function("lts64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LtS()
                }
            }
            assertBothEqual(1L, bytes, "lts64", -1L, 0L)
            assertBothEqual(0L, bytes, "lts64", 0L, -1L)
        }

        @Test
        fun `i64 le_s`() {
            val bytes = buildWasmBytes {
                function("les64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LeS()
                }
            }
            assertBothEqual(1L, bytes, "les64", 5L, 5L)
        }

        @Test
        fun `i64 le_s with negative less than positive`() {
            val bytes = buildWasmBytes {
                function("les64neg", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LeS()
                }
            }
            assertBothEqual(1L, bytes, "les64neg", -10L, 5L)
            assertBothEqual(0L, bytes, "les64neg", 5L, -10L)
        }

        @Test
        fun `i64 ge_s with equal and greater`() {
            val bytes = buildWasmBytes {
                function("ges64eq", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GeS()
                }
            }
            assertBothEqual(1L, bytes, "ges64eq", 10L, 10L)
            assertBothEqual(1L, bytes, "ges64eq", 10L, 5L)
            assertBothEqual(0L, bytes, "ges64eq", -10L, 5L)
        }

        @Test
        fun `i64 le_u with max unsigned`() {
            val bytes = buildWasmBytes {
                function("leu64max", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LeU()
                }
            }
            assertBothEqual(1L, bytes, "leu64max", 5L, -1L)
            assertBothEqual(1L, bytes, "leu64max", -1L, -1L)
            assertBothEqual(0L, bytes, "leu64max", -1L, 5L)
        }

        @Test
        fun `i64 ge_u with max unsigned`() {
            val bytes = buildWasmBytes {
                function("geu64max", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GeU()
                }
            }
            assertBothEqual(1L, bytes, "geu64max", -1L, 5L)
            assertBothEqual(1L, bytes, "geu64max", -1L, -1L)
            assertBothEqual(0L, bytes, "geu64max", 5L, -1L)
        }

        @Test
        fun `i64 gt_s`() {
            val bytes = buildWasmBytes {
                function("gts64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GtS()
                }
            }
            assertBothEqual(1L, bytes, "gts64", 10L, 5L)
        }

        @Test
        fun `i64 ge_s`() {
            val bytes = buildWasmBytes {
                function("ges64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GeS()
                }
            }
            assertBothEqual(1L, bytes, "ges64", 5L, 5L)
            assertBothEqual(0L, bytes, "ges64", 3L, 5L)
        }

        @Test
        fun `i64 lt_u`() {
            val bytes = buildWasmBytes {
                function("ltu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LtU()
                }
            }
            assertBothEqual(1L, bytes, "ltu64", 5L, Long.MAX_VALUE.toLong())
        }

        @Test
        fun `i64 le_u`() {
            val bytes = buildWasmBytes {
                function("leu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64LeU()
                }
            }
            assertBothEqual(1L, bytes, "leu64", 5L, 5L)
        }

        @Test
        fun `i64 gt_u`() {
            val bytes = buildWasmBytes {
                function("gtu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GtU()
                }
            }
            assertBothEqual(1L, bytes, "gtu64", -1L, 5L)
        }

        @Test
        fun `i64 ge_u`() {
            val bytes = buildWasmBytes {
                function("geu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64GeU()
                }
            }
            assertBothEqual(1L, bytes, "geu64", -1L, -1L)
        }
    }

    @Nested
    inner class F32Comparison {

        @Test
        fun `f32 eq`() {
            val bytes = buildWasmBytes {
                function("feq", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Eq()
                }
            }
            assertBothEqual(1L, bytes, "feq", floatBits(3.0f), floatBits(3.0f))
            assertBothEqual(0L, bytes, "feq", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 ne`() {
            val bytes = buildWasmBytes {
                function("fne", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Ne()
                }
            }
            assertBothEqual(1L, bytes, "fne", floatBits(3.0f), floatBits(4.0f))
            assertBothEqual(0L, bytes, "fne", floatBits(3.0f), floatBits(3.0f))
        }

        @Test
        fun `f32 lt`() {
            val bytes = buildWasmBytes {
                function("flt", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Lt()
                }
            }
            assertBothEqual(1L, bytes, "flt", floatBits(3.0f), floatBits(4.0f))
            assertBothEqual(0L, bytes, "flt", floatBits(4.0f), floatBits(3.0f))
        }

        @Test
        fun `f32 gt`() {
            val bytes = buildWasmBytes {
                function("fgt", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Gt()
                }
            }
            assertBothEqual(1L, bytes, "fgt", floatBits(4.0f), floatBits(3.0f))
            assertBothEqual(0L, bytes, "fgt", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 le`() {
            val bytes = buildWasmBytes {
                function("fle", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Le()
                }
            }
            assertBothEqual(1L, bytes, "fle", floatBits(3.0f), floatBits(3.0f))
            assertBothEqual(1L, bytes, "fle", floatBits(3.0f), floatBits(4.0f))
            assertBothEqual(0L, bytes, "fle", floatBits(4.0f), floatBits(3.0f))
        }

        @Test
        fun `f32 ge`() {
            val bytes = buildWasmBytes {
                function("fge", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Ge()
                }
            }
            assertBothEqual(1L, bytes, "fge", floatBits(4.0f), floatBits(4.0f))
            assertBothEqual(1L, bytes, "fge", floatBits(4.0f), floatBits(3.0f))
            assertBothEqual(0L, bytes, "fge", floatBits(3.0f), floatBits(4.0f))
        }
    }

    @Nested
    inner class F64Comparison {

        @Test
        fun `f64 eq`() {
            val bytes = buildWasmBytes {
                function("deq", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Eq()
                }
            }
            assertBothEqual(1L, bytes, "deq", doubleBits(3.0), doubleBits(3.0))
            assertBothEqual(0L, bytes, "deq", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 ne`() {
            val bytes = buildWasmBytes {
                function("dne", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Ne()
                }
            }
            assertBothEqual(1L, bytes, "dne", doubleBits(3.0), doubleBits(4.0))
            assertBothEqual(0L, bytes, "dne", doubleBits(3.0), doubleBits(3.0))
        }

        @Test
        fun `f64 lt`() {
            val bytes = buildWasmBytes {
                function("dlt", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Lt()
                }
            }
            assertBothEqual(1L, bytes, "dlt", doubleBits(3.0), doubleBits(4.0))
            assertBothEqual(0L, bytes, "dlt", doubleBits(4.0), doubleBits(3.0))
        }

        @Test
        fun `f64 gt`() {
            val bytes = buildWasmBytes {
                function("dgt", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Gt()
                }
            }
            assertBothEqual(1L, bytes, "dgt", doubleBits(4.0), doubleBits(3.0))
        }

        @Test
        fun `f64 le`() {
            val bytes = buildWasmBytes {
                function("dle", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Le()
                }
            }
            assertBothEqual(1L, bytes, "dle", doubleBits(3.0), doubleBits(3.0))
            assertBothEqual(1L, bytes, "dle", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 ge`() {
            val bytes = buildWasmBytes {
                function("dge", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Ge()
                }
            }
            assertBothEqual(1L, bytes, "dge", doubleBits(4.0), doubleBits(4.0))
            assertBothEqual(1L, bytes, "dge", doubleBits(4.0), doubleBits(3.0))
        }
    }

    @Nested
    inner class Select {

        @Test
        fun `select with true condition`() {
            val bytes = buildWasmBytes {
                function("sel", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.select()
                }
            }
            assertBothEqual(10L, bytes, "sel", 10L, 20L, 1L)
        }

        @Test
        fun `select with false condition`() {
            val bytes = buildWasmBytes {
                function("sel", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.select()
                }
            }
            assertBothEqual(20L, bytes, "sel", 10L, 20L, 0L)
        }

        @Test
        fun `select with non-boolean condition`() {
            val bytes = buildWasmBytes {
                function("sel", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.select()
                }
            }
            assertBothEqual(10L, bytes, "sel", 10L, 20L, 42L)
        }

        @Test
        fun `select with i64 operands`() {
            val bytes = buildWasmBytes {
                function("sel64", listOf(WasmValueType.I64, WasmValueType.I64, WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.select()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "sel64", 100L, 200L, 1L)
            assertEquals(100L, interp[0])
            assertEquals(100L, jit[0])
        }

        @Test
        fun `select with f64 operands`() {
            val bytes = buildWasmBytes {
                function("selF64", listOf(WasmValueType.F64, WasmValueType.F64, WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.localGet(func.getParameter(2))
                    asm.select()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "selF64", doubleBits(3.14), doubleBits(2.72), 1L)
            assertEquals(doubleBits(3.14), interp[0], "Interpreter: select f64 true")
            assertEquals(doubleBits(3.14), jit[0], "JIT: select f64 true")
        }
    }

    @Nested
    inner class ComparisonEdgeCases {

        @Test
        fun `i32 lt_s MIN_VALUE less than MAX_VALUE`() {
            val bytes = buildWasmBytes {
                function("ltsEdge", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LtS()
                }
            }
            val minValue = 0x80000000L
            val maxValue = 0x7FFFFFFFL
            assertBothEqual(1L, bytes, "ltsEdge", minValue, maxValue)
        }

        @Test
        fun `i32 lt_u MIN_VALUE is greater than MAX_VALUE unsigned`() {
            val bytes = buildWasmBytes {
                function("ltuEdge", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32LtU()
                }
            }
            val minValue = 0x80000000L
            val maxValue = 0x7FFFFFFFL
            assertBothEqual(0L, bytes, "ltuEdge", minValue, maxValue)
        }

        @Test
        fun `f64 ne NaN not equal to NaN`() {
            val bytes = buildWasmBytes {
                function("nanNe", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Ne()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(1L, bytes, "nanNe", nanBits, nanBits)
        }

        @Test
        fun `f64 eq NaN is not equal to NaN`() {
            val bytes = buildWasmBytes {
                function("nanEq", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Eq()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(0L, bytes, "nanEq", nanBits, nanBits)
        }

        @Test
        fun `f64 eq positive zero equals negative zero`() {
            val bytes = buildWasmBytes {
                function("zeroEq", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Eq()
                }
            }
            val posZero = doubleBits(0.0)
            val negZero = doubleBits(-0.0)
            assertBothEqual(1L, bytes, "zeroEq", posZero, negZero)
        }

        @Test
        fun `f64 lt NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanLt", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Lt()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(0L, bytes, "nanLt", nanBits, doubleBits(1.0))
            assertBothEqual(0L, bytes, "nanLt", doubleBits(1.0), nanBits)
            assertBothEqual(0L, bytes, "nanLt", nanBits, nanBits)
        }

        @Test
        fun `f64 gt NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanGt", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Gt()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(0L, bytes, "nanGt", nanBits, doubleBits(1.0))
            assertBothEqual(0L, bytes, "nanGt", doubleBits(1.0), nanBits)
        }

        @Test
        fun `f64 le NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanLe", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Le()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(0L, bytes, "nanLe", nanBits, doubleBits(1.0))
            assertBothEqual(0L, bytes, "nanLe", nanBits, nanBits)
        }

        @Test
        fun `f64 ge NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanGe", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Ge()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            assertBothEqual(0L, bytes, "nanGe", nanBits, doubleBits(1.0))
            assertBothEqual(0L, bytes, "nanGe", nanBits, nanBits)
        }

        @Test
        fun `f32 eq NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanEq", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Eq()
                }
            }
            val nanBits = floatBits(Float.NaN)
            assertBothEqual(0L, bytes, "nanEq", nanBits, nanBits)
        }

        @Test
        fun `f32 ne NaN is always true`() {
            val bytes = buildWasmBytes {
                function("nanNe", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Ne()
                }
            }
            val nanBits = floatBits(Float.NaN)
            assertBothEqual(1L, bytes, "nanNe", nanBits, nanBits)
        }

        @Test
        fun `f32 lt NaN is always false`() {
            val bytes = buildWasmBytes {
                function("nanLt", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Lt()
                }
            }
            val nanBits = floatBits(Float.NaN)
            assertBothEqual(0L, bytes, "nanLt", nanBits, floatBits(1.0f))
            assertBothEqual(0L, bytes, "nanLt", nanBits, nanBits)
        }
    }
}

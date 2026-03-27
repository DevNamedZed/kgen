package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmConversionTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class WrapExtend {

        @Test
        fun `i32 wrap_i64`() {
            val bytes = buildWasmBytes {
                function("wrap", listOf(WasmValueType.I64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32WrapI64()
                }
            }
            assertBothEqual(42L, bytes, "wrap", 0x100000002AL)
        }

        @Test
        fun `i64 extend_i32_s`() {
            val bytes = buildWasmBytes {
                function("exts", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64ExtendI32S()
                }
            }
            val negOne = (-1L and 0xFFFFFFFFL)
            val (interp, jit) = executeInBothModes(bytes, "exts", negOne)
            assertEquals(-1L, interp[0], "Interpreter: extend_i32_s(-1)")
            assertEquals(-1L, jit[0], "JIT: extend_i32_s(-1)")
        }

        @Test
        fun `i64 extend_i32_s positive`() {
            val bytes = buildWasmBytes {
                function("exts", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64ExtendI32S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "exts", 42L)
            assertEquals(42L, interp[0])
            assertEquals(42L, jit[0])
        }

        @Test
        fun `i64 extend_i32_u`() {
            val bytes = buildWasmBytes {
                function("extu", listOf(WasmValueType.I32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64ExtendI32U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "extu", 0xFFFFFFFFL)
            assertEquals(0xFFFFFFFFL, interp[0], "Interpreter: extend_i32_u(0xFFFFFFFF)")
            assertEquals(0xFFFFFFFFL, jit[0], "JIT: extend_i32_u(0xFFFFFFFF)")
        }
    }

    @Nested
    inner class FloatConvert {

        @Test
        fun `f32 convert_i32_s`() {
            val bytes = buildWasmBytes {
                function("cvt", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32ConvertI32S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "cvt", 42L)
            assertEquals(floatBits(42.0f), interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(floatBits(42.0f), jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `f32 convert_i32_u`() {
            val bytes = buildWasmBytes {
                function("cvtu", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32ConvertI32U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "cvtu", 100L)
            assertEquals(floatBits(100.0f), interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(floatBits(100.0f), jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `f32 convert_i64_s`() {
            val bytes = buildWasmBytes {
                function("cvt64s", listOf(WasmValueType.I64), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32ConvertI64S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "cvt64s", 100L)
            assertEquals(floatBits(100.0f), interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(floatBits(100.0f), jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `f32 convert_i64_u`() {
            val bytes = buildWasmBytes {
                function("cvt64u", listOf(WasmValueType.I64), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32ConvertI64U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "cvt64u", 100L)
            assertEquals(floatBits(100.0f), interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(floatBits(100.0f), jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `f64 convert_i32_s`() {
            val bytes = buildWasmBytes {
                function("dcvt", listOf(WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ConvertI32S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "dcvt", 42L)
            assertEquals(doubleBits(42.0), interp[0], "Interpreter")
            assertEquals(doubleBits(42.0), jit[0], "JIT")
        }

        @Test
        fun `f64 convert_i32_u`() {
            val bytes = buildWasmBytes {
                function("dcvtu", listOf(WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ConvertI32U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "dcvtu", 100L)
            assertEquals(doubleBits(100.0), interp[0], "Interpreter")
            assertEquals(doubleBits(100.0), jit[0], "JIT")
        }

        @Test
        fun `f64 convert_i64_s`() {
            val bytes = buildWasmBytes {
                function("dcvt64s", listOf(WasmValueType.I64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ConvertI64S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "dcvt64s", 100L)
            assertEquals(doubleBits(100.0), interp[0], "Interpreter")
            assertEquals(doubleBits(100.0), jit[0], "JIT")
        }

        @Test
        fun `f64 convert_i64_u`() {
            val bytes = buildWasmBytes {
                function("dcvt64u", listOf(WasmValueType.I64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ConvertI64U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "dcvt64u", 100L)
            assertEquals(doubleBits(100.0), interp[0], "Interpreter")
            assertEquals(doubleBits(100.0), jit[0], "JIT")
        }
    }

    @Nested
    inner class FloatTrunc {

        @Test
        fun `i32 trunc_f32_s`() {
            val bytes = buildWasmBytes {
                function("trunc", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncF32S()
                }
            }
            assertBothEqual(3L, bytes, "trunc", floatBits(3.7f))
        }

        @Test
        fun `i32 trunc_f32_u`() {
            val bytes = buildWasmBytes {
                function("truncu", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncF32U()
                }
            }
            assertBothEqual(3L, bytes, "truncu", floatBits(3.7f))
        }

        @Test
        fun `i32 trunc_f64_s`() {
            val bytes = buildWasmBytes {
                function("truncd", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncF64S()
                }
            }
            assertBothEqual(3L, bytes, "truncd", doubleBits(3.7))
        }

        @Test
        fun `i32 trunc_f64_u`() {
            val bytes = buildWasmBytes {
                function("truncdu", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncF64U()
                }
            }
            assertBothEqual(3L, bytes, "truncdu", doubleBits(3.7))
        }

        @Test
        fun `i64 trunc_f32_s`() {
            val bytes = buildWasmBytes {
                function("trunc64", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF32S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "trunc64", floatBits(3.7f))
            assertEquals(3L, interp[0])
            assertEquals(3L, jit[0])
        }

        @Test
        fun `i64 trunc_f32_u`() {
            val bytes = buildWasmBytes {
                function("trunc64u", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF32U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "trunc64u", floatBits(3.7f))
            assertEquals(3L, interp[0])
            assertEquals(3L, jit[0])
        }

        @Test
        fun `i64 trunc_f32_u with 42_9f`() {
            val bytes = buildWasmBytes {
                function("trunc64u42", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF32U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "trunc64u42", floatBits(42.9f))
            assertEquals(42L, interp[0])
            assertEquals(42L, jit[0])
        }

        @Test
        fun `i64 trunc_f32_u with large unsigned value`() {
            val bytes = buildWasmBytes {
                function("trunc64uLarge", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF32U()
                }
            }
            val largeFloat = 1.0e10f
            val expected = largeFloat.toLong()
            val (interp, jit) = executeInBothModes(bytes, "trunc64uLarge", floatBits(largeFloat))
            assertEquals(expected, interp[0])
            assertEquals(expected, jit[0])
        }

        @Test
        fun `i64 trunc_f64_s`() {
            val bytes = buildWasmBytes {
                function("trunc64d", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF64S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "trunc64d", doubleBits(3.7))
            assertEquals(3L, interp[0])
            assertEquals(3L, jit[0])
        }

        @Test
        fun `i64 trunc_f64_u`() {
            val bytes = buildWasmBytes {
                function("trunc64du", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF64U()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "trunc64du", doubleBits(3.7))
            assertEquals(3L, interp[0])
            assertEquals(3L, jit[0])
        }
    }

    @Nested
    inner class DemotePromote {

        @Test
        fun `f32 demote_f64`() {
            val bytes = buildWasmBytes {
                function("demote", listOf(WasmValueType.F64), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32DemoteF64()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "demote", doubleBits(3.0))
            assertEquals(floatBits(3.0f), interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(floatBits(3.0f), jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `f64 promote_f32`() {
            val bytes = buildWasmBytes {
                function("promote", listOf(WasmValueType.F32), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64PromoteF32()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "promote", floatBits(3.0f))
            assertEquals(doubleBits(3.0), interp[0], "Interpreter")
            assertEquals(doubleBits(3.0), jit[0], "JIT")
        }
    }

    @Nested
    inner class Reinterpret {

        @Test
        fun `i32 reinterpret_f32`() {
            val bytes = buildWasmBytes {
                function("ri32", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32ReinterpretF32()
                }
            }
            val inputBits = floatBits(1.0f)
            assertBothEqual(inputBits, bytes, "ri32", inputBits)
        }

        @Test
        fun `f32 reinterpret_i32`() {
            val bytes = buildWasmBytes {
                function("rf32", listOf(WasmValueType.I32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32ReinterpretI32()
                }
            }
            val inputBits = floatBits(1.0f)
            val (interp, jit) = executeInBothModes(bytes, "rf32", inputBits)
            assertEquals(inputBits, interp[0] and 0xFFFFFFFFL, "Interpreter")
            assertEquals(inputBits, jit[0] and 0xFFFFFFFFL, "JIT")
        }

        @Test
        fun `i64 reinterpret_f64`() {
            val bytes = buildWasmBytes {
                function("ri64", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64ReinterpretF64()
                }
            }
            val inputBits = doubleBits(1.0)
            val (interp, jit) = executeInBothModes(bytes, "ri64", inputBits)
            assertEquals(inputBits, interp[0], "Interpreter")
            assertEquals(inputBits, jit[0], "JIT")
        }

        @Test
        fun `f64 reinterpret_i64`() {
            val bytes = buildWasmBytes {
                function("rf64", listOf(WasmValueType.I64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ReinterpretI64()
                }
            }
            val inputBits = doubleBits(1.0)
            val (interp, jit) = executeInBothModes(bytes, "rf64", inputBits)
            assertEquals(inputBits, interp[0], "Interpreter")
            assertEquals(inputBits, jit[0], "JIT")
        }
    }

    @Nested
    inner class SaturatingTruncation {

        @Test
        fun `i32 trunc_sat_f64_s normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF64S()
                }
            }
            assertBothEqual(42L, bytes, "test", doubleBits(42.9))
            assertBothEqual(-7L and 0xFFFFFFFFL, bytes, "test", doubleBits(-7.3))
        }

        @Test
        fun `i32 trunc_sat_f64_s saturates on overflow`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF64S()
                }
            }
            assertBothEqual(Int.MAX_VALUE.toLong() and 0xFFFFFFFFL, bytes, "test", doubleBits(1e15))
            assertBothEqual(Int.MIN_VALUE.toLong() and 0xFFFFFFFFL, bytes, "test", doubleBits(-1e15))
        }

        @Test
        fun `i32 trunc_sat_f64_s NaN returns zero`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF64S()
                }
            }
            assertBothEqual(0L, bytes, "test", doubleBits(Double.NaN))
        }

        @Test
        fun `i32 trunc_sat_f64_u normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF64U()
                }
            }
            assertBothEqual(42L, bytes, "test", doubleBits(42.9))
        }

        @Test
        fun `i32 trunc_sat_f64_u saturates on overflow`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF64U()
                }
            }
            assertBothEqual(0xFFFFFFFFL, bytes, "test", doubleBits(1e15))
            assertBothEqual(0L, bytes, "test", doubleBits(-1.0))
        }

        @Test
        fun `i32 trunc_sat_f32_s normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF32S()
                }
            }
            assertBothEqual(42L, bytes, "test", floatBits(42.9f))
        }

        @Test
        fun `i32 trunc_sat_f32_s saturates on positive infinity`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF32S()
                }
            }
            assertBothEqual(Int.MAX_VALUE.toLong() and 0xFFFFFFFFL, bytes, "test", floatBits(Float.POSITIVE_INFINITY))
            assertBothEqual(Int.MIN_VALUE.toLong() and 0xFFFFFFFFL, bytes, "test", floatBits(Float.NEGATIVE_INFINITY))
        }

        @Test
        fun `i32 trunc_sat_f32_u normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncSatF32U()
                }
            }
            assertBothEqual(200L, bytes, "test", floatBits(200.5f))
        }

        @Test
        fun `i64 trunc_sat_f64_s normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF64S()
                }
            }
            assertBothEqualI64(42L, bytes, "test", doubleBits(42.9))
            assertBothEqualI64(-100L, bytes, "test", doubleBits(-100.1))
        }

        @Test
        fun `i64 trunc_sat_f64_s saturates on overflow`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF64S()
                }
            }
            assertBothEqualI64(Long.MAX_VALUE, bytes, "test", doubleBits(1e30))
            assertBothEqualI64(Long.MIN_VALUE, bytes, "test", doubleBits(-1e30))
        }

        @Test
        fun `i64 trunc_sat_f64_s NaN returns zero`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF64S()
                }
            }
            assertBothEqualI64(0L, bytes, "test", doubleBits(Double.NaN))
        }

        @Test
        fun `i64 trunc_sat_f64_u normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF64U()
                }
            }
            assertBothEqualI64(1000L, bytes, "test", doubleBits(1000.7))
        }

        @Test
        fun `i64 trunc_sat_f32_s normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF32S()
                }
            }
            assertBothEqualI64(42L, bytes, "test", floatBits(42.5f))
        }

        @Test
        fun `i64 trunc_sat_f32_u normal`() {
            val bytes = buildWasmBytes {
                function("test", listOf(WasmValueType.F32), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncSatF32U()
                }
            }
            assertBothEqualI64(200L, bytes, "test", floatBits(200.9f))
        }
    }

    @Nested
    inner class ConversionEdgeCases {

        @Test
        fun `convert MIN_INT to f64 and back`() {
            val convertBytes = buildWasmBytes {
                function("toF64", listOf(WasmValueType.I32), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64ConvertI32S()
                }
            }
            val truncBytes = buildWasmBytes {
                function("toI32", listOf(WasmValueType.F64), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32TruncF64S()
                }
            }
            val minInt = 0x80000000L
            val (interpConv, jitConv) = executeInBothModes(convertBytes, "toF64", minInt)
            assertEquals(doubleBits(Int.MIN_VALUE.toDouble()), interpConv[0], "Interpreter: i32 MIN to f64")
            assertEquals(doubleBits(Int.MIN_VALUE.toDouble()), jitConv[0], "JIT: i32 MIN to f64")

            val (interpTrunc, jitTrunc) = executeInBothModes(truncBytes, "toI32", interpConv[0])
            assertEquals(Int.MIN_VALUE.toLong(), interpTrunc[0].toInt().toLong(), "Interpreter: f64 back to i32")
            assertEquals(Int.MIN_VALUE.toLong(), jitTrunc[0].toInt().toLong(), "JIT: f64 back to i32")
        }

        @Test
        fun `convert large f64 to i64`() {
            val bytes = buildWasmBytes {
                function("truncLarge", listOf(WasmValueType.F64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64TruncF64S()
                }
            }
            val largeValue = 9.0e18
            val expected = largeValue.toLong()
            val (interp, jit) = executeInBothModes(bytes, "truncLarge", doubleBits(largeValue))
            assertEquals(expected, interp[0], "Interpreter: large f64 to i64")
            assertEquals(expected, jit[0], "JIT: large f64 to i64")
        }

        @Test
        fun `f32 demote of f64 that loses precision`() {
            val bytes = buildWasmBytes {
                function("demotePrecision", listOf(WasmValueType.F64), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32DemoteF64()
                }
            }
            val preciseDouble = 1.0000001192092896
            val expectedFloat = preciseDouble.toFloat()
            val (interp, jit) = executeInBothModes(bytes, "demotePrecision", doubleBits(preciseDouble))
            val interpFloat = java.lang.Float.intBitsToFloat(interp[0].toInt())
            val jitFloat = java.lang.Float.intBitsToFloat(jit[0].toInt())
            assertEquals(expectedFloat, interpFloat, "Interpreter: f32 demote precision loss")
            assertEquals(expectedFloat, jitFloat, "JIT: f32 demote precision loss")
        }
    }
}

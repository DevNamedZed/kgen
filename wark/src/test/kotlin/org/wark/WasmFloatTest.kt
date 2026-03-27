package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmFloatTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class F32Arithmetic {

        @Test
        fun `f32 add`() {
            val bytes = buildWasmBytes {
                function("addF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Add()
                }
            }
            assertBothEqualF32(7.0f, bytes, "addF32", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 sub`() {
            val bytes = buildWasmBytes {
                function("subF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Sub()
                }
            }
            assertBothEqualF32(6.0f, bytes, "subF32", floatBits(10.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 mul`() {
            val bytes = buildWasmBytes {
                function("mulF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Mul()
                }
            }
            assertBothEqualF32(12.0f, bytes, "mulF32", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 div`() {
            val bytes = buildWasmBytes {
                function("divF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Div()
                }
            }
            assertBothEqualF32(2.5f, bytes, "divF32", floatBits(10.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 neg`() {
            val bytes = buildWasmBytes {
                function("negF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Neg()
                }
            }
            assertBothEqualF32(-5.0f, bytes, "negF32", floatBits(5.0f))
        }

        @Test
        fun `f32 abs`() {
            val bytes = buildWasmBytes {
                function("absF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Abs()
                }
            }
            assertBothEqualF32(5.0f, bytes, "absF32", floatBits(-5.0f))
        }
    }

    @Nested
    inner class F64Arithmetic {

        @Test
        fun `f64 add`() {
            val bytes = buildWasmBytes {
                function("addF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Add()
                }
            }
            assertBothEqualF64(7.0, bytes, "addF64", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 sub`() {
            val bytes = buildWasmBytes {
                function("subF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Sub()
                }
            }
            assertBothEqualF64(6.0, bytes, "subF64", doubleBits(10.0), doubleBits(4.0))
        }

        @Test
        fun `f64 mul`() {
            val bytes = buildWasmBytes {
                function("mulF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Mul()
                }
            }
            assertBothEqualF64(12.0, bytes, "mulF64", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 div`() {
            val bytes = buildWasmBytes {
                function("divF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Div()
                }
            }
            assertBothEqualF64(2.5, bytes, "divF64", doubleBits(10.0), doubleBits(4.0))
        }

        @Test
        fun `f64 neg`() {
            val bytes = buildWasmBytes {
                function("negF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Neg()
                }
            }
            assertBothEqualF64(-5.0, bytes, "negF64", doubleBits(5.0))
        }

        @Test
        fun `f64 abs`() {
            val bytes = buildWasmBytes {
                function("absF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Abs()
                }
            }
            assertBothEqualF64(5.0, bytes, "absF64", doubleBits(-5.0))
        }
    }

    @Nested
    inner class F32Math {

        @Test
        fun `f32 sqrt`() {
            val bytes = buildWasmBytes {
                function("sqrtF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Sqrt()
                }
            }
            assertBothEqualF32(2.0f, bytes, "sqrtF32", floatBits(4.0f))
        }

        @Test
        fun `f32 ceil`() {
            val bytes = buildWasmBytes {
                function("ceilF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Ceil()
                }
            }
            assertBothEqualF32(4.0f, bytes, "ceilF32", floatBits(3.2f))
        }

        @Test
        fun `f32 floor`() {
            val bytes = buildWasmBytes {
                function("floorF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Floor()
                }
            }
            assertBothEqualF32(3.0f, bytes, "floorF32", floatBits(3.7f))
        }

        @Test
        fun `f32 trunc`() {
            val bytes = buildWasmBytes {
                function("truncF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Trunc()
                }
            }
            assertBothEqualF32(-3.0f, bytes, "truncF32", floatBits(-3.7f))
        }

        @Test
        fun `f32 nearest bankers rounding 2_5 rounds to 2`() {
            val bytes = buildWasmBytes {
                function("nearF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Nearest()
                }
            }
            assertBothEqualF32(2.0f, bytes, "nearF32", floatBits(2.5f))
        }

        @Test
        fun `f32 nearest bankers rounding 3_5 rounds to 4`() {
            val bytes = buildWasmBytes {
                function("nearF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Nearest()
                }
            }
            assertBothEqualF32(4.0f, bytes, "nearF32", floatBits(3.5f))
        }

        @Test
        fun `f32 nearest bankers rounding 4_5 rounds to 4`() {
            val bytes = buildWasmBytes {
                function("nearF32", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f32Nearest()
                }
            }
            assertBothEqualF32(4.0f, bytes, "nearF32", floatBits(4.5f))
        }

        @Test
        fun `f32 min`() {
            val bytes = buildWasmBytes {
                function("minF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Min()
                }
            }
            assertBothEqualF32(3.0f, bytes, "minF32", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 max`() {
            val bytes = buildWasmBytes {
                function("maxF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Max()
                }
            }
            assertBothEqualF32(4.0f, bytes, "maxF32", floatBits(3.0f), floatBits(4.0f))
        }

        @Test
        fun `f32 copysign`() {
            val bytes = buildWasmBytes {
                function("csF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Copysign()
                }
            }
            assertBothEqualF32(-3.0f, bytes, "csF32", floatBits(3.0f), floatBits(-1.0f))
        }
    }

    @Nested
    inner class F64Math {

        @Test
        fun `f64 sqrt`() {
            val bytes = buildWasmBytes {
                function("sqrtF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Sqrt()
                }
            }
            assertBothEqualF64(2.0, bytes, "sqrtF64", doubleBits(4.0))
        }

        @Test
        fun `f64 ceil`() {
            val bytes = buildWasmBytes {
                function("ceilF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Ceil()
                }
            }
            assertBothEqualF64(4.0, bytes, "ceilF64", doubleBits(3.2))
        }

        @Test
        fun `f64 floor`() {
            val bytes = buildWasmBytes {
                function("floorF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Floor()
                }
            }
            assertBothEqualF64(3.0, bytes, "floorF64", doubleBits(3.7))
        }

        @Test
        fun `f64 trunc`() {
            val bytes = buildWasmBytes {
                function("truncF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Trunc()
                }
            }
            assertBothEqualF64(-3.0, bytes, "truncF64", doubleBits(-3.7))
        }

        @Test
        fun `f64 nearest bankers rounding 2_5 rounds to 2`() {
            val bytes = buildWasmBytes {
                function("nearF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Nearest()
                }
            }
            assertBothEqualF64(2.0, bytes, "nearF64", doubleBits(2.5))
        }

        @Test
        fun `f64 nearest bankers rounding 3_5 rounds to 4`() {
            val bytes = buildWasmBytes {
                function("nearF64", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.f64Nearest()
                }
            }
            assertBothEqualF64(4.0, bytes, "nearF64", doubleBits(3.5))
        }

        @Test
        fun `f64 min`() {
            val bytes = buildWasmBytes {
                function("minF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Min()
                }
            }
            assertBothEqualF64(3.0, bytes, "minF64", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 max`() {
            val bytes = buildWasmBytes {
                function("maxF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Max()
                }
            }
            assertBothEqualF64(4.0, bytes, "maxF64", doubleBits(3.0), doubleBits(4.0))
        }

        @Test
        fun `f64 copysign`() {
            val bytes = buildWasmBytes {
                function("csF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Copysign()
                }
            }
            assertBothEqualF64(-3.0, bytes, "csF64", doubleBits(3.0), doubleBits(-1.0))
        }
    }

    @Nested
    inner class FloatEdgeCases {

        @Test
        fun `f64 add with very large values`() {
            val bytes = buildWasmBytes {
                function("addLarge", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Add()
                }
            }
            assertBothEqualF64(2.0e308, bytes, "addLarge", doubleBits(1.0e308), doubleBits(1.0e308))
        }

        @Test
        fun `f64 mul with very small values`() {
            val bytes = buildWasmBytes {
                function("mulSmall", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Mul()
                }
            }
            val smallValue = 1.0e-308
            val expected = smallValue * 2.0
            assertBothEqualF64(expected, bytes, "mulSmall", doubleBits(smallValue), doubleBits(2.0))
        }

        @Test
        fun `f32 mul zero times large value is zero`() {
            val bytes = buildWasmBytes {
                function("mulZero", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f32Mul()
                }
            }
            assertBothEqualF32(0.0f, bytes, "mulZero", floatBits(0.0f), floatBits(1.0e30f))
        }

        @Test
        fun `f64 mul zero times value is zero`() {
            val bytes = buildWasmBytes {
                function("mulZero64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Mul()
                }
            }
            assertBothEqualF64(0.0, bytes, "mulZero64", doubleBits(0.0), doubleBits(12345.6789))
        }

        @Test
        fun `f64 min with NaN returns NaN`() {
            val bytes = buildWasmBytes {
                function("minNan", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Min()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            val normalBits = doubleBits(5.0)
            val (interp, jit) = executeInBothModes(bytes, "minNan", nanBits, normalBits)
            val interpDouble = java.lang.Double.longBitsToDouble(interp[0])
            val jitDouble = java.lang.Double.longBitsToDouble(jit[0])
            assertEquals(true, interpDouble.isNaN(), "Interpreter: f64.min with NaN returns NaN")
            assertEquals(true, jitDouble.isNaN(), "JIT: f64.min with NaN returns NaN")
        }

        @Test
        fun `f64 max with NaN returns NaN`() {
            val bytes = buildWasmBytes {
                function("maxNan", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.f64Max()
                }
            }
            val nanBits = doubleBits(Double.NaN)
            val normalBits = doubleBits(5.0)
            val (interp, jit) = executeInBothModes(bytes, "maxNan", normalBits, nanBits)
            val interpDouble = java.lang.Double.longBitsToDouble(interp[0])
            val jitDouble = java.lang.Double.longBitsToDouble(jit[0])
            assertEquals(true, interpDouble.isNaN(), "Interpreter: f64.max with NaN returns NaN")
            assertEquals(true, jitDouble.isNaN(), "JIT: f64.max with NaN returns NaN")
        }
    }
}

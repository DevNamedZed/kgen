package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertEquals

class WasmArithmeticTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class I32Arithmetic {

        @Test
        fun `i32 add`() {
            val bytes = buildWasmBytes {
                function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Add()
                }
            }
            assertBothEqual(7L, bytes, "add", 3L, 4L)
        }

        @Test
        fun `i32 sub`() {
            val bytes = buildWasmBytes {
                function("sub", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Sub()
                }
            }
            assertBothEqual(6L, bytes, "sub", 10L, 4L)
        }

        @Test
        fun `i32 mul`() {
            val bytes = buildWasmBytes {
                function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Mul()
                }
            }
            assertBothEqual(42L, bytes, "mul", 6L, 7L)
        }

        @Test
        fun `i32 mul with overflow`() {
            val bytes = buildWasmBytes {
                function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Mul()
                }
            }
            val expected = (100000L * 100000L).toInt().toLong()
            assertBothEqual(expected, bytes, "mul", 100000L, 100000L)
        }

        @Test
        fun `i32 div_s`() {
            val bytes = buildWasmBytes {
                function("divs", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32DivS()
                }
            }
            assertBothEqual(3L, bytes, "divs", 10L, 3L)
        }

        @Test
        fun `i32 div_u`() {
            val bytes = buildWasmBytes {
                function("divu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32DivU()
                }
            }
            assertBothEqual(2L, bytes, "divu", 10L, 5L)
        }

        @Test
        fun `i32 rem_s`() {
            val bytes = buildWasmBytes {
                function("rems", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32RemS()
                }
            }
            assertBothEqual(1L, bytes, "rems", 10L, 3L)
        }

        @Test
        fun `i32 rem_s with negative`() {
            val bytes = buildWasmBytes {
                function("rems", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32RemS()
                }
            }
            val negSeven = (-7L and 0xFFFFFFFFL)
            val expected = (-1).toLong()
            assertBothEqual(expected, bytes, "rems", negSeven, 3L)
        }

        @Test
        fun `i32 rem_u`() {
            val bytes = buildWasmBytes {
                function("remu", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32RemU()
                }
            }
            assertBothEqual(1L, bytes, "remu", 10L, 3L)
        }
    }

    @Nested
    inner class I32Bitwise {

        @Test
        fun `i32 and`() {
            val bytes = buildWasmBytes {
                function("and", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32And()
                }
            }
            assertBothEqual(0x0AL, bytes, "and", 0x0FL, 0xFAL)
        }

        @Test
        fun `i32 and with 0xFF mask`() {
            val bytes = buildWasmBytes {
                function("and", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32And()
                }
            }
            assertBothEqual(0xCDL, bytes, "and", 0xABCDL, 0xFFL)
        }

        @Test
        fun `i32 or`() {
            val bytes = buildWasmBytes {
                function("or", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Or()
                }
            }
            assertBothEqual(0xFFL, bytes, "or", 0xF0L, 0x0FL)
        }

        @Test
        fun `i32 xor`() {
            val bytes = buildWasmBytes {
                function("xor", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Xor()
                }
            }
            assertBothEqual(0xFFL, bytes, "xor", 0xF0L, 0x0FL)
        }

        @Test
        fun `i32 shl`() {
            val bytes = buildWasmBytes {
                function("shl", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Shl()
                }
            }
            assertBothEqual(8L, bytes, "shl", 1L, 3L)
        }

        @Test
        fun `i32 shl by zero`() {
            val bytes = buildWasmBytes {
                function("shl", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Shl()
                }
            }
            assertBothEqual(42L, bytes, "shl", 42L, 0L)
        }

        @Test
        fun `i32 shr_s`() {
            val bytes = buildWasmBytes {
                function("shrs", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32ShrS()
                }
            }
            assertBothEqual(4L, bytes, "shrs", 32L, 3L)
        }

        @Test
        fun `i32 shr_s with sign extension`() {
            val bytes = buildWasmBytes {
                function("shrs", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32ShrS()
                }
            }
            val negativeValue = (-8L and 0xFFFFFFFFL)
            val expected = (-1L).toInt().toLong()
            assertBothEqual(expected, bytes, "shrs", negativeValue, 31L)
        }

        @Test
        fun `i32 shr_u`() {
            val bytes = buildWasmBytes {
                function("shru", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32ShrU()
                }
            }
            assertBothEqual(4L, bytes, "shru", 32L, 3L)
        }

        @Test
        fun `i32 rotl`() {
            val bytes = buildWasmBytes {
                function("rotl", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Rotl()
                }
            }
            val expected = (1 shl 4).toLong()
            assertBothEqual(expected, bytes, "rotl", 1L, 4L)
        }

        @Test
        fun `i32 rotr`() {
            val bytes = buildWasmBytes {
                function("rotr", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Rotr()
                }
            }
            val expected = 1L
            assertBothEqual(expected, bytes, "rotr", 16L, 4L)
        }
    }

    @Nested
    inner class I32Unary {

        @Test
        fun `i32 clz of zero`() {
            val bytes = buildWasmBytes {
                function("clz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Clz()
                }
            }
            assertBothEqual(32L, bytes, "clz", 0L)
        }

        @Test
        fun `i32 clz of one`() {
            val bytes = buildWasmBytes {
                function("clz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Clz()
                }
            }
            assertBothEqual(31L, bytes, "clz", 1L)
        }

        @Test
        fun `i32 clz of max value`() {
            val bytes = buildWasmBytes {
                function("clz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Clz()
                }
            }
            assertBothEqual(0L, bytes, "clz", 0x80000000L)
        }

        @Test
        fun `i32 ctz of zero`() {
            val bytes = buildWasmBytes {
                function("ctz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Ctz()
                }
            }
            assertBothEqual(32L, bytes, "ctz", 0L)
        }

        @Test
        fun `i32 ctz of 0x80`() {
            val bytes = buildWasmBytes {
                function("ctz", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Ctz()
                }
            }
            assertBothEqual(7L, bytes, "ctz", 0x80L)
        }

        @Test
        fun `i32 popcnt`() {
            val bytes = buildWasmBytes {
                function("popcnt", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Popcnt()
                }
            }
            assertBothEqual(3L, bytes, "popcnt", 0b1011L)
        }

        @Test
        fun `i32 popcnt of zero`() {
            val bytes = buildWasmBytes {
                function("popcnt", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Popcnt()
                }
            }
            assertBothEqual(0L, bytes, "popcnt", 0L)
        }

        @Test
        fun `i32 extend8_s with 0x80`() {
            val bytes = buildWasmBytes {
                function("ext8", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Extend8S()
                }
            }
            val expected = (-128).toLong()
            assertBothEqual(expected, bytes, "ext8", 0x80L)
        }

        @Test
        fun `i32 extend8_s with positive value`() {
            val bytes = buildWasmBytes {
                function("ext8", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Extend8S()
                }
            }
            assertBothEqual(127L, bytes, "ext8", 0x7FL)
        }

        @Test
        fun `i32 extend16_s with 0x8000`() {
            val bytes = buildWasmBytes {
                function("ext16", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Extend16S()
                }
            }
            val expected = (-32768).toLong()
            assertBothEqual(expected, bytes, "ext16", 0x8000L)
        }

        @Test
        fun `i32 extend16_s with positive value`() {
            val bytes = buildWasmBytes {
                function("ext16", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i32Extend16S()
                }
            }
            assertBothEqual(32767L, bytes, "ext16", 0x7FFFL)
        }
    }

    @Nested
    inner class I64Arithmetic {

        @Test
        fun `i64 add`() {
            val bytes = buildWasmBytes {
                function("add64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Add()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "add64", 1000000000L, 2000000000L)
            assertEquals(3000000000L, interp[0])
            assertEquals(3000000000L, jit[0])
        }

        @Test
        fun `i64 sub`() {
            val bytes = buildWasmBytes {
                function("sub64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Sub()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "sub64", 5000000000L, 2000000000L)
            assertEquals(3000000000L, interp[0])
            assertEquals(3000000000L, jit[0])
        }

        @Test
        fun `i64 mul`() {
            val bytes = buildWasmBytes {
                function("mul64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Mul()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "mul64", 100000L, 200000L)
            assertEquals(20000000000L, interp[0])
            assertEquals(20000000000L, jit[0])
        }

        @Test
        fun `i64 div_s`() {
            val bytes = buildWasmBytes {
                function("divs64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64DivS()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "divs64", 100L, 3L)
            assertEquals(33L, interp[0])
            assertEquals(33L, jit[0])
        }

        @Test
        fun `i64 div_u`() {
            val bytes = buildWasmBytes {
                function("divu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64DivU()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "divu64", 100L, 3L)
            assertEquals(33L, interp[0])
            assertEquals(33L, jit[0])
        }

        @Test
        fun `i64 rem_s`() {
            val bytes = buildWasmBytes {
                function("rems64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64RemS()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "rems64", 10L, 3L)
            assertEquals(1L, interp[0])
            assertEquals(1L, jit[0])
        }

        @Test
        fun `i64 rem_u`() {
            val bytes = buildWasmBytes {
                function("remu64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64RemU()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "remu64", 10L, 3L)
            assertEquals(1L, interp[0])
            assertEquals(1L, jit[0])
        }
    }

    @Nested
    inner class I64Bitwise {

        @Test
        fun `i64 and`() {
            val bytes = buildWasmBytes {
                function("and64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64And()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "and64", 0xFF00FFL, 0x00FF00FFL)
            assertEquals(0xFF00FFL, interp[0])
            assertEquals(0xFF00FFL, jit[0])
        }

        @Test
        fun `i64 and with alternating bytes`() {
            val bytes = buildWasmBytes {
                function("and64alt", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64And()
                }
            }
            val operandA = -0x00FF00FF00FF0100L // 0xFF00FF00FF00FF00
            val operandB = 0x00FF00FF00FF00FFL
            val expected = 0x0000000000000000L
            val (interp, jit) = executeInBothModes(bytes, "and64alt", operandA, operandB)
            assertEquals(expected, interp[0])
            assertEquals(expected, jit[0])
        }

        @Test
        fun `i64 or`() {
            val bytes = buildWasmBytes {
                function("or64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Or()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "or64", 0xF0L, 0x0FL)
            assertEquals(0xFFL, interp[0])
            assertEquals(0xFFL, jit[0])
        }

        @Test
        fun `i64 or with alternating bytes`() {
            val bytes = buildWasmBytes {
                function("or64alt", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Or()
                }
            }
            val operandA = -0x00FF00FF00FF0100L // 0xFF00FF00FF00FF00
            val operandB = 0x00FF00FF00FF00FFL
            val expected = -1L // 0xFFFFFFFFFFFFFFFF
            val (interp, jit) = executeInBothModes(bytes, "or64alt", operandA, operandB)
            assertEquals(expected, interp[0])
            assertEquals(expected, jit[0])
        }

        @Test
        fun `i64 xor`() {
            val bytes = buildWasmBytes {
                function("xor64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Xor()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "xor64", 0xFFL, 0x0FL)
            assertEquals(0xF0L, interp[0])
            assertEquals(0xF0L, jit[0])
        }

        @Test
        fun `i64 xor with alternating bytes`() {
            val bytes = buildWasmBytes {
                function("xor64alt", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Xor()
                }
            }
            val operandA = -0x00FF00FF00FF0100L // 0xFF00FF00FF00FF00
            val operandB = 0x00FF00FF00FF00FFL
            val expected = -1L // all bits differ
            val (interp, jit) = executeInBothModes(bytes, "xor64alt", operandA, operandB)
            assertEquals(expected, interp[0])
            assertEquals(expected, jit[0])
        }

        @Test
        fun `i64 shl`() {
            val bytes = buildWasmBytes {
                function("shl64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Shl()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "shl64", 1L, 40L)
            assertEquals(1L shl 40, interp[0])
            assertEquals(1L shl 40, jit[0])
        }

        @Test
        fun `i64 shr_s`() {
            val bytes = buildWasmBytes {
                function("shrs64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64ShrS()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "shrs64", -16L, 2L)
            assertEquals(-4L, interp[0])
            assertEquals(-4L, jit[0])
        }

        @Test
        fun `i64 shr_u`() {
            val bytes = buildWasmBytes {
                function("shru64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64ShrU()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "shru64", 256L, 4L)
            assertEquals(16L, interp[0])
            assertEquals(16L, jit[0])
        }

        @Test
        fun `i64 rotl`() {
            val bytes = buildWasmBytes {
                function("rotl64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Rotl()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "rotl64", 1L, 4L)
            assertEquals(16L, interp[0])
            assertEquals(16L, jit[0])
        }

        @Test
        fun `i64 rotr`() {
            val bytes = buildWasmBytes {
                function("rotr64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Rotr()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "rotr64", 16L, 4L)
            assertEquals(1L, interp[0])
            assertEquals(1L, jit[0])
        }
    }

    @Nested
    inner class I64Unary {

        @Test
        fun `i64 clz`() {
            val bytes = buildWasmBytes {
                function("clz64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Clz()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "clz64", 0L)
            assertEquals(64L, interp[0])
            assertEquals(64L, jit[0])
        }

        @Test
        fun `i64 clz of one`() {
            val bytes = buildWasmBytes {
                function("clz64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Clz()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "clz64", 1L)
            assertEquals(63L, interp[0])
            assertEquals(63L, jit[0])
        }

        @Test
        fun `i64 ctz`() {
            val bytes = buildWasmBytes {
                function("ctz64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Ctz()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "ctz64", 0L)
            assertEquals(64L, interp[0])
            assertEquals(64L, jit[0])
        }

        @Test
        fun `i64 ctz of 0x100`() {
            val bytes = buildWasmBytes {
                function("ctz64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Ctz()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "ctz64", 0x100L)
            assertEquals(8L, interp[0])
            assertEquals(8L, jit[0])
        }

        @Test
        fun `i64 popcnt`() {
            val bytes = buildWasmBytes {
                function("popcnt64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Popcnt()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "popcnt64", 0b1010101L)
            assertEquals(4L, interp[0])
            assertEquals(4L, jit[0])
        }

        @Test
        fun `i64 extend8_s`() {
            val bytes = buildWasmBytes {
                function("ext8_64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Extend8S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "ext8_64", 0x80L)
            assertEquals(-128L, interp[0])
            assertEquals(-128L, jit[0])
        }

        @Test
        fun `i64 extend16_s`() {
            val bytes = buildWasmBytes {
                function("ext16_64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Extend16S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "ext16_64", 0x8000L)
            assertEquals(-32768L, interp[0])
            assertEquals(-32768L, jit[0])
        }

        @Test
        fun `i64 extend32_s`() {
            val bytes = buildWasmBytes {
                function("ext32_64", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.i64Extend32S()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "ext32_64", 0x80000000L)
            assertEquals(-2147483648L, interp[0])
            assertEquals(-2147483648L, jit[0])
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `i32 div_s MIN_VALUE by negative one returns MIN_VALUE`() {
            val bytes = buildWasmBytes {
                function("divEdge", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32DivS()
                }
            }
            val minValue = 0x80000000L
            val negOne = (-1L and 0xFFFFFFFFL)
            val expected = Int.MIN_VALUE.toLong()
            assertBothEqual(expected, bytes, "divEdge", minValue, negOne)
        }

        @Test
        fun `i32 shl by 32 masks to zero shift`() {
            val bytes = buildWasmBytes {
                function("shlMask", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Shl()
                }
            }
            assertBothEqual(7L, bytes, "shlMask", 7L, 32L)
        }

        @Test
        fun `i64 shl by 64 masks to zero shift`() {
            val bytes = buildWasmBytes {
                function("shlMask64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64Shl()
                }
            }
            val (interp, jit) = executeInBothModes(bytes, "shlMask64", 7L, 64L)
            assertEquals(7L, interp[0])
            assertEquals(7L, jit[0])
        }

        @Test
        fun `i32 mul overflow wraps correctly`() {
            val bytes = buildWasmBytes {
                function("mulOverflow", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32Mul()
                }
            }
            val expected = (0x7FFFFFFF * 2).toLong()
            assertBothEqual(expected, bytes, "mulOverflow", 0x7FFFFFFFL, 2L)
        }

        @Test
        fun `i32 rem_s negative one by negative one is zero`() {
            val bytes = buildWasmBytes {
                function("remEdge", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32RemS()
                }
            }
            val negOne = (-1L and 0xFFFFFFFFL)
            assertBothEqual(0L, bytes, "remEdge", negOne, negOne)
        }

        @Test
        fun `i32 rem_s MIN_VALUE by negative one returns zero`() {
            val bytes = buildWasmBytes {
                function("remEdge", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i32RemS()
                }
            }
            val minValue = 0x80000000L
            val negOne = (-1L and 0xFFFFFFFFL)
            assertBothEqual(0L, bytes, "remEdge", minValue, negOne)
        }

        @Test
        fun `i64 div_s MIN_VALUE by negative one returns MIN_VALUE`() {
            val bytes = buildWasmBytes {
                function("divEdge", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64DivS()
                }
            }
            assertBothEqualI64(Long.MIN_VALUE, bytes, "divEdge", Long.MIN_VALUE, -1L)
        }

        @Test
        fun `i64 rem_s MIN_VALUE by negative one returns zero`() {
            val bytes = buildWasmBytes {
                function("remEdge", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { func, asm ->
                    asm.localGet(func.getParameter(0))
                    asm.localGet(func.getParameter(1))
                    asm.i64RemS()
                }
            }
            assertBothEqualI64(0L, bytes, "remEdge", Long.MIN_VALUE, -1L)
        }
    }
}

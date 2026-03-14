package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class ConstantFoldingComprehensiveTest {

    private val fold = ConstantFolding()

    private fun buildAndFold(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return fold.run(ir.build())
    }

    @Nested
    inner class IntegerRemainder {

        @Test
        fun `folds constant srem`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = srem(Constant.I32(17), Constant.I32(5))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(2, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant srem with negative dividend`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = srem(Constant.I32(-17), Constant.I32(5))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-2, (ret.value as Constant.I32).value)
        }

        @Test
        fun `does not fold srem by zero`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = srem(Constant.I32(10), Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertTrue(insts.any { it is SRem })
        }

        @Test
        fun `folds constant urem`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = urem(Constant.I32(17), Constant.I32(5))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(2, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant i64 srem`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = srem(Constant.I64(100L), Constant.I64(7L))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(2L, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class UnsignedDivision {

        @Test
        fun `folds constant udiv`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = udiv(Constant.I32(20), Constant.I32(3))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(6, (ret.value as Constant.I32).value)
        }

        @Test
        fun `does not fold udiv by zero`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = udiv(Constant.I32(10), Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val insts = module.functions[0].blocks[0].instructions
            assertTrue(insts.any { it is UDiv })
        }
    }

    @Nested
    inner class BitwiseOperations {

        @Test
        fun `folds constant or`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = or(Constant.I32(0x0F00), Constant.I32(0x00F0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0x0FF0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant xor`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = xor(Constant.I32(0xFF00), Constant.I32(0x0FF0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0xF0F0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant i64 and`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = and(Constant.I64(0xFFFF0000L), Constant.I64(0x0000FFFFL))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0L, (ret.value as Constant.I64).value)
        }

        @Test
        fun `folds constant i64 or`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = or(Constant.I64(0xFF00L), Constant.I64(0x00FFL))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0xFFFFL, (ret.value as Constant.I64).value)
        }

        @Test
        fun `folds constant i64 xor`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = xor(Constant.I64(0xFF00L), Constant.I64(0xFFFFL))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0x00FFL, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class ShiftOperations {

        @Test
        fun `folds constant lshr`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = lshr(Constant.I32(1024), Constant.I32(3))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(128, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant ashr preserving sign`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = ashr(Constant.I32(-16), Constant.I32(2))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-4, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds constant i64 shl`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = shl(Constant.I64(1L), Constant.I64(32L))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(4294967296L, (ret.value as Constant.I64).value)
        }

        @Test
        fun `folds constant i64 lshr`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = lshr(Constant.I64(0x100000000L), Constant.I64(16L))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0x10000L, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class FloatingPoint {

        @Test
        fun `folds constant f64 subtraction`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F64)
                appendBlock("entry")
                val r = fsub(Constant.F64(10.5), Constant.F64(3.5))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(7.0, (ret.value as Constant.F64).value)
        }

        @Test
        fun `folds constant f64 multiplication`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F64)
                appendBlock("entry")
                val r = fmul(Constant.F64(3.0), Constant.F64(4.0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(12.0, (ret.value as Constant.F64).value)
        }

        @Test
        fun `folds constant f64 division`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F64)
                appendBlock("entry")
                val r = fdiv(Constant.F64(10.0), Constant.F64(4.0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(2.5, (ret.value as Constant.F64).value)
        }

        @Test
        fun `folds constant f64 negation`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F64)
                appendBlock("entry")
                val r = fneg(Constant.F64(3.14))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-3.14, (ret.value as Constant.F64).value)
        }

        @Test
        fun `folds constant f32 addition`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F32)
                appendBlock("entry")
                val r = fadd(Constant.F32(1.5f), Constant.F32(2.5f))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(4.0f, (ret.value as Constant.F32).value)
        }

        @Test
        fun `folds constant f32 negation`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.F32)
                appendBlock("entry")
                val r = fneg(Constant.F32(2.5f))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-2.5f, (ret.value as Constant.F32).value)
        }
    }

    @Nested
    inner class IntegerNegation {

        @Test
        fun `folds constant i64 negation`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = neg(Constant.I64(100L))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-100L, (ret.value as Constant.I64).value)
        }

        @Test
        fun `folds negation of zero`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = neg(Constant.I32(0))
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }
    }

    @Nested
    inner class SignExtension {

        @Test
        fun `folds sext i1 true to i32`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = sext(Constant.I1(true), Type.I32)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-1, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds sext i1 false to i32`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = sext(Constant.I1(false), Type.I32)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds sext i32 to i64`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = sext(Constant.I32(-42), Type.I64)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-42L, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class IntTrunc {

        @Test
        fun `folds trunc i64 to i32`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = trunc(Constant.I64(0x1_FFFF_FFFEL), Type.I32)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(-2, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds trunc i32 to i16`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I16)
                appendBlock("entry")
                val r = trunc(Constant.I32(0x1234), Type.I16)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0x1234.toShort(), (ret.value as Constant.I16).value)
        }

        @Test
        fun `folds trunc i64 to i8`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I8)
                appendBlock("entry")
                val r = trunc(Constant.I64(0xABL), Type.I8)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0xAB.toByte(), (ret.value as Constant.I8).value)
        }

        @Test
        fun `folds trunc i64 to i1`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I1)
                appendBlock("entry")
                val r = trunc(Constant.I64(3L), Type.I1)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(true, (ret.value as Constant.I1).value)
        }
    }

    @Nested
    inner class ZeroExtension {

        @Test
        fun `folds zext i1 true to i64`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = zext(Constant.I1(true), Type.I64)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(1L, (ret.value as Constant.I64).value)
        }

        @Test
        fun `folds zext i8 to i32`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = zext(Constant.I8(0xFF.toByte()), Type.I32)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(255, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds zext i16 to i64`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = zext(Constant.I16(0x1234), Type.I64)
                ret(r)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0x1234L, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class ICmpPredicates {

        @Test
        fun `folds icmp eq true`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = icmp(ICmpPredicate.EQ, Constant.I32(5), Constant.I32(5))
                val ext = zext(r, Type.I32)
                ret(ext)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(1, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds icmp ne`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = icmp(ICmpPredicate.NE, Constant.I32(5), Constant.I32(10))
                val ext = zext(r, Type.I32)
                ret(ext)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(1, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds icmp sge`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = icmp(ICmpPredicate.SGE, Constant.I32(10), Constant.I32(10))
                val ext = zext(r, Type.I32)
                ret(ext)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(1, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds icmp sgt false`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                val r = icmp(ICmpPredicate.SGT, Constant.I32(5), Constant.I32(10))
                val ext = zext(r, Type.I32)
                ret(ext)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(0, (ret.value as Constant.I32).value)
        }

        @Test
        fun `folds icmp i64`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r = icmp(ICmpPredicate.SLT, Constant.I64(100L), Constant.I64(200L))
                val ext = zext(r, Type.I64)
                ret(ext)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(1L, (ret.value as Constant.I64).value)
        }
    }

    @Nested
    inner class ExternalFunctions {

        @Test
        fun `skips external functions`() {
            val module = buildAndFold {
                declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
            }
            assertTrue(module.functions[0].isExternal)
        }
    }

    @Nested
    inner class ChainedExpressions {

        @Test
        fun `folds deeply nested constant chain across types`() {
            val module = buildAndFold {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val a = add(Constant.I32(10), Constant.I32(20))
                val b = zext(a, Type.I64)
                val c = add(b, Constant.I64(100L))
                ret(c)
                finalizeFunction()
            }
            val ret = module.functions[0].blocks[0].instructions.last() as Ret
            assertEquals(130L, (ret.value as Constant.I64).value)
        }
    }
}

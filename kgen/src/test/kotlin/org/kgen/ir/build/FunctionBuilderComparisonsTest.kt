package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FunctionBuilderComparisonsTest {

    @Nested
    inner class UnsignedComparisons {

        @Test
        fun `ult emits unsigned less than`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("u_lt",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.ult(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.ULT, icmp.predicate)
        }

        @Test
        fun `ule emits unsigned less or equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("u_le",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.ule(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.ULE, icmp.predicate)
        }

        @Test
        fun `ugt emits unsigned greater than`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("u_gt",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.ugt(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.UGT, icmp.predicate)
        }

        @Test
        fun `uge emits unsigned greater or equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("u_ge",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.uge(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.UGE, icmp.predicate)
        }
    }

    @Nested
    inner class FloatComparisons {

        @Test
        fun `feq emits ordered equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_eq",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.feq(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.OEQ, fcmp.predicate)
        }

        @Test
        fun `fne emits ordered not equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_ne",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.fne(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.ONE, fcmp.predicate)
        }

        @Test
        fun `flt emits ordered less than`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_lt",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.flt(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.OLT, fcmp.predicate)
        }

        @Test
        fun `fle emits ordered less or equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_le",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.fle(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.OLE, fcmp.predicate)
        }

        @Test
        fun `fgt emits ordered greater than`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_gt",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.fgt(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.OGT, fcmp.predicate)
        }

        @Test
        fun `fge emits ordered greater or equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f_ge",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            val ins = fn.instructions
            val result = ins.fge(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val fcmp = mod.functions[0].blocks[0].instructions[0] as FCmp
            assertEquals(FCmpPredicate.OGE, fcmp.predicate)
        }
    }

    @Nested
    inner class SignedComparisons {

        @Test
        fun `eq emits integer equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("s_eq",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.eq(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.EQ, icmp.predicate)
        }

        @Test
        fun `ne emits integer not equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("s_ne",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.ne(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.NE, icmp.predicate)
        }

        @Test
        fun `lt emits signed less than`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("s_lt",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.lt(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.SLT, icmp.predicate)
        }

        @Test
        fun `ge emits signed greater or equal`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("s_ge",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            val ins = fn.instructions
            val result = ins.ge(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val icmp = mod.functions[0].blocks[0].instructions[0] as ICmp
            assertEquals(ICmpPredicate.SGE, icmp.predicate)
        }
    }

    @Nested
    inner class ConversionOperations {

        @Test
        fun `uintCast zero extends`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("uzext", listOf(Param("x", Type.I32)), Type.I64)
            val ins = fn.instructions
            val result = ins.uintCast(fn.param(0), Type.I64)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is ZExt)
        }

        @Test
        fun `uintCast truncates`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("utrunc", listOf(Param("x", Type.I64)), Type.I32)
            val ins = fn.instructions
            val result = ins.uintCast(fn.param(0), Type.I32)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is IntTrunc)
        }

        @Test
        fun `uintCast same size is identity`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("uid", listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.uintCast(fn.param(0), Type.I32)
            assertSame(fn.param(0), result)
            fn.ret(result)
            fn.end()
        }

        @Test
        fun `intCast same size is identity`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("sid", listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.intCast(fn.param(0), Type.I32)
            assertSame(fn.param(0), result)
            fn.ret(result)
            fn.end()
        }

        @Test
        fun `floatCast same size is identity`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("fid", listOf(Param("x", Type.F64)), Type.F64)
            val ins = fn.instructions
            val result = ins.floatCast(fn.param(0), Type.F64)
            assertSame(fn.param(0), result)
            fn.ret(result)
            fn.end()
        }

        @Test
        fun `floatCast truncates F64 to F32`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("ftrunc", listOf(Param("x", Type.F64)), Type.F32)
            val ins = fn.instructions
            val result = ins.floatCast(fn.param(0), Type.F32)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FPTrunc)
        }

        @Test
        fun `floatCast extends F32 to F64`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("fext", listOf(Param("x", Type.F32)), Type.F64)
            val ins = fn.instructions
            val result = ins.floatCast(fn.param(0), Type.F64)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FPExt)
        }

        @Test
        fun `toFloat emits SIToFP`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("i2f", listOf(Param("x", Type.I32)), Type.F64)
            val ins = fn.instructions
            val result = ins.toFloat(fn.param(0), Type.F64)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is SIToFP)
        }

        @Test
        fun `toInt emits FPToSI`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("f2i", listOf(Param("x", Type.F64)), Type.I32)
            val ins = fn.instructions
            val result = ins.toInt(fn.param(0), Type.I32)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FPToSI)
        }

        @Test
        fun `bitcast emits BitCast`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("reinterpret", listOf(Param("x", Type.I32)), Type.F32)
            val ins = fn.instructions
            val result = ins.bitcast(fn.param(0), Type.F32)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is BitCast)
        }
    }

    @Nested
    inner class VariableWithExplicitType {

        @Test
        fun `variable with explicit type allocates correct type`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("typed_var", emptyList(), Type.I64)
            val ins = fn.instructions
            val v = ins.variable(Type.I64, Type.i64(42L))
            fn.ret(ins.get(v))
            fn.end()

            val mod = ir.build()
            assertTrue(IrVerifier.verify(mod).isValid)
            val instrs = mod.functions[0].blocks[0].instructions
            val alloca = instrs[0] as Alloca
            assertEquals(Type.I64, alloca.allocType)
        }
    }

    @Nested
    inner class ErrorCases {

        @Test
        fun `breakOut outside loop fails`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("bad_break", emptyList(), Type.Void)
            assertThrows(IllegalStateException::class.java) {
                fn.breakOut()
            }
        }

        @Test
        fun `continueOn outside loop fails`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("bad_continue", emptyList(), Type.Void)
            assertThrows(IllegalStateException::class.java) {
                fn.continueOn()
            }
        }

        @Test
        fun `end without terminator adds unreachable`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("no_ret", emptyList(), Type.Void)
            fn.end()

            val mod = ir.build()
            val lastInstr = mod.functions[0].blocks.last().instructions.last()
            assertTrue(lastInstr is Unreachable)
        }
    }

    @Nested
    inner class RawEscapeHatch {

        @Test
        fun `raw block can access low-level instructions`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("raw_phi", listOf(Param("x", Type.I32)), Type.I32)
            var result: Value? = null
            fn.raw {
                val ptr = alloca(Type.I32, align = 8)
                store(fn.param(0), ptr)
                result = load(Type.I32, ptr)
            }
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            assertTrue(IrVerifier.verify(mod).isValid)
        }
    }

    @Nested
    inner class AdditionalArithmetic {

        @Test
        fun `udiv emits UDiv`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("unsigned_div",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.udiv(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is UDiv)
        }

        @Test
        fun `urem emits URem`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("unsigned_rem",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.urem(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is URem)
        }

        @Test
        fun `fneg emits FNeg`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("float_neg",
                listOf(Param("x", Type.F64)), Type.F64)
            val ins = fn.instructions
            val result = ins.fneg(fn.param(0))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FNeg)
        }

        @Test
        fun `sqrt emits Sqrt`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("square_root",
                listOf(Param("x", Type.F64)), Type.F64)
            val ins = fn.instructions
            val result = ins.sqrt(fn.param(0))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is Sqrt)
        }

        @Test
        fun `not emits Not`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("bitwise_not",
                listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.not(fn.param(0))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is Not)
        }

        @Test
        fun `ushr emits LShr`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("logical_shift",
                listOf(Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
            val ins = fn.instructions
            val result = ins.ushr(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is LShr)
        }

        @Test
        fun `fsub emits FSub`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("float_sub",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            val ins = fn.instructions
            val result = ins.fsub(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FSub)
        }

        @Test
        fun `fdiv emits FDiv`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("float_div",
                listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            val ins = fn.instructions
            val result = ins.fdiv(fn.param(0), fn.param(1))
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is FDiv)
        }
    }

    @Nested
    inner class ParameterAccess {

        @Test
        fun `param returns correct parameter`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("params",
                listOf(Param("x", Type.I32), Param("y", Type.F64)), Type.I32)
            assertEquals("x", (fn.param(0) as Parameter).name)
            assertEquals(Type.I32, fn.param(0).type)
            assertEquals("y", (fn.param(1) as Parameter).name)
            assertEquals(Type.F64, fn.param(1).type)
            assertEquals(2, fn.paramCount)
            fn.ret(fn.param(0))
            fn.end()
        }
    }
}

package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.Type
import org.kgen.ir.VectorReduceOp

class AutoVectorizationCompanionTest {

    @Nested
    inner class SuggestVectorFactor {

        @Test
        fun `i8 suggests factor 16`() {
            assertEquals(16, AutoVectorization.suggestVectorFactor(Type.I8))
        }

        @Test
        fun `i16 suggests factor 8`() {
            assertEquals(8, AutoVectorization.suggestVectorFactor(Type.I16))
        }

        @Test
        fun `i32 suggests factor 4`() {
            assertEquals(4, AutoVectorization.suggestVectorFactor(Type.I32))
        }

        @Test
        fun `f32 suggests factor 4`() {
            assertEquals(4, AutoVectorization.suggestVectorFactor(Type.F32))
        }

        @Test
        fun `i64 suggests factor 2`() {
            assertEquals(2, AutoVectorization.suggestVectorFactor(Type.I64))
        }

        @Test
        fun `f64 suggests factor 2`() {
            assertEquals(2, AutoVectorization.suggestVectorFactor(Type.F64))
        }

        @Test
        fun `pointer type suggests factor 1`() {
            assertEquals(1, AutoVectorization.suggestVectorFactor(Type.OpaquePointer))
        }

        @Test
        fun `struct type suggests factor 1`() {
            assertEquals(1, AutoVectorization.suggestVectorFactor(Type.Struct(null, listOf(Type.I32, Type.I32))))
        }
    }

    @Nested
    inner class ToVectorReduceOp {

        @Test
        fun `ADD maps to VectorReduceOp ADD`() {
            assertEquals(VectorReduceOp.ADD, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.ADD))
        }

        @Test
        fun `MUL maps to VectorReduceOp MUL`() {
            assertEquals(VectorReduceOp.MUL, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.MUL))
        }

        @Test
        fun `AND maps to VectorReduceOp AND`() {
            assertEquals(VectorReduceOp.AND, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.AND))
        }

        @Test
        fun `OR maps to VectorReduceOp OR`() {
            assertEquals(VectorReduceOp.OR, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.OR))
        }

        @Test
        fun `XOR maps to VectorReduceOp XOR`() {
            assertEquals(VectorReduceOp.XOR, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.XOR))
        }

        @Test
        fun `FADD maps to VectorReduceOp FADD`() {
            assertEquals(VectorReduceOp.FADD, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.FADD))
        }

        @Test
        fun `FMUL maps to VectorReduceOp FMUL`() {
            assertEquals(VectorReduceOp.FMUL, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.FMUL))
        }

        @Test
        fun `all reduction ops are covered`() {
            for (op in AutoVectorization.ReductionOp.entries) {
                assertDoesNotThrow { AutoVectorization.toVectorReduceOp(op) }
            }
        }
    }
}

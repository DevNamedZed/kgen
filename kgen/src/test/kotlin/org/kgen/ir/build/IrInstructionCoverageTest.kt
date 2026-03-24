package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*
import org.kgen.ir.verify.IrVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Comprehensive coverage test for all 171 IR instruction types.
 * Each test creates a module via ModuleBuilder, emits the instruction,
 * and verifies it appears in the built module.
 */
class IrInstructionCoverageTest {

    private fun i32(v: Int) = Constant.I32(v)
    private fun i64(v: Long) = Constant.I64(v)
    private fun f32(v: Float) = Constant.F32(v)
    private fun f64(v: Double) = Constant.F64(v)
    private fun i1(v: Boolean) = Constant.I1(v)

    private fun buildSingleInstr(
        params: List<Param> = listOf(Param("a", Type.I32), Param("b", Type.I32)),
        returnType: Type = Type.I32,
        block: ModuleBuilder.(List<Parameter>) -> Unit,
    ): Instruction {
        val ir = ModuleBuilder("test", Target.x86_64())
        val p = ir.createFunction("f", params, returnType)
        ir.appendBlock("entry")
        ir.block(p)
        ir.finalizeFunction()
        val mod = ir.build()
        return mod.functions[0].blocks[0].instructions[0]
    }

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return ir.build()
    }

    // --- Integer arithmetic ---

    @Test
    fun `Add instruction`() {
        val inst = buildSingleInstr { p -> val r = add(p[0], p[1]); ret(r) }
        assertTrue(inst is Add)
    }

    @Test
    fun `Add with nuw flag`() {
        val inst = buildSingleInstr { p -> val r = add(p[0], p[1], nuw = true); ret(r) }
        assertTrue((inst as Add).nuw)
    }

    @Test
    fun `Add with nsw flag`() {
        val inst = buildSingleInstr { p -> val r = add(p[0], p[1], nsw = true); ret(r) }
        assertTrue((inst as Add).nsw)
    }

    @Test
    fun `Sub instruction`() {
        val inst = buildSingleInstr { p -> val r = sub(p[0], p[1]); ret(r) }
        assertTrue(inst is Sub)
    }

    @Test
    fun `Sub with nuw and nsw flags`() {
        val inst = buildSingleInstr { p -> val r = sub(p[0], p[1], nuw = true, nsw = true); ret(r) }
        val s = inst as Sub
        assertTrue(s.nuw)
        assertTrue(s.nsw)
    }

    @Test
    fun `Mul instruction`() {
        val inst = buildSingleInstr { p -> val r = mul(p[0], p[1]); ret(r) }
        assertTrue(inst is Mul)
    }

    @Test
    fun `Mul with flags`() {
        val inst = buildSingleInstr { p -> val r = mul(p[0], p[1], nuw = true, nsw = true); ret(r) }
        val m = inst as Mul
        assertTrue(m.nuw && m.nsw)
    }

    @Test
    fun `UDiv instruction`() {
        val inst = buildSingleInstr { p -> val r = udiv(p[0], p[1]); ret(r) }
        assertTrue(inst is UDiv)
    }

    @Test
    fun `UDiv with exact flag`() {
        val inst = buildSingleInstr { p -> val r = udiv(p[0], p[1], exact = true); ret(r) }
        assertTrue((inst as UDiv).exact)
    }

    @Test
    fun `SDiv instruction`() {
        val inst = buildSingleInstr { p -> val r = sdiv(p[0], p[1]); ret(r) }
        assertTrue(inst is SDiv)
    }

    @Test
    fun `SDiv with exact flag`() {
        val inst = buildSingleInstr { p -> val r = sdiv(p[0], p[1], exact = true); ret(r) }
        assertTrue((inst as SDiv).exact)
    }

    @Test
    fun `URem instruction`() {
        val inst = buildSingleInstr { p -> val r = urem(p[0], p[1]); ret(r) }
        assertTrue(inst is URem)
    }

    @Test
    fun `SRem instruction`() {
        val inst = buildSingleInstr { p -> val r = srem(p[0], p[1]); ret(r) }
        assertTrue(inst is SRem)
    }

    @Test
    fun `Neg instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = neg(p[0]); ret(r) }
        assertTrue(inst is Neg)
    }

    // --- Overflow-checked arithmetic ---

    @Test
    fun `SAddOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = saddOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is SAddOverflow)
    }

    @Test
    fun `UAddOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = uaddOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is UAddOverflow)
    }

    @Test
    fun `SSubOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = ssubOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is SSubOverflow)
    }

    @Test
    fun `USubOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = usubOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is USubOverflow)
    }

    @Test
    fun `SMulOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = smulOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is SMulOverflow)
    }

    @Test
    fun `UMulOverflow instruction`() {
        val inst = buildSingleInstr { p ->
            val r = umulOverflow(p[0], p[1])
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is UMulOverflow)
    }

    // --- Saturating arithmetic ---

    @Test
    fun `SAddSat instruction`() {
        val inst = buildSingleInstr { p -> val r = saddSat(p[0], p[1]); ret(r) }
        assertTrue(inst is SAddSat)
    }

    @Test
    fun `UAddSat instruction`() {
        val inst = buildSingleInstr { p -> val r = uaddSat(p[0], p[1]); ret(r) }
        assertTrue(inst is UAddSat)
    }

    @Test
    fun `SSubSat instruction`() {
        val inst = buildSingleInstr { p -> val r = ssubSat(p[0], p[1]); ret(r) }
        assertTrue(inst is SSubSat)
    }

    @Test
    fun `USubSat instruction`() {
        val inst = buildSingleInstr { p -> val r = usubSat(p[0], p[1]); ret(r) }
        assertTrue(inst is USubSat)
    }

    // --- Min, max, abs ---

    @Test
    fun `SMin instruction`() {
        val inst = buildSingleInstr { p -> val r = smin(p[0], p[1]); ret(r) }
        assertTrue(inst is SMin)
    }

    @Test
    fun `SMax instruction`() {
        val inst = buildSingleInstr { p -> val r = smax(p[0], p[1]); ret(r) }
        assertTrue(inst is SMax)
    }

    @Test
    fun `UMin instruction`() {
        val inst = buildSingleInstr { p -> val r = umin(p[0], p[1]); ret(r) }
        assertTrue(inst is UMin)
    }

    @Test
    fun `UMax instruction`() {
        val inst = buildSingleInstr { p -> val r = umax(p[0], p[1]); ret(r) }
        assertTrue(inst is UMax)
    }

    @Test
    fun `Abs instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = abs(p[0]); ret(r) }
        assertTrue(inst is Abs)
    }

    @Test
    fun `Abs with isIntMin flag`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = abs(p[0], isIntMin = true); ret(r) }
        assertTrue((inst as Abs).isIntMin)
    }

    // --- Float arithmetic ---

    @Test
    fun `FAdd instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fadd(p[0], p[1]); ret(r) }
        assertTrue(inst is FAdd)
    }

    @Test
    fun `FSub instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fsub(p[0], p[1]); ret(r) }
        assertTrue(inst is FSub)
    }

    @Test
    fun `FMul instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.F32,
        ) { p -> val r = fmul(p[0], p[1]); ret(r) }
        assertTrue(inst is FMul)
    }

    @Test
    fun `FDiv instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fdiv(p[0], p[1]); ret(r) }
        assertTrue(inst is FDiv)
    }

    @Test
    fun `FRem instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = frem(p[0], p[1]); ret(r) }
        assertTrue(inst is FRem)
    }

    @Test
    fun `FNeg instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fneg(p[0]); ret(r) }
        assertTrue(inst is FNeg)
    }

    @Test
    fun `FAbs instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fabs(p[0]); ret(r) }
        assertTrue(inst is FAbs)
    }

    @Test
    fun `FMA instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fma(p[0], p[1], p[2]); ret(r) }
        assertTrue(inst is FMA)
    }

    @Test
    fun `FMin instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fmin(p[0], p[1]); ret(r) }
        assertTrue(inst is FMin)
    }

    @Test
    fun `FMax instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = fmax(p[0], p[1]); ret(r) }
        assertTrue(inst is FMax)
    }

    @Test
    fun `Sqrt instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = sqrt(p[0]); ret(r) }
        assertTrue(inst is Sqrt)
    }

    @Test
    fun `Ceil instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = ceil(p[0]); ret(r) }
        assertTrue(inst is Ceil)
    }

    @Test
    fun `Floor instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = floor(p[0]); ret(r) }
        assertTrue(inst is Floor)
    }

    @Test
    fun `Round instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = round(p[0]); ret(r) }
        assertTrue(inst is Round)
    }

    @Test
    fun `Trunc float instruction via ftrunc`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = ftrunc(p[0]); ret(r) }
        assertTrue(inst is FTrunc)
    }

    @Test
    fun `CopySign instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64,
        ) { p -> val r = copySign(p[0], p[1]); ret(r) }
        assertTrue(inst is CopySign)
    }

    // --- Bitwise operations ---

    @Test
    fun `And instruction`() {
        val inst = buildSingleInstr { p -> val r = and(p[0], p[1]); ret(r) }
        assertTrue(inst is And)
    }

    @Test
    fun `Or instruction`() {
        val inst = buildSingleInstr { p -> val r = or(p[0], p[1]); ret(r) }
        assertTrue(inst is Or)
    }

    @Test
    fun `Xor instruction`() {
        val inst = buildSingleInstr { p -> val r = xor(p[0], p[1]); ret(r) }
        assertTrue(inst is Xor)
    }

    @Test
    fun `Not instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = not(p[0]); ret(r) }
        assertTrue(inst is Not)
    }

    @Test
    fun `Shl instruction`() {
        val inst = buildSingleInstr { p -> val r = shl(p[0], p[1]); ret(r) }
        assertTrue(inst is Shl)
    }

    @Test
    fun `Shl with nuw and nsw`() {
        val inst = buildSingleInstr { p -> val r = shl(p[0], p[1], nuw = true, nsw = true); ret(r) }
        val s = inst as Shl
        assertTrue(s.nuw && s.nsw)
    }

    @Test
    fun `LShr instruction`() {
        val inst = buildSingleInstr { p -> val r = lshr(p[0], p[1]); ret(r) }
        assertTrue(inst is LShr)
    }

    @Test
    fun `LShr with exact`() {
        val inst = buildSingleInstr { p -> val r = lshr(p[0], p[1], exact = true); ret(r) }
        assertTrue((inst as LShr).exact)
    }

    @Test
    fun `AShr instruction`() {
        val inst = buildSingleInstr { p -> val r = ashr(p[0], p[1]); ret(r) }
        assertTrue(inst is AShr)
    }

    @Test
    fun `AShr with exact`() {
        val inst = buildSingleInstr { p -> val r = ashr(p[0], p[1], exact = true); ret(r) }
        assertTrue((inst as AShr).exact)
    }

    @Test
    fun `RotateLeft instruction`() {
        val inst = buildSingleInstr { p -> val r = rotateLeft(p[0], p[1]); ret(r) }
        assertTrue(inst is Rotl)
    }

    @Test
    fun `RotateRight instruction`() {
        val inst = buildSingleInstr { p -> val r = rotateRight(p[0], p[1]); ret(r) }
        assertTrue(inst is Rotr)
    }

    // --- Bit manipulation ---

    @Test
    fun `Ctlz instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = ctlz(p[0]); ret(r) }
        assertTrue(inst is Ctlz)
    }

    @Test
    fun `Ctlz with isZeroPoison`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = ctlz(p[0], isZeroPoison = true); ret(r) }
        assertTrue((inst as Ctlz).isZeroPoison)
    }

    @Test
    fun `Cttz instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = cttz(p[0]); ret(r) }
        assertTrue(inst is Cttz)
    }

    @Test
    fun `Cttz with isZeroPoison`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = cttz(p[0], isZeroPoison = true); ret(r) }
        assertTrue((inst as Cttz).isZeroPoison)
    }

    @Test
    fun `Ctpop instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = ctpop(p[0]); ret(r) }
        assertTrue(inst is Ctpop)
    }

    @Test
    fun `BSwap instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = bswap(p[0]); ret(r) }
        assertTrue(inst is BSwap)
    }

    @Test
    fun `BitReverse instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.I32)),
        ) { p -> val r = bitReverse(p[0]); ret(r) }
        assertTrue(inst is BitReverse)
    }

    // --- Comparison: ICmp all predicates ---

    @Test
    fun `ICmp EQ`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.EQ, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.EQ, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp NE`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.NE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.NE, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp UGT`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.UGT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.UGT, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp UGE`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.UGE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.UGE, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp ULT`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.ULT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.ULT, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp ULE`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.ULE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.ULE, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp SGT`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.SGT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.SGT, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp SGE`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.SGE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.SGE, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp SLT`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.SLT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.SLT, (inst as ICmp).predicate)
    }

    @Test
    fun `ICmp SLE`() {
        val inst = buildSingleInstr { p ->
            val r = icmp(ICmpPredicate.SLE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(ICmpPredicate.SLE, (inst as ICmp).predicate)
    }

    // --- Comparison: FCmp all predicates ---

    @Test
    fun `FCmp OEQ`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.OEQ, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.OEQ, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp ONE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.ONE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.ONE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp OGT`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.OGT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.OGT, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp OGE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.OGE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.OGE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp OLT`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.OLT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.OLT, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp OLE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.OLE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.OLE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp ORD`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.ORD, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.ORD, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp UEQ`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.UEQ, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.UEQ, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp UNE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.UNE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.UNE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp UGT`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.UGT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.UGT, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp UGE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.UGE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.UGE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp ULT`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.ULT, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.ULT, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp ULE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.ULE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.ULE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp UNO`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.UNO, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.UNO, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp TRUE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.TRUE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.TRUE, (inst as FCmp).predicate)
    }

    @Test
    fun `FCmp FALSE`() {
        val inst = buildSingleInstr(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I32,
        ) { p ->
            val r = fcmp(FCmpPredicate.FALSE, p[0], p[1])
            ret(select(r, i32(1), i32(0)))
        }
        assertEquals(FCmpPredicate.FALSE, (inst as FCmp).predicate)
    }

    // --- Memory ---

    @Test
    fun `Alloca instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
        ) { p ->
            val ptr = alloca(Type.I32)
            store(p[0], ptr)
            ret(p[0])
        }
        assertTrue(inst is Alloca)
        assertEquals(Type.I32, (inst as Alloca).allocType)
    }

    @Test
    fun `Alloca with alignment`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
        ) { p ->
            val ptr = alloca(Type.I32, align = 16)
            store(p[0], ptr)
            ret(p[0])
        }
        assertEquals(16, (inst as Alloca).align)
    }

    @Test
    fun `Alloca with numElements`() {
        val inst = buildSingleInstr(
            params = listOf(Param("n", Type.I32)),
        ) { p ->
            val ptr = alloca(Type.I32, numElements = p[0])
            ret(p[0])
        }
        assertNotNull((inst as Alloca).numElements)
    }

    @Test
    fun `Load instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val v = load(Type.I32, p[0])
            ret(v)
        }
        assertTrue(inst is Load)
        assertEquals(Type.I32, (inst as Load).loadType)
    }

    @Test
    fun `Load volatile`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val v = load(Type.I32, p[0], volatile = true)
            ret(v)
        }
        assertTrue((inst as Load).volatile)
    }

    @Test
    fun `Load atomic`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val v = load(Type.I32, p[0], ordering = AtomicOrdering.SEQ_CST)
            ret(v)
        }
        assertEquals(AtomicOrdering.SEQ_CST, (inst as Load).ordering)
    }

    @Test
    fun `Store instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32)), Param("v", Type.I32)), Type.Void)
            appendBlock("entry")
            store(param(1), param(0))
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Store)
    }

    @Test
    fun `Store volatile`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32)), Param("v", Type.I32)), Type.Void)
            appendBlock("entry")
            store(param(1), param(0), volatile = true)
            ret()
            finalizeFunction()
        }
        assertTrue((mod.functions[0].blocks[0].instructions[0] as Store).volatile)
    }

    @Test
    fun `Store atomic`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32)), Param("v", Type.I32)), Type.Void)
            appendBlock("entry")
            store(param(1), param(0), ordering = AtomicOrdering.RELEASE)
            ret()
            finalizeFunction()
        }
        assertEquals(AtomicOrdering.RELEASE, (mod.functions[0].blocks[0].instructions[0] as Store).ordering)
    }

    @Test
    fun `GetElementPtr instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val ep = gep(Type.I32, p[0], i32(3))
            val v = load(Type.I32, ep)
            ret(v)
        }
        assertTrue(inst is GetElementPtr)
    }

    @Test
    fun `GetElementPtr not inBounds`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val ep = gep(Type.I32, p[0], i32(0), inBounds = false)
            val v = load(Type.I32, ep)
            ret(v)
        }
        assertFalse((inst as GetElementPtr).inBounds)
    }

    @Test
    fun `Fence instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            fence(AtomicOrdering.SEQ_CST)
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Fence)
        assertEquals(AtomicOrdering.SEQ_CST, (inst as Fence).ordering)
    }

    @Test
    fun `Fence with syncScope`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            fence(AtomicOrdering.ACQ_REL, syncScope = "singlethread")
            ret()
            finalizeFunction()
        }
        assertEquals("singlethread", (mod.functions[0].blocks[0].instructions[0] as Fence).syncScope)
    }

    @Test
    fun `CmpXchg instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val r = cmpxchg(p[0], i32(0), i32(1), AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE)
            val v = extractValue(r, 0)
            ret(v)
        }
        assertTrue(inst is CmpXchg)
    }

    @Test
    fun `CmpXchg weak and volatile`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val r = cmpxchg(p[0], i32(0), i32(1), AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE, weak = true, volatile = true)
            val v = extractValue(r, 0)
            ret(v)
        }
        val cx = inst as CmpXchg
        assertTrue(cx.weak)
        assertTrue(cx.volatile)
    }

    @Test
    fun `AtomicRMW ADD`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val r = atomicRMW(AtomicRMWOp.ADD, p[0], i32(1), AtomicOrdering.SEQ_CST)
            ret(r)
        }
        assertTrue(inst is AtomicRMW)
        assertEquals(AtomicRMWOp.ADD, (inst as AtomicRMW).op)
    }

    @Test
    fun `AtomicRMW XCHG`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32))),
            returnType = Type.I32,
        ) { p ->
            val r = atomicRMW(AtomicRMWOp.XCHG, p[0], i32(42), AtomicOrdering.ACQUIRE)
            ret(r)
        }
        assertEquals(AtomicRMWOp.XCHG, (inst as AtomicRMW).op)
    }

    @Test
    fun `MemCpy instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memcpy(param(0), param(1), i32(16))
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is MemCpy)
    }

    @Test
    fun `MemSet instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("dst", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memset(param(0), Constant.I8(0), i32(64))
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is MemSet)
    }

    @Test
    fun `MemMove instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memmove(param(0), param(1), i32(32))
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is MemMove)
    }

    @Test
    fun `Prefetch instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            prefetch(param(0), rw = 0, locality = 3, cacheType = 1)
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0] as Prefetch
        assertEquals(0, inst.rw)
        assertEquals(3, inst.locality)
        assertEquals(1, inst.cacheType)
    }

    // --- Stack save/restore ---

    @Test
    fun `StackSave instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val sp = stackSave()
            stackRestore(sp)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is StackSave)
    }

    @Test
    fun `StackRestore instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val sp = stackSave()
            stackRestore(sp)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is StackRestore)
    }

    // --- Lifetime markers ---

    @Test
    fun `LifetimeStart instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            lifetimeStart(ptr, 4)
            lifetimeEnd(ptr, 4)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is LifetimeStart)
    }

    @Test
    fun `LifetimeEnd instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            lifetimeStart(ptr, 4)
            lifetimeEnd(ptr, 4)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[2] is LifetimeEnd)
    }

    // --- Conversions ---

    @Test
    fun `IntTrunc instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.I32,
        ) { p -> ret(trunc(p[0], Type.I32)) }
        assertTrue(inst is IntTrunc)
    }

    @Test
    fun `ZExt instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I64,
        ) { p -> ret(zext(p[0], Type.I64)) }
        assertTrue(inst is ZExt)
    }

    @Test
    fun `SExt instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I16)),
            returnType = Type.I32,
        ) { p -> ret(sext(p[0], Type.I32)) }
        assertTrue(inst is SExt)
    }

    @Test
    fun `FPTrunc instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.F32,
        ) { p -> ret(fptrunc(p[0], Type.F32)) }
        assertTrue(inst is FPTrunc)
    }

    @Test
    fun `FPExt instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.F32)),
            returnType = Type.F64,
        ) { p -> ret(fpext(p[0], Type.F64)) }
        assertTrue(inst is FPExt)
    }

    @Test
    fun `FPToUI instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I32,
        ) { p -> ret(fptoui(p[0], Type.I32)) }
        assertTrue(inst is FPToUI)
    }

    @Test
    fun `FPToSI instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I32,
        ) { p -> ret(fptosi(p[0], Type.I32)) }
        assertTrue(inst is FPToSI)
    }

    @Test
    fun `UIToFP instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F64,
        ) { p -> ret(uitofp(p[0], Type.F64)) }
        assertTrue(inst is UIToFP)
    }

    @Test
    fun `SIToFP instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F64,
        ) { p -> ret(sitofp(p[0], Type.F64)) }
        assertTrue(inst is SIToFP)
    }

    @Test
    fun `PtrToInt instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.I64,
        ) { p -> ret(ptrtoint(p[0], Type.I64)) }
        assertTrue(inst is PtrToInt)
    }

    @Test
    fun `IntToPtr instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.OpaquePointer,
        ) { p -> ret(inttoptr(p[0], Type.OpaquePointer)) }
        assertTrue(inst is IntToPtr)
    }

    @Test
    fun `BitCast instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F32,
        ) { p -> ret(bitcast(p[0], Type.F32)) }
        assertTrue(inst is BitCast)
    }

    @Test
    fun `AddrSpaceCast instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("ptr", Type.Pointer(Type.I32, 0))),
            returnType = Type.Pointer(Type.I32, 1),
        ) { p -> ret(addrspacecast(p[0], Type.Pointer(Type.I32, 1))) }
        assertTrue(inst is AddrSpaceCast)
    }

    // --- Control flow ---

    @Test
    fun `Ret void instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Ret)
        assertNull((inst as Ret).value)
    }

    @Test
    fun `Ret with value`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(i32(42))
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Ret)
        assertNotNull((inst as Ret).value)
    }

    @Test
    fun `Br instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            br(BlockRef("exit"))
            appendBlock("exit")
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Br)
        assertEquals(BlockRef("exit"), (inst as Br).target)
    }

    @Test
    fun `CondBr instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, p[0], i32(0))
            condBr(cmp, BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(i32(1))
            appendBlock("else")
            ret(i32(0))
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions.last()
        assertTrue(inst is CondBr)
    }

    @Test
    fun `Switch instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(p[0], BlockRef("default"), listOf(i32(0) to BlockRef("c0"), i32(1) to BlockRef("c1")))
            appendBlock("c0"); ret(i32(10))
            appendBlock("c1"); ret(i32(20))
            appendBlock("default"); ret(i32(-1))
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Switch)
        assertEquals(2, (inst as Switch).cases.size)
    }

    @Test
    fun `IndirectBr instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("addr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            indirectBr(p[0], listOf(BlockRef("dest1"), BlockRef("dest2")))
            appendBlock("dest1"); ret()
            appendBlock("dest2"); ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is IndirectBr)
        assertEquals(2, (inst as IndirectBr).targets.size)
    }

    @Test
    fun `Unreachable instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            unreachable()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Unreachable)
    }

    @Test
    fun `Trap instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            trap()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Trap)
    }

    @Test
    fun `DebugTrap instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            debugTrap()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is DebugTrap)
    }

    // --- Calls ---

    @Test
    fun `Call with return value`() {
        val mod = buildModule {
            declareFunction("getVal", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call("getVal", emptyList(), Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[1].blocks[0].instructions[0] is Call)
    }

    @Test
    fun `Call void function`() {
        val mod = buildModule {
            declareFunction("doStuff", emptyList(), Type.Void)
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            call("doStuff", emptyList(), Type.Void)
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[1].blocks[0].instructions[0] as Call
        assertNull(inst.result)
    }

    @Test
    fun `Call with FunctionRef`() {
        val mod = buildModule {
            val ref = declareFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = call(ref, listOf(i32(1), i32(2)), Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[1].blocks[0].instructions[0] is Call)
    }

    @Test
    fun `Invoke instruction`() {
        val mod = buildModule {
            val ref = declareFunction("mayThrow", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = invoke(ref, emptyList(), Type.I32, BlockRef("normal"), BlockRef("unwind"))
            appendBlock("normal")
            ret(i32(0))
            appendBlock("unwind")
            val lp = landingPad(Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)), emptyList(), cleanup = true)
            ret(i32(-1))
            finalizeFunction()
        }
        val inst = mod.functions[1].blocks[0].instructions[0]
        assertTrue(inst is Invoke)
    }

    @Test
    fun `CallBr instruction`() {
        val mod = buildModule {
            val ref = declareFunction("asmCall", emptyList(), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            callBr(ref, emptyList(), Type.I32, "fallthrough", listOf("alt"))
            appendBlock("fallthrough"); ret(i32(0))
            appendBlock("alt"); ret(i32(1))
            finalizeFunction()
        }
        assertTrue(mod.functions[1].blocks[0].instructions[0] is CallBr)
    }

    // --- Varargs ---

    @Test
    fun `VAStart instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("n", Type.I32)), Type.Void, isVarArg = true)
            appendBlock("entry")
            val ap = alloca(Type.OpaquePointer)
            vaStart(ap)
            vaEnd(ap)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is VAStart)
    }

    @Test
    fun `VAEnd instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("n", Type.I32)), Type.Void, isVarArg = true)
            appendBlock("entry")
            val ap = alloca(Type.OpaquePointer)
            vaStart(ap)
            vaEnd(ap)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[2] is VAEnd)
    }

    @Test
    fun `VACopy instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("n", Type.I32)), Type.Void, isVarArg = true)
            appendBlock("entry")
            val ap = alloca(Type.OpaquePointer)
            val ap2 = alloca(Type.OpaquePointer)
            vaStart(ap)
            vaCopy(ap2, ap)
            vaEnd(ap)
            vaEnd(ap2)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[3] is VACopy)
    }

    @Test
    fun `VAArg instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("n", Type.I32)), Type.I32, isVarArg = true)
            appendBlock("entry")
            val ap = alloca(Type.OpaquePointer)
            vaStart(ap)
            val v = vaArg(ap, Type.I32)
            vaEnd(ap)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[2] is VAArg)
    }

    // --- Exception handling (native) ---

    @Test
    fun `LandingPad instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val lp = landingPad(
                Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)),
                listOf(LandingPadClause.Catch(Constant.NullPtr)),
                cleanup = true,
            )
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is LandingPad)
        assertTrue((inst as LandingPad).cleanup)
    }

    @Test
    fun `Resume instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val lp = landingPad(
                Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)),
                emptyList(), cleanup = true,
            )
            resume(lp)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is Resume)
    }

    @Test
    fun `CatchSwitch instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val cs = catchSwitch(null, listOf("handler1"), unwindDest = null)
            ret()
            appendBlock("handler1")
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CatchSwitch)
    }

    @Test
    fun `CatchPad instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val cs = catchSwitch(null, listOf("handler"), null)
            appendBlock("handler")
            val cp = catchPad(cs, emptyList())
            catchRet(cp, "done")
            appendBlock("done")
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[1].instructions[0] is CatchPad)
    }

    @Test
    fun `CleanupPad instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val cp = cleanupPad(null, emptyList())
            cleanupRet(cp, null)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CleanupPad)
    }

    @Test
    fun `CatchRet instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val cs = catchSwitch(null, listOf("handler"), null)
            appendBlock("handler")
            val cp = catchPad(cs, emptyList())
            catchRet(cp, "done")
            appendBlock("done")
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[1].instructions[1] is CatchRet)
    }

    @Test
    fun `CleanupRet instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val cp = cleanupPad(null, emptyList())
            cleanupRet(cp, null)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is CleanupRet)
    }

    // --- SSA ---

    @Test
    fun `Phi instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, p[0], i32(0))
            condBr(cmp, BlockRef("then"), BlockRef("else"))
            appendBlock("then"); br(BlockRef("merge"))
            appendBlock("else"); br(BlockRef("merge"))
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(i32(1) to BlockRef("then"), i32(0) to BlockRef("else")))
            ret(phi)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[3].instructions[0] is Phi)
    }

    @Test
    fun `Select instruction`() {
        val inst = buildSingleInstr { p ->
            val cmp = icmp(ICmpPredicate.SGT, p[0], p[1])
            ret(select(cmp, p[0], p[1]))
        }
        assertTrue(inst is ICmp)
        // select is the second instruction
    }

    @Test
    fun `Select instruction directly`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, p[0], p[1])
            val s = select(cmp, p[0], p[1])
            ret(s)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is Select)
    }

    @Test
    fun `Freeze instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
        ) { p ->
            val f = freeze(p[0])
            ret(f)
        }
        assertTrue(inst is Freeze)
    }

    // --- Vector operations ---

    @Test
    fun `ExtractElement instruction`() {
        val vecType = Type.Vector(Type.I32, 4)
        val inst = buildSingleInstr(
            params = listOf(Param("v", vecType)),
            returnType = Type.I32,
        ) { p ->
            val e = extractElement(p[0], i32(0))
            ret(e)
        }
        assertTrue(inst is ExtractElement)
    }

    @Test
    fun `InsertElement instruction`() {
        val vecType = Type.Vector(Type.I32, 4)
        val inst = buildSingleInstr(
            params = listOf(Param("v", vecType)),
            returnType = vecType,
        ) { p ->
            val r = insertElement(p[0], i32(42), i32(0))
            ret(r)
        }
        assertTrue(inst is InsertElement)
    }

    @Test
    fun `ShuffleVector instruction`() {
        val vecType = Type.Vector(Type.I32, 4)
        val inst = buildSingleInstr(
            params = listOf(Param("v1", vecType), Param("v2", vecType)),
            returnType = Type.Vector(Type.I32, 4),
        ) { p ->
            val r = shuffleVector(p[0], p[1], listOf(0, 2, 4, 6))
            ret(r)
        }
        assertTrue(inst is ShuffleVector)
    }

    @Test
    fun `Splat instruction`() {
        val vecType = Type.Vector(Type.I32, 4)
        val inst = buildSingleInstr(
            params = listOf(Param("s", Type.I32)),
            returnType = vecType,
        ) { p ->
            val r = splat(p[0], vecType)
            ret(r)
        }
        assertTrue(inst is Splat)
    }

    @Test
    fun `VectorReduce ADD`() {
        val vecType = Type.Vector(Type.I32, 4)
        val inst = buildSingleInstr(
            params = listOf(Param("v", vecType)),
            returnType = Type.I32,
        ) { p ->
            val r = vectorReduce(VectorReduceOp.ADD, p[0])
            ret(r)
        }
        assertTrue(inst is VectorReduce)
        assertEquals(VectorReduceOp.ADD, (inst as VectorReduce).op)
    }

    // --- Aggregate operations ---

    @Test
    fun `ExtractValue instruction`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val inst = buildSingleInstr(
            params = listOf(Param("s", structType)),
            returnType = Type.I32,
        ) { p ->
            val r = extractValue(p[0], 0)
            ret(r)
        }
        assertTrue(inst is ExtractValue)
    }

    @Test
    fun `InsertValue instruction`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val inst = buildSingleInstr(
            params = listOf(Param("s", structType)),
            returnType = structType,
        ) { p ->
            val r = insertValue(p[0], i32(42), 0)
            ret(r)
        }
        assertTrue(inst is InsertValue)
    }

    // --- High-level: Object lifecycle ---

    @Test
    fun `NewObject instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.ClassRef("Foo"))
            appendBlock("entry")
            val obj = newObject("Foo")
            ret(obj)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is NewObject)
    }

    @Test
    fun `NewArray instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Array(Type.I32, 0))
            appendBlock("entry")
            val arr = newArray(Type.I32, i32(10))
            ret(arr)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is NewArray)
    }

    @Test
    fun `NewMultiArray instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Array(Type.I32, 0))
            appendBlock("entry")
            val arr = newMultiArray(Type.I32, listOf(i32(3), i32(4)))
            ret(arr)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is NewMultiArray)
    }

    // --- High-level: Field access ---

    @Test
    fun `GetField instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Point"))), Type.I32)
            appendBlock("entry")
            val v = getField(p[0], "Point", "x", Type.I32)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GetField)
    }

    @Test
    fun `PutField instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Point")), Param("v", Type.I32)), Type.Void)
            appendBlock("entry")
            putField(p[0], "Point", "x", Type.I32, p[1])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is PutField)
    }

    @Test
    fun `GetStatic instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val v = getStatic("Counter", "count", Type.I32)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GetStatic)
    }

    @Test
    fun `PutStatic instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            putStatic("Counter", "count", Type.I32, i32(0))
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is PutStatic)
    }

    // --- High-level: Method dispatch ---

    @Test
    fun `VirtualCall instruction`() {
        val methodType = Type.Function(listOf(Type.I32), Type.I32)
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.I32)
            appendBlock("entry")
            val r = virtualCall(p[0], "Obj", "getValue", methodType, listOf(i32(0)))
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is VirtualCall)
    }

    @Test
    fun `InterfaceCall instruction`() {
        val methodType = Type.Function(emptyList(), Type.I32)
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.I32)
            appendBlock("entry")
            val r = interfaceCall(p[0], "Comparable", "compareTo", methodType, emptyList())
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InterfaceCall)
    }

    @Test
    fun `SpecialCall instruction`() {
        val methodType = Type.Function(emptyList(), Type.I32)
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.I32)
            appendBlock("entry")
            val r = specialCall(p[0], "Obj", "privateMethod", methodType, emptyList())
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is SpecialCall)
    }

    @Test
    fun `StaticCall instruction`() {
        val methodType = Type.Function(listOf(Type.I32), Type.I32)
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = staticCall("Math", "abs", methodType, listOf(i32(-5)))
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is StaticCall)
    }

    @Test
    fun `DynamicCall instruction`() {
        val methodType = Type.Function(listOf(Type.I32), Type.I32)
        val bootstrap = BootstrapMethod(
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            Type.Function(emptyList(), Type.OpaquePointer),
        )
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = dynamicCall(bootstrap, "apply", methodType, listOf(i32(1)))
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is DynamicCall)
    }

    @Test
    fun `ConstructorCall instruction`() {
        val ctorType = Type.Function(listOf(Type.I32), Type.Void)
        val mod = buildModule {
            createFunction("f", emptyList(), Type.ClassRef("Foo"))
            appendBlock("entry")
            val obj = newObject("Foo")
            constructorCall(obj, "Foo", ctorType, listOf(i32(42)))
            ret(obj)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is ConstructorCall)
    }

    // --- High-level: Type operations ---

    @Test
    fun `InstanceOf instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Object"))), Type.I32)
            appendBlock("entry")
            val r = instanceOf(p[0], Type.ClassRef("String"))
            ret(select(r, i32(1), i32(0)))
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InstanceOf)
    }

    @Test
    fun `CheckCast instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Object"))), Type.ClassRef("String"))
            appendBlock("entry")
            val r = checkCast(p[0], Type.ClassRef("String"))
            ret(r)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CheckCast)
    }

    @Test
    fun `TypeId instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Object"))), Type.I32)
            appendBlock("entry")
            val r = typeId(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is TypeId)
    }

    // --- High-level: Managed arrays ---

    @Test
    fun `ArrayGet instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("arr", Type.Array(Type.I32, 0))), Type.I32)
            appendBlock("entry")
            val v = arrayGet(p[0], i32(0), Type.I32)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ArrayGet)
    }

    @Test
    fun `ArraySet instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("arr", Type.Array(Type.I32, 0))), Type.Void)
            appendBlock("entry")
            arraySet(p[0], i32(0), i32(42), Type.I32)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ArraySet)
    }

    @Test
    fun `ArrayLength instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("arr", Type.Array(Type.I32, 0))), Type.I32)
            appendBlock("entry")
            val len = arrayLength(p[0])
            ret(len)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ArrayLength)
    }

    // --- High-level: Monitor ---

    @Test
    fun `MonitorEnter instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Object"))), Type.Void)
            appendBlock("entry")
            monitorEnter(p[0])
            monitorExit(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is MonitorEnter)
    }

    @Test
    fun `MonitorExit instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Object"))), Type.Void)
            appendBlock("entry")
            monitorEnter(p[0])
            monitorExit(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is MonitorExit)
    }

    // --- High-level: Managed exceptions ---

    @Test
    fun `Throw instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("ex", Type.ClassRef("Exception"))), Type.Void)
            appendBlock("entry")
            throwException(p[0])
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Throw)
    }

    @Test
    fun `TryCatchRegion instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            tryCatch("tryBlock", listOf(CatchHandler(Type.ClassRef("Exception"), "catchBlock")), "finallyBlock")
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is TryCatchRegion)
    }

    // --- High-level: Boxing ---

    @Test
    fun `Box instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("v", Type.I32)), Type.ClassRef("Integer"))
            appendBlock("entry")
            val boxed = box(p[0], Type.ClassRef("Integer"))
            ret(boxed)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Box)
    }

    @Test
    fun `Unbox instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Integer"))), Type.I32)
            appendBlock("entry")
            val v = unbox(p[0], Type.I32)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Unbox)
    }

    // --- High-level: Closures ---

    @Test
    fun `ClosureCreate instruction`() {
        val closureType = Type.Function(listOf(Type.I32), Type.I32)
        val mod = buildModule {
            val fnRef = declareFunction("impl", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", listOf(Param("captured", Type.I32)), closureType)
            appendBlock("entry")
            val c = closureCreate(fnRef, listOf(param(0)), closureType)
            ret(c)
            finalizeFunction()
        }
        assertTrue(mod.functions[1].blocks[0].instructions[0] is ClosureCreate)
    }

    @Test
    fun `ClosureInvoke instruction`() {
        val closureType = Type.Function(listOf(Type.I32), Type.I32)
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("c", closureType)), Type.I32)
            appendBlock("entry")
            val r = closureInvoke(p[0], listOf(i32(5)), Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ClosureInvoke)
    }

    // --- High-level: Tagged unions ---

    @Test
    fun `ConstructVariant instruction`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = buildModule {
            createFunction("f", emptyList(), unionType)
            appendBlock("entry")
            val v = constructVariant(unionType, "Circle", listOf(f64(3.14)))
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ConstructVariant)
    }

    @Test
    fun `GetTag instruction`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("s", unionType)), Type.I32)
            appendBlock("entry")
            val tag = getTag(p[0])
            ret(tag)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GetTag)
    }

    @Test
    fun `GetVariantField instruction`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("s", unionType)), Type.F64)
            appendBlock("entry")
            val v = getVariantField(p[0], "Circle", 0, Type.F64)
            ret(v)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GetVariantField)
    }

    @Test
    fun `TagSwitch instruction`() {
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("s", unionType)), Type.F64)
            appendBlock("entry")
            tagSwitch(p[0], listOf("Circle" to BlockRef("handleCircle"), "Rect" to BlockRef("handleRect")))
            appendBlock("handleCircle")
            ret(f64(1.0))
            appendBlock("handleRect")
            ret(f64(2.0))
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is TagSwitch)
    }

    // --- High-level: GC ---

    @Test
    fun `GCAlloc instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Reference(Type.ClassRef("Obj")))
            appendBlock("entry")
            val r = gcAlloc(Type.ClassRef("Obj"))
            ret(r)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GCAlloc)
    }

    @Test
    fun `GCSafepoint instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            gcSafepoint()
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is GCSafepoint)
    }

    @Test
    fun `GCRoot instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val ptr = alloca(Type.OpaquePointer)
            gcRoot(ptr)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is GCRoot)
    }

    // --- High-level: Pinning and interior pointers ---

    @Test
    fun `Pin instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("ref", Type.Reference(Type.I32))), Type.PinnedRef(Type.I32))
            appendBlock("entry")
            val pinned = pin(p[0])
            ret(pinned)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Pin)
    }

    @Test
    fun `Unpin instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("ref", Type.Reference(Type.I32))), Type.Void)
            appendBlock("entry")
            val pinned = pin(p[0])
            unpin(pinned)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is Unpin)
    }

    @Test
    fun `InteriorPtr instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("ref", Type.Reference(Type.I32))), Type.InteriorRef(Type.I32))
            appendBlock("entry")
            val ip = interiorPtr(p[0], i32(0), Type.I32)
            ret(ip)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InteriorPtr)
    }

    @Test
    fun `WriteBarrier instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(
                Param("obj", Type.ClassRef("Obj")),
                Param("val", Type.ClassRef("Obj")),
            ), Type.Void)
            appendBlock("entry")
            writeBarrier(p[0], i32(0), p[1])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is WriteBarrier)
    }

    @Test
    fun `ReadBarrier instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("ref", Type.Reference(Type.I32))), Type.Reference(Type.I32))
            appendBlock("entry")
            val r = readBarrier(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ReadBarrier)
    }

    @Test
    fun `ManagedCall MANAGED_TO_NATIVE`() {
        val mod = buildModule {
            val nativeRef = declareFunction("nativeFunc", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = managedCall(nativeRef, listOf(i32(1)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
            ret(r!!)
            finalizeFunction()
        }
        val inst = mod.functions[1].blocks[0].instructions[0]
        assertTrue(inst is ManagedCall)
        assertEquals(ManagedCallDirection.MANAGED_TO_NATIVE, (inst as ManagedCall).direction)
    }

    @Test
    fun `ManagedCall NATIVE_TO_MANAGED`() {
        val mod = buildModule {
            val managedRef = declareFunction("managedFunc", emptyList(), Type.Void)
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            managedCall(managedRef, emptyList(), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[1].blocks[0].instructions[0]
        assertEquals(ManagedCallDirection.NATIVE_TO_MANAGED, (inst as ManagedCall).direction)
    }

    // --- High-level: Reference counting ---

    @Test
    fun `RefRetain instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.Void)
            appendBlock("entry")
            refRetain(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is RefRetain)
    }

    @Test
    fun `RefRelease instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.Void)
            appendBlock("entry")
            refRelease(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is RefRelease)
    }

    @Test
    fun `RefCount instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("obj", Type.ClassRef("Obj"))), Type.I32)
            appendBlock("entry")
            val c = refCount(p[0])
            ret(c)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is RefCount)
    }

    // --- High-level: Coroutines ---

    @Test
    fun `CoroBegin instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.OpaquePointer)
            appendBlock("entry")
            val h = coroBegin(i32(0), Constant.NullPtr)
            ret(h)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroBegin)
    }

    @Test
    fun `CoroEnd instruction`() {
        val mod = buildModule {
            createFunction("f", listOf(Param("h", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            coroEnd(param(0))
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroEnd)
    }

    @Test
    fun `CoroSuspend instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I8)
            appendBlock("entry")
            val r = coroSuspend()
            ret(r)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroSuspend)
    }

    @Test
    fun `CoroResume instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("h", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            coroResume(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroResume)
    }

    @Test
    fun `CoroDestroy instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("h", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            coroDestroy(p[0])
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroDestroy)
    }

    @Test
    fun `CoroSize instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val s = coroSize()
            ret(s)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is CoroSize)
    }

    // --- Intrinsic / Inline Assembly ---

    @Test
    fun `Intrinsic instruction with return`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = intrinsic("llvm.bswap.i32", listOf(i32(0x12345678)), Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Intrinsic)
    }

    @Test
    fun `Intrinsic instruction void`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            intrinsic("llvm.debugtrap", emptyList(), Type.Void)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Intrinsic)
    }

    @Test
    fun `InlineAsm instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val r = inlineAsm("mov \$0, 42", "=r", returnType = Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InlineAsm)
    }

    @Test
    fun `InlineAsm void`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            inlineAsm("nop", "")
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InlineAsm)
    }

    @Test
    fun `InlineAsm with Intel dialect`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            inlineAsm("nop", "", dialect = AsmDialect.INTEL)
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0] as InlineAsm
        assertEquals(AsmDialect.INTEL, inst.dialect)
    }

    // --- Debug / metadata ---

    @Test
    fun `DebugLoc instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            debugLoc(10, 5, "main.kt")
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0] as DebugLoc
        assertEquals(10, inst.line)
        assertEquals(5, inst.col)
        assertEquals("main.kt", inst.scope)
    }

    @Test
    fun `DebugLoc with inlinedAt`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            debugLoc(10, 5, "main.kt", inlinedAt = "caller.kt:20")
            ret()
            finalizeFunction()
        }
        assertEquals("caller.kt:20", (mod.functions[0].blocks[0].instructions[0] as DebugLoc).inlinedAt)
    }

    @Test
    fun `DebugValue instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.Void)
            appendBlock("entry")
            debugValue("x", p[0])
            ret()
            finalizeFunction()
        }
        val inst = mod.functions[0].blocks[0].instructions[0] as DebugValue
        assertEquals("x", inst.variable)
    }

    @Test
    fun `DebugDeclare instruction`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            debugDeclare("myVar", ptr)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is DebugDeclare)
    }

    // --- Optimizer hints ---

    @Test
    fun `Assume instruction`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.Void)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, p[0], i32(0))
            assume(cond)
            ret()
            finalizeFunction()
        }
        assertTrue(mod.functions[0].blocks[0].instructions[1] is Assume)
    }

    @Test
    fun `Expect instruction`() {
        val inst = buildSingleInstr(
            params = listOf(Param("x", Type.I32)),
        ) { p ->
            val r = expect(p[0], i32(1))
            ret(r)
        }
        assertTrue(inst is Expect)
    }

    // --- Verifier integration: complex function verifies cleanly ---

    @Test
    fun `verifier passes for complete function with arithmetic`() {
        val mod = buildModule {
            val p = createFunction("compute", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(p[0], p[1])
            val diff = sub(p[0], p[1])
            val prod = mul(sum, diff)
            ret(prod)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for control flow with phi`() {
        val mod = buildModule {
            val p = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGE, p[0], i32(0))
            condBr(cmp, BlockRef("pos"), BlockRef("neg"))
            appendBlock("pos")
            br(BlockRef("merge"))
            appendBlock("neg")
            val negated = neg(p[0])
            br(BlockRef("merge"))
            appendBlock("merge")
            val result = phi(Type.I32, listOf(p[0] to BlockRef("pos"), negated to BlockRef("neg")))
            ret(result)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for memory operations`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(p[0], ptr)
            val loaded = load(Type.I32, ptr)
            val doubled = add(loaded, loaded)
            store(doubled, ptr)
            val final_ = load(Type.I32, ptr)
            ret(final_)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for float operations`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val sum = fadd(p[0], p[1])
            val sq = fmul(sum, sum)
            val root = sqrt(sq)
            ret(root)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for conversion chain`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.F64)
            appendBlock("entry")
            val extended = sext(p[0], Type.I64)
            val fp = sitofp(extended, Type.F64)
            ret(fp)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for switch with multiple cases`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(p[0], "default", listOf(
                i32(0) to "c0",
                i32(1) to "c1",
                i32(2) to "c2",
            ))
            appendBlock("c0"); ret(i32(100))
            appendBlock("c1"); ret(i32(200))
            appendBlock("c2"); ret(i32(300))
            appendBlock("default"); ret(i32(-1))
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for select instruction`() {
        val mod = buildModule {
            val p = createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.SGT, p[0], p[1])
            val result = select(cmp, p[0], p[1])
            ret(result)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for bitwise operations`() {
        val mod = buildModule {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val a = and(p[0], p[1])
            val o = or(a, p[0])
            val x = xor(o, p[1])
            val n = not(x)
            val sl = shl(n, i32(2))
            val sr = lshr(sl, i32(1))
            val ar = ashr(sr, i32(1))
            ret(ar)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for call instruction`() {
        val mod = buildModule {
            declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
            val p = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = call("external_fn", listOf(p[0]), Type.I32)
            ret(r!!)
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }

    @Test
    fun `verifier passes for void return`() {
        val mod = buildModule {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val result = IrVerifier.verify(mod)
        assertTrue(result.isValid, "Verification failed: $result")
    }
}

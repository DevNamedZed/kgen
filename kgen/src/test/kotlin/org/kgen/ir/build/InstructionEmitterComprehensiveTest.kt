package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InstructionEmitterComprehensiveTest {

    private fun builder(): ModuleBuilder {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        return ir
    }

    private fun builderWithParams(vararg params: Param): Pair<ModuleBuilder, List<Parameter>> {
        val ir = ModuleBuilder("test", Target.wasm())
        val p = ir.createFunction("f", params.toList(), Type.Void)
        ir.appendBlock("entry")
        return ir to p
    }

    private fun i32Vals(): Pair<Value, Value> = Type.i32(10) to Type.i32(20)
    private fun i64Vals(): Pair<Value, Value> = Type.i64(100) to Type.i64(200)
    private fun f32Vals(): Pair<Value, Value> = Type.f32(1.5f) to Type.f32(2.5f)
    private fun f64Vals(): Pair<Value, Value> = Type.f64(3.14) to Type.f64(2.71)

    @Test
    fun `add returns i32 value`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.add(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `add with nuw flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.add(a, b, nuw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `add with nsw flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.add(a, b, nsw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `add with both nuw and nsw`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.add(a, b, nuw = true, nsw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `sub returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.sub(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `sub with nuw flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.sub(a, b, nuw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `sub with nsw flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.sub(a, b, nsw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `mul returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.mul(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `mul with nuw and nsw`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.mul(a, b, nuw = true, nsw = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `udiv returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.udiv(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `udiv with exact flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.udiv(a, b, exact = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `sdiv returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.sdiv(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `sdiv with exact flag`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.sdiv(a, b, exact = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `urem returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.urem(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `srem returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.srem(a, b)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `neg returns correct type`() {
        val ir = builder()
        val result = ir.neg(Type.i32(42))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `arithmetic on i64 values`() {
        val ir = builder()
        val (a, b) = i64Vals()
        assertEquals(Type.I64, ir.add(a, b).type)
        assertEquals(Type.I64, ir.sub(a, b).type)
        assertEquals(Type.I64, ir.mul(a, b).type)
        assertEquals(Type.I64, ir.sdiv(a, b).type)
        assertEquals(Type.I64, ir.udiv(a, b).type)
        assertEquals(Type.I64, ir.srem(a, b).type)
        assertEquals(Type.I64, ir.urem(a, b).type)
    }

    @Test
    fun `saddOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.saddOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(2, structType.fields.size)
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `uaddOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.uaddOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `ssubOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.ssubOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `usubOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.usubOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
    }

    @Test
    fun `smulOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.smulOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `umulOverflow returns struct of value and i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        val result = ir.umulOverflow(a, b)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `saddSat returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.saddSat(a, b).type)
    }

    @Test
    fun `uaddSat returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.uaddSat(a, b).type)
    }

    @Test
    fun `ssubSat returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.ssubSat(a, b).type)
    }

    @Test
    fun `usubSat returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.usubSat(a, b).type)
    }

    @Test
    fun `smin returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.smin(a, b).type)
    }

    @Test
    fun `smax returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.smax(a, b).type)
    }

    @Test
    fun `umin returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.umin(a, b).type)
    }

    @Test
    fun `umax returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.umax(a, b).type)
    }

    @Test
    fun `abs returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.abs(Type.i32(-5)).type)
    }

    @Test
    fun `abs with isIntMin flag`() {
        val ir = builder()
        assertEquals(Type.I32, ir.abs(Type.i32(-5), isIntMin = true).type)
    }

    @Test
    fun `fadd returns f32 type`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.F32, ir.fadd(a, b).type)
    }

    @Test
    fun `fadd with fast math flags`() {
        val ir = builder()
        val (a, b) = f32Vals()
        val fm = FastMathFlags(noNaNs = true, noInfs = true)
        assertEquals(Type.F32, ir.fadd(a, b, fm).type)
    }

    @Test
    fun `fsub returns f64 type`() {
        val ir = builder()
        val (a, b) = f64Vals()
        assertEquals(Type.F64, ir.fsub(a, b).type)
    }

    @Test
    fun `fsub with fast math flags`() {
        val ir = builder()
        val (a, b) = f64Vals()
        val fm = FastMathFlags(reassoc = true)
        assertEquals(Type.F64, ir.fsub(a, b, fm).type)
    }

    @Test
    fun `fmul returns correct type`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.F32, ir.fmul(a, b).type)
    }

    @Test
    fun `fdiv returns correct type`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.F32, ir.fdiv(a, b).type)
    }

    @Test
    fun `frem returns correct type`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.F32, ir.frem(a, b).type)
    }

    @Test
    fun `fneg returns correct type`() {
        val ir = builder()
        assertEquals(Type.F32, ir.fneg(Type.f32(3.14f)).type)
    }

    @Test
    fun `fneg with fast math flags`() {
        val ir = builder()
        val fm = FastMathFlags(noSignedZeros = true)
        assertEquals(Type.F32, ir.fneg(Type.f32(3.14f), fm).type)
    }

    @Test
    fun `fabs returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.fabs(Type.f64(3.14)).type)
    }

    @Test
    fun `fma returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.fma(Type.f64(1.0), Type.f64(2.0), Type.f64(3.0)).type)
    }

    @Test
    fun `fmin returns correct type`() {
        val ir = builder()
        val (a, b) = f64Vals()
        assertEquals(Type.F64, ir.fmin(a, b).type)
    }

    @Test
    fun `fmax returns correct type`() {
        val ir = builder()
        val (a, b) = f64Vals()
        assertEquals(Type.F64, ir.fmax(a, b).type)
    }

    @Test
    fun `sqrt returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.sqrt(Type.f64(4.0)).type)
    }

    @Test
    fun `ceil returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.ceil(Type.f64(3.2)).type)
    }

    @Test
    fun `floor returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.floor(Type.f64(3.8)).type)
    }

    @Test
    fun `round returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.round(Type.f64(3.5)).type)
    }

    @Test
    fun `ftrunc returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.ftrunc(Type.f64(3.9)).type)
    }

    @Test
    fun `copySign returns correct type`() {
        val ir = builder()
        assertEquals(Type.F64, ir.copySign(Type.f64(3.0), Type.f64(-1.0)).type)
    }

    @Test
    fun `and returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.and(a, b).type)
    }

    @Test
    fun `or returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.or(a, b).type)
    }

    @Test
    fun `xor returns correct type`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I32, ir.xor(a, b).type)
    }

    @Test
    fun `not returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.not(Type.i32(0xFF)).type)
    }

    @Test
    fun `shl returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.shl(Type.i32(1), Type.i32(4)).type)
    }

    @Test
    fun `shl with nuw and nsw`() {
        val ir = builder()
        assertEquals(Type.I32, ir.shl(Type.i32(1), Type.i32(4), nuw = true, nsw = true).type)
    }

    @Test
    fun `lshr returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.lshr(Type.i32(16), Type.i32(2)).type)
    }

    @Test
    fun `lshr with exact flag`() {
        val ir = builder()
        assertEquals(Type.I32, ir.lshr(Type.i32(16), Type.i32(2), exact = true).type)
    }

    @Test
    fun `ashr returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.ashr(Type.i32(-16), Type.i32(2)).type)
    }

    @Test
    fun `ashr with exact flag`() {
        val ir = builder()
        assertEquals(Type.I32, ir.ashr(Type.i32(-16), Type.i32(2), exact = true).type)
    }

    @Test
    fun `rotateLeft returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.rotateLeft(Type.i32(1), Type.i32(3)).type)
    }

    @Test
    fun `rotateRight returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.rotateRight(Type.i32(1), Type.i32(3)).type)
    }

    @Test
    fun `ctlz returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.ctlz(Type.i32(8)).type)
    }

    @Test
    fun `ctlz with isZeroPoison`() {
        val ir = builder()
        assertEquals(Type.I32, ir.ctlz(Type.i32(8), isZeroPoison = true).type)
    }

    @Test
    fun `cttz returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.cttz(Type.i32(8)).type)
    }

    @Test
    fun `cttz with isZeroPoison`() {
        val ir = builder()
        assertEquals(Type.I32, ir.cttz(Type.i32(8), isZeroPoison = true).type)
    }

    @Test
    fun `ctpop returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.ctpop(Type.i32(0xFF)).type)
    }

    @Test
    fun `bswap returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.bswap(Type.i32(0x12345678)).type)
    }

    @Test
    fun `bitReverse returns correct type`() {
        val ir = builder()
        assertEquals(Type.I32, ir.bitReverse(Type.i32(0xFF)).type)
    }

    @Test
    fun `icmp EQ returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.EQ, a, b).type)
    }

    @Test
    fun `icmp NE returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.NE, a, b).type)
    }

    @Test
    fun `icmp UGT returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.UGT, a, b).type)
    }

    @Test
    fun `icmp UGE returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.UGE, a, b).type)
    }

    @Test
    fun `icmp ULT returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.ULT, a, b).type)
    }

    @Test
    fun `icmp ULE returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.ULE, a, b).type)
    }

    @Test
    fun `icmp SGT returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.SGT, a, b).type)
    }

    @Test
    fun `icmp SGE returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.SGE, a, b).type)
    }

    @Test
    fun `icmp SLT returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.SLT, a, b).type)
    }

    @Test
    fun `icmp SLE returns i1`() {
        val ir = builder()
        val (a, b) = i32Vals()
        assertEquals(Type.I1, ir.icmp(ICmpPredicate.SLE, a, b).type)
    }

    @Test
    fun `fcmp OEQ returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OEQ, a, b).type)
    }

    @Test
    fun `fcmp OGT returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OGT, a, b).type)
    }

    @Test
    fun `fcmp OGE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OGE, a, b).type)
    }

    @Test
    fun `fcmp OLT returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OLT, a, b).type)
    }

    @Test
    fun `fcmp OLE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OLE, a, b).type)
    }

    @Test
    fun `fcmp ONE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.ONE, a, b).type)
    }

    @Test
    fun `fcmp ORD returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.ORD, a, b).type)
    }

    @Test
    fun `fcmp UEQ returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.UEQ, a, b).type)
    }

    @Test
    fun `fcmp UGT returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.UGT, a, b).type)
    }

    @Test
    fun `fcmp UGE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.UGE, a, b).type)
    }

    @Test
    fun `fcmp ULT returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.ULT, a, b).type)
    }

    @Test
    fun `fcmp ULE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.ULE, a, b).type)
    }

    @Test
    fun `fcmp UNE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.UNE, a, b).type)
    }

    @Test
    fun `fcmp UNO returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.UNO, a, b).type)
    }

    @Test
    fun `fcmp FALSE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.FALSE, a, b).type)
    }

    @Test
    fun `fcmp TRUE returns i1`() {
        val ir = builder()
        val (a, b) = f32Vals()
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.TRUE, a, b).type)
    }

    @Test
    fun `fcmp with fast math flags`() {
        val ir = builder()
        val (a, b) = f32Vals()
        val fm = FastMathFlags(noNaNs = true)
        assertEquals(Type.I1, ir.fcmp(FCmpPredicate.OEQ, a, b, fm).type)
    }

    @Test
    fun `alloca returns pointer type`() {
        val ir = builder()
        val result = ir.alloca(Type.I32)
        assertEquals(Type.Pointer(Type.I32), result.type)
    }

    @Test
    fun `alloca with alignment`() {
        val ir = builder()
        val result = ir.alloca(Type.I64, align = 8)
        assertEquals(Type.Pointer(Type.I64), result.type)
    }

    @Test
    fun `alloca with numElements`() {
        val ir = builder()
        val result = ir.alloca(Type.I32, numElements = Type.i32(10))
        assertEquals(Type.Pointer(Type.I32), result.type)
    }

    @Test
    fun `load returns loaded type`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.load(Type.I32, ptr)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `load with alignment`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I64)
        val result = ir.load(Type.I64, ptr, align = 8)
        assertEquals(Type.I64, result.type)
    }

    @Test
    fun `load volatile`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.load(Type.I32, ptr, volatile = true)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `load with atomic ordering`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.load(Type.I32, ptr, ordering = AtomicOrdering.SEQ_CST)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `store emits without returning value`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.store(Type.i32(42), ptr)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is Store })
    }

    @Test
    fun `store with alignment and volatile`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.store(Type.i32(42), ptr, align = 4, volatile = true)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val store = mod.functions[0].blocks[0].instructions.filterIsInstance<Store>().single()
        assertEquals(4, store.align)
        assertTrue(store.volatile)
    }

    @Test
    fun `store with atomic ordering`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.store(Type.i32(42), ptr, ordering = AtomicOrdering.RELEASE)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val store = mod.functions[0].blocks[0].instructions.filterIsInstance<Store>().single()
        assertEquals(AtomicOrdering.RELEASE, store.ordering)
    }

    @Test
    fun `gep returns pointer type`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.gep(Type.I32, ptr, Type.i32(0))
        assertEquals(Type.Pointer(Type.I32), result.type)
    }

    @Test
    fun `gep with inBounds false`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.gep(Type.I32, ptr, Type.i32(0), inBounds = false)
        assertEquals(Type.Pointer(Type.I32), result.type)
    }

    @Test
    fun `fence emits instruction`() {
        val ir = builder()
        ir.fence(AtomicOrdering.SEQ_CST)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Fence })
    }

    @Test
    fun `fence with sync scope`() {
        val ir = builder()
        ir.fence(AtomicOrdering.ACQ_REL, syncScope = "singlethread")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val fence = mod.functions[0].blocks[0].instructions.filterIsInstance<Fence>().single()
        assertEquals("singlethread", fence.syncScope)
    }

    @Test
    fun `cmpxchg returns struct of value and i1`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.cmpxchg(ptr, Type.i32(0), Type.i32(1), AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE)
        val structType = result.type as Type.Struct
        assertEquals(Type.I32, structType.fields[0])
        assertEquals(Type.I1, structType.fields[1])
    }

    @Test
    fun `cmpxchg weak and volatile`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.cmpxchg(ptr, Type.i32(0), Type.i32(1), AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE, weak = true, volatile = true)
        assertNotNull(result)
    }

    @Test
    fun `atomicRMW returns value type`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.atomicRMW(AtomicRMWOp.ADD, ptr, Type.i32(1), AtomicOrdering.SEQ_CST)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `atomicRMW with all operations`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        for (op in listOf(AtomicRMWOp.XCHG, AtomicRMWOp.ADD, AtomicRMWOp.SUB, AtomicRMWOp.AND, AtomicRMWOp.OR, AtomicRMWOp.XOR, AtomicRMWOp.MAX, AtomicRMWOp.MIN)) {
            val result = ir.atomicRMW(op, ptr, Type.i32(1), AtomicOrdering.SEQ_CST)
            assertEquals(Type.I32, result.type)
        }
    }

    @Test
    fun `memcpy emits instruction`() {
        val ir = builder()
        val dst = ir.alloca(Type.I8)
        val src = ir.alloca(Type.I8)
        ir.memcpy(dst, src, Type.i32(10))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is MemCpy })
    }

    @Test
    fun `memset emits instruction`() {
        val ir = builder()
        val dst = ir.alloca(Type.I8)
        ir.memset(dst, Type.i8(0), Type.i32(10))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is MemSet })
    }

    @Test
    fun `memmove emits instruction`() {
        val ir = builder()
        val dst = ir.alloca(Type.I8)
        val src = ir.alloca(Type.I8)
        ir.memmove(dst, src, Type.i32(10))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is MemMove })
    }

    @Test
    fun `prefetch emits instruction`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I8)
        ir.prefetch(ptr, 0, 3, 1)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Prefetch })
    }

    @Test
    fun `stackSave returns opaque pointer`() {
        val ir = builder()
        assertEquals(Type.OpaquePointer, ir.stackSave().type)
    }

    @Test
    fun `stackRestore emits instruction`() {
        val ir = builder()
        val sp = ir.stackSave()
        ir.stackRestore(sp)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is StackRestore })
    }

    @Test
    fun `lifetimeStart emits instruction`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.lifetimeStart(ptr, 4)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is LifetimeStart })
    }

    @Test
    fun `lifetimeEnd emits instruction`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.lifetimeEnd(ptr, 4)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is LifetimeEnd })
    }

    @Test
    fun `trunc narrows type`() {
        val ir = builder()
        val result = ir.trunc(Type.i64(42), Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `zext widens type`() {
        val ir = builder()
        val result = ir.zext(Type.i32(42), Type.I64)
        assertEquals(Type.I64, result.type)
    }

    @Test
    fun `sext widens type`() {
        val ir = builder()
        val result = ir.sext(Type.i32(-1), Type.I64)
        assertEquals(Type.I64, result.type)
    }

    @Test
    fun `fptrunc narrows float`() {
        val ir = builder()
        val result = ir.fptrunc(Type.f64(3.14), Type.F32)
        assertEquals(Type.F32, result.type)
    }

    @Test
    fun `fpext widens float`() {
        val ir = builder()
        val result = ir.fpext(Type.f32(3.14f), Type.F64)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `fptoui converts float to unsigned int`() {
        val ir = builder()
        val result = ir.fptoui(Type.f64(42.0), Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `fptosi converts float to signed int`() {
        val ir = builder()
        val result = ir.fptosi(Type.f64(-42.0), Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `uitofp converts unsigned int to float`() {
        val ir = builder()
        val result = ir.uitofp(Type.i32(42), Type.F64)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `sitofp converts signed int to float`() {
        val ir = builder()
        val result = ir.sitofp(Type.i32(-42), Type.F64)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `ptrtoint converts pointer to int`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.ptrtoint(ptr, Type.I64)
        assertEquals(Type.I64, result.type)
    }

    @Test
    fun `inttoptr converts int to pointer`() {
        val ir = builder()
        val result = ir.inttoptr(Type.i64(0), Type.OpaquePointer)
        assertEquals(Type.OpaquePointer, result.type)
    }

    @Test
    fun `bitcast converts between types`() {
        val ir = builder()
        val result = ir.bitcast(Type.i32(0), Type.F32)
        assertEquals(Type.F32, result.type)
    }

    @Test
    fun `addrspacecast converts pointer address space`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        val result = ir.addrspacecast(ptr, Type.OpaquePointer)
        assertEquals(Type.OpaquePointer, result.type)
    }

    @Test
    fun `ret void emits instruction`() {
        val ir = builder()
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val ret = mod.functions[0].blocks[0].instructions.last() as Ret
        assertNull(ret.value)
    }

    @Test
    fun `ret with value emits instruction`() {
        val ir = builder()
        ir.ret(Type.i32(42))
        ir.finalizeFunction()
        val mod = ir.build()
        val ret = mod.functions[0].blocks[0].instructions.last() as Ret
        assertNotNull(ret.value)
    }

    @Test
    fun `br emits unconditional branch`() {
        val ir = builder()
        ir.createBlock("target")
        ir.br(BlockRef("target"))
        ir.finalizeFunction()
        val mod = ir.build()
        val br = mod.functions[0].blocks[0].instructions.last() as Br
        assertEquals(BlockRef("target"), br.target)
    }

    @Test
    fun `condBr emits conditional branch`() {
        val ir = builder()
        ir.createBlock("then")
        ir.createBlock("else")
        val cond = ir.icmp(ICmpPredicate.EQ, Type.i32(1), Type.i32(1))
        ir.condBr(cond, BlockRef("then"), BlockRef("else"))
        ir.finalizeFunction()
        val mod = ir.build()
        val condBr = mod.functions[0].blocks[0].instructions.last() as CondBr
        assertEquals(BlockRef("then"), condBr.trueTarget)
        assertEquals(BlockRef("else"), condBr.falseTarget)
    }

    @Test
    fun `switch emits switch instruction`() {
        val ir = builder()
        ir.createBlock("default")
        ir.createBlock("case0")
        ir.createBlock("case1")
        ir.switch(Type.i32(0), "default", listOf(
            Constant.I32(0) to "case0",
            Constant.I32(1) to "case1",
        ))
        ir.finalizeFunction()
        val mod = ir.build()
        val sw = mod.functions[0].blocks[0].instructions.last() as Switch
        assertEquals(BlockRef("default"), sw.defaultTarget)
        assertEquals(2, sw.cases.size)
    }

    @Test
    fun `indirectBr emits indirect branch`() {
        val ir = builder()
        val addr = ir.alloca(Type.I8)
        ir.createBlock("t1")
        ir.createBlock("t2")
        ir.indirectBr(addr, listOf(BlockRef("t1"), BlockRef("t2")))
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.last() is IndirectBr)
    }

    @Test
    fun `unreachable emits instruction`() {
        val ir = builder()
        ir.unreachable()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.last() is Unreachable)
    }

    @Test
    fun `trap emits instruction`() {
        val ir = builder()
        ir.trap()
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Trap })
    }

    @Test
    fun `debugTrap emits instruction`() {
        val ir = builder()
        ir.debugTrap()
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is DebugTrap })
    }

    @Test
    fun `call with return value`() {
        val ir = builder()
        val fnRef = GlobalRef("add", Type.Function(listOf(Type.I32, Type.I32), Type.I32))
        val result = ir.call(fnRef, listOf(Type.i32(1), Type.i32(2)), Type.I32)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `call void returns null`() {
        val ir = builder()
        val fnRef = GlobalRef("print", Type.Function(listOf(Type.I32), Type.Void))
        val result = ir.call(fnRef, listOf(Type.i32(1)), Type.Void)
        assertNull(result)
    }

    @Test
    fun `call by name`() {
        val ir = builder()
        val result = ir.call("add", listOf(Type.i32(1), Type.i32(2)), Type.I32)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `call with calling convention`() {
        val ir = builder()
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.I32))
        val result = ir.call(fnRef, emptyList(), Type.I32, CallingConvention.FAST)
        assertNotNull(result)
    }

    @Test
    fun `call with tail call kind`() {
        val ir = builder()
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.I32))
        val result = ir.call(fnRef, emptyList(), Type.I32, tailCall = TailCallKind.TAIL)
        assertNotNull(result)
    }

    @Test
    fun `invoke with return value`() {
        val ir = builder()
        ir.createBlock("normal")
        ir.createBlock("unwind")
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.I32))
        val result = ir.invoke(fnRef, emptyList(), Type.I32, BlockRef("normal"), BlockRef("unwind"))
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `invoke void returns null`() {
        val ir = builder()
        ir.createBlock("normal")
        ir.createBlock("unwind")
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.Void))
        val result = ir.invoke(fnRef, emptyList(), Type.Void, BlockRef("normal"), BlockRef("unwind"))
        assertNull(result)
    }

    @Test
    fun `callBr with return value`() {
        val ir = builder()
        ir.createBlock("fallthrough")
        ir.createBlock("indirect")
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.I32))
        val result = ir.callBr(fnRef, emptyList(), Type.I32, "fallthrough", listOf("indirect"))
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `callBr void returns null`() {
        val ir = builder()
        ir.createBlock("fallthrough")
        val fnRef = GlobalRef("f", Type.Function(emptyList(), Type.Void))
        val result = ir.callBr(fnRef, emptyList(), Type.Void, "fallthrough", emptyList())
        assertNull(result)
    }

    @Test
    fun `vaStart emits instruction`() {
        val ir = builder()
        val list = ir.alloca(Type.I8)
        ir.vaStart(list)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is VAStart })
    }

    @Test
    fun `vaEnd emits instruction`() {
        val ir = builder()
        val list = ir.alloca(Type.I8)
        ir.vaEnd(list)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is VAEnd })
    }

    @Test
    fun `vaCopy emits instruction`() {
        val ir = builder()
        val dst = ir.alloca(Type.I8)
        val src = ir.alloca(Type.I8)
        ir.vaCopy(dst, src)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is VACopy })
    }

    @Test
    fun `vaArg returns correct type`() {
        val ir = builder()
        val list = ir.alloca(Type.I8)
        val result = ir.vaArg(list, Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `landingPad returns result type`() {
        val ir = builder()
        val typeInfo = GlobalRef("_ZTIi", Type.OpaquePointer)
        val result = ir.landingPad(
            Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)),
            listOf(LandingPadClause.Catch(typeInfo))
        )
        val structType = result.type as Type.Struct
        assertEquals(2, structType.fields.size)
    }

    @Test
    fun `landingPad with cleanup`() {
        val ir = builder()
        val result = ir.landingPad(
            Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)),
            emptyList(),
            cleanup = true
        )
        assertNotNull(result)
    }

    @Test
    fun `resume emits instruction`() {
        val ir = builder()
        val exc = ir.landingPad(Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)), emptyList(), cleanup = true)
        ir.resume(exc)
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Resume })
    }

    @Test
    fun `catchSwitch returns token`() {
        val ir = builder()
        ir.createBlock("handler")
        val result = ir.catchSwitch(null, listOf("handler"), null)
        assertEquals(Type.Token, result.type)
    }

    @Test
    fun `catchPad returns token`() {
        val ir = builder()
        ir.createBlock("handler")
        val cs = ir.catchSwitch(null, listOf("handler"), null)
        val result = ir.catchPad(cs, emptyList())
        assertEquals(Type.Token, result.type)
    }

    @Test
    fun `cleanupPad returns token`() {
        val ir = builder()
        val result = ir.cleanupPad(null, emptyList())
        assertEquals(Type.Token, result.type)
    }

    @Test
    fun `catchRet emits instruction`() {
        val ir = builder()
        ir.createBlock("handler")
        ir.createBlock("cont")
        val cs = ir.catchSwitch(null, listOf("handler"), null)
        val cp = ir.catchPad(cs, emptyList())
        ir.catchRet(cp, "cont")
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CatchRet })
    }

    @Test
    fun `cleanupRet emits instruction`() {
        val ir = builder()
        ir.createBlock("unwind")
        val cp = ir.cleanupPad(null, emptyList())
        ir.cleanupRet(cp, "unwind")
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CleanupRet })
    }

    @Test
    fun `phi returns correct type`() {
        val ir = builder()
        val result = ir.phi(Type.I32, listOf(Type.i32(1) to BlockRef("block1"), Type.i32(2) to BlockRef("block2")))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `select returns trueValue type`() {
        val ir = builder()
        val cond = ir.icmp(ICmpPredicate.EQ, Type.i32(1), Type.i32(1))
        val result = ir.select(cond, Type.i32(10), Type.i32(20))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `freeze returns same type`() {
        val ir = builder()
        val result = ir.freeze(Type.i32(42))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `extractElement returns element type`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val vec = InstructionRef("%vec", vecType)
        val result = ir.extractElement(vec, Type.i32(0))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `insertElement returns vector type`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val vec = InstructionRef("%vec", vecType)
        val result = ir.insertElement(vec, Type.i32(42), Type.i32(0))
        assertEquals(vecType, result.type)
    }

    @Test
    fun `shuffleVector returns resized vector type`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val v1 = InstructionRef("%v1", vecType)
        val v2 = InstructionRef("%v2", vecType)
        val result = ir.shuffleVector(v1, v2, listOf(0, 2, 4, 6))
        val resultType = result.type as Type.Vector
        assertEquals(Type.I32, resultType.element)
        assertEquals(4, resultType.lanes)
    }

    @Test
    fun `shuffleVector with different mask size`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val v1 = InstructionRef("%v1", vecType)
        val v2 = InstructionRef("%v2", vecType)
        val result = ir.shuffleVector(v1, v2, listOf(0, 1))
        val resultType = result.type as Type.Vector
        assertEquals(2, resultType.lanes)
    }

    @Test
    fun `splat returns vector type`() {
        val ir = builder()
        val targetType = Type.Vector(Type.I32, 4)
        val result = ir.splat(Type.i32(42), targetType)
        assertEquals(targetType, result.type)
    }

    @Test
    fun `vectorReduce returns element type`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val vec = InstructionRef("%vec", vecType)
        val result = ir.vectorReduce(VectorReduceOp.ADD, vec)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `extractValue from struct`() {
        val ir = builder()
        val structType = Type.Struct(null, listOf(Type.I32, Type.F64))
        val agg = InstructionRef("%agg", structType)
        val result = ir.extractValue(agg, 1)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `extractValue from array`() {
        val ir = builder()
        val arrayType = Type.Array(Type.I32, 10)
        val agg = InstructionRef("%arr", arrayType)
        val result = ir.extractValue(agg, 0)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `extractValue nested indices`() {
        val ir = builder()
        val inner = Type.Struct(null, listOf(Type.I32, Type.F64))
        val outer = Type.Struct(null, listOf(inner, Type.I8))
        val agg = InstructionRef("%agg", outer)
        val result = ir.extractValue(agg, 0, 1)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `insertValue returns aggregate type`() {
        val ir = builder()
        val structType = Type.Struct(null, listOf(Type.I32, Type.F64))
        val agg = InstructionRef("%agg", structType)
        val result = ir.insertValue(agg, Type.i32(42), 0)
        assertEquals(structType, result.type)
    }

    @Test
    fun `newObject returns class ref type`() {
        val ir = builder()
        val result = ir.newObject("MyClass")
        assertEquals(Type.ClassRef("MyClass"), result.type)
    }

    @Test
    fun `newObject with type args`() {
        val ir = builder()
        val result = ir.newObject("List", listOf(Type.I32))
        assertEquals(Type.ClassRef("List"), result.type)
    }

    @Test
    fun `newArray returns array type`() {
        val ir = builder()
        val result = ir.newArray(Type.I32, Type.i32(10))
        val arrType = result.type as Type.Array
        assertEquals(Type.I32, arrType.element)
    }

    @Test
    fun `newMultiArray returns array type`() {
        val ir = builder()
        val result = ir.newMultiArray(Type.I32, listOf(Type.i32(3), Type.i32(4)))
        val arrType = result.type as Type.Array
        assertEquals(Type.I32, arrType.element)
    }

    @Test
    fun `getField returns field type`() {
        val ir = builder()
        val obj = ir.newObject("Point")
        val result = ir.getField(obj, "Point", "x", Type.F64)
        assertEquals(Type.F64, result.type)
    }

    @Test
    fun `putField emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Point")
        ir.putField(obj, "Point", "x", Type.F64, Type.f64(3.14))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is PutField })
    }

    @Test
    fun `getStatic returns field type`() {
        val ir = builder()
        val result = ir.getStatic("Counter", "count", Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `putStatic emits instruction`() {
        val ir = builder()
        ir.putStatic("Counter", "count", Type.I32, Type.i32(0))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is PutStatic })
    }

    @Test
    fun `virtualCall with return value`() {
        val ir = builder()
        val obj = ir.newObject("Animal")
        val methodType = Type.Function(emptyList(), Type.I32)
        val result = ir.virtualCall(obj, "Animal", "getAge", methodType, emptyList())
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `virtualCall void returns null`() {
        val ir = builder()
        val obj = ir.newObject("Animal")
        val methodType = Type.Function(listOf(Type.I32), Type.Void)
        val result = ir.virtualCall(obj, "Animal", "setAge", methodType, listOf(Type.i32(5)))
        assertNull(result)
    }

    @Test
    fun `interfaceCall with return value`() {
        val ir = builder()
        val obj = ir.newObject("Dog")
        val methodType = Type.Function(emptyList(), Type.I32)
        val result = ir.interfaceCall(obj, "Comparable", "compareTo", methodType, emptyList())
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `specialCall with return value`() {
        val ir = builder()
        val obj = ir.newObject("Child")
        val methodType = Type.Function(emptyList(), Type.I32)
        val result = ir.specialCall(obj, "Parent", "method", methodType, emptyList())
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `staticCall with return value`() {
        val ir = builder()
        val methodType = Type.Function(listOf(Type.I32), Type.I32)
        val result = ir.staticCall("Math", "abs", methodType, listOf(Type.i32(-5)))
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `staticCall void returns null`() {
        val ir = builder()
        val methodType = Type.Function(listOf(Type.I32), Type.Void)
        val result = ir.staticCall("System", "exit", methodType, listOf(Type.i32(0)))
        assertNull(result)
    }

    @Test
    fun `dynamicCall with return value`() {
        val ir = builder()
        val bootstrap = BootstrapMethod("Bootstrap", "invoke", Type.Function(emptyList(), Type.I32))
        val methodType = Type.Function(emptyList(), Type.I32)
        val result = ir.dynamicCall(bootstrap, "target", methodType, emptyList())
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `constructorCall emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Point")
        val ctorType = Type.Function(listOf(Type.F64, Type.F64), Type.Void)
        ir.constructorCall(obj, "Point", ctorType, listOf(Type.f64(1.0), Type.f64(2.0)))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is ConstructorCall })
    }

    @Test
    fun `instanceOf returns i1`() {
        val ir = builder()
        val obj = ir.newObject("Dog")
        val result = ir.instanceOf(obj, Type.ClassRef("Animal"))
        assertEquals(Type.I1, result.type)
    }

    @Test
    fun `checkCast returns cast type`() {
        val ir = builder()
        val obj = ir.newObject("Animal")
        val result = ir.checkCast(obj, Type.ClassRef("Dog"))
        assertEquals(Type.ClassRef("Dog"), result.type)
    }

    @Test
    fun `typeId returns i32`() {
        val ir = builder()
        val obj = ir.newObject("MyClass")
        val result = ir.typeId(obj)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `arrayGet returns element type`() {
        val ir = builder()
        val arr = ir.newArray(Type.I32, Type.i32(10))
        val result = ir.arrayGet(arr, Type.i32(0), Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `arraySet emits instruction`() {
        val ir = builder()
        val arr = ir.newArray(Type.I32, Type.i32(10))
        ir.arraySet(arr, Type.i32(0), Type.i32(42), Type.I32)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is ArraySet })
    }

    @Test
    fun `arrayLength returns i32`() {
        val ir = builder()
        val arr = ir.newArray(Type.I32, Type.i32(10))
        val result = ir.arrayLength(arr)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `monitorEnter emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Lock")
        ir.monitorEnter(obj)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is MonitorEnter })
    }

    @Test
    fun `monitorExit emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Lock")
        ir.monitorExit(obj)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is MonitorExit })
    }

    @Test
    fun `throwException emits instruction`() {
        val ir = builder()
        val exc = ir.newObject("Exception")
        ir.throwException(exc)
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Throw })
    }

    @Test
    fun `tryCatch emits instruction`() {
        val ir = builder()
        ir.tryCatch("tryBlock", listOf(CatchHandler(Type.ClassRef("Exception"), "catchBlock")))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is TryCatchRegion })
    }

    @Test
    fun `tryCatch with finally`() {
        val ir = builder()
        ir.tryCatch("tryBlock", listOf(CatchHandler(Type.ClassRef("Exception"), "catchBlock")), finallyBlock = "finallyBlock")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val tc = mod.functions[0].blocks[0].instructions.filterIsInstance<TryCatchRegion>().single()
        assertEquals(BlockRef("finallyBlock"), tc.finallyBlock)
    }

    @Test
    fun `box returns box type`() {
        val ir = builder()
        val result = ir.box(Type.i32(42), Type.ClassRef("Integer"))
        assertEquals(Type.ClassRef("Integer"), result.type)
    }

    @Test
    fun `unbox returns unboxed type`() {
        val ir = builder()
        val boxed = ir.box(Type.i32(42), Type.ClassRef("Integer"))
        val result = ir.unbox(boxed, Type.I32)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `closureCreate returns closure type`() {
        val ir = builder()
        val fnRef = GlobalRef("lambda", Type.Function(listOf(Type.I32), Type.I32))
        val closureType = Type.Function(listOf(Type.I32), Type.I32)
        val result = ir.closureCreate(fnRef, listOf(Type.i32(10)), closureType)
        assertEquals(closureType, result.type)
    }

    @Test
    fun `closureInvoke with return value`() {
        val ir = builder()
        val fnRef = GlobalRef("lambda", Type.Function(listOf(Type.I32), Type.I32))
        val closureType = Type.Function(listOf(Type.I32), Type.I32)
        val closure = ir.closureCreate(fnRef, emptyList(), closureType)
        val result = ir.closureInvoke(closure, listOf(Type.i32(5)), Type.I32)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `closureInvoke void returns null`() {
        val ir = builder()
        val fnRef = GlobalRef("lambda", Type.Function(emptyList(), Type.Void))
        val closureType = Type.Function(emptyList(), Type.Void)
        val closure = ir.closureCreate(fnRef, emptyList(), closureType)
        val result = ir.closureInvoke(closure, emptyList(), Type.Void)
        assertNull(result)
    }

    @Test
    fun `constructVariant returns union type`() {
        val ir = builder()
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val result = ir.constructVariant(unionType, "Circle", listOf(Type.f64(5.0)))
        assertEquals(unionType, result.type)
    }

    @Test
    fun `getTag returns tag type`() {
        val ir = builder()
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
        ))
        val variant = ir.constructVariant(unionType, "Circle", listOf(Type.f64(5.0)))
        val tag = ir.getTag(variant)
        assertEquals(Type.I32, tag.type)
    }

    @Test
    fun `getVariantField returns field type`() {
        val ir = builder()
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
        ))
        val variant = ir.constructVariant(unionType, "Circle", listOf(Type.f64(5.0)))
        val field = ir.getVariantField(variant, "Circle", 0, Type.F64)
        assertEquals(Type.F64, field.type)
    }

    @Test
    fun `tagSwitch emits instruction`() {
        val ir = builder()
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
            TaggedVariant("Rect", 1, listOf(Type.F64, Type.F64)),
        ))
        val variant = ir.constructVariant(unionType, "Circle", listOf(Type.f64(5.0)))
        ir.createBlock("circleBlock")
        ir.createBlock("rectBlock")
        ir.tagSwitch(variant, listOf("Circle" to "circleBlock", "Rect" to "rectBlock"))
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is TagSwitch })
    }

    @Test
    fun `tagSwitch with default target`() {
        val ir = builder()
        val unionType = Type.TaggedUnion("Shape", Type.I32, listOf(
            TaggedVariant("Circle", 0, listOf(Type.F64)),
        ))
        val variant = ir.constructVariant(unionType, "Circle", listOf(Type.f64(5.0)))
        ir.createBlock("circleBlock")
        ir.createBlock("default")
        ir.tagSwitch(variant, listOf("Circle" to "circleBlock"), defaultTarget = "default")
        ir.finalizeFunction()
        val mod = ir.build()
        val ts = mod.functions[0].blocks[0].instructions.filterIsInstance<TagSwitch>().single()
        assertEquals(BlockRef("default"), ts.defaultTarget)
    }

    @Test
    fun `gcAlloc returns reference type`() {
        val ir = builder()
        val result = ir.gcAlloc(Type.I32)
        assertEquals(Type.Reference(Type.I32), result.type)
    }

    @Test
    fun `gcAlloc with explicit size`() {
        val ir = builder()
        val result = ir.gcAlloc(Type.I32, size = Type.i64(16))
        assertEquals(Type.Reference(Type.I32), result.type)
    }

    @Test
    fun `gcSafepoint emits instruction`() {
        val ir = builder()
        ir.gcSafepoint()
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is GCSafepoint })
    }

    @Test
    fun `gcRoot emits instruction`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.gcRoot(ptr)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is GCRoot })
    }

    @Test
    fun `gcRoot with metadata`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.gcRoot(ptr, metadata = Type.i32(0))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is GCRoot })
    }

    @Test
    fun `pin returns pinned ref type`() {
        val ir = builder()
        val ref = ir.gcAlloc(Type.I32)
        val pinned = ir.pin(ref)
        assertEquals(Type.PinnedRef(Type.I32), pinned.type)
    }

    @Test
    fun `unpin emits instruction`() {
        val ir = builder()
        val ref = ir.gcAlloc(Type.I32)
        ir.unpin(ref)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Unpin })
    }

    @Test
    fun `interiorPtr returns interior ref type`() {
        val ir = builder()
        val ref = ir.gcAlloc(Type.I32)
        val result = ir.interiorPtr(ref, Type.i32(0), Type.I32)
        assertEquals(Type.InteriorRef(Type.I32), result.type)
    }

    @Test
    fun `writeBarrier emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Container")
        ir.writeBarrier(obj, Type.i32(0), Type.i32(42))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is WriteBarrier })
    }

    @Test
    fun `readBarrier returns same type`() {
        val ir = builder()
        val ref = ir.gcAlloc(Type.I32)
        val result = ir.readBarrier(ref)
        assertEquals(ref.type, result.type)
    }

    @Test
    fun `managedCall with return value`() {
        val ir = builder()
        val fnRef = GlobalRef("native_func", Type.Function(emptyList(), Type.I32))
        val result = ir.managedCall(fnRef, emptyList(), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `managedCall void returns null`() {
        val ir = builder()
        val fnRef = GlobalRef("native_func", Type.Function(emptyList(), Type.Void))
        val result = ir.managedCall(fnRef, emptyList(), Type.Void, ManagedCallDirection.MANAGED_TO_NATIVE)
        assertNull(result)
    }

    @Test
    fun `refRetain emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Obj")
        ir.refRetain(obj)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is RefRetain })
    }

    @Test
    fun `refRelease emits instruction`() {
        val ir = builder()
        val obj = ir.newObject("Obj")
        ir.refRelease(obj)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is RefRelease })
    }

    @Test
    fun `refCount returns i32`() {
        val ir = builder()
        val obj = ir.newObject("Obj")
        val result = ir.refCount(obj)
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `coroBegin returns opaque pointer`() {
        val ir = builder()
        assertEquals(Type.OpaquePointer, ir.coroBegin(Type.i64(0), ir.alloca(Type.I8)).type)
    }

    @Test
    fun `coroEnd emits instruction`() {
        val ir = builder()
        val handle = ir.coroBegin(Type.i64(0), ir.alloca(Type.I8))
        ir.coroEnd(handle)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CoroEnd })
    }

    @Test
    fun `coroEnd with unwind`() {
        val ir = builder()
        val handle = ir.coroBegin(Type.i64(0), ir.alloca(Type.I8))
        ir.coroEnd(handle, unwind = true)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CoroEnd })
    }

    @Test
    fun `coroSuspend returns i8`() {
        val ir = builder()
        val result = ir.coroSuspend()
        assertEquals(Type.I8, result.type)
    }

    @Test
    fun `coroSuspend final`() {
        val ir = builder()
        val result = ir.coroSuspend(isFinal = true)
        assertEquals(Type.I8, result.type)
    }

    @Test
    fun `coroResume emits instruction`() {
        val ir = builder()
        val handle = ir.coroBegin(Type.i64(0), ir.alloca(Type.I8))
        ir.coroResume(handle)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CoroResume })
    }

    @Test
    fun `coroDestroy emits instruction`() {
        val ir = builder()
        val handle = ir.coroBegin(Type.i64(0), ir.alloca(Type.I8))
        ir.coroDestroy(handle)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is CoroDestroy })
    }

    @Test
    fun `coroSize returns i64`() {
        val ir = builder()
        assertEquals(Type.I64, ir.coroSize().type)
    }

    @Test
    fun `intrinsic with return value`() {
        val ir = builder()
        val result = ir.intrinsic("llvm.bswap.i32", listOf(Type.i32(0x12345678)), Type.I32)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `intrinsic void returns null`() {
        val ir = builder()
        val result = ir.intrinsic("llvm.debugtrap", emptyList(), Type.Void)
        assertNull(result)
    }

    @Test
    fun `inlineAsm with return value`() {
        val ir = builder()
        val result = ir.inlineAsm("mov \$0, \$1", "=r,r", listOf(Type.i32(42)), Type.I32)
        assertNotNull(result)
        assertEquals(Type.I32, result!!.type)
    }

    @Test
    fun `inlineAsm void returns null`() {
        val ir = builder()
        val result = ir.inlineAsm("nop", "")
        assertNull(result)
    }

    @Test
    fun `inlineAsm with all options`() {
        val ir = builder()
        val result = ir.inlineAsm("int $$0x3", "", sideEffects = true, alignStack = true, dialect = AsmDialect.INTEL, returnType = Type.Void)
        assertNull(result)
    }

    @Test
    fun `debugLoc emits instruction`() {
        val ir = builder()
        ir.debugLoc(42, 10, "main")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val dl = mod.functions[0].blocks[0].instructions.filterIsInstance<DebugLoc>().single()
        assertEquals(42, dl.line)
        assertEquals(10, dl.col)
        assertEquals("main", dl.scope)
    }

    @Test
    fun `debugLoc with inlinedAt`() {
        val ir = builder()
        ir.debugLoc(42, 10, "main", inlinedAt = "caller")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val dl = mod.functions[0].blocks[0].instructions.filterIsInstance<DebugLoc>().single()
        assertEquals("caller", dl.inlinedAt)
    }

    @Test
    fun `debugValue emits instruction`() {
        val ir = builder()
        ir.debugValue("x", Type.i32(42))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is DebugValue })
    }

    @Test
    fun `debugValue with expression`() {
        val ir = builder()
        ir.debugValue("x", Type.i32(42), expression = "DW_OP_deref")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val dv = mod.functions[0].blocks[0].instructions.filterIsInstance<DebugValue>().single()
        assertEquals("DW_OP_deref", dv.expression)
    }

    @Test
    fun `debugDeclare emits instruction`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.debugDeclare("x", ptr)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is DebugDeclare })
    }

    @Test
    fun `debugDeclare with expression`() {
        val ir = builder()
        val ptr = ir.alloca(Type.I32)
        ir.debugDeclare("x", ptr, expression = "DW_OP_plus_uconst 8")
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        val dd = mod.functions[0].blocks[0].instructions.filterIsInstance<DebugDeclare>().single()
        assertEquals("DW_OP_plus_uconst 8", dd.expression)
    }

    @Test
    fun `assume emits instruction`() {
        val ir = builder()
        val cond = ir.icmp(ICmpPredicate.SGT, Type.i32(5), Type.i32(0))
        ir.assume(cond)
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Assume })
    }

    @Test
    fun `expect returns value type`() {
        val ir = builder()
        val result = ir.expect(Type.i32(1), Constant.I32(1))
        assertEquals(Type.I32, result.type)
    }

    @Test
    fun `each instruction gets unique SSA name`() {
        val ir = builder()
        val r1 = ir.add(Type.i32(1), Type.i32(2))
        val r2 = ir.add(Type.i32(3), Type.i32(4))
        val r3 = ir.mul(r1, r2)
        assertNotEquals((r1 as InstructionRef).name, (r2 as InstructionRef).name)
        assertNotEquals((r2 as InstructionRef).name, (r3 as InstructionRef).name)
    }

    @Test
    fun `instructions are emitted to current block`() {
        val ir = builder()
        ir.add(Type.i32(1), Type.i32(2))
        ir.sub(Type.i32(3), Type.i32(4))
        ir.ret()
        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(3, mod.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun `emitting without insertion point throws`() {
        val ir = ModuleBuilder("test", Target.wasm())
        ir.createFunction("f", emptyList(), Type.Void)
        assertThrows(IllegalStateException::class.java) {
            ir.add(Type.i32(1), Type.i32(2))
        }
    }

    @Test
    fun `chained arithmetic produces valid IR`() {
        val ir = builder()
        val a = Type.i32(10)
        val b = Type.i32(20)
        val c = Type.i32(30)
        val sum = ir.add(a, b)
        val product = ir.mul(sum, c)
        val result = ir.sub(product, a)
        ir.ret(result)
        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun `all vectorReduceOp variants work`() {
        val ir = builder()
        val vecType = Type.Vector(Type.I32, 4)
        val vec = InstructionRef("%vec", vecType)
        for (op in listOf(VectorReduceOp.ADD, VectorReduceOp.MUL, VectorReduceOp.AND, VectorReduceOp.OR, VectorReduceOp.XOR, VectorReduceOp.SMIN, VectorReduceOp.SMAX, VectorReduceOp.UMIN, VectorReduceOp.UMAX)) {
            val result = ir.vectorReduce(op, vec)
            assertEquals(Type.I32, result.type)
        }
    }

    @Test
    fun `float vector reduce operations`() {
        val ir = builder()
        val vecType = Type.Vector(Type.F32, 4)
        val vec = InstructionRef("%vec", vecType)
        for (op in listOf(VectorReduceOp.FADD, VectorReduceOp.FMUL, VectorReduceOp.FMIN, VectorReduceOp.FMAX)) {
            val result = ir.vectorReduce(op, vec)
            assertEquals(Type.F32, result.type)
        }
    }
}

package org.kgen.target.clr.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.clr.*
import org.kgen.target.clr.asm.CilDisassembler
import org.kgen.target.clr.asm.CilInstruction

class CilCodeGenComprehensiveTest {

    private fun buildAndDisassemble(block: (IrBuilder) -> Unit): List<CilInstruction> {
        val ir = IrBuilder("test", Target.msil())
        block(ir)
        val module = ir.build()
        val gen = CilCodeGenerator()
        val bytes = gen.generate(module)
        return CilDisassembler().disassemble(bytes)
    }

    private fun buildMethodAndDisassemble(
        params: List<Param> = emptyList(),
        returnType: Type = Type.I32,
        block: (IrBuilder) -> Unit,
    ): List<CilInstruction> {
        val ir = IrBuilder("test", Target.msil())
        ir.createFunction("testFn", params, returnType)
        ir.appendBlock("entry")
        block(ir)
        ir.finalizeFunction()
        val module = ir.build()
        val gen = CilCodeGenerator()
        val fn = module.functions.first { !it.isExternal }
        val bytes = gen.generateMethod(fn, module.functions)
        return CilDisassembler().disassemble(bytes)
    }

    private fun hasOpCode(instructions: List<CilInstruction>, opcode: CilOpCode): Boolean =
        instructions.any { it.opcode == opcode }

    // Integer Arithmetic - i32

    @Test
    fun `add i32 produces ADD opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.add(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
    }

    @Test
    fun `sub i32 produces SUB opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.sub(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SUB))
    }

    @Test
    fun `mul i32 produces MUL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.mul(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
    }

    @Test
    fun `sdiv i32 produces DIV opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.sdiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV))
    }

    @Test
    fun `udiv i32 produces DIV_UN opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.udiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV_UN))
    }

    @Test
    fun `srem i32 produces REM opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.srem(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.REM))
    }

    @Test
    fun `urem i32 produces REM_UN opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.urem(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.REM_UN))
    }

    // Integer Arithmetic - i64

    @Test
    fun `add i64 produces ADD opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.add(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
    }

    @Test
    fun `sub i64 produces SUB opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.sub(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SUB))
    }

    @Test
    fun `mul i64 produces MUL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.mul(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
    }

    @Test
    fun `sdiv i64 produces DIV opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.sdiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV))
    }

    @Test
    fun `udiv i64 produces DIV_UN opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.udiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV_UN))
    }

    @Test
    fun `srem i64 produces REM opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.srem(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.REM))
    }

    @Test
    fun `urem i64 produces REM_UN opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.urem(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.REM_UN))
    }

    // Float Arithmetic - f32

    @Test
    fun `fadd f32 produces ADD opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.F32
        ) { ir ->
            val a = Parameter("a", Type.F32, 0)
            val b = Parameter("b", Type.F32, 1)
            ir.ret(ir.fadd(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
    }

    @Test
    fun `fsub f32 produces SUB opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.F32
        ) { ir ->
            val a = Parameter("a", Type.F32, 0)
            val b = Parameter("b", Type.F32, 1)
            ir.ret(ir.fsub(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SUB))
    }

    @Test
    fun `fmul f32 produces MUL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.F32
        ) { ir ->
            val a = Parameter("a", Type.F32, 0)
            val b = Parameter("b", Type.F32, 1)
            ir.ret(ir.fmul(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
    }

    @Test
    fun `fdiv f32 produces DIV opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.F32
        ) { ir ->
            val a = Parameter("a", Type.F32, 0)
            val b = Parameter("b", Type.F32, 1)
            ir.ret(ir.fdiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV))
    }

    @Test
    fun `fneg f32 produces NEG opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F32)),
            returnType = Type.F32
        ) { ir ->
            val x = Parameter("x", Type.F32, 0)
            ir.ret(ir.fneg(x))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NEG))
    }

    // Float Arithmetic - f64

    @Test
    fun `fadd f64 produces ADD opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fadd(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
    }

    @Test
    fun `fsub f64 produces SUB opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fsub(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SUB))
    }

    @Test
    fun `fmul f64 produces MUL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fmul(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
    }

    @Test
    fun `fdiv f64 produces DIV opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.F64
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fdiv(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.DIV))
    }

    @Test
    fun `fneg f64 produces NEG opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fneg(x))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NEG))
    }

    // Bitwise Operations

    @Test
    fun `and produces AND opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.and(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.AND))
    }

    @Test
    fun `or produces OR opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.or(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `xor produces XOR opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.xor(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.XOR))
    }

    @Test
    fun `shl produces SHL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.shl(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SHL))
    }

    @Test
    fun `lshr produces SHR_UN opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.lshr(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SHR_UN))
    }

    @Test
    fun `ashr produces SHR opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.ashr(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SHR))
    }

    @Test
    fun `not produces NOT opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            ir.ret(ir.not(a))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NOT))
    }

    @Test
    fun `neg produces NEG opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32))
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.neg(x))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NEG))
    }

    // Bitwise on i64

    @Test
    fun `and i64 produces AND opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.and(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.AND))
    }

    @Test
    fun `or i64 produces OR opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.or(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `xor i64 produces XOR opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.xor(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.XOR))
    }

    @Test
    fun `shl i64 produces SHL opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.shl(a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.SHL))
    }

    @Test
    fun `not i64 produces NOT opcode`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            ir.ret(ir.not(a))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NOT))
    }

    // ICmp - All Predicates

    @Test
    fun `icmp EQ produces CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.EQ, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `icmp NE produces CEQ then CEQ with 0`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.NE, a, b))
        }
        val ceqCount = instructions.count { it.opcode == CilOpCode.CEQ }
        assertEquals(2, ceqCount, "NE should produce two CEQ instructions (negate)")
    }

    @Test
    fun `icmp SGT produces CGT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.SGT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
    }

    @Test
    fun `icmp SLT produces CLT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.SLT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
    }

    @Test
    fun `icmp SGE produces CLT then CEQ with 0`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.SGE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `icmp SLE produces CGT then CEQ with 0`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.SLE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `icmp UGT produces CGT_UN`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.UGT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT_UN))
    }

    @Test
    fun `icmp ULT produces CLT_UN`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.ULT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT_UN))
    }

    @Test
    fun `icmp UGE produces CLT_UN then CEQ with 0`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.UGE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT_UN))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `icmp ULE produces CGT_UN then CEQ with 0`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.icmp(ICmpPredicate.ULE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT_UN))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    // ICmp on i64

    @Test
    fun `icmp EQ i64 produces CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64))
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.icmp(ICmpPredicate.EQ, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `icmp SGT i64 produces CGT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64))
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            ir.ret(ir.icmp(ICmpPredicate.SGT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
    }

    // FCmp - Key Predicates

    @Test
    fun `fcmp OEQ produces CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OEQ, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp UEQ produces CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.UEQ, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp ONE produces CEQ CEQ negate`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.ONE, a, b))
        }
        val ceqCount = instructions.count { it.opcode == CilOpCode.CEQ }
        assertEquals(2, ceqCount)
    }

    @Test
    fun `fcmp UNE produces negated CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.UNE, a, b))
        }
        val ceqCount = instructions.count { it.opcode == CilOpCode.CEQ }
        assertEquals(2, ceqCount)
    }

    @Test
    fun `fcmp OGT produces CGT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OGT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
    }

    @Test
    fun `fcmp OLT produces CLT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OLT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
    }

    @Test
    fun `fcmp OGE produces CLT then negate`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OGE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp OLE produces CGT then negate`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OLE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp UGT produces CGT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.UGT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
    }

    @Test
    fun `fcmp ULT produces CLT`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.ULT, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
    }

    @Test
    fun `fcmp UGE produces CLT then negate`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.UGE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CLT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp ULE produces CGT then negate`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.ULE, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp FALSE generates instruction sequence`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.FALSE, a, b))
        }
        assertTrue(instructions.isNotEmpty())
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    @Test
    fun `fcmp TRUE generates instruction sequence`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.TRUE, a, b))
        }
        assertTrue(instructions.isNotEmpty())
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `fcmp ORD generates instruction sequence`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.ORD, a, b))
        }
        assertTrue(instructions.isNotEmpty())
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `fcmp UNO generates instruction sequence`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F64), Param("b", Type.F64)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fcmp(FCmpPredicate.UNO, a, b))
        }
        assertTrue(instructions.isNotEmpty())
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `fcmp on f32 produces CEQ`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.F32), Param("b", Type.F32)),
            returnType = Type.I1
        ) { ir ->
            val a = Parameter("a", Type.F32, 0)
            val b = Parameter("b", Type.F32, 1)
            ir.ret(ir.fcmp(FCmpPredicate.OEQ, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
    }

    // Conversions - Integer to Integer

    @Test
    fun `sext i32 to i64 produces CONV_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.sext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I8))
    }

    @Test
    fun `sext i8 to i32 produces CONV_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I8)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I8, 0)
            ir.ret(ir.sext(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `sext i16 to i32 produces CONV_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I16)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I16, 0)
            ir.ret(ir.sext(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `sext i8 to i64 produces CONV_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I8)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I8, 0)
            ir.ret(ir.sext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I8))
    }

    @Test
    fun `sext i16 to i64 produces CONV_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I16)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I16, 0)
            ir.ret(ir.sext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I8))
    }

    @Test
    fun `zext i32 to i64 produces CONV_U8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.zext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U8))
    }

    @Test
    fun `zext i8 to i32 produces CONV_U4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I8)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I8, 0)
            ir.ret(ir.zext(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U4))
    }

    @Test
    fun `zext i16 to i32 produces CONV_U4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I16)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I16, 0)
            ir.ret(ir.zext(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U4))
    }

    @Test
    fun `zext i8 to i64 produces CONV_U8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I8)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I8, 0)
            ir.ret(ir.zext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U8))
    }

    @Test
    fun `zext i16 to i64 produces CONV_U8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I16)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.I16, 0)
            ir.ret(ir.zext(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U8))
    }

    @Test
    fun `trunc i64 to i32 produces CONV_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.trunc(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `trunc i32 to i16 produces CONV_I2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I16
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.trunc(x, Type.I16))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I2))
    }

    @Test
    fun `trunc i32 to i8 produces CONV_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I8
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.trunc(x, Type.I8))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I1))
    }

    @Test
    fun `trunc i32 to i1 produces AND with 1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I1
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.trunc(x, Type.I1))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.AND))
    }

    @Test
    fun `trunc i64 to i16 produces CONV_I2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.I16
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.trunc(x, Type.I16))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I2))
    }

    @Test
    fun `trunc i64 to i8 produces CONV_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.I8
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.trunc(x, Type.I8))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I1))
    }

    // Conversions - Int to Float

    @Test
    fun `sitofp i32 to f64 produces CONV_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.sitofp(x, Type.F64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R8))
    }

    @Test
    fun `sitofp i32 to f32 produces CONV_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F32
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.sitofp(x, Type.F32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R4))
    }

    @Test
    fun `sitofp i64 to f64 produces CONV_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.sitofp(x, Type.F64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R8))
    }

    @Test
    fun `sitofp i64 to f32 produces CONV_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.F32
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.sitofp(x, Type.F32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R4))
    }

    @Test
    fun `uitofp i32 to f32 produces CONV_R_UN then CONV_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F32
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.uitofp(x, Type.F32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R_UN))
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R4))
    }

    @Test
    fun `uitofp i32 to f64 produces CONV_R_UN then CONV_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.uitofp(x, Type.F64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R_UN))
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R8))
    }

    @Test
    fun `uitofp i64 to f64 produces CONV_R_UN`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I64)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.I64, 0)
            ir.ret(ir.uitofp(x, Type.F64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R_UN))
    }

    // Conversions - Float to Int

    @Test
    fun `fptosi f64 to i32 produces CONV_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptosi(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `fptosi f64 to i64 produces CONV_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptosi(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I8))
    }

    @Test
    fun `fptosi f64 to i16 produces CONV_I2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I16
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptosi(x, Type.I16))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I2))
    }

    @Test
    fun `fptosi f64 to i8 produces CONV_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I8
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptosi(x, Type.I8))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I1))
    }

    @Test
    fun `fptosi f32 to i32 produces CONV_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F32)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.F32, 0)
            ir.ret(ir.fptosi(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `fptoui f64 to i32 produces CONV_U4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptoui(x, Type.I32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U4))
    }

    @Test
    fun `fptoui f64 to i64 produces CONV_U8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I64
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptoui(x, Type.I64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U8))
    }

    @Test
    fun `fptoui f64 to i16 produces CONV_U2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I16
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptoui(x, Type.I16))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U2))
    }

    @Test
    fun `fptoui f64 to i8 produces CONV_U1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.I8
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptoui(x, Type.I8))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_U1))
    }

    // Conversions - Float to Float

    @Test
    fun `fpext f32 to f64 produces CONV_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F32)),
            returnType = Type.F64
        ) { ir ->
            val x = Parameter("x", Type.F32, 0)
            ir.ret(ir.fpext(x, Type.F64))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R8))
    }

    @Test
    fun `fptrunc f64 to f32 produces CONV_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.F64)),
            returnType = Type.F32
        ) { ir ->
            val x = Parameter("x", Type.F64, 0)
            ir.ret(ir.fptrunc(x, Type.F32))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R4))
    }

    // Control Flow

    @Test
    fun `ret void produces single RET`() {
        val instructions = buildMethodAndDisassemble(
            returnType = Type.Void
        ) { ir ->
            ir.ret()
        }
        assertEquals(1, instructions.size)
        assertEquals(CilOpCode.RET, instructions[0].opcode)
    }

    @Test
    fun `ret i32 constant pushes value then RET`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(42))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.RET))
        assertTrue(instructions.size >= 2)
    }

    @Test
    fun `br produces BR opcode`() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("test", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.br(BlockRef("target"))

            ir.appendBlock("target")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BR))
    }

    @Test
    fun `condBr produces BRTRUE and BR`() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("test", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            val cmp = ir.icmp(ICmpPredicate.SGT, x, Constant.I32(0))
            ir.condBr(cmp, BlockRef("positive"), BlockRef("negative"))

            ir.appendBlock("positive")
            ir.ret(Constant.I32(1))

            ir.appendBlock("negative")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BRTRUE))
        assertTrue(hasOpCode(instructions, CilOpCode.BR))
    }

    @Test
    fun `switch produces CEQ per case and BR for default`() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("sw", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            ir.switch(x, "default", listOf(
                Constant.I32(0) to "case0",
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2"
            ))

            ir.appendBlock("case0")
            ir.ret(Constant.I32(10))
            ir.appendBlock("case1")
            ir.ret(Constant.I32(20))
            ir.appendBlock("case2")
            ir.ret(Constant.I32(30))
            ir.appendBlock("default")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        val ceqCount = instructions.count { it.opcode == CilOpCode.CEQ }
        assertEquals(3, ceqCount, "Switch with 3 cases should produce 3 CEQ instructions")
        assertTrue(hasOpCode(instructions, CilOpCode.BR))
    }

    @Test
    fun `switch with single case`() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("sw1", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            ir.switch(x, "default", listOf(
                Constant.I32(5) to "case5"
            ))
            ir.appendBlock("case5")
            ir.ret(Constant.I32(50))
            ir.appendBlock("default")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
        assertTrue(hasOpCode(instructions, CilOpCode.BRTRUE))
        assertTrue(hasOpCode(instructions, CilOpCode.BR))
    }

    // Select

    @Test
    fun `select produces BRFALSE for conditional`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val cmp = ir.icmp(ICmpPredicate.SGT, a, b)
            ir.ret(ir.select(cmp, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BRFALSE))
    }

    @Test
    fun `select with constants`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("cond", Type.I32))
        ) { ir ->
            val cond = Parameter("cond", Type.I32, 0)
            val cmp = ir.icmp(ICmpPredicate.NE, cond, Constant.I32(0))
            ir.ret(ir.select(cmp, Constant.I32(100), Constant.I32(200)))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BRFALSE))
    }

    @Test
    fun `select with i64 values`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I64), Param("b", Type.I64)),
            returnType = Type.I64
        ) { ir ->
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val cmp = ir.icmp(ICmpPredicate.SGT, a, b)
            ir.ret(ir.select(cmp, a, b))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BRFALSE))
    }

    // Load/Store

    @Test
    fun `load i32 produces LDIND_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer))
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.I32, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_I4))
    }

    @Test
    fun `load i64 produces LDIND_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.I64
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.I64, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_I8))
    }

    @Test
    fun `load i8 produces LDIND_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.I8
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.I8, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_I1))
    }

    @Test
    fun `load i16 produces LDIND_I2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.I16
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.I16, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_I2))
    }

    @Test
    fun `load f32 produces LDIND_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.F32
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.F32, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_R4))
    }

    @Test
    fun `load f64 produces LDIND_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.F64
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.ret(ir.load(Type.F64, ptr))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_R8))
    }

    @Test
    fun `store i32 produces STIND_I4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I32(42), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I4))
    }

    @Test
    fun `store i64 produces STIND_I8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I64(100L), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I8))
    }

    @Test
    fun `store i8 produces STIND_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I8(1), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I1))
    }

    @Test
    fun `store i16 produces STIND_I2`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I16(100), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I2))
    }

    @Test
    fun `store f32 produces STIND_R4`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.F32(3.14f), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_R4))
    }

    @Test
    fun `store f64 produces STIND_R8`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.F64(2.718), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_R8))
    }

    @Test
    fun `store bool produces STIND_I1`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I1(true), ptr)
            ir.ret()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I1))
    }

    @Test
    fun `load and store round trip`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer))
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I32(42), ptr)
            val loaded = ir.load(Type.I32, ptr)
            ir.ret(loaded)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.STIND_I4))
        assertTrue(hasOpCode(instructions, CilOpCode.LDIND_I4))
    }

    // Calls

    @Test
    fun `call function produces CALL opcode`() {
        val ir = IrBuilder("test", Target.msil())

        ir.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Parameter("x", Type.I32, 0))
        ir.finalizeFunction()

        ir.createFunction("caller", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val result = ir.call("helper", listOf(a), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val callerFn = module.functions.first { it.name == "caller" }
        val bytes = gen.generateMethod(callerFn, module.functions)
        val instructions = CilDisassembler().disassemble(bytes)

        assertTrue(hasOpCode(instructions, CilOpCode.CALL))
    }

    @Test
    fun `call void function no result store`() {
        val ir = IrBuilder("test", Target.msil())

        ir.createFunction("sideEffect", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        ir.createFunction("caller", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.call("sideEffect", emptyList(), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val callerFn = module.functions.first { it.name == "caller" }
        val bytes = gen.generateMethod(callerFn, module.functions)
        val instructions = CilDisassembler().disassemble(bytes)

        assertTrue(hasOpCode(instructions, CilOpCode.CALL))
        assertTrue(hasOpCode(instructions, CilOpCode.RET))
    }

    @Test
    fun `call with multiple arguments`() {
        val ir = IrBuilder("test", Target.msil())

        ir.createFunction("add3", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
        ), Type.I32)
        ir.appendBlock("entry")
        val pa = Parameter("a", Type.I32, 0)
        val pb = Parameter("b", Type.I32, 1)
        val pc = Parameter("c", Type.I32, 2)
        val s1 = ir.add(pa, pb)
        val s2 = ir.add(s1, pc)
        ir.ret(s2)
        ir.finalizeFunction()

        ir.createFunction("caller", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("add3", listOf(Constant.I32(1), Constant.I32(2), Constant.I32(3)), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val callerFn = module.functions.first { it.name == "caller" }
        val bytes = gen.generateMethod(callerFn, module.functions)
        val instructions = CilDisassembler().disassemble(bytes)

        assertTrue(hasOpCode(instructions, CilOpCode.CALL))
    }

    // GC no-ops

    @Test
    fun `gcSafepoint is a no-op`() {
        val instructions = buildMethodAndDisassemble(
            returnType = Type.Void
        ) { ir ->
            ir.gcSafepoint()
            ir.ret()
        }
        assertEquals(1, instructions.size, "gcSafepoint should produce no CIL instructions")
        assertEquals(CilOpCode.RET, instructions[0].opcode)
    }

    @Test
    fun `gcRoot is a no-op`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("ptr", Type.OpaquePointer)),
            returnType = Type.Void
        ) { ir ->
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.gcRoot(ptr)
            ir.ret()
        }
        assertEquals(1, instructions.size, "gcRoot should produce no CIL instructions")
        assertEquals(CilOpCode.RET, instructions[0].opcode)
    }

    @Test
    fun `multiple gcSafepoints are all no-ops`() {
        val instructions = buildMethodAndDisassemble(
            returnType = Type.Void
        ) { ir ->
            ir.gcSafepoint()
            ir.gcSafepoint()
            ir.gcSafepoint()
            ir.ret()
        }
        assertEquals(1, instructions.size)
    }

    // Constants

    @Test
    fun `constant i32 zero produces LDC_I4_0`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(0))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4_0))
    }

    @Test
    fun `constant i32 one produces LDC_I4_1`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(1))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4_1))
    }

    @Test
    fun `constant i32 minus one produces LDC_I4_M1`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(-1))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4_M1))
    }

    @Test
    fun `constant i32 small value produces LDC_I4_S`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(42))
        }
        val ldc = instructions.first { it.opcode == CilOpCode.LDC_I4_S }
        assertEquals(42, ldc.operand)
    }

    @Test
    fun `constant i32 large value produces LDC_I4`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I32(1000000))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4))
    }

    @Test
    fun `constant i64 produces LDC_I8`() {
        val instructions = buildMethodAndDisassemble(returnType = Type.I64) { ir ->
            ir.ret(Constant.I64(9999999999L))
        }
        val ldc = instructions.first { it.opcode == CilOpCode.LDC_I8 }
        assertEquals(9999999999L, ldc.operand)
    }

    @Test
    fun `constant f32 produces LDC_R4`() {
        val instructions = buildMethodAndDisassemble(returnType = Type.F32) { ir ->
            ir.ret(Constant.F32(1.5f))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_R4))
    }

    @Test
    fun `constant f64 produces LDC_R8`() {
        val instructions = buildMethodAndDisassemble(returnType = Type.F64) { ir ->
            ir.ret(Constant.F64(3.14159))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_R8))
    }

    @Test
    fun `constant bool true produces LDC_I4_1`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I1(true))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4_1))
    }

    @Test
    fun `constant bool false produces LDC_I4_0`() {
        val instructions = buildMethodAndDisassemble { ir ->
            ir.ret(Constant.I1(false))
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDC_I4_0))
    }

    @Test
    fun `constant i8 produces LDC_I4`() {
        val instructions = buildMethodAndDisassemble(returnType = Type.I8) { ir ->
            ir.ret(Constant.I8(127))
        }
        assertTrue(instructions.any {
            it.opcode == CilOpCode.LDC_I4_S || it.opcode == CilOpCode.LDC_I4
        })
    }

    @Test
    fun `constant i16 produces LDC_I4`() {
        val instructions = buildMethodAndDisassemble(returnType = Type.I16) { ir ->
            ir.ret(Constant.I16(1000))
        }
        assertTrue(instructions.any {
            it.opcode == CilOpCode.LDC_I4_S || it.opcode == CilOpCode.LDC_I4
        })
    }

    @Test
    fun `constant nullptr produces LDNULL`() {
        val instructions = buildMethodAndDisassemble(
            returnType = Type.OpaquePointer
        ) { ir ->
            ir.ret(Constant.NullPtr)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.LDNULL))
    }

    @Test
    fun `constant i32 values 2 through 8 use short form`() {
        for (i in 2..8) {
            val instructions = buildMethodAndDisassemble { ir ->
                ir.ret(Constant.I32(i))
            }
            val shortOpcodes = listOf(
                CilOpCode.LDC_I4_2, CilOpCode.LDC_I4_3, CilOpCode.LDC_I4_4,
                CilOpCode.LDC_I4_5, CilOpCode.LDC_I4_6, CilOpCode.LDC_I4_7,
                CilOpCode.LDC_I4_8
            )
            assertTrue(
                instructions.any { it.opcode in shortOpcodes || it.opcode == CilOpCode.LDC_I4_S },
                "Constant $i should use a short or optimized form"
            )
        }
    }

    // Multiple Functions

    @Test
    fun `generate with multiple functions produces container format`() {
        val ir = IrBuilder("test", Target.msil())

        ir.createFunction("funcA", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()

        ir.createFunction("funcB", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(2))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val bytes = gen.generate(module)

        // Multi-method format: starts with count (4 bytes big-endian)
        assertTrue(bytes.size > 4)
        val count = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes)).readInt()
        assertEquals(2, count)
    }

    @Test
    fun `single function does not use container format`() {
        val ir = IrBuilder("test", Target.msil())
        ir.createFunction("only", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(99))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val bytes = gen.generate(module)

        // Should be raw CIL bytes, not container format
        val instructions = CilDisassembler().disassemble(bytes)
        assertTrue(instructions.isNotEmpty())
        assertTrue(hasOpCode(instructions, CilOpCode.RET))
    }

    @Test
    fun `generateMethod returns bytes for specific function`() {
        val ir = IrBuilder("test", Target.msil())

        ir.createFunction("ignored", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        ir.createFunction("target", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val targetFn = module.functions.first { it.name == "target" }
        val bytes = gen.generateMethod(targetFn, module.functions)
        val instructions = CilDisassembler().disassemble(bytes)

        assertTrue(hasOpCode(instructions, CilOpCode.RET))
    }

    @Test
    fun `generateMethodAssembler returns assembler`() {
        val ir = IrBuilder("test", Target.msil())
        ir.createFunction("fn", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(5))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val fn = module.functions.first()
        val asm = gen.generateMethodAssembler(fn)
        val bytes = asm.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    // Imports/Externals

    @Test
    fun `external functions are skipped in generate`() {
        val ir = IrBuilder("test", Target.msil())

        ir.declareFunction("externalFn", listOf(Param("x", Type.I32)), Type.I32)

        ir.createFunction("caller", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val result = ir.call("externalFn", listOf(a), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val bytes = gen.generate(module)

        // Should produce bytes for just the caller (single method = raw bytes)
        val instructions = CilDisassembler().disassemble(bytes)
        assertTrue(hasOpCode(instructions, CilOpCode.CALL))
        assertTrue(hasOpCode(instructions, CilOpCode.RET))
    }

    @Test
    fun `targetName is msil`() {
        val gen = CilCodeGenerator()
        assertEquals("msil", gen.targetName)
    }

    // Complex Patterns

    @Test
    fun `chained arithmetic operations`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val c = Parameter("c", Type.I32, 2)
            val sum = ir.add(a, b)
            val product = ir.mul(sum, c)
            ir.ret(product)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
    }

    @Test
    fun `mixed int and float operations via conversion`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32)),
            returnType = Type.I32
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            val f = ir.sitofp(x, Type.F64)
            val doubled = ir.fmul(f, Constant.F64(2.0))
            val back = ir.fptosi(doubled, Type.I32)
            ir.ret(back)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_R8))
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
        assertTrue(hasOpCode(instructions, CilOpCode.CONV_I4))
    }

    @Test
    fun `diamond control flow pattern`() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("diamond", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            val cmp = ir.icmp(ICmpPredicate.SGT, x, Constant.I32(0))
            ir.condBr(cmp, BlockRef("then"), BlockRef("else"))

            ir.appendBlock("then")
            val doubled = ir.mul(x, Constant.I32(2))
            ir.br(BlockRef("merge"))

            ir.appendBlock("else")
            val negated = ir.neg(x)
            ir.br(BlockRef("merge"))

            ir.appendBlock("merge")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertTrue(hasOpCode(instructions, CilOpCode.BRTRUE))
        assertTrue(hasOpCode(instructions, CilOpCode.MUL))
        assertTrue(hasOpCode(instructions, CilOpCode.NEG))
        val brCount = instructions.count { it.opcode == CilOpCode.BR }
        assertTrue(brCount >= 2, "Diamond should have at least 2 BR instructions")
    }

    @Test
    fun `multiple comparisons in sequence`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("a", Type.I32), Param("b", Type.I32))
        ) { ir ->
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val eq = ir.icmp(ICmpPredicate.EQ, a, b)
            val gt = ir.icmp(ICmpPredicate.SGT, a, b)
            val combined = ir.or(eq, gt)
            ir.ret(combined)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.CEQ))
        assertTrue(hasOpCode(instructions, CilOpCode.CGT))
        assertTrue(hasOpCode(instructions, CilOpCode.OR))
    }

    @Test
    fun `arithmetic with constants`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32))
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            val added = ir.add(x, Constant.I32(10))
            val shifted = ir.shl(added, Constant.I32(2))
            ir.ret(shifted)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.ADD))
        assertTrue(hasOpCode(instructions, CilOpCode.SHL))
    }

    @Test
    fun `bitwise complement pattern`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(Param("x", Type.I32))
        ) { ir ->
            val x = Parameter("x", Type.I32, 0)
            val notted = ir.not(x)
            val anded = ir.and(notted, Constant.I32(0xFF))
            ir.ret(anded)
        }
        assertTrue(hasOpCode(instructions, CilOpCode.NOT))
        assertTrue(hasOpCode(instructions, CilOpCode.AND))
    }

    @Test
    fun `ldarg indices for multiple parameters`() {
        val instructions = buildMethodAndDisassemble(
            params = listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            )
        ) { ir ->
            val d = Parameter("d", Type.I32, 3)
            ir.ret(d)
        }
        // ldarg.3 or ldarg.s 3
        assertTrue(instructions.any {
            it.opcode == CilOpCode.LDARG_3 ||
            (it.opcode == CilOpCode.LDARG_S && it.operand == 3L)
        })
    }

    @Test
    fun `parameter at index 5 uses LDARG_S`() {
        val params = (0 until 6).map { Param("p$it", Type.I32) }
        val instructions = buildMethodAndDisassemble(params = params) { ir ->
            val p5 = Parameter("p5", Type.I32, 5)
            ir.ret(p5)
        }
        assertTrue(instructions.any {
            it.opcode == CilOpCode.LDARG_S && it.operand == 5L
        })
    }
}

class CilClassBuilderComprehensiveTest {

    @Test
    fun `build class with single method`() {
        val builder = CilClassBuilder("TestAsm", "MyNamespace.MyClass")
        builder.method("DoWork", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ret()
        }
        val meta = builder.build()
        assertEquals(1, meta.tables.methodDefs.size)
        assertEquals("DoWork", meta.strings.get(meta.tables.methodDefs[0].name))
    }

    @Test
    fun `build class with multiple methods`() {
        val builder = CilClassBuilder("TestAsm", "MathOps")
        builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.ldarg(1); code.add(); code.ret()
        }
        builder.method("Mul", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.ldarg(1); code.mul(); code.ret()
        }
        builder.method("Negate", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.neg(); code.ret()
        }

        val meta = builder.build()
        assertEquals(3, meta.tables.methodDefs.size)
        assertEquals("Add", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("Mul", meta.strings.get(meta.tables.methodDefs[1].name))
        assertEquals("Negate", meta.strings.get(meta.tables.methodDefs[2].name))
    }

    @Test
    fun `field definitions with different access levels`() {
        val builder = CilClassBuilder("TestAsm", "DataClass")
        builder.field("privateField", CilSigType.I4, CilFieldFlags.PRIVATE)
        builder.field("publicField", CilSigType.I4, CilFieldFlags.PUBLIC)
        builder.field("staticField", CilSigType.I8, CilFieldFlags.PUBLIC or CilFieldFlags.STATIC)
        builder.field("readonlyField", CilSigType.R8, CilFieldFlags.PUBLIC or CilFieldFlags.INIT_ONLY)

        val meta = builder.build()
        assertEquals(4, meta.tables.fields.size)
        assertEquals("privateField", meta.strings.get(meta.tables.fields[0].name))
        assertEquals("publicField", meta.strings.get(meta.tables.fields[1].name))
        assertEquals("staticField", meta.strings.get(meta.tables.fields[2].name))
        assertEquals("readonlyField", meta.strings.get(meta.tables.fields[3].name))
    }

    @Test
    fun `field with default private access`() {
        val builder = CilClassBuilder("TestAsm", "Cls")
        builder.field("x", CilSigType.I4)
        val meta = builder.build()
        assertEquals(1, meta.tables.fields.size)
        assertEquals(CilFieldFlags.PRIVATE, meta.tables.fields[0].flags)
    }

    @Test
    fun `field types i4 i8 r4 r8 string object`() {
        val builder = CilClassBuilder("TestAsm", "TypedFields")
        builder.field("intField", CilSigType.I4)
        builder.field("longField", CilSigType.I8)
        builder.field("floatField", CilSigType.R4)
        builder.field("doubleField", CilSigType.R8)
        builder.field("stringField", CilSigType.STRING)
        builder.field("objectField", CilSigType.OBJECT)

        val meta = builder.build()
        assertEquals(6, meta.tables.fields.size)
    }

    @Test
    fun `property accessor pattern with getter and setter methods`() {
        val builder = CilClassBuilder("TestAsm", "PropClass")
        builder.field("_value", CilSigType.I4, CilFieldFlags.PRIVATE)

        // Getter
        builder.method("get_Value",
            CilClassBuilder.instanceSig(CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.SPECIAL_NAME or CilMethodFlags.HIDE_BY_SIG
        ) { code ->
            code.ldarg(0)
            code.ret()
        }

        // Setter
        builder.method("set_Value",
            CilClassBuilder.instanceSig(CilSigType.VOID, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.SPECIAL_NAME or CilMethodFlags.HIDE_BY_SIG
        ) { code ->
            code.ldarg(0)
            code.ldarg(1)
            code.ret()
        }

        val meta = builder.build()
        assertEquals(1, meta.tables.fields.size)
        assertEquals(2, meta.tables.methodDefs.size)
        assertEquals("get_Value", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("set_Value", meta.strings.get(meta.tables.methodDefs[1].name))
    }

    @Test
    fun `interface pattern with abstract methods`() {
        val builder = CilClassBuilder("TestAsm", "IShape")
        builder.flags(0x000000A1) // Interface | Public | Abstract

        builder.method("GetArea",
            CilClassBuilder.instanceSig(CilSigType.R8),
            CilMethodFlags.PUBLIC or CilMethodFlags.VIRTUAL or CilMethodFlags.ABSTRACT
        )
        builder.method("GetPerimeter",
            CilClassBuilder.instanceSig(CilSigType.R8),
            CilMethodFlags.PUBLIC or CilMethodFlags.VIRTUAL or CilMethodFlags.ABSTRACT
        )

        val meta = builder.build()
        assertEquals(2, meta.tables.methodDefs.size)
    }

    @Test
    fun `enum type pattern with literal fields`() {
        val builder = CilClassBuilder("TestAsm", "Color")
        builder.field("value__", CilSigType.I4,
            CilFieldFlags.PUBLIC or 0x0200 or 0x0400) // SpecialName | RTSpecialName for fields
        builder.field("Red", CilSigType.I4,
            CilFieldFlags.PUBLIC or CilFieldFlags.STATIC or CilFieldFlags.LITERAL)
        builder.field("Green", CilSigType.I4,
            CilFieldFlags.PUBLIC or CilFieldFlags.STATIC or CilFieldFlags.LITERAL)
        builder.field("Blue", CilSigType.I4,
            CilFieldFlags.PUBLIC or CilFieldFlags.STATIC or CilFieldFlags.LITERAL)

        val meta = builder.build()
        assertEquals(4, meta.tables.fields.size)
        assertEquals("value__", meta.strings.get(meta.tables.fields[0].name))
        assertEquals("Red", meta.strings.get(meta.tables.fields[1].name))
        assertEquals("Green", meta.strings.get(meta.tables.fields[2].name))
        assertEquals("Blue", meta.strings.get(meta.tables.fields[3].name))
    }

    @Test
    fun `struct type pattern with sealed class flags`() {
        val builder = CilClassBuilder("TestAsm", "Point")
        builder.flags(0x00100109) // Public | Sequential | Sealed | BeforeFieldInit
        builder.field("X", CilSigType.I4, CilFieldFlags.PUBLIC)
        builder.field("Y", CilSigType.I4, CilFieldFlags.PUBLIC)

        val meta = builder.build()
        assertEquals(2, meta.tables.fields.size)
        val classDef = meta.tables.typeDefs[1]
        assertTrue(classDef.flags and 0x00000100 != 0, "Should have Sealed flag")
    }

    @Test
    fun `entry point method sets token`() {
        val builder = CilClassBuilder("TestAsm", "Program")
        builder.method("Main", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ret()
        }
        builder.entryPoint(0)

        val meta = builder.build()
        assertEquals(0x06000001, meta.entryPointToken)
    }

    @Test
    fun `entry point with second method`() {
        val builder = CilClassBuilder("TestAsm", "Program")
        builder.method("Helper", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PRIVATE or CilMethodFlags.STATIC
        ) { code -> code.ret() }
        builder.method("Main", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code -> code.ret() }
        builder.entryPoint(1)

        val meta = builder.build()
        assertEquals(0x06000002, meta.entryPointToken)
    }

    @Test
    fun `no entry point produces zero token`() {
        val builder = CilClassBuilder("TestAsm", "Lib")
        builder.method("DoWork", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code -> code.ret() }

        val meta = builder.build()
        assertEquals(0, meta.entryPointToken)
    }

    @Test
    fun `default assembly ref is mscorlib`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals(1, meta.tables.assemblyRefs.size)
        assertEquals("mscorlib", meta.strings.get(meta.tables.assemblyRefs[0].name))
    }

    @Test
    fun `default type ref is System Object`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals(1, meta.tables.typeRefs.size)
        assertEquals("Object", meta.strings.get(meta.tables.typeRefs[0].name))
        assertEquals("System", meta.strings.get(meta.tables.typeRefs[0].namespace))
    }

    @Test
    fun `additional assembly ref`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.addAssemblyRef("System.Runtime", 6, 0, 0, 0)

        val meta = builder.build()
        assertEquals(2, meta.tables.assemblyRefs.size)
        assertEquals("mscorlib", meta.strings.get(meta.tables.assemblyRefs[0].name))
        assertEquals("System.Runtime", meta.strings.get(meta.tables.assemblyRefs[1].name))
    }

    @Test
    fun `additional type ref`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val idx = builder.addTypeRef(1, "Console", "System")
        val meta = builder.build()
        assertEquals(2, meta.tables.typeRefs.size)
        assertEquals("Console", meta.strings.get(meta.tables.typeRefs[1].name))
        assertEquals("System", meta.strings.get(meta.tables.typeRefs[1].namespace))
        assertEquals(2, idx)
    }

    @Test
    fun `member ref for external method call`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val consoleIdx = builder.addTypeRef(1, "Console", "System")
        val writeLineToken = builder.addMemberRef(consoleIdx, "WriteLine",
            CilClassBuilder.sig(CilSigType.VOID, CilSigType.STRING))

        val meta = builder.build()
        assertEquals(1, meta.tables.memberRefs.size)
        assertEquals("WriteLine", meta.strings.get(meta.tables.memberRefs[0].name))
        assertTrue(writeLineToken.isMemberRef)
    }

    @Test
    fun `pinvoke method has PInvokeImpl flag`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertTrue(meta.tables.methodDefs[0].flags and 0x2000 != 0)
    }

    @Test
    fun `pinvoke creates module ref`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("user32.dll", "MessageBoxA",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I, CilSigType.I, CilSigType.I, CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(1, meta.tables.moduleRefs.size)
        assertEquals("user32.dll", meta.strings.get(meta.tables.moduleRefs[0].name))
    }

    @Test
    fun `pinvoke creates impl map`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("msvcrt.dll", "printf",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(1, meta.tables.implMaps.size)
        assertEquals("printf", meta.strings.get(meta.tables.implMaps[0].importName))
    }

    @Test
    fun `pinvoke with different native name`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("msvcrt.dll", "Print", "puts",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals("Print", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("puts", meta.strings.get(meta.tables.implMaps[0].importName))
    }

    @Test
    fun `multiple pinvokes from same dll reuse module ref`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)
        builder.pinvoke("kernel32.dll", "GetCurrentProcessId",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(1, meta.tables.moduleRefs.size)
        assertEquals(2, meta.tables.implMaps.size)
    }

    @Test
    fun `pinvokes from different dlls create separate module refs`() {
        val builder = CilClassBuilder("TestAsm", "NativeLib")
        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)
        builder.pinvoke("user32.dll", "MessageBoxA",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I, CilSigType.I, CilSigType.I, CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(2, meta.tables.moduleRefs.size)
    }

    @Test
    fun `sig with no params`() {
        val sig = CilClassBuilder.sig(CilSigType.VOID)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01), sig)
    }

    @Test
    fun `sig with one param`() {
        val sig = CilClassBuilder.sig(CilSigType.I4, CilSigType.I4)
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x08, 0x08), sig)
    }

    @Test
    fun `sig with two params`() {
        val sig = CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4)
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x08, 0x08, 0x08), sig)
    }

    @Test
    fun `sig with mixed types`() {
        val sig = CilClassBuilder.sig(CilSigType.R8, CilSigType.I4, CilSigType.I8, CilSigType.R4)
        assertEquals(0x00, sig[0].toInt()) // DEFAULT
        assertEquals(0x03, sig[1].toInt()) // 3 params
        assertEquals(CilSigType.R8.code, sig[2].toInt() and 0xFF) // return type
    }

    @Test
    fun `instance sig has HASTHIS flag`() {
        val sig = CilClassBuilder.instanceSig(CilSigType.VOID)
        assertEquals(0x20, sig[0].toInt() and 0xFF)
    }

    @Test
    fun `instance sig with params`() {
        val sig = CilClassBuilder.instanceSig(CilSigType.VOID, CilSigType.I4, CilSigType.STRING)
        assertEquals(0x20, sig[0].toInt() and 0xFF) // HASTHIS
        assertEquals(0x02, sig[1].toInt()) // 2 params
        assertEquals(CilSigType.VOID.code, sig[2].toInt() and 0xFF)
    }

    @Test
    fun `class with namespace parses correctly`() {
        val builder = CilClassBuilder("TestAsm", "My.Deep.Namespace.ClassName")
        val meta = builder.build()
        val classDef = meta.tables.typeDefs[1]
        assertEquals("ClassName", meta.strings.get(classDef.name))
        assertEquals("My.Deep.Namespace", meta.strings.get(classDef.namespace))
    }

    @Test
    fun `class without namespace`() {
        val builder = CilClassBuilder("TestAsm", "SimpleClass")
        val meta = builder.build()
        val classDef = meta.tables.typeDefs[1]
        assertEquals("SimpleClass", meta.strings.get(classDef.name))
        assertEquals("", meta.strings.get(classDef.namespace))
    }

    @Test
    fun `module type is always present`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals(2, meta.tables.typeDefs.size)
        assertEquals("<Module>", meta.strings.get(meta.tables.typeDefs[0].name))
    }

    @Test
    fun `assembly name appears in assembly table`() {
        val builder = CilClassBuilder("CustomAssembly", "MyClass")
        val meta = builder.build()
        assertEquals(1, meta.tables.assemblies.size)
        assertEquals("CustomAssembly", meta.strings.get(meta.tables.assemblies[0].name))
    }

    @Test
    fun `module name is assembly name plus dll`() {
        val builder = CilClassBuilder("MyLib", "MyClass")
        val meta = builder.build()
        assertEquals(1, meta.tables.modules.size)
        assertEquals("MyLib.dll", meta.strings.get(meta.tables.modules[0].name))
    }

    @Test
    fun `toBytes produces non-empty output`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.method("Run", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code -> code.ret() }
        val bytes = builder.toBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `toBytes starts with BSJB signature`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.method("Run", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code -> code.ret() }

        val bytes = builder.toBytes()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))
    }

    @Test
    fun `method with pre-assembled code bytes`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        // Raw CIL for "ret" = 0x2A
        builder.method("RetOnly", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC,
            byteArrayOf(0x2A)
        )
        val meta = builder.build()
        assertEquals(1, meta.tables.methodDefs.size)
    }

    @Test
    fun `method with no body abstract`() {
        val builder = CilClassBuilder("TestAsm", "AbstractBase")
        builder.method("AbstractMethod",
            CilClassBuilder.instanceSig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.VIRTUAL or CilMethodFlags.ABSTRACT
        )
        val meta = builder.build()
        assertEquals(1, meta.tables.methodDefs.size)
    }

    @Test
    fun `flags builder method returns this for chaining`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val result = builder.flags(0x00100001)
        assertSame(builder, result)
    }

    @Test
    fun `field method returns this for chaining`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val result = builder.field("x", CilSigType.I4)
        assertSame(builder, result)
    }

    @Test
    fun `method builder returns this for chaining`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val result = builder.method("M", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { it.ret() }
        assertSame(builder, result)
    }

    @Test
    fun `entryPoint returns this for chaining`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.method("Main", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { it.ret() }
        val result = builder.entryPoint(0)
        assertSame(builder, result)
    }

    @Test
    fun `addAssemblyRef returns this for chaining`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val result = builder.addAssemblyRef("System.Runtime")
        assertSame(builder, result)
    }

    @Test
    fun `CilSigType has expected code values`() {
        assertEquals(0x01, CilSigType.VOID.code)
        assertEquals(0x02, CilSigType.BOOLEAN.code)
        assertEquals(0x04, CilSigType.I1.code)
        assertEquals(0x05, CilSigType.U1.code)
        assertEquals(0x06, CilSigType.I2.code)
        assertEquals(0x07, CilSigType.U2.code)
        assertEquals(0x08, CilSigType.I4.code)
        assertEquals(0x09, CilSigType.U4.code)
        assertEquals(0x0A, CilSigType.I8.code)
        assertEquals(0x0B, CilSigType.U8.code)
        assertEquals(0x0C, CilSigType.R4.code)
        assertEquals(0x0D, CilSigType.R8.code)
        assertEquals(0x0E, CilSigType.STRING.code)
        assertEquals(0x1C, CilSigType.OBJECT.code)
        assertEquals(0x18, CilSigType.I.code)
        assertEquals(0x19, CilSigType.U.code)
    }

    @Test
    fun `CilMethodFlags has standard values`() {
        assertEquals(0x0006, CilMethodFlags.PUBLIC)
        assertEquals(0x0001, CilMethodFlags.PRIVATE)
        assertEquals(0x0010, CilMethodFlags.STATIC)
        assertEquals(0x0040, CilMethodFlags.VIRTUAL)
        assertEquals(0x0400, CilMethodFlags.ABSTRACT)
        assertEquals(0x0080, CilMethodFlags.HIDE_BY_SIG)
        assertEquals(0x0020, CilMethodFlags.FINAL)
        assertEquals(0x0800, CilMethodFlags.SPECIAL_NAME)
        assertEquals(0x1000, CilMethodFlags.RT_SPECIAL_NAME)
    }

    @Test
    fun `CilFieldFlags has standard values`() {
        assertEquals(0x0006, CilFieldFlags.PUBLIC)
        assertEquals(0x0001, CilFieldFlags.PRIVATE)
        assertEquals(0x0010, CilFieldFlags.STATIC)
        assertEquals(0x0020, CilFieldFlags.INIT_ONLY)
        assertEquals(0x0040, CilFieldFlags.LITERAL)
    }

    @Test
    fun `metadata flags include IL only`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals(1, meta.flags and 1, "Should have ILONLY flag")
    }

    @Test
    fun `metadata runtime version`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals(2, meta.majorRuntimeVersion)
        assertEquals(5, meta.minorRuntimeVersion)
    }

    @Test
    fun `metadata version string`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        assertEquals("v4.0.30319", meta.metadataVersion)
    }
}

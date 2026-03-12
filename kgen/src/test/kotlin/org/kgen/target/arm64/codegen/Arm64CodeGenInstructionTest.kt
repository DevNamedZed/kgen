package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64CodeGenInstructionTest {

    private val disasm = Arm64Disassembler()

    private fun compile(block: IrBuilder.() -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.arm64())
        ir.block()
        val module = ir.build()
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)
        return obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }.data
    }

    private fun compileAndDisassemble(block: IrBuilder.() -> Unit): List<String> {
        val code = compile(block)
        return disasm.disassemble(code).map { it.toString() }
    }

    private fun assertCompiles(block: IrBuilder.() -> Unit) {
        val code = compile(block)
        assertTrue(code.isNotEmpty(), "Should produce non-empty code")
    }

    @Test
    fun `add i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = add(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("add") }, "Should contain add: $lines")
    }

    @Test
    fun `add i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = add(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("add") }, "Should contain add: $lines")
    }

    @Test
    fun `sub i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = sub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `sub i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = sub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `mul i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = mul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mul") }, "Should contain mul: $lines")
    }

    @Test
    fun `mul i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = mul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mul") }, "Should contain mul: $lines")
    }

    @Test
    fun `sdiv i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = sdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "Should contain sdiv: $lines")
    }

    @Test
    fun `sdiv i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = sdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "Should contain sdiv: $lines")
    }

    @Test
    fun `udiv i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = udiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("udiv") }, "Should contain udiv: $lines")
    }

    @Test
    fun `udiv i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = udiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("udiv") }, "Should contain udiv: $lines")
    }

    @Test
    fun `srem i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = srem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `srem i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = srem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `urem i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = urem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `urem i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = urem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `and i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = and(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("and") }, "Should contain and: $lines")
    }

    @Test
    fun `and i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = and(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("and") }, "Should contain and: $lines")
    }

    @Test
    fun `or i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = or(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("orr") }, "Should contain orr: $lines")
    }

    @Test
    fun `or i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = or(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("orr") }, "Should contain orr: $lines")
    }

    @Test
    fun `xor i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = xor(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("eor") }, "Should contain eor: $lines")
    }

    @Test
    fun `xor i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = xor(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("eor") }, "Should contain eor: $lines")
    }

    @Test
    fun `shl i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = shl(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("lsl") }, "Should contain lsl: $lines")
    }

    @Test
    fun `shl i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = shl(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("lsl") }, "Should contain lsl: $lines")
    }

    @Test
    fun `lshr i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = lshr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("lsr") }, "Should contain lsr: $lines")
    }

    @Test
    fun `lshr i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = lshr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("lsr") }, "Should contain lsr: $lines")
    }

    @Test
    fun `ashr i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = ashr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("asr") }, "Should contain asr: $lines")
    }

    @Test
    fun `ashr i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = ashr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("asr") }, "Should contain asr: $lines")
    }

    @Test
    fun `neg i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = neg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("neg") || it.contains("sub") }, "Should contain neg or sub: $lines")
    }

    @Test
    fun `neg i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = neg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("neg") || it.contains("sub") }, "Should contain neg or sub: $lines")
    }

    @Test
    fun `not i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = not(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mvn") || it.contains("orn") || it.contains("eor") }, "Should contain mvn/orn/eor: $lines")
    }

    @Test
    fun `not i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = not(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mvn") || it.contains("orn") || it.contains("eor") }, "Should contain mvn/orn/eor: $lines")
    }

    @Test
    fun `fadd f64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fadd(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
    }

    @Test
    fun `fadd f32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fadd(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
    }

    @Test
    fun `fsub f64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fsub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fsub") }, "Should contain fsub: $lines")
    }

    @Test
    fun `fsub f32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fsub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fsub") }, "Should contain fsub: $lines")
    }

    @Test
    fun `fmul f64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fmul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fmul") }, "Should contain fmul: $lines")
    }

    @Test
    fun `fmul f32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fmul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fmul") }, "Should contain fmul: $lines")
    }

    @Test
    fun `fdiv f64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fdiv") }, "Should contain fdiv: $lines")
    }

    @Test
    fun `fdiv f32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fdiv") }, "Should contain fdiv: $lines")
    }

    @Test
    fun `fneg f64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fneg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fneg") }, "Should contain fneg: $lines")
    }

    @Test
    fun `fneg f32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fneg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fneg") }, "Should contain fneg: $lines")
    }

    @Test
    fun `icmp eq`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ne`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.NE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp sgt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp sge`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp slt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SLT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp sle`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SLE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ugt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.UGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp uge`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.UGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ult`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.ULT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ule`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.ULE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp eq i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp sgt i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp oeq f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ogt f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp olt f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OLT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ole f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OLE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp oge f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp one f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.ONE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ueq f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.UEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp une f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.UNE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ord f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.ORD, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp uno f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.UNO, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp oeq f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `zext i32 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = zext(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sext i32 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = sext(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `trunc i64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = trunc(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = sitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i64 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = sitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i32 to f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = sitofp(p[0], Type.F32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `uitofp i32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = uitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = fptosi(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f64 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = fptosi(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptoui f64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = fptoui(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fpext f32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = fpext(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptrunc f64 to f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val r = fptrunc(p[0], Type.F32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `select i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("csel") }, "Should contain csel: $lines")
    }

    @Test
    fun `select i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and load and store i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.I32)
            store(p[0], slot)
            val r = load(Type.I32, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and load and store i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.I64)
            store(p[0], slot)
            val r = load(Type.I64, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and load and store f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.F64)
            store(p[0], slot)
            val r = load(Type.F64, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `load and store f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.F32)
            store(p[0], slot)
            val r = load(Type.F32, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `call with return value`() {
        assertCompiles {
            declareFunction("other", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("other", listOf(Constant.I32(42)), Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `call void`() {
        assertCompiles {
            declareFunction("sideEffect", listOf(Param("x", Type.I32)), Type.Void)
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            call("sideEffect", listOf(Constant.I32(1)), Type.Void)
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `call with multiple args`() {
        assertCompiles {
            declareFunction("multi", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            ), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("multi", listOf(
                Constant.I32(1), Constant.I32(2),
                Constant.I32(3), Constant.I32(4)
            ), Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ret void`() {
        val lines = compileAndDisassemble {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `ret i32 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(42))
            finalizeFunction()
        }
    }

    @Test
    fun `ret i64 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(100))
            finalizeFunction()
        }
    }

    @Test
    fun `ret f64 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.F64(3.14))
            finalizeFunction()
        }
    }

    @Test
    fun `unconditional br`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            br("target")
            positionAtEnd(appendBlock("target"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `conditional br`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.EQ, p[0], Constant.I32(0))
            condBr(c, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `icmp fused with condBr`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            condBr(c, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(p[0])
            positionAtEnd(appendBlock("else"))
            ret(p[1])
            finalizeFunction()
        }
    }

    @Test
    fun `switch i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            switch(p[0], "default", listOf(
                Constant.I32(0) to "case0",
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2"
            ))
            positionAtEnd(appendBlock("case0"))
            ret(Constant.I32(10))
            positionAtEnd(appendBlock("case1"))
            ret(Constant.I32(20))
            positionAtEnd(appendBlock("case2"))
            ret(Constant.I32(30))
            positionAtEnd(appendBlock("default"))
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    @Test
    fun `gc safepoint`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.Void, gc = "statepoint")
            positionAtEnd(appendBlock("entry"))
            gcSafepoint()
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `gc root`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.Void, gc = "statepoint")
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.OpaquePointer)
            gcRoot(slot)
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `add with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = add(p[0], Constant.I32(10))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sub with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = sub(p[0], Constant.I32(5))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `chain of arithmetic`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(p[0], p[1])
            val prod = mul(sum, p[0])
            val diff = sub(prod, p[1])
            ret(diff)
            finalizeFunction()
        }
    }

    @Test
    fun `chain of float arithmetic`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val sum = fadd(p[0], p[1])
            val prod = fmul(sum, p[0])
            val diff = fsub(prod, p[1])
            ret(diff)
            finalizeFunction()
        }
    }

    @Test
    fun `multiple blocks with branches`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val isZero = icmp(ICmpPredicate.EQ, p[0], Constant.I32(0))
            condBr(isZero, "zero", "nonzero")
            positionAtEnd(appendBlock("zero"))
            ret(Constant.I32(0))
            positionAtEnd(appendBlock("nonzero"))
            val isNeg = icmp(ICmpPredicate.SLT, p[0], Constant.I32(0))
            condBr(isNeg, "negative", "positive")
            positionAtEnd(appendBlock("negative"))
            val negated = neg(p[0])
            ret(negated)
            positionAtEnd(appendBlock("positive"))
            ret(p[0])
            finalizeFunction()
        }
    }

    @Test
    fun `complex control flow diamond`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            condBr(c, "left", "right")
            positionAtEnd(appendBlock("left"))
            val sum = add(p[0], Constant.I32(1))
            br("merge")
            positionAtEnd(appendBlock("right"))
            val diff = sub(p[1], Constant.I32(1))
            br("merge")
            positionAtEnd(appendBlock("merge"))
            ret(p[0])
            finalizeFunction()
        }
    }

    @Test
    fun `multiple functions in module`() {
        assertCompiles {
            val p1 = createFunction("add1", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r1 = add(p1[0], Constant.I32(1))
            ret(r1)
            finalizeFunction()
            val p2 = createFunction("sub1", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r2 = sub(p2[0], Constant.I32(1))
            ret(r2)
            finalizeFunction()
        }
    }

    @Test
    fun `load pointer`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.OpaquePointer)
            positionAtEnd(appendBlock("entry"))
            val r = load(Type.OpaquePointer, p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `store and load pointer`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.OpaquePointer)
            positionAtEnd(appendBlock("entry"))
            val slot = alloca(Type.OpaquePointer)
            store(p[0], slot)
            val r = load(Type.OpaquePointer, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `select f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.OGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `zext i1 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sext i1 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = sext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `uitofp i64 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val r = uitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptoui f64 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val r = fptoui(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f32 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = fptosi(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ugt f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.UGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ult f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.ULT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ule f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.ULE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp uge f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val c = fcmp(FCmpPredicate.UGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `switch i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            switch(p[0], "default", listOf(
                Constant.I64(0) to "case0",
                Constant.I64(1) to "case1"
            ))
            positionAtEnd(appendBlock("case0"))
            ret(Constant.I64(100))
            positionAtEnd(appendBlock("case1"))
            ret(Constant.I64(200))
            positionAtEnd(appendBlock("default"))
            ret(Constant.I64(-1))
            finalizeFunction()
        }
    }

    @Test
    fun `intTrunc i64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = trunc(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `load i32 from pointer param`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = load(Type.I32, p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `store i32 to pointer param`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("val", Type.I32), Param("ptr", Type.OpaquePointer)), Type.Void)
            positionAtEnd(appendBlock("entry"))
            store(p[0], p[1])
            ret(null)
            finalizeFunction()
        }
    }
}

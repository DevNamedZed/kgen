package org.kgen.target.x86.codegen

import org.kgen.target.x86.disasm.X86Disassembler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class X86CodeGenInstructionTest {

    private val disasm = X86Disassembler()

    private fun compile(block: ModuleBuilder.() -> Unit): ByteArray {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        val module = ir.build()
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)
        return obj.sections.first { it.name == ".text" }.data
    }

    private fun compileAndDisassemble(block: ModuleBuilder.() -> Unit): List<String> {
        val code = compile(block)
        return disasm.disassembleRaw(code).map { it.text() }
    }

    private fun assertCompiles(block: ModuleBuilder.() -> Unit) {
        val code = compile(block)
        assertTrue(code.isNotEmpty(), "Should produce non-empty code")
    }

    @Test
    fun `add i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = add(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("add") }, "Should contain add: $lines")
    }

    @Test
    fun `add i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = add(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("add") }, "Should contain add: $lines")
    }

    @Test
    fun `sub i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `sub i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = sub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `mul i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `mul i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = mul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sdiv i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sdiv i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = sdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `udiv i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = udiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `udiv i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = udiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `srem i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = srem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `srem i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = srem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `urem i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = urem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `urem i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = urem(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `and i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
            val r = or(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("or") }, "Should contain or: $lines")
    }

    @Test
    fun `or i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = or(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("or") }, "Should contain or: $lines")
    }

    @Test
    fun `xor i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = xor(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("xor") }, "Should contain xor: $lines")
    }

    @Test
    fun `xor i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = xor(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("xor") }, "Should contain xor: $lines")
    }

    @Test
    fun `shl i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = shl(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("shl") }, "Should contain shl: $lines")
    }

    @Test
    fun `shl i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = shl(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("shl") }, "Should contain shl: $lines")
    }

    @Test
    fun `lshr i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = lshr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("shr") }, "Should contain shr: $lines")
    }

    @Test
    fun `lshr i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = lshr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("shr") }, "Should contain shr: $lines")
    }

    @Test
    fun `ashr i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = ashr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sar") }, "Should contain sar: $lines")
    }

    @Test
    fun `ashr i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = ashr(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sar") }, "Should contain sar: $lines")
    }

    @Test
    fun `neg i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = neg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("neg") }, "Should contain neg: $lines")
    }

    @Test
    fun `neg i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = neg(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("neg") }, "Should contain neg: $lines")
    }

    @Test
    fun `not i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = not(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("not") }, "Should contain not: $lines")
    }

    @Test
    fun `not i64`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = not(p[0])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("not") }, "Should contain not: $lines")
    }

    @Test
    fun `fadd f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = fadd(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fsub f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = fsub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fmul f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = fmul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fdiv f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = fdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fneg f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = fneg(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fadd f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            val r = fadd(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fsub f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            val r = fsub(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fmul f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            val r = fmul(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fdiv f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            val r = fdiv(p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fneg f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.F32)
            appendBlock("entry")
            val r = fneg(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp eq`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp oeq`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ogt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp olt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OLT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ole`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OLE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp one`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.ONE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ueq`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.UEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp une`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.UNE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ord`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.ORD, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp uno`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.UNO, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `zext i32 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val r = zext(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sext i32 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val r = sext(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `trunc i64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            val r = trunc(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            val r = sitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `uitofp i32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            val r = uitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            appendBlock("entry")
            val r = fptosi(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptoui f64 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I32)
            appendBlock("entry")
            val r = fptoui(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fpext f32 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.F64)
            appendBlock("entry")
            val r = fpext(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptrunc f64 to f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F32)
            appendBlock("entry")
            val r = fptrunc(p[0], Type.F32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ptrtoint ptr to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.OpaquePointer)), Type.I64)
            appendBlock("entry")
            val r = ptrtoint(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `inttoptr i64 to ptr`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.OpaquePointer)
            appendBlock("entry")
            val r = inttoptr(p[0], Type.OpaquePointer)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `bitcast i64 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            appendBlock("entry")
            val r = bitcast(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `select i32`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmov") }, "Should contain cmov: $lines")
    }

    @Test
    fun `select i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and store and load i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val slot = alloca(Type.I32)
            store(p[0], slot)
            val r = load(Type.I32, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and store and load i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val slot = alloca(Type.I64)
            store(p[0], slot)
            val r = load(Type.I64, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca and store and load f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val slot = alloca(Type.F64)
            store(p[0], slot)
            val r = load(Type.F64, slot)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `call with return value`() {
        assertCompiles {
            declareFunction("other", listOf(Param("x", Type.I32)), Type.I32)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
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
            appendBlock("entry")
            call("sideEffect", listOf(Constant.I32(1)), Type.Void)
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `ret void`() {
        val lines = compileAndDisassemble {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `ret i32 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(42))
            finalizeFunction()
        }
    }

    @Test
    fun `ret i64 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(100))
            finalizeFunction()
        }
    }

    @Test
    fun `ret f64 constant`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            ret(Constant.F64(3.14))
            finalizeFunction()
        }
    }

    @Test
    fun `unconditional br`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("target"))
            appendBlock("target")
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `conditional br`() {
        val lines = compileAndDisassemble {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.EQ, p[0], Constant.I32(0))
            condBr(c, BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(Constant.I32(1))
            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("j") && !it.startsWith("jmp") }, "Should contain conditional jump: $lines")
    }

    @Test
    fun `switch i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(p[0], "default", listOf(
                Constant.I32(0) to "case0",
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2"
            ))
            appendBlock("case0")
            ret(Constant.I32(10))
            appendBlock("case1")
            ret(Constant.I32(20))
            appendBlock("case2")
            ret(Constant.I32(30))
            appendBlock("default")
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    @Test
    fun `gep struct field`() {
        assertCompiles {
            val structType = Type.Struct("point", listOf(Type.I32, Type.I32))
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val fieldPtr = gep(structType, p[0], Constant.I32(0), Constant.I32(1))
            val v = load(Type.I32, fieldPtr)
            ret(v)
            finalizeFunction()
        }
    }

    @Test
    fun `gep array element`() {
        assertCompiles {
            val arrType = Type.Array(Type.I32, 10)
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val elemPtr = gep(arrType, p[0], Constant.I32(0), Constant.I32(3))
            val v = load(Type.I32, elemPtr)
            ret(v)
            finalizeFunction()
        }
    }

    @Test
    fun `extractValue from struct`() {
        assertCompiles {
            val structType = Type.Struct("pair", listOf(Type.I32, Type.I64))
            declareFunction("getPair", emptyList(), structType)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val pair = call("getPair", emptyList(), structType)!!
            val first = extractValue(pair, 0)
            ret(first)
            finalizeFunction()
        }
    }

    @Test
    fun `insertValue into struct`() {
        assertCompiles {
            val structType = Type.Struct("pair", listOf(Type.I32, Type.I32))
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), structType)
            appendBlock("entry")
            val undef = Constant.Undef(structType)
            val s1 = insertValue(undef, p[0], 0)
            val s2 = insertValue(s1, p[1], 1)
            ret(s2)
            finalizeFunction()
        }
    }

    @Test
    fun `ctlz i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = ctlz(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ctlz i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = ctlz(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `cttz i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = cttz(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `cttz i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = cttz(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ctpop i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = ctpop(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ctpop i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = ctpop(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `bswap i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = bswap(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `bswap i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val r = bswap(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sqrt f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = sqrt(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ceil f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = ceil(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `floor f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = floor(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `round f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val r = round(p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `memcpy`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memcpy(p[0], p[1], Constant.I64(16))
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `memset`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("dst", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memset(p[0], Constant.I8(0), Constant.I64(32))
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `memmove`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            memmove(p[0], p[1], Constant.I64(16))
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `trap`() {
        val lines = compileAndDisassemble {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            trap()
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("int3") }, "Should contain int3: $lines")
    }

    @Test
    fun `debugTrap`() {
        val lines = compileAndDisassemble {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            debugTrap()
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("int3") }, "Should contain int3: $lines")
    }

    @Test
    fun `unreachable`() {
        // Codegen emits ud2 (0x0F 0x0B) but the disassembler doesn't decode it yet, so verify compilation only
        assertCompiles {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            unreachable()
            finalizeFunction()
        }
    }

    @Test
    fun `gc safepoint`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.Void, gc = "statepoint")
            appendBlock("entry")
            gcSafepoint()
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `gc root`() {
        assertCompiles {
            createFunction("f", emptyList(), Type.Void, gc = "statepoint")
            appendBlock("entry")
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
            appendBlock("entry")
            val r = add(p[0], Constant.I32(10))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sub with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = sub(p[0], Constant.I32(5))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `mul with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = mul(p[0], Constant.I32(3))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `and with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = and(p[0], Constant.I32(0xFF))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `or with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = or(p[0], Constant.I32(0x80))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `xor with constant operand`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = xor(p[0], Constant.I32(0xFF))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `shl with constant shift amount`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = shl(p[0], Constant.I32(4))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `lshr with constant shift amount`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = lshr(p[0], Constant.I32(4))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `ashr with constant shift amount`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val r = ashr(p[0], Constant.I32(4))
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp fused with condBr`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            condBr(c, BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(p[0])
            appendBlock("else")
            ret(p[1])
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp oge`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ugt`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.UGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ult`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.ULT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ule`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.ULE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp uge`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.UGE, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `load and store pointer`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.OpaquePointer)
            appendBlock("entry")
            val slot = alloca(Type.OpaquePointer)
            store(p[0], slot)
            val r = load(Type.OpaquePointer, slot)
            ret(r)
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
            appendBlock("entry")
            val r = call("multi", listOf(
                Constant.I32(1), Constant.I32(2),
                Constant.I32(3), Constant.I32(4)
            ), Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i64 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            appendBlock("entry")
            val r = sitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f64 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            appendBlock("entry")
            val r = fptosi(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `chain of arithmetic`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
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
            appendBlock("entry")
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
            appendBlock("entry")
            val isZero = icmp(ICmpPredicate.EQ, p[0], Constant.I32(0))
            condBr(isZero, BlockRef("zero"), BlockRef("nonzero"))
            appendBlock("zero")
            ret(Constant.I32(0))
            appendBlock("nonzero")
            val isNeg = icmp(ICmpPredicate.SLT, p[0], Constant.I32(0))
            condBr(isNeg, BlockRef("negative"), BlockRef("positive"))
            appendBlock("negative")
            val negated = neg(p[0])
            ret(negated)
            appendBlock("positive")
            ret(p[0])
            finalizeFunction()
        }
    }

    @Test
    fun `vastart`() {
        assertCompiles {
            createFunction("f", listOf(Param("n", Type.I32)), Type.Void, isVarArg = true)
            appendBlock("entry")
            val ap = alloca(Type.I64)
            vaStart(ap)
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `zext i1 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
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
            appendBlock("entry")
            val c = icmp(ICmpPredicate.EQ, p[0], p[1])
            val r = sext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `load f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.F32)
            appendBlock("entry")
            val r = load(Type.F32, p[0])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `store f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("val", Type.F32), Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            store(p[0], p[1])
            ret(null)
            finalizeFunction()
        }
    }

    @Test
    fun `select f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OGT, p[0], p[1])
            val r = select(c, p[0], p[1])
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp f32 oeq`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            appendBlock("entry")
            val c = fcmp(FCmpPredicate.OEQ, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `gep with variable index`() {
        assertCompiles {
            val arrType = Type.Array(Type.I32, 100)
            val p = createFunction("f", listOf(Param("ptr", Type.OpaquePointer), Param("idx", Type.I64)), Type.I32)
            appendBlock("entry")
            val elemPtr = gep(arrType, p[0], Constant.I32(0), p[1])
            val v = load(Type.I32, elemPtr)
            ret(v)
            finalizeFunction()
        }
    }

    @Test
    fun `switch i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            switch(p[0], "default", listOf(
                Constant.I64(0) to "case0",
                Constant.I64(1) to "case1"
            ))
            appendBlock("case0")
            ret(Constant.I64(100))
            appendBlock("case1")
            ret(Constant.I64(200))
            appendBlock("default")
            ret(Constant.I64(-1))
            finalizeFunction()
        }
    }

    @Test
    fun `uitofp i64 to f64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64)), Type.F64)
            appendBlock("entry")
            val r = uitofp(p[0], Type.F64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptoui f64 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            appendBlock("entry")
            val r = fptoui(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i32 to f32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32)), Type.F32)
            appendBlock("entry")
            val r = sitofp(p[0], Type.F32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f32 to i32`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F32)), Type.I32)
            appendBlock("entry")
            val r = fptosi(p[0], Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `bitcast f64 to i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.F64)), Type.I64)
            appendBlock("entry")
            val r = bitcast(p[0], Type.I64)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `complex control flow diamond`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            condBr(c, BlockRef("left"), BlockRef("right"))
            appendBlock("left")
            val sum = add(p[0], Constant.I32(1))
            br(BlockRef("merge"))
            appendBlock("right")
            val diff = sub(p[1], Constant.I32(1))
            br(BlockRef("merge"))
            appendBlock("merge")
            ret(p[0])
            finalizeFunction()
        }
    }

    @Test
    fun `multiple functions in module`() {
        assertCompiles {
            val p1 = createFunction("add1", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r1 = add(p1[0], Constant.I32(1))
            ret(r1)
            finalizeFunction()
            val p2 = createFunction("sub1", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val r2 = sub(p2[0], Constant.I32(1))
            ret(r2)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp sgt i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SGT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp slt i64`() {
        assertCompiles {
            val p = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            appendBlock("entry")
            val c = icmp(ICmpPredicate.SLT, p[0], p[1])
            val r = zext(c, Type.I32)
            ret(r)
            finalizeFunction()
        }
    }
}

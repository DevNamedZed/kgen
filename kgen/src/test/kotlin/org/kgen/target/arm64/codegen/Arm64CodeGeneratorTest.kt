package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.ir.*
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.build.IrBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64CodeGeneratorTest {

    private val disasm = Arm64Disassembler()

    private fun buildAndDisassemble(block: IrBuilder.() -> Unit): List<String> {
        val ir = IrBuilder("test", Target.arm64())
        ir.block()
        val module = ir.build()
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val textSection = obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }
        val instructions = disasm.disassemble(textSection.data)
        return instructions.map { it.toString() }
    }

    @Test
    fun `generates add function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        // add instruction for 32-bit params uses X registers (ABI passes in X regs)
        val addLines = lines.filter { it.startsWith("add") }
        assertTrue(addLines.size >= 2, "Should have add instructions (prologue + body): $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates sub function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("subtract", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = sub(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `generates mul function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("multiply", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = mul(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mul") }, "Should contain mul: $lines")
    }

    @Test
    fun `generates sdiv function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("divide", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = sdiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "Should contain sdiv: $lines")
    }

    @Test
    fun `generates select with compare`() {
        val lines = buildAndDisassemble {
            val params = createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csel") }, "Should contain csel: $lines")
    }

    @Test
    fun `generates 64-bit add`() {
        val lines = buildAndDisassemble {
            val params = createFunction("add64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = add(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("add") && it.contains("x") }, "Should use X registers: $lines")
    }

    @Test
    fun `generates void return`() {
        val lines = buildAndDisassemble {
            createFunction("doNothing", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("ret") }, "Should have ret: $lines")
        assertTrue(lines.any { it.contains("stp") }, "Should have prologue: $lines")
        assertTrue(lines.any { it.contains("ldp") }, "Should have epilogue: $lines")
    }

    @Test
    fun `generates call`() {
        val lines = buildAndDisassemble {
            declareFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            val params = createFunction("caller", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("helper", listOf(params[0]), Type.I32)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("bl") }, "Should contain bl (call): $lines")
    }

    @Test
    fun `produces valid object file`() {
        val ir = IrBuilder("test", Target.arm64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(params[0], params[1]))
        ir.finalizeFunction()
        val module = ir.build()
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)

        val textSection = obj.sections.find { it.name == ".text" }
        assertNotNull(textSection, "Should have .text section")
        assertTrue(textSection!!.data.isNotEmpty(), "Should have code")

        val addSym = obj.symbols.firstOrNull { it.name == "add" }
        assertNotNull(addSym, "Should have 'add' symbol")
        assertEquals(org.kgen.binary.SymbolKind.FUNCTION, addSym!!.kind)
    }

    @Test
    fun `generates ELF object bytes`() {
        val ir = IrBuilder("test", Target.arm64())
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()
        val module = ir.build()
        val gen = Arm64CodeGenerator()
        val bytes = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.OBJECT))

        assertEquals(0x7F, bytes[0].toInt() and 0xFF)
        assertEquals('E'.code, bytes[1].toInt() and 0xFF)
        assertEquals('L'.code, bytes[2].toInt() and 0xFF)
        assertEquals('F'.code, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun `generates logical operations`() {
        val lines = buildAndDisassemble {
            val params = createFunction("bitops", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val x = and(params[0], params[1])
            val y = or(x, params[1])
            val z = xor(y, params[0])
            ret(z)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("and") }, "Should contain and: $lines")
        assertTrue(lines.any { it.contains("orr") }, "Should contain orr: $lines")
        assertTrue(lines.any { it.contains("eor") }, "Should contain eor: $lines")
    }

    @Test
    fun `generates shift operations`() {
        val lines = buildAndDisassemble {
            val params = createFunction("shifts", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val x = shl(params[0], params[1])
            val y = lshr(x, params[1])
            ret(y)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("lsl") }, "Should contain lsl: $lines")
        assertTrue(lines.any { it.contains("lsr") }, "Should contain lsr: $lines")
    }

    @Test
    fun `generates fadd function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fadd_test", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fadd(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates fsub function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fsub_test", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fsub(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fsub") }, "Should contain fsub: $lines")
    }

    @Test
    fun `generates fmul and fdiv`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fmuld", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val product = fmul(params[0], params[1])
            val result = fdiv(product, params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fmul") }, "Should contain fmul: $lines")
        assertTrue(lines.any { it.contains("fdiv") }, "Should contain fdiv: $lines")
    }

    @Test
    fun `generates fneg`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fneg_test", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fneg(params[0])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fneg") }, "Should contain fneg: $lines")
    }

    @Test
    fun `generates sitofp conversion`() {
        val lines = buildAndDisassemble {
            val params = createFunction("int_to_double", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            val result = sitofp(params[0], Type.F64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("scvtf") }, "Should contain scvtf: $lines")
    }

    @Test
    fun `generates fptosi conversion`() {
        val lines = buildAndDisassemble {
            val params = createFunction("double_to_int", listOf(Param("a", Type.F64)), Type.I32)
            appendBlock("entry")
            val result = fptosi(params[0], Type.I32)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcvtzs") }, "Should contain fcvtzs: $lines")
    }

    @Test
    fun `generates fpext f32 to f64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("float_to_double", listOf(Param("a", Type.F32)), Type.F64)
            appendBlock("entry")
            val result = fpext(params[0], Type.F64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcvt") }, "Should contain fcvt: $lines")
    }

    @Test
    fun `generates fptrunc f64 to f32`() {
        val lines = buildAndDisassemble {
            val params = createFunction("double_to_float", listOf(Param("a", Type.F64)), Type.F32)
            appendBlock("entry")
            val result = fptrunc(params[0], Type.F32)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcvt") }, "Should contain fcvt: $lines")
    }

    @Test
    fun `generates fcmp`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fcmp_test", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            appendBlock("entry")
            val result = fcmp(FCmpPredicate.OLT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcmp") }, "Should contain fcmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates fp constant loading`() {
        val lines = buildAndDisassemble {
            val params = createFunction("add_const", listOf(Param("a", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fadd(params[0], Constant.F64(3.14))
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fmov") }, "Should contain fmov for constant load: $lines")
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
    }

    @Test
    fun `generates fp call with fp args and return`() {
        val lines = buildAndDisassemble {
            declareFunction("compute", listOf(Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
            val params = createFunction("caller", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = call("compute", listOf(params[0], params[1]), Type.F64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("bl") }, "Should contain bl (call): $lines")
    }

    @Test
    fun `generates neg instruction`() {
        val lines = buildAndDisassemble {
            val params = createFunction("neg", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = neg(params[0])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("neg") }, "Should contain neg: $lines")
    }

    @Test
    fun `generates not instruction`() {
        val lines = buildAndDisassemble {
            val params = createFunction("bitnot", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = not(params[0])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("eor") }, "Should contain eor (NOT is eor with -1): $lines")
    }

    @Test
    fun `generates intTrunc`() {
        val lines = buildAndDisassemble {
            val params = createFunction("narrow", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            val result = trunc(params[0], Type.I32)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("ret") }, "Should compile and contain ret: $lines")
    }

    @Test
    fun `generates switch`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sw", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(params[0], "default", listOf(
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2"
            ))

            appendBlock("case1")
            ret(Constant.I32(10))

            appendBlock("case2")
            ret(Constant.I32(20))

            appendBlock("default")
            ret(Constant.I32(0))

            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp for case comparison: $lines")
    }
}

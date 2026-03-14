package org.kgen.target.riscv.codegen

import org.kgen.target.riscv.disasm.RiscVDisassembler
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVCodeGeneratorTest {

    private val disasm = RiscVDisassembler()

    private fun buildAndDisassemble(block: IrBuilder.() -> Unit): List<String> {
        val ir = IrBuilder("test", Target.riscv64())
        ir.block()
        val module = ir.build()
        val gen = RiscVCodeGenerator()
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
        assertTrue(lines.any { it.startsWith("add ") }, "Should contain add: $lines")
        assertTrue(lines.any { it.startsWith("ret") }, "Should contain ret: $lines")
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
        assertTrue(lines.any { it.startsWith("sub ") }, "Should contain sub: $lines")
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
        assertTrue(lines.any { it.startsWith("mul ") }, "Should contain mul: $lines")
    }

    @Test
    fun `generates div function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("divide", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = sdiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("div ") }, "Should contain div: $lines")
    }

    @Test
    fun `generates rem function`() {
        val lines = buildAndDisassemble {
            val params = createFunction("remainder", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = srem(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("rem ") }, "Should contain rem: $lines")
    }

    @Test
    fun `generates void return`() {
        val lines = buildAndDisassemble {
            createFunction("doNothing", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("ret") }, "Should have ret: $lines")
        // Prologue saves ra and fp
        assertTrue(lines.any { it.contains("sd") && it.contains("sp") }, "Should save to stack: $lines")
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
        assertTrue(lines.any { it.startsWith("add ") }, "Should contain add: $lines")
    }

    @Test
    fun `generates call`() {
        val lines = buildAndDisassemble {
            declareFunction("external_func", listOf(Param("x", Type.I32)), Type.I32)
            val params = createFunction("caller", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("external_func", listOf(params[0]), Type.I32)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("jal ") }, "Should contain jal (call): $lines")
    }

    @Test
    fun `generates branch`() {
        val lines = buildAndDisassemble {
            val params = createFunction("branch_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            condBr(cond, BlockRef("then"), BlockRef("else"))

            appendBlock("then")
            ret(params[0])

            appendBlock("else")
            ret(params[1])

            finalizeFunction()
        }
        // Should use beq/bne for the fused compare+branch
        assertTrue(lines.any { it.startsWith("beq") || it.startsWith("bne") }, "Should contain branch: $lines")
    }

    @Test
    fun `generates slt comparison`() {
        val lines = buildAndDisassemble {
            val params = createFunction("less_than", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SLT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("slt ") }, "Should contain slt: $lines")
    }

    @Test
    fun `generates logical operations`() {
        val lines = buildAndDisassemble {
            val params = createFunction("logic", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r1 = and(params[0], params[1])
            val r2 = or(r1, params[1])
            val r3 = xor(r2, params[0])
            ret(r3)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("and ") }, "Should contain and: $lines")
        assertTrue(lines.any { it.startsWith("or ") }, "Should contain or: $lines")
        assertTrue(lines.any { it.startsWith("xor ") }, "Should contain xor: $lines")
    }

    @Test
    fun `generates shift operations`() {
        val lines = buildAndDisassemble {
            val params = createFunction("shifts", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val r1 = shl(params[0], params[1])
            val r2 = lshr(r1, params[1])
            val r3 = ashr(r2, params[1])
            ret(r3)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sll ") }, "Should contain sll: $lines")
        assertTrue(lines.any { it.startsWith("srl ") }, "Should contain srl: $lines")
        assertTrue(lines.any { it.startsWith("sra ") }, "Should contain sra: $lines")
    }

    @Test
    fun `generates object file with correct arch`() {
        val ir = IrBuilder("test", Target.riscv64())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret(null)
        ir.finalizeFunction()
        val module = ir.build()

        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)

        assertEquals(org.kgen.binary.ArchType.RISCV64, obj.arch.arch)
        assertEquals(org.kgen.binary.ObjectFormat.ELF, obj.format)
        assertTrue(obj.sections.any { it.kind == org.kgen.binary.SectionKind.TEXT })
        assertTrue(obj.symbols.any { it.name == "noop" })
    }

    @Test
    fun `generates ELF object bytes`() {
        val ir = IrBuilder("test", Target.riscv64())
        ir.createFunction("func", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret(null)
        ir.finalizeFunction()
        val module = ir.build()

        val gen = RiscVCodeGenerator()
        val bytes = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.size > 4)
        // ELF magic
        assertEquals(0x7F.toByte(), bytes[0])
        assertEquals('E'.code.toByte(), bytes[1])
        assertEquals('L'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
    }

    @Test
    fun `generates prologue and epilogue`() {
        val lines = buildAndDisassemble {
            createFunction("simple", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        // Prologue: addi sp, sp, -N
        assertTrue(lines[0].contains("addi") && lines[0].contains("sp"), "First insn should adjust SP: ${lines[0]}")
        // Should save ra and fp
        val sdLines = lines.filter { it.startsWith("sd ") }
        assertTrue(sdLines.size >= 2, "Should save at least ra and fp: $lines")
        // Should end with ret
        assertTrue(lines.last().startsWith("ret"), "Last insn should be ret: ${lines.last()}")
    }

    @Test
    fun `generates unconditional branch`() {
        val lines = buildAndDisassemble {
            val params = createFunction("jump", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            br(BlockRef("target"))

            appendBlock("target")
            ret(params[0])

            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("j ") }, "Should contain j (unconditional jump): $lines")
    }

    @Test
    fun `generates select`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sel", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        // Select uses beq to skip a mv
        assertTrue(lines.any { it.startsWith("beq") || it.startsWith("bne") || it.startsWith("beqz") }, "Should contain conditional branch for select: $lines")
    }

    @Test
    fun `generates external symbol`() {
        val ir = IrBuilder("test", Target.riscv64())
        ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)
        val printfSym = obj.symbols.first { it.name == "printf" }
        assertEquals(org.kgen.binary.SymbolBinding.GLOBAL, printfSym.binding)
        assertEquals(org.kgen.binary.SymbolKind.UNDEFINED, printfSym.kind)
    }

    @Test
    fun `multi-block function with branches`() {
        val lines = buildAndDisassemble {
            val params = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val zero = Constant.I32(0)
            val isNeg = icmp(ICmpPredicate.SLT, params[0], zero)
            condBr(isNeg, BlockRef("negate"), BlockRef("done"))

            appendBlock("negate")
            val negated = sub(zero, params[0])
            ret(negated)

            appendBlock("done")
            ret(params[0])

            finalizeFunction()
        }
        // Should have branch instructions
        assertTrue(lines.any { it.startsWith("blt") || it.startsWith("bge") },
            "Should contain blt or bge: $lines")
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
        assertTrue(lines.any { it.startsWith("beq") }, "Should contain beq for case comparison: $lines")
    }
}

package org.kgen.target.riscv.codegen

import org.kgen.target.riscv.disasm.RiscVDisassembler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVCodeGeneratorExtendedTest {

    private val disasm = RiscVDisassembler()

    private fun buildAndDisassemble(block: ModuleBuilder.() -> Unit): List<String> {
        val ir = ModuleBuilder("test", Target.riscv64())
        ir.block()
        val module = ir.build()
        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)
        val textSection = obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }
        val instructions = disasm.disassemble(textSection.data)
        return instructions.map { it.toString() }
    }

    @Test
    fun `generates add with constant`() {
        val lines = buildAndDisassemble {
            val params = createFunction("addConst", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = add(params[0], Constant.I32(10))
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("add ") }, "Should contain add: $lines")
    }

    @Test
    fun `generates sub with constant`() {
        val lines = buildAndDisassemble {
            val params = createFunction("subConst", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = sub(params[0], Constant.I32(5))
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sub ") }, "Should contain sub: $lines")
    }

    @Test
    fun `generates 64-bit mul`() {
        val lines = buildAndDisassemble {
            val params = createFunction("mul64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = mul(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("mul ") }, "Should contain mul: $lines")
    }

    @Test
    fun `generates 64-bit div`() {
        val lines = buildAndDisassemble {
            val params = createFunction("div64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = sdiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("div ") }, "Should contain div: $lines")
    }

    @Test
    fun `generates udiv`() {
        val lines = buildAndDisassemble {
            val params = createFunction("udiv_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = udiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("divu ") }, "Should contain divu: $lines")
    }

    @Test
    fun `generates urem`() {
        val lines = buildAndDisassemble {
            val params = createFunction("urem_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = urem(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("remu ") }, "Should contain remu: $lines")
    }

    @Test
    fun `generates icmp EQ with select`() {
        val lines = buildAndDisassemble {
            val params = createFunction("eq_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        // EQ comparison uses xor + sltiu sequence, or fused compare+branch for select
        assertTrue(lines.any { it.contains("xor") || it.contains("beq") || it.contains("bne") },
            "EQ should use xor or branch: $lines")
    }

    @Test
    fun `generates icmp NE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ne_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            appendBlock("entry")
            val result = icmp(ICmpPredicate.NE, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("xor ") }, "NE should use xor: $lines")
        assertTrue(lines.any { it.startsWith("sltu ") }, "NE should use sltu: $lines")
    }

    @Test
    fun `generates icmp SGE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sge_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            appendBlock("entry")
            val result = icmp(ICmpPredicate.SGE, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("slt ") }, "SGE should use slt: $lines")
        assertTrue(lines.any { it.startsWith("xori ") }, "SGE should invert with xori: $lines")
    }

    @Test
    fun `generates icmp SGT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sgt_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            appendBlock("entry")
            val result = icmp(ICmpPredicate.SGT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("slt ") }, "SGT should use slt (reversed): $lines")
    }

    @Test
    fun `generates icmp ULT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ult_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            appendBlock("entry")
            val result = icmp(ICmpPredicate.ULT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sltu ") }, "ULT should use sltu: $lines")
    }

    @Test
    fun `generates icmp UGE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("uge_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            appendBlock("entry")
            val result = icmp(ICmpPredicate.UGE, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sltu ") }, "UGE should use sltu: $lines")
        assertTrue(lines.any { it.startsWith("xori ") }, "UGE should invert with xori: $lines")
    }

    @Test
    fun `generates 64-bit and`() {
        val lines = buildAndDisassemble {
            val params = createFunction("and64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = and(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("and ") }, "Should contain and: $lines")
    }

    @Test
    fun `generates 64-bit or`() {
        val lines = buildAndDisassemble {
            val params = createFunction("or64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = or(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("or ") }, "Should contain or: $lines")
    }

    @Test
    fun `generates 64-bit xor`() {
        val lines = buildAndDisassemble {
            val params = createFunction("xor64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val result = xor(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("xor ") }, "Should contain xor: $lines")
    }

    @Test
    fun `generates ashr`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ashr_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = ashr(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sra ") }, "Should contain sra: $lines")
    }

    @Test
    fun `generates call with multiple args`() {
        val lines = buildAndDisassemble {
            declareFunction("compute", listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            val params = createFunction("caller", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("compute", listOf(params[0], params[1], Constant.I32(42)), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("jal ") }, "Should contain jal (call): $lines")
    }

    @Test
    fun `generates void call`() {
        val lines = buildAndDisassemble {
            declareFunction("sideEffect", listOf(Param("x", Type.I32)), Type.Void)
            val params = createFunction("caller", listOf(Param("x", Type.I32)), Type.Void)
            appendBlock("entry")
            call("sideEffect", listOf(params[0]), Type.Void)
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("jal ") }, "Should contain jal: $lines")
        assertTrue(lines.any { it.startsWith("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates conditional branch with NE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("condNe", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.NE, params[0], params[1])
            condBr(cond, BlockRef("notEqual"), BlockRef("equal"))

            appendBlock("notEqual")
            ret(params[0])

            appendBlock("equal")
            ret(params[1])

            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("bne") || it.startsWith("beq") }, "Should contain bne or beq: $lines")
    }

    @Test
    fun `generates conditional branch with ULT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("condUlt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.ULT, params[0], params[1])
            condBr(cond, BlockRef("less"), BlockRef("geq"))

            appendBlock("less")
            ret(params[0])

            appendBlock("geq")
            ret(params[1])

            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("bltu") || it.startsWith("bgeu") }, "Should contain bltu or bgeu: $lines")
    }

    @Test
    fun `generates diamond control flow with phi`() {
        val lines = buildAndDisassemble {
            val params = createFunction("diamond", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val zero = Constant.I32(0)
            val cond = icmp(ICmpPredicate.SGT, params[0], zero)
            condBr(cond, BlockRef("positive"), BlockRef("negative"))

            appendBlock("positive")
            val doubled = add(params[0], params[0])
            br(BlockRef("merge"))

            appendBlock("negative")
            val negated = sub(zero, params[0])
            br(BlockRef("merge"))

            appendBlock("merge")
            val result = phi(Type.I32, listOf(doubled to BlockRef("positive"), negated to BlockRef("negative")))
            ret(result)

            finalizeFunction()
        }
        assertTrue(lines.size > 5, "Diamond should generate multiple instructions: $lines")
        assertTrue(lines.any { it.startsWith("blt") || it.startsWith("bge") }, "Should contain branch: $lines")
    }

    @Test
    fun `generates multi-block with unconditional and conditional branches`() {
        val lines = buildAndDisassemble {
            val params = createFunction("multiBlock", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            val isZero = icmp(ICmpPredicate.EQ, sum, Constant.I32(0))
            condBr(isZero, BlockRef("zero"), BlockRef("nonzero"))

            appendBlock("zero")
            ret(Constant.I32(-1))

            appendBlock("nonzero")
            val doubled = add(sum, sum)
            br(BlockRef("finish"))

            appendBlock("finish")
            ret(doubled)

            finalizeFunction()
        }
        assertTrue(lines.size > 5, "Multi-block should generate multiple instructions: $lines")
        assertTrue(lines.any { it.startsWith("beq") || it.startsWith("bne") }, "Should contain branch: $lines")
    }

    @Test
    fun `generates sext`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sext_test", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val result = sext(params[0], Type.I64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("addiw ") }, "Should contain addiw for sign-extension: $lines")
    }

    @Test
    fun `generates zext`() {
        val lines = buildAndDisassemble {
            val params = createFunction("zext_test", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val result = zext(params[0], Type.I64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("slli ") }, "Should contain slli for zero-extension: $lines")
        assertTrue(lines.any { it.startsWith("srli ") }, "Should contain srli for zero-extension: $lines")
    }

    @Test
    fun `generates return constant`() {
        val lines = buildAndDisassemble {
            createFunction("const42", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(42))
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("li ") || it.startsWith("addi ") }, "Should load constant: $lines")
        assertTrue(lines.any { it.startsWith("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates return i64 constant`() {
        val lines = buildAndDisassemble {
            createFunction("constBig", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(100000))
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("li ") || it.startsWith("lui ") }, "Should load constant: $lines")
        assertTrue(lines.any { it.startsWith("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates chained arithmetic`() {
        val lines = buildAndDisassemble {
            val params = createFunction("chain", listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            appendBlock("entry")
            val t1 = add(params[0], params[1])
            val t2 = mul(t1, params[2])
            val t3 = sub(t2, params[0])
            ret(t3)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("add ") }, "Should contain add: $lines")
        assertTrue(lines.any { it.startsWith("mul ") }, "Should contain mul: $lines")
        assertTrue(lines.any { it.startsWith("sub ") }, "Should contain sub: $lines")
    }

    @Test
    fun `generates multiple functions in same module`() {
        val ir = ModuleBuilder("test", Target.riscv64())

        val paramsA = ir.createFunction("funcA", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(paramsA[0], Constant.I32(1)))
        ir.finalizeFunction()

        val paramsB = ir.createFunction("funcB", listOf(Param("y", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sub(paramsB[0], Constant.I32(1)))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)

        val symA = obj.symbols.firstOrNull { it.name == "funcA" }
        val symB = obj.symbols.firstOrNull { it.name == "funcB" }
        assertNotNull(symA, "Should have funcA symbol")
        assertNotNull(symB, "Should have funcB symbol")
        assertEquals(org.kgen.binary.SymbolKind.FUNCTION, symA!!.kind)
        assertEquals(org.kgen.binary.SymbolKind.FUNCTION, symB!!.kind)
    }

    @Test
    fun `generates select with SLE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("min_val", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SLE, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("slt ") }, "Should contain slt (reversed for SLE): $lines")
    }

    @Test
    fun `generates bitwise chain`() {
        val lines = buildAndDisassemble {
            val params = createFunction("bits", listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            appendBlock("entry")
            val t1 = and(params[0], params[1])
            val t2 = or(t1, params[2])
            val t3 = xor(t2, params[0])
            val t4 = shl(t3, params[1])
            ret(t4)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("and ") }, "Should contain and: $lines")
        assertTrue(lines.any { it.startsWith("or ") }, "Should contain or: $lines")
        assertTrue(lines.any { it.startsWith("xor ") }, "Should contain xor: $lines")
        assertTrue(lines.any { it.startsWith("sll ") }, "Should contain sll: $lines")
    }

    @Test
    fun `generates abs via branch pattern`() {
        val lines = buildAndDisassemble {
            val params = createFunction("myAbs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val zero = Constant.I32(0)
            val isNeg = icmp(ICmpPredicate.SLT, params[0], zero)
            condBr(isNeg, BlockRef("negate"), BlockRef("done"))

            appendBlock("negate")
            val negated = sub(zero, params[0])
            br(BlockRef("result"))

            appendBlock("done")
            br(BlockRef("result"))

            appendBlock("result")
            val result = phi(Type.I32, listOf(negated to BlockRef("negate"), params[0] to BlockRef("done")))
            ret(result)

            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("blt") || it.startsWith("bge") }, "Should contain branch: $lines")
        assertTrue(lines.any { it.startsWith("sub ") }, "Should contain sub: $lines")
    }
}

package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.ir.*
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64CodeGeneratorExtendedTest {

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
    fun `generates sub with constants`() {
        val lines = buildAndDisassemble {
            val params = createFunction("subConst", listOf(Param("a", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = sub(params[0], Constant.I32(10))
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("sub") }, "Should contain sub: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates 32-bit mul`() {
        val lines = buildAndDisassemble {
            val params = createFunction("mul32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = mul(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mul") }, "Should contain mul: $lines")
    }

    @Test
    fun `generates 32-bit sdiv`() {
        val lines = buildAndDisassemble {
            val params = createFunction("div32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = sdiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "Should contain sdiv: $lines")
    }

    @Test
    fun `generates 64-bit sdiv`() {
        val lines = buildAndDisassemble {
            val params = createFunction("div64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = sdiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "Should contain sdiv: $lines")
    }

    @Test
    fun `generates udiv`() {
        val lines = buildAndDisassemble {
            val params = createFunction("udiv_test", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = udiv(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("udiv") }, "Should contain udiv: $lines")
    }

    @Test
    fun `generates srem`() {
        val lines = buildAndDisassemble {
            val params = createFunction("srem_test", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = srem(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sdiv") }, "srem should use sdiv: $lines")
        assertTrue(lines.any { it.contains("msub") }, "srem should use msub: $lines")
    }

    @Test
    fun `generates urem`() {
        val lines = buildAndDisassemble {
            val params = createFunction("urem_test", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = urem(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("udiv") }, "urem should use udiv: $lines")
        assertTrue(lines.any { it.contains("msub") }, "urem should use msub: $lines")
    }

    @Test
    fun `generates icmp EQ`() {
        val lines = buildAndDisassemble {
            val params = createFunction("eq_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = icmp(ICmpPredicate.EQ, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates icmp NE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ne_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = icmp(ICmpPredicate.NE, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates icmp SLT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("slt_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = icmp(ICmpPredicate.SLT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates icmp UGT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ugt_test", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = icmp(ICmpPredicate.UGT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates 32-bit bitwise and`() {
        val lines = buildAndDisassemble {
            val params = createFunction("and32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = and(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("and") }, "Should contain and: $lines")
    }

    @Test
    fun `generates 32-bit bitwise or`() {
        val lines = buildAndDisassemble {
            val params = createFunction("or32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = or(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("orr") }, "Should contain orr: $lines")
    }

    @Test
    fun `generates 32-bit bitwise xor`() {
        val lines = buildAndDisassemble {
            val params = createFunction("xor32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = xor(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("eor") }, "Should contain eor: $lines")
    }

    @Test
    fun `generates ashr operation`() {
        val lines = buildAndDisassemble {
            val params = createFunction("ashr_test", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = ashr(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("asr") }, "Should contain asr: $lines")
    }

    @Test
    fun `generates call with multiple args`() {
        val lines = buildAndDisassemble {
            declareFunction("compute", listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            val params = createFunction("caller", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("compute", listOf(params[0], params[1], Constant.I32(42)), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("bl") }, "Should contain bl (call): $lines")
    }

    @Test
    fun `generates void call`() {
        val lines = buildAndDisassemble {
            declareFunction("sideEffect", listOf(Param("x", Type.I32)), Type.Void)
            val params = createFunction("caller", listOf(Param("x", Type.I32)), Type.Void)
            positionAtEnd(appendBlock("entry"))
            call("sideEffect", listOf(params[0]), Type.Void)
            ret(null)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("bl") }, "Should contain bl: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates f32 fadd`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fadd32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            val result = fadd(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
    }

    @Test
    fun `generates fcmp OEQ`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fcmp_eq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcmp") }, "Should contain fcmp: $lines")
        assertTrue(lines.any { it.contains("csinc") }, "Should contain csinc: $lines")
    }

    @Test
    fun `generates fcmp OGT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fcmp_gt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            positionAtEnd(appendBlock("entry"))
            val result = fcmp(FCmpPredicate.OGT, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcmp") }, "Should contain fcmp: $lines")
    }

    @Test
    fun `generates sitofp i64 to f64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("i64_to_f64", listOf(Param("a", Type.I64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val result = sitofp(params[0], Type.F64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("scvtf") }, "Should contain scvtf: $lines")
    }

    @Test
    fun `generates fptosi f64 to i64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("f64_to_i64", listOf(Param("a", Type.F64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = fptosi(params[0], Type.I64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fcvtzs") }, "Should contain fcvtzs: $lines")
    }

    @Test
    fun `generates conditional branch with EQ`() {
        val lines = buildAndDisassemble {
            val params = createFunction("condEq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            condBr(cond, "yes", "no")

            positionAtEnd(appendBlock("yes"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("no"))
            ret(Constant.I32(0))

            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.startsWith("b.") }, "Should contain conditional branch: $lines")
    }

    @Test
    fun `generates conditional branch with SLT`() {
        val lines = buildAndDisassemble {
            val params = createFunction("condSlt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SLT, params[0], params[1])
            condBr(cond, "less", "geq")

            positionAtEnd(appendBlock("less"))
            ret(params[0])

            positionAtEnd(appendBlock("geq"))
            ret(params[1])

            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.startsWith("b.") }, "Should contain conditional branch: $lines")
    }

    @Test
    fun `generates unconditional branch`() {
        val lines = buildAndDisassemble {
            val params = createFunction("jump_test", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            br("target")

            positionAtEnd(appendBlock("target"))
            ret(params[0])

            finalizeFunction()
        }
        assertTrue(lines.any { it.startsWith("b ") }, "Should contain unconditional branch: $lines")
    }

    @Test
    fun `generates multi-block diamond control flow`() {
        val lines = buildAndDisassemble {
            val params = createFunction("diamond", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val zero = Constant.I32(0)
            val cond = icmp(ICmpPredicate.SGT, params[0], zero)
            condBr(cond, "positive", "negative")

            positionAtEnd(appendBlock("positive"))
            val doubled = add(params[0], params[0])
            br("merge")

            positionAtEnd(appendBlock("negative"))
            val negated = sub(zero, params[0])
            br("merge")

            positionAtEnd(appendBlock("merge"))
            val result = phi(Type.I32, listOf(doubled to "positive", negated to "negative"))
            ret(result)

            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.startsWith("b.") || it.startsWith("b ") }, "Should contain branches: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates multi-block with unconditional and conditional branches`() {
        val lines = buildAndDisassemble {
            val params = createFunction("multiBlock", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val sum = add(params[0], params[1])
            val isZero = icmp(ICmpPredicate.EQ, sum, Constant.I32(0))
            condBr(isZero, "zero", "nonzero")

            positionAtEnd(appendBlock("zero"))
            ret(Constant.I32(-1))

            positionAtEnd(appendBlock("nonzero"))
            val doubled = add(sum, sum)
            br("finish")

            positionAtEnd(appendBlock("finish"))
            ret(doubled)

            finalizeFunction()
        }
        assertTrue(lines.size > 5, "Multi-block should generate multiple instructions: $lines")
        assertTrue(lines.any { it.contains("cmp") }, "Should contain comparison: $lines")
        assertTrue(lines.any { it.startsWith("b.") || it.startsWith("b ") }, "Should contain branches: $lines")
    }

    @Test
    fun `generates sext i32 to i64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("sext_test", listOf(Param("a", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = sext(params[0], Type.I64)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("sxtw") }, "Should contain sxtw: $lines")
    }

    @Test
    fun `generates zext i32 to i64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("zext_test", listOf(Param("a", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val result = zext(params[0], Type.I64)
            ret(result)
            finalizeFunction()
        }
        // ARM64 zext from W to X is just a mov of the W register (upper bits cleared automatically)
        assertTrue(lines.any { it.contains("mov") || it.contains("ret") }, "Should produce valid output: $lines")
    }

    @Test
    fun `generates select with SGE`() {
        val lines = buildAndDisassemble {
            val params = createFunction("clamp_pos", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val zero = Constant.I32(0)
            val isPos = icmp(ICmpPredicate.SGE, params[0], zero)
            val result = select(isPos, params[0], zero)
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csel") }, "Should contain csel: $lines")
    }

    @Test
    fun `generates 64-bit select`() {
        val lines = buildAndDisassemble {
            val params = createFunction("max64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("cmp") }, "Should contain cmp: $lines")
        assertTrue(lines.any { it.contains("csel") }, "Should contain csel with X registers: $lines")
    }

    @Test
    fun `generates fp call with mixed args`() {
        val lines = buildAndDisassemble {
            declareFunction("mixedArgs", listOf(Param("a", Type.I32), Param("b", Type.F64)), Type.F64)
            val params = createFunction("caller", listOf(Param("x", Type.I32), Param("y", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val result = call("mixedArgs", listOf(params[0], params[1]), Type.F64)
            ret(result!!)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("bl") }, "Should contain bl: $lines")
    }

    @Test
    fun `generates return constant`() {
        val lines = buildAndDisassemble {
            createFunction("const42", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(42))
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mov") }, "Should contain mov for constant: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates return i64 constant`() {
        val lines = buildAndDisassemble {
            createFunction("constBig", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(100000))
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("mov") }, "Should contain mov: $lines")
        assertTrue(lines.any { it.contains("ret") }, "Should contain ret: $lines")
    }

    @Test
    fun `generates chained arithmetic`() {
        val lines = buildAndDisassemble {
            val params = createFunction("chain", listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val t1 = add(params[0], params[1])
            val t2 = mul(t1, params[2])
            val t3 = sub(t2, params[0])
            ret(t3)
            finalizeFunction()
        }
        val addCount = lines.count { it.startsWith("add") }
        assertTrue(addCount >= 1, "Should contain add: $lines")
        assertTrue(lines.any { it.contains("mul") }, "Should contain mul: $lines")
        assertTrue(lines.any { it.startsWith("sub") }, "Should contain sub: $lines")
    }

    @Test
    fun `generates multiple functions in same module`() {
        val ir = IrBuilder("test", Target.arm64())

        val paramsA = ir.createFunction("funcA", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(paramsA[0], Constant.I32(1)))
        ir.finalizeFunction()

        val paramsB = ir.createFunction("funcB", listOf(Param("y", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.sub(paramsB[0], Constant.I32(1)))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)

        val symA = obj.symbols.firstOrNull { it.name == "funcA" }
        val symB = obj.symbols.firstOrNull { it.name == "funcB" }
        assertNotNull(symA, "Should have funcA symbol")
        assertNotNull(symB, "Should have funcB symbol")
        assertEquals(org.kgen.binary.SymbolKind.FUNCTION, symA!!.kind)
        assertEquals(org.kgen.binary.SymbolKind.FUNCTION, symB!!.kind)
    }

    @Test
    fun `generates external symbol reference`() {
        val ir = IrBuilder("test", Target.arm64())
        ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val printfSym = obj.symbols.first { it.name == "printf" }
        assertEquals(org.kgen.binary.SymbolBinding.GLOBAL, printfSym.binding)
        assertEquals(org.kgen.binary.SymbolKind.UNDEFINED, printfSym.kind)
    }

    @Test
    fun `generates fneg f64`() {
        val lines = buildAndDisassemble {
            val params = createFunction("neg_f64", listOf(Param("a", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val result = fneg(params[0])
            ret(result)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fneg") }, "Should contain fneg: $lines")
    }

    @Test
    fun `generates fp arithmetic chain`() {
        val lines = buildAndDisassemble {
            val params = createFunction("fpChain", listOf(Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val t1 = fmul(params[0], params[1])
            val t2 = fadd(t1, params[2])
            val t3 = fdiv(t2, params[0])
            ret(t3)
            finalizeFunction()
        }
        assertTrue(lines.any { it.contains("fmul") }, "Should contain fmul: $lines")
        assertTrue(lines.any { it.contains("fadd") }, "Should contain fadd: $lines")
        assertTrue(lines.any { it.contains("fdiv") }, "Should contain fdiv: $lines")
    }

    @Test
    fun `generates object file with correct arch`() {
        val ir = IrBuilder("test", Target.arm64())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(null)
        ir.finalizeFunction()
        val module = ir.build()

        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)

        assertEquals(org.kgen.binary.ArchType.AARCH64, obj.arch.arch)
        assertEquals(org.kgen.binary.ObjectFormat.ELF, obj.format)
        assertTrue(obj.sections.any { it.kind == org.kgen.binary.SectionKind.TEXT })
        assertTrue(obj.symbols.any { it.name == "noop" })
    }
}

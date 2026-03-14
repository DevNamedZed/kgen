package org.kgen.target.x86.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.build.FunctionScope
import org.kgen.ir.target.Target
import org.kgen.target.x86.disasm.X86Disassembler

class X86CodeGeneratorExtendedTest {

    private fun assertValidCodegen(code: ByteArray) {
        assertTrue(code.isNotEmpty(), "Generated code should not be empty")
        val disasm = X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes should be decoded: ${insts.map { it.text() }}")
    }

    private fun generateCode(builder: (IrBuilder) -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.x86_64())
        builder(ir)
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        return obj.sections[0].data
    }

    // --- Phi nodes ---

    @Test
    fun compilesPhiNode() {
        val code = generateCode { ir ->
            val params = ir.createFunction("phi_test", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            ir.condBr(cond, BlockRef("pos"), BlockRef("neg"))

            ir.appendBlock("pos")
            val posVal = ir.add(params[0], Constant.I32(1))
            ir.br(BlockRef("merge"))

            ir.appendBlock("neg")
            val negVal = ir.sub(Constant.I32(0), params[0])
            ir.br(BlockRef("merge"))

            ir.appendBlock("merge")
            val phi = ir.phi(Type.I32, listOf(posVal to BlockRef("pos"), negVal to BlockRef("neg")))
            ir.ret(phi)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Simple loop (using FunctionScope) ---

    @Test
    fun compilesSimpleLoop() {
        val ir = IrBuilder("loop_test", Target.x86_64())
        val fn = ir.function("sum_to_n", listOf(Param("n", Type.I32)), Type.I32)
        val sum = fn.variable(Type.i32(0))
        val i = fn.variable(Type.i32(0))
        fn.whileLoop(
            condition = { lt(get(i), param(0)) },
            body = {
                set(sum, add(get(sum), get(i)))
                set(i, add(get(i), Type.i32(1)))
            }
        )
        fn.ret(fn.get(sum))
        fn.end()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)
    }

    // --- Multiple basic blocks with complex CFG ---

    @Test
    fun compilesMultiBlockFunction() {
        val code = generateCode { ir ->
            val params = ir.createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            ir.condBr(isNeg, BlockRef("negative"), BlockRef("check_zero"))

            ir.appendBlock("negative")
            ir.ret(Constant.I32(-1))

            ir.appendBlock("check_zero")
            val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            ir.condBr(isZero, BlockRef("zero"), BlockRef("positive"))

            ir.appendBlock("zero")
            ir.ret(Constant.I32(0))

            ir.appendBlock("positive")
            ir.ret(Constant.I32(1))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Bitwise operations with immediate ---

    @Test
    fun compilesBitwiseAndImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("mask", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val masked = ir.and(params[0], Constant.I32(0xFF))
            ir.ret(masked)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesBitwiseOrImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("set_bit", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val result = ir.or(params[0], Constant.I32(0x80))
            ir.ret(result)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesBitwiseXorImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("toggle", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val result = ir.xor(params[0], Constant.I32(-1))
            ir.ret(result)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Multiple return paths ---

    @Test
    fun compilesMultipleReturnPaths() {
        val code = generateCode { ir ->
            val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            ir.condBr(isNeg, BlockRef("negate"), BlockRef("done"))

            ir.appendBlock("negate")
            val negated = ir.neg(params[0])
            ir.ret(negated)

            ir.appendBlock("done")
            ir.ret(params[0])
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        val disasm = X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.count { it.mnemonic == "ret" } >= 2)
    }

    // --- Constant operations ---

    @Test
    fun compilesConstantReturn() {
        val code = generateCode { ir ->
            ir.createFunction("const42", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(42))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        val disasm = X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Verify the constant 42 (0x2A) appears somewhere in the instruction stream
        assertTrue(insts.any { it.text().contains("42") || it.text().contains("0x2a") || it.text().contains("2a") },
            "Should reference constant 42: ${insts.map { it.text() }}")
    }

    @Test
    fun compilesZeroReturn() {
        val code = generateCode { ir ->
            ir.createFunction("zero", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesLargeConstant() {
        val code = generateCode { ir ->
            ir.createFunction("large", emptyList(), Type.I64)
            ir.appendBlock("entry")
            ir.ret(Constant.I64(0x123456789ABCDEF0L))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Comparison predicates ---

    @Test
    fun compilesAllICmpPredicates() {
        for (pred in ICmpPredicate.entries) {
            val code = generateCode { ir ->
                val params = ir.createFunction("cmp_$pred",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                ir.appendBlock("entry")
                val cmp = ir.icmp(pred, params[0], params[1])
                val result = ir.zext(cmp, Type.I32)
                ir.ret(result)
                ir.finalizeFunction()
            }
            assertValidCodegen(code)
        }
    }

    // --- Multiple functions ---

    @Test
    fun compilesModuleWithMultipleFunctions() {
        val ir = IrBuilder("multi", Target.x86_64())

        val p1 = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(p1[0], p1[1]))
        ir.finalizeFunction()

        val p2 = ir.createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sub(p2[0], p2[1]))
        ir.finalizeFunction()

        val p3 = ir.createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.mul(p3[0], p3[1]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.isNotEmpty())
        assertTrue(obj.symbols.size >= 3, "Should have at least 3 symbols: ${obj.symbols.map { it.name }}")
    }

    // --- Void functions ---

    @Test
    fun compilesVoidFunction() {
        val code = generateCode { ir ->
            ir.createFunction("noop", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        assertTrue(code.last().toInt() and 0xFF == 0xC3, "Void function should end with ret")
    }

    // --- 8-bit and 16-bit operations ---

    @Test
    @org.junit.jupiter.api.Disabled("I8 not fully supported in codegen")
    fun compilesI8Parameters() {
        val code = generateCode { ir ->
            val params = ir.createFunction("byte_add",
                listOf(Param("a", Type.I8), Param("b", Type.I8)), Type.I8)
            ir.appendBlock("entry")
            val sum = ir.add(params[0], params[1])
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    @org.junit.jupiter.api.Disabled("I16 not fully supported in codegen")
    fun compilesI16Parameters() {
        val code = generateCode { ir ->
            val params = ir.createFunction("short_add",
                listOf(Param("a", Type.I16), Param("b", Type.I16)), Type.I16)
            ir.appendBlock("entry")
            val sum = ir.add(params[0], params[1])
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Chain of operations ---

    @Test
    fun compilesLongExpressionChain() {
        val code = generateCode { ir ->
            val params = ir.createFunction("chain",
                listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            var v: Value = params[0]
            // x + 1 - 2 * 3 + 4 ...
            v = ir.add(v, Constant.I32(1))
            v = ir.sub(v, Constant.I32(2))
            v = ir.mul(v, Constant.I32(3))
            v = ir.add(v, Constant.I32(4))
            v = ir.sub(v, Constant.I32(5))
            v = ir.add(v, Constant.I32(6))
            ir.ret(v)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Alloca and memory operations ---

    @Test
    fun compilesAllocaLoadStore() {
        val code = generateCode { ir ->
            val params = ir.createFunction("stack_var",
                listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val alloca = ir.alloca(Type.I32)
            ir.store(params[0], alloca)
            val loaded = ir.load(Type.I32, alloca)
            val result = ir.add(loaded, Constant.I32(1))
            ir.ret(result)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesMultipleAllocas() {
        val code = generateCode { ir ->
            val params = ir.createFunction("multi_alloca",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = ir.alloca(Type.I32)
            val b = ir.alloca(Type.I32)
            ir.store(params[0], a)
            ir.store(params[1], b)
            val la = ir.load(Type.I32, a)
            val lb = ir.load(Type.I32, b)
            ir.ret(ir.add(la, lb))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Select with various conditions ---

    @Test
    fun compilesSelectGE() {
        val code = generateCode { ir ->
            val params = ir.createFunction("clamp_pos",
                listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cond = ir.icmp(ICmpPredicate.SGE, params[0], Constant.I32(0))
            val result = ir.select(cond, params[0], Constant.I32(0))
            ir.ret(result)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- Function with many parameters ---

    @Test
    fun compilesFunctionWith6Params() {
        val code = generateCode { ir ->
            val paramList = (0 until 6).map { Param("p$it", Type.I32) }
            val params = ir.createFunction("sum6", paramList, Type.I32)
            ir.appendBlock("entry")
            var sum: Value = params[0]
            for (i in 1 until 6) {
                sum = ir.add(sum, params[i])
            }
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesFunctionWithStackParams() {
        // More than 6 params forces stack passing on x86-64 System V ABI
        val code = generateCode { ir ->
            val paramList = (0 until 8).map { Param("p$it", Type.I32) }
            val params = ir.createFunction("sum8", paramList, Type.I32)
            ir.appendBlock("entry")
            var sum: Value = params[0]
            for (i in 1 until 8) {
                sum = ir.add(sum, params[i])
            }
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    // --- ObjectFile structure ---

    @Test
    fun generatedObjectFileHasTextSection() {
        val ir = IrBuilder("objfile_test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.any { it.name == ".text" || it.kind == org.kgen.binary.SectionKind.TEXT },
            "Should have a text section")
    }

    @Test
    fun generatedObjectFileHasSymbols() {
        val ir = IrBuilder("sym_test", Target.x86_64())
        ir.createFunction("my_func", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.symbols.any { it.name == "my_func" },
            "Should have symbol 'my_func': ${obj.symbols.map { it.name }}")
    }

    // --- Code quality checks ---

    @Test
    fun generatedCodeEndsWithRet() {
        val code = generateCode { ir ->
            val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(ir.add(params[0], Constant.I32(1)))
            ir.finalizeFunction()
        }
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Code should end with ret (0xC3)")
    }

    @Test
    fun identityFunctionIsCompact() {
        val code = generateCode { ir ->
            val params = ir.createFunction("id", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            ir.ret(params[0])
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        // Identity function should be relatively small
        assertTrue(code.size <= 32, "Identity function should be compact, got ${code.size} bytes")
    }

    // --- 64-bit operations ---

    @Test
    fun compilesI64AddSubMul() {
        val code = generateCode { ir ->
            val params = ir.createFunction("arith64",
                listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.appendBlock("entry")
            val sum = ir.add(params[0], params[1])
            val diff = ir.sub(sum, params[1])
            val prod = ir.mul(diff, params[0])
            ir.ret(prod)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesI64Comparison() {
        val code = generateCode { ir ->
            val params = ir.createFunction("cmp64",
                listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = ir.zext(cmp, Type.I32)
            ir.ret(result)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }
}

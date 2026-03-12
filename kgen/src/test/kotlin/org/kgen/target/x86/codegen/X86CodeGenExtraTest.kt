package org.kgen.target.x86.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.disasm.X86Disassembler

class X86CodeGenExtraTest {

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

    @Test
    fun compilesPhiNode() {
        val code = generateCode { ir ->
            val params = ir.createFunction("phi_test", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            ir.condBr(cond, "pos", "neg")

            ir.positionAtEnd(ir.appendBlock("pos"))
            val posVal = ir.add(params[0], Constant.I32(1))
            ir.br("merge")

            ir.positionAtEnd(ir.appendBlock("neg"))
            val negVal = ir.sub(Constant.I32(0), params[0])
            ir.br("merge")

            ir.positionAtEnd(ir.appendBlock("merge"))
            val phi = ir.phi(Type.I32, listOf(posVal to "pos", negVal to "neg"))
            ir.ret(phi)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

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
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun compilesMultiBlockFunction() {
        val code = generateCode { ir ->
            val params = ir.createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            ir.condBr(isNeg, "negative", "check_zero")

            ir.positionAtEnd(ir.appendBlock("negative"))
            ir.ret(Constant.I32(-1))

            ir.positionAtEnd(ir.appendBlock("check_zero"))
            val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            ir.condBr(isZero, "zero", "positive")

            ir.positionAtEnd(ir.appendBlock("zero"))
            ir.ret(Constant.I32(0))

            ir.positionAtEnd(ir.appendBlock("positive"))
            ir.ret(Constant.I32(1))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesBitwiseAndImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("mask", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.and(params[0], Constant.I32(0xFF)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesBitwiseOrImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("set_bit", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.or(params[0], Constant.I32(0x80)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesBitwiseXorImm() {
        val code = generateCode { ir ->
            val params = ir.createFunction("toggle", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.xor(params[0], Constant.I32(-1)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesMultipleReturnPaths() {
        val code = generateCode { ir ->
            val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            ir.condBr(isNeg, "negate", "done")
            ir.positionAtEnd(ir.appendBlock("negate"))
            ir.ret(ir.neg(params[0]))
            ir.positionAtEnd(ir.appendBlock("done"))
            ir.ret(params[0])
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        val disasm = X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.count { it.mnemonic == "ret" } >= 2)
    }

    @Test
    fun compilesConstantReturn() {
        val code = generateCode { ir ->
            ir.createFunction("const42", emptyList(), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I32(42))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesZeroReturn() {
        val code = generateCode { ir ->
            ir.createFunction("zero", emptyList(), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesLargeI64Constant() {
        val code = generateCode { ir ->
            ir.createFunction("large", emptyList(), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I64(0x123456789ABCDEF0L))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesAllICmpPredicates() {
        for (pred in ICmpPredicate.entries) {
            val code = generateCode { ir ->
                val params = ir.createFunction("cmp_$pred",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                ir.positionAtEnd(ir.appendBlock("entry"))
                val cmp = ir.icmp(pred, params[0], params[1])
                ir.ret(ir.zext(cmp, Type.I32))
                ir.finalizeFunction()
            }
            assertValidCodegen(code)
        }
    }

    @Test
    fun compilesMultipleFunctions() {
        val ir = IrBuilder("multi", Target.x86_64())
        val p1 = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(p1[0], p1[1]))
        ir.finalizeFunction()

        val p2 = ir.createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.sub(p2[0], p2[1]))
        ir.finalizeFunction()

        val p3 = ir.createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.mul(p3[0], p3[1]))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.isNotEmpty())
        assertTrue(obj.symbols.size >= 3)
    }

    @Test
    fun compilesVoidFunction() {
        val code = generateCode { ir ->
            ir.createFunction("noop", emptyList(), Type.Void)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret()
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        assertEquals(0xC3, code.last().toInt() and 0xFF)
    }

    @Test
    @org.junit.jupiter.api.Disabled("I8 operations not fully supported in x86 codegen yet")
    fun compilesI8Params() {
        val code = generateCode { ir ->
            val params = ir.createFunction("byte_add",
                listOf(Param("a", Type.I8), Param("b", Type.I8)), Type.I8)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(params[0], params[1]))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesLongExpressionChain() {
        val code = generateCode { ir ->
            val params = ir.createFunction("chain", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            var v: Value = params[0]
            v = ir.add(v, Constant.I32(1))
            v = ir.sub(v, Constant.I32(2))
            v = ir.mul(v, Constant.I32(3))
            v = ir.add(v, Constant.I32(4))
            v = ir.sub(v, Constant.I32(5))
            ir.ret(v)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesAllocaLoadStore() {
        val code = generateCode { ir ->
            val params = ir.createFunction("stack_var", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val alloca = ir.alloca(Type.I32)
            ir.store(params[0], alloca)
            val loaded = ir.load(Type.I32, alloca)
            ir.ret(ir.add(loaded, Constant.I32(1)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesMultipleAllocas() {
        val code = generateCode { ir ->
            val params = ir.createFunction("multi_alloca",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val a = ir.alloca(Type.I32)
            val b = ir.alloca(Type.I32)
            ir.store(params[0], a)
            ir.store(params[1], b)
            ir.ret(ir.add(ir.load(Type.I32, a), ir.load(Type.I32, b)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesSelectGE() {
        val code = generateCode { ir ->
            val params = ir.createFunction("clamp_pos", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SGE, params[0], Constant.I32(0))
            ir.ret(ir.select(cond, params[0], Constant.I32(0)))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesSixParams() {
        val code = generateCode { ir ->
            val paramList = (0 until 6).map { Param("p$it", Type.I32) }
            val params = ir.createFunction("sum6", paramList, Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            var sum: Value = params[0]
            for (i in 1 until 6) sum = ir.add(sum, params[i])
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesStackParams() {
        val code = generateCode { ir ->
            val paramList = (0 until 8).map { Param("p$it", Type.I32) }
            val params = ir.createFunction("sum8", paramList, Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            var sum: Value = params[0]
            for (i in 1 until 8) sum = ir.add(sum, params[i])
            ir.ret(sum)
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun generatedObjectFileHasTextSection() {
        val ir = IrBuilder("objfile", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.any { it.name == ".text" || it.kind == org.kgen.binary.SectionKind.TEXT })
    }

    @Test
    fun generatedObjectFileHasSymbol() {
        val ir = IrBuilder("sym_test", Target.x86_64())
        ir.createFunction("my_func", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.symbols.any { it.name == "my_func" })
    }

    @Test
    fun codeEndsWithRet() {
        val code = generateCode { ir ->
            val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.add(params[0], Constant.I32(1)))
            ir.finalizeFunction()
        }
        assertEquals(0xC3, code.last().toInt() and 0xFF)
    }

    @Test
    fun identityIsCompact() {
        val code = generateCode { ir ->
            val params = ir.createFunction("id", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(params[0])
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
        assertTrue(code.size <= 32)
    }

    @Test
    fun compilesI64Arithmetic() {
        val code = generateCode { ir ->
            val params = ir.createFunction("arith64",
                listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val sum = ir.add(params[0], params[1])
            val diff = ir.sub(sum, params[1])
            ir.ret(ir.mul(diff, params[0]))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesI64Comparison() {
        val code = generateCode { ir ->
            val params = ir.createFunction("cmp64",
                listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(ir.zext(ir.icmp(ICmpPredicate.SGT, params[0], params[1]), Type.I32))
            ir.finalizeFunction()
        }
        assertValidCodegen(code)
    }

    @Test
    fun compilesIfElseWithFunctionScope() {
        val ir = IrBuilder("ifelse_test", Target.x86_64())
        val fn = ir.function("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val result = fn.variable(Type.i32(0))
        fn.ifElse(fn.gt(fn.param(0), fn.param(1)),
            { set(result, param(0)) },
            { set(result, param(1)) }
        )
        fn.ret(fn.get(result))
        fn.end()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun compilesForLoop() {
        val ir = IrBuilder("for_test", Target.x86_64())
        val fn = ir.function("factorial", listOf(Param("n", Type.I32)), Type.I32)
        val result = fn.variable(Type.i32(1))
        val i = fn.variable(Type.i32(0))
        fn.forLoop(
            init = { set(i, Type.i32(1)) },
            condition = { le(get(i), param(0)) },
            update = { set(i, add(get(i), Type.i32(1))) },
            body = { set(result, mul(get(result), get(i))) }
        )
        fn.ret(fn.get(result))
        fn.end()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }
}

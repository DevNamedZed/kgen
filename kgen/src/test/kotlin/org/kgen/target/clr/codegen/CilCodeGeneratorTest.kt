package org.kgen.target.clr.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.clr.CilOpCode
import org.kgen.target.clr.asm.CilDisassembler
import org.kgen.target.clr.asm.CilInstruction

class CilCodeGeneratorTest {

    private fun buildAndDisassemble(block: (IrBuilder) -> Unit): List<CilInstruction> {
        val ir = IrBuilder("test", Target.msil())
        block(ir)
        val module = ir.build()
        val gen = CilCodeGenerator()
        val bytes = gen.generate(module)
        return CilDisassembler().disassemble(bytes)
    }

    @Test
    fun returnConstant() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("getFortyTwo", emptyList(), Type.I32)
            ir.appendBlock("entry")
            ir.ret(Constant.I32(42))
            ir.finalizeFunction()
        }

        // ldc.i4.s 42, ret
        assertEquals(2, instructions.size)
        assertEquals(CilOpCode.LDC_I4_S, instructions[0].opcode)
        assertEquals(42, instructions[0].operand)
        assertEquals(CilOpCode.RET, instructions[1].opcode)
    }

    @Test
    fun addTwoArgs() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val sum = ir.add(a, b)
            ir.ret(sum)
            ir.finalizeFunction()
        }

        // ldarg.0, ldarg.1, add, stloc.0, ldloc.0, ret
        assertTrue(instructions.any { it.opcode == CilOpCode.LDARG_0 })
        assertTrue(instructions.any { it.opcode == CilOpCode.LDARG_1 })
        assertTrue(instructions.any { it.opcode == CilOpCode.ADD })
        assertTrue(instructions.any { it.opcode == CilOpCode.RET })
    }

    @Test
    fun subtractInts() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.sub(a, b))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.SUB })
    }

    @Test
    fun multiplyInts() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.mul(a, b))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.MUL })
    }

    @Test
    fun divideInts() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("div", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.sdiv(a, b))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.DIV })
    }

    @Test
    fun voidReturn() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("doNothing", emptyList(), Type.Void)
            ir.appendBlock("entry")
            ir.ret()
            ir.finalizeFunction()
        }

        assertEquals(1, instructions.size)
        assertEquals(CilOpCode.RET, instructions[0].opcode)
    }

    @Test
    fun longConstant() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("getLong", emptyList(), Type.I64)
            ir.appendBlock("entry")
            ir.ret(Constant.I64(123456789L))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.LDC_I8 })
        val ldcI8 = instructions.first { it.opcode == CilOpCode.LDC_I8 }
        assertEquals(123456789L, ldcI8.operand)
    }

    @Test
    fun floatConstant() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("getFloat", emptyList(), Type.F32)
            ir.appendBlock("entry")
            ir.ret(Constant.F32(3.14f))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.LDC_R4 })
    }

    @Test
    fun doubleConstant() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("getDouble", emptyList(), Type.F64)
            ir.appendBlock("entry")
            ir.ret(Constant.F64(2.718))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.LDC_R8 })
    }

    @Test
    fun negation() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("neg", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            ir.ret(ir.neg(x))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.NEG })
    }

    @Test
    fun bitwiseAnd() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("bitand", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            ir.ret(ir.and(a, b))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.AND })
    }

    @Test
    fun icmpEqual() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("eq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val cmp = ir.icmp(ICmpPredicate.EQ, a, b)
            ir.ret(cmp)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CEQ })
    }

    @Test
    fun icmpLessThan() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("lt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val cmp = ir.icmp(ICmpPredicate.SLT, a, b)
            ir.ret(cmp)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CLT })
    }

    @Test
    fun icmpGreaterEqual() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("ge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val cmp = ir.icmp(ICmpPredicate.SGE, a, b)
            ir.ret(cmp)
            ir.finalizeFunction()
        }

        // SGE = !(a < b) = clt + ldc.i4.0 + ceq
        assertTrue(instructions.any { it.opcode == CilOpCode.CLT })
        assertTrue(instructions.any { it.opcode == CilOpCode.CEQ })
    }

    @Test
    fun conditionalBranch() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            val zero = Constant.I32(0)
            val cmp = ir.icmp(ICmpPredicate.SGE, x, zero)
            ir.condBr(cmp, BlockRef("positive"), BlockRef("negative"))

            ir.appendBlock("positive")
            ir.ret(x)

            ir.appendBlock("negative")
            val negVal = ir.neg(x)
            ir.ret(negVal)

            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.BRTRUE })
        assertTrue(instructions.any { it.opcode == CilOpCode.BR })
        assertTrue(instructions.any { it.opcode == CilOpCode.NEG })
    }

    @Test
    fun sextI32ToI64() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("widen", listOf(Param("x", Type.I32)), Type.I64)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            val wide = ir.sext(x, Type.I64)
            ir.ret(wide)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CONV_I8 })
    }

    @Test
    fun truncI64ToI32() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("narrow", listOf(Param("x", Type.I64)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I64, 0)
            val narrow = ir.trunc(x, Type.I32)
            ir.ret(narrow)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CONV_I4 })
    }

    @Test
    fun floatArithmetic() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("fadd", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            ir.ret(ir.fadd(a, b))
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.ADD })
    }

    @Test
    fun intToFloat() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("convert", listOf(Param("x", Type.I32)), Type.F64)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            val f = ir.sitofp(x, Type.F64)
            ir.ret(f)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CONV_R8 })
    }

    @Test
    fun floatToInt() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("convert", listOf(Param("x", Type.F64)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.F64, 0)
            val i = ir.fptosi(x, Type.I32)
            ir.ret(i)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CONV_I4 })
    }

    @Test
    fun callFunction() {
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

        assertTrue(instructions.any { it.opcode == CilOpCode.CALL })
    }

    @Test
    fun singleMethodGenerate() {
        val ir = IrBuilder("test", Target.msil())
        ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I32, 0)
        val b = Parameter("b", Type.I32, 1)
        val sum = ir.add(a, b)
        ir.ret(sum)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = CilCodeGenerator()
        val fn = module.functions.first { !it.isExternal }
        val bytes = gen.generateMethod(fn, module.functions)
        val instructions = CilDisassembler().disassemble(bytes)

        assertEquals(CilOpCode.LDARG_0, instructions[0].opcode)
        assertEquals(CilOpCode.LDARG_1, instructions[1].opcode)
        assertEquals(CilOpCode.ADD, instructions[2].opcode)
    }

    @Test
    fun select() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val b = Parameter("b", Type.I32, 1)
            val cmp = ir.icmp(ICmpPredicate.SGT, a, b)
            val result = ir.select(cmp, a, b)
            ir.ret(result)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.BRFALSE })
    }

    @Test
    fun constantOptimization() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("consts", emptyList(), Type.I32)
            ir.appendBlock("entry")
            val a = ir.add(Constant.I32(-1), Constant.I32(0))
            ir.ret(a)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.LDC_I4_M1 })
        assertTrue(instructions.any { it.opcode == CilOpCode.LDC_I4_0 })
    }

    @Test
    fun floatCompare() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("feq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I1)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.F64, 0)
            val b = Parameter("b", Type.F64, 1)
            val cmp = ir.fcmp(FCmpPredicate.OEQ, a, b)
            ir.ret(cmp)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CEQ })
    }

    @Test
    fun unsignedIntToFloat() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("toFloat", listOf(Param("a", Type.I32)), Type.F32)
            ir.appendBlock("entry")
            val a = Parameter("a", Type.I32, 0)
            val f = ir.uitofp(a, Type.F32)
            ir.ret(f)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CONV_R_UN })
    }

    @Test
    fun loadAndStore() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("loadstore", listOf(Param("ptr", Type.OpaquePointer)), Type.I32)
            ir.appendBlock("entry")
            val ptr = Parameter("ptr", Type.OpaquePointer, 0)
            ir.store(Constant.I32(42), ptr)
            val loaded = ir.load(Type.I32, ptr)
            ir.ret(loaded)
            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.STIND_I4 })
        assertTrue(instructions.any { it.opcode == CilOpCode.LDIND_I4 })
    }

    @Test
    fun switchCases() {
        val instructions = buildAndDisassemble { ir ->
            ir.createFunction("sw", listOf(Param("x", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val x = Parameter("x", Type.I32, 0)
            ir.switch(x, "default", listOf(
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2"
            ))

            ir.appendBlock("case1")
            ir.ret(Constant.I32(10))

            ir.appendBlock("case2")
            ir.ret(Constant.I32(20))

            ir.appendBlock("default")
            ir.ret(Constant.I32(0))

            ir.finalizeFunction()
        }

        assertTrue(instructions.any { it.opcode == CilOpCode.CEQ })
    }
}

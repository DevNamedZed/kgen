package org.kgen.target.wasm.codegen

import org.kgen.target.wasm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target

class WasmCodeGeneratorTest {

    private fun wasmMagic(wasm: ByteArray) {
        assertTrue(wasm.size > 8, "WASM binary too small")
        assertEquals(0x00, wasm[0].toInt() and 0xFF)
        assertEquals(0x61, wasm[1].toInt() and 0xFF) // 'a'
        assertEquals(0x73, wasm[2].toInt() and 0xFF) // 's'
        assertEquals(0x6D, wasm[3].toInt() and 0xFF) // 'm'
        // version 1
        assertEquals(0x01, wasm[4].toInt() and 0xFF)
        assertEquals(0x00, wasm[5].toInt() and 0xFF)
        assertEquals(0x00, wasm[6].toInt() and 0xFF)
        assertEquals(0x00, wasm[7].toInt() and 0xFF)
    }

    @Test
    fun `generates valid WASM from IR add function`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)

        wasmMagic(wasm)
    }

    @Test
    fun `generates WASM with external imports`() {
        val ir = IrBuilder("imports", Target.wasm())
        ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)

        ir.createFunction("main", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.call("log", listOf(Constant.I32(42)), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val module = ir.build()
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)

        assertTrue(wasm.size > 8)
    }

    @Test
    fun `generates WASM with arithmetic`() {
        val ir = IrBuilder("math", Target.wasm())
        val params = ir.createFunction("compute", listOf(
            Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val product = ir.mul(sum, Constant.I32(2))
        val result = ir.sub(product, Constant.I32(1))
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)
        assertTrue(wasm.size > 8)
    }

    @Test
    fun `one IR module produces WASM, ELF, and PE output`() {
        val ir = IrBuilder("multi_target")
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        val module = ir.build()

        // Generate WASM
        val wasm = WasmCodeGenerator().generate(module)
        assertEquals(0x00, wasm[0].toInt() and 0xFF) // WASM magic
        assertEquals(0x61, wasm[1].toInt() and 0xFF)

        // Generate x86-64 ELF .o
        val x86Gen = org.kgen.target.x86.codegen.X86CodeGenerator()
        val elf = x86Gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertEquals(0x7f, elf[0].toInt() and 0xFF) // ELF magic
        assertEquals('E'.code, elf[1].toInt() and 0xFF)

        // Generate x86-64 PE — same IR, Windows target triple
        val winModule = module.copy(targetTriple = "x86_64-unknown-windows-msvc")
        val peBinary = x86Gen.generate(winModule, CodeGenOptions(outputFormat = OutputFormat.BINARY))
        assertEquals('M'.code, peBinary[0].toInt() and 0xFF) // MZ header
        assertEquals('Z'.code, peBinary[1].toInt() and 0xFF)

        // Three distinct binaries from one IR
        assertNotEquals(wasm.size, elf.size)
        assertNotEquals(elf.size, peBinary.size)
    }

    @Test
    fun `generates void function`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function returning i64`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("add64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function returning f32`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("addf32", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function returning f64`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("addf64", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function with multiple parameters`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("sum4", listOf(
            Param("a", Type.I32), Param("b", Type.I32),
            Param("c", Type.I32), Param("d", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val ab = ir.add(params[0], params[1])
        val cd = ir.add(params[2], params[3])
        val total = ir.add(ab, cd)
        ir.ret(total)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function with mixed parameter types`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("mixed", listOf(
            Param("i", Type.I32), Param("f", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        // Just return the float param
        ir.ret(params[1])
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates multiple functions in one module`() {
        val ir = IrBuilder("test", Target.wasm())

        // Function 1: add
        val addParams = ir.createFunction("add", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        // Function 2: sub
        val subParams = ir.createFunction("sub", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.sub(subParams[0], subParams[1]))
        ir.finalizeFunction()

        // Function 3: mul
        val mulParams = ir.createFunction("mul", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.mul(mulParams[0], mulParams[1]))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates i64 arithmetic`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("arith64", listOf(
            Param("x", Type.I64), Param("y", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val diff = ir.sub(sum, Constant.I64(1L))
        val prod = ir.mul(diff, Constant.I64(3L))
        ir.ret(prod)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates float arithmetic f32`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("floatMath", listOf(
            Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        val diff = ir.fsub(sum, Constant.F32(1.0f))
        val prod = ir.fmul(diff, Constant.F32(2.0f))
        val quot = ir.fdiv(prod, params[1])
        ir.ret(quot)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates float arithmetic f64`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("doubleMath", listOf(
            Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        val diff = ir.fsub(sum, Constant.F64(1.0))
        val prod = ir.fmul(diff, Constant.F64(2.0))
        val quot = ir.fdiv(prod, params[1])
        ir.ret(quot)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates bitwise operations i32`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("bitwise", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val anded = ir.and(params[0], params[1])
        val ored = ir.or(anded, Constant.I32(0xFF))
        val xored = ir.xor(ored, params[1])
        val shifted = ir.shl(xored, Constant.I32(2))
        ir.ret(shifted)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates bitwise operations i64`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("bitwise64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val anded = ir.and(params[0], params[1])
        val ored = ir.or(anded, Constant.I64(0xFFL))
        val xored = ir.xor(ored, params[1])
        val shifted = ir.shl(xored, Constant.I64(4L))
        ir.ret(shifted)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates icmp EQ`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("eq", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, params[0], params[1])
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates icmp NE`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("ne", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.NE, params[0], params[1])
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates all signed icmp predicates`() {
        for (pred in listOf(ICmpPredicate.SLT, ICmpPredicate.SLE, ICmpPredicate.SGT, ICmpPredicate.SGE)) {
            val ir = IrBuilder("test", Target.wasm())
            val params = ir.createFunction("cmp_${pred.name}", listOf(
                Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(pred, params[0], params[1])
            val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
            ir.ret(result)
            ir.finalizeFunction()

            val wasm = WasmCodeGenerator().generate(ir.build())
            wasmMagic(wasm)
        }
    }

    @Test
    fun `generates all unsigned icmp predicates`() {
        for (pred in listOf(ICmpPredicate.ULT, ICmpPredicate.ULE, ICmpPredicate.UGT, ICmpPredicate.UGE)) {
            val ir = IrBuilder("test", Target.wasm())
            val params = ir.createFunction("cmp_${pred.name}", listOf(
                Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(pred, params[0], params[1])
            val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
            ir.ret(result)
            ir.finalizeFunction()

            val wasm = WasmCodeGenerator().generate(ir.build())
            wasmMagic(wasm)
        }
    }

    @Test
    fun `generates i64 comparison`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("cmp64", listOf(
            Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, params[0], params[1])
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates select instruction`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("max", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates function call to another defined function`() {
        val ir = IrBuilder("test", Target.wasm())

        // helper function
        val helperParams = ir.createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(helperParams[0], helperParams[0]))
        ir.finalizeFunction()

        // main function calls helper
        val mainParams = ir.createFunction("main", listOf(Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("double", listOf(mainParams[0]), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates call to imported function with return value`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.declareFunction("getInput", emptyList(), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val input = ir.call("getInput", emptyList(), Type.I32)
        val result = ir.add(input!!, Constant.I32(1))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates constant return values`() {
        val ir = IrBuilder("test", Target.wasm())

        ir.createFunction("constI32", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        ir.createFunction("constI64", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(9999999999L))
        ir.finalizeFunction()

        ir.createFunction("constF32", emptyList(), Type.F32)
        ir.appendBlock("entry")
        ir.ret(Constant.F32(3.14f))
        ir.finalizeFunction()

        ir.createFunction("constF64", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(2.71828))
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates boolean constants`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("boolSelect", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.select(Constant.I1(true), Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates internal linkage function as non-export`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("helper", emptyList(), Type.I32,
            linkage = Linkage.INTERNAL)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        // An exported function
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("helper", emptyList(), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates complex expression chain`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("complex", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        // result = (a + b) * c - (a & b) | (b ^ c)
        val sum = ir.add(params[0], params[1])
        val prod = ir.mul(sum, params[2])
        val anded = ir.and(params[0], params[1])
        val diff = ir.sub(prod, anded)
        val xored = ir.xor(params[1], params[2])
        val result = ir.or(diff, xored)
        ir.ret(result)
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generates multiple imports`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.declareFunction("print_i32", listOf(Param("v", Type.I32)), Type.Void)
        ir.declareFunction("print_f64", listOf(Param("v", Type.F64)), Type.Void)
        ir.declareFunction("read_i32", emptyList(), Type.I32)

        ir.createFunction("main", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val v = ir.call("read_i32", emptyList(), Type.I32)
        ir.call("print_i32", listOf(v!!), Type.Void)
        ir.call("print_f64", listOf(Constant.F64(3.14)), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val wasm = WasmCodeGenerator().generate(ir.build())
        wasmMagic(wasm)
    }

    @Test
    fun `generated WASM has deterministic output`() {
        val ir1 = IrBuilder("test", Target.wasm())
        ir1.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir1.appendBlock("entry")
        ir1.ret(ir1.add(ir1.param(0), Constant.I32(1)))
        ir1.finalizeFunction()

        val ir2 = IrBuilder("test", Target.wasm())
        ir2.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir2.appendBlock("entry")
        ir2.ret(ir2.add(ir2.param(0), Constant.I32(1)))
        ir2.finalizeFunction()

        val wasm1 = WasmCodeGenerator().generate(ir1.build())
        val wasm2 = WasmCodeGenerator().generate(ir2.build())
        assertArrayEquals(wasm1, wasm2)
    }
}

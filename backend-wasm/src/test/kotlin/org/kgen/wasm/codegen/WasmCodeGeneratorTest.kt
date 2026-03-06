package org.kgen.wasm.codegen

import org.kgen.wasm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.codegen.*
import org.kgen.ir.target.Target

class WasmCodeGeneratorTest {

    @Test
    fun `generates valid WASM from IR add function`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)

        // Verify WASM magic
        assertEquals(0x00, wasm[0].toInt() and 0xFF)
        assertEquals(0x61, wasm[1].toInt() and 0xFF) // 'a'
        assertEquals(0x73, wasm[2].toInt() and 0xFF) // 's'
        assertEquals(0x6D, wasm[3].toInt() and 0xFF) // 'm'

        assertTrue(wasm.size > 8)
    }

    @Test
    fun `generates WASM with external imports`() {
        val ir = IrBuilder("imports", Target.wasm())
        ir.declareFunction("log", listOf(Param("v", Type.I32)), Type.Void)

        ir.createFunction("main", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call("log", listOf(Constant.I32(42)), Type.Void)
        ir.ret()
        ir.finalizeFunction()

        val module = ir.build()
        val gen = WasmCodeGenerator()
        val wasm = gen.generate(module)

        // Should have import section (section ID 2)
        assertTrue(wasm.size > 8)
    }

    @Test
    fun `generates WASM with arithmetic`() {
        val ir = IrBuilder("math", Target.wasm())
        val params = ir.createFunction("compute", listOf(
            Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        val module = ir.build()

        // Generate WASM
        val wasm = WasmCodeGenerator().generate(module)
        assertEquals(0x00, wasm[0].toInt() and 0xFF) // WASM magic
        assertEquals(0x61, wasm[1].toInt() and 0xFF)

        // Generate x86-64 ELF .o
        val x86Gen = org.kgen.x86.codegen.X86CodeGenerator()
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
}

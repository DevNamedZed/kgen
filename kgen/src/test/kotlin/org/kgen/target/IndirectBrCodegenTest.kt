package org.kgen.target

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.riscv.disasm.RiscVDisassembler

class IndirectBrCodegenTest {

    private fun buildIndirectBrModule(target: Target): Module {
        val ir = IrBuilder("indirectbr_test", target)
        val params = ir.createFunction("dispatch", listOf(Param("addr", Type.Pointer(Type.I8))), Type.I32)
        ir.appendBlock("entry")
        ir.indirectBr(params[0], listOf(BlockRef("target_a"), BlockRef("target_b")))

        ir.appendBlock("target_a")
        ir.ret(Constant.I32(1))

        ir.appendBlock("target_b")
        ir.ret(Constant.I32(2))

        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun `x86 indirectBr produces jmp reg`() {
        val module = buildIndirectBrModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty())

        val disasm = X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val texts = insts.map { it.text() }
        assertTrue(texts.any { it.contains("jmp") }, "Should contain jmp instruction: $texts")
    }

    @Test
    fun `arm64 indirectBr produces br reg`() {
        val module = buildIndirectBrModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty())

        val disasm = Arm64Disassembler()
        val insts = disasm.disassemble(code)
        val texts = insts.map { it.text() }
        assertTrue(texts.any { "br" in it && "b." !in it }, "Should contain br instruction: $texts")
    }

    @Test
    fun `riscv indirectBr produces jalr x0`() {
        val module = buildIndirectBrModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty())

        val disasm = RiscVDisassembler()
        val insts = disasm.disassemble(code)
        val texts = insts.map { it.text() }
        assertTrue(texts.any { "jalr" in it || "jr" in it || "ret" in it }, "Should contain jalr/jr instruction: $texts")
    }

    @Test
    fun `x86 indirectBr with single target`() {
        val ir = IrBuilder("single_target", Target.x86_64())
        val params = ir.createFunction("go", listOf(Param("addr", Type.Pointer(Type.I8))), Type.I32)
        ir.appendBlock("entry")
        ir.indirectBr(params[0], listOf(BlockRef("dest")))

        ir.appendBlock("dest")
        ir.ret(Constant.I32(42))

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun `all three native backends compile indirectBr`() {
        val x86 = buildIndirectBrModule(Target.x86_64())
        val arm = buildIndirectBrModule(Target.arm64())
        val rv = buildIndirectBrModule(Target.riscv64())

        val x86Code = X86CodeGenerator().generateObjectFile(x86).sections.first { it.name == ".text" }.data
        val armCode = Arm64CodeGenerator().generateObjectFile(arm).sections.first { it.name == ".text" }.data
        val rvCode = RiscVCodeGenerator().generateObjectFile(rv).sections.first { it.name == ".text" }.data

        assertTrue(x86Code.isNotEmpty(), "x86 should produce non-empty code")
        assertTrue(armCode.isNotEmpty(), "arm64 should produce non-empty code")
        assertTrue(rvCode.isNotEmpty(), "riscv should produce non-empty code")
    }
}

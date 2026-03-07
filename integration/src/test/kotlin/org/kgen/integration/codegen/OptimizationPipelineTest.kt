package org.kgen.integration.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.pass.OptLevel
import org.kgen.backend.x86.codegen.X86CodeGenerator

class OptimizationPipelineTest {

    @Test
    fun o2PipelineThenCodegen() {
        val ir = IrBuilder("opt_test", Target.x86_64())
        val params = ir.createFunction("compute", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))

        val slot = ir.alloca(Type.I32)
        val sum = ir.add(params[0], params[1])
        ir.store(sum, slot)
        val loaded = ir.load(Type.I32, slot)

        val zero = ir.add(loaded, Constant.I32(0))
        val doubled = ir.add(zero, zero)
        val result = ir.sub(doubled, params[2])

        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val optimized = OptLevel.O2.pipeline().execute(module)

        val fn = optimized.functions[0]
        val hasAlloca = fn.blocks.any { b -> b.instructions.any { it is Instruction.Alloca } }
        assertFalse(hasAlloca, "Alloca should be promoted by mem2reg")

        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())
        assertEquals(0x55, code[0].toInt() and 0xFF, "push rbp")
        assertEquals(0xC3, code.last().toInt() and 0xFF, "ret")

        val disasm = org.kgen.backend.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun o2PipelineWithBranches() {
        val ir = IrBuilder("phi_opt", Target.x86_64())
        val params = ir.createFunction("abs_add", listOf(
            Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))

        val slot = ir.alloca(Type.I32)
        val cond = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        ir.condBr(cond, "negate", "keep")

        ir.positionAtEnd(ir.appendBlock("negate"))
        val neg = ir.sub(Constant.I32(0), params[0])
        ir.store(neg, slot)
        ir.br("merge")

        ir.positionAtEnd(ir.appendBlock("keep"))
        ir.store(params[0], slot)
        ir.br("merge")

        ir.positionAtEnd(ir.appendBlock("merge"))
        val absX = ir.load(Type.I32, slot)
        val result = ir.add(absX, params[1])
        ir.ret(result)

        ir.finalizeFunction()

        val module = ir.build()
        val optimized = OptLevel.O2.pipeline().execute(module)

        val fn = optimized.functions[0]
        val hasPhi = fn.blocks.any { b -> b.instructions.any { it is Instruction.Phi } }
        assertTrue(hasPhi, "Mem2reg should produce phi: ${fn.blocks.map { it.label to it.instructions }}")

        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())
        assertEquals(0xC3, code.last().toInt() and 0xFF, "ret")

        val disasm = org.kgen.backend.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "Clean decode: ${insts.map { it.text() }}")
    }
}

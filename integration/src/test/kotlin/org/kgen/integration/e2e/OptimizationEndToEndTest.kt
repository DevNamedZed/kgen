package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.binary.SectionKind
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.pass.OptLevel
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Optimization pipeline end-to-end: IR → O2 passes → codegen → verify correctness.
 * Verifies that optimized code produces the same results as unoptimized code.
 */
class OptimizationEndToEndTest {

    // -- Constant folding --

    @Test
    fun constantFoldingProducesSmallerCode() {
        val unoptModule = buildConstantExprModule()
        val optModule = OptLevel.O2.pipeline().execute(buildConstantExprModule())

        val unoptObj = X86CodeGenerator().generateObjectFile(unoptModule)
        val optObj = X86CodeGenerator().generateObjectFile(optModule)

        val unoptSize = unoptObj.sections.first { it.kind == SectionKind.TEXT }.data.size
        val optSize = optObj.sections.first { it.kind == SectionKind.TEXT }.data.size

        assertTrue(optSize <= unoptSize,
            "Optimized code ($optSize) should be <= unoptimized ($unoptSize)")
    }

    // -- Mem2reg --

    @Test
    fun mem2regEliminatesAllocas() {
        val module = buildAllocaModule()
        val optimized = OptLevel.O2.pipeline().execute(module)

        val fn = optimized.functions[0]
        val hasAlloca = fn.blocks.any { b -> b.instructions.any { it is Instruction.Alloca } }
        assertFalse(hasAlloca, "Mem2reg should eliminate allocas")

        // Verify the optimized code still compiles
        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections.first { it.kind == SectionKind.TEXT }.data
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun mem2regIntroducesPhiNodes() {
        val module = buildPhiCandidateModule()
        val optimized = OptLevel.O2.pipeline().execute(module)

        val fn = optimized.functions[0]
        val hasPhi = fn.blocks.any { b -> b.instructions.any { it is Instruction.Phi } }
        assertTrue(hasPhi, "Mem2reg should produce phi nodes for branching stores")
    }

    // -- Optimized execution (JIT) --

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun optimizedCodeProducesCorrectResults() {
        // Build unoptimized and optimized versions
        val unoptModule = buildComputeModule()
        val optModule = OptLevel.O2.pipeline().execute(buildComputeModule())

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addModule(optModule)

            // Verify the optimized code produces correct results
            assertEquals(10L, jit.call("compute", 5))   // 5*2 = 10
            assertEquals(0L, jit.call("compute", 0))    // 0*2 = 0
            assertEquals(-20L, jit.call("compute", -10)) // -10*2 = -20
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun optimizedBranchingProducesCorrectResults() {
        val optModule = OptLevel.O2.pipeline().execute(buildBranchModule())

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addModule(optModule)

            assertEquals(5L, jit.call("abs_val", 5))
            assertEquals(5L, jit.call("abs_val", -5))
            assertEquals(0L, jit.call("abs_val", 0))
            assertEquals(100L, jit.call("abs_val", -100))
        }
    }

    // -- Dead code elimination --

    @Test
    fun deadCodeEliminationRemovesUnusedInstructions() {
        val module = buildDeadCodeModule()
        val optimized = OptLevel.O2.pipeline().execute(module)

        val fn = optimized.functions[0]
        val totalInsts = fn.blocks.sumOf { it.instructions.size }

        // The dead mul instruction should be eliminated
        val hasMul = fn.blocks.any { b ->
            b.instructions.any { it is Instruction.Mul }
        }
        assertFalse(hasMul, "Dead mul should be eliminated")
    }

    // -- Disassembly validation --

    @Test
    fun optimizedCodeDisassemblesCleanly() {
        val optimized = OptLevel.O2.pipeline().execute(buildComputeModule())
        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections.first { it.kind == SectionKind.TEXT }.data

        val insts = X86Disassembler().disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes should decode: ${insts.map { it.text() }}")

        // Should end with ret
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")
    }

    // -- Idempotency --

    @Test
    fun optimizingTwiceProducesSameResult() {
        val pipeline = OptLevel.O2.pipeline()
        val module = buildComputeModule()

        val opt1 = pipeline.execute(module)
        val opt2 = pipeline.execute(opt1)

        val code1 = X86CodeGenerator().generateObjectFile(opt1).sections.first { it.kind == SectionKind.TEXT }.data
        val code2 = X86CodeGenerator().generateObjectFile(opt2).sections.first { it.kind == SectionKind.TEXT }.data

        assertArrayEquals(code1, code2, "Optimizing twice should produce identical code")
    }

    // -- Helpers --

    private fun buildConstantExprModule(): Module {
        val ir = IrBuilder("const_fold", Target.x86_64())
        ir.createFunction("constant", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = ir.add(Constant.I64(10), Constant.I64(20))
        val b = ir.mul(a, Constant.I64(3))
        ir.ret(b)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildAllocaModule(): Module {
        val ir = IrBuilder("alloca_test", Target.x86_64())
        val p = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val slot = ir.alloca(Type.I32)
        ir.store(p[0], slot)
        val loaded = ir.load(Type.I32, slot)
        ir.ret(loaded)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildPhiCandidateModule(): Module {
        val ir = IrBuilder("phi_test", Target.x86_64())
        val p = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))

        val slot = ir.alloca(Type.I32)
        val cond = ir.icmp(ICmpPredicate.SGT, p[0], Constant.I32(0))
        ir.condBr(cond, "pos", "neg")

        ir.positionAtEnd(ir.appendBlock("pos"))
        ir.store(p[0], slot)
        ir.br("merge")

        ir.positionAtEnd(ir.appendBlock("neg"))
        val neg = ir.sub(Constant.I32(0), p[0])
        ir.store(neg, slot)
        ir.br("merge")

        ir.positionAtEnd(ir.appendBlock("merge"))
        val result = ir.load(Type.I32, slot)
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildComputeModule(): Module {
        val ir = IrBuilder("compute_mod", Target.x86_64())
        val p = ir.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val slot = ir.alloca(Type.I64)
        ir.store(p[0], slot)
        val loaded = ir.load(Type.I64, slot)
        val doubled = ir.add(loaded, loaded)
        ir.ret(doubled)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildBranchModule(): Module {
        val ir = IrBuilder("branch_mod", Target.x86_64())
        val p = ir.createFunction("abs_val", listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))

        val slot = ir.alloca(Type.I64)
        val cond = ir.icmp(ICmpPredicate.SGE, p[0], Constant.I64(0))
        ir.condBr(cond, "pos", "neg")

        ir.positionAtEnd(ir.appendBlock("pos"))
        ir.store(p[0], slot)
        ir.br("done")

        ir.positionAtEnd(ir.appendBlock("neg"))
        val neg = ir.sub(Constant.I64(0), p[0])
        ir.store(neg, slot)
        ir.br("done")

        ir.positionAtEnd(ir.appendBlock("done"))
        val result = ir.load(Type.I64, slot)
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildDeadCodeModule(): Module {
        val ir = IrBuilder("dce_test", Target.x86_64())
        val p = ir.createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val dead = ir.mul(p[0], Constant.I64(999)) // unused
        val result = ir.add(p[0], Constant.I64(1))
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }
}

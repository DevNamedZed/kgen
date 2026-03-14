package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.pass.ConstantFolding
import org.kgen.pass.DeadCodeElimination
import org.kgen.pass.PassPipeline

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class TieredCompilationTest {

    private fun buildAddModule(): Module {
        val ir = IrBuilder("add_module", Target.x86_64())
        ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.add(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildConstantModule(name: String, funcName: String, value: Long): Module {
        val ir = IrBuilder(name, Target.x86_64())
        ir.createFunction(funcName, emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun tieredRecompilationTriggersAtThreshold() {
        val jit = JitEngine(X86CodeGenerator())
        val tiered = TieredCompilation(recompileThreshold = 5)
        val pipeline = PassPipeline()
        pipeline.add(ConstantFolding())
        pipeline.add(DeadCodeElimination())
        tiered.setTier1Pipeline(pipeline)
        jit.setTieredCompilation(tiered)

        jit.addModule(buildAddModule())

        // Call 4 times — no recompilation yet
        for (i in 1..4) {
            assertEquals(i + 10L, jit.call("add", i.toLong(), 10))
        }
        assertFalse(tiered.isRecompiled("add"))
        assertEquals(4, tiered.callCount("add"))

        // 5th call triggers recompilation
        assertEquals(15L, jit.call("add", 5, 10))
        assertTrue(tiered.isRecompiled("add"))

        // Continues working after recompilation
        assertEquals(20L, jit.call("add", 8, 12))

        jit.close()
    }

    @Test
    fun recompilationOnlyHappensOnce() {
        val jit = JitEngine(X86CodeGenerator())
        val tiered = TieredCompilation(recompileThreshold = 3)
        jit.setTieredCompilation(tiered)

        jit.addModule(buildConstantModule("m", "getValue", 42))

        // Call past threshold multiple times
        for (i in 1..10) {
            assertEquals(42L, jit.call("getValue"))
        }
        assertTrue(tiered.isRecompiled("getValue"))
        // Module count should be 1 (old removed, new added)
        assertEquals(1, jit.modules().size)

        jit.close()
    }

    @Test
    fun callCountTracking() {
        val tiered = TieredCompilation(recompileThreshold = 100)
        assertEquals(0, tiered.callCount("foo"))
        assertFalse(tiered.isRecompiled("foo"))
    }

    @Test
    fun resetClearsState() {
        val tiered = TieredCompilation(recompileThreshold = 5)
        tiered.registerModule("foo", buildAddModule())
        tiered.recordCall("foo")
        tiered.recordCall("foo")
        assertEquals(2, tiered.callCount("foo"))

        tiered.reset()
        assertEquals(0, tiered.callCount("foo"))
        assertFalse(tiered.isRecompiled("foo"))
    }

    @Test
    fun noTieredStillWorks() {
        val jit = JitEngine(X86CodeGenerator())
        // No tiered compilation set
        jit.addModule(buildAddModule())
        assertEquals(7L, jit.call("add", 3, 4))
        jit.close()
    }

    @Test
    fun tieredWithPipeline() {
        val jit = JitEngine(X86CodeGenerator())
        val tiered = TieredCompilation(recompileThreshold = 2)

        val tier0Pipeline = PassPipeline() // no passes
        val tier1Pipeline = PassPipeline()
        tier1Pipeline.add(ConstantFolding())
        tier1Pipeline.add(DeadCodeElimination())
        tiered.setTier1Pipeline(tier1Pipeline)

        jit.setOptimizationPipeline(tier0Pipeline)
        jit.setTieredCompilation(tiered)

        jit.addModule(buildAddModule())
        assertEquals(7L, jit.call("add", 3, 4))
        assertEquals(11L, jit.call("add", 5, 6))
        // Now recompiled with tier1
        assertTrue(tiered.isRecompiled("add"))
        assertEquals(30L, jit.call("add", 10, 20))

        jit.close()
    }

    @Test
    fun multipleFunctionsIndependentCounts() {
        val jit = JitEngine(X86CodeGenerator())
        val tiered = TieredCompilation(recompileThreshold = 3)
        jit.setTieredCompilation(tiered)

        val ir = IrBuilder("multi", Target.x86_64())
        ir.createFunction("f1", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(1))
        ir.finalizeFunction()

        ir.createFunction("f2", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(2))
        ir.finalizeFunction()

        jit.addModule(ir.build())

        // Call f1 three times (triggers recompile), f2 only once
        jit.call("f1")
        jit.call("f1")
        jit.call("f2")
        jit.call("f1") // triggers recompile of f1's module

        assertTrue(tiered.isRecompiled("f1"))
        // f2 may also be recompiled since it's in the same module
        // but independently, its count is only 1
        assertEquals(1, tiered.callCount("f2"))

        jit.close()
    }
}

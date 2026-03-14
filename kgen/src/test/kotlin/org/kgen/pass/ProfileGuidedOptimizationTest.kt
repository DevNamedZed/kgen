package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class ProfileGuidedOptimizationTest {

    private fun buildSimpleModule(): Module {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("hotFunc", listOf(Param("x", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        builder.ret(params[0])
        builder.finalizeFunction()

        val params2 = builder.createFunction("coldFunc", listOf(Param("x", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        builder.ret(params2[0])
        builder.finalizeFunction()

        return builder.build()
    }

    private fun buildModuleWithBranch(): Module {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("branchFunc", listOf(Param("x", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        val cmp = builder.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        builder.condBr(cmp, BlockRef("then"), BlockRef("else"))

        builder.appendBlock("then")
        builder.ret(Constant.I32(1))

        builder.appendBlock("else")
        builder.ret(Constant.I32(0))

        builder.finalizeFunction()
        return builder.build()
    }

    @Test
    fun emptyProfileNoChange() {
        val module = buildSimpleModule()
        val pgo = ProfileGuidedOptimization()
        val optimized = pgo.run(module)
        assertSame(module, optimized)
    }

    @Test
    fun hotFunctionAnnotated() {
        val module = buildSimpleModule()
        val profile = ProfileData.of(
            functionCounts = mapOf("hotFunc" to 5000, "coldFunc" to 5),
            hotThreshold = 1000,
            coldThreshold = 10,
        )
        val pgo = ProfileGuidedOptimization(profile)
        val optimized = pgo.run(module)

        val hotFunc = optimized.functions.first { it.name == "hotFunc" }
        assertTrue(hotFunc.attributes.contains(FnAttribute.HOT))
        assertFalse(hotFunc.attributes.contains(FnAttribute.COLD))
    }

    @Test
    fun coldFunctionAnnotated() {
        val module = buildSimpleModule()
        val profile = ProfileData.of(
            functionCounts = mapOf("hotFunc" to 5000, "coldFunc" to 5),
            hotThreshold = 1000,
            coldThreshold = 10,
        )
        val pgo = ProfileGuidedOptimization(profile)
        val optimized = pgo.run(module)

        val coldFunc = optimized.functions.first { it.name == "coldFunc" }
        assertTrue(coldFunc.attributes.contains(FnAttribute.COLD))
        assertFalse(coldFunc.attributes.contains(FnAttribute.HOT))
    }

    @Test
    fun branchWeightsPropagated() {
        val module = buildModuleWithBranch()
        val profile = ProfileData.of(
            functionCounts = mapOf("branchFunc" to 1000),
            branchCounts = mapOf(
                "branchFunc:entry" to ProfileData.BranchData(trueCount = 900, falseCount = 100)
            ),
        )
        val pgo = ProfileGuidedOptimization(profile)
        val optimized = pgo.run(module)

        val fn = optimized.functions.first { it.name == "branchFunc" }
        val condBr = fn.blocks.first { it.label == "entry" }.instructions
            .filterIsInstance<CondBr>().first()

        assertEquals(900, condBr.trueWeight)
        assertEquals(100, condBr.falseWeight)
    }

    @Test
    fun instrumentInsertsCounters() {
        val module = buildSimpleModule()
        val instrumented = ProfileGuidedOptimization.instrument(module)

        val fn = instrumented.functions.first { it.name == "hotFunc" }
        val entryBlock = fn.blocks.first()
        val firstInst = entryBlock.instructions.first()

        assertTrue(firstInst is Call, "First instruction should be a counter call")
        val call = firstInst as Call
        assertEquals("__pgo_increment_counter", call.function.name)
    }

    @Test
    fun profileDataEmpty() {
        val profile = ProfileData.empty()
        assertTrue(profile.isEmpty())
        assertEquals(0, profile.functionExecutionCount("foo"))
        assertNull(profile.branchData("foo:entry"))
    }

    @Test
    fun profileDataOf() {
        val profile = ProfileData.of(
            functionCounts = mapOf("main" to 100, "helper" to 50),
            branchCounts = mapOf("main:entry" to ProfileData.BranchData(80, 20)),
        )

        assertFalse(profile.isEmpty())
        assertEquals(100, profile.functionExecutionCount("main"))
        assertEquals(50, profile.functionExecutionCount("helper"))
        assertEquals(0, profile.functionExecutionCount("unknown"))

        val branchData = profile.branchData("main:entry")!!
        assertEquals(80, branchData.trueCount)
        assertEquals(20, branchData.falseCount)
        assertEquals(0.8, branchData.trueProbability, 0.01)
    }

    @Test
    fun profileDataToString() {
        val profile = ProfileData.of(
            functionCounts = mapOf("a" to 10, "b" to 20),
        )
        val str = profile.toString()
        assertTrue(str.contains("2 functions"))
        assertTrue(str.contains("30 samples"))
    }

    @Test
    fun profiledFunctions() {
        val profile = ProfileData.of(
            functionCounts = mapOf("foo" to 10, "bar" to 20),
        )
        assertEquals(setOf("foo", "bar"), profile.profiledFunctions())
    }

    @Test
    fun branchDataProbabilityZeroTotal() {
        val data = ProfileData.BranchData(0, 0)
        assertEquals(0.5, data.trueProbability, 0.01)
    }
}

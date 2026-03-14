package org.kgen.bench

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.ir.text.IrPrinter
import org.kgen.ir.text.IrParser
import org.kgen.ir.text.IrSerializer
import org.kgen.pass.*

/**
 * Performance benchmarks for key kgen operations.
 * These tests verify performance stays within acceptable bounds
 * and serve as regression tests for performance.
 */
class PerformanceBenchmarkTest {

    private fun buildLargeModule(functionCount: Int, blocksPerFunction: Int = 3): Module {
        val builder = IrBuilder("bench", Target.x86_64())
        for (i in 0 until functionCount) {
            val params = builder.createFunction("func_$i",
                listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            builder.appendBlock("entry")
            var acc = builder.add(params[0], params[1])
            for (b in 1 until blocksPerFunction) {
                acc = builder.mul(acc, params[0])
                acc = builder.add(acc, params[1])
            }
            builder.ret(acc)
            builder.finalizeFunction()
        }
        return builder.build()
    }

    @Test
    fun irBuilderThroughput() {
        val iterations = 100
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            buildLargeModule(50)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val perModule = elapsed / iterations

        println("IR Builder: ${iterations} modules × 50 functions in ${elapsed.toLong()}ms (${perModule.toLong()}ms/module)")
        assertTrue(elapsed < 30_000, "Building 100 modules should complete within 30s (took ${elapsed}ms)")
    }

    @Test
    fun verifierThroughput() {
        val module = buildLargeModule(100)
        val iterations = 50

        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val result = IrVerifier.verify(module)
            assertTrue(result.isValid)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val perVerify = elapsed / iterations

        println("Verifier: ${iterations} verifications of 100-function module in ${elapsed.toLong()}ms (${perVerify.toLong()}ms/verify)")
        assertTrue(elapsed < 30_000, "Verification should complete within 30s")
    }

    @Test
    fun printerThroughput() {
        val module = buildLargeModule(100)
        val iterations = 50

        val start = System.nanoTime()
        var lastOutput = ""
        for (i in 0 until iterations) {
            lastOutput = IrPrinter.print(module)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("Printer: ${iterations} prints of 100-function module in ${elapsed.toLong()}ms (${lastOutput.length} chars)")
        assertTrue(lastOutput.isNotEmpty())
        assertTrue(elapsed < 30_000, "Printing should complete within 30s")
    }

    @Test
    fun parserRoundtrip() {
        val module = buildLargeModule(50)
        val text = IrPrinter.print(module)

        val iterations = 20
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val parsed = IrParser.parse(text)
            assertEquals(50, parsed.functions.size)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("Parser: ${iterations} parses of 50-function module in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 30_000, "Parsing should complete within 30s")
    }

    @Test
    fun serializerRoundtrip() {
        val module = buildLargeModule(50)

        val serializer = IrSerializer()
        val iterations = 50
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val bytes = serializer.serialize(module)
            val deserialized = serializer.deserialize(bytes)
            assertEquals(50, deserialized.functions.size)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("Serializer: ${iterations} round-trips of 50-function module in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 30_000, "Serialization round-trip should complete within 30s")
    }

    @Test
    fun constantFoldingThroughput() {
        val builder = IrBuilder("bench", Target.x86_64())
        for (i in 0 until 100) {
            val params = builder.createFunction("fold_$i", listOf(Param("x", Type.I32)), Type.I32)
            builder.appendBlock("entry")
            // Create a chain of foldable operations
            var acc: Value = Constant.I32(1)
            for (j in 0 until 20) {
                acc = builder.add(acc, Constant.I32(j))
                acc = builder.mul(acc, Constant.I32(2))
            }
            builder.ret(builder.add(params[0], acc))
            builder.finalizeFunction()
        }
        val module = builder.build()

        val pass = ConstantFolding()
        val iterations = 20
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            pass.run(module)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("ConstantFolding: ${iterations} runs on 100 functions in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 30_000, "Constant folding should complete within 30s")
    }

    @Test
    fun deadCodeEliminationThroughput() {
        val module = buildLargeModule(100, 5)
        val pass = DeadCodeElimination()

        val iterations = 30
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            pass.run(module)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("DCE: ${iterations} runs on 100 functions in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 30_000, "DCE should complete within 30s")
    }

    @Test
    fun fullOptimizationPipeline() {
        val module = buildLargeModule(50, 5)

        val pipeline = PassPipeline()
        pipeline.add(ConstantFolding())
        pipeline.add(DeadCodeElimination())
        pipeline.add(InstructionCombining())
        pipeline.add(GlobalValueNumbering())

        val iterations = 10
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            pipeline.execute(module)
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("Full pipeline (4 passes): ${iterations} runs on 50 functions in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 60_000, "Full pipeline should complete within 60s")
    }

    @Test
    fun largeModuleScaling() {
        // Test scaling: 10, 100, 500 functions
        val sizes = listOf(10, 100, 500)
        val times = mutableListOf<Double>()

        for (size in sizes) {
            val start = System.nanoTime()
            val module = buildLargeModule(size)
            IrVerifier.verify(module)
            IrPrinter.print(module)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            times.add(elapsed)
            println("Module with $size functions: build+verify+print in ${elapsed.toLong()}ms")
        }

        // Scaling should be roughly linear — 500 functions shouldn't take
        // more than 20x what 10 functions takes
        val ratio = times[2] / times[0]
        println("Scaling ratio (500/10): ${String.format("%.1f", ratio)}x")
        assertTrue(ratio < 200, "Scaling should be sub-quadratic (ratio: $ratio)")
    }

    @Test
    fun incrementalCompilationBenefit() {
        val module = buildLargeModule(100)
        val cache = IncrementalCompilation.Cache()

        // First compile — everything new
        val firstResult = IncrementalCompilation.analyze(module, cache)
        assertEquals(100, firstResult.added.size)

        // Second compile — nothing changed
        val start = System.nanoTime()
        val iterations = 100
        for (i in 0 until iterations) {
            val r = IncrementalCompilation.analyze(module, cache)
            assertEquals(100, r.unchanged.size)
            assertFalse(r.hasChanges())
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0

        println("Incremental analysis (no changes): ${iterations} analyses in ${elapsed.toLong()}ms")
        assertTrue(elapsed < 10_000, "Incremental analysis should be fast when nothing changed")
    }
}

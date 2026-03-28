package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.codegen.alloc.LivenessAnalysis
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class SnakeLivenessTest {

    @Test
    fun func14LivenessVerification() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        // Compile to get the IR
        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())
        val irModule = instance.compiledIr() ?: return

        // Find func_14
        val func14 = irModule.functions.find { it.name == "func_14" }
        if (func14 == null) {
            println("func_14 not found")
            return
        }

        println("func_14: ${func14.params.size} params, ${func14.blocks.size} blocks")
        var totalInstructions = 0
        for (block in func14.blocks) {
            totalInstructions += block.instructions.size
        }
        println("total IR instructions: $totalInstructions")

        // Compute liveness
        val liveness = LivenessAnalysis(func14)
        val intervals = liveness.intervals()
        println("live intervals: ${intervals.size}")

        // Check: for each instruction, verify all operand values have intervals
        // that cover this instruction position. Must use same block ordering as
        // LivenessAnalysis (RPO order via sortBlocksRPO).
        var instIdx = 0
        val sortedBlocks = org.kgen.codegen.alloc.LivenessAnalysis.sortBlocksRPO(func14)
        var violations = 0
        val intervalMap = intervals.associateBy { it.name }

        for (block in sortedBlocks) {
            for (inst in block.instructions) {
                instIdx++
                // For phi nodes, the incoming values are used at the PREDECESSOR's
                // block end (where phi copies are emitted), not at the phi position.
                // Skip phi operand checks — they're covered by Pass 2 extension.
                if (inst is org.kgen.ir.instructions.Phi) { continue }
                for (operand in inst.operands) {
                    val name = operand.name
                    val interval = intervalMap[name] ?: continue
                    if (instIdx < interval.start || instIdx > interval.end) {
                        violations++
                        if (violations <= 20) {
                            println("VIOLATION: ${inst.javaClass.simpleName} uses '$name' at position $instIdx, " +
                                "but interval is [${interval.start}..${interval.end}]")
                            println("  instruction: $inst")
                        }
                    }
                }
            }
        }

        println("\ntotal liveness violations: $violations")
        if (violations == 0) {
            println("ALL operand uses are within their live intervals")
        }

        // Also check the x86 allocator's allocation
        val allocDump = org.kgen.target.x86.codegen.X86CodeGenerator.dumpAlloc("func_14")
        if (allocDump.startsWith("No allocation")) {
            println("No allocation data for func_14")
        } else {
            // Check for values assigned to the same register whose usage overlaps
            println("\n$allocDump")
        }
    }

    @Test
    fun allSnakeFunctionsLivenessCheck() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())
        val irModule = instance.compiledIr() ?: return

        var totalViolations = 0
        for (fn in irModule.functions) {
            if (fn.isExternal) { continue }

            val liveness = LivenessAnalysis(fn)
            val intervals = liveness.intervals()
            val intervalMap = intervals.associateBy { it.name }

            var instIdx = 0
            val rpoBlocks = org.kgen.codegen.alloc.LivenessAnalysis.sortBlocksRPO(fn)
            for (block in rpoBlocks) {
                for (inst in block.instructions) {
                    instIdx++
                    if (inst is org.kgen.ir.instructions.Phi) { continue }
                    for (operand in inst.operands) {
                        val name = operand.name
                        val interval = intervalMap[name] ?: continue
                        if (instIdx < interval.start || instIdx > interval.end) {
                            totalViolations++
                            if (totalViolations <= 10) {
                                println("VIOLATION in ${fn.name}: ${inst.javaClass.simpleName} uses '$name' at $instIdx, interval=[${interval.start}..${interval.end}]")
                                println("  instruction: $inst")
                            }
                        }
                    }
                }
            }
        }

        println("total violations across all snake functions: $totalViolations")
    }
}

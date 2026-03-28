package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.ir.Module
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmToIrCompiler
import org.kgen.pipeline.Mem2Reg
import java.nio.file.Files
import java.nio.file.Path

class Mem2RegVerificationTest {

    @Test
    fun snakeIrBeforeAndAfterMem2Reg() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)

        val target = Target.native()
        val compiler = WasmToIrCompiler(target, wasmModule)
        val irBefore = compiler.compileAll()

        // Check func_10 before Mem2Reg
        val func10Before = irBefore.functions.find { it.name == "func_10" }
        if (func10Before != null) {
            println("func_10 BEFORE Mem2Reg:")
            println("  blocks: ${func10Before.blocks.size}")
            var totalInstr = 0
            var allocaCount = 0
            var loadCount = 0
            var storeCount = 0
            for (block in func10Before.blocks) {
                totalInstr += block.instructions.size
                for (inst in block.instructions) {
                    if (inst is Alloca) { allocaCount++ }
                    if (inst is Load) { loadCount++ }
                    if (inst is Store) { storeCount++ }
                }
            }
            println("  instructions: $totalInstr, alloca: $allocaCount, load: $loadCount, store: $storeCount")
        }

        // Run Mem2Reg
        val irAfter = Mem2Reg().run(irBefore)

        // Check func_10 after Mem2Reg
        val func10After = irAfter.functions.find { it.name == "func_10" }
        if (func10After != null) {
            println("\nfunc_10 AFTER Mem2Reg:")
            println("  blocks: ${func10After.blocks.size}")
            var totalInstr = 0
            var phiCount = 0
            var allocaCount = 0
            var loadCount = 0
            var storeCount = 0
            for (block in func10After.blocks) {
                totalInstr += block.instructions.size
                for (inst in block.instructions) {
                    if (inst is Phi) { phiCount++ }
                    if (inst is Alloca) { allocaCount++ }
                    if (inst is Load) { loadCount++ }
                    if (inst is Store) { storeCount++ }
                }
            }
            println("  instructions: $totalInstr, phi: $phiCount, alloca: $allocaCount, load: $loadCount, store: $storeCount")

            // Verify: no alloca should remain after Mem2Reg (all promoted)
            if (allocaCount > 0) {
                println("  WARNING: $allocaCount allocas remain after Mem2Reg!")
            }

            // Check for dangling references: values used but not defined
            val defined = mutableSetOf<String>()
            for (param in func10After.params) {
                defined.add(param.name)
            }
            for (block in func10After.blocks) {
                for (inst in block.instructions) {
                    val result = inst.result
                    if (result != null) {
                        defined.add(result.name)
                    }
                }
            }

            var danglingCount = 0
            for (block in func10After.blocks) {
                for (inst in block.instructions) {
                    for (operand in inst.operands) {
                        if (operand is org.kgen.ir.InstructionRef && operand.name !in defined) {
                            danglingCount++
                            if (danglingCount <= 5) {
                                println("  DANGLING: ${inst.javaClass.simpleName} uses undefined '${operand.name}'")
                                println("    instruction: $inst")
                            }
                        }
                    }
                }
            }
            println("  dangling references: $danglingCount")
        }
    }

    @Test
    fun allSnakeFunctionsMem2RegVerification() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)

        val target = Target.native()
        val compiler = WasmToIrCompiler(target, wasmModule)
        val irBefore = compiler.compileAll()
        val irAfter = Mem2Reg().run(irBefore)

        var totalDangling = 0
        for (fn in irAfter.functions) {
            if (fn.isExternal) { continue }

            val defined = mutableSetOf<String>()
            for (param in fn.params) { defined.add(param.name) }
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    val result = inst.result
                    if (result != null) { defined.add(result.name) }
                }
            }

            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    for (operand in inst.operands) {
                        if (operand is org.kgen.ir.InstructionRef && operand.name !in defined) {
                            totalDangling++
                            if (totalDangling <= 10) {
                                println("DANGLING in ${fn.name}: ${inst.javaClass.simpleName} uses '${operand.name}'")
                            }
                        }
                    }
                }
            }
        }
        println("total dangling references across all snake functions: $totalDangling")

        // Also verify phi incoming: every phi incoming value must be defined
        var badPhis = 0
        for (fn in irAfter.functions) {
            if (fn.isExternal) { continue }
            val defined = mutableSetOf<String>()
            for (param in fn.params) { defined.add(param.name) }
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    val result = inst.result
                    if (result != null) { defined.add(result.name) }
                }
            }

            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst is Phi) {
                        for ((value, _) in inst.incoming) {
                            if (value is org.kgen.ir.InstructionRef && value.name !in defined) {
                                badPhis++
                                if (badPhis <= 5) {
                                    println("BAD PHI in ${fn.name}: phi ${inst.dest.name} has undefined incoming '${value.name}'")
                                }
                            }
                        }
                    }
                }
            }
        }
        println("total bad phi incoming values: $badPhis")
    }
}

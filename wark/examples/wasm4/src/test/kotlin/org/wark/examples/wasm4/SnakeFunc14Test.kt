package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

class SnakeFunc14Test {

    @Test
    fun func14TypedBlockAnalysis() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val disassembler = WasmDisassembler()

        val function = wasmModule.functions[14]
        val instructions = disassembler.disassemble(function.body)

        // Find all block/loop/if instructions and their block types
        var typedLoopCount = 0
        var typedBlockCount = 0
        var typedIfCount = 0
        for ((index, inst) in instructions.withIndex()) {
            val text = inst.text()
            when {
                text.startsWith("loop") -> {
                    val type = inst.operands
                    if (type is org.kgen.target.wasm.disasm.WasmInstruction.Operands.BlockType && type.type != -64) {
                        typedLoopCount++
                        println("  PC $index: TYPED LOOP (type=${type.type})")
                    }
                }
                text.startsWith("block") -> {
                    val type = inst.operands
                    if (type is org.kgen.target.wasm.disasm.WasmInstruction.Operands.BlockType && type.type != -64) {
                        typedBlockCount++
                        println("  PC $index: TYPED BLOCK (type=${type.type})")
                    }
                }
                text.startsWith("if") -> {
                    val type = inst.operands
                    if (type is org.kgen.target.wasm.disasm.WasmInstruction.Operands.BlockType && type.type != -64) {
                        typedIfCount++
                        println("  PC $index: TYPED IF (type=${type.type})")
                    }
                }
            }
        }
        println("func_14: typed loops=$typedLoopCount, typed blocks=$typedBlockCount, typed ifs=$typedIfCount")

        // Check ALL snake functions for typed loops
        println("\nAll snake functions with typed loops:")
        for ((localIndex, func) in wasmModule.functions.withIndex()) {
            val insts = disassembler.disassemble(func.body)
            var hasTypedLoop = false
            for (inst in insts) {
                if (inst.text().startsWith("loop")) {
                    val type = inst.operands
                    if (type is org.kgen.target.wasm.disasm.WasmInstruction.Operands.BlockType && type.type != -64) {
                        hasTypedLoop = true
                        break
                    }
                }
            }
            if (hasTypedLoop) {
                println("  func_$localIndex: has typed loop")
            }
        }
    }
}

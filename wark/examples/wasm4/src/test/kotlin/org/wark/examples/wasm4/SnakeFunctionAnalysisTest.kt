package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class SnakeFunctionAnalysisTest {

    @Test
    fun snakeFunctionSizes() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        println("Snake: ${wasmModule.functions.size} functions, $importCount imports")
        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val funcType = wasmModule.types[function.typeIndex]
            val instructions = disassembler.disassemble(function.body)
            val name = wasmModule.functionName(importCount + localIndex) ?: "func_$localIndex"

            // Check if function references DRAW_COLORS (address 20 = 0x14)
            var writesDc = false
            var usesI32Store16 = false
            for (inst in instructions) {
                if (inst.text().contains("i32.const 20") || inst.text().contains("0x14")) {
                    writesDc = true
                }
                if (inst.opcode.mnemonic == "i32.store16") {
                    usesI32Store16 = true
                }
            }
            val dcMarker = if (writesDc && usesI32Store16) { " ← DRAW_COLORS" } else { "" }
            println("  $name: ${funcType.params} → ${funcType.results}, ${instructions.size} instr, ${function.locals.size} locals$dcMarker")
        }
    }

    @Test
    fun snakeNativeCodeSizes() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())
        val inspector = instance.inspector() ?: return

        println("Snake JIT native code sizes:")
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        for ((localIndex, _) in wasmModule.functions.withIndex()) {
            val name = wasmModule.functionName(importCount + localIndex) ?: "func_$localIndex"
            val asm = inspector.dumpAsm(name)
            val lines = asm.lines().filter { it.isNotBlank() }
            println("  $name: ${lines.size} asm lines")
        }

        // Dump the function that writes to DRAW_COLORS (address 0x14)
        // From hs_err: context at [rbp-0x68], writes 0x14 and 0x02
        for ((localIndex, _) in wasmModule.functions.withIndex()) {
            val name = wasmModule.functionName(importCount + localIndex) ?: "func_$localIndex"
            val asm = inspector.dumpAsm(name)
            if (asm.contains("mov r8d, 0x14") || asm.contains("mov.*0x14.*store16")) {
                println("\n$name writes DRAW_COLORS:")
                // Find the context spill offset
                val lines = asm.lines()
                for (line in lines.take(5)) {
                    println("  $line") // prologue
                }
                // Search for the DRAW_COLORS pattern
                for ((idx, line) in lines.withIndex()) {
                    if (line.contains("0x14") && idx > 5) {
                        for (contextLine in lines.subList(maxOf(0, idx - 3), minOf(lines.size, idx + 10))) {
                            println("  $contextLine")
                        }
                        println()
                        break
                    }
                }
            }
        }
    }
}

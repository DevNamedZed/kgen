package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeEndianTest {

    private val wasmPath = Path.of("../assets/quake.wasm")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount

        println("=== Searching for byte-swap and endian functions ===")
        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name.contains("Little", ignoreCase = true) ||
                name.contains("Big", ignoreCase = true) ||
                name.contains("bswap", ignoreCase = true) ||
                name.contains("Swap", ignoreCase = true) ||
                name.contains("endian", ignoreCase = true) ||
                name.contains("byte_order", ignoreCase = true)) {
                val funcType = module.wasmModule.types[function.typeIndex]
                val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()
                val instructions = disasm.disassemble(function.body)
                println("  $name (func $localIndex): params=${funcType.params} results=${funcType.results}, ${instructions.size} instr")
                if (instructions.size < 30) {
                    for ((index, inst) in instructions.withIndex()) {
                        println("    [$index] ${inst.opcode} ${inst.operands}")
                    }
                }
            }
        }

        println("\n=== Checking Mod_Load* functions for byte-swap patterns ===")
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()
        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name.startsWith("Mod_Load")) {
                val instructions = disasm.disassemble(function.body)
                var hasShift24 = false
                var hasAnd0xFF = false
                for (inst in instructions) {
                    if (inst.opcode == org.kgen.target.wasm.WasmOpCode.I32_CONST) {
                        val value = (inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.I32).value
                        if (value == 24) { hasShift24 = true }
                        if (value == 0xFF00 || value == 0xFF0000) { hasAnd0xFF = true }
                    }
                }
                if (hasShift24 && hasAnd0xFF) {
                    println("  $name (func $localIndex): HAS BYTE-SWAP PATTERN (shift 24 + mask 0xFF00/0xFF0000)")
                }
                val funcType = module.wasmModule.types[function.typeIndex]
                println("  $name (func $localIndex): ${instructions.size} instr, params=${funcType.params}")
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/quake.wasm"))) {
                QuakeEndianTest().run()
            }
        }
    }
}

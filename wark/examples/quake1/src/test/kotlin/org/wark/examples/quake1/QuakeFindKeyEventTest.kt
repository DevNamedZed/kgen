package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeFindKeyEventTest {
    private val wasmPath = Path.of("../assets/quake.wasm")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun findKeyEventByPattern() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        // WASM_QueueKeyEvent writes key to memory at base 1121024
        // The drain function reads from 1121024 and calls Key_Event
        // Find unnamed functions that reference address 1121024 and also CALL another function
        println("Functions that reference address 1121024 (key queue):")
        for ((li, function) in module.wasmModule.functions.withIndex()) {
            val globalIdx = li + importCount
            val instructions = disasm.disassemble(function.body)
            var refsKeyQueue = false
            var callTargets = mutableListOf<Int>()
            for (inst in instructions) {
                if (inst.opcode == org.kgen.target.wasm.WasmOpCode.I32_CONST) {
                    val value = (inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.I32).value
                    if (value == 1121024 || value == 1121008 || value == 1121012) {
                        refsKeyQueue = true
                    }
                }
                if (inst.opcode == org.kgen.target.wasm.WasmOpCode.CALL) {
                    callTargets.add((inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index).value)
                }
            }
            if (refsKeyQueue) {
                val name = module.wasmModule.functionName(globalIdx) ?: "UNNAMED"
                val funcType = module.wasmModule.types[function.typeIndex]
                println("  func $globalIdx ($name): ${instructions.size} instr, type=${funcType.params}→${funcType.results}")
                println("    calls: ${callTargets.map { "$it(${module.wasmModule.functionName(it) ?: "unnamed"})" }}")
            }
        }
    }
}

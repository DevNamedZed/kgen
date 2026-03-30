package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeOobFunctionTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun analyzeOobFunctions() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        val targetFunctions = listOf(787) // Sys_Error

        for (localIndex in targetFunctions) {
            val globalIndex = localIndex + importCount
            val function = module.wasmModule.functions[localIndex]
            val funcType = module.wasmModule.types[function.typeIndex]
            val name = module.wasmModule.functionName(globalIndex) ?: "func_$localIndex"
            val instructions = disasm.disassemble(function.body)

            println("=== func $localIndex ($name) ===")
            println("  type: params=${funcType.params} results=${funcType.results}")
            println("  locals: ${function.locals}")
            println("  instructions: ${instructions.size}")

            for ((index, inst) in instructions.withIndex()) {
                println("  [$index] ${inst.opcode} ${inst.operands}")
            }
            println()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeOobFunctionTest()
            if (test.quakeWasmExists()) {
                test.analyzeOobFunctions()
            } else {
                println("quake.wasm not found")
            }
        }
    }
}

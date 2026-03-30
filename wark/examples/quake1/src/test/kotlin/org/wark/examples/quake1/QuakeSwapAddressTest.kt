package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeSwapAddressTest {

    private val wasmPath = Path.of("../assets/quake.wasm")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        println("=== Element section offset ===")
        for (element in module.wasmModule.elements) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                println("  offsetExpr bytes: ${element.offsetExpr.joinToString(" ") { "%02x".format(it) }}")
                println("  funcIndices count: ${element.funcIndices.size}")
                println("  First 5 entries: ${element.funcIndices.take(5)}")
                val offset = if (element.offsetExpr.isNotEmpty() && element.offsetExpr[0].toInt() and 0xFF == 0x41) {
                    var value = 0; var shift = 0; var pos = 1
                    while (pos < element.offsetExpr.size) {
                        val byte = element.offsetExpr[pos].toInt() and 0xFF; pos++
                        value = value or ((byte and 0x7F) shl shift); shift += 7
                        if (byte and 0x80 == 0) { break }
                    }
                    value
                } else { 0 }
                println("  Computed offset: $offset")

                // Check SwapNoSwap entries
                for ((slot, funcIdx) in element.funcIndices.withIndex()) {
                    val globalIdx = funcIdx
                    val name = module.wasmModule.functionName(globalIdx) ?: continue
                    if (name.contains("Swap") || name.contains("NoSwap")) {
                        println("  table[${offset + slot}] = func $funcIdx ($name)")
                    }
                }
            }
        }

        val targetFunctions = listOf("Host_Shutdown")

        for (name in targetFunctions) {
            for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
                val funcName = module.wasmModule.functionName(localIndex + importCount) ?: continue
                if (funcName == name) {
                    val instructions = disasm.disassemble(function.body)
                    println("=== $funcName (func $localIndex, ${instructions.size} instr) ===")

                    for ((instrIndex, inst) in instructions.withIndex()) {
                        if (instrIndex in 60..150) {
                            println("    [$instrIndex] ${inst.opcode} ${inst.operands}")
                        }
                    }
                    break
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/quake.wasm"))) {
                QuakeSwapAddressTest().run()
            }
        }
    }
}

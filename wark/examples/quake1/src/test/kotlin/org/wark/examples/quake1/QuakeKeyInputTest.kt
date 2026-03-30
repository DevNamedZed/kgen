package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeKeyInputTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun dumpKeyEventFunction() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()
        val importCount = module.wasmModule.importedFunctionCount

        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name == "q_key_event") {
                val instructions = disasm.disassemble(function.body)
                println("q_key_event: ${instructions.size} instructions")
                println("  calls function 817 = ${module.wasmModule.functionName(817) ?: "unnamed"}")
                break
            }
        }

        // Find WASM_DrainKeyEvents
        for ((localIndex, function) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount) ?: continue
            if (name.contains("DrainKey") || name.contains("drain_key")) {
                println("Found: $name at global index ${localIndex + importCount}")
                val instructions = disasm.disassemble(function.body)
                println("  ${instructions.size} instructions, type=${module.wasmModule.types[function.typeIndex]}")
                for ((i, inst) in instructions.take(10).withIndex()) {
                    println("  [$i] ${inst.opcode} ${inst.operands}")
                }
            }
        }

        // Check function table for DrainKeyEvents-related entries
        for (element in module.wasmModule.elements) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                println("Element segment: ${element.funcIndices.size} entries")
                for ((i, funcIdx) in element.funcIndices.withIndex()) {
                    val name = module.wasmModule.functionName(funcIdx)
                    if (name != null && (name.contains("Drain") || name.contains("signature_mismatch"))) {
                        println("  table[$i] = func $funcIdx ($name)")
                    }
                }
            }
        }
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun testKeyEventAffectsOutput() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()

        // Run frames until demo starts
        for (i in 1..10) { runner.frame(1.0f / 60.0f) }

        // Capture framebuffer before key
        val fbBefore = runner.memory().readBytes(runner.framebufferPointer(), 100)

        // Press Escape (should open menu / stop demo)
        runner.keyEvent(27, true)
        runner.frame(1.0f / 60.0f)
        runner.keyEvent(27, false)
        runner.frame(1.0f / 60.0f)
        runner.frame(1.0f / 60.0f)

        // Capture framebuffer after key
        val fbAfter = runner.memory().readBytes(runner.framebufferPointer(), 100)

        var changed = 0
        for (i in fbBefore.indices) {
            if (fbBefore[i] != fbAfter[i]) changed++
        }
        println("Framebuffer pixels changed after Escape: $changed / ${fbBefore.size}")

        // Try typing in console: press tilde then 'h' 'e' 'l' 'p'
        runner.keyEvent('`'.code, true)
        runner.frame(1.0f / 60.0f)
        runner.keyEvent('`'.code, false)
        runner.frame(1.0f / 60.0f)

        // Type 'quit' + enter
        for (ch in "quit") {
            runner.keyEvent(ch.code, true)
            runner.frame(1.0f / 60.0f)
            runner.keyEvent(ch.code, false)
        }
        runner.keyEvent(13, true)  // Enter
        runner.frame(1.0f / 60.0f)
        runner.keyEvent(13, false)
        runner.frame(1.0f / 60.0f)

        val fbConsole = runner.memory().readBytes(runner.framebufferPointer(), 100)
        var changedConsole = 0
        for (i in fbAfter.indices) {
            if (fbAfter[i] != fbConsole[i]) changedConsole++
        }
        println("Framebuffer pixels changed after console input: $changedConsole / ${fbAfter.size}")

        runner.shutdown()
    }
}

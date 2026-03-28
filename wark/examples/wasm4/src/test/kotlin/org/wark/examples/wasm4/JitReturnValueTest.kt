package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class JitReturnValueTest {

    @Test
    fun minesweeperFunc2Disassembly() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        val function = wasmModule.functions[2]
        val instructions = disassembler.disassemble(function.body)

        // Dump code around call sites to func_73 and func_23
        val callSites = listOf(49, 55, 304, 317, 338)
        for (callPc in callSites) {
            if (callPc >= instructions.size) { continue }
            println("--- around PC $callPc (${instructions[callPc].text()}) ---")
            for (index in maxOf(0, callPc - 15) until minOf(instructions.size, callPc + 5)) {
                val marker = if (index == callPc) { ">>>" } else { "   " }
                println("  $marker $index: ${instructions[index].text()}")
            }
            println()
        }
    }

    @Test
    fun minesweeperFunc73Func23ReturnValues() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        // Dump func_73 and func_23 fully
        for (localIndex in listOf(73, 23)) {
            val function = wasmModule.functions[localIndex]
            val funcType = wasmModule.types[function.typeIndex]
            val instructions = disassembler.disassemble(function.body)
            println("=== func_$localIndex: ${funcType.params} -> ${funcType.results}, ${instructions.size} instructions ===")
            for ((index, instruction) in instructions.withIndex()) {
                println("  $index: ${instruction.text()}")
            }
            println()
        }
    }

    @Test
    fun minesweeperFunc2IrDump() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())
        val irModule = instance.compiledIr()
        if (irModule == null) {
            println("No compiled IR available")
            return
        }

        // Find func_2 in the IR module
        val func2 = irModule.functions.find { it.name == "func_2" }
        if (func2 == null) {
            println("func_2 not found in IR")
            println("available: ${irModule.functions.map { it.name }.take(20)}")
            return
        }

        println("func_2 IR: ${func2.params.size} params, ${func2.blocks.size} blocks")
        println("params: ${func2.params.map { "${it}: ${it.type}" }}")

        // Count instructions and find key patterns
        var totalInstructions = 0
        var phiCount = 0
        var callCount = 0
        var selectCount = 0
        for (block in func2.blocks) {
            totalInstructions += block.instructions.size
            for (instruction in block.instructions) {
                if (instruction is org.kgen.ir.instructions.Phi) { phiCount++ }
                if (instruction is org.kgen.ir.instructions.Call) { callCount++ }
                if (instruction is org.kgen.ir.instructions.Select) { selectCount++ }
            }
        }
        println("total IR instructions: $totalInstructions, blocks: ${func2.blocks.size}")
        println("phi: $phiCount, calls: $callCount, selects: $selectCount")

        // Dump the first 20 blocks
        for ((blockIndex, block) in func2.blocks.withIndex()) {
            if (blockIndex >= 20) {
                println("... (${func2.blocks.size - 20} more blocks)")
                break
            }
            println("${block.label}: (${block.instructions.size} instructions)")
            for (instruction in block.instructions) {
                println("  $instruction")
            }
        }
    }

    @Test
    fun minesweeperFunc2NativeCodeDump() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())

        val inspector = instance.inspector()
        if (inspector == null) {
            println("No inspector available")
            return
        }

        // Dump func_2 native code
        val asm = inspector.dumpAsm("func_2")
        val lines = asm.lines()
        println("func_2 native code: ${lines.size} lines")
        // Print first 60 lines (prologue + entry block)
        for (line in lines.take(60)) {
            println(line)
        }
        println("...")
        // Print last 20 lines (epilogue)
        for (line in lines.takeLast(20)) {
            println(line)
        }

        // Also dump func_73 and func_23 native code (they're small)
        println("\n=== func_73 native ===")
        println(inspector.dumpAsm("func_73"))
        println("\n=== func_23 native ===")
        val func23Asm = inspector.dumpAsm("func_23")
        val func23Lines = func23Asm.lines()
        println("func_23: ${func23Lines.size} lines")
        for (line in func23Lines.take(40)) {
            println(line)
        }
    }

    @Test
    fun minesweeperJitReturnValueDiff() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        // Interpreter: track return values per function call
        val interpReturns = mutableListOf<Triple<String, String, Long>>()
        val interpHost = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost.buildImports())
        val interpreter = interpInstance.interpreter()

        val callStack = mutableListOf<String>()
        interpreter.onFunctionEntry = { funcIndex, args ->
            val importCount = interpInstance.module.wasmModule.importedFunctionCount
            val name = if (funcIndex < importCount) {
                val imp = interpInstance.module.wasmModule.imports
                    .filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Func>()[funcIndex]
                imp.name
            } else {
                "func_${funcIndex - importCount}"
            }
            callStack.add(name)
        }

        if (interpInstance.exportedFunctions().contains("_initialize")) {
            interpInstance.call("_initialize")
        }
        callStack.clear()
        interpInstance.call("update")

        // JIT: track return values
        val jitReturns = mutableListOf<Triple<String, String, Long>>()
        val jitHost = Wasm4Host()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost.buildImports())

        jitInstance.enableTracing { funcId, message ->
            if (message.contains("<<<")) {
                val match = Regex("(func_\\d+|indirect) returned 0x([0-9a-fA-F]+) \\((-?\\d+)\\) to (func_\\d+)").find(message)
                if (match != null) {
                    val callee = match.groupValues[1]
                    val returnVal = match.groupValues[3].toLong()
                    val caller = match.groupValues[4]
                    jitReturns.add(Triple(caller, callee, returnVal))
                }
            }
        }

        if (jitInstance.exportedFunctions().contains("_initialize")) {
            jitInstance.call("_initialize")
        }
        jitReturns.clear()
        try {
            jitInstance.call("update")
        } catch (e: WasmTrap) {
            println("JIT trapped after ${jitReturns.size} return events: ${e.message}")
        }

        // Print JIT return values around func_73 and func_23 calls
        println("\nJIT returns involving func_73 or func_23 (first 30):")
        var count = 0
        for ((index, triple) in jitReturns.withIndex()) {
            val (caller, callee, returnVal) = triple
            if ((callee == "func_73" || callee == "func_23") && count < 30) {
                println("  return #$index: $callee returned $returnVal to $caller")
                count++
            }
        }
    }
}

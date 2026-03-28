package org.wark.examples.wasm4

import kotlin.test.assertTrue
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

class JitCrashInvestigationTest {

    @Test
    fun minesweeperFunc53Analysis() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount

        // func_53 is local index 53, global index = importCount + 53
        val localIndex = 53
        val function = wasmModule.functions[localIndex]
        val funcType = wasmModule.types[function.typeIndex]
        println("func_53 type: params=${funcType.params}, results=${funcType.results}")

        val disassembler = WasmDisassembler()
        val instructions = disassembler.disassemble(function.body)
        println("func_53: ${instructions.size} instructions")

        // Find unreachable instructions
        for ((index, instruction) in instructions.withIndex()) {
            if (instruction.opcode.mnemonic == "unreachable") {
                println("  unreachable at PC=$index: ${instruction.text()}")
                // Print context: 5 instructions before
                for (contextIndex in maxOf(0, index - 5) until index) {
                    println("    PC=$contextIndex: ${instructions[contextIndex].text()}")
                }
            }
        }

        // Print all instructions for func_53
        println("\nfunc_53 full disassembly:")
        for ((index, instruction) in instructions.withIndex()) {
            println("  $index: ${instruction.text()}")
        }
    }

    @Test
    fun minesweeperJitDivergenceTrace() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        // Run interpreter with function tracing
        val interpHost = Wasm4Host()
        val interpImports = interpHost.buildImports()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpImports)
        val interpreter = interpInstance.interpreter()
        interpreter.traceEnabled = true

        // Run _initialize + start if exported
        if (interpInstance.exportedFunctions().contains("_initialize")) {
            interpInstance.call("_initialize")
        }

        // Run 1 update frame on interpreter
        interpreter.tracedCalls().toMutableList().clear()
        interpInstance.call("update")
        val interpTrace = interpreter.tracedCalls()
        println("interpreter update() called ${interpTrace.size} functions")
        println("interpreter first 30 calls: ${interpTrace.take(30)}")

        // Now try JIT
        val jitHost = Wasm4Host()
        val jitImports = jitHost.buildImports()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitImports)

        if (jitInstance.exportedFunctions().contains("_initialize")) {
            try {
                jitInstance.call("_initialize")
                println("JIT _initialize OK")
            } catch (e: WasmTrap) {
                println("JIT _initialize TRAPPED: ${e.message}")
                return
            }
        }

        try {
            jitInstance.call("update")
            println("JIT update() OK")
        } catch (e: WasmTrap) {
            println("JIT update() TRAPPED: ${e.message}")
        }

        // Compare memory at key locations
        val interpMem = interpInstance.memory()
        val jitMem = jitInstance.memory()

        // Compare framebuffer
        val interpFb = interpMem.readBytes(Wasm4Host.FRAMEBUFFER_ADDRESS, 6400)
        val jitFb = jitMem.readBytes(Wasm4Host.FRAMEBUFFER_ADDRESS, 6400)
        var fbDiffs = 0
        for (index in interpFb.indices) {
            if (interpFb[index] != jitFb[index]) { fbDiffs++ }
        }
        println("framebuffer diffs: $fbDiffs / 6400 bytes")

        // Compare first 256 bytes of system memory
        println("\nsystem memory comparison (0x00-0xFF):")
        for (offset in 0 until 256 step 16) {
            val interpSlice = interpMem.readBytes(offset, 16)
            val jitSlice = jitMem.readBytes(offset, 16)
            if (!interpSlice.contentEquals(jitSlice)) {
                val interpHex = interpSlice.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
                val jitHex = jitSlice.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
                println("  0x${"%04x".format(offset)}: interp=$interpHex")
                println("  0x${"%04x".format(offset)}: jit   =$jitHex")
            }
        }
    }

    @Test
    fun minesweeperUpdateDisassembly() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        // Disassemble the update function (local index 0 = "update")
        val updateFunc = wasmModule.functions[0]
        val updateInstructions = disassembler.disassemble(updateFunc.body)
        println("update function: ${updateInstructions.size} instructions")

        // Find calls to func_53 (global index = importCount + 53)
        val globalIndex53 = importCount + 53
        for ((index, instruction) in updateInstructions.withIndex()) {
            if (instruction.opcode.mnemonic == "call") {
                val operands = instruction.operands as? org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index
                if (operands != null && operands.value == globalIndex53) {
                    println("update calls func_53 at PC=$index")
                    // Print context
                    for (contextIndex in maxOf(0, index - 10) until minOf(updateInstructions.size, index + 3)) {
                        val marker = if (contextIndex == index) { ">>>" } else { "   " }
                        println("  $marker $contextIndex: ${updateInstructions[contextIndex].text()}")
                    }
                }
            }
        }

        // Print first 80 instructions of update to see the entry logic
        println("\nupdate first 80 instructions:")
        for (index in 0 until minOf(80, updateInstructions.size)) {
            println("  $index: ${updateInstructions[index].text()}")
        }
    }

    @Test
    fun minesweeperJitVsInterpFunctionTrace() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        // Interpreter trace
        val interpHost = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost.buildImports())
        val interpreter = interpInstance.interpreter()
        interpreter.traceEnabled = true
        if (interpInstance.exportedFunctions().contains("_initialize")) {
            interpInstance.call("_initialize")
        }
        val initCalls = interpreter.tracedCalls().toList()
        println("interpreter _initialize: ${initCalls.size} calls")
        println("  last 10: ${initCalls.takeLast(10)}")

        // JIT with tracing enabled
        val jitHost = Wasm4Host()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost.buildImports())

        // Enable JIT tracing
        val jitCalls = mutableListOf<String>()
        jitInstance.enableTracing { funcId, name ->
            jitCalls.add(name ?: "func_$funcId")
        }

        if (jitInstance.exportedFunctions().contains("_initialize")) {
            try {
                jitInstance.call("_initialize")
                println("JIT _initialize: ${jitCalls.size} calls")
                println("  last 10: ${jitCalls.takeLast(10)}")
            } catch (e: WasmTrap) {
                println("JIT _initialize TRAPPED at call #${jitCalls.size}: ${e.message}")
                println("  last 10 calls: ${jitCalls.takeLast(10)}")
                return
            }
        }

        // Compare _initialize traces
        if (initCalls.size != jitCalls.size) {
            println("_initialize divergence: interp=${initCalls.size} calls, jit=${jitCalls.size} calls")
            val minLen = minOf(initCalls.size, jitCalls.size)
            for (index in 0 until minLen) {
                if (initCalls[index] != jitCalls[index]) {
                    println("  first diff at call #$index: interp=${initCalls[index]}, jit=${jitCalls[index]}")
                    break
                }
            }
        }

        // Now compare update
        jitCalls.clear()
        try {
            jitInstance.call("update")
            println("JIT update: ${jitCalls.size} calls (OK)")
        } catch (e: WasmTrap) {
            println("JIT update TRAPPED at call #${jitCalls.size}: ${e.message}")
            println("  last 10 calls: ${jitCalls.takeLast(10)}")
        }
    }

    @Test
    fun minesweeperDifferentialFirstDivergence() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        // Interpreter with function call tracing
        val interpHost = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost.buildImports())
        val interpreter = interpInstance.interpreter()
        interpreter.traceEnabled = true

        // JIT with function call tracing
        val jitHost = Wasm4Host()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost.buildImports())
        val jitCalls = mutableListOf<String>()
        jitInstance.enableTracing { funcId, name ->
            val funcName = name ?: "func_$funcId"
            if (!funcName.contains("<<<")) {
                jitCalls.add(funcName.trim())
            }
        }

        // Run _initialize
        if (interpInstance.exportedFunctions().contains("_initialize")) {
            interpInstance.call("_initialize")
            jitInstance.call("_initialize")
        }

        // Compare global 0 (stack pointer) before update
        val wasmModule = WasmModuleReader.read(bytes)
        val importedGlobalCount = wasmModule.imports.count { it is org.kgen.target.wasm.module.WasmModule.Import.Global }
        val totalGlobals = importedGlobalCount + wasmModule.globals.size
        println("total globals: $totalGlobals")
        for (index in 0 until totalGlobals) {
            try {
                val interpVal = interpInstance.global(index).rawValue()
                val jitVal = jitInstance.global(index).rawValue()
                println("  global[$index]: interp=$interpVal, jit=$jitVal ${if (interpVal != jitVal) { "MISMATCH" } else { "" }}")
            } catch (e: Exception) {
                println("  global[$index]: error ${e.message}")
            }
        }

        // Compare key memory before update
        val interpMem = interpInstance.memory()
        val jitMem = jitInstance.memory()
        for (offset in listOf(11908, 11916, 11940, 20)) {
            val interpVal = interpMem.readI32(offset)
            val jitVal = jitMem.readI32(offset)
            if (interpVal != jitVal) {
                println("  MEMORY MISMATCH at 0x${Integer.toHexString(offset)}: interp=$interpVal, jit=$jitVal")
            }
        }

        // Run interpreter update
        interpreter.tracedCalls().toMutableList().clear()
        interpInstance.call("update")
        // Filter out host function (import) calls — JIT trace doesn't include them
        val importNames = wasmModule.imports.filterIsInstance<org.kgen.target.wasm.module.WasmModule.Import.Func>()
            .map { "${it.module}_${it.name}" }.toSet()
            .plus(setOf("diskr", "diskw", "trace", "tracef", "blit", "blitSub", "line", "hline",
                "vline", "oval", "rect", "text", "tone", "textUtf8", "textUtf16"))
        val interpCalls = interpreter.tracedCalls().filter { it !in importNames }
        println("interpreter update: ${interpCalls.size} WASM calls (${interpreter.tracedCalls().size} total)")

        // Run JIT update
        jitCalls.clear()
        try {
            jitInstance.call("update")
            println("JIT update: ${jitCalls.size} calls (OK)")
        } catch (e: WasmTrap) {
            println("JIT update: TRAPPED after ${jitCalls.size} calls: ${e.message}")
        }

        // Find first divergence in call traces
        val maxCompare = minOf(interpCalls.size, jitCalls.size, 200)
        for (index in 0 until maxCompare) {
            val interpFunc = interpCalls[index]
            val jitFunc = jitCalls[index]
            if (interpFunc != jitFunc) {
                println("FIRST DIVERGENCE at call #$index: interp=$interpFunc, jit=$jitFunc")
                println("  interp calls ${index - 2}..${index + 2}: ${interpCalls.subList(maxOf(0, index - 2), minOf(interpCalls.size, index + 3))}")
                println("  jit calls ${index - 2}..${index + 2}: ${jitCalls.subList(maxOf(0, index - 2), minOf(jitCalls.size, index + 3))}")
                break
            }
        }
        if (maxCompare > 0 && (0 until maxCompare).all { interpCalls[it] == jitCalls[it] }) {
            println("first $maxCompare calls match between interp and JIT")
            if (jitCalls.size > interpCalls.size) {
                println("JIT has ${jitCalls.size - interpCalls.size} extra calls after interp ends")
                println("  extra calls start: ${jitCalls.subList(interpCalls.size, minOf(jitCalls.size, interpCalls.size + 10))}")
            }
        }
    }

    @Test
    fun minesweeperFunc2Analysis() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        // Disassemble func_2 (the one that loops func_73/func_23)
        for (localIndex in listOf(2, 56, 73, 23)) {
            val function = wasmModule.functions[localIndex]
            val funcType = wasmModule.types[function.typeIndex]
            val instructions = disassembler.disassemble(function.body)
            println("func_$localIndex: params=${funcType.params}, results=${funcType.results}, ${instructions.size} instructions")

            // Find all calls
            for ((index, instruction) in instructions.withIndex()) {
                if (instruction.opcode.mnemonic == "call") {
                    val operands = instruction.operands as? org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index
                    if (operands != null) {
                        val targetLocal = operands.value - importCount
                        val targetName = if (operands.value < importCount) { "import_${operands.value}" } else { "func_$targetLocal" }
                        println("  PC $index: call $targetName")
                    }
                }
            }
        }
    }

    @Test
    fun minesweeperJitCallerOfFunc53() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        // Find all functions that call func_53
        val globalIndex53 = importCount + 53
        println("func_53 global index: $globalIndex53")

        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val instructions = disassembler.disassemble(function.body)
            for (instruction in instructions) {
                if (instruction.opcode.mnemonic == "call") {
                    val operands = instruction.operands as? org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index
                    if (operands != null && operands.value == globalIndex53) {
                        val name = wasmModule.functionName(importCount + localIndex) ?: "func_$localIndex"
                        println("func_53 called from $name (local=$localIndex)")
                    }
                }
            }
        }
    }
}

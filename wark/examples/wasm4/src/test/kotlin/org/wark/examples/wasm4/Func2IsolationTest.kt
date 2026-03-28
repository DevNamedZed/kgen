package org.wark.examples.wasm4

import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class Func2IsolationTest {

    @Test
    fun captureFunc2ArgsAndCompare() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount

        // Run interpreter, capture func_2's arguments
        val interpHost = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost.buildImports())
        val interpreter = interpInstance.interpreter()

        var func2Args: LongArray? = null
        val func2GlobalIndex = importCount + 2
        interpreter.onFunctionEntry = { funcIndex, args ->
            if (funcIndex == func2GlobalIndex && func2Args == null) {
                func2Args = args.clone()
            }
        }

        interpInstance.call("update")

        if (func2Args == null) {
            println("func_2 was not called during update — skipping")
            return
        }

        println("func_2 captured args: ${func2Args!!.map { "0x${it.toString(16)} ($it)" }}")

        // Snapshot memory BEFORE func_2 was called
        // We need to re-run from _initialize to get the state just before func_2
        val interpHost2 = Wasm4Host()
        val interpInstance2 = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost2.buildImports())
        val interpreter2 = interpInstance2.interpreter()

        // Trace calls and stop right before func_2
        var callCount = 0
        val callsBeforeFunc2 = mutableListOf<String>()
        interpreter2.onFunctionEntry = { funcIndex, args ->
            if (funcIndex == func2GlobalIndex) {
                func2Args = args.clone()
            }
            val name = if (funcIndex < importCount) { "import" } else { "func_${funcIndex - importCount}" }
            callsBeforeFunc2.add(name)
            callCount++
        }

        interpInstance2.call("update")

        // Now snapshot the memory state after update
        val interpMem = interpInstance2.memory()
        val memorySnapshot = interpMem.readBytes(0, interpMem.sizeBytes())

        // Now run JIT on a fresh instance, set memory to the same state, call func_2 directly
        val jitHost = Wasm4Host()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost.buildImports())

        // Copy memory state from interpreter (after _initialize, before update)
        // Actually we need to copy the state BEFORE func_2 is called
        // For now, just compare by running update on both and comparing
        val jitMem = jitInstance.memory()

        // Compare memory before update
        val interpPreMem = interpInstance2.memory().readBytes(0, 4096)
        val jitPreMem = jitMem.readBytes(0, 4096)
        var preDiffs = 0
        for (index in interpPreMem.indices) {
            if (interpPreMem[index] != jitPreMem[index]) { preDiffs++ }
        }
        println("memory diffs before update (first 4K): $preDiffs")

        // Run both with interpreter tracing on interp side
        val interpHost3 = Wasm4Host()
        val interpInstance3 = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpHost3.buildImports())

        // Set up interpreter to log func_73 return values
        val interpreter3 = interpInstance3.interpreter()
        val interpFunc73Returns = mutableListOf<Long>()
        val interpFunc23Returns = mutableListOf<Long>()
        val func73GlobalIndex = importCount + 73
        val func23GlobalIndex = importCount + 23

        // Use a wrapper approach: track returns by watching subsequent calls
        interpreter3.traceEnabled = true

        interpInstance3.call("update")
        val interpTrace = interpreter3.tracedCalls()

        // Now set up JIT with return value tracing
        val jitHost3 = Wasm4Host()
        val jitInstance3 = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(jitHost3.buildImports())

        val jitFunc73Returns = mutableListOf<Long>()
        val jitFunc23Returns = mutableListOf<Long>()
        jitInstance3.enableTracing { funcId, message ->
            if (message.contains("<<<") && message.contains("func_73")) {
                val match = Regex("\\((-?\\d+)\\)").find(message)
                if (match != null) {
                    jitFunc73Returns.add(match.groupValues[1].toLong())
                }
            }
            if (message.contains("<<<") && message.contains("func_23")) {
                val match = Regex("\\((-?\\d+)\\)").find(message)
                if (match != null) {
                    jitFunc23Returns.add(match.groupValues[1].toLong())
                }
            }
        }

        try {
            jitInstance3.call("update")
        } catch (e: WasmTrap) {
            println("JIT trapped: ${e.message}")
        }

        println("\nJIT func_73 returns (${jitFunc73Returns.size}): ${jitFunc73Returns.take(20)}")
        println("JIT func_23 returns (${jitFunc23Returns.size}): ${jitFunc23Returns.take(20)}")

        // Compare memory after update (focus on game state area)
        val interpPostMem = interpInstance3.memory().readBytes(0x2A00, 256)
        val jitPostMem = jitInstance3.memory().readBytes(0x2A00, 256)
        var postDiffs = 0
        var firstDiffAddr = -1
        for (index in interpPostMem.indices) {
            if (interpPostMem[index] != jitPostMem[index]) {
                postDiffs++
                if (firstDiffAddr < 0) { firstDiffAddr = 0x2A00 + index }
            }
        }
        println("\nmemory diffs after update (0x2A00-0x2AFF): $postDiffs")
        if (firstDiffAddr >= 0) {
            println("first diff at 0x${Integer.toHexString(firstDiffAddr)}")
        }

        // Compare globals after update
        val totalGlobals = wasmModule.imports.count { it is org.kgen.target.wasm.module.WasmModule.Import.Global } + wasmModule.globals.size
        for (index in 0 until totalGlobals) {
            val interpVal = interpInstance3.global(index).rawValue()
            val jitVal = jitInstance3.global(index).rawValue()
            if (interpVal != jitVal) {
                println("global[$index] MISMATCH: interp=$interpVal, jit=$jitVal")
            }
        }
    }

    @Test
    fun func2BackEdgeX86Dump() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())

        val inspector = instance.inspector() ?: return

        val asm = inspector.dumpAsm("func_2")
        val lines = asm.lines()

        // Find the back-edge: look for jumps to loop_header_4
        // The label format in the disassembly is typically the block address
        // Let me search for the pattern around the func_23 call and subsequent back-edge
        println("func_2 x86 (${lines.size} lines total)")
        println()

        // Find lines with call and loop-related patterns
        for ((index, line) in lines.withIndex()) {
            if (line.contains("call") && (line.contains("func_73") || line.contains("func_23"))) {
                println("[$index] $line")
                // Print 15 lines after each call (includes phi copies and back-edge jump)
                for (contextIndex in index + 1 until minOf(lines.size, index + 20)) {
                    println("[$contextIndex] ${lines[contextIndex]}")
                }
                println()
            }
        }
    }

    @Test
    fun func2PhiNodeDump() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val host = Wasm4Host()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(host.buildImports())
        val irModule = instance.compiledIr() ?: return

        val func2 = irModule.functions.find { it.name == "func_2" } ?: return

        // Dump all phi nodes with their block labels
        println("func_2: ${func2.blocks.size} blocks, finding phis:")
        for (block in func2.blocks) {
            val phis = block.instructions.filterIsInstance<org.kgen.ir.instructions.Phi>()
            if (phis.isNotEmpty()) {
                println("\n  ${block.label}: ${phis.size} phis")
                for (phi in phis) {
                    println("    $phi")
                }
                // Also show the terminator
                val terminator = block.instructions.lastOrNull()
                if (terminator != null && terminator !is org.kgen.ir.instructions.Phi) {
                    println("    terminator: $terminator")
                }
            }
        }

        // Find the loop header (block with back-edge predecessor)
        println("\n\nAll block labels: ${func2.blocks.map { it.label }}")

        // Dump blocks around the func_73/func_23 calls
        for (block in func2.blocks) {
            for (instruction in block.instructions) {
                if (instruction is org.kgen.ir.instructions.Call) {
                    val name = instruction.function.name
                    if (name == "func_73" || name == "func_23") {
                        println("\n  call to $name in block ${block.label}:")
                        for (inst in block.instructions) {
                            println("    $inst")
                        }
                        break
                    }
                }
            }
        }
    }
}

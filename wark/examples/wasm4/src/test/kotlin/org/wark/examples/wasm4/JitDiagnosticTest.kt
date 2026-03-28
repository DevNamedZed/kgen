package org.wark.examples.wasm4

import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class JitDiagnosticTest {

    @Test
    @org.junit.jupiter.api.Disabled("JIT native crash")
    fun snakeJitStartAndUpdate() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.JIT)

        try {
            runner.start()
            println("snake JIT start() OK")
        } catch (e: Exception) {
            println("snake JIT start() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace(System.out)
            fail("snake JIT start() should not throw: ${e.message}")
        }

        for (frame in 1..10) {
            try {
                runner.update()
                val pixels = countPixels(runner)
                println("snake JIT frame $frame: $pixels pixels")
            } catch (e: Exception) {
                println("snake JIT frame $frame FAILED: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace(System.out)
                return
            }
        }
        println("snake JIT 10 frames OK")
    }

    @Test
    @org.junit.jupiter.api.Disabled("JIT unreachable trap")
    fun minesweeperJitStartAndUpdate() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val runner = Wasm4Runner.load(path, ExecutionMode.JIT)

        try {
            runner.start()
            println("minesweeper JIT start() OK")
        } catch (e: Exception) {
            println("minesweeper JIT start() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace(System.out)
            fail("minesweeper JIT start() should not throw: ${e.message}")
        }

        for (frame in 1..10) {
            try {
                runner.update()
                val pixels = countPixels(runner)
                println("minesweeper JIT frame $frame: $pixels pixels")
            } catch (e: Exception) {
                println("minesweeper JIT frame $frame FAILED: ${e.javaClass.simpleName}: ${e.message}")
                return
            }
        }
        println("minesweeper JIT 10 frames OK")
    }

    @Test
    @org.junit.jupiter.api.Disabled("JIT native crash")
    fun snakeJitVsInterpDifferential() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)

        val interpHost = Wasm4Host()
        val interpImports = interpHost.buildImports()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(interpImports)

        val jitHost = Wasm4Host()
        val jitImports = jitHost.buildImports()
        val jitInstance = try {
            WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
                .load(bytes).instantiate(jitImports)
        } catch (e: Exception) {
            println("snake JIT instantiation failed: ${e.message}")
            return
        }

        // Call start on both
        try {
            interpInstance.call("start")
            println("interp start() OK")
        } catch (e: Exception) {
            println("interp start() failed: ${e.message}")
        }

        try {
            jitInstance.call("start")
            println("JIT start() OK")
        } catch (e: Exception) {
            println("JIT start() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace(System.out)
            return
        }

        // Compare memory after start
        val interpMem = interpInstance.memory()
        val jitMem = jitInstance.memory()
        val interpBytes = interpMem.readBytes(0, minOf(interpMem.sizeBytes(), 0x3000))
        val jitBytes = jitMem.readBytes(0, minOf(jitMem.sizeBytes(), 0x3000))

        var firstDiff = -1
        for (index in interpBytes.indices) {
            if (index < jitBytes.size && interpBytes[index] != jitBytes[index]) {
                if (firstDiff < 0) {
                    firstDiff = index
                    println("first memory diff after start at 0x${Integer.toHexString(index)}: " +
                        "interp=0x${Integer.toHexString(interpBytes[index].toInt() and 0xFF)}, " +
                        "jit=0x${Integer.toHexString(jitBytes[index].toInt() and 0xFF)}")
                }
            }
        }
        if (firstDiff < 0) {
            println("memory identical after start (${interpBytes.size} bytes)")
        }

        // Run update on both
        for (frame in 1..5) {
            try {
                interpInstance.call("update")
            } catch (e: WasmTrap) {
                println("interp update frame $frame trapped: ${e.message}")
                return
            }
            try {
                jitInstance.call("update")
            } catch (e: Exception) {
                println("JIT update frame $frame FAILED: ${e.javaClass.simpleName}: ${e.message}")
                return
            }

            // Compare framebuffer
            val interpFb = interpMem.readBytes(Wasm4Host.FRAMEBUFFER_ADDRESS, 6400)
            val jitFb = jitMem.readBytes(Wasm4Host.FRAMEBUFFER_ADDRESS, 6400)
            if (!interpFb.contentEquals(jitFb)) {
                var diffs = 0
                for (index in interpFb.indices) {
                    if (interpFb[index] != jitFb[index]) { diffs++ }
                }
                println("frame $frame: framebuffer differs at $diffs bytes")
            } else {
                println("frame $frame: framebuffer matches")
            }
        }
    }

    @Test
    fun opcodeAuditAllGames() {
        val games = listOf("watris", "snake", "minesweeper")
        for (gameName in games) {
            val path = Path.of("../assets/wasm4/$gameName.wasm")
            if (!Files.exists(path)) { continue }
            val bytes = Files.readAllBytes(path)
            val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(bytes)
            val audit = org.wark.compile.WasmOpcodeAudit(wasmModule)
            val stats = audit.audit()

            val jitMissing = stats.values.filter { !it.jitHandled && it.count > 0 }
                .sortedByDescending { it.count }
            val totalOpcodes = stats.values.sumOf { it.count }
            val uniqueOpcodes = stats.size

            println("=== $gameName: ${wasmModule.functions.size} functions, $totalOpcodes instructions, $uniqueOpcodes unique opcodes ===")
            if (jitMissing.isNotEmpty()) {
                println("  JIT MISSING:")
                for (stat in jitMissing) {
                    println("    ${stat.mnemonic}: ${stat.count} occurrences")
                }
            } else {
                println("  JIT: 100% opcode coverage")
            }

            // Report opcodes unique to this game (not in watris)
            val watrisPath = Path.of("../assets/wasm4/watris.wasm")
            if (gameName != "watris" && Files.exists(watrisPath)) {
                val watrisBytes = Files.readAllBytes(watrisPath)
                val watrisModule = org.kgen.target.wasm.module.WasmModuleReader.read(watrisBytes)
                val watrisAudit = org.wark.compile.WasmOpcodeAudit(watrisModule)
                val watrisStats = watrisAudit.audit()
                val watrisOpcodes = watrisStats.keys
                val uniqueToGame = stats.keys.filter { it !in watrisOpcodes }
                if (uniqueToGame.isNotEmpty()) {
                    println("  Opcodes NOT in watris: $uniqueToGame")
                }
            }
        }
    }

    @Test
    fun snakeJitCompilationOnly() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val host = Wasm4Host()
        val imports = host.buildImports()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(imports)

        println("snake JIT instantiation OK (${instance.exportedFunctions().size} exports)")
        for (export in instance.exportedFunctions()) {
            println("  export: $export")
        }
    }

    private fun countPixels(runner: Wasm4Runner): Int {
        var count = 0
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                if (runner.getPixel(x, y) != 0) { count++ }
            }
        }
        return count
    }
}

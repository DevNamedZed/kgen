package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeNewGameTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    private fun loadAndInitialize(mode: ExecutionMode, boundsCheck: Boolean = false): QuakeRunner {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, mode)
        if (boundsCheck) {
            println("[TEST] Bounds checking enabled")
            runner.instance().enableBoundsChecking()
        }
        runner.initialize()
        return runner
    }

    private fun runFrames(runner: QuakeRunner, count: Int) {
        val deltaTime = 1.0f / 30.0f
        for (frameIndex in 1..count) {
            runner.frame(deltaTime)
        }
    }

    private fun pressKey(runner: QuakeRunner, keyCode: Int) {
        try {
            runner.keyEvent(keyCode, true)
        } catch (exception: Exception) {
            println("[TEST] keyEvent($keyCode, down) exception: ${exception.message}")
        }
        runFrames(runner, 2)
        try {
            runner.keyEvent(keyCode, false)
        } catch (exception: Exception) {
            println("[TEST] keyEvent($keyCode, up) exception: ${exception.message}")
        }
        runFrames(runner, 2)
    }

    private fun navigateToNewGame(runner: QuakeRunner): Boolean {
        println("[TEST] Running initial frames...")
        runFrames(runner, 10)

        println("[TEST] Pressing Escape to open menu...")
        pressKey(runner, 27)
        runFrames(runner, 5)

        println("[TEST] Pressing Enter on 'Single Player'...")
        pressKey(runner, 13)
        runFrames(runner, 5)

        println("[TEST] Pressing Enter on first episode...")
        pressKey(runner, 13)
        runFrames(runner, 5)

        println("[TEST] Pressing Enter on difficulty...")
        pressKey(runner, 13)

        println("[TEST] Running frames for map loading...")
        for (frameIndex in 1..120) {
            try {
                runner.frame(1.0f / 30.0f)
            } catch (exception: Exception) {
                println("[TEST] Exception at frame $frameIndex: ${exception.javaClass.simpleName}: ${exception.message}")
                return false
            }
        }
        println("[TEST] Map loading frames complete")
        return true
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun startNewGameInterpreter() {
        println("=== Testing New Game with INTERPRETER ===")
        val runner = loadAndInitialize(ExecutionMode.INTERPRET)
        val succeeded = navigateToNewGame(runner)
        println("=== Interpreter test: ${if (succeeded) "PASSED" else "FAILED"} ===")
        runner.shutdown()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun startNewGameJit() {
        println("=== Testing New Game with JIT ===")
        val runner = loadAndInitialize(ExecutionMode.JIT)
        val succeeded = navigateToNewGame(runner)
        println("=== JIT test: ${if (succeeded) "PASSED" else "FAILED"} ===")
        runner.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mode = if (args.isNotEmpty() && args[0] == "jit") {
                ExecutionMode.JIT
            } else {
                ExecutionMode.INTERPRET
            }
            val boundsCheck = args.any { it == "bounds" }
            println("=== QuakeNewGameTest: mode=$mode boundsCheck=$boundsCheck ===")
            val test = QuakeNewGameTest()
            if (!test.quakeWasmExists()) {
                println("quake.wasm not found at ${test.wasmPath}")
                return
            }
            val runner = test.loadAndInitialize(mode, boundsCheck)
            val succeeded = test.navigateToNewGame(runner)
            println("=== Result: ${if (succeeded) "PASSED" else "FAILED"} ===")
            try {
                runner.shutdown()
            } catch (exception: Exception) {
                println("[TEST] Shutdown exception: ${exception.message}")
            }
        }
    }
}

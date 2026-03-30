package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeMapLoadTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun run(mode: ExecutionMode) {
        println("=== Map load test: mode=$mode ===")
        val runner = QuakeRunner.load(wasmPath, gameDirectory, mode)
        runner.initialize()

        println("[TEST] Running 10 initial frames...")
        for (i in 1..10) {
            runner.frame(1.0f / 30.0f)
        }

        val memory = runner.memory() as org.wark.WarkMemory
        memory.watchAddress = 0x39AF38
        var watchHitCount = 0
        memory.watchCallback = { writeOffset, newValue, oldValue ->
            watchHitCount++
            if (watchHitCount <= 10) {
                System.err.println("[WATCH] #$watchHitCount at 0x${Integer.toHexString(writeOffset)}: $oldValue → $newValue")
                Thread.currentThread().stackTrace.drop(1).take(5).forEach { frame ->
                    if (!frame.className.contains("Thread") && !frame.className.contains("lambda")) {
                        System.err.println("[WATCH]   $frame")
                    }
                }
            }
        }

        println("[TEST] Escape → menu")
        sendKey(runner, 27)

        println("[TEST] Enter → Single Player")
        sendKey(runner, 13)

        println("[TEST] Enter → Episode 1")
        sendKey(runner, 13)

        println("[TEST] Enter → Easy difficulty")
        sendKey(runner, 13)

        println("[TEST] Running 30 post-load frames...")
        for (i in 1..30) {
            runner.frame(1.0f / 30.0f)
        }

        println("[TEST] Second attempt: Enter again")
        sendKey(runner, 13)

        println("[TEST] Running 30 more frames...")
        for (i in 1..30) {
            runner.frame(1.0f / 30.0f)
        }

        val fbWidth = runner.framebufferWidth()
        val fbHeight = runner.framebufferHeight()
        val fbPtr = runner.framebufferPointer()
        println("[TEST] Framebuffer: ${fbWidth}x${fbHeight} at 0x${Integer.toHexString(fbPtr)}")

        val pixels = runner.memory().readBytes(fbPtr, fbWidth * fbHeight)
        val uniqueColors = mutableSetOf<Int>()
        for (pixel in pixels) {
            uniqueColors.add(pixel.toInt() and 0xFF)
        }
        println("[TEST] Unique palette indices: ${uniqueColors.size}")
        println("[TEST] ${if (uniqueColors.size > 10) "GOOD - varied framebuffer" else "BAD - blank/uniform"}")

        try {
            runner.shutdown()
        } catch (exception: Exception) {
            println("[TEST] Shutdown: ${exception.message}")
        }
    }

    private fun sendKey(runner: QuakeRunner, keyCode: Int) {
        runner.keyEvent(keyCode, true)
        for (i in 1..3) { runner.frame(1.0f / 30.0f) }
        runner.keyEvent(keyCode, false)
        for (i in 1..3) { runner.frame(1.0f / 30.0f) }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val mode = if (args.isNotEmpty() && args[0] == "jit") ExecutionMode.JIT else ExecutionMode.INTERPRET
            val test = QuakeMapLoadTest()
            if (Files.exists(test.wasmPath)) {
                test.run(mode)
            }
        }
    }
}

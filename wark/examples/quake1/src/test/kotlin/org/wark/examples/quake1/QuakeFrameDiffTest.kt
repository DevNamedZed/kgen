package org.wark.examples.quake1

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

class QuakeFrameDiffTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun run() {
        val interpLines = mutableListOf<String>()
        val jitLines = mutableListOf<String>()

        println("=== Running interpreter for 2 frames ===")
        val interpRunner = loadWithCapture(ExecutionMode.INTERPRET, interpLines)
        interpRunner.initialize()
        interpLines.add("--- FRAME 1 ---")
        interpRunner.frame(1.0f / 30.0f)
        interpLines.add("--- FRAME 2 ---")
        interpRunner.frame(1.0f / 30.0f)
        val interpSnap = interpRunner.memory().readBytes(0, interpRunner.memory().sizeBytes())

        println("\n=== Running JIT for 2 frames ===")
        val jitRunner = loadWithCapture(ExecutionMode.JIT, jitLines)
        jitRunner.initialize()
        jitLines.add("--- FRAME 1 ---")
        jitRunner.frame(1.0f / 30.0f)
        jitLines.add("--- FRAME 2 ---")
        jitRunner.frame(1.0f / 30.0f)
        val jitSnap = jitRunner.memory().readBytes(0, jitRunner.memory().sizeBytes())

        println("\n=== Console output comparison ===")
        var firstDiff = -1
        val maxLines = maxOf(interpLines.size, jitLines.size)
        for (line in 0 until maxLines) {
            val interpLine = interpLines.getOrElse(line) { "<missing>" }
            val jitLine = jitLines.getOrElse(line) { "<missing>" }
            if (interpLine != jitLine && firstDiff == -1) {
                firstDiff = line
            }
        }
        println("  Interp lines: ${interpLines.size}, JIT lines: ${jitLines.size}")
        if (firstDiff >= 0) {
            println("  First diff at line $firstDiff:")
            val start = maxOf(0, firstDiff - 3)
            val end = minOf(maxLines, firstDiff + 10)
            for (line in start until end) {
                val interpLine = interpLines.getOrElse(line) { "<missing>" }
                val jitLine = jitLines.getOrElse(line) { "<missing>" }
                val marker = if (interpLine != jitLine) ">>>" else "   "
                println("$marker [$line] INTERP: $interpLine")
                if (interpLine != jitLine) {
                    println("$marker [$line] JIT:    $jitLine")
                }
            }
        } else {
            println("  Console output is IDENTICAL")
        }

        println("\n=== Memory diffs ===")
        val minSize = minOf(interpSnap.size, jitSnap.size)
        var diffCount = 0
        for (address in 0 until minSize step 4) {
            val interpVal = readI32(interpSnap, address)
            val jitVal = readI32(jitSnap, address)
            if (interpVal != jitVal) {
                diffCount++
            }
        }
        println("  Total diffs: $diffCount")

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    private fun loadWithCapture(mode: ExecutionMode, output: MutableList<String>): QuakeRunner {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        setjmpEmulation.traceEnabled = true
        val wasi = WasiPreview1.builder().directory(gameDirectory).build()
        val runtime = WarkRuntime.create(WasmTarget.V2_0, mode)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        // Override clock_time_get with deterministic time to eliminate timing diffs
        val startTimeNanos = 1000000000000L // fixed start time
        val clockCounter = longArrayOf(0)
        builder.function("wasi_snapshot_preview1", "clock_time_get",
            org.wark.HostFunction { instance, args ->
                val clockId = args[0].toInt()
                val resultAddress = args[2].toInt()
                val timeNanos = startTimeNanos + clockCounter[0] * 33333333L // ~30fps increments
                clockCounter[0]++
                instance.memory().writeI64(resultAddress, timeNanos)
                longArrayOf(0L)
            })

        builder.function("quake:host/system", "print",
            org.wark.HostFunction { instance, args ->
                val address = args[0].toInt()
                val length = args[1].toInt()
                val bytes = instance.memory().readBytes(address, length)
                val text = String(bytes, Charsets.UTF_8)
                for (line in text.split('\n')) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        output.add(trimmed)
                    }
                }
                longArrayOf()
            })

        val instance = module.instantiate(builder.build())
        if (mode == ExecutionMode.JIT) {
            instance.enableBoundsChecking()
            if (System.getProperty("skipMem2Reg", "false") == "true") {
                instance.skipMem2Reg = true
            }
        }
        return QuakeRunner(instance, host, wasi)
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) { return 0 }
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeFrameDiffTest()
            if (Files.exists(test.wasmPath)) {
                test.run()
            }
        }
    }
}

package org.wark.examples.quake1

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkInstance
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the Quake 1 lifecycle: loading the WASM module, calling initialization,
 * running frames, and forwarding input events.
 *
 * Exports used:
 * - _initialize() — WASI module init
 * - q_init(memoryMB) → status — initialize Quake engine
 * - q_frame(dt) → status — run one frame (physics + render)
 * - q_key_event(key, down) — keyboard input
 * - q_mouse_move(dx, dy) — mouse movement
 * - q_mouse_button(button, down) — mouse buttons
 * - q_console_command(ptr) — execute console command
 * - q_shutdown() — clean up
 * - q_get_framebuffer_ptr/width/height — framebuffer access
 * - q_get_palette_ptr — 256-color palette
 * - q_get_audio_buffer_ptr/size/sample_rate — audio access
 */
class QuakeRunner(
    private val instance: WarkInstance,
    private val host: QuakeHost,
    private val wasi: WasiPreview1,
) {

    fun initialize(memoryMegabytes: Int = 32): Int {
        relocateStack()
        instance.call("_initialize")
        populatePreopensIfNeeded()
        val result = instance.call("q_init", memoryMegabytes.toLong())
        return result[0].toInt()
    }

    private fun relocateStack() {
        val memory = instance.memory()
        val currentTop = memory.sizeBytes()
        val newStackPointer = currentTop - 16
        instance.global(0).setI32(newStackPointer)
    }

    fun frame(deltaTime: Float): Int {
        val bits = java.lang.Float.floatToRawIntBits(deltaTime)
        return try {
            val result = instance.call("q_frame", bits.toLong())
            result[0].toInt()
        } catch (trap: org.wark.WasmTrap) {
            0
        }
    }

    fun keyEvent(key: Int, down: Boolean) {
        instance.call("q_key_event", key.toLong(), if (down) 1L else 0L)
    }

    fun mouseMove(deltaX: Int, deltaY: Int) {
        instance.call("q_mouse_move", deltaX.toLong(), deltaY.toLong())
    }

    fun mouseButton(button: Int, down: Boolean) {
        instance.call("q_mouse_button", button.toLong(), if (down) 1L else 0L)
    }

    fun consoleCommand(command: String) {
        val memory = instance.memory()
        val bytes = command.toByteArray(Charsets.UTF_8)
        val address = mallocInWasm(bytes.size + 1)
        memory.writeBytes(address, bytes)
        memory.writeByte(address + bytes.size, 0)
        instance.call("q_console_command", address.toLong())
        freeInWasm(address)
    }

    fun shutdown() {
        instance.call("q_shutdown")
        wasi.fileTable().closeAll()
    }

    fun framebufferPointer(): Int = instance.call("q_get_framebuffer_ptr")[0].toInt()
    fun framebufferWidth(): Int = instance.call("q_get_framebuffer_width")[0].toInt()
    fun framebufferHeight(): Int = instance.call("q_get_framebuffer_height")[0].toInt()
    fun palettePointer(): Int = instance.call("q_get_palette_ptr")[0].toInt()
    fun audioBufferPointer(): Int = instance.call("q_get_audio_buffer_ptr")[0].toInt()
    fun audioBufferSize(): Int = instance.call("q_get_audio_buffer_size")[0].toInt()
    fun audioSampleRate(): Int = instance.call("q_get_audio_sample_rate")[0].toInt()

    fun memory(): org.wark.WarkMemory = instance.memory()
    fun host(): QuakeHost = host

    private fun mallocInWasm(size: Int): Int =
        instance.call("malloc", size.toLong())[0].toInt()

    private fun freeInWasm(address: Int) {
        instance.call("free", address.toLong())
    }

    private fun populatePreopensIfNeeded() {
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount

        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val globalIndex = localIndex + importCount
            val name = wasmModule.functionName(globalIndex)
            if (name == "__wasilibc_populate_preopens") {
                instance.callByIndex(globalIndex)
                return
            }
        }
    }

    companion object {
        @JvmStatic
        fun load(
            wasmPath: Path,
            gameDirectory: Path,
            mode: ExecutionMode = ExecutionMode.INTERPRET,
            frameCallback: QuakeHost.FrameCallback? = null,
        ): QuakeRunner {
            val wasmBytes = Files.readAllBytes(wasmPath)
            val host = QuakeHost(frameCallback)
            val setjmpEmulation = SetjmpEmulation()

            val wasi = WasiPreview1.builder()
                .directory(gameDirectory)
                .build()

            val runtime = WarkRuntime.create(WasmTarget.V2_0, mode)
            val module = runtime.load(wasmBytes)

            val builder = WarkImports.builder()
            host.registerImports(builder)
            wasi.registerImports(builder)
            setjmpEmulation.registerImports(builder)
            val instance = module.instantiate(builder.build())

            return QuakeRunner(instance, host, wasi)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size < 2) {
                println("Usage: quake <path/to/quake.wasm> <game-directory> [interpret|jit]")
                return
            }

            val wasmPath = Path.of(args[0])
            val gameDirectory = Path.of(args[1])
            val mode = if (args.size > 2 && args[2] == "interpret") {
                ExecutionMode.INTERPRET
            } else {
                ExecutionMode.JIT
            }

            println("[QUAKE] Loading: $wasmPath")
            println("[QUAKE] Game directory: $gameDirectory")
            println("[QUAKE] Mode: $mode")

            val runner = load(wasmPath, gameDirectory, mode)

            println("[QUAKE] Initializing...")
            val initResult = runner.initialize()
            println("[QUAKE] Init result: $initResult")
            println("[QUAKE] Framebuffer: ${runner.framebufferWidth()}x${runner.framebufferHeight()}")
            println("[QUAKE] Audio: ${runner.audioSampleRate()} Hz, buffer=${runner.audioBufferSize()}")

            val targetFrameTime = 1.0f / 60.0f
            var lastTime = System.nanoTime()

            println("[QUAKE] Running...")
            while (true) {
                val now = System.nanoTime()
                val deltaTime = (now - lastTime) / 1_000_000_000.0f
                lastTime = now

                runner.frame(deltaTime.coerceAtMost(targetFrameTime * 3))

                val elapsed = (System.nanoTime() - now) / 1_000_000.0
                val sleepMs = ((targetFrameTime * 1000.0) - elapsed).toLong()
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs)
                }
            }

            runner.shutdown()
            println("[QUAKE] Shutdown complete")
        }
    }
}

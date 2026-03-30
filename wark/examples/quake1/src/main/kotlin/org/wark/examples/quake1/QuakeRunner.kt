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
        setupSurfaceExtentTrace()
        // The stack lives at the top of WASM memory. q_init's malloc for the hunk
        // grows memory via sbrk from the heap upward. If the hunk is too large it
        // overlaps the stack — Cache_FreeHigh zeroes model_precache during BSP loading,
        // and R_RenderView's warpbuffer gets corrupted. Cap the hunk to leave room.
        val memoryBytes = instance.memory().sizeBytes()
        val stackReserve = 2 * 1024 * 1024 // 2MB for stack headroom
        val maxHunkMegabytes = (memoryBytes - stackReserve) / (1024 * 1024)
        val hunkMegabytes = minOf(memoryMegabytes, maxHunkMegabytes)
        val result = instance.call("q_init", hunkMegabytes.toLong())
        return result[0].toInt()
    }

    private fun setupSurfaceExtentTrace() {
        val wasmModule = instance.module.wasmModule
        val importCount = wasmModule.importedFunctionCount
        var calcExtentsFuncIndex = -1
        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val name = wasmModule.functionName(localIndex + importCount)
            if (name == "CalcSurfaceExtents") {
                calcExtentsFuncIndex = localIndex + importCount
                break
            }
        }
        if (calcExtentsFuncIndex >= 0) {
            val modLoadFacesFuncIndex = findFunction(wasmModule, importCount, "Mod_LoadFaces")
            val hunkAllocFuncIndex = findFunction(wasmModule, importCount, "Hunk_AllocName")
            val modLoadBrushFuncIndex = findFunction(wasmModule, importCount, "Mod_LoadBrushModel")
            var bspBufferAddr = 0
            var startBspParsing = false
            var allocDuringParse = 0

            instance.setFunctionEntryCallback { functionIndex, args ->
                if (functionIndex == calcExtentsFuncIndex && args.isNotEmpty()) {
                    host.lastSurfacePointer = args[0].toInt()
                }
                if (functionIndex == modLoadBrushFuncIndex && args.size >= 2) {
                    bspBufferAddr = args[1].toInt()
                    System.err.println("[DEBUG] Mod_LoadBrushModel(buffer=0x${Integer.toHexString(bspBufferAddr)})")
                }
                if (functionIndex == modLoadFacesFuncIndex && args.isNotEmpty()) {
                    val lumpPtr = args[0].toInt()
                    val mem = instance.memory()
                    val fileofs = mem.readI32(lumpPtr)
                    val filelen = mem.readI32(lumpPtr + 4)
                    val count = filelen / 20
                    System.err.println("[DEBUG] Mod_LoadFaces(fileofs=$fileofs, filelen=$filelen, count=$count)")
                    if (count == 5556 && bspBufferAddr != 0) {
                        startBspParsing = true
                        allocDuringParse = 0
                        val face730InBuf = bspBufferAddr + fileofs + 730 * 20 + 8
                        val raw = mem.readI32(face730InBuf)
                        val numedges = raw and 0xFFFF
                        System.err.println("[DEBUG] BSP buffer face 730: addr=0x${Integer.toHexString(face730InBuf)} raw=0x${Integer.toHexString(raw)} numedges=$numedges")
                    }
                }
                if (functionIndex == hunkAllocFuncIndex && startBspParsing && bspBufferAddr != 0) {
                    allocDuringParse++
                    val size = args[0].toInt()
                    val mem = instance.memory()
                    val face730InBuf = bspBufferAddr + 255248 + 730 * 20 + 8
                    val numedges = mem.readI32(face730InBuf) and 0xFFFF
                    if (numedges != 6 && allocDuringParse <= 20) {
                        System.err.println("[DEBUG] Alloc #$allocDuringParse size=$size: face 730 numedges=$numedges CORRUPTED at 0x${Integer.toHexString(face730InBuf)}")
                    }
                }
            }
        }
    }

    private fun relocateStack() {
        val memory = instance.memory()
        val currentTop = memory.sizeBytes()
        val newStackPointer = currentTop - 16
        instance.global(0).setI32(newStackPointer)
        instance.syncGlobals()
    }

    fun frame(deltaTime: Float): Int {
        val bits = java.lang.Float.floatToRawIntBits(deltaTime)
        return try {
            val result = instance.call("q_frame", bits.toLong())
            result[0].toInt()
        } catch (exit: org.wark.wasi.WasiExitException) {
            System.err.println("[QUAKE] proc_exit(${exit.exitCode}) during frame — ignoring")
            instance.clearExceptionState()
            0
        } catch (trap: org.wark.WasmTrap) {
            instance.clearExceptionState()
            0
        }
    }

    fun keyEvent(key: Int, down: Boolean) {
        clearPendingTrap()
        try {
            instance.call("q_key_event", key.toLong(), if (down) 1L else 0L)
        } catch (trap: org.wark.WasmTrap) {
            instance.clearExceptionState()
        }
    }

    fun mouseMove(deltaX: Int, deltaY: Int) {
        clearPendingTrap()
        try {
            instance.call("q_mouse_move", deltaX.toLong(), deltaY.toLong())
        } catch (trap: org.wark.WasmTrap) {
            instance.clearExceptionState()
        }
    }

    fun mouseButton(button: Int, down: Boolean) {
        clearPendingTrap()
        try {
            instance.call("q_mouse_button", button.toLong(), if (down) 1L else 0L)
        } catch (trap: org.wark.WasmTrap) {
            instance.clearExceptionState()
        }
    }

    private fun clearPendingTrap() {
        try {
            org.wark.WarkInstance.checkPendingTrap()
        } catch (ignored: org.wark.WasmTrap) {
        }
        instance.clearExceptionState()
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
        try {
            instance.call("q_shutdown")
        } catch (trap: org.wark.WasmTrap) {
            instance.clearExceptionState()
        }
        wasi.fileTable().closeAll()
    }

    fun framebufferPointer(): Int = instance.call("q_get_framebuffer_ptr")[0].toInt()
    fun framebufferWidth(): Int = instance.call("q_get_framebuffer_width")[0].toInt()
    fun framebufferHeight(): Int = instance.call("q_get_framebuffer_height")[0].toInt()
    fun palettePointer(): Int = instance.call("q_get_palette_ptr")[0].toInt()
    fun audioBufferPointer(): Int = instance.call("q_get_audio_buffer_ptr")[0].toInt()
    fun audioBufferSize(): Int = instance.call("q_get_audio_buffer_size")[0].toInt()
    fun audioSampleRate(): Int = instance.call("q_get_audio_sample_rate")[0].toInt()

    fun instance(): WarkInstance = instance
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

        private fun findFunction(wasmModule: org.kgen.target.wasm.module.WasmModule, importCount: Int, name: String): Int {
            for ((localIndex, _) in wasmModule.functions.withIndex()) {
                if (wasmModule.functionName(localIndex + importCount) == name) {
                    return localIndex + importCount
                }
            }
            return -1
        }

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

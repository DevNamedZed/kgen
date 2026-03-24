package org.wark.examples.doom

import org.wark.ExecutionMode
import org.wark.HostFunction
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

/**
 * Host for jacobenget/doom.wasm — a clean DOOM port with minimal imports.
 *
 * Interface (10 imports, 4 exports):
 *
 * Imports:
 * - loading.onGameInit(width, height)
 * - loading.wadSizes() → i32
 * - loading.readWads(ptr)
 * - runtimeControl.timeInMilliseconds() → i32
 * - ui.drawFrame(ptr)
 * - gameSaving.sizeOfSaveGame() → i32
 * - gameSaving.readSaveGame(ptr, size) → i32
 * - gameSaving.writeSaveGame(ptr, size) → i32
 * - console.onInfoMessage(ptr)
 * - console.onErrorMessage(ptr)
 *
 * Exports:
 * - initGame()
 * - tickGame()
 * - reportKeyDown(doomKey: i32)
 * - reportKeyUp(doomKey: i32)
 *
 * @see <a href="https://github.com/jacobenget/doom.wasm">doom.wasm</a>
 */
class DoomHost(
    private val wadData: ByteArray,
    private val frameCallback: FrameCallback? = null,
) {
    private val startTime = System.currentTimeMillis()
    private val consoleLog = StringBuilder()

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("loading", "onGameInit", onGameInit())
        builder.function("loading", "wadSizes", wadSizes())
        builder.function("loading", "readWads", readWads())
        builder.function("runtimeControl", "timeInMilliseconds", timeInMilliseconds())
        builder.function("ui", "drawFrame", drawFrame())
        builder.function("gameSaving", "sizeOfSaveGame", sizeOfSaveGame())
        builder.function("gameSaving", "readSaveGame", readSaveGame())
        builder.function("gameSaving", "writeSaveGame", writeSaveGame())
        builder.function("console", "onInfoMessage", onInfoMessage())
        builder.function("console", "onErrorMessage", onErrorMessage())
    }

    fun consoleLog(): String = consoleLog.toString()

    var screenWidth = 0
    var screenHeight = 0

    private fun onGameInit(): HostFunction = HostFunction { instance, args ->
        screenWidth = args[0].toInt()
        screenHeight = args[1].toInt()
        System.out.println("[DOOM] Video init: ${screenWidth}x${screenHeight}")
        System.out.flush()
        longArrayOf()
    }

    private fun wadSizes(): HostFunction = HostFunction { instance, args ->
        longArrayOf(wadData.size.toLong())
    }

    private fun readWads(): HostFunction = HostFunction { instance, args ->
        val destAddress = args[0].toInt()
        val memory = instance.memory()
        memory.writeBytes(destAddress, wadData)
        longArrayOf()
    }

    private fun timeInMilliseconds(): HostFunction = HostFunction { instance, args ->
        longArrayOf((System.currentTimeMillis() - startTime))
    }

    private var frameCount = 0

    private fun drawFrame(): HostFunction = HostFunction { instance, args ->
        val framebufferAddress = args[0].toInt()
        frameCount++
        if (frameCount <= 3 || frameCount % 60 == 0) {
            System.out.println("[DOOM] Frame $frameCount (fb@$framebufferAddress)")
            System.out.flush()
        }
        frameCallback?.onFrame(instance, framebufferAddress)
        longArrayOf()
    }

    private fun sizeOfSaveGame(): HostFunction = HostFunction { _, _ -> longArrayOf(0) }
    private fun readSaveGame(): HostFunction = HostFunction { _, _ -> longArrayOf(0) }
    private fun writeSaveGame(): HostFunction = HostFunction { _, _ -> longArrayOf(0) }

    private fun onInfoMessage(): HostFunction = HostFunction { instance, args ->
        val messageAddress = args[0].toInt()
        val message = instance.memory().readUtf8(messageAddress)
        System.out.println("[DOOM] $message")
        System.out.flush()
        longArrayOf()
    }

    private fun onErrorMessage(): HostFunction = HostFunction { instance, args ->
        val messageAddress = args[0].toInt()
        val message = instance.memory().readUtf8(messageAddress)
        System.err.println("[DOOM ERROR] $message")
        System.err.flush()
        longArrayOf()
    }

    fun interface FrameCallback {
        fun onFrame(instance: org.wark.WarkInstance, framebufferAddress: Int)
    }

    companion object {
        @JvmStatic
        fun load(wasmPath: Path, wadPath: Path, mode: ExecutionMode = ExecutionMode.INTERPRET): DoomRunner {
            val wasmBytes = Files.readAllBytes(wasmPath)
            val wadData = Files.readAllBytes(wadPath)
            val host = DoomHost(wadData)

            val runtime = WarkRuntime.create(WasmTarget.V2_0, mode)
            val module = runtime.load(wasmBytes)

            val builder = WarkImports.builder()
            host.registerImports(builder)
            val instance = module.instantiate(builder.build())

            return DoomRunner(instance, host)
        }
    }
}

class DoomRunner(
    private val instance: org.wark.WarkInstance,
    private val host: DoomHost,
) {
    fun initGame() {
        instance.call("initGame")
    }

    fun tick() {
        instance.call("tickGame")
    }

    fun keyDown(doomKey: Int) {
        instance.call("reportKeyDown", doomKey.toLong())
    }

    fun keyUp(doomKey: Int) {
        instance.call("reportKeyUp", doomKey.toLong())
    }

    fun consoleLog(): String = host.consoleLog()

    companion object {
        const val KEY_RIGHTARROW = 0xAE
        const val KEY_LEFTARROW = 0xAC
        const val KEY_UPARROW = 0xAD
        const val KEY_DOWNARROW = 0xAF
        const val KEY_STRAFE_L = 0xA0
        const val KEY_STRAFE_R = 0xA1
        const val KEY_USE = 0xA2
        const val KEY_FIRE = 0xA3
        const val KEY_ESCAPE = 0x1B
        const val KEY_ENTER = 0x0D
        const val KEY_TAB = 0x09
        const val KEY_RSHIFT = 0xB6
        const val KEY_RCTRL = 0x9D
        const val KEY_RALT = 0x9E
    }
}

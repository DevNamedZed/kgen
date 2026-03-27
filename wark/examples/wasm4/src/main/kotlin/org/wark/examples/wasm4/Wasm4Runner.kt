package org.wark.examples.wasm4

import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs a WASM-4 game cartridge.
 *
 * ```java
 * var runner = Wasm4Runner.load(Path.of("snake.wasm"));
 * runner.start();
 * for (int frame = 0; frame < 300; frame++) {
 *     runner.update();
 * }
 * ```
 */
class Wasm4Runner private constructor(
    private val wasmBytes: ByteArray,
    private val host: Wasm4Host,
    private val mode: ExecutionMode,
) {
    private var instance: org.wark.WarkInstance? = null
    private var frameCount = 0

    fun start() {
        val runtime = WarkRuntime.create(WasmTarget.MVP, mode)
        val module = runtime.load(wasmBytes)
        val imports = host.buildImports()
        val inst = module.instantiate(imports)
        instance = inst

        // System memory initialized by Wasm4Host.registerImports before instantiation

        if (inst.exportedFunctions().contains("_initialize")) {
            inst.call("_initialize")
        }
        if (inst.exportedFunctions().contains("start")) {
            inst.call("start")
        }
    }

    fun update() {
        val inst = instance ?: throw WasmTrap("Not started")
        if (inst.exportedFunctions().contains("update")) {
            inst.call("update")
        }
        host.readFramebuffer(inst)
        frameCount++
    }

    fun setGamepad(buttons: Int) {
        val inst = instance ?: return
        val memory = inst.memory()
        memory.writeByte(Wasm4Host.GAMEPAD1_ADDRESS, buttons.toByte())
        memory.writeByte(Wasm4Host.GAMEPAD1_ADDRESS + 1, 0)
        memory.writeByte(Wasm4Host.GAMEPAD1_ADDRESS + 2, 0)
        memory.writeByte(Wasm4Host.GAMEPAD1_ADDRESS + 3, 0)
    }

    fun setMouse(x: Int, y: Int, buttons: Int) {
        val inst = instance ?: return
        val memory = inst.memory()
        memory.writeI32(Wasm4Host.MOUSE_X_ADDRESS, x.toShort().toInt())
        memory.writeI32(Wasm4Host.MOUSE_Y_ADDRESS, y.toShort().toInt())
        memory.writeByte(Wasm4Host.MOUSE_BUTTONS_ADDRESS, buttons.toByte())
    }

    fun frameCount(): Int = frameCount

    fun traceOutput(): String = host.traceOutput()

    fun getPixel(x: Int, y: Int): Int = host.getPixel(x, y)

    companion object {
        @JvmStatic
        fun load(path: Path, mode: ExecutionMode = ExecutionMode.INTERPRET): Wasm4Runner {
            val bytes = Files.readAllBytes(path)
            return load(bytes, mode)
        }

        @JvmStatic
        fun load(bytes: ByteArray, mode: ExecutionMode = ExecutionMode.INTERPRET): Wasm4Runner {
            val host = Wasm4Host()
            return Wasm4Runner(bytes, host, mode)
        }
    }
}

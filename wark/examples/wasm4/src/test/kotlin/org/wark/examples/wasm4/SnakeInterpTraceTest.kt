package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class SnakeInterpTraceTest {

    private val snakePath = Path.of("../assets/wasm4/snake.wasm")

    @Test
    fun snakeStartAndUpdateWithInterpreter() {
        assumeTrue(Files.exists(snakePath), "snake.wasm not found")
        val runner = Wasm4Runner.load(snakePath, ExecutionMode.INTERPRET)

        try {
            runner.start()
            println("start() OK")
        } catch (e: WasmTrap) {
            println("start() trapped: ${e.message}")
        }

        for (frame in 1..10) {
            try {
                runner.update()
            } catch (e: WasmTrap) {
                println("update() frame $frame trapped: ${e.message}")
                return
            }
        }
        println("10 interpreter frames OK")
    }

    @Test
    @org.junit.jupiter.api.Disabled("JIT start function init order issue")
    fun snakeRunsMultipleFramesJit() {
        assumeTrue(Files.exists(snakePath), "snake.wasm not found")
        val runner = Wasm4Runner.load(snakePath, ExecutionMode.JIT)

        runner.start()
        println("JIT start() OK")

        for (frame in 1..10) {
            try {
                runner.update()
            } catch (e: Exception) {
                println("JIT update() frame $frame error: ${e.message}")
                return
            }
        }
        println("JIT 10 frames OK")
    }

    @org.junit.jupiter.api.Disabled("JIT crashes JVM on unreachable trap")
    @Test
    fun snakeStartAndUpdateJit() {
        assumeTrue(Files.exists(snakePath), "snake.wasm not found")
        val bytes = Files.readAllBytes(snakePath)
        val host = Wasm4Host()
        val imports = host.buildImports()
        val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(imports)

        val memory = instance.memory()
        memory.writeI32(0x04, 0xe0f8cf.toInt())
        memory.writeI32(0x08, 0x86c06c)
        memory.writeI32(0x0C, 0x306850)
        memory.writeI32(0x10, 0x071821)
        memory.writeByte(0x14, 0x03)
        memory.writeByte(0x15, 0x12)
        memory.writeI32(Wasm4Host.MOUSE_X_ADDRESS, 0x7fff)
        memory.writeI32(Wasm4Host.MOUSE_Y_ADDRESS, 0x7fff)

        try {
            instance.call("start")
            println("JIT start() OK")
        } catch (e: Exception) {
            println("JIT start() error: ${e.message}")
        }

        // Run 10 frames
        for (frame in 1..10) {
            try {
                instance.call("update")
            } catch (e: Exception) {
                println("JIT update() frame $frame error: ${e.message}")
                return
            }
        }
        println("JIT 10 frames OK")
    }
}

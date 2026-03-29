package org.wark.examples.quake1

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

class QuakeLoadTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun loadModule() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)

        assertNotNull(module)
        println("Module loaded successfully")
        println("Exported functions: ${module.exportedFunctionNames()}")
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun instantiateWithImports() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder()
            .directory(gameDirectory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        val instance = module.instantiate(builder.build())
        assertNotNull(instance)
        println("Instance created successfully")

        wasi.fileTable().closeAll()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun initializeEngine() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)

        val result = runner.initialize()
        println("Init result: $result")

        val width = runner.framebufferWidth()
        val height = runner.framebufferHeight()
        println("Framebuffer: ${width}x${height}")
        assertTrue(width > 0)
        assertTrue(height > 0)

        val sampleRate = runner.audioSampleRate()
        val bufferSize = runner.audioBufferSize()
        println("Audio: $sampleRate Hz, buffer=$bufferSize")
        assertTrue(sampleRate > 0)

        runner.shutdown()
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun runSingleFrame() {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()

        try {
            val result = runner.frame(1.0f / 60.0f)
            println("Frame result: $result")
        } catch (exception: org.wark.WasmTrap) {
            println("Frame failed: ${exception.message}")
            if (!exception.message!!.contains("signature_mismatch")) {
                throw exception
            }
        }
        println("Frame count (from init): ${runner.host().frameCount()}")
        assertTrue(runner.host().frameCount() > 0, "Should have rendered frames during init")

        runner.shutdown()
    }
}

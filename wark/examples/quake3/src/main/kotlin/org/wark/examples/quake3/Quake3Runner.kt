package org.wark.examples.quake3

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.examples.quake3.EmscriptenHost
import org.wark.examples.quake3.SdlHost
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

/**
 * Quake 3 WASM runner.
 *
 * ```
 * Quake3Runner.run(Path.of("ioquake3.wasm"))
 * ```
 */
object Quake3Runner {

    @JvmStatic
    fun run(wasmPath: Path, mode: ExecutionMode = ExecutionMode.JIT) {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, mode)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()

        val wasi = WasiPreview1.builder()
            .args("quake3")
            .build()
        wasi.registerImports(builder)

        val emscripten = EmscriptenHost()
        emscripten.registerImports(builder)

        val sdl = SdlHost.load()
        sdl?.registerImports(builder)

        val instance = module.instantiate(builder.build())

        if (instance.exportedFunctions().contains("_start")) {
            instance.call("_start")
        } else if (instance.exportedFunctions().contains("main")) {
            instance.call("main", 0, 0)
        }

        if (emscripten.mainLoopFunction() >= 0) {
            println("Quake 3 main loop registered. Running frames...")
            var frameCount = 0
            while (emscripten.isRunning() && frameCount < 60 * 60) {
                Thread.sleep(16)
                frameCount++
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage: Quake3Runner <path-to-ioquake3.wasm>")
            return
        }
        val mode = if (args.contains("--interpret")) {
            ExecutionMode.INTERPRET
        } else {
            ExecutionMode.JIT
        }
        run(Path.of(args[0]), mode)
    }
}

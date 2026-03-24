package org.wark

import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom.wasm"))
    val wadBytes = Files.readAllBytes(Path.of("examples/assets/doom1.wad"))
    var frameCount = 0

    System.err.println("Starting DOOM JIT runner (heap: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB)")
    System.err.flush()
    println("Creating instance...")
    val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT).load(wasmBytes).instantiate(
        WarkImports.builder()
            .function("loading", "onGameInit", HostFunction { _, args -> println("onGameInit(${args[0]}, ${args[1]})"); longArrayOf() })
            .function("loading", "wadSizes", HostFunction { _, _ -> longArrayOf() })
            .function("loading", "readWads", HostFunction { _, _ -> longArrayOf() })
            .function("runtimeControl", "timeInMilliseconds", HostFunction { _, _ -> longArrayOf(System.currentTimeMillis()) })
            .function("ui", "drawFrame", HostFunction { inst, args ->
                frameCount++
                if (frameCount <= 3) {
                    println("drawFrame #$frameCount (ptr=${args[0]})")
                }
                longArrayOf()
            })
            .function("gameSaving", "sizeOfSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("gameSaving", "readSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("gameSaving", "writeSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("console", "onInfoMessage", HostFunction { _, _ -> longArrayOf() })
            .function("console", "onErrorMessage", HostFunction { _, _ -> longArrayOf() })
            .build()
    )

    println("Calling initGame...")
    instance.call("initGame")
    println("initGame completed! Memory: ${instance.memory().pages()} pages")

    println("Calling tickGame (3 frames)...")
    for (tick in 1..3) {
        instance.call("tickGame")
        println("tickGame #$tick done, frames=$frameCount")
    }
    println("DOOM is running! $frameCount frames rendered.")
}

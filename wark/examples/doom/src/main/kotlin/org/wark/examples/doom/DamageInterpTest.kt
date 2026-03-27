package org.wark.examples.doom

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

object DamageInterpTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val wasmPath = Path.of("../assets/doom_wasm_debug.wasm")
        val wadPath = Path.of("../assets/doom1.wad")
        if (!Files.exists(wasmPath)) { println("WASM not found"); return }

        val wadBytes = if (Files.exists(wadPath)) { Files.readAllBytes(wadPath) } else { ByteArray(0) }
        val wasmBytes = Files.readAllBytes(wasmPath)

        println("Loading with interpreter...")
        val host = DoomHost(wadBytes)
        val builder = WarkImports.builder()
        host.registerImports(builder)
        val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(builder.build())

        println("Running initGame...")
        instance.setInstructionLimit(500_000_000)
        try {
            instance.call("initGame")
            println("initGame done!")
        } catch (e: Exception) {
            println("initGame: ${e.message}")
        }

        // Read health before
        // players[0].health is at a fixed offset in DOOM's memory
        // We don't know the exact offset, but we can check la_damage and P_DamageMobj calls

        // Fire weapon by simulating key events
        val KEY_FIRE = 0xA3
        println("Pressing fire key...")
        instance.call("reportKeyDown", KEY_FIRE.toLong())

        println("Running 5 ticks with fire held...")
        for (tick in 1..5) {
            try {
                instance.call("tickGame")
                val laDamage = instance.memory().readI32(4385300)
                println("  tick $tick: la_damage=$laDamage")
            } catch (e: Exception) {
                println("  tick $tick: ${e.message}")
                break
            }
        }

        instance.call("reportKeyUp", KEY_FIRE.toLong())

        println("Running 5 more ticks...")
        for (tick in 6..10) {
            try {
                instance.call("tickGame")
            } catch (e: Exception) {
                println("  tick $tick: ${e.message}")
                break
            }
        }

        println("Done. Check if damage was applied (would need memory inspection).")
    }
}

package org.wark.examples.doom

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class DoomWasmLoadTest {

    private val doomWasmPath = Path.of("../assets/doom.wasm")

    @Test
    fun loadAndInspectDoomWasm() {
        assumeTrue(Files.exists(doomWasmPath), "doom.wasm not found at $doomWasmPath")

        val bytes = Files.readAllBytes(doomWasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0)
        val module = runtime.load(bytes)

        val exports = module.exportedFunctionNames()
        println("DOOM exports (${exports.size}): $exports")
        assertTrue(exports.contains("initGame"), "Should export initGame, got: $exports")
        assertTrue(exports.contains("tickGame"), "Should export tickGame")

        val importCount = module.importedFunctionCount()
        println("DOOM function imports: $importCount")

        val allImports = module.imports()
        for (imp in allImports) {
            println("  import: ${imp.module}.${imp.name}")
        }
    }
}

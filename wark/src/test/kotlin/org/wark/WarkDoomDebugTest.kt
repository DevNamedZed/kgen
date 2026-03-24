package org.wark

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmOpcodeAudit
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class WarkDoomDebugTest {

    private val doomPath = Path.of("examples/assets/doom.wasm")

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun auditDoomOpcodeCoverage() {
        assumeTrue(Files.exists(doomPath))
        val module = WasmModuleReader.read(Files.readAllBytes(doomPath))
        println(WasmOpcodeAudit(module).report())
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun dumpDoomModuleInfo() {
        assumeTrue(Files.exists(doomPath))
        val module = WasmModuleReader.read(Files.readAllBytes(doomPath))

        println("Functions: ${module.functions.size}")
        println("Imports: ${module.importedFunctionCount} functions")
        println("Globals: ${module.globals.size}")
        for ((index, global) in module.globals.withIndex()) {
            val value = evalInit(global.initExpr)
            println("  global[$index]: type=${global.type} mutable=${global.mutable} value=$value")
        }
        println("Memories: min=${module.memories.firstOrNull()?.min} max=${module.memories.firstOrNull()?.max}")
        println("Tables: ${module.tables.size}")
        val activeElements = module.elements.filterIsInstance<org.kgen.target.wasm.module.WasmModule.Element.Active>()
        println("Elements: ${activeElements.sumOf { it.funcIndices.size }} function table entries")
    }

    private fun evalInit(expr: ByteArray): Long {
        if (expr.isEmpty() || expr[0].toInt() and 0xFF != 0x41) { return 0L }
        var result = 0; var shift = 0; var pos = 1
        while (pos < expr.size) {
            val b = expr[pos].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift); shift += 7; pos++
            if (b and 0x80 == 0) { if (shift < 32 && b and 0x40 != 0) { result = result or ((-1) shl shift) }; break }
        }
        return result.toLong()
    }
}

package org.wark

import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val debugPath = Path.of("examples/assets/doom1_debug.wasm")
    val wasmBytes = Files.readAllBytes(debugPath)
    val wasmModule = WasmModuleReader.read(wasmBytes)
    val importCount = wasmModule.importedFunctionCount

    // Find all function names from DWARF (if name section is available)
    println("Looking for named functions...")
    val namedFunctions = mutableMapOf<String, Int>()
    for ((index, func) in wasmModule.functions.withIndex()) {
        val globalIndex = index + importCount
        val name = wasmModule.functionName(globalIndex)
        if (name != null) {
            namedFunctions[name] = index
        }
    }
    println("Named functions: ${namedFunctions.size}")

    // If no names from name section, try to match by body signature
    if (namedFunctions.isEmpty()) {
        println("No name section. Using body matching...")
        val disasm = org.kgen.target.wasm.disasm.WasmDisassembler()

        for ((index, func) in wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(func.body)
            val mnemonics = instructions.map { it.opcode.mnemonic }
            val text = mnemonics.joinToString(" ")

            // FixedMul: (i32, i32) -> i32, body < 20 bytes, has i64.mul + i64.shr_u
            val funcType = wasmModule.types[func.typeIndex]
            if (funcType.params.size == 2 && func.body.size < 20 && "i64.mul" in text && "i64.shr_u" in text) {
                namedFunctions["FixedMul"] = index
            }
        }
    }

    if (namedFunctions.isNotEmpty()) {
        println("Found functions:")
        for ((name, index) in namedFunctions.entries.sortedBy { it.value }.take(30)) {
            println("  func_$index: $name")
        }
    }

    // Now create JIT and interpreter instances and compare call_indirect dispatch
    println()
    println("Creating JIT instance...")
    val wadBytes = ByteArray(0) // no WAD needed for function testing

    val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(buildMinimalImports())

    println("Running initGame via JIT...")
    jitInstance.call("initGame")
    println("initGame done.")

    // Read some game state to verify
    val memory = jitInstance.memory()
    println()
    println("Memory size: ${memory.sizeBytes()} bytes (${memory.pages()} pages)")

    // Dump the function table to see what call_indirect dispatches to
    val inspector = jitInstance.inspector()
    if (inspector != null) {
        println()
        println("JIT inspector available")
        // Try to get IR for a damage-related function
        for (name in listOf("FixedMul", "P_Random", "P_DamageMobj", "P_LineAttack")) {
            val funcIndex = namedFunctions[name]
            if (funcIndex != null) {
                val funcName = "func_$funcIndex"
                val ir = inspector.dumpIr(funcName)
                println()
                println("=== $name ($funcName) IR ===")
                println(ir.take(500))
                if (ir.length > 500) println("... (${ir.length} chars)")
            }
        }
    }
}

private fun buildMinimalImports(): WarkImports = WarkImports.builder()
    .function("loading", "onGameInit") { _, _ -> longArrayOf() }
    .function("loading", "wadSizes") { _, _ -> longArrayOf(0) }
    .function("loading", "readWads") { _, _ -> longArrayOf() }
    .function("runtimeControl", "timeInMilliseconds") { _, _ -> longArrayOf(0) }
    .function("ui", "drawFrame") { _, _ -> longArrayOf() }
    .function("gameSaving", "sizeOfSaveGame") { _, _ -> longArrayOf(0) }
    .function("gameSaving", "readSaveGame") { _, _ -> longArrayOf(0) }
    .function("gameSaving", "writeSaveGame") { _, _ -> longArrayOf(0) }
    .function("console", "onInfoMessage") { _, _ -> longArrayOf() }
    .function("console", "onErrorMessage") { _, _ -> longArrayOf() }
    .build()

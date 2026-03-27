package org.wark

import org.kgen.binary.dwarf.DwarfReader
import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val wasmBytes = Files.readAllBytes(Path.of("examples/assets/doom-directly-after-linking.wasm"))
    val module = WasmModuleReader.read(wasmBytes)

    // Extract DWARF custom sections
    val debugInfo = module.customSections.firstOrNull { it.name == ".debug_info" }?.data
    val debugAbbrev = module.customSections.firstOrNull { it.name == ".debug_abbrev" }?.data
    val debugStr = module.customSections.firstOrNull { it.name == ".debug_str" }?.data

    if (debugInfo == null || debugAbbrev == null) {
        println("No DWARF sections found")
        return
    }

    println("DWARF sections: info=${debugInfo.size}, abbrev=${debugAbbrev.size}, str=${debugStr?.size ?: 0}")

    // WASM uses 32-bit addresses (function indices)
    val info = DwarfReader.read(debugInfo, debugAbbrev, debugStr, is64Bit = false)

    println("Compile units: ${info.compileUnits.size}")
    var totalSubprograms = 0
    for (cu in info.compileUnits) {
        totalSubprograms += cu.subprograms.size
    }
    println("Total subprograms: $totalSubprograms")
    println()

    // Build code offset → function index map
    // DWARF lowPC is the byte offset into the code section PAYLOAD (after the function count LEB128).
    // Each function body is preceded by a LEB128 size prefix, then the body bytes.
    val codeOffsets = mutableListOf<Pair<Int, Int>>() // (startOffset, funcIndex)
    var offset = 0
    for ((index, func) in module.functions.withIndex()) {
        // Account for the LEB128 size prefix before the body
        val bodySize = func.body.size
        var prefixLen = 0
        var remaining = bodySize
        do { prefixLen++; remaining = remaining ushr 7 } while (remaining != 0)
        codeOffsets.add(offset to index)
        offset += prefixLen + bodySize
    }

    fun offsetToFuncIndex(codeOffset: Long): Int? {
        for ((start, idx) in codeOffsets.reversed()) {
            if (codeOffset >= start) {
                return idx
            }
        }
        return null
    }

    // Build function index → name map
    val funcIndexToName = mutableMapOf<Int, String>()
    for (cu in info.compileUnits) {
        for (sub in cu.subprograms) {
            if (sub.lowPC > 0) {
                val funcIdx = offsetToFuncIndex(sub.lowPC)
                if (funcIdx != null) {
                    funcIndexToName[funcIdx] = sub.name
                }
            }
        }
    }

    println("Mapped functions: ${funcIndexToName.size}")
    println()

    // Write full mapping to file
    val out = java.io.File("build/doom-func-names.txt")
    out.parentFile.mkdirs()
    val sb = StringBuilder()
    for ((index, func) in module.functions.withIndex()) {
        val name = funcIndexToName[index] ?: ""
        sb.appendLine("func_$index: $name (${func.body.size} bytes)")
    }
    out.writeText(sb.toString())
    println("Wrote to ${out.path}")
    println()

    // Print damage-related
    val keywords = listOf("Damage", "Random", "Shoot", "Attack", "Line", "Aim", "Traverse",
        "Fixed", "P_", "A_Fire", "PTR_", "Gun", "Mobj", "Player", "Think", "Ticker",
        "Psprite", "BuildTiccmd", "WeaponReady")
    for ((idx, name) in funcIndexToName.entries.sortedBy { it.key }) {
        if (keywords.any { name.contains(it) }) {
            println("func_$idx: $name")
        }
    }
}

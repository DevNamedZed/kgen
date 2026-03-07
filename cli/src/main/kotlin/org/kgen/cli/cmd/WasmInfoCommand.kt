package org.kgen.cli.cmd

import org.kgen.backend.wasm.module.WasmModule
import org.kgen.backend.wasm.module.WasmModuleReader
import org.kgen.backend.wasm.disasm.WasmDisassembler
import org.kgen.cli.*

object WasmInfoCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen wasminfo <file>")
        val data = readFileOrExit(path)

        if (detectFormat(data) != BinaryFormat.WASM) {
            err("not a WASM file: $path")
            return
        }

        val module = WasmModuleReader.read(data)

        val showAll = parsed.has("all")
        val showSections = showAll || parsed.has("sections")
        val showImports = showAll || parsed.has("imports")
        val showExports = showAll || parsed.has("exports")
        val showFunctions = showAll || parsed.has("functions")
        val showCode = showAll || parsed.has("c", "code")
        val showNone = !showSections && !showImports && !showExports && !showFunctions && !showCode

        printSummary(module)

        if (showNone) return

        if (showSections) printSections(module)
        if (showImports) printImports(module)
        if (showExports) printExports(module)
        if (showFunctions) printFunctions(module)
        if (showCode) printCode(module)
    }

    private fun printSummary(m: WasmModule) {
        println("WASM Module (version ${m.version})")
        println("  Types:          ${m.types.size}")
        println("  Imports:        ${m.imports.size}")
        println("  Functions:      ${m.functions.size}")
        println("  Tables:         ${m.tables.size}")
        println("  Memories:       ${m.memories.size}")
        println("  Globals:        ${m.globals.size}")
        println("  Exports:        ${m.exports.size}")
        println("  Elements:       ${m.elements.size}")
        println("  Data segments:  ${m.dataSegments.size}")
        if (m.start != null)
            println("  Start function: ${m.start}")
        if (m.customSections.isNotEmpty())
            println("  Custom sects:   ${m.customSections.size} (${m.customSections.joinToString(", ") { it.name }})")
        println()
    }

    private fun printSections(m: WasmModule) {
        println("Sections:")
        var count = 0
        if (m.types.isNotEmpty()) { println("  [1]  Type         ${m.types.size} entries"); count++ }
        if (m.imports.isNotEmpty()) { println("  [2]  Import       ${m.imports.size} entries"); count++ }
        if (m.functions.isNotEmpty()) { println("  [3]  Function     ${m.functions.size} entries"); count++ }
        if (m.tables.isNotEmpty()) { println("  [4]  Table        ${m.tables.size} entries"); count++ }
        if (m.memories.isNotEmpty()) { println("  [5]  Memory       ${m.memories.size} entries"); count++ }
        if (m.globals.isNotEmpty()) { println("  [6]  Global       ${m.globals.size} entries"); count++ }
        if (m.exports.isNotEmpty()) { println("  [7]  Export       ${m.exports.size} entries"); count++ }
        if (m.start != null) { println("  [8]  Start        function ${m.start}"); count++ }
        if (m.elements.isNotEmpty()) { println("  [9]  Element      ${m.elements.size} entries"); count++ }
        if (m.functions.isNotEmpty()) {
            val totalBytes = m.functions.sumOf { it.body.size }
            println("  [10] Code         ${m.functions.size} bodies ($totalBytes bytes)")
            count++
        }
        if (m.dataSegments.isNotEmpty()) {
            val totalBytes = m.dataSegments.sumOf { it.data.size }
            println("  [11] Data         ${m.dataSegments.size} segments ($totalBytes bytes)")
            count++
        }
        for (cs in m.customSections) {
            println("  [0]  Custom       \"${cs.name}\" (${cs.data.size} bytes)")
            count++
        }
        println("  Total: $count sections")
        println()
    }

    private fun printImports(m: WasmModule) {
        if (m.imports.isEmpty()) return

        println("Imports (${m.imports.size}):")
        for ((i, imp) in m.imports.withIndex()) {
            val detail = when (imp) {
                is WasmModule.Import.Func -> {
                    val sig = formatFuncType(m, imp.typeIndex)
                    "func $sig"
                }
                is WasmModule.Import.Table -> {
                    val max = if (imp.max != null) "..${imp.max}" else ".."
                    "table ${imp.refType} ${imp.min}$max"
                }
                is WasmModule.Import.Memory -> {
                    val max = if (imp.max != null) "..${imp.max}" else ".."
                    "memory ${imp.min}$max pages"
                }
                is WasmModule.Import.Global -> {
                    val mut = if (imp.mutable) "var" else "const"
                    "global $mut ${imp.type}"
                }
            }
            println("  [$i] ${imp.module}::${imp.name} ($detail)")
        }
        println()
    }

    private fun printExports(m: WasmModule) {
        if (m.exports.isEmpty()) return

        println("Exports (${m.exports.size}):")
        for ((i, exp) in m.exports.withIndex()) {
            val kind = exp.kind.name.lowercase()
            val detail = when (exp.kind) {
                WasmModule.ExportKind.FUNCTION -> {
                    val funcIdx = exp.index
                    val importedCount = m.importedFunctionCount
                    if (funcIdx >= importedCount && funcIdx - importedCount < m.functions.size) {
                        val typeIdx = m.functions[funcIdx - importedCount].typeIndex
                        " ${formatFuncType(m, typeIdx)}"
                    } else ""
                }
                else -> " index=${exp.index}"
            }
            println("  [$i] $kind ${exp.name}$detail")
        }
        println()
    }

    private fun printFunctions(m: WasmModule) {
        if (m.functions.isEmpty()) return

        val importedCount = m.importedFunctionCount
        println("Functions (${m.functions.size}):")
        for ((i, func) in m.functions.withIndex()) {
            val idx = importedCount + i
            val name = func.name ?: m.functionName(idx) ?: "func$idx"
            val sig = formatFuncType(m, func.typeIndex)
            val locals = if (func.locals.isNotEmpty())
                " locals=[${func.locals.joinToString(", ")}]" else ""
            println("  [$idx] $name $sig (${func.body.size} bytes)$locals")
        }
        println()
    }

    private fun printCode(m: WasmModule) {
        if (m.functions.isEmpty()) return

        val disasm = WasmDisassembler()
        val importedCount = m.importedFunctionCount

        for ((i, func) in m.functions.withIndex()) {
            val idx = importedCount + i
            val name = func.name ?: m.functionName(idx) ?: "func$idx"
            val sig = formatFuncType(m, func.typeIndex)
            println("$name $sig:")

            val instructions = disasm.disassemble(func)
            var indent = 1
            for (inst in instructions) {
                val mnemonic = inst.opcode.mnemonic
                // Decrease indent before end/else
                if (mnemonic == "end" || mnemonic == "else") indent = maxOf(1, indent - 1)

                val prefix = "  ".repeat(indent)
                println("  %04x: %s%s".format(inst.offset, prefix, inst.text()))

                // Increase indent after block/loop/if/else
                if (mnemonic == "block" || mnemonic == "loop" || mnemonic == "if" || mnemonic == "else")
                    indent++
            }
            println()
        }
    }

    private fun formatFuncType(m: WasmModule, typeIndex: Int): String {
        if (typeIndex < 0 || typeIndex >= m.types.size) return "(type $typeIndex)"
        val ft = m.types[typeIndex]
        val params = if (ft.params.isEmpty()) "" else ft.params.joinToString(", ")
        val results = if (ft.results.isEmpty()) "" else " -> ${ft.results.joinToString(", ")}"
        return "($params)$results"
    }
}

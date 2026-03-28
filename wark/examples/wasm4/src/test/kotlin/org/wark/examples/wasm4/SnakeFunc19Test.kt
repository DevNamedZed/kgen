package org.wark.examples.wasm4

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.WasmTrap
import java.nio.file.Files
import java.nio.file.Path

class SnakeFunc19Test {

    @Test
    fun func19DiffTrace() {
        val path = Path.of("../assets/wasm4/snake.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount

        // Disassemble func_19
        val function = wasmModule.functions[19]
        val funcType = wasmModule.types[function.typeIndex]
        val disassembler = WasmDisassembler()
        val instructions = disassembler.disassemble(function.body)
        println("func_19: ${funcType.params} -> ${funcType.results}, ${instructions.size} instructions")

        // Find unreachable instructions and their context
        for ((index, inst) in instructions.withIndex()) {
            if (inst.opcode.mnemonic == "unreachable") {
                println("  unreachable at PC=$index")
                for (ctx in maxOf(0, index - 8) until index) {
                    println("    PC=$ctx: ${instructions[ctx].text()}")
                }
                println("    PC=$index: ${instructions[index].text()}")
            }
        }

        // Check for typed blocks/loops
        for ((index, inst) in instructions.withIndex()) {
            val text = inst.text()
            if (text.startsWith("block") || text.startsWith("loop") || text.startsWith("if")) {
                val type = inst.operands
                if (type is org.kgen.target.wasm.disasm.WasmInstruction.Operands.BlockType && type.type != -64) {
                    println("  PC=$index: TYPED ${text.substringBefore(' ')} (type=${type.type})")
                }
            }
        }

        // Run interpreter and trace what func_19 does
        val host = Wasm4Host()
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(host.buildImports())
        val interpreter = interpInstance.interpreter()
        val func19Global = importCount + 19

        var func19Called = false
        interpreter.onFunctionEntry = { funcIndex, args ->
            if (funcIndex == func19Global && !func19Called) {
                func19Called = true
                println("\ninterp func_19 called with args: ${args.toList()}")
            }
        }

        interpInstance.call("update")
        println("interpreter update OK, func_19 called: $func19Called")

        // Also disassemble func_10 (the one that actually traps)
        val func10 = wasmModule.functions[10]
        val func10Type = wasmModule.types[func10.typeIndex]
        val func10Instrs = disassembler.disassemble(func10.body)
        println("\nfunc_10: ${func10Type.params} -> ${func10Type.results}, ${func10Instrs.size} instructions")
        for ((index, inst) in func10Instrs.withIndex()) {
            if (inst.opcode.mnemonic == "unreachable") {
                println("  unreachable at PC=$index")
                for (ctx in maxOf(0, index - 5) until index) {
                    println("    PC=$ctx: ${func10Instrs[ctx].text()}")
                }
            }
        }
        // Print first 30 instructions
        println("  first 30 instructions:")
        for (index in 0 until minOf(30, func10Instrs.size)) {
            println("    PC=$index: ${func10Instrs[index].text()}")
        }
        // Print interpreter's global values
        println("interpreter globals:")
        for (index in 0 until 20) {
            try {
                println("  global[$index] = ${interpInstance.global(index).rawValue()}")
            } catch (e: Exception) { break }
        }

        // Check WASM module's global definitions
        println("\nWASM globals (${wasmModule.globals.size} module-defined):")
        for ((index, g) in wasmModule.globals.withIndex()) {
            println("  global[$index]: type=${g.type}, mutable=${g.mutable}, initExpr=${g.initExpr.map { it.toInt() and 0xFF }}")
        }
        println("Imported globals: ${wasmModule.imports.count { it is org.kgen.target.wasm.module.WasmModule.Import.Global }}")
        println("Start function: ${wasmModule.start}")
        println("Exports: ${wasmModule.exports.map { "${it.name} (${it.kind}, index ${it.index})" }}")

        // NOTE: Snake JIT crashes the JVM — do NOT run JIT here.
        // Use the standalone SnakeJitRunner instead.

        // Memory comparison omitted — JIT crashes the JVM
    }
}

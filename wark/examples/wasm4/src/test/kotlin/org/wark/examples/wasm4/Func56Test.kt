package org.wark.examples.wasm4

import kotlin.test.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkMemory
import org.wark.WarkRuntime
import org.wark.WasmTarget
import java.nio.file.Files
import java.nio.file.Path

class Func56Test {

    @Test
    fun func56DisassemblyAndTest() {
        val path = Path.of("../assets/wasm4/minesweeper.wasm")
        assumeTrue(Files.exists(path))
        val bytes = Files.readAllBytes(path)
        val wasmModule = WasmModuleReader.read(bytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        // Dump func_56
        val function = wasmModule.functions[56]
        val funcType = wasmModule.types[function.typeIndex]
        val instructions = disassembler.disassemble(function.body)
        println("func_56: ${funcType.params} -> ${funcType.results}, ${instructions.size} instructions")
        for ((index, instruction) in instructions.withIndex()) {
            println("  $index: ${instruction.text()}")
        }

        // Test func_56 with the actual argument from func_2: p3 = 16 (0x10)
        val memory = WarkMemory.create(2, 2)
        memory.writeI32(0x04, 0xe0f8cf.toInt())
        memory.writeI32(0x08, 0x86c06c)
        memory.writeI32(0x0C, 0x306850)
        memory.writeI32(0x10, 0x071821)
        memory.writeByte(0x14, 0x03)
        memory.writeByte(0x15, 0x12)
        val imports = WarkImports.builder()
            .memory("env", "memory", memory)
            .function("env", "blit") { _, _ -> longArrayOf() }
            .function("env", "blitSub") { _, _ -> longArrayOf() }
            .function("env", "line") { _, _ -> longArrayOf() }
            .function("env", "hline") { _, _ -> longArrayOf() }
            .function("env", "vline") { _, _ -> longArrayOf() }
            .function("env", "oval") { _, _ -> longArrayOf() }
            .function("env", "rect") { _, _ -> longArrayOf() }
            .function("env", "text") { _, _ -> longArrayOf() }
            .function("env", "textUtf8") { _, _ -> longArrayOf() }
            .function("env", "textUtf16") { _, _ -> longArrayOf() }
            .function("env", "tone") { _, _ -> longArrayOf() }
            .function("env", "diskr") { _, _ -> longArrayOf(0) }
            .function("env", "diskw") { _, _ -> longArrayOf(0) }
            .function("env", "trace") { _, _ -> longArrayOf() }
            .function("env", "tracef") { _, _ -> longArrayOf() }
            .build()

        // Test func_56 on interpreter
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(bytes).instantiate(imports)

        // Test func_56 on JIT
        val memory2 = WarkMemory.create(2, 2)
        memory2.writeI32(0x04, 0xe0f8cf.toInt())
        memory2.writeI32(0x08, 0x86c06c)
        memory2.writeI32(0x0C, 0x306850)
        memory2.writeI32(0x10, 0x071821)
        memory2.writeByte(0x14, 0x03)
        memory2.writeByte(0x15, 0x12)
        val imports2 = WarkImports.builder()
            .memory("env", "memory", memory2)
            .function("env", "blit") { _, _ -> longArrayOf() }
            .function("env", "blitSub") { _, _ -> longArrayOf() }
            .function("env", "line") { _, _ -> longArrayOf() }
            .function("env", "hline") { _, _ -> longArrayOf() }
            .function("env", "vline") { _, _ -> longArrayOf() }
            .function("env", "oval") { _, _ -> longArrayOf() }
            .function("env", "rect") { _, _ -> longArrayOf() }
            .function("env", "text") { _, _ -> longArrayOf() }
            .function("env", "textUtf8") { _, _ -> longArrayOf() }
            .function("env", "textUtf16") { _, _ -> longArrayOf() }
            .function("env", "tone") { _, _ -> longArrayOf() }
            .function("env", "diskr") { _, _ -> longArrayOf(0) }
            .function("env", "diskw") { _, _ -> longArrayOf(0) }
            .function("env", "trace") { _, _ -> longArrayOf() }
            .function("env", "tracef") { _, _ -> longArrayOf() }
            .build()
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(bytes).instantiate(imports2)

        // Call func_56 with various inputs
        val globalIndex56 = importCount + 56
        val exportName = wasmModule.exports.find { it.index == globalIndex56 }?.name

        // func_56 isn't exported, so we call it through the instance's internal API
        // Instead, test by comparing update results — the func_56 return affects post-loop behavior
        println("\nfunc_2 args: (8328, 49152, 16384, 16)")
        println("func_56 is called with p3=16 as first call, some computed value as second call")

        // Test func_56 by calling func_26 which wraps it (or call update and trace func_56 returns)
        // func_56 is not exported directly, but we can test via a wrapper
        // Let me build a small WASM that calls func_56's logic: loop with shr until <= 1
        val testAsm = org.kgen.target.wasm.asm.WasmAssembler.create()
        testAsm.memory("mem", 1, exported = true)
        testAsm.beginFunction("countBits", listOf(org.kgen.target.wasm.WasmValueType.I32), listOf(org.kgen.target.wasm.WasmValueType.I32), exported = true)
        // Same logic as func_56: loop counting right-shifts until p0 <= 1
        val localCounter = testAsm.declareLocal(org.kgen.target.wasm.WasmValueType.I32)
        testAsm.beginLoop(org.kgen.target.wasm.WasmBlockType.I32)
        testAsm.localGet(0) // p0
        testAsm.i32Const(1)
        testAsm.i32LeU()
        testAsm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
        testAsm.localGet(localCounter) // return counter
        testAsm.beginElse()
        testAsm.localGet(0)
        testAsm.i32Const(1)
        testAsm.i32ShrU()
        testAsm.localSet(0)
        testAsm.localGet(localCounter)
        testAsm.i32Const(1)
        testAsm.i32Add()
        testAsm.localSet(localCounter)
        testAsm.br(1) // loop back
        testAsm.end() // end if
        testAsm.end() // end loop
        testAsm.endFunction()
        val testBytes = testAsm.assemble()

        val testInterp = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(testBytes).instantiate()
        val testJit = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(testBytes).instantiate()

        for (input in listOf(0, 1, 2, 4, 8, 16, 32, 1024, 0x7FFFFFFF.toLong())) {
            val interpResult = testInterp.call("countBits", input)[0]
            val jitResult = testJit.call("countBits", input)[0]
            val match = if (interpResult == jitResult) { "OK" } else { "MISMATCH!" }
            println("countBits($input): interp=$interpResult, jit=$jitResult $match")
        }
        println()

        // Also test the shr step: func_56(16) should return 4, then shr(15, 4) = 0, then func_56(0) should return 0
        val shr15by4 = 15 ushr 4
        println("shr(15, 4) = $shr15by4")
        println("countBits(0) should be 0: interp=${testInterp.call("countBits", 0)[0]}, jit=${testJit.call("countBits", 0)[0]}")

        // Dump IR
        val irModule = testJit.compiledIr()
        if (irModule != null) {
            val fn = irModule.functions.find { it.name == "countBits" }
            if (fn != null) {
                println("\ncountBits IR:")
                println("params: ${fn.params}")
                println("blocks: ${fn.blocks.size}")
                for (block in fn.blocks) {
                    println("  ${block.label}: (${block.instructions.size} instructions)")
                    for (inst in block.instructions) {
                        println("    $inst")
                    }
                }
            }
        }
    }
}

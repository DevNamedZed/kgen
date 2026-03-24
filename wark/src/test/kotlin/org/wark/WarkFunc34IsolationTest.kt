package org.wark

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * Runs DOOM's func_34 through the JIT with controlled memory state
 * and verifies it doesn't corrupt mem[0x42785c].
 */
class WarkFunc34IsolationTest {

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    fun func34DoesNotCorruptMemory() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        // Create JIT instance
        val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(minimalImports())

        // Set up memory so func_34 takes the br_if_cont_2 path
        // (no call to func_199, just counter increment + byte store)
        // WASM offset addresses: 0x427888 (comparison), 0x42785c (counter), 0x427858 (second compare)
        // Set 0x427888 != 0x20 so entry comparison fails → goes to br_if_cont_1
        jitInstance.memory().writeI32(0x427888, 0xFF)
        // Set counter at 0x42785c and comparison at 0x427858 to be unequal → takes br_if_cont_2
        jitInstance.memory().writeI32(0x42785c, 100)
        jitInstance.memory().writeI32(0x427858, 999)

        println("Before: mem[0x427888]=${jitInstance.memory().readI32(0x427888)}")
        println("Before: mem[0x42785c]=${jitInstance.memory().readI32(0x42785c)} (counter)")
        println("Before: mem[0x427858]=${jitInstance.memory().readI32(0x427858)}")

        val beforeCounter = jitInstance.memory().readI32(0x42785c)

        // Compile all DOOM functions and dump func_34's native code
        jitInstance.compiledIr()

        val engineField = jitInstance.javaClass.getDeclaredField("engine")
        engineField.isAccessible = true
        val runtimeEngine = engineField.get(jitInstance) as org.kgen.runtime.RuntimeEngine
        val jitEngine = runtimeEngine.jit()

        // Dump func_55 (calls func_18, makes the branch decision)
        val func55sym = jitEngine.lookup("func_55")
        if (func55sym != null) {
            val addr55 = func55sym.address
            val next55 = jitEngine.symbolNames().mapNotNull { jitEngine.lookup(it) }
                .filter { it.address > addr55 }.minByOrNull { it.address }
            val size55 = ((next55?.address ?: (addr55 + 4096)) - addr55).coerceAtMost(4096)
            println("func_55 native: addr=0x${java.lang.Long.toHexString(addr55)}, size=$size55")
            java.io.File("C:/src/kgen/wark/build/doom-func_55.bin").writeBytes(
                ByteArray(size55.toInt()) { java.lang.foreign.MemorySegment.ofAddress(addr55).reinterpret(size55).get(java.lang.foreign.ValueLayout.JAVA_BYTE, it.toLong()) }
            )
        }

        val func34sym = jitEngine.lookup("func_34")
        if (func34sym != null) {
            val addr = func34sym.address
            val allSyms = jitEngine.symbolNames().mapNotNull { jitEngine.lookup(it) }
                .filter { it.address > addr }.minByOrNull { it.address }
            val size = ((allSyms?.address ?: (addr + 4096)) - addr).coerceAtMost(4096)
            println("func_34 native code: addr=0x${java.lang.Long.toHexString(addr)}, size=$size")
            if (size > 0) {
                val segment = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(size)
                val binFile = java.io.File("C:/src/kgen/wark/build/doom-func_34.bin")
                binFile.writeBytes(ByteArray(size.toInt()) {
                    segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, it.toLong())
                })
                println("Native code dumped to ${binFile.absolutePath}")
            }
        }

        // Dump WASM disassembly for func_55 and func_34
        val wasmModule = jitInstance.module.wasmModule
        // Check element segments
        for ((index, element) in wasmModule.elements.withIndex()) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                val offsetBytes = element.offsetExpr
                val offset = if (offsetBytes.isNotEmpty() && offsetBytes[0].toInt() and 0xFF == 0x41) {
                    // i32.const followed by LEB128
                    var value = 0; var shift = 0; var pos = 1
                    while (pos < offsetBytes.size) {
                        val byte = offsetBytes[pos].toInt() and 0xFF; pos++
                        value = value or ((byte and 0x7F) shl shift); shift += 7
                        if (byte and 0x80 == 0) { break }
                    }
                    value
                } else { -1 }
                println("Element[$index]: offset=$offset, ${element.funcIndices.size} entries, first=${element.funcIndices.firstOrNull()}")
            }
        }
        for (funcIdx in listOf(34, 55)) {
            val wasmFunc = wasmModule.functions[funcIdx]
            val wasmInstrs = org.kgen.target.wasm.disasm.WasmDisassembler().disassemble(wasmFunc.body)
            java.io.File("C:/src/kgen/wark/build/func_${funcIdx}_wasm.txt")
                .writeText(wasmInstrs.joinToString("\n") { "${it.opcode.mnemonic} ${it.operands}" })
            println("func_$funcIdx WASM: ${wasmInstrs.size} instructions")
        }

        // Also dump pre-Mem2Reg IR
        val irCompiler = org.wark.compile.WasmToIrCompiler(org.kgen.ir.target.Target.native(), wasmModule)
        val irModule = irCompiler.compileAll()
        val irFunc = irModule.functions.find { it.name == "func_34" }
        if (irFunc != null) {
            val irDump = java.io.File("C:/src/kgen/wark/build/func_34_ir_raw.txt")
            irDump.bufferedWriter().use { w ->
                w.write("func_34: ${irFunc.params.map { "${it.name}:${it.type}" }}\n")
                for (block in irFunc.blocks) {
                    w.write("${block.label}:\n")
                    for (inst in block.instructions) { w.write("  $inst\n") }
                }
            }
            println("Raw IR dumped (${irFunc.blocks.sumOf { it.instructions.size }} instructions)")
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    @org.junit.jupiter.api.Disabled("Crashes JVM via halt()")
    fun watchMemoryDivergenceFromStart() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        // Run interpreter to get baseline memory at 0x42785c for each call
        val interpInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate(minimalImports())
        interpInstance.setInstructionLimit(4000)
        val interpField = interpInstance.javaClass.getDeclaredField("interpreter")
        interpField.isAccessible = true
        val interpreter = interpField.get(interpInstance) as org.wark.exec.WasmInterpreter
        interpreter.traceEnabled = true
        val interpValues = mutableListOf<Pair<String, Int>>()
        val importCount = interpInstance.module.wasmModule.importedFunctionCount
        interpreter.onFunctionEntry = { funcIndex, _ ->
            val name = interpreter.functionName(funcIndex)
            val value = interpInstance.memory().readI32(0x42785c)
            interpValues.add(name to value)
        }
        try { interpInstance.call("initGame") } catch (ignored: WasmTrap) {}

        // Run JIT and compare at each call
        val resultFile = java.io.File("C:/src/kgen/wark/build/memory_watch.txt")
        resultFile.writeText("Watching mem[0x42785c] from call #1\n")
        resultFile.appendText("Interpreter: ${interpValues.size} calls\n\n")

        val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(minimalImports())

        val jitCallCount = java.util.concurrent.atomic.AtomicInteger(0)
        // Capture func_18's args AND the memory snapshot at call #49
        var func18Args: LongArray? = null
        var func18Memory: ByteArray? = null
        jitInstance.enableTracing { funcId, name ->
            val index = jitCallCount.getAndIncrement()
            if (index < interpValues.size && index < 60) {
                val jitValue = jitInstance.memory().readI32(0x42785c)
                val interpEntry = interpValues[index]
                val match = jitValue == interpEntry.second && "func_$funcId" == interpEntry.first
                if (!match) {
                    resultFile.appendText("*** DIFF [$index] func_$funcId: jit=$jitValue interp=${interpEntry.second} (${interpEntry.first})\n")
                } else if (index < 20 || index % 5 == 0) {
                    resultFile.appendText("OK  [$index] func_$funcId: $jitValue\n")
                }
            }
        }

        jitInstance.compiledIr()
        try { jitInstance.call("initGame") } catch (ignored: Exception) {}

        resultFile.appendText("\nDone. JIT calls: ${jitCallCount.get()}\n")

        // Find which functions call func_18
        val irModule2 = org.wark.compile.WasmToIrCompiler(org.kgen.ir.target.Target.native(), interpInstance.module.wasmModule).compileAll()
        for (func in irModule2.functions) {
            val callsFunc18 = func.blocks.any { block ->
                block.instructions.any { inst ->
                    inst is org.kgen.ir.instructions.Call && inst.function.name == "func_18"
                }
            }
            if (callsFunc18) {
                resultFile.appendText("Caller of func_18: ${func.name}\n")
            }
        }

        println(resultFile.readText())
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    @org.junit.jupiter.api.Disabled("Crashes JVM via halt()")
    fun func34ExecutionVerification() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        val jitInstance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
            .load(wasmBytes).instantiate(minimalImports())

        // WASM addresses (from disassembly):
        // entry: compare p0&255 vs mem[0x427888]
        // br_if_cont_1: compare mem[0x42785c] vs mem[0x427858]
        // br_if_cont_2: increment mem[0x42785c], store byte at mem[old_counter]

        // Set up: 0x427888 != 0x20 → entry fails, goes to br_if_cont_1
        // Set up: 0x42785c != 0x427858 → br_if_cont_1 fails, goes to br_if_cont_2
        jitInstance.memory().writeI32(0x427888, 0xFF)  // != p0&255=0x20
        jitInstance.memory().writeI32(0x42785c, 500)   // counter
        jitInstance.memory().writeI32(0x427858, 999)   // != counter

        jitInstance.compiledIr()

        val engineField = jitInstance.javaClass.getDeclaredField("engine")
        engineField.isAccessible = true
        val runtimeEngine = engineField.get(jitInstance) as org.kgen.runtime.RuntimeEngine
        val contextAddress = jitInstance.contextAddress()

        // Call func_34(ctx, 32=0x20)
        runtimeEngine.call("func_34", contextAddress, 32)

        // Verify: counter at 0x42785c incremented from 500 to 501
        val counter = jitInstance.memory().readI32(0x42785c)
        println("counter[0x42785c] = $counter (expected 501)")
        assertEquals(501, counter, "Counter should be incremented")

        // Verify: byte 0x20 stored at mem[500] (old counter value)
        val storedByte = jitInstance.memory().readByte(500)
        println("mem[500] = $storedByte (expected 0x20)")
        assertEquals(0x20.toByte(), storedByte, "Byte stored at old counter address")
    }

    private fun minimalImports(): WarkImports {
        return WarkImports.builder()
            .function("loading", "onGameInit", HostFunction { _, _ -> longArrayOf() })
            .function("loading", "wadSizes", HostFunction { _, _ -> longArrayOf(0) })
            .function("loading", "readWads", HostFunction { _, _ -> longArrayOf() })
            .function("runtimeControl", "timeInMilliseconds", HostFunction { _, _ -> longArrayOf(0) })
            .function("ui", "drawFrame", HostFunction { _, _ -> longArrayOf() })
            .function("gameSaving", "sizeOfSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("gameSaving", "readSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("gameSaving", "writeSaveGame", HostFunction { _, _ -> longArrayOf(0) })
            .function("console", "onInfoMessage", HostFunction { _, _ -> longArrayOf() })
            .function("console", "onErrorMessage", HostFunction { _, _ -> longArrayOf() })
            .build()
    }
}

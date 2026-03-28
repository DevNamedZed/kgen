package org.wark

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.foreign.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val gamePath = Path.of("examples/wasm4/build/bin/games/minesweeper.wasm")
    val altPath = Path.of("examples/assets/wasm4/minesweeper.wasm")
    val wasmPath = if (Files.exists(gamePath)) { gamePath } else { altPath }
    if (!Files.exists(wasmPath)) {
        System.err.println("minesweeper.wasm not found at $wasmPath or $altPath")
        return
    }

    val traceFile = File("build/minesweeper-jit-trace.txt")
    traceFile.parentFile.mkdirs()
    val traceStream = PrintStream(FileOutputStream(traceFile), true)

    // If running snake, install crash handler
    if (System.getenv("SNAKE_MODE") != null || wasmPath.toString().contains("snake")) {
        traceStream.println("Snake mode: skipping minesweeper, loading snake...")
    }

    // Quick test: typed loop pattern
    val testAsm = org.kgen.target.wasm.asm.WasmAssembler.create()
    testAsm.memory("mem", 1, exported = true)
    testAsm.beginFunction("countBits", listOf(org.kgen.target.wasm.WasmValueType.I32), listOf(org.kgen.target.wasm.WasmValueType.I32), exported = true)
    val cnt = testAsm.declareLocal(org.kgen.target.wasm.WasmValueType.I32)
    testAsm.beginLoop(org.kgen.target.wasm.WasmBlockType.I32)
    testAsm.localGet(0); testAsm.i32Const(1); testAsm.i32LeU()
    testAsm.beginIf(org.kgen.target.wasm.WasmBlockType.I32)
    testAsm.localGet(cnt)
    testAsm.beginElse()
    testAsm.localGet(0); testAsm.i32Const(1); testAsm.i32ShrU(); testAsm.localSet(0)
    testAsm.localGet(cnt); testAsm.i32Const(1); testAsm.i32Add(); testAsm.localSet(cnt)
    testAsm.br(1)
    testAsm.end(); testAsm.end()
    testAsm.endFunction()
    val testBytes = testAsm.assemble()

    val interpInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(testBytes).instantiate()
    val jitInst = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(testBytes).instantiate()
    for (input in listOf(0L, 1L, 2L, 4L, 16L)) {
        val ir = interpInst.call("countBits", input)[0]
        val jr = jitInst.call("countBits", input)[0]
        traceStream.println("countBits($input): interp=$ir, jit=$jr ${if (ir == jr) { "OK" } else { "MISMATCH" }}")
    }
    traceStream.flush()

    traceStream.println("Loading minesweeper.wasm...")
    val wasmBytes = Files.readAllBytes(wasmPath)
    val memory = WarkMemory.create(2, 2)
    // Initialize WASM-4 system memory
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
        .function("env", "diskr") { _, args -> longArrayOf(0) }
        .function("env", "diskw") { _, args -> longArrayOf(0) }
        .function("env", "trace") { _, _ -> longArrayOf() }
        .function("env", "tracef") { _, _ -> longArrayOf() }
        .build()

    traceStream.println("Creating JIT instance...")
    val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(imports)

    // Dump native code info
    val inspector = instance.inspector()
    if (inspector != null) {
        traceStream.println(inspector.dumpSummary())
        // Dump func_2 and update function native code size
        val updateAsm = inspector.dumpAsm("update")
        traceStream.println("update native: ${updateAsm.lines().size} asm lines")
        val func2Asm = inspector.dumpAsm("func_2")
        traceStream.println("func_2 native: ${func2Asm.lines().size} asm lines")

        // Write full func_2 disassembly to separate file
        val func2File = File("build/func_2_disasm.txt")
        func2File.writeText(func2Asm)
        traceStream.println("func_2 disassembly written to ${func2File.absolutePath}")
    }

    // Run update through interpreter first to get expected values
    traceStream.println("\nSetting up interpreter...")
    val interpMemory = WarkMemory.create(2, 2)
    interpMemory.writeI32(0x04, 0xe0f8cf.toInt())
    interpMemory.writeI32(0x08, 0x86c06c)
    interpMemory.writeI32(0x0C, 0x306850)
    interpMemory.writeI32(0x10, 0x071821)
    interpMemory.writeByte(0x14, 0x03)
    interpMemory.writeByte(0x15, 0x12)
    val interpImports = WarkImports.builder()
        .memory("env", "memory", interpMemory)
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
    val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        .load(wasmBytes).instantiate(interpImports)

    // Compare memory BEFORE any update call
    traceStream.println("\nMemory before update (both fresh from _initialize):")
    val preInterpMem = interpInstance.memory()
    val preJitMem = instance.memory()
    var preDiffs = 0
    var preFirstDiff = -1
    for (offset in 0 until minOf(preInterpMem.sizeBytes(), preJitMem.sizeBytes())) {
        val interpByte = preInterpMem.readByte(offset)
        val jitByte = preJitMem.readByte(offset)
        if (interpByte != jitByte) {
            preDiffs++
            if (preFirstDiff < 0) { preFirstDiff = offset }
            if (preDiffs <= 10) {
                traceStream.println("  PRE-DIFF at 0x${Integer.toHexString(offset)}: interp=0x${Integer.toHexString(interpByte.toInt() and 0xFF)} jit=0x${Integer.toHexString(jitByte.toInt() and 0xFF)}")
            }
        }
    }
    traceStream.println("  Total pre-update diffs: $preDiffs")

    // Snapshot memory at key game state addresses BEFORE update
    traceStream.println("\nKey memory before update:")
    for (addr in listOf(0x2E80, 0x2E84, 0x2E88, 0x2E8C, 0x2EA0, 0x2EA4, 11908, 11916, 11940)) {
        val interpVal = preInterpMem.readI32(addr)
        val jitVal = preJitMem.readI32(addr)
        traceStream.println("  [0x${Integer.toHexString(addr)}] = interp:${interpVal} jit:${jitVal} ${if (interpVal != jitVal) { "MISMATCH" } else { "" }}")
    }

    // Now run interpreter update for post-comparison
    val importCount = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes).importedFunctionCount
    val interpreter = interpInstance.interpreter()
    val func56GlobalIdx = importCount + 56
    interpreter.onFunctionEntry = { funcIndex, args ->
        if (funcIndex == func56GlobalIdx) {
            traceStream.println("  interp func_56 called with: ${args.toList()}")
        }
    }
    interpInstance.call("update")
    traceStream.println("Interpreter update() OK")

    traceStream.println("\nCalling JIT update()...")
    traceStream.flush()
    try {
        instance.call("update")
        traceStream.println("JIT update() completed OK!")
    } catch (exception: WasmTrap) {
        traceStream.println("JIT TRAP: ${exception.message}")

        // Compare memory state at key addresses
        val interpMem = interpInstance.memory()
        val jitMem = instance.memory()
        traceStream.println("\nMemory comparison at key addresses:")
        for (addr in listOf(0, 0x14, 0x16, 0x1F, 0x2A00, 0x2E80, 0x2E88, 0x2E8C, 0x2E90, 0x2EA0)) {
            val interpVal = interpMem.readI32(addr)
            val jitVal = jitMem.readI32(addr)
            val match = if (interpVal == jitVal) { "" } else { " MISMATCH" }
            traceStream.println("  [0x${Integer.toHexString(addr)}]: interp=0x${Integer.toHexString(interpVal)} jit=0x${Integer.toHexString(jitVal)}$match")
        }

        // Compare broader memory ranges
        var totalDiffs = 0
        var firstDiffAddr = -1
        for (offset in 0 until minOf(interpMem.sizeBytes(), jitMem.sizeBytes())) {
            if (interpMem.readByte(offset) != jitMem.readByte(offset)) {
                totalDiffs++
                if (firstDiffAddr < 0) { firstDiffAddr = offset }
            }
        }
        traceStream.println("Total memory diffs: $totalDiffs bytes, first at 0x${Integer.toHexString(firstDiffAddr)}")
    } catch (exception: Exception) {
        traceStream.println("ERROR: ${exception.javaClass.simpleName}: ${exception.message}")
        exception.printStackTrace(traceStream)
    }
    traceStream.flush()

    println("Results written to ${traceFile.absolutePath}")
    println("Last line: ${traceFile.readLines().lastOrNull()}")
}

package org.wark

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarkDoomIrDumpTest {

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun crossFunctionCallJitNoTrace() {
        val asm = WasmAssembler.create()
        asm.beginFunction("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.localGet(0)
        asm.i32Add()
        asm.endFunction()

        asm.beginFunction("quadruple", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.call(0)
        asm.call(0)
        asm.endFunction()

        val bytes = asm.assemble()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()

        val result = instance.call("quadruple", 5L)
        println("quadruple(5) = ${result[0]}")
        assertEquals(20L, result[0])
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    fun tracingWorksOnSimpleModule() {
        val asm = WasmAssembler.create()
        asm.beginFunction("add5", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i32Const(5)
        asm.i32Add()
        asm.endFunction()

        val bytes = asm.assemble()
        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()

        val tracedNames = mutableListOf<String>()
        instance.enableTracing { funcId, name ->
            synchronized(tracedNames) {
                tracedNames.add(name)
            }
        }

        val result = instance.call("add5", 10L)
        println("add5(10) = ${result[0]}, traced: $tracedNames")
        assertEquals(15L, result[0])
        assertTrue(tracedNames.isNotEmpty(), "Should have traced at least one call")
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun doomIrValidityAndMem2Reg() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes)
        val compiler = org.wark.compile.WasmToIrCompiler(org.kgen.ir.target.Target.native(), wasmModule)

        var startTime = System.currentTimeMillis()
        val irModule = compiler.compileAll()
        println("WASM→IR: ${irModule.functions.size} functions in ${System.currentTimeMillis() - startTime}ms")

        var danglingCount = 0
        var multiTerminatorCount = 0
        for (function in irModule.functions) {
            if (function.isExternal || function.blocks.isEmpty()) {
                continue
            }
            val labels = function.blocks.map { it.label }.toHashSet()
            for (block in function.blocks) {
                var terminatorsSeen = 0
                for (instruction in block.instructions) {
                    val targets = when (instruction) {
                        is org.kgen.ir.instructions.Br -> listOf(instruction.target.label)
                        is org.kgen.ir.instructions.CondBr -> listOf(instruction.trueTarget.label, instruction.falseTarget.label)
                        is org.kgen.ir.instructions.Switch -> listOf(instruction.defaultTarget.label) + instruction.cases.map { it.second.label }
                        is org.kgen.ir.instructions.Ret -> { terminatorsSeen++; emptyList() }
                        else -> emptyList()
                    }
                    if (targets.isNotEmpty()) {
                        terminatorsSeen++
                    }
                    for (target in targets) {
                        if (target !in labels) {
                            danglingCount++
                        }
                    }
                }
                if (terminatorsSeen > 1) {
                    multiTerminatorCount++
                }
            }
        }
        println("Dangling refs: $danglingCount, multi-terminators: $multiTerminatorCount")
        assertEquals(0, danglingCount, "No dangling block references")
        assertEquals(0, multiTerminatorCount, "No multi-terminator blocks")

        startTime = System.currentTimeMillis()
        val optimized = org.kgen.pipeline.Mem2Reg().run(irModule)
        val mem2RegTime = System.currentTimeMillis() - startTime
        println("Mem2Reg: ${mem2RegTime}ms, ${optimized.functions.size} functions")
        assertTrue(mem2RegTime < 30000, "Mem2Reg should complete in under 30s")
    }

    private fun findPattern(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (index in 0..data.size - pattern.size) {
            for (patternIndex in pattern.indices) {
                if (data[index + patternIndex] != pattern[patternIndex]) {
                    continue@outer
                }
            }
            return index
        }
        return -1
    }

    private fun dumpNativeBytes(inspector: org.kgen.jit.JitInspector, functionName: String, traceFile: java.io.File) {
        try {
            val engine = inspector.javaClass.getDeclaredField("engine")
            engine.isAccessible = true
            val jitEngine = engine.get(inspector) as org.kgen.jit.JitEngine
            val symbol = jitEngine.lookup(functionName) ?: return
            val address = symbol.address

            // Find next symbol to determine size
            val allSymbols = jitEngine.symbolNames()
                .mapNotNull { jitEngine.lookup(it) }
                .filter { it.address > address }
                .sortedBy { it.address }
            val nextAddr = allSymbols.firstOrNull()?.address ?: (address + 4096)
            val estimatedSize = (nextAddr - address).coerceAtMost(8192)

            traceFile.appendText("NATIVE $functionName: addr=0x${java.lang.Long.toHexString(address)}, est_size=$estimatedSize\n")
            if (estimatedSize > 0) {
                val segment = java.lang.foreign.MemorySegment.ofAddress(address).reinterpret(estimatedSize)
                java.io.File("build/doom-$functionName.bin").writeBytes(
                    ByteArray(estimatedSize.toInt()) { segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, it.toLong()) }
                )
                traceFile.appendText("  (binary dumped to build/doom-$functionName.bin)\n")
            }
        } catch (exception: Exception) {
            traceFile.appendText("  dump failed: ${exception.message}\n")
        }
    }

    private fun createDoomInstance(wasmBytes: ByteArray, wadBytes: ByteArray): WarkInstance {
        return WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT).load(wasmBytes).instantiate(
            WarkImports.builder()
                .function("loading", "onGameInit") { inst, args ->
                    java.io.File("build/doom-trace.log").appendText("HOST: onGameInit(${args[0]}, ${args[1]})\n")
                    longArrayOf()
                }
                .function("loading", "wadSizes") { inst, args -> longArrayOf(wadBytes.size.toLong()) }
                .function("loading", "readWads") { inst, args ->
                    inst.memory().writeBytes(args[0].toInt(), wadBytes)
                    longArrayOf()
                }
                .function("runtimeControl", "timeInMilliseconds") { inst, args ->
                    longArrayOf(System.currentTimeMillis() % 10_000_000)
                }
                .function("ui", "drawFrame") { inst, args -> longArrayOf() }
                .function("gameSaving", "sizeOfSaveGame") { inst, args -> longArrayOf(0) }
                .function("gameSaving", "readSaveGame") { inst, args -> longArrayOf(0) }
                .function("gameSaving", "writeSaveGame") { inst, args -> longArrayOf(0) }
                .function("console", "onInfoMessage") { inst, args -> longArrayOf() }
                .function("console", "onErrorMessage") { inst, args -> longArrayOf() }
                .build()
        )
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun doomFunc122IrDump() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes)
        val compiler = org.wark.compile.WasmToIrCompiler(org.kgen.ir.target.Target.native(), wasmModule)
        val irModule = compiler.compileAll()

        // Dump func_18 (divergence function) to file
        val optimized = org.kgen.pipeline.Mem2Reg().run(irModule)
        val function = optimized.functions.find { it.name == "func_34" }
        if (function != null) {
            val dumpFile = java.io.File("C:/src/kgen/wark/build/func_34_ir.txt")
            dumpFile.bufferedWriter().use { writer ->
                writer.write("func_18: ${function.params.size} params, ${function.blocks.size} blocks, ${function.blocks.sumOf { it.instructions.size }} instructions\n")
                writer.write("Params: ${function.params.map { "${it.name}: ${it.type}" }}\n\n")
                for (block in function.blocks) {
                    writer.write("${block.label}: (${block.instructions.size} instructions)\n")
                    for (instruction in block.instructions) {
                        writer.write("  $instruction\n")
                    }
                }
            }
            println("func_34 IR dumped to build/func_34_ir.txt (${function.blocks.size} blocks, ${function.blocks.sumOf { it.instructions.size }} instructions)")
        }

    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    @org.junit.jupiter.api.Disabled("Crashes JVM — use wark debug CLI")
    fun doomJitExecution() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        val wadPath = Path.of("examples/assets/doom1.wad")
        assumeTrue(Files.exists(wasmPath))
        assumeTrue(Files.exists(wadPath))

        val wasmBytes = Files.readAllBytes(wasmPath)
        val wadBytes = Files.readAllBytes(wadPath)

        val traceFile = java.io.File("build/doom-trace.log")
        traceFile.writeText("")

        System.setProperty("kgen.alloc.dump.function", "func_18")
        val instance = createDoomInstance(wasmBytes, wadBytes)
        val callLog = java.io.RandomAccessFile("build/doom-calls.log", "rw")
        callLog.setLength(0)
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        instance.enableTracing { funcId, name ->
            val count = callCount.incrementAndGet()
            val line = "$count: func_$funcId\n"
            callLog.writeBytes(line)
        }
        instance.compiledIr()
        traceFile.appendText("Compiled\n")
        WarkInstance.lastTrapInstance = instance

        try {
            instance.call("initGame")
            traceFile.appendText("initGame completed!\n")
        } catch (exception: Exception) {
            traceFile.appendText("ERROR: ${exception.javaClass.simpleName}: ${exception.message}\n")
        }

        if (traceFile.exists()) {
            traceFile.readLines().takeLast(50).forEach { println(it) }
        }
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @org.junit.jupiter.api.Disabled("Diagnostic test — not needed for normal runs")
    fun doomGlobalReadWriteViaJit() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        assumeTrue(Files.exists(wasmPath))
        val wasmBytes = Files.readAllBytes(wasmPath)

        // Use interpreter to read global_0
        val interpInst = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET).load(wasmBytes).instantiate(
            WarkImports.builder()
                .function("loading", "onGameInit") { i, a -> longArrayOf() }
                .function("loading", "wadSizes") { i, a -> longArrayOf(0) }
                .function("loading", "readWads") { i, a -> longArrayOf() }
                .function("runtimeControl", "timeInMilliseconds") { i, a -> longArrayOf(0) }
                .function("ui", "drawFrame") { i, a -> longArrayOf() }
                .function("gameSaving", "sizeOfSaveGame") { i, a -> longArrayOf(0) }
                .function("gameSaving", "readSaveGame") { i, a -> longArrayOf(0) }
                .function("gameSaving", "writeSaveGame") { i, a -> longArrayOf(0) }
                .function("console", "onInfoMessage") { i, a -> longArrayOf() }
                .function("console", "onErrorMessage") { i, a -> longArrayOf() }
                .build()
        )
        println("Interpreter global_0: ${interpInst.global(0).rawValue()}")

        // Interpreter: call func_817 which reads global_1, checks mem[offset], possibly writes 1
        // func_817 reads global_1 (=0), adds 0x4278c0, loads mem at that offset
        // mem is initially 0 at that offset, so it traps
        interpInst.setInstructionLimit(1000)
        try { interpInst.call("initGame") } catch (e: WasmTrap) { }
        println("After interp initGame (1K limit): global_0 = ${interpInst.global(0).rawValue()}")
        println("  mem[0x4278c0] = ${interpInst.memory().readI32(0x4278c0)}")

        // JIT: compile and call initGame (will crash in malloc)
        // But first, let's just compile and check if the .data section global matches
        val jitInst = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT).load(wasmBytes).instantiate(
            WarkImports.builder()
                .function("loading", "onGameInit") { i, a -> longArrayOf() }
                .function("loading", "wadSizes") { i, a -> longArrayOf(0) }
                .function("loading", "readWads") { i, a -> longArrayOf() }
                .function("runtimeControl", "timeInMilliseconds") { i, a -> longArrayOf(0) }
                .function("ui", "drawFrame") { i, a -> longArrayOf() }
                .function("gameSaving", "sizeOfSaveGame") { i, a -> longArrayOf(0) }
                .function("gameSaving", "readSaveGame") { i, a -> longArrayOf(0) }
                .function("gameSaving", "writeSaveGame") { i, a -> longArrayOf(0) }
                .function("console", "onInfoMessage") { i, a -> longArrayOf() }
                .function("console", "onErrorMessage") { i, a -> longArrayOf() }
                .build()
        )
        jitInst.compiledIr()
        println("JIT compiled. global_0 (from WarkGlobal): ${jitInst.global(0).rawValue()}")

        // Check the .data section has the right global value
        val inspector = jitInst.inspector()
        if (inspector != null) {
            println("JIT summary:\n${inspector.dumpSummary()}")
        }
    }

    @Test
    @Timeout(30, unit = TimeUnit.SECONDS)
    @org.junit.jupiter.api.Disabled("Crash in DOOM malloc — need to debug func_123 codegen")
    fun doomInitGameWithGuardPreset() {
        val wasmPath = Path.of("examples/assets/doom.wasm")
        val wadPath = Path.of("examples/assets/doom1.wad")
        assumeTrue(Files.exists(wasmPath))
        assumeTrue(Files.exists(wadPath))

        val wasmBytes = Files.readAllBytes(wasmPath)
        val wadBytes = Files.readAllBytes(wadPath)
        val instance = createDoomInstance(wasmBytes, wadBytes)

        // Pre-set the guard flag so func_817 doesn't trap
        instance.memory().writeI32(0x4278c0, 1)

        instance.compiledIr()
        println("Compiled. Memory: ${instance.memory().pages()} pages, ${instance.memory().sizeBytes()} bytes")

        // Call initGame — should go through guard, into func_379 → func_380 → malloc
        val traceFile = java.io.File("build/doom-trace.log")
        traceFile.writeText("")
        try {
            instance.call("initGame")
            println("initGame completed!")
        } catch (exception: Exception) {
            println("initGame error: ${exception.javaClass.simpleName}: ${exception.message}")
        }
        println("After: mem[0x4278c0] = ${instance.memory().readI32(0x4278c0)}")
    }
}

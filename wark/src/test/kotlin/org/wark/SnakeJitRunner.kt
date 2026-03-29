package org.wark

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.foreign.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path

private var crashStream: PrintStream? = null
private var jitTextBase: Long = 0L
private var jitTextSize: Long = 0L

fun onSnakeCrash(exceptionPointers: Long): Int {
    val stream = crashStream ?: return 0
    try {
        val ptrs = MemorySegment.ofAddress(exceptionPointers).reinterpret(16)
        val exRecAddr = ptrs.get(ValueLayout.JAVA_LONG, 0)
        val ctxAddr = ptrs.get(ValueLayout.JAVA_LONG, 8)
        val exRec = MemorySegment.ofAddress(exRecAddr).reinterpret(0x20)
        val code = exRec.get(ValueLayout.JAVA_INT, 0)
        if (code != 0xC0000005.toInt()) { return 0 }

        val ctx = MemorySegment.ofAddress(ctxAddr).reinterpret(0x100)
        val rip = ctx.get(ValueLayout.JAVA_LONG, 0xF8)

        // Skip JVM internal addresses (DLLs in high memory)
        if (rip ushr 40 != 0L) {
            return 0
        }

        val offset = rip - jitTextBase
        val rax = ctx.get(ValueLayout.JAVA_LONG, 0x78)
        val rbx = ctx.get(ValueLayout.JAVA_LONG, 0x90)
        val rcx = ctx.get(ValueLayout.JAVA_LONG, 0x80)
        val rdx = ctx.get(ValueLayout.JAVA_LONG, 0x88)
        val rsp = ctx.get(ValueLayout.JAVA_LONG, 0x98)
        val rbp = ctx.get(ValueLayout.JAVA_LONG, 0xA0)
        val rsi = ctx.get(ValueLayout.JAVA_LONG, 0xA8)
        val rdi = ctx.get(ValueLayout.JAVA_LONG, 0xB0)
        val r8 = ctx.get(ValueLayout.JAVA_LONG, 0xB8)
        val r9 = ctx.get(ValueLayout.JAVA_LONG, 0xC0)
        val r10 = ctx.get(ValueLayout.JAVA_LONG, 0xC8)
        val r11 = ctx.get(ValueLayout.JAVA_LONG, 0xD0)
        val r12 = ctx.get(ValueLayout.JAVA_LONG, 0xD8)
        val r13 = ctx.get(ValueLayout.JAVA_LONG, 0xE0)
        val r14 = ctx.get(ValueLayout.JAVA_LONG, 0xE8)
        val r15 = ctx.get(ValueLayout.JAVA_LONG, 0xF0)

        stream.println("CRASH at .text+0x${offset.toULong().toString(16)}")
        stream.println("  RIP=0x${rip.toULong().toString(16)}")
        stream.println("  RAX=0x${rax.toULong().toString(16)} RBX=0x${rbx.toULong().toString(16)}")
        stream.println("  RCX=0x${rcx.toULong().toString(16)} RDX=0x${rdx.toULong().toString(16)}")
        stream.println("  RSP=0x${rsp.toULong().toString(16)} RBP=0x${rbp.toULong().toString(16)}")
        stream.println("  RSI=0x${rsi.toULong().toString(16)} RDI=0x${rdi.toULong().toString(16)}")
        stream.println("  R8=0x${r8.toULong().toString(16)}  R9=0x${r9.toULong().toString(16)}")
        stream.println("  R10=0x${r10.toULong().toString(16)} R11=0x${r11.toULong().toString(16)}")
        stream.println("  R12=0x${r12.toULong().toString(16)} R13=0x${r13.toULong().toString(16)}")
        stream.println("  R14=0x${r14.toULong().toString(16)} R15=0x${r15.toULong().toString(16)}")

        // Dump stack (return addresses)
        stream.println("  Stack trace:")
        val stackSeg = MemorySegment.ofAddress(rsp).reinterpret(256)
        for (stackIdx in 0 until 16) {
            val stackVal = stackSeg.get(ValueLayout.JAVA_LONG, stackIdx.toLong() * 8)
            val stackOff = stackVal - jitTextBase
            val inJit = stackVal >= jitTextBase && stackVal < jitTextBase + jitTextSize
            if (inJit) {
                stream.println("    [RSP+${stackIdx * 8}] = .text+0x${stackOff.toULong().toString(16)}")
            }
        }

        // Dump RBP chain
        stream.println("  Frame chain:")
        var framePtr = rbp
        for (depth in 0 until 5) {
            try {
                val frameSeg = MemorySegment.ofAddress(framePtr).reinterpret(16)
                val savedRbp = frameSeg.get(ValueLayout.JAVA_LONG, 0)
                val returnAddr = frameSeg.get(ValueLayout.JAVA_LONG, 8)
                val retOff = returnAddr - jitTextBase
                val inJit = returnAddr >= jitTextBase && returnAddr < jitTextBase + jitTextSize
                if (inJit) {
                    stream.println("    frame[$depth]: .text+0x${retOff.toULong().toString(16)}")
                } else {
                    stream.println("    frame[$depth]: 0x${returnAddr.toULong().toString(16)} (outside JIT)")
                    break
                }
                framePtr = savedRbp
            } catch (e: Throwable) {
                break
            }
        }

        stream.flush()
    } catch (ignored: Throwable) { }
    return 0
}

fun main() {
    val snakePath = Path.of("examples/assets/wasm4/snake.wasm")
    if (!Files.exists(snakePath)) {
        System.err.println("snake.wasm not found at $snakePath")
        return
    }

    val traceFile = File("build/snake-jit-crash.txt")
    traceFile.parentFile.mkdirs()
    val traceStream = PrintStream(FileOutputStream(traceFile), true)
    crashStream = traceStream

    // Install VEH crash handler
    try {
        val linker = java.lang.foreign.Linker.nativeLinker()
        val lookup = MethodHandles.lookup()
        val handle = lookup.findStatic(
            Class.forName("org.wark.SnakeJitRunnerKt"),
            "onSnakeCrash",
            MethodType.methodType(Int::class.java, Long::class.javaPrimitiveType)
        )
        val descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
        val arena = Arena.ofAuto()
        val stub = linker.upcallStub(handle, descriptor, arena)
        val kernel32 = SymbolLookup.libraryLookup("kernel32", arena)
        val addVeh = linker.downcallHandle(
            kernel32.find("AddVectoredExceptionHandler").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
        )
        addVeh.invoke(1, stub)
        traceStream.println("VEH crash handler installed")
    } catch (e: Throwable) {
        traceStream.println("VEH install failed: ${e.message}")
    }

    val wasmBytes = Files.readAllBytes(snakePath)
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

    // Check what the snake actually imports
    val wasmModule = org.kgen.target.wasm.module.WasmModuleReader.read(wasmBytes)
    traceStream.println("Snake imports:")
    for (imp in wasmModule.imports) {
        traceStream.println("  $imp")
    }

    traceStream.println("Loading snake.wasm...")
    val instance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(imports)

    // Enable tracing BEFORE inspector (which triggers compilation)
    val jitCalls = mutableListOf<String>()
    instance.enableTracing { funcId, message ->
        if (!message.contains("<<<") && jitCalls.size < 100) {
            jitCalls.add(message.trim())
        }
    }

    // Get text base address
    val inspector = instance.inspector()
    if (inspector != null) {
        traceStream.println(inspector.dumpSummary())
        // Get the text section base from the JIT engine
        // Get text base from disassembly
        val updateAsm = inspector.dumpAsm("update")
        traceStream.println("update: ${updateAsm.lines().size} lines")
    }

    // Dump func_14 disassembly
    if (inspector != null) {
        val func14Asm = inspector.dumpAsm("func_14")
        val func14File = File("build/snake_func14_disasm.txt")
        func14File.writeText(func14Asm)
        traceStream.println("func_14 disassembly: ${func14Asm.lines().size} lines → ${func14File.absolutePath}")

        // Extract text base from first instruction address
        val firstLine = func14Asm.lines().firstOrNull { it.contains(":") }
        if (firstLine != null) {
            val addrMatch = Regex("([0-9a-fA-F]{8,16}):").find(firstLine)
            if (addrMatch != null) {
                val addr = java.lang.Long.parseUnsignedLong(addrMatch.groupValues[1], 16)
                // The text base is before this function's start
                traceStream.println("func_14 starts at 0x${addr.toULong().toString(16)}")
            }
        }
    }

    traceStream.println("Exports: ${instance.exportedFunctions()}")

    // Dump function prologues (sub rsp sizes) to identify crashing function
    if (inspector != null) {
        traceStream.println("\nFunction stack sizes:")
        val impCount = wasmModule.importedFunctionCount
        for ((localIdx, _) in wasmModule.functions.withIndex()) {
            val name = wasmModule.functionName(impCount + localIdx) ?: "func_$localIdx"
            val funcAsm = inspector.dumpAsm(name)
            val subRsp = Regex("sub rsp, 0x([0-9a-fA-F]+)").find(funcAsm)
            val size = if (subRsp != null) { "sub rsp 0x${subRsp.groupValues[1]}" } else { "no sub rsp" }
            val bytes = Regex("\\(([0-9]+) bytes\\)").find(funcAsm)?.groupValues?.get(1) ?: "?"
            traceStream.println("  $name: $bytes bytes, $size")
        }
    }

    // Run interpreter to get expected function calls
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
    val interpreter = interpInstance.interpreter()

    val importCount = wasmModule.importedFunctionCount
    val interpCallArgs = mutableListOf<Pair<String, List<Long>>>()
    interpreter.onFunctionEntry = { funcIndex, args ->
        if (funcIndex >= importCount && interpCallArgs.size < 200) {
            interpCallArgs.add("func_${funcIndex - importCount}" to args.map { it.toInt().toLong() })
        }
    }
    interpInstance.call("update")
    traceStream.println("Interpreter: ${interpCallArgs.size} WASM calls")
    for ((idx, pair) in interpCallArgs.withIndex()) {
        traceStream.println("  interp #$idx: ${pair.first}(${pair.second.map { "0x${it.toString(16)}" }})")
    }

    traceStream.println("\nCalling JIT update()...")
    traceStream.flush()
    try {
        instance.call("update")
        traceStream.println("update() OK!")
    } catch (e: WasmTrap) {
        traceStream.println("TRAP: ${e.message}")
        traceStream.println("JIT calls (${jitCalls.size}):")
        for ((idx, call) in jitCalls.withIndex()) {
            traceStream.println("  jit #$idx: $call")
        }
        // Compare with interpreter
        val interpFuncs = interpCallArgs.map { it.first }
        val minCalls = minOf(interpFuncs.size, jitCalls.size)
        for (idx in 0 until minCalls) {
            if (interpFuncs[idx] != jitCalls[idx]) {
                traceStream.println("FIRST DIVERGENCE at call #$idx: interp=${interpFuncs[idx]}, jit=${jitCalls[idx]}")
                break
            }
        }
        if (minCalls > 0 && (0 until minCalls).all { interpFuncs[it] == jitCalls[it] }) {
            traceStream.println("First $minCalls calls match")
        }
    } catch (e: Exception) {
        traceStream.println("ERROR: ${e.javaClass.simpleName}: ${e.message}")
    }
    traceStream.flush()

    println("Results: ${traceFile.absolutePath}")
    for (line in traceFile.readLines()) {
        println(line)
    }
}

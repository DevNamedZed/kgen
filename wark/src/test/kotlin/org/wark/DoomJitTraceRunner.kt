package org.wark

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.foreign.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path

var textBase: Long = 0L
private var crashStream: PrintStream? = null

fun onException(exceptionPointers: Long): Int {
    val stream = crashStream ?: return 0
    try {
        val ptrs = MemorySegment.ofAddress(exceptionPointers).reinterpret(16)
        val exRecAddr = ptrs.get(ValueLayout.JAVA_LONG, 0)
        val ctxAddr = ptrs.get(ValueLayout.JAVA_LONG, 8)

        val exRec = MemorySegment.ofAddress(exRecAddr).reinterpret(0x20)
        val code = exRec.get(ValueLayout.JAVA_INT, 0)

        if (code != 0xC0000005.toInt()) {
            return 0
        }

        val ctx = MemorySegment.ofAddress(ctxAddr).reinterpret(0x100)
        val rip = ctx.get(ValueLayout.JAVA_LONG, 0xF8)

        // Skip JVM's internal AVs — they have high RIP addresses (in DLL range)
        if (rip ushr 40 != 0L) {
            return 0
        }

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

        val offset = rip - textBase
        stream.println("CRASH! AV code=0xc0000005")
        stream.println("  RIP=0x${rip.toULong().toString(16)} (.text+0x${offset.toULong().toString(16)})")
        stream.println("  RAX=0x${rax.toULong().toString(16)} RBX=0x${rbx.toULong().toString(16)}")
        stream.println("  RCX=0x${rcx.toULong().toString(16)} RDX=0x${rdx.toULong().toString(16)}")
        stream.println("  RSP=0x${rsp.toULong().toString(16)} RBP=0x${rbp.toULong().toString(16)}")
        stream.println("  RSI=0x${rsi.toULong().toString(16)} RDI=0x${rdi.toULong().toString(16)}")
        stream.println("  R8=0x${r8.toULong().toString(16)}  R9=0x${r9.toULong().toString(16)}")
        stream.println("  R10=0x${r10.toULong().toString(16)} R11=0x${r11.toULong().toString(16)}")
        stream.println("  R12=0x${r12.toULong().toString(16)} R13=0x${r13.toULong().toString(16)}")
        stream.println("  R14=0x${r14.toULong().toString(16)} R15=0x${r15.toULong().toString(16)}")
        stream.println("  TEXT_BASE=0x${textBase.toULong().toString(16)}")
        stream.flush()
    } catch (ignored: Throwable) {
        // handler must not throw
    }
    return 0 // EXCEPTION_CONTINUE_SEARCH
}

fun installCrashHandler(stream: PrintStream) {
    crashStream = stream
    try {
        val linker = Linker.nativeLinker()
        val lookup = MethodHandles.lookup()
        val handle = lookup.findStatic(
            Class.forName("org.wark.DoomJitTraceRunnerKt"),
            "onException",
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
        stream.println("VEH crash handler installed")
    } catch (exception: Throwable) {
        stream.println("VEH install failed: ${exception.message}")
    }
}

fun main() {
    val doomPath = Path.of("examples/assets/doom.wasm")
    if (!Files.exists(doomPath)) {
        System.err.println("doom.wasm not found at $doomPath")
        return
    }

    val traceFile = File("build/doom-jit-trace.txt")
    traceFile.parentFile.mkdirs()
    val traceStream = PrintStream(FileOutputStream(traceFile), true)

    // installCrashHandler(traceStream)

    val wasmBytes = Files.readAllBytes(doomPath)
    val imports = WarkImports.builder()
        .function("loading", "onGameInit") { _, args ->
            traceStream.println("HOST: onGameInit(${args[0]}, ${args[1]})")
            longArrayOf()
        }
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

    traceStream.println("Creating JIT instance...")
    val instance = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT)
        .load(wasmBytes).instantiate(imports)

    // Tracing disabled — generates GB+ of output for DOOM
    // instance.enableTracing { funcId, message -> traceStream.println(message) }
    // instance.enableBoundsChecking()

    traceStream.println("Calling initGame...")
    traceStream.flush()
    try {
        instance.call("initGame")
        traceStream.println("initGame completed!")
    } catch (exception: Exception) {
        traceStream.println("ERROR: ${exception.javaClass.simpleName}: ${exception.message}")
    }
    traceStream.flush()
}

package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

class QuakeDebugTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    @Test
    @EnabledIf("quakeWasmExists")
    fun tracePathOpenCalls() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder()
            .directory(gameDirectory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)

        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        // Override path_open with logging
        builder.function("wasi_snapshot_preview1", "path_open",
            org.wark.HostFunction { inst, pathArgs ->
                val dirFd = pathArgs[0].toInt()
                val pathAddress = pathArgs[2].toInt()
                val pathLength = pathArgs[3].toInt()
                val openFlags = pathArgs[4].toInt()
                val filePath = String(inst.memory().readBytes(pathAddress, pathLength), Charsets.UTF_8)

                val openResult = wasi.fileTable().openFile(dirFd, filePath, openFlags)
                when (openResult) {
                    is org.wark.wasi.WasiFileTable.OpenResult.Success -> {
                        println("[PATH_OPEN] \"$filePath\" flags=$openFlags → fd=${openResult.descriptor}")
                        inst.memory().writeI32(pathArgs[8].toInt(), openResult.descriptor)
                        longArrayOf(0L)
                    }
                    is org.wark.wasi.WasiFileTable.OpenResult.Error -> {
                        println("[PATH_OPEN] \"$filePath\" flags=$openFlags → ERRNO=${openResult.errno}")
                        longArrayOf(openResult.errno.toLong())
                    }
                }
            })

        // Override fd_write with logging for non-stdout/stderr
        builder.function("wasi_snapshot_preview1", "fd_write",
            org.wark.HostFunction { inst, writeArgs ->
                val fd = writeArgs[0].toInt()
                val iovecAddr = writeArgs[1].toInt()
                val iovecCount = writeArgs[2].toInt()
                val nwrittenAddr = writeArgs[3].toInt()
                val memory = inst.memory()

                var totalWritten = 0
                for (i in 0 until iovecCount) {
                    val bufAddr = memory.readI32(iovecAddr + i * 8)
                    val bufLen = memory.readI32(iovecAddr + i * 8 + 4)
                    if (bufLen > 0) {
                        val bytes = memory.readBytes(bufAddr, bufLen)
                        val written = wasi.fileTable().write(fd, bytes)
                        if (written < 0) {
                            println("[FD_WRITE] fd=$fd len=$bufLen → FAILED")
                            memory.writeI32(nwrittenAddr, totalWritten)
                            return@HostFunction longArrayOf(8L) // BADF
                        }
                        totalWritten += written
                    }
                }
                if (fd > 2) {
                    println("[FD_WRITE] fd=$fd wrote=$totalWritten bytes")
                }
                memory.writeI32(nwrittenAddr, totalWritten)
                longArrayOf(0L)
            })

        val instance = module.instantiate(builder.build())
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)

        instance.call("_initialize")
        val importCount = module.wasmModule.importedFunctionCount
        for ((li, _) in module.wasmModule.functions.withIndex()) {
            val n = module.wasmModule.functionName(li + importCount)
            if (n == "__wasilibc_populate_preopens") {
                instance.callByIndex(li + importCount)
                break
            }
        }

        println("=== Running q_init ===")
        instance.call("q_init", 32L)
        println("=== Init complete ===")

        println("=== Running frames (INTERPRET) ===")
        var lastTime = System.nanoTime()
        for (frame in 1..200) {
            try {
                val bits = java.lang.Float.floatToRawIntBits(1.0f / 30.0f)
                instance.call("q_frame", bits.toLong())
            } catch (trap: org.wark.WasmTrap) {
                println("Frame $frame trap: ${trap.message}")
            }
        }
        println("=== Done ===")

        wasi.fileTable().closeAll()
    }
}

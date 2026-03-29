package org.wark.wasi

import org.wark.HostFunction
import org.wark.WarkImports
import java.io.OutputStream
import java.nio.file.Path

/**
 * WASI Preview 1 implementation. Provides the complete set of WASI syscalls
 * that C/Rust/Go compiled WASM modules expect.
 *
 * ```java
 * var imports = WasiPreview1.builder()
 *     .stdout(System.out)
 *     .stderr(System.err)
 *     .args("program", "--verbose")
 *     .directory(Path.of("."))
 *     .build();
 *
 * var instance = module.instantiate(imports);
 * instance.call("_start");
 * ```
 */
class WasiPreview1 private constructor(
    private val stdout: OutputStream,
    private val stderr: OutputStream,
    private val args: List<String>,
    private val env: Map<String, String>,
    private val fileTable: WasiFileTable,
    private val fileOperations: WasiFileOperations,
) {

    fun registerImports(builder: WarkImports.Builder): WarkImports.Builder {
        fileOperations.registerImports(builder)

        builder.function("wasi_snapshot_preview1", "args_sizes_get", argsSizesGet())
        builder.function("wasi_snapshot_preview1", "args_get", argsGet())
        builder.function("wasi_snapshot_preview1", "environ_sizes_get", environSizesGet())
        builder.function("wasi_snapshot_preview1", "environ_get", environGet())
        builder.function("wasi_snapshot_preview1", "clock_time_get", clockTimeGet())
        builder.function("wasi_snapshot_preview1", "clock_res_get", clockResGet())
        builder.function("wasi_snapshot_preview1", "proc_exit", procExit())
        builder.function("wasi_snapshot_preview1", "proc_raise", procRaise())
        builder.function("wasi_snapshot_preview1", "sched_yield", schedYield())
        builder.function("wasi_snapshot_preview1", "random_get", randomGet())
        builder.function("wasi_snapshot_preview1", "poll_oneoff", pollOneoff())
        builder.function("wasi_snapshot_preview1", "sock_recv", sockRecv())
        builder.function("wasi_snapshot_preview1", "sock_send", sockSend())
        builder.function("wasi_snapshot_preview1", "sock_shutdown", sockShutdown())
        return builder
    }

    fun buildImports(): WarkImports = registerImports(WarkImports.builder()).build()

    fun fileTable(): WasiFileTable = fileTable

    private fun argsSizesGet(): HostFunction = HostFunction { instance, args ->
        val argCountAddress = args[0].toInt()
        val argSizeAddress = args[1].toInt()
        val memory = instance.memory()

        memory.writeI32(argCountAddress, this.args.size)
        val totalSize = this.args.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
        memory.writeI32(argSizeAddress, totalSize)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun argsGet(): HostFunction = HostFunction { instance, args ->
        val argvAddress = args[0].toInt()
        val argvBufferAddress = args[1].toInt()
        val memory = instance.memory()

        var bufferOffset = argvBufferAddress
        for ((index, arg) in this.args.withIndex()) {
            memory.writeI32(argvAddress + index * 4, bufferOffset)
            val bytes = arg.toByteArray(Charsets.UTF_8)
            memory.writeBytes(bufferOffset, bytes)
            memory.writeByte(bufferOffset + bytes.size, 0)
            bufferOffset += bytes.size + 1
        }
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun environSizesGet(): HostFunction = HostFunction { instance, args ->
        val envCountAddress = args[0].toInt()
        val envSizeAddress = args[1].toInt()
        val memory = instance.memory()

        memory.writeI32(envCountAddress, env.size)
        val totalSize = env.entries.sumOf {
            "${it.key}=${it.value}".toByteArray(Charsets.UTF_8).size + 1
        }
        memory.writeI32(envSizeAddress, totalSize)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun environGet(): HostFunction = HostFunction { instance, args ->
        val envpAddress = args[0].toInt()
        val envBufferAddress = args[1].toInt()
        val memory = instance.memory()

        var bufferOffset = envBufferAddress
        for ((index, entry) in env.entries.withIndex()) {
            memory.writeI32(envpAddress + index * 4, bufferOffset)
            val bytes = "${entry.key}=${entry.value}".toByteArray(Charsets.UTF_8)
            memory.writeBytes(bufferOffset, bytes)
            memory.writeByte(bufferOffset + bytes.size, 0)
            bufferOffset += bytes.size + 1
        }
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun clockTimeGet(): HostFunction = HostFunction { instance, args ->
        val clockId = args[0].toInt()
        val resultAddress = args[2].toInt()
        val memory = instance.memory()

        val nanos = when (clockId) {
            WasiErrno.CLOCK_REALTIME -> System.currentTimeMillis() * 1_000_000L
            WasiErrno.CLOCK_MONOTONIC -> System.nanoTime()
            else -> 0L
        }
        memory.writeI64(resultAddress, nanos)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun clockResGet(): HostFunction = HostFunction { instance, args ->
        val clockId = args[0].toInt()
        val resultAddress = args[1].toInt()
        val memory = instance.memory()

        val resolution = when (clockId) {
            WasiErrno.CLOCK_REALTIME -> 1_000_000L
            WasiErrno.CLOCK_MONOTONIC -> 1L
            else -> 1_000_000L
        }
        memory.writeI64(resultAddress, resolution)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun procExit(): HostFunction = HostFunction { _, args ->
        throw WasiExitException(args[0].toInt())
    }

    private fun procRaise(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOSYS.toLong())
    }

    private fun schedYield(): HostFunction = HostFunction { _, _ ->
        Thread.yield()
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun randomGet(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val bufferLength = args[1].toInt()
        val memory = instance.memory()

        val random = java.security.SecureRandom()
        val bytes = ByteArray(bufferLength)
        random.nextBytes(bytes)
        memory.writeBytes(bufferAddress, bytes)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun pollOneoff(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOSYS.toLong())
    }

    private fun sockRecv(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOSYS.toLong())
    }

    private fun sockSend(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOSYS.toLong())
    }

    private fun sockShutdown(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOSYS.toLong())
    }

    class Builder {
        private var stdout: OutputStream = System.out
        private var stderr: OutputStream = System.err
        private var args: List<String> = emptyList()
        private var env: Map<String, String> = emptyMap()
        private val directories = mutableListOf<Pair<Path, String>>()

        fun stdout(stream: OutputStream): Builder { this.stdout = stream; return this }
        fun stderr(stream: OutputStream): Builder { this.stderr = stream; return this }
        fun args(vararg arguments: String): Builder { this.args = arguments.toList(); return this }
        fun args(arguments: List<String>): Builder { this.args = arguments; return this }
        fun env(environment: Map<String, String>): Builder { this.env = environment; return this }

        fun directory(hostPath: Path, mountPath: String = "."): Builder {
            directories.add(hostPath to mountPath)
            return this
        }

        fun build(): WasiPreview1 {
            val fileTable = WasiFileTable(stdout, stderr)
            for ((hostPath, mountPath) in directories) {
                fileTable.addPreopenedDirectory(hostPath, mountPath)
            }
            val fileOperations = WasiFileOperations(fileTable)
            return WasiPreview1(stdout, stderr, args, env, fileTable, fileOperations)
        }
    }

    companion object {
        @JvmStatic fun builder(): Builder = Builder()
    }
}

class WasiExitException(val exitCode: Int) : RuntimeException("WASI proc_exit($exitCode)")

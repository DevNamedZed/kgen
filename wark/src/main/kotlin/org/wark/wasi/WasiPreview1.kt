package org.wark.wasi

import org.wark.HostFunction
import org.wark.WarkImports
import java.io.OutputStream

/**
 * WASI Preview 1 implementation. Provides the standard WASI syscalls that
 * C/Rust/Go compiled WASM modules expect.
 *
 * ```java
 * var imports = WasiPreview1.builder()
 *     .stdout(System.out)
 *     .stderr(System.err)
 *     .args("program", "--verbose")
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
) {

    fun registerImports(builder: WarkImports.Builder): WarkImports.Builder {
        builder.function("wasi_snapshot_preview1", "fd_write", fdWrite())
        builder.function("wasi_snapshot_preview1", "fd_read", fdRead())
        builder.function("wasi_snapshot_preview1", "fd_close", fdClose())
        builder.function("wasi_snapshot_preview1", "fd_seek", fdSeek())
        builder.function("wasi_snapshot_preview1", "fd_prestat_get", fdPrestatGet())
        builder.function("wasi_snapshot_preview1", "fd_prestat_dir_name", fdPrestatDirName())
        builder.function("wasi_snapshot_preview1", "args_sizes_get", argsSizesGet())
        builder.function("wasi_snapshot_preview1", "args_get", argsGet())
        builder.function("wasi_snapshot_preview1", "environ_sizes_get", environSizesGet())
        builder.function("wasi_snapshot_preview1", "environ_get", environGet())
        builder.function("wasi_snapshot_preview1", "clock_time_get", clockTimeGet())
        builder.function("wasi_snapshot_preview1", "proc_exit", procExit())
        builder.function("wasi_snapshot_preview1", "random_get", randomGet())
        builder.function("wasi_snapshot_preview1", "path_open", pathOpen())
        return builder
    }

    fun buildImports(): WarkImports = registerImports(WarkImports.builder()).build()

    private fun fdWrite(): HostFunction = HostFunction { instance, args ->
        val fileDescriptor = args[0].toInt()
        val iovecAddress = args[1].toInt()
        val iovecCount = args[2].toInt()
        val bytesWrittenAddress = args[3].toInt()

        val memory = instance.memory()
        var totalWritten = 0

        val outputStream = when (fileDescriptor) {
            1 -> stdout
            2 -> stderr
            else -> null
        }

        for (index in 0 until iovecCount) {
            val iovecOffset = iovecAddress + index * 8
            val bufferAddress = memory.readI32(iovecOffset)
            val bufferLength = memory.readI32(iovecOffset + 4)

            if (outputStream != null && bufferLength > 0) {
                val bytes = memory.readBytes(bufferAddress, bufferLength)
                outputStream.write(bytes)
                totalWritten += bufferLength
            }
        }

        outputStream?.flush()
        memory.writeI32(bytesWrittenAddress, totalWritten)
        longArrayOf(0)
    }

    private fun fdRead(): HostFunction = HostFunction { instance, args ->
        longArrayOf(ERRNO_BADF.toLong())
    }

    private fun fdClose(): HostFunction = HostFunction { instance, args ->
        longArrayOf(0)
    }

    private fun fdSeek(): HostFunction = HostFunction { instance, args ->
        longArrayOf(ERRNO_BADF.toLong())
    }

    private fun fdPrestatGet(): HostFunction = HostFunction { instance, args ->
        longArrayOf(ERRNO_BADF.toLong())
    }

    private fun fdPrestatDirName(): HostFunction = HostFunction { instance, args ->
        longArrayOf(ERRNO_BADF.toLong())
    }

    private fun argsSizesGet(): HostFunction = HostFunction { instance, args ->
        val argCountAddress = args[0].toInt()
        val argSizeAddress = args[1].toInt()
        val memory = instance.memory()

        memory.writeI32(argCountAddress, this.args.size)
        val totalSize = this.args.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }
        memory.writeI32(argSizeAddress, totalSize)
        longArrayOf(0)
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
        longArrayOf(0)
    }

    private fun environSizesGet(): HostFunction = HostFunction { instance, args ->
        val envCountAddress = args[0].toInt()
        val envSizeAddress = args[1].toInt()
        val memory = instance.memory()

        memory.writeI32(envCountAddress, env.size)
        val totalSize = env.entries.sumOf { "${it.key}=${it.value}".toByteArray(Charsets.UTF_8).size + 1 }
        memory.writeI32(envSizeAddress, totalSize)
        longArrayOf(0)
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
        longArrayOf(0)
    }

    private fun clockTimeGet(): HostFunction = HostFunction { instance, args ->
        val clockId = args[0].toInt()
        val resultAddress = args[2].toInt()
        val memory = instance.memory()

        val nanos = when (clockId) {
            CLOCK_REALTIME -> System.currentTimeMillis() * 1_000_000
            CLOCK_MONOTONIC -> System.nanoTime()
            else -> 0L
        }
        memory.writeI64(resultAddress, nanos)
        longArrayOf(0)
    }

    private fun procExit(): HostFunction = HostFunction { instance, args ->
        throw WasiExitException(args[0].toInt())
    }

    private fun randomGet(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val bufferLength = args[1].toInt()
        val memory = instance.memory()

        val random = java.security.SecureRandom()
        val bytes = ByteArray(bufferLength)
        random.nextBytes(bytes)
        memory.writeBytes(bufferAddress, bytes)
        longArrayOf(0)
    }

    private fun pathOpen(): HostFunction = HostFunction { instance, args ->
        longArrayOf(ERRNO_NOENT.toLong())
    }

    class Builder {
        private var stdout: OutputStream = System.out
        private var stderr: OutputStream = System.err
        private var args: List<String> = emptyList()
        private var env: Map<String, String> = emptyMap()

        fun stdout(stream: OutputStream): Builder { this.stdout = stream; return this }
        fun stderr(stream: OutputStream): Builder { this.stderr = stream; return this }
        fun args(vararg arguments: String): Builder { this.args = arguments.toList(); return this }
        fun args(arguments: List<String>): Builder { this.args = arguments; return this }
        fun env(environment: Map<String, String>): Builder { this.env = environment; return this }

        fun build(): WasiPreview1 = WasiPreview1(stdout, stderr, args, env)
    }

    companion object {
        @JvmStatic fun builder(): Builder = Builder()

        private const val ERRNO_BADF = 8
        private const val ERRNO_NOENT = 44
        private const val CLOCK_REALTIME = 0
        private const val CLOCK_MONOTONIC = 1
    }
}

class WasiExitException(val exitCode: Int) : RuntimeException("WASI proc_exit($exitCode)")

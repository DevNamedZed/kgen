package org.wark

import org.wark.wasi.WasiExitException
import org.wark.wasi.WasiPreview1
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * High-level API for running WASM programs. Handles WASI setup, module loading,
 * and execution in one call.
 *
 * ```java
 * // Run a WASI program
 * int exitCode = WarkRunner.run(Path.of("hello.wasm"));
 *
 * // With options
 * int exitCode = WarkRunner.builder()
 *     .file(Path.of("program.wasm"))
 *     .args("program", "--verbose")
 *     .env("HOME", "/tmp")
 *     .stdout(myOutputStream)
 *     .target(WasmTarget.V2_0)
 *     .mode(ExecutionMode.INTERPRET)
 *     .build()
 *     .run();
 * ```
 */
class WarkRunner private constructor(
    private val wasmBytes: ByteArray,
    private val target: WasmTarget,
    private val mode: ExecutionMode,
    private val wasi: WasiPreview1,
    private val additionalImports: WarkImports,
    private val entryPoint: String,
) {
    /**
     * Run the WASM program. Returns the exit code (0 for success).
     */
    fun run(): Int {
        val runtime = WarkRuntime.create(target, mode)
        val module = runtime.load(wasmBytes)

        val importsBuilder = WarkImports.builder()
        wasi.registerImports(importsBuilder)

        val instance = module.instantiate(importsBuilder.build())

        return try {
            instance.call(entryPoint)
            0
        } catch (exit: WasiExitException) {
            exit.exitCode
        }
    }

    class Builder {
        private var wasmBytes: ByteArray? = null
        private var target: WasmTarget = WasmTarget.V2_0
        private var mode: ExecutionMode = ExecutionMode.INTERPRET
        private var stdout: OutputStream = System.out
        private var stderr: OutputStream = System.err
        private var args: List<String> = emptyList()
        private var env: MutableMap<String, String> = mutableMapOf()
        private var entryPoint: String = "_start"
        private var additionalImports: WarkImports = WarkImports.empty()

        fun file(path: Path): Builder { this.wasmBytes = Files.readAllBytes(path); return this }
        fun bytes(bytes: ByteArray): Builder { this.wasmBytes = bytes; return this }
        fun target(target: WasmTarget): Builder { this.target = target; return this }
        fun mode(mode: ExecutionMode): Builder { this.mode = mode; return this }
        fun stdout(stream: OutputStream): Builder { this.stdout = stream; return this }
        fun stderr(stream: OutputStream): Builder { this.stderr = stream; return this }
        fun args(vararg arguments: String): Builder { this.args = arguments.toList(); return this }
        fun args(arguments: List<String>): Builder { this.args = arguments; return this }
        fun env(key: String, value: String): Builder { this.env[key] = value; return this }
        fun env(environment: Map<String, String>): Builder { this.env.putAll(environment); return this }
        fun entryPoint(name: String): Builder { this.entryPoint = name; return this }
        fun imports(imports: WarkImports): Builder { this.additionalImports = imports; return this }

        fun build(): WarkRunner {
            val bytes = wasmBytes ?: throw IllegalStateException("No WASM file specified")
            val wasi = WasiPreview1.builder()
                .stdout(stdout)
                .stderr(stderr)
                .args(args)
                .env(env)
                .build()
            return WarkRunner(bytes, target, mode, wasi, additionalImports, entryPoint)
        }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        /**
         * Run a WASM file with default settings (WASI, interpret mode).
         */
        @JvmStatic
        fun run(path: Path): Int = builder().file(path).build().run()

        /**
         * Run WASM bytes with default settings.
         */
        @JvmStatic
        fun run(bytes: ByteArray): Int = builder().bytes(bytes).build().run()
    }
}

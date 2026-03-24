package org.wark

import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Top-level entry point for the Wark WASM runtime.
 *
 * Creates a runtime with a specific feature set, loads WASM modules, and
 * provides the execution environment. Supports JIT, AOT, and interpreted
 * execution depending on configuration.
 *
 * ```java
 * var runtime = WarkRuntime.create(WasmTarget.V2_0);
 * var module = runtime.load(Path.of("game.wasm"));
 * var instance = module.instantiate(imports);
 * instance.call("main");
 * ```
 */
class WarkRuntime private constructor(
    val features: WasmFeatureSet,
    val executionMode: ExecutionMode = ExecutionMode.JIT,
) : AutoCloseable {

    /**
     * Load a WASM module from a file path.
     */
    fun load(path: Path): WarkModule {
        val bytes = Files.readAllBytes(path)
        return load(bytes)
    }

    /**
     * Load a WASM module from raw bytes.
     */
    fun load(bytes: ByteArray): WarkModule {
        val wasmModule = WasmModuleReader.read(bytes)
        return WarkModule(this, wasmModule, bytes)
    }

    /**
     * Load a WASM module from a pre-parsed [WasmModule].
     */
    fun load(wasmModule: WasmModule): WarkModule {
        return WarkModule(this, wasmModule, byteArrayOf())
    }

    override fun close() {
    }

    companion object {
        /**
         * Create a runtime with the features enabled by a [WasmTarget] preset.
         */
        @JvmStatic
        fun create(target: WasmTarget): WarkRuntime {
            return WarkRuntime(target.features())
        }

        @JvmStatic
        fun create(target: WasmTarget, mode: ExecutionMode): WarkRuntime {
            return WarkRuntime(target.features(), mode)
        }

        @JvmStatic
        fun create(features: WasmFeatureSet): WarkRuntime {
            return WarkRuntime(features)
        }

        /**
         * Create a runtime with all features enabled.
         */
        @JvmStatic
        fun createLatest(): WarkRuntime {
            return WarkRuntime(WasmFeatureSet.all())
        }
    }
}

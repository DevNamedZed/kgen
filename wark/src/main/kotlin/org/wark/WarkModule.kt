package org.wark

import org.kgen.target.wasm.module.WasmModule

/**
 * A loaded but not yet instantiated WASM module.
 *
 * Contains the parsed module structure and raw bytes. Call [instantiate] to
 * create an executable instance with linked imports.
 *
 * ```java
 * var module = runtime.load(wasmBytes);
 * var exports = module.exportNames();
 * var instance = module.instantiate(imports);
 * ```
 */
class WarkModule(
    val runtime: WarkRuntime,
    val wasmModule: WasmModule,
    val rawBytes: ByteArray,
) {

    /**
     * Instantiate this module with the given imports.
     * Links all imports, initializes memory and globals, runs the start function.
     */
    fun instantiate(imports: WarkImports = WarkImports.empty()): WarkInstance = WarkInstance(this, imports)

    /**
     * Names of all exported functions.
     */
    fun exportedFunctionNames(): List<String> = wasmModule.exports
        .filter { it.kind == WasmModule.ExportKind.FUNCTION }
        .map { it.name }

    /**
     * Names of all exported memories.
     */
    fun exportedMemoryNames(): List<String> = wasmModule.exports
        .filter { it.kind == WasmModule.ExportKind.MEMORY }
        .map { it.name }

    /**
     * Names of all exported globals.
     */
    fun exportedGlobalNames(): List<String> = wasmModule.exports
        .filter { it.kind == WasmModule.ExportKind.GLOBAL }
        .map { it.name }

    /**
     * Number of imported functions this module requires.
     */
    fun importedFunctionCount(): Int = wasmModule.importedFunctionCount

    /**
     * All import declarations (module + name + type).
     */
    fun imports(): List<WasmModule.Import> = wasmModule.imports
}

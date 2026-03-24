package org.wark

/**
 * How wark executes WASM code.
 *
 * ```java
 * // JIT — compile at runtime, execute in-process
 * var runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.JIT);
 *
 * // AOT — compile to native binary ahead of time
 * var runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.AOT);
 *
 * // Interpret — no compilation, direct execution (slow but portable)
 * var runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET);
 * ```
 */
enum class ExecutionMode {
    /**
     * JIT compilation. Compiles WASM to native machine code at runtime via kgen's
     * JitEngine. Fast execution, requires platform-specific code generation.
     * This is the default mode.
     */
    JIT,

    /**
     * Ahead-of-time compilation. Compiles WASM to a standalone native binary
     * or shared library before execution. No JVM required at runtime.
     * Uses kgen's NativeCompiler pipeline.
     */
    AOT,

    /**
     * Bytecode interpreter. Executes WASM instructions directly without
     * compilation. Slower but fully portable, useful for debugging and
     * platforms without native code generation support.
     */
    INTERPRET,

    /**
     * Tiered execution. Starts with interpreter for cold code, promotes to
     * JIT compilation after a threshold. Best of both worlds — fast startup
     * with peak performance for hot code.
     */
    TIERED,
}

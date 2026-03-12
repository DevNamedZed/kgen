package org.kgen.codegen

import org.kgen.ir.Module

/**
 * Translates a [Module] into target-specific binary output.
 *
 * Each backend (WASM, JVM, x86-64, ARM64, RISC-V, MSIL) provides an implementation.
 * Backends are discovered via [TargetRegistry] and [java.util.ServiceLoader].
 *
 * ```kotlin
 * val wasm = TargetRegistry.generator("wasm")
 * val bytes = wasm.generate(module, CodeGenOptions(optimizationLevel = OptLevel.O2))
 * ```
 */
interface CodeGenerator {
    /** Target identifier (e.g., "wasm", "jvm", "x86_64", "arm64", "riscv", "msil"). */
    val targetName: String

    /** Compile [module] to binary output according to [options]. */
    fun generate(module: Module, options: CodeGenOptions = CodeGenOptions()): ByteArray

    /**
     * Compile [module] to a lightweight [CompiledCode] containing assembled bytes,
     * symbols, and relocations without constructing a full object file.
     *
     * This is the preferred entry point for JIT compilation. For AOT, use [generate]
     * or call [CompiledCode.toObjectFile] on the result.
     *
     * The default implementation is not available — backends must override this
     * to get the JIT-optimized path.
     */
    fun generateCode(module: Module): CompiledCode {
        throw UnsupportedOperationException("$targetName backend does not support generateCode()")
    }
}

package org.kgen.codegen

import org.kgen.ir.Module

/**
 * Translates a [Module] into target-specific binary output.
 *
 * This is the top layer of kgen's three-layer compilation API:
 *
 * 1. **CodeGenerator** (this interface) -- IR to binary. Accepts a [Module], runs lowering
 *    passes, register allocation, instruction selection, and emits an object file or raw code.
 * 2. **Assembler** -- instruction-level emit. Encodes individual machine instructions
 *    into bytes (e.g., `X86Assembler`, `Arm64Assembler`).
 * 3. **Disassembler** -- binary to instructions. Decodes bytes back into a structured
 *    instruction stream for analysis or display.
 *
 * Each backend (WASM, JVM, x86-64, ARM64, RISC-V, MSIL) provides an implementation.
 * Backends are discovered via [TargetRegistry] and [java.util.ServiceLoader].
 *
 * ```java
 * CodeGenerator wasm = TargetRegistry.generator("wasm");
 * byte[] bytes = wasm.generate(module, new CodeGenOptions(OptLevel.O2));
 * ```
 *
 * See `spec/api-design.md` for the full API design and `spec/extensibility.md` for
 * adding custom backends.
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

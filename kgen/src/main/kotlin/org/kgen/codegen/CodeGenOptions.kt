package org.kgen.codegen

import org.kgen.binary.mangling.ManglingScheme
import org.kgen.ir.target.Target

/**
 * Options controlling code generation behavior.
 *
 * ```java
 * // With target
 * new CodeGenOptions(Target.x86_64(X86CPU.HASWELL), OptLevel.O2);
 *
 * // Without target (backend uses its default)
 * new CodeGenOptions(OptLevel.O2);
 * ```
 */
data class CodeGenOptions(
    /** Target architecture. When set, the code generator uses this for CPU features, triple, data layout. */
    val target: Target? = null,
    /** Optimization level. Higher levels trade compile time for better output. */
    val optimizationLevel: OptLevel = OptLevel.O0,
    /** Whether to emit debug information (DWARF, PDB, etc.). */
    val debugInfo: Boolean = false,
    /** Position-independent code mode. */
    val picMode: PICMode = PICMode.STATIC,
    /** Relocation model for the generated code. */
    val relocationModel: RelocationModel = RelocationModel.STATIC,
    /** Output format. */
    val outputFormat: OutputFormat = OutputFormat.BINARY,
    /** Symbol mangling scheme. When set, exported symbol names are mangled accordingly. */
    val manglingScheme: ManglingScheme? = null,
    /** Entry point symbol name for executables. When set, the linker uses this instead of searching for _start/main. */
    val entryPoint: String? = null,
)

/** Optimization levels. */
enum class OptLevel {
    /** No optimization. Fast compile, debuggable output. */
    O0,
    /** Basic optimizations. Minimal compile-time increase. */
    O1,
    /** Standard optimizations. Good balance of speed and compile time. */
    O2,
    /** Aggressive optimizations. May increase compile time and code size significantly. */
    O3,
    /** Optimize for size. Like O2 but prefers smaller code. */
    OS,
    /** Optimize aggressively for size. May sacrifice performance for the smallest possible output. */
    OZ,
}

/** Position-independent code modes. */
enum class PICMode {
    /** Static (absolute) addressing. Code cannot be relocated at load time. */
    STATIC,
    /** Position-independent code. Required for shared libraries. */
    PIC,
    /** Position-independent executable. Like PIC but for executables (enables ASLR). */
    PIE,
}

/** Relocation models for code generation. */
enum class RelocationModel {
    /** Absolute addresses. Fastest but not relocatable. */
    STATIC,
    /** GOT/PLT-based relocation for shared libraries. */
    PIC,
    /** Dynamic linking without PIC (uses text relocations). Deprecated on most platforms. */
    DYNAMIC_NO_PIC,
}

/** Output format for code generation. */
enum class OutputFormat {
    /** Raw executable binary (platform-specific: .wasm, .class, raw machine code). */
    BINARY,
    /** Object file (ELF .o, PE .obj, Mach-O .o). Needs linking. */
    OBJECT,
    /** Human-readable assembly text. */
    ASSEMBLY_TEXT,
}

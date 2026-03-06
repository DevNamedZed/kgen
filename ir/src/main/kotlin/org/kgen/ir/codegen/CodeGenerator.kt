package org.kgen.ir.codegen

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
}

/** Options controlling code generation behavior. */
data class CodeGenOptions(
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

/**
 * Raw instruction-level assembler. Emits target-specific instructions without going through IR.
 *
 * Type parameter [I] is the target's instruction type (e.g., `X86Instruction`, `ArmInstruction`).
 * Useful for hand-tuned code, intrinsics, or JIT compilation.
 *
 * ```kotlin
 * val asm: Assembler<X86Instruction> = ...
 * asm.label("main")
 * asm.emit(X86Instruction.Push(RBP))
 * asm.emit(X86Instruction.Mov(RBP, RSP))
 * val bytes = asm.assemble()
 * ```
 */
interface Assembler<I : Any> {
    /** Target identifier. */
    val targetName: String

    /** Emit a single instruction. */
    fun emit(instruction: I)

    /** Emit a list of instructions in order. */
    fun emit(instructions: List<I>) { instructions.forEach(::emit) }

    /** Define a label at the current position. */
    fun label(name: String)

    /** Insert alignment padding to the next [bytes]-byte boundary. */
    fun align(bytes: Int)

    /** Emit raw data bytes at the current position. */
    fun data(bytes: ByteArray)

    /** Finalize and return the assembled binary. */
    fun assemble(): ByteArray

    /** Reset the assembler state for reuse. */
    fun reset()
}

/**
 * Disassembles binary machine code into structured instructions.
 *
 * Type parameter [I] is the target's instruction type.
 *
 * ```kotlin
 * val dis: Disassembler<X86Instruction> = ...
 * val instructions = dis.disassemble(bytes, baseAddress = 0x401000)
 * instructions.forEach { println(it) }  // "0x00401000:  push rbp"
 * ```
 */
interface Disassembler<I : Any> {
    /** Target identifier. */
    val targetName: String

    /** Disassemble all instructions in [bytes], starting at [baseAddress]. */
    fun disassemble(bytes: ByteArray, baseAddress: Long = 0): List<DisassembledInstruction<I>>

    /** Disassemble a single instruction at [offset] in [bytes]. Returns null if decoding fails. */
    fun disassembleOne(bytes: ByteArray, offset: Int = 0, baseAddress: Long = 0): DisassembledInstruction<I>?
}

/** A single disassembled instruction with its address, raw bytes, and textual representation. */
data class DisassembledInstruction<I : Any>(
    /** Virtual address of this instruction. */
    val address: Long,
    /** Raw encoded bytes. */
    val bytes: ByteArray,
    /** Decoded instruction in the target's type system. */
    val instruction: I,
    /** Instruction mnemonic (e.g., "mov", "add", "push"). */
    val mnemonic: String,
    /** Operand string (e.g., "rax, rbx"). */
    val operands: String,
    /** Instruction size in bytes. */
    val size: Int,
) {
    override fun toString(): String = "0x${address.toString(16).padStart(8, '0')}:  $mnemonic $operands"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisassembledInstruction<*>) return false
        return address == other.address && bytes.contentEquals(other.bytes) && instruction == other.instruction
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + instruction.hashCode()
        return result
    }
}

/**
 * Registry for code generators, assemblers, and disassemblers.
 *
 * Backends register themselves (typically via ServiceLoader), and consumers
 * look up backends by target name:
 *
 * ```kotlin
 * val registry = TargetRegistry()
 * ServiceLoader.load(CodeGenerator::class.java).forEach { registry.registerGenerator(it) }
 *
 * val gen = registry.generator("wasm")
 * val bytes = gen.generate(module)
 * ```
 */
class TargetRegistry {
    private val generators = mutableMapOf<String, CodeGenerator>()
    private val assemblers = mutableMapOf<String, Assembler<*>>()
    private val disassemblers = mutableMapOf<String, Disassembler<*>>()

    fun registerGenerator(gen: CodeGenerator) { generators[gen.targetName] = gen }
    fun registerAssembler(asm: Assembler<*>) { assemblers[asm.targetName] = asm }
    fun registerDisassembler(dis: Disassembler<*>) { disassemblers[dis.targetName] = dis }

    fun generator(target: String): CodeGenerator =
        generators[target] ?: error("No code generator registered for target: $target")

    fun assembler(target: String): Assembler<*> =
        assemblers[target] ?: error("No assembler registered for target: $target")

    fun disassembler(target: String): Disassembler<*> =
        disassemblers[target] ?: error("No disassembler registered for target: $target")

    /** All target names that have at least one registered component. */
    fun availableTargets(): Set<String> = generators.keys + assemblers.keys + disassemblers.keys
}

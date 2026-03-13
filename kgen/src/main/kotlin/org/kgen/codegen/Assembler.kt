package org.kgen.codegen

/**
 * Low-level instruction assembler for a specific target architecture.
 *
 * Part of the three-layer code generation API: [CodeGenerator] (IR to binary),
 * [Assembler] (structured instructions to raw bytes), and [Disassembler] (raw bytes to instructions).
 * Each backend provides its own concrete assembler (e.g., `X86Assembler`, `Arm64Assembler`,
 * `RiscVAssembler`, `WasmAssembler`, `JvmAssembler`, `CilAssembler`) parameterized by
 * a backend-specific instruction type [I].
 *
 * Assemblers are stateful: emit instructions and labels in order, then call [assemble]
 * to produce the final byte sequence. Labels are resolved during assembly, so forward
 * references (e.g., branch targets not yet emitted) are handled automatically.
 *
 * ```java
 * Assembler<X86Instruction> asm = new X86Assembler();
 * asm.emit(X86Instruction.MOV(RAX, RBX));
 * asm.label("loop");
 * byte[] bytes = asm.assemble();
 * ```
 *
 * See `spec/roadmap.md` for the full list of supported targets and instruction coverage.
 */
interface Assembler<I> {
    /** Target identifier (e.g., "x86_64", "aarch64"). */
    val targetName: String

    /** Emit a single instruction. */
    fun emit(instruction: I)

    /** Define a label at the current position. */
    fun label(name: String)

    /** Insert alignment padding to the next [bytes]-byte boundary. */
    fun align(bytes: Int)

    /** Emit raw data bytes. */
    fun data(bytes: ByteArray)

    /** Assemble all emitted instructions and data into a byte array. */
    fun assemble(): ByteArray

    /** Reset the assembler, discarding all emitted content. */
    fun reset()
}

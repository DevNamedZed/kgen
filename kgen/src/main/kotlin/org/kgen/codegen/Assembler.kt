package org.kgen.codegen

/**
 * Low-level instruction assembler for a specific target.
 *
 * Assemblers turn structured instruction objects into raw bytes.
 * Each backend provides its own instruction type [I].
 *
 * ```kotlin
 * val asm: Assembler<X86Instruction> = ...
 * asm.emit(X86Instruction.MOV(RAX, RBX))
 * asm.label("loop")
 * val bytes = asm.assemble()
 * ```
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

package org.kgen.codegen

/**
 * Decodes raw bytes into structured instruction sequences.
 *
 * Each backend provides its own instruction type [I].
 *
 * ```kotlin
 * val disasm: Disassembler<X86Instruction> = ...
 * val instructions = disasm.disassemble(bytes, baseAddress = 0x401000)
 * instructions.forEach { println("${it.address}: ${it.instruction}") }
 * ```
 */
interface Disassembler<I> {
    /** Target identifier (e.g., "x86_64", "aarch64"). */
    val targetName: String

    /** Disassemble all instructions in [bytes] starting from [baseAddress]. */
    fun disassemble(bytes: ByteArray, baseAddress: Long = 0): List<DisassembledInstruction<I>>

    /** Decode a single instruction at [offset] within [bytes]. Returns null if decoding fails. */
    fun disassembleOne(bytes: ByteArray, offset: Int, baseAddress: Long = 0): DisassembledInstruction<I>?
}

/**
 * A single disassembled instruction with its address, raw bytes, and decoded form.
 */
data class DisassembledInstruction<I>(
    /** Virtual address of this instruction. */
    val address: Long,
    /** Raw encoded bytes. */
    val bytes: ByteArray,
    /** Decoded instruction. */
    val instruction: I,
    /** Human-readable mnemonic (e.g., "mov", "add"). */
    val mnemonic: String = "",
    /** Human-readable operand text (e.g., "rax, rbx"). */
    val operands: String = "",
    /** Size of the instruction in bytes. */
    val size: Int = bytes.size,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisassembledInstruction<*>) return false
        return address == other.address && bytes.contentEquals(other.bytes) && instruction == other.instruction
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + (instruction?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "0x${address.toString(16).padStart(8, '0')}: $instruction"
}

package org.kgen.reflect

/**
 * A disassembled instruction from any architecture.
 *
 * Implemented by [org.kgen.target.x86.disasm.X86Instruction],
 * [org.kgen.target.arm64.disasm.Arm64Instruction],
 * [org.kgen.target.riscv.disasm.RiscVDisassembler.DisassembledInsn],
 * and [org.kgen.target.wasm.disasm.WasmInstruction].
 *
 * Downcast to the concrete type for arch-specific details (x86 typed operands, etc).
 */
interface Instruction {
    val address: Long
    val bytes: ByteArray
    val mnemonic: String
    val size: Int get() = bytes.size

    fun operandsText(): String
    fun text(): String
}

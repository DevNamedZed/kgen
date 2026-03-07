package org.kgen.reflect

import org.kgen.backend.x86.disasm.X86Disassembler
import org.kgen.backend.x86.disasm.X86Instruction
import org.kgen.backend.arm64.disasm.Arm64Disassembler
import org.kgen.backend.arm64.disasm.Arm64Instruction
import org.kgen.backend.riscv.disasm.RiscVDisassembler

/**
 * Disassemble native code from a live address or byte array.
 *
 * ```java
 * long addr = ProcessSymbols.lookup("strlen");
 * var insns = CodeView.disassembleAt(addr, 64);
 * for (var inst : insns) System.out.println(inst);
 * ```
 */
object CodeView {

    /**
     * Read [length] bytes from [address] and disassemble as x86-64.
     */
    @JvmStatic
    fun disassembleX86(address: Long, length: Int): List<X86Instruction> {
        val bytes = NativeMemory.readBytes(address, length)
        return X86Disassembler().disassembleRaw(bytes, address)
    }

    /**
     * Disassemble x86-64 code from a byte array.
     */
    @JvmStatic
    fun disassembleX86(bytes: ByteArray, baseAddress: Long = 0): List<X86Instruction> {
        return X86Disassembler().disassembleRaw(bytes, baseAddress)
    }

    /**
     * Read [length] bytes from [address] and disassemble as ARM64.
     */
    @JvmStatic
    fun disassembleArm64(address: Long, length: Int): List<Arm64Instruction> {
        val bytes = NativeMemory.readBytes(address, length)
        return Arm64Disassembler().disassemble(bytes, address)
    }

    /**
     * Read [length] bytes from [address] and disassemble as RISC-V.
     */
    @JvmStatic
    fun disassembleRiscV(address: Long, length: Int): List<RiscVDisassembler.DisassembledInsn> {
        val bytes = NativeMemory.readBytes(address, length)
        return RiscVDisassembler().disassemble(bytes, address)
    }

    /**
     * Disassemble at [address] using the architecture of the current process.
     * Returns a list of formatted instruction strings.
     */
    @JvmStatic
    fun disassembleAt(address: Long, length: Int): List<String> {
        val arch = System.getProperty("os.arch")
        return when {
            arch == "amd64" || arch == "x86_64" -> disassembleX86(address, length).map { it.toString() }
            arch == "aarch64" -> disassembleArm64(address, length).map { it.toString() }
            else -> throw UnsupportedOperationException("Unsupported architecture: $arch")
        }
    }
}

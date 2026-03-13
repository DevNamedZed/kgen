package org.kgen.reflect

import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.asm.Arm64Assembler
import org.kgen.target.riscv.X0
import org.kgen.target.riscv.X6
import org.kgen.target.riscv.X10
import org.kgen.target.riscv.asm.RiscVAssembler
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler

/**
 * Patch live native code in the current process.
 *
 * ```java
 * byte[] original = CodePatch.read(address, 16);
 * CodePatch.writeNop(address, 5);
 * CodePatch.writeJump(address, newTarget);
 * CodePatch.restore(address, original);
 * ```
 */
object CodePatch {

    @JvmStatic
    fun read(address: Long, length: Int): ByteArray {
        return NativeMemory.readBytes(address, length)
    }

    @JvmStatic
    fun write(address: Long, code: ByteArray) {
        val size = code.size.toLong()
        NativeMemory.mprotect(address, size, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE or NativeMemory.PROT_EXEC)
        NativeMemory.writeBytes(address, code)
    }

    @JvmStatic
    fun restore(address: Long, originalCode: ByteArray) {
        write(address, originalCode)
    }

    // ── x86-64 ───────────────────────────────────────────────────────

    /** Write [count] x86 NOP instructions at [address]. Overwrites [count] bytes. */
    @JvmStatic
    fun writeNop(address: Long, count: Int) {
        val asm = X86Assembler()
        repeat(count) { asm.nop() }
        write(address, asm.toByteArray())
    }

    /** Write a 5-byte relative JMP at [address] to [target]. x86-64. */
    @JvmStatic
    fun writeJump(address: Long, target: Long) {
        // x86 assembler uses labels; for patching at runtime we need a raw rel32
        val rel = (target - address - 5).toInt()
        val asm = X86Assembler()
        asm.emitByte(0xE9)
        asm.emitInt32(rel)
        write(address, asm.toByteArray())
    }

    /** Write a 14-byte absolute JMP at [address] to [target]. x86-64. */
    @JvmStatic
    fun writeAbsoluteJump(address: Long, target: Long) {
        val asm = X86Assembler()
        // jmp qword ptr [rip+0]
        asm.emitByte(0xFF)
        asm.emitByte(0x25)
        asm.emitInt32(0)
        asm.emitInt64(target)
        write(address, asm.toByteArray())
    }

    /** Write a `ret` at [address]. x86-64. Overwrites 1 byte. */
    @JvmStatic
    fun writeRet(address: Long) {
        val asm = X86Assembler()
        asm.ret()
        write(address, asm.toByteArray())
    }

    /** Write `mov eax, value; ret` at [address]. x86-64. Overwrites 6 bytes. */
    @JvmStatic
    fun writeReturnInt(address: Long, value: Int) {
        val asm = X86Assembler()
        asm.mov(X86Register.EAX, value)
        asm.ret()
        write(address, asm.toByteArray())
    }

    // ── ARM64 ────────────────────────────────────────────────────────

    /** Write [count] ARM64 NOP instructions at [address]. Overwrites `count * 4` bytes. */
    @JvmStatic
    fun writeArm64Nop(address: Long, count: Int) {
        val asm = Arm64Assembler()
        repeat(count) { asm.nop() }
        write(address, asm.bytes())
    }

    /**
     * Write a PC-relative B (branch) at [address] to [target]. ARM64.
     * Range: +/-128 MB. Overwrites 4 bytes.
     */
    @JvmStatic
    fun writeArm64Jump(address: Long, target: Long) {
        val offset = target - address
        require(offset in -0x8000000L..0x7FFFFFFL) {
            "ARM64 B offset out of range (+/-128MB): $offset"
        }
        val asm = Arm64Assembler()
        asm.b(offset.toInt())
        write(address, asm.bytes())
    }

    /**
     * Write an absolute jump to [target] using X16 (IP0). ARM64.
     * MOVZ+3xMOVK+BR X16. Overwrites 20 bytes.
     */
    @JvmStatic
    fun writeArm64AbsoluteJump(address: Long, target: Long) {
        val asm = Arm64Assembler()
        asm.movz(Arm64Register.X16, (target and 0xFFFF).toInt(), 0)
        asm.movk(Arm64Register.X16, ((target shr 16) and 0xFFFF).toInt(), 16)
        asm.movk(Arm64Register.X16, ((target shr 32) and 0xFFFF).toInt(), 32)
        asm.movk(Arm64Register.X16, ((target shr 48) and 0xFFFF).toInt(), 48)
        asm.br(Arm64Register.X16)
        write(address, asm.bytes())
    }

    /** Write a RET instruction at [address]. ARM64. Overwrites 4 bytes. */
    @JvmStatic
    fun writeArm64Ret(address: Long) {
        val asm = Arm64Assembler()
        asm.ret()
        write(address, asm.bytes())
    }

    /**
     * Write `MOVZ W0, #value; RET` at [address]. ARM64.
     * 8 bytes for values 0..65535, 12 bytes for larger 32-bit values.
     */
    @JvmStatic
    fun writeArm64ReturnInt(address: Long, value: Int) {
        val asm = Arm64Assembler()
        val lo = value and 0xFFFF
        val hi = (value ushr 16) and 0xFFFF
        asm.movz(Arm64Register.W0, lo, 0)
        if (hi != 0) {
            // Use X0 for MOVK with shift (W0 aliases lower 32 bits of X0)
            asm.movk(Arm64Register.X0, hi, 16)
        }
        asm.ret()
        write(address, asm.bytes())
    }

    // ── RISC-V ───────────────────────────────────────────────────────

    /** Write [count] RISC-V NOP instructions at [address]. Overwrites `count * 4` bytes. */
    @JvmStatic
    fun writeRiscVNop(address: Long, count: Int) {
        val asm = RiscVAssembler()
        repeat(count) { asm.nop() }
        write(address, asm.toByteArray())
    }

    /**
     * Write a JAL x0 (unconditional jump) at [address] to [target]. RISC-V.
     * Range: +/-1 MB. Overwrites 4 bytes.
     */
    @JvmStatic
    fun writeRiscVJump(address: Long, target: Long) {
        val offset = (target - address).toInt()
        require(offset.toLong() in -0x100000L..0xFFFFFL) {
            "RISC-V JAL offset out of range (+/-1MB): $offset"
        }
        val asm = RiscVAssembler()
        asm.jal(X0, offset)
        write(address, asm.toByteArray())
    }

    /**
     * Write an absolute jump to [target] using t1 (x6). RISC-V.
     * Uses li+jalr sequence. Overwrites variable bytes depending on target value.
     */
    @JvmStatic
    fun writeRiscVAbsoluteJump(address: Long, target: Long) {
        val asm = RiscVAssembler()
        asm.li(X6, target)
        asm.jalr(X0, X6, 0)
        write(address, asm.toByteArray())
    }

    /** Write a RET instruction at [address]. RISC-V. Overwrites 4 bytes. */
    @JvmStatic
    fun writeRiscVRet(address: Long) {
        val asm = RiscVAssembler()
        asm.ret()
        write(address, asm.toByteArray())
    }

    /**
     * Write `li a0, value; ret` at [address]. RISC-V.
     * 8 bytes for small immediates, 12 bytes for larger 32-bit values.
     */
    @JvmStatic
    fun writeRiscVReturnInt(address: Long, value: Int) {
        val asm = RiscVAssembler()
        asm.li(X10, value)
        asm.ret()
        write(address, asm.toByteArray())
    }
}

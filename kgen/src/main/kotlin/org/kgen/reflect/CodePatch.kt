package org.kgen.reflect

import org.kgen.backend.x86.asm.X86Assembler
import org.kgen.backend.x86.disasm.X86Disassembler
import org.kgen.backend.x86.disasm.X86Instruction

/**
 * Patch live native code in the current process.
 *
 * ```java
 * // Read, patch, write back
 * byte[] original = CodePatch.read(address, 16);
 * CodePatch.writeNop(address, 5);           // NOP out 5 bytes
 * CodePatch.writeJump(address, newTarget);   // redirect to new function
 * CodePatch.restore(address, original);      // restore original code
 *
 * // Assemble and patch
 * var asm = new X86Assembler();
 * asm.mov(eax, 42);
 * asm.ret_();
 * CodePatch.write(address, asm.assemble());
 * ```
 */
object CodePatch {

    /**
     * Read [length] bytes of code at [address].
     */
    @JvmStatic
    fun read(address: Long, length: Int): ByteArray {
        return NativeMemory.readBytes(address, length)
    }

    /**
     * Write [code] bytes at [address].
     * Makes the memory writable first, then restores the original protection.
     */
    @JvmStatic
    fun write(address: Long, code: ByteArray) {
        val size = code.size.toLong()
        NativeMemory.mprotect(address, size, NativeMemory.PROT_READ or NativeMemory.PROT_WRITE or NativeMemory.PROT_EXEC)
        NativeMemory.writeBytes(address, code)
    }

    /**
     * Restore original code at [address].
     */
    @JvmStatic
    fun restore(address: Long, originalCode: ByteArray) {
        write(address, originalCode)
    }

    /**
     * Write [count] NOP instructions (0x90) at [address]. x86-64 only.
     */
    @JvmStatic
    fun writeNop(address: Long, count: Int) {
        write(address, ByteArray(count) { 0x90.toByte() })
    }

    /**
     * Write a 5-byte relative JMP at [address] to [target]. x86-64 only.
     * Overwrites 5 bytes: `E9 <rel32>`.
     */
    @JvmStatic
    fun writeJump(address: Long, target: Long) {
        val rel = (target - address - 5).toInt()
        val code = ByteArray(5)
        code[0] = 0xE9.toByte()
        code[1] = (rel and 0xFF).toByte()
        code[2] = ((rel shr 8) and 0xFF).toByte()
        code[3] = ((rel shr 16) and 0xFF).toByte()
        code[4] = ((rel shr 24) and 0xFF).toByte()
        write(address, code)
    }

    /**
     * Write a 14-byte absolute JMP at [address] to [target]. x86-64 only.
     * Uses `jmp [rip+0]; .quad target` — works for any 64-bit address.
     * Overwrites 14 bytes.
     */
    @JvmStatic
    fun writeAbsoluteJump(address: Long, target: Long) {
        val code = ByteArray(14)
        // FF 25 00 00 00 00  = jmp qword ptr [rip+0]
        code[0] = 0xFF.toByte()
        code[1] = 0x25
        code[2] = 0; code[3] = 0; code[4] = 0; code[5] = 0
        // followed by the 8-byte absolute address
        for (i in 0..7) code[6 + i] = ((target shr (i * 8)) and 0xFF).toByte()
        write(address, code)
    }

    /**
     * Write a `ret` instruction at [address]. x86-64 only.
     * Overwrites 1 byte.
     */
    @JvmStatic
    fun writeRet(address: Long) {
        write(address, byteArrayOf(0xC3.toByte()))
    }

    /**
     * Write `mov eax, [value]; ret` at [address]. x86-64 only.
     * Makes the function always return [value]. Overwrites 6 bytes.
     */
    @JvmStatic
    fun writeReturnInt(address: Long, value: Int) {
        val code = ByteArray(6)
        code[0] = 0xB8.toByte() // mov eax, imm32
        code[1] = (value and 0xFF).toByte()
        code[2] = ((value shr 8) and 0xFF).toByte()
        code[3] = ((value shr 16) and 0xFF).toByte()
        code[4] = ((value shr 24) and 0xFF).toByte()
        code[5] = 0xC3.toByte() // ret
        write(address, code)
    }
}

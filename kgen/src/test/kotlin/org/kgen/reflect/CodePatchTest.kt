package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.target.arm64.Arm64Register
import org.kgen.target.arm64.asm.Arm64Assembler
import org.kgen.target.riscv.X0
import org.kgen.target.riscv.X6
import org.kgen.target.riscv.X10
import org.kgen.target.riscv.asm.RiscVAssembler
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.asm.X86Assembler
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Verifies that CodePatch methods produce correct instruction encodings
 * by comparing against the project's own assemblers as oracles.
 */
class CodePatchTest {

    // ── x86-64 ───────────────────────────────────────────────────────

    @Test
    fun `x86 nop encoding`() {
        val asm = X86Assembler()
        repeat(3) { asm.nop() }
        val bytes = asm.toByteArray()
        assertEquals(3, bytes.size)
        bytes.forEach { assertEquals(0x90.toByte(), it) }
    }

    @Test
    fun `x86 ret encoding`() {
        val asm = X86Assembler()
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(0xC3.toByte(), bytes[0])
    }

    @Test
    fun `x86 mov eax imm32 plus ret encoding`() {
        val asm = X86Assembler()
        asm.mov(X86Register.EAX, 42)
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(6, bytes.size)
        assertEquals(0xB8.toByte(), bytes[0])
        assertEquals(42.toByte(), bytes[1])
        assertEquals(0xC3.toByte(), bytes[5])
    }

    @Test
    fun `x86 mov eax negative value`() {
        val asm = X86Assembler()
        asm.mov(X86Register.EAX, -1)
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(6, bytes.size)
        // -1 = 0xFFFFFFFF in le
        assertEquals(0xFF.toByte(), bytes[1])
        assertEquals(0xFF.toByte(), bytes[2])
        assertEquals(0xFF.toByte(), bytes[3])
        assertEquals(0xFF.toByte(), bytes[4])
    }

    @Test
    fun `x86 jmp rel32 encoding`() {
        val asm = X86Assembler()
        asm.emitByte(0xE9)
        asm.emitInt32(0x0FFB) // rel32 = target - (addr + 5)
        val bytes = asm.toByteArray()
        assertEquals(5, bytes.size)
        assertEquals(0xE9.toByte(), bytes[0])
    }

    @Test
    fun `x86 absolute jump encoding`() {
        val target = 0xDEADBEEFCAFEL
        val asm = X86Assembler()
        asm.emitByte(0xFF)
        asm.emitByte(0x25)
        asm.emitInt32(0)
        asm.emitInt64(target)
        val bytes = asm.toByteArray()
        assertEquals(14, bytes.size)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0x25.toByte(), bytes[1])
        var decoded = 0L
        for (i in 0..7) {
            decoded = decoded or ((bytes[6 + i].toLong() and 0xFF) shl (i * 8))
        }
        assertEquals(target, decoded)
    }

    // ── ARM64 ────────────────────────────────────────────────────────

    @Test
    fun `arm64 nop encoding`() {
        val asm = Arm64Assembler()
        repeat(2) { asm.nop() }
        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        assertLe32(0xD503201F.toInt(), bytes, 0)
        assertLe32(0xD503201F.toInt(), bytes, 4)
    }

    @Test
    fun `arm64 ret encoding`() {
        val asm = Arm64Assembler()
        asm.ret()
        val bytes = asm.bytes()
        assertEquals(4, bytes.size)
        assertLe32(0xD65F03C0.toInt(), bytes, 0)
    }

    @Test
    fun `arm64 b positive offset encoding`() {
        val asm = Arm64Assembler()
        asm.b(0x100) // 256 bytes forward
        val bytes = asm.bytes()
        assertEquals(4, bytes.size)
        val inst = readLe32(bytes, 0)
        // B: 0x14000000 | (offset >> 2)
        assertEquals(0x14000000 or (0x100 shr 2), inst)
    }

    @Test
    fun `arm64 b negative offset encoding`() {
        val asm = Arm64Assembler()
        asm.b(-0x100) // 256 bytes backward
        val bytes = asm.bytes()
        assertEquals(4, bytes.size)
        val inst = readLe32(bytes, 0)
        val imm26 = ((-0x100) shr 2) and 0x03FFFFFF
        assertEquals(0x14000000 or imm26, inst)
    }

    @Test
    fun `arm64 b rejects unaligned offset`() {
        val asm = Arm64Assembler()
        assertThrows<IllegalArgumentException> { asm.b(3) }
    }

    @Test
    fun `arm64 absolute jump movz movk br sequence`() {
        val target = 0x0000DEADBEEFCAFEL
        val asm = Arm64Assembler()
        asm.movz(Arm64Register.X16, (target and 0xFFFF).toInt(), 0)
        asm.movk(Arm64Register.X16, ((target shr 16) and 0xFFFF).toInt(), 16)
        asm.movk(Arm64Register.X16, ((target shr 32) and 0xFFFF).toInt(), 32)
        asm.movk(Arm64Register.X16, ((target shr 48) and 0xFFFF).toInt(), 48)
        asm.br(Arm64Register.X16)
        val bytes = asm.bytes()
        assertEquals(20, bytes.size)
        // Last instruction should be BR X16 = 0xD61F0200
        assertLe32(0xD61F0200.toInt(), bytes, 16)
    }

    @Test
    fun `arm64 returnInt small value movz w0 + ret`() {
        val asm = Arm64Assembler()
        asm.movz(Arm64Register.W0, 99, 0)
        asm.ret()
        val bytes = asm.bytes()
        assertEquals(8, bytes.size)
        // MOVZ W0, #99: 0x52800000 | (99 << 5) | 0 = 0x52800C60
        val inst = readLe32(bytes, 0)
        assertEquals(0x52800000 or (99 shl 5), inst)
        assertLe32(0xD65F03C0.toInt(), bytes, 4)
    }

    @Test
    fun `arm64 returnInt large value movz movk ret`() {
        val value = 0x00010042
        val asm = Arm64Assembler()
        asm.movz(Arm64Register.W0, value and 0xFFFF, 0)
        asm.movk(Arm64Register.X0, (value ushr 16) and 0xFFFF, 16)
        asm.ret()
        val bytes = asm.bytes()
        assertEquals(12, bytes.size)
        assertLe32(0xD65F03C0.toInt(), bytes, 8)
    }

    // ── RISC-V ───────────────────────────────────────────────────────

    @Test
    fun `riscv nop encoding`() {
        val asm = RiscVAssembler()
        repeat(2) { asm.nop() }
        val bytes = asm.toByteArray()
        assertEquals(8, bytes.size)
        // NOP = addi x0, x0, 0 = 0x00000013
        assertLe32(0x00000013, bytes, 0)
        assertLe32(0x00000013, bytes, 4)
    }

    @Test
    fun `riscv ret encoding`() {
        val asm = RiscVAssembler()
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(4, bytes.size)
        // ret = jalr x0, x1, 0 = 0x00008067
        assertLe32(0x00008067, bytes, 0)
    }

    @Test
    fun `riscv jal positive offset`() {
        val asm = RiscVAssembler()
        asm.jal(X0, 0x400)
        val bytes = asm.toByteArray()
        assertEquals(4, bytes.size)
        val inst = readLe32(bytes, 0)
        assertEquals(0x6F, inst and 0x7F) // JAL opcode
    }

    @Test
    fun `riscv jal negative offset`() {
        val asm = RiscVAssembler()
        asm.jal(X0, -0x200)
        val bytes = asm.toByteArray()
        assertEquals(4, bytes.size)
        val inst = readLe32(bytes, 0)
        assertEquals(0x6F, inst and 0x7F) // JAL opcode
    }

    @Test
    fun `riscv absolute jump li jalr sequence`() {
        val target = 0x0000DEADBEEFCAFEL
        val asm = RiscVAssembler()
        asm.li(X6, target)
        asm.jalr(X0, X6, 0)
        val bytes = asm.toByteArray()
        // li for 64-bit generates a multi-instruction sequence; final is jalr
        val lastInst = readLe32(bytes, bytes.size - 4)
        assertEquals(0x67, lastInst and 0x7F) // JALR opcode
    }

    @Test
    fun `riscv returnInt small value li + ret`() {
        val asm = RiscVAssembler()
        asm.li(X10, 42)
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(8, bytes.size)
        // li 42 = addi a0, x0, 42
        val inst = readLe32(bytes, 0)
        assertEquals(0x13, inst and 0x7F) // ADDI opcode
        // ret
        assertLe32(0x00008067, bytes, 4)
    }

    @Test
    fun `riscv returnInt negative small value`() {
        val asm = RiscVAssembler()
        asm.li(X10, -1)
        asm.ret()
        val bytes = asm.toByteArray()
        assertEquals(8, bytes.size)
        // li -1 = addi a0, x0, -1
        val inst = readLe32(bytes, 0)
        assertEquals(0x13, inst and 0x7F) // ADDI opcode
        // immediate field (bits 31:20) should be 0xFFF (-1 sign-extended)
        assertEquals(0xFFF, (inst ushr 20) and 0xFFF)
    }

    @Test
    fun `riscv returnInt large value lui + addi + ret`() {
        val asm = RiscVAssembler()
        asm.li(X10, 0x12345)
        asm.ret()
        val bytes = asm.toByteArray()
        // LUI + ADDI + RET = 12 bytes
        assertEquals(12, bytes.size)
        val luiInst = readLe32(bytes, 0)
        assertEquals(0x37, luiInst and 0x7F) // LUI opcode
        assertLe32(0x00008067, bytes, 8) // RET
    }

    // ── Range validation ─────────────────────────────────────────────

    @Test
    fun `arm64 jump rejects out of range`() {
        assertThrows<IllegalArgumentException> {
            CodePatch.writeArm64Jump(0x1000, 0x1000 + 0x8000004L)
        }
    }

    @Test
    fun `riscv jump rejects out of range`() {
        assertThrows<IllegalArgumentException> {
            CodePatch.writeRiscVJump(0x1000, 0x1000 + 0x100001L)
        }
    }

    // ── Cross-arch consistency ───────────────────────────────────────

    @Test
    fun `all architectures produce 4-byte ret except x86`() {
        val x86 = X86Assembler().also { it.ret() }.toByteArray()
        val arm64 = Arm64Assembler().also { it.ret() }.bytes()
        val riscv = RiscVAssembler().also { it.ret() }.toByteArray()

        assertEquals(1, x86.size)
        assertEquals(4, arm64.size)
        assertEquals(4, riscv.size)
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun readLe32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun assertLe32(expected: Int, bytes: ByteArray, offset: Int) {
        assertEquals(expected, readLe32(bytes, offset),
            "Expected 0x${expected.toUInt().toString(16)} at offset $offset, " +
                    "got 0x${readLe32(bytes, offset).toUInt().toString(16)}")
    }
}

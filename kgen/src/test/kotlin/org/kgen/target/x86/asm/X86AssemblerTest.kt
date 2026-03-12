package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.kgen.binary.elf.ElfWriter
import org.kgen.binary.pe.PeWriter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path

class X86AssemblerTest {

    private val rax = X86Register.RAX as X86Register64
    private val rcx = X86Register.RCX as X86Register64
    private val rdx = X86Register.RDX as X86Register64
    private val rsp = X86Register.RSP as X86Register64
    private val rbp = X86Register.RBP as X86Register64
    private val rsi = X86Register.RSI as X86Register64
    private val rdi = X86Register.RDI as X86Register64
    private val r8  = X86Register.R8  as X86Register64
    private val r9  = X86Register.R9  as X86Register64

    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32
    private val edx = X86Register.EDX as X86Register32
    private val edi = X86Register.EDI as X86Register32
    private val r8d = X86Register.R8D as X86Register32

    @Test
    fun `mov reg-reg encodes correctly`() {
        val asm = X86Assembler()
        asm.mov(rax, rcx as X86Operand64)
        assertArrayEquals(byteArrayOf(0x48, 0x8B.toByte(), 0xC1.toByte()), asm.toByteArray())
    }

    @Test
    fun `mov reg-imm32 encodes correctly`() {
        val asm = X86Assembler()
        asm.mov(eax, 42)
        assertArrayEquals(byteArrayOf(0xB8.toByte(), 42, 0, 0, 0), asm.toByteArray())
    }

    @Test
    fun `mov r64-imm64 encodes correctly`() {
        val asm = X86Assembler()
        asm.mov(rax, 0x0102030405060708L)
        val bytes = asm.toByteArray()
        assertEquals(10, bytes.size)
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0xB8.toByte(), bytes[1])
        assertEquals(0x08.toByte(), bytes[2])
    }

    @Test
    fun `xor reg-reg clears register`() {
        val asm = X86Assembler()
        asm.xor_(edi, edi as X86Operand32)
        val bytes = asm.toByteArray()
        assertEquals(0x33.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[1])
    }

    @Test
    fun `syscall encodes correctly`() {
        val asm = X86Assembler()
        asm.syscall()
        assertArrayEquals(byteArrayOf(0x0F, 0x05), asm.toByteArray())
    }

    @Test
    fun `extended registers need REX`() {
        val asm = X86Assembler()
        asm.mov(r8, r9 as X86Operand64)
        val bytes = asm.toByteArray()
        assertEquals(0x4D.toByte(), bytes[0])
        assertEquals(0x8B.toByte(), bytes[1])
        assertEquals(0xC1.toByte(), bytes[2])
    }

    @Test
    fun `memory operand with displacement`() {
        val asm = X86Assembler()
        val mem = X86Memory.base(rbp).offset(-8)
        asm.mov(rax, mem as X86Operand64)
        val bytes = asm.toByteArray()
        assertEquals(0x48.toByte(), bytes[0])
        assertEquals(0x8B.toByte(), bytes[1])
        assertEquals(0x45.toByte(), bytes[2])
        assertEquals(0xF8.toByte(), bytes[3])
    }

    @Test
    fun `labels and jumps resolve`() {
        val asm = X86Assembler()
        asm.label("start")
        asm.nop()
        asm.jmpLabel("start")
        val bytes = asm.toByteArray()
        assertEquals(0x90.toByte(), bytes[0])
        assertEquals(0xE9.toByte(), bytes[1])
        val rel = (bytes[2].toInt() and 0xFF) or
                ((bytes[3].toInt() and 0xFF) shl 8) or
                ((bytes[4].toInt() and 0xFF) shl 16) or
                ((bytes[5].toInt() and 0xFF) shl 24)
        assertEquals(-6, rel)
    }

    @Test
    fun `assemble Linux hello world`() {
        val message = "Hello, World!\n"
        val msgBytes = message.toByteArray(Charsets.US_ASCII)

        val asm = X86Assembler()

        // First pass: measure code size
        asm.mov(eax, 1)
        asm.mov(edi, 1)
        asm.mov(rsi, 0L)
        asm.mov(edx, msgBytes.size)
        asm.syscall()
        asm.mov(eax, 60)
        asm.xor_(edi, edi as X86Operand32)
        asm.syscall()
        val codeSize = asm.position()

        val dataVaddr = ElfWriter.dataVaddr(codeSize, msgBytes.size)

        // Second pass: real address
        asm.reset()
        asm.mov(eax, 1)
        asm.mov(edi, 1)
        asm.mov(rsi, dataVaddr)
        asm.mov(edx, msgBytes.size)
        asm.syscall()
        asm.mov(eax, 60)
        asm.xor_(edi, edi as X86Operand32)
        asm.syscall()

        val code = asm.toByteArray()
        assertEquals(codeSize, code.size)

        val elf = ElfWriter.writeFlat(code, msgBytes)

        assertEquals(0x7f, elf[0].toInt() and 0xFF)
        assertEquals('E'.code, elf[1].toInt() and 0xFF)
        assertEquals('L'.code, elf[2].toInt() and 0xFF)
        assertEquals('F'.code, elf[3].toInt() and 0xFF)
        assertEquals(2, elf[4].toInt())
        assertEquals(1, elf[5].toInt())

        assertTrue(String(elf, Charsets.US_ASCII).contains("Hello, World!"))

        val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))
        val elfPath = tmpDir.resolve("kgen_hello.elf")
        Files.write(elfPath, elf)
        println("ELF binary written to: $elfPath (${elf.size} bytes)")
    }

    @Test
    fun `assemble Windows hello world`() {
        val message = "Hello, World!\n"
        val msgBytes = (message + "\u0000").toByteArray(Charsets.US_ASCII)

        // Use position tracking to record patch locations
        val asm = X86Assembler()
        assembleWindowsHelloBody(asm, msgBytes.size - 1)
        val codeSize = asm.position()
        val code = asm.toByteArray()

        // Compute section RVAs
        val textRVA = 0x1000
        val rodataRVA = textRVA + alignUp(codeSize, 0x1000)
        val idataRVA = rodataRVA + alignUp(msgBytes.size, 0x1000)
        val iatBaseRVA = idataRVA + 72

        // Re-assemble tracking positions
        asm.reset()
        val patchInfo = assembleWindowsHelloBody(asm, msgBytes.size - 1)
        assertEquals(codeSize, asm.position())
        val code2 = asm.toByteArray()

        // Patch RIP-relative references
        patchRipRel(code2, patchInfo.getStdHandleOff + 2, patchInfo.getStdHandleOff + 6, iatBaseRVA, textRVA)
        patchRipRel(code2, patchInfo.leaMsgOff + 3, patchInfo.leaMsgOff + 7, rodataRVA, textRVA)
        patchRipRel(code2, patchInfo.writeFileOff + 2, patchInfo.writeFileOff + 6, iatBaseRVA + 8, textRVA)
        patchRipRel(code2, patchInfo.exitProcessOff + 2, patchInfo.exitProcessOff + 6, iatBaseRVA + 16, textRVA)

        val pe = PeWriter.writeFlat(code2, msgBytes)

        assertEquals('M'.code, pe[0].toInt() and 0xFF)
        assertEquals('Z'.code, pe[1].toInt() and 0xFF)
        val peOffset = getU32(pe, 0x3C)
        assertEquals('P'.code, pe[peOffset].toInt() and 0xFF)
        assertEquals('E'.code, pe[peOffset + 1].toInt() and 0xFF)
        assertEquals(0x8664, getU16(pe, peOffset + 4))

        val peStr = String(pe, Charsets.US_ASCII)
        assertTrue(peStr.contains("kernel32.dll"))
        assertTrue(peStr.contains("ExitProcess"))
        assertTrue(peStr.contains("Hello, World!"))

        val tmpDir = Path.of(System.getProperty("java.io.tmpdir"))
        val pePath = tmpDir.resolve("kgen_hello.exe")
        Files.write(pePath, pe)
        println("PE binary written to: $pePath (${pe.size} bytes)")
    }

    private data class PePatchInfo(
        val getStdHandleOff: Int,
        val leaMsgOff: Int,
        val writeFileOff: Int,
        val exitProcessOff: Int,
    )

    private fun assembleWindowsHelloBody(asm: X86Assembler, msgLen: Int): PePatchInfo {
        // sub rsp, 0x28
        asm.sub(rsp, 0x28)
        // mov ecx, -11 (STD_OUTPUT_HANDLE)
        asm.mov(ecx, -11)
        // call [rip+disp32] GetStdHandle
        val getStdHandleOff = asm.position()
        asm.emitBytes(0xFF, 0x15, 0, 0, 0, 0)
        // mov rcx, rax
        asm.mov(rcx, rax as X86Operand64)
        // lea rdx, [rip+msg]
        val leaMsgOff = asm.position()
        asm.emitBytes(0x48, 0x8D, 0x15, 0, 0, 0, 0)
        // mov r8d, msgLen
        asm.mov(r8d, msgLen)
        // lea r9, [rsp+0x20]
        asm.emitBytes(0x4C, 0x8D, 0x4C, 0x24, 0x20)
        // mov qword [rsp+0x20], 0
        asm.emitBytes(0x48, 0xC7, 0x44, 0x24, 0x20, 0, 0, 0, 0)
        // call [rip+disp32] WriteFile
        val writeFileOff = asm.position()
        asm.emitBytes(0xFF, 0x15, 0, 0, 0, 0)
        // xor ecx, ecx
        asm.xor_(ecx, ecx as X86Operand32)
        // call [rip+disp32] ExitProcess
        val exitProcessOff = asm.position()
        asm.emitBytes(0xFF, 0x15, 0, 0, 0, 0)
        // int3
        asm.emitByte(0xCC)

        return PePatchInfo(getStdHandleOff, leaMsgOff, writeFileOff, exitProcessOff)
    }

    private fun patchRipRel(code: ByteArray, patchOffset: Int, instrEnd: Int, targetRVA: Int, codeBaseRVA: Int) {
        val rel = targetRVA - (codeBaseRVA + instrEnd)
        code[patchOffset] = (rel and 0xFF).toByte()
        code[patchOffset + 1] = ((rel shr 8) and 0xFF).toByte()
        code[patchOffset + 2] = ((rel shr 16) and 0xFF).toByte()
        code[patchOffset + 3] = ((rel shr 24) and 0xFF).toByte()
    }

    private fun alignUp(value: Int, alignment: Int): Int =
        (value + alignment - 1) and (alignment - 1).inv()

    private fun getU16(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)

    private fun getU32(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or
        ((data[off + 1].toInt() and 0xFF) shl 8) or
        ((data[off + 2].toInt() and 0xFF) shl 16) or
        ((data[off + 3].toInt() and 0xFF) shl 24)

    // --- VEX encoding tests ---

    @Test
    fun `VEX andn r32 r32 r32 encodes correctly`() {
        // andn eax, ecx, edx → VEX.NDS.LZ.0F38.W0 F2 /r
        // 3-byte VEX: C4 [RXB.mmmmm] [W.vvvv.L.pp] F2 [ModRM]
        // R=1,X=1,B=1,map=2 → E2; W=0,vvvv=~1=0xE,L=0,pp=0 → 70; opcode F2; ModRM C2 (eax,edx)
        val asm = X86Assembler()
        asm.andn(eax, ecx, edx as X86Operand32)
        val bytes = asm.toByteArray()
        assertArrayEquals(
            byteArrayOf(0xC4.toByte(), 0xE2.toByte(), 0x70.toByte(), 0xF2.toByte(), 0xC2.toByte()),
            bytes,
            "andn eax, ecx, edx: ${bytes.map { "0x%02X".format(it) }}"
        )
    }

    @Test
    fun `VEX andn r64 r64 r64 encodes correctly`() {
        // andn rax, rcx, rdx → VEX.NDS.LZ.0F38.W1 F2 /r
        // 3-byte VEX: C4 E2 F0 F2 C2
        // W=1 → byte2 high bit set: 0xF0
        val asm = X86Assembler()
        asm.andn(rax, rcx, rdx as X86Operand64)
        val bytes = asm.toByteArray()
        assertArrayEquals(
            byteArrayOf(0xC4.toByte(), 0xE2.toByte(), 0xF0.toByte(), 0xF2.toByte(), 0xC2.toByte()),
            bytes,
            "andn rax, rcx, rdx: ${bytes.map { "0x%02X".format(it) }}"
        )
    }

    @Test
    fun `VEX blsi r32 rm32 encodes correctly`() {
        // blsi ecx, edx → VEX.NDS.LZ.0F38.W0 F3 /3
        // EXT mode: opcodeExt=3 in reg field, vvvv=ecx(1), rm=edx(2)
        // 3-byte VEX: C4 E2 70 F3 DA (mod=11, ext=3, rm=2 → 0xC0|0x18|2 = 0xDA)
        // Wait: vvvv = ~ecx.enc = ~1 = 14 = 0xE → byte2 = 0x70
        val asm = X86Assembler()
        asm.blsi(ecx, edx as X86Operand32)
        val bytes = asm.toByteArray()
        assertArrayEquals(
            byteArrayOf(0xC4.toByte(), 0xE2.toByte(), 0x70.toByte(), 0xF3.toByte(), 0xDA.toByte()),
            bytes,
            "blsi ecx, edx: ${bytes.map { "0x%02X".format(it) }}"
        )
    }

    @Test
    fun `VEX with extended registers encodes REX bits`() {
        // andn r8d, ecx, edx → dest=r8d (enc=8), vvvv=ecx(1), rm=edx(2)
        // R=0 (r8d >= 8), X=1, B=1, map=2 → 0x62
        // W=0, vvvv=~1=0xE, L=0, pp=0 → 0x70
        val asm = X86Assembler()
        asm.andn(r8d, ecx, edx as X86Operand32)
        val bytes = asm.toByteArray()
        assertEquals(0xC4.toByte(), bytes[0], "3-byte VEX prefix")
        assertEquals(0x62.toByte(), bytes[1], "R=0 for r8d: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x70.toByte(), bytes[2], "vvvv=ecx: ${bytes.map { "0x%02X".format(it) }}")
    }

    @Test
    fun `VEX rorx with immediate encodes correctly`() {
        // rorx eax, ecx, 5 → VEX.LZ.0F3A.W0 F0 /r ib
        // mandatoryPrefix=0xF2 → pp=3
        // No vvvv register → vvvv=0xF (unused = all 1s)
        val asm = X86Assembler()
        asm.rorx(eax, ecx as X86Operand32, 5.toByte())
        val bytes = asm.toByteArray()
        assertTrue(bytes.size >= 5, "rorx should be at least 5 bytes: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0xC4.toByte(), bytes[0], "3-byte VEX")
    }

    // --- EVEX encoding tests ---

    private val zmm0 = X86Register.ZMM0 as X86Zmm
    private val zmm1 = X86Register.ZMM1 as X86Zmm
    private val zmm2 = X86Register.ZMM2 as X86Zmm

    @Test
    fun `EVEX vaddps zmm encodes correctly`() {
        // vaddps zmm0, zmm1, zmm2 → EVEX.NDS.512.0F.W0 58 /r
        // Expected: 62 F1 74 48 58 C2
        val asm = X86Assembler()
        asm.vaddps_z(zmm0, zmm1, zmm2)
        val bytes = asm.toByteArray()
        assertEquals(6, bytes.size, "EVEX vaddps should be 6 bytes: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x62.toByte(), bytes[0], "EVEX prefix byte")
        assertEquals(0xF1.toByte(), bytes[1], "EVEX P1: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x74.toByte(), bytes[2], "EVEX P2: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x48.toByte(), bytes[3], "EVEX P3 (512-bit): ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x58.toByte(), bytes[4], "opcode")
        assertEquals(0xC2.toByte(), bytes[5], "ModRM: ${bytes.map { "0x%02X".format(it) }}")
    }

    @Test
    fun `EVEX vaddpd zmm encodes correctly`() {
        // vaddpd zmm0, zmm1, zmm2 → EVEX.NDS.512.66.0F.W1 58 /r
        // Expected: 62 F1 F5 48 58 C2
        // W=1 → byte2 = 0xF5 (W=1,vvvv=~1=0xE,1,pp=01)
        val asm = X86Assembler()
        asm.vaddpd_z(zmm0, zmm1, zmm2)
        val bytes = asm.toByteArray()
        assertEquals(6, bytes.size, "EVEX vaddpd should be 6 bytes: ${bytes.map { "0x%02X".format(it) }}")
        assertEquals(0x62.toByte(), bytes[0])
        assertEquals(0xF1.toByte(), bytes[1])
        assertEquals(0xF5.toByte(), bytes[2], "W=1, pp=01: ${bytes.map { "0x%02X".format(it) }}")
    }
}

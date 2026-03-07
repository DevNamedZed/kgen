package org.kgen.backend.riscv

import org.kgen.backend.riscv.asm.RiscVAssembler
import org.kgen.backend.riscv.disasm.RiscVDisassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVDisassemblerExtendedTest {

    private val disasm = RiscVDisassembler()

    private fun roundTrip(build: RiscVAssembler.() -> Unit): List<RiscVDisassembler.DisassembledInsn> {
        val asm = RiscVAssembler()
        asm.build()
        return disasm.disassemble(asm.toByteArray())
    }

    // --- R-type arithmetic ---

    @Test
    fun disassemblesSlt() {
        val insns = roundTrip { slt(X10, X11, X12) }
        assertEquals("slt", insns[0].mnemonic)
        assertEquals("a0, a1, a2", insns[0].operandsStr)
    }

    @Test
    fun disassemblesSltu() {
        val insns = roundTrip { sltu(X10, X11, X12) }
        assertEquals("sltu", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSll() {
        val insns = roundTrip { sll(X10, X11, X12) }
        assertEquals("sll", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSrl() {
        val insns = roundTrip { srl(X10, X11, X12) }
        assertEquals("srl", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSra() {
        val insns = roundTrip { sra(X10, X11, X12) }
        assertEquals("sra", insns[0].mnemonic)
    }

    // --- I-type arithmetic ---

    @Test
    fun disassemblesAddi() {
        val insns = roundTrip { addi(X10, X11, 42) }
        assertEquals("addi", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("42"))
    }

    @Test
    fun disassemblesSlti() {
        val insns = roundTrip { slti(X10, X11, 10) }
        assertEquals("slti", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSltiu() {
        val insns = roundTrip { sltiu(X10, X11, 10) }
        assertEquals("sltiu", insns[0].mnemonic)
    }

    @Test
    fun disassemblesXori() {
        val insns = roundTrip { xori(X10, X11, 0xFF) }
        assertEquals("xori", insns[0].mnemonic)
    }

    @Test
    fun disassemblesOri() {
        val insns = roundTrip { ori(X10, X11, 0xF0) }
        assertEquals("ori", insns[0].mnemonic)
    }

    @Test
    fun disassemblesAndi() {
        val insns = roundTrip { andi(X10, X11, 0x0F) }
        assertEquals("andi", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSlli() {
        val insns = roundTrip { slli(X10, X11, 4) }
        assertEquals("slli", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSrli() {
        val insns = roundTrip { srli(X10, X11, 4) }
        assertEquals("srli", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSrai() {
        val insns = roundTrip { srai(X10, X11, 4) }
        assertEquals("srai", insns[0].mnemonic)
    }

    // --- Loads ---

    @Test
    fun disassemblesLb() {
        val insns = roundTrip { lb(X10, RiscVMemory(X11, 0)) }
        assertEquals("lb", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLh() {
        val insns = roundTrip { lh(X10, RiscVMemory(X11, 0)) }
        assertEquals("lh", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLw() {
        val insns = roundTrip { lw(X10, RiscVMemory(X11, 0)) }
        assertEquals("lw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLd() {
        val insns = roundTrip { ld(X10, RiscVMemory(X11, 8)) }
        assertEquals("ld", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLbu() {
        val insns = roundTrip { lbu(X10, RiscVMemory(X11, 0)) }
        assertEquals("lbu", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLhu() {
        val insns = roundTrip { lhu(X10, RiscVMemory(X11, 0)) }
        assertEquals("lhu", insns[0].mnemonic)
    }

    @Test
    fun disassemblesLwu() {
        val insns = roundTrip { lwu(X10, RiscVMemory(X11, 0)) }
        assertEquals("lwu", insns[0].mnemonic)
    }

    // --- Stores ---

    @Test
    fun disassemblesSb() {
        val insns = roundTrip { sb(X10, RiscVMemory(X11, 0)) }
        assertEquals("sb", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSh() {
        val insns = roundTrip { sh(X10, RiscVMemory(X11, 0)) }
        assertEquals("sh", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSw() {
        val insns = roundTrip { sw(X10, RiscVMemory(X11, 0)) }
        assertEquals("sw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSd() {
        val insns = roundTrip { sd(X10, RiscVMemory(X11, 8)) }
        assertEquals("sd", insns[0].mnemonic)
    }

    // --- Branches ---

    @Test
    fun disassemblesBeq() {
        val insns = roundTrip { beq(X10, X11, 8) }
        assertEquals("beq", insns[0].mnemonic)
    }

    @Test
    fun disassemblesBne() {
        val insns = roundTrip { bne(X10, X11, 8) }
        assertEquals("bne", insns[0].mnemonic)
    }

    @Test
    fun disassemblesBlt() {
        val insns = roundTrip { blt(X10, X11, 8) }
        assertEquals("blt", insns[0].mnemonic)
    }

    @Test
    fun disassemblesBge() {
        val insns = roundTrip { bge(X10, X11, 8) }
        assertEquals("bge", insns[0].mnemonic)
    }

    @Test
    fun disassemblesBltu() {
        val insns = roundTrip { bltu(X10, X11, 8) }
        assertEquals("bltu", insns[0].mnemonic)
    }

    @Test
    fun disassemblesBgeu() {
        val insns = roundTrip { bgeu(X10, X11, 8) }
        assertEquals("bgeu", insns[0].mnemonic)
    }

    // --- Jump ---

    @Test
    fun disassemblesJal() {
        val insns = roundTrip { jal(X1, 16) }
        assertEquals("jal", insns[0].mnemonic)
    }

    @Test
    fun disassemblesJalr() {
        val insns = roundTrip { jalr(X1, X10, 0) }
        assertEquals("jalr", insns[0].mnemonic)
    }

    // --- Upper immediates ---

    @Test
    fun disassemblesLui() {
        val insns = roundTrip { lui(X10, 0x12345) }
        assertEquals("lui", insns[0].mnemonic)
    }

    @Test
    fun disassemblesAuipc() {
        val insns = roundTrip { auipc(X10, 0x1000) }
        assertEquals("auipc", insns[0].mnemonic)
    }

    // --- RV64I word ops ---

    @Test
    fun disassemblesAddw() {
        val insns = roundTrip { addw(X10, X11, X12) }
        assertEquals("addw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesSubw() {
        val insns = roundTrip { subw(X10, X11, X12) }
        assertEquals("subw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesAddiw() {
        val insns = roundTrip { addiw(X10, X11, 5) }
        assertEquals("addiw", insns[0].mnemonic)
    }

    // --- M extension ---

    @Test
    fun disassemblesMul() {
        val insns = roundTrip { mul(X10, X11, X12) }
        assertEquals("mul", insns[0].mnemonic)
    }

    @Test
    fun disassemblesDiv() {
        val insns = roundTrip { div(X10, X11, X12) }
        assertEquals("div", insns[0].mnemonic)
    }

    @Test
    fun disassemblesRem() {
        val insns = roundTrip { rem(X10, X11, X12) }
        assertEquals("rem", insns[0].mnemonic)
    }

    @Test
    fun disassemblesMulw() {
        val insns = roundTrip { mulw(X10, X11, X12) }
        assertEquals("mulw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesDivw() {
        val insns = roundTrip { divw(X10, X11, X12) }
        assertEquals("divw", insns[0].mnemonic)
    }

    @Test
    fun disassemblesRemw() {
        val insns = roundTrip { remw(X10, X11, X12) }
        assertEquals("remw", insns[0].mnemonic)
    }

    // --- System ---

    @Test
    fun disassemblesEcall() {
        val insns = roundTrip { ecall() }
        assertEquals("ecall", insns[0].mnemonic)
    }

    @Test
    fun disassemblesEbreak() {
        val insns = roundTrip { ebreak() }
        assertEquals("ebreak", insns[0].mnemonic)
    }

    // --- Multiple instructions ---

    @Test
    fun disassemblesMultipleInstructions() {
        val insns = roundTrip {
            addi(X10, X0, 42)
            add(X11, X10, X10)
            sd(X11, RiscVMemory(X2, 0))
        }
        assertEquals(3, insns.size)
        assertTrue(insns[0].mnemonic == "addi" || insns[0].mnemonic == "li", "Expected addi or li: ${insns[0].mnemonic}")
        assertEquals("add", insns[1].mnemonic)
        assertEquals("sd", insns[2].mnemonic)
    }

    @Test
    fun instructionAddresses() {
        val insns = roundTrip {
            add(X10, X11, X12)
            sub(X10, X11, X12)
            mul(X10, X11, X12)
        }
        assertEquals(3, insns.size)
        assertEquals(0L, insns[0].address)
        assertEquals(4L, insns[1].address)
        assertEquals(8L, insns[2].address)
    }

    @Test
    fun instructionSize() {
        val insns = roundTrip { add(X10, X11, X12) }
        assertEquals(4, insns[0].size)
    }

    @Test
    fun emptyInput() {
        val insns = disasm.disassemble(ByteArray(0))
        assertEquals(0, insns.size)
    }

    @Test
    fun truncatedInput() {
        val insns = disasm.disassemble(byteArrayOf(0, 0, 0))
        // Disassembler may pad or partially decode truncated input
        assertTrue(insns.size <= 1)
    }

    @Test
    fun disassemblesWithBaseAddress() {
        val asm = RiscVAssembler()
        asm.add(X10, X11, X12)
        val insns = disasm.disassemble(asm.toByteArray(), baseAddress = 0x80000000L)
        assertEquals(1, insns.size)
        assertEquals(0x80000000L, insns[0].address)
    }

    @Test
    fun disassemblesNegativeImmediate() {
        val insns = roundTrip { addi(X10, X11, -1) }
        assertEquals("addi", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("-1"), "Should show -1: ${insns[0].operandsStr}")
    }

    @Test
    fun disassemblesLoadWithOffset() {
        val insns = roundTrip { ld(X10, RiscVMemory(X11, 16)) }
        assertEquals("ld", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("16"), "Should show offset 16: ${insns[0].operandsStr}")
    }

    @Test
    fun disassemblesStoreWithOffset() {
        val insns = roundTrip { sd(X10, RiscVMemory(X11, 24)) }
        assertEquals("sd", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("24"), "Should show offset 24: ${insns[0].operandsStr}")
    }
}

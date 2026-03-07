package org.kgen.backend.riscv

import org.kgen.backend.riscv.asm.RiscVAssembler
import org.kgen.backend.riscv.disasm.RiscVDisassembler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RiscVDisassemblerTest {

    private val disasm = RiscVDisassembler()

    private fun roundTrip(build: RiscVAssembler.() -> Unit): List<RiscVDisassembler.DisassembledInsn> {
        val asm = RiscVAssembler()
        asm.build()
        return disasm.disassemble(asm.toByteArray())
    }

    // --- Arithmetic ---

    @Test
    fun `disassembles add`() {
        val insns = roundTrip { add(X10, X11, X12) }
        assertEquals(1, insns.size)
        assertEquals("add", insns[0].mnemonic)
        assertEquals("a0, a1, a2", insns[0].operandsStr)
    }

    @Test
    fun `disassembles sub`() {
        val insns = roundTrip { sub(X10, X11, X12) }
        assertEquals("sub", insns[0].mnemonic)
        assertEquals("a0, a1, a2", insns[0].operandsStr)
    }

    @Test
    fun `disassembles and or xor`() {
        val insns = roundTrip { and(X5, X6, X7); or(X5, X6, X7); xor(X5, X6, X7) }
        assertEquals("and", insns[0].mnemonic)
        assertEquals("or", insns[1].mnemonic)
        assertEquals("xor", insns[2].mnemonic)
    }

    @Test
    fun `disassembles shifts`() {
        val insns = roundTrip { sll(X10, X11, X12); srl(X10, X11, X12); sra(X10, X11, X12) }
        assertEquals("sll", insns[0].mnemonic)
        assertEquals("srl", insns[1].mnemonic)
        assertEquals("sra", insns[2].mnemonic)
    }

    // --- Immediates ---

    @Test
    fun `disassembles addi`() {
        val insns = roundTrip { addi(X10, X11, 42) }
        assertEquals("addi", insns[0].mnemonic)
        assertEquals("a0, a1, 42", insns[0].operandsStr)
    }

    @Test
    fun `disassembles slli srli srai`() {
        val insns = roundTrip { slli(X10, X11, 3); srli(X10, X11, 5); srai(X10, X11, 4) }
        assertEquals("slli", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("3"))
        assertEquals("srli", insns[1].mnemonic)
        assertEquals("srai", insns[2].mnemonic)
    }

    @Test
    fun `disassembles lui`() {
        val insns = roundTrip { lui(X10, 0x12345) }
        assertEquals("lui", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("12345"))
    }

    @Test
    fun `disassembles auipc`() {
        val insns = roundTrip { auipc(X10, 1) }
        assertEquals("auipc", insns[0].mnemonic)
    }

    // --- Loads/Stores ---

    @Test
    fun `disassembles ld`() {
        val insns = roundTrip { ld(X10, RiscVMemory(X2, 16)) }
        assertEquals("ld", insns[0].mnemonic)
        assertEquals("a0, 16(sp)", insns[0].operandsStr)
    }

    @Test
    fun `disassembles sd`() {
        val insns = roundTrip { sd(X1, RiscVMemory(X2, -8)) }
        assertEquals("sd", insns[0].mnemonic)
        assertEquals("ra, -8(sp)", insns[0].operandsStr)
    }

    @Test
    fun `disassembles lw sw lb sb`() {
        val insns = roundTrip {
            lw(X10, RiscVMemory(X8, 0))
            sw(X10, RiscVMemory(X8, 4))
            lb(X10, RiscVMemory(X8, 0))
            sb(X10, RiscVMemory(X8, 0))
        }
        assertEquals("lw", insns[0].mnemonic)
        assertEquals("sw", insns[1].mnemonic)
        assertEquals("lb", insns[2].mnemonic)
        assertEquals("sb", insns[3].mnemonic)
    }

    // --- Branches ---

    @Test
    fun `disassembles beq`() {
        val insns = roundTrip { beq(X10, X11, 8) }
        assertEquals("beq", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("a0"))
        assertTrue(insns[0].operandsStr.contains("a1"))
    }

    @Test
    fun `disassembles bne blt bge`() {
        val insns = roundTrip { bne(X10, X11, 4); blt(X10, X11, 4); bge(X10, X11, 4) }
        assertEquals("bne", insns[0].mnemonic)
        assertEquals("blt", insns[1].mnemonic)
        assertEquals("bge", insns[2].mnemonic)
    }

    // --- Jumps ---

    @Test
    fun `disassembles jal`() {
        val insns = roundTrip { jal(X1, 100) }
        assertEquals("jal", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.contains("ra"))
    }

    @Test
    fun `disassembles jalr`() {
        val insns = roundTrip { jalr(X1, X10, 0) }
        assertEquals("jalr", insns[0].mnemonic)
    }

    // --- M extension ---

    @Test
    fun `disassembles mul div rem`() {
        val insns = roundTrip { mul(X10, X11, X12); div(X10, X11, X12); rem(X10, X11, X12) }
        assertEquals("mul", insns[0].mnemonic)
        assertEquals("div", insns[1].mnemonic)
        assertEquals("rem", insns[2].mnemonic)
    }

    @Test
    fun `disassembles mulw divw remw`() {
        val insns = roundTrip { mulw(X10, X11, X12); divw(X10, X11, X12); remw(X10, X11, X12) }
        assertEquals("mulw", insns[0].mnemonic)
        assertEquals("divw", insns[1].mnemonic)
        assertEquals("remw", insns[2].mnemonic)
    }

    // --- Pseudo-instructions ---

    @Test
    fun `disassembles nop`() {
        val insns = roundTrip { nop() }
        assertEquals("nop", insns[0].mnemonic)
    }

    @Test
    fun `disassembles mv`() {
        val insns = roundTrip { mv(X10, X11) }
        assertEquals("mv", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles li`() {
        val insns = roundTrip { li(X10, 42) }
        assertEquals("li", insns[0].mnemonic)
        assertEquals("a0, 42", insns[0].operandsStr)
    }

    @Test
    fun `disassembles neg`() {
        val insns = roundTrip { neg(X10, X11) }
        assertEquals("neg", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles not`() {
        val insns = roundTrip { not(X10, X11) }
        assertEquals("not", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }

    @Test
    fun `disassembles ret`() {
        val insns = roundTrip { ret() }
        assertEquals("ret", insns[0].mnemonic)
    }

    @Test
    fun `disassembles j`() {
        val insns = roundTrip { j(100) }
        assertEquals("j", insns[0].mnemonic)
    }

    // --- System ---

    @Test
    fun `disassembles ecall ebreak`() {
        val insns = roundTrip { ecall(); ebreak() }
        assertEquals("ecall", insns[0].mnemonic)
        assertEquals("ebreak", insns[1].mnemonic)
    }

    // --- Word instructions ---

    @Test
    fun `disassembles addw subw`() {
        val insns = roundTrip { addw(X10, X11, X12); subw(X10, X11, X12) }
        assertEquals("addw", insns[0].mnemonic)
        assertEquals("subw", insns[1].mnemonic)
    }

    @Test
    fun `disassembles addiw`() {
        val insns = roundTrip { addiw(X10, X11, 5) }
        assertEquals("addiw", insns[0].mnemonic)
    }

    // --- Full function round-trip ---

    @Test
    fun `round-trips complete function`() {
        val insns = roundTrip {
            addi(X2, X2, -16)       // allocate stack
            sd(X1, RiscVMemory(X2, 8)) // save ra
            add(X10, X10, X11)      // result = a + b
            ld(X1, RiscVMemory(X2, 8)) // restore ra
            addi(X2, X2, 16)        // deallocate stack
            ret()
        }
        assertEquals(6, insns.size)
        assertEquals("addi", insns[0].mnemonic)
        assertEquals("sd", insns[1].mnemonic)
        assertEquals("add", insns[2].mnemonic)
        assertEquals("ld", insns[3].mnemonic)
        assertEquals("addi", insns[4].mnemonic)
        assertEquals("ret", insns[5].mnemonic)
    }

    @Test
    fun `disassembles beqz bnez pseudos`() {
        val insns = roundTrip { beq(X10, X0, 8); bne(X10, X0, 8) }
        assertEquals("beqz", insns[0].mnemonic)
        assertTrue(insns[0].operandsStr.startsWith("a0"))
        assertEquals("bnez", insns[1].mnemonic)
    }

    @Test
    fun `disassembles seqz`() {
        val insns = roundTrip { seqz(X10, X11) }
        assertEquals("seqz", insns[0].mnemonic)
        assertEquals("a0, a1", insns[0].operandsStr)
    }
}

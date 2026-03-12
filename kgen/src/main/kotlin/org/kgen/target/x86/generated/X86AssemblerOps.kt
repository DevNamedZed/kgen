// Generated — do not edit
package org.kgen.target.x86

/**
 * Generated assembler dispatch methods for x86-64.
 *
 * Each method corresponds to an x86 instruction mnemonic.
 * Overloads select the correct encoding based on operand types.
 * The abstract `encode*` methods are implemented by the hand-written assembler.
 */
abstract class X86AssemblerOps {

    // Encoding primitives — implemented by X86Assembler
    protected abstract fun encodeLegacy(enc: X86EncodingInfo, vararg operands: Any)
    protected abstract fun encodeVex(enc: X86EncodingInfo, vararg operands: Any)
    protected abstract fun encodeEvex(enc: X86EncodingInfo, vararg operands: Any)

    /** Check if a register is the accumulator (AL/AX/EAX/RAX — encoding 0). */
    private fun isAccumulator(r: Any): Boolean {
        val reg = r as? X86Register
        return reg != null && reg.encoding() == 0
    }

    /** Move: r8, r/m8 */
    fun mov(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(138), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move: r/m8, r8 */
    fun mov(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(136), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Move: r16, r/m16 */
    fun mov(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(139), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Move: r/m16, r16 */
    fun mov(mem: X86Memory, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(137), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r2, mem)
    }

    /** Move: r32, r/m32 */
    fun mov(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(139), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move: r/m32, r32 */
    fun mov(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(137), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Move: r64, r/m64 */
    fun mov(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(139), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Move: r/m64, r64 */
    fun mov(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(137), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Move: r8, imm8 */
    fun mov(r1: X86Register8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(176), plusReg = true), r1, imm)
    }

    /** Move: r16, imm16 */
    fun mov(r1: X86Register16, imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(184), mandatoryPrefix = 0x66, plusReg = true), r1, imm)
    }

    /** Move: r32, imm32 */
    fun mov(r1: X86Register32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(184), plusReg = true), r1, imm)
    }

    /** Move: r64, imm64 */
    fun mov(r1: X86Register64, imm: Long) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(184), rexW = true, plusReg = true), r1, imm)
    }

    /** Move: r/m8, imm8 */
    fun mov(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(198), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Move: r/m16, imm16 */
    fun mov(rm1: X86Operand16, imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(199), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1, imm)
    }

    /** Move: r/m32, imm32 */
    fun mov(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(199), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Move: r/m64, imm32 */
    fun mov(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(199), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, imm)
    }

    /** Move: moffs8, al */
    fun mov(offset: Long, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(162)), offset, r2)
    }

    /** Move: moffs64, rax */
    fun mov(offset: Long, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(163), rexW = true), offset, r2)
    }

    /** Move: r/m16, sreg */
    fun mov(rm1: X86Operand16, sreg: X86SegReg) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(140), modrmMode = ModrmMode.REG), rm1, sreg)
    }

    /** Move: sreg, r/m16 */
    fun mov(sreg: X86SegReg, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(142), modrmMode = ModrmMode.REG), sreg, rm2)
    }

    /** Move with zero-extend: r16, r/m8 */
    fun movzx(r1: X86Register16, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 182), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Move with zero-extend: r32, r/m8 */
    fun movzx(r1: X86Register32, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 182), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move with zero-extend: r64, r/m8 */
    fun movzx(r1: X86Register64, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 182), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Move with zero-extend: r32, r/m16 */
    fun movzx(r1: X86Register32, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 183), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move with zero-extend: r64, r/m16 */
    fun movzx(r1: X86Register64, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 183), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Move with sign-extend: r16, r/m8 */
    fun movsx(r1: X86Register16, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 190), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Move with sign-extend: r32, r/m8 */
    fun movsx(r1: X86Register32, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 190), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move with sign-extend: r64, r/m8 */
    fun movsx(r1: X86Register64, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 190), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Move with sign-extend: r32, r/m16 */
    fun movsx(r1: X86Register32, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 191), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Move with sign-extend: r64, r/m16 */
    fun movsx(r1: X86Register64, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 191), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Move with sign-extend doubleword to quadword: r64, r/m32 */
    fun movsxd(r1: X86Register64, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(99), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Load effective address: r16, m16 */
    fun lea(r1: X86Register16, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(141), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, mem)
    }

    /** Load effective address: r32, m32 */
    fun lea(r1: X86Register32, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(141), modrmMode = ModrmMode.REG), r1, mem)
    }

    /** Load effective address: r64, m64 */
    fun lea(r1: X86Register64, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(141), modrmMode = ModrmMode.REG, rexW = true), r1, mem)
    }

    /** Exchange: r/m8, r8 */
    fun xchg(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(134), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Exchange: r/m16, r16 */
    fun xchg(rm1: X86Operand16, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(135), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r2, rm1)
    }

    /** Exchange: ax, r16 (accumulator short form) / r/m16, r16 */
    fun xchg(r1: X86Register16, r2: X86Register16) {
        if (isAccumulator(r1) || isAccumulator(r2)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(144), mandatoryPrefix = 0x66, plusReg = true), r1, r2)
        else xchg(r1 as X86Operand16, r2)
    }

    /** Exchange: r/m32, r32 */
    fun xchg(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(135), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Exchange: eax, r32 (accumulator short form) / r/m32, r32 */
    fun xchg(r1: X86Register32, r2: X86Register32) {
        if (isAccumulator(r1) || isAccumulator(r2)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(144), plusReg = true), r1, r2)
        else xchg(r1 as X86Operand32, r2)
    }

    /** Exchange: r/m64, r64 */
    fun xchg(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(135), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Exchange: rax, r64 (accumulator short form) / r/m64, r64 */
    fun xchg(r1: X86Register64, r2: X86Register64) {
        if (isAccumulator(r1) || isAccumulator(r2)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(144), rexW = true, plusReg = true), r1, r2)
        else xchg(r1 as X86Operand64, r2)
    }

    /** Byte swap: r32 */
    fun bswap(r1: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 200), plusReg = true), r1)
    }

    /** Byte swap: r64 */
    fun bswap(r1: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 200), rexW = true, plusReg = true), r1)
    }

    /** Compare and exchange: r/m8, r8 */
    fun cmpxchg(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 176), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Compare and exchange: r/m16, r16 */
    fun cmpxchg(rm1: X86Operand16, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 177), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r2, rm1)
    }

    /** Compare and exchange: r/m32, r32 */
    fun cmpxchg(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 177), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Compare and exchange: r/m64, r64 */
    fun cmpxchg(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 177), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Compare and exchange 8 bytes: m64 */
    fun cmpxchg8b(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 1), mem)
    }

    /** Compare and exchange 16 bytes: m128 */
    fun cmpxchg16b(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), mem)
    }

    /** Exchange and add: r/m8, r8 */
    fun xadd(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 192), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Exchange and add: r/m16, r16 */
    fun xadd(rm1: X86Operand16, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 193), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r2, rm1)
    }

    /** Exchange and add: r/m32, r32 */
    fun xadd(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 193), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Exchange and add: r/m64, r64 */
    fun xadd(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 193), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Move data after swapping bytes: r16, m16 */
    fun movbe(r1: X86Register16, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 240), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, mem)
    }

    /** Move data after swapping bytes: r32, m32 */
    fun movbe(r1: X86Register32, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 240), modrmMode = ModrmMode.REG), r1, mem)
    }

    /** Move data after swapping bytes: r64, m64 */
    fun movbe(r1: X86Register64, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 240), modrmMode = ModrmMode.REG, rexW = true), r1, mem)
    }

    /** Move data after swapping bytes: m16, r16 */
    fun movbe(mem: X86Memory, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), mem, r2)
    }

    /** Move data after swapping bytes: m32, r32 */
    fun movbe(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG), mem, r2)
    }

    /** Move data after swapping bytes: m64, r64 */
    fun movbe(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, rexW = true), mem, r2)
    }

    /** Conditional move if above (CF=0, ZF=0): r16, r/m16 */
    fun cmova(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 71), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if above (CF=0, ZF=0): r32, r/m32 */
    fun cmova(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 71), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if above (CF=0, ZF=0): r64, r/m64 */
    fun cmova(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 71), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if above or equal (CF=0): r16, r/m16 */
    fun cmovae(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 67), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if above or equal (CF=0): r32, r/m32 */
    fun cmovae(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 67), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if above or equal (CF=0): r64, r/m64 */
    fun cmovae(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 67), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if below (CF=1): r16, r/m16 */
    fun cmovb(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 66), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if below (CF=1): r32, r/m32 */
    fun cmovb(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 66), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if below (CF=1): r64, r/m64 */
    fun cmovb(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 66), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if below or equal (CF=1 or ZF=1): r16, r/m16 */
    fun cmovbe(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 70), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if below or equal (CF=1 or ZF=1): r32, r/m32 */
    fun cmovbe(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 70), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if below or equal (CF=1 or ZF=1): r64, r/m64 */
    fun cmovbe(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 70), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if equal (ZF=1): r16, r/m16 */
    fun cmove(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 68), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if equal (ZF=1): r32, r/m32 */
    fun cmove(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 68), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if equal (ZF=1): r64, r/m64 */
    fun cmove(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 68), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if greater (ZF=0, SF=OF): r16, r/m16 */
    fun cmovg(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 79), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if greater (ZF=0, SF=OF): r32, r/m32 */
    fun cmovg(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 79), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if greater (ZF=0, SF=OF): r64, r/m64 */
    fun cmovg(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 79), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if greater or equal (SF=OF): r16, r/m16 */
    fun cmovge(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 77), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if greater or equal (SF=OF): r32, r/m32 */
    fun cmovge(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 77), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if greater or equal (SF=OF): r64, r/m64 */
    fun cmovge(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 77), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if less (SF!=OF): r16, r/m16 */
    fun cmovl(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 76), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if less (SF!=OF): r32, r/m32 */
    fun cmovl(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 76), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if less (SF!=OF): r64, r/m64 */
    fun cmovl(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 76), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if less or equal (ZF=1 or SF!=OF): r16, r/m16 */
    fun cmovle(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 78), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if less or equal (ZF=1 or SF!=OF): r32, r/m32 */
    fun cmovle(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 78), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if less or equal (ZF=1 or SF!=OF): r64, r/m64 */
    fun cmovle(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 78), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if not equal (ZF=0): r16, r/m16 */
    fun cmovne(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 69), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if not equal (ZF=0): r32, r/m32 */
    fun cmovne(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 69), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if not equal (ZF=0): r64, r/m64 */
    fun cmovne(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 69), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if not overflow (OF=0): r16, r/m16 */
    fun cmovno(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 65), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if not overflow (OF=0): r32, r/m32 */
    fun cmovno(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 65), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if not overflow (OF=0): r64, r/m64 */
    fun cmovno(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 65), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if not parity (PF=0): r16, r/m16 */
    fun cmovnp(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 75), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if not parity (PF=0): r32, r/m32 */
    fun cmovnp(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 75), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if not parity (PF=0): r64, r/m64 */
    fun cmovnp(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 75), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if not sign (SF=0): r16, r/m16 */
    fun cmovns(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 73), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if not sign (SF=0): r32, r/m32 */
    fun cmovns(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 73), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if not sign (SF=0): r64, r/m64 */
    fun cmovns(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 73), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if overflow (OF=1): r16, r/m16 */
    fun cmovo(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 64), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if overflow (OF=1): r32, r/m32 */
    fun cmovo(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 64), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if overflow (OF=1): r64, r/m64 */
    fun cmovo(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 64), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if parity (PF=1): r16, r/m16 */
    fun cmovp(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 74), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if parity (PF=1): r32, r/m32 */
    fun cmovp(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 74), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if parity (PF=1): r64, r/m64 */
    fun cmovp(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 74), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Conditional move if sign (SF=1): r16, r/m16 */
    fun cmovs(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 72), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Conditional move if sign (SF=1): r32, r/m32 */
    fun cmovs(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 72), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Conditional move if sign (SF=1): r64, r/m64 */
    fun cmovs(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 72), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Convert byte to word (AL -> AX):  */
    fun cbw() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(152), mandatoryPrefix = 0x66))
    }

    /** Convert word to doubleword (AX -> EAX):  */
    fun cwde() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(152)))
    }

    /** Convert doubleword to quadword (EAX -> RAX):  */
    fun cdqe() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(152), rexW = true))
    }

    /** Convert word to doubleword (AX -> DX:AX):  */
    fun cwd() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(153), mandatoryPrefix = 0x66))
    }

    /** Convert doubleword to quadword (EAX -> EDX:EAX):  */
    fun cdq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(153)))
    }

    /** Convert quadword to double-quadword (RAX -> RDX:RAX):  */
    fun cqo() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(153), rexW = true))
    }

    /** Add: r/m8, imm8 */
    fun add(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Add: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun add(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(4)), r1, imm)
        else add(r1 as X86Operand8, imm)
    }

    /** Add: r/m16, imm16 */
    fun add(rm1: X86Operand16, imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1, imm)
    }

    /** Add: ax, imm16 (accumulator short form) / r/m16, imm16 */
    fun add(r1: X86Register16, imm: Short) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(5), mandatoryPrefix = 0x66), r1, imm)
        else add(r1 as X86Operand16, imm)
    }

    /** Add: r/m32, imm32 */
    fun add(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Add: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun add(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(5)), r1, imm)
        else add(r1 as X86Operand32, imm)
    }

    /** Add: r/m64, imm32 */
    fun add(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, imm)
    }

    /** Add: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun add(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(5), rexW = true), r1, imm)
        else add(r1 as X86Operand64, imm)
    }

    /** Add: r/m16, imm8 */
    fun add(rm1: X86Operand16, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1, imm)
    }

    /** Add: r/m32, imm8 */
    fun add(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Add: r/m64, imm8 */
    fun add(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, imm)
    }

    /** Add: r/m8, r8 */
    fun add(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(0), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Add: r/m16, r16 */
    fun add(mem: X86Memory, r2: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(1), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r2, mem)
    }

    /** Add: r/m32, r32 */
    fun add(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(1), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Add: r/m64, r64 */
    fun add(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(1), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Add: r8, r/m8 */
    fun add(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(2), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Add: r16, r/m16 */
    fun add(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(3), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Add: r32, r/m32 */
    fun add(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(3), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Add: r64, r/m64 */
    fun add(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(3), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Add with carry: r/m8, imm8 */
    fun adc(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, imm)
    }

    /** Add with carry: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun adc(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(20)), r1, imm)
        else adc(r1 as X86Operand8, imm)
    }

    /** Add with carry: r/m32, imm32 */
    fun adc(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, imm)
    }

    /** Add with carry: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun adc(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(21)), r1, imm)
        else adc(r1 as X86Operand32, imm)
    }

    /** Add with carry: r/m64, imm32 */
    fun adc(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1, imm)
    }

    /** Add with carry: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun adc(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(21), rexW = true), r1, imm)
        else adc(r1 as X86Operand64, imm)
    }

    /** Add with carry: r/m32, imm8 */
    fun adc(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, imm)
    }

    /** Add with carry: r/m64, imm8 */
    fun adc(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1, imm)
    }

    /** Add with carry: r/m8, r8 */
    fun adc(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(16), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Add with carry: r/m32, r32 */
    fun adc(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(17), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Add with carry: r/m64, r64 */
    fun adc(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(17), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Add with carry: r8, r/m8 */
    fun adc(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(18), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Add with carry: r32, r/m32 */
    fun adc(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(19), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Add with carry: r64, r/m64 */
    fun adc(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(19), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Subtract: r/m8, imm8 */
    fun sub(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Subtract: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun sub(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(44)), r1, imm)
        else sub(r1 as X86Operand8, imm)
    }

    /** Subtract: r/m32, imm32 */
    fun sub(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Subtract: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun sub(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(45)), r1, imm)
        else sub(r1 as X86Operand32, imm)
    }

    /** Subtract: r/m64, imm32 */
    fun sub(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1, imm)
    }

    /** Subtract: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun sub(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(45), rexW = true), r1, imm)
        else sub(r1 as X86Operand64, imm)
    }

    /** Subtract: r/m32, imm8 */
    fun sub(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Subtract: r/m64, imm8 */
    fun sub(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1, imm)
    }

    /** Subtract: r/m8, r8 */
    fun sub(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(40), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Subtract: r/m32, r32 */
    fun sub(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(41), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Subtract: r/m64, r64 */
    fun sub(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(41), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Subtract: r8, r/m8 */
    fun sub(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(42), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Subtract: r32, r/m32 */
    fun sub(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(43), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Subtract: r64, r/m64 */
    fun sub(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(43), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Subtract with borrow: r/m8, imm8 */
    fun sbb(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, imm)
    }

    /** Subtract with borrow: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun sbb(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(28)), r1, imm)
        else sbb(r1 as X86Operand8, imm)
    }

    /** Subtract with borrow: r/m32, imm32 */
    fun sbb(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, imm)
    }

    /** Subtract with borrow: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun sbb(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(29)), r1, imm)
        else sbb(r1 as X86Operand32, imm)
    }

    /** Subtract with borrow: r/m64, imm32 */
    fun sbb(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1, imm)
    }

    /** Subtract with borrow: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun sbb(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(29), rexW = true), r1, imm)
        else sbb(r1 as X86Operand64, imm)
    }

    /** Subtract with borrow: r/m32, imm8 */
    fun sbb(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, imm)
    }

    /** Subtract with borrow: r/m64, imm8 */
    fun sbb(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1, imm)
    }

    /** Subtract with borrow: r/m8, r8 */
    fun sbb(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(24), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Subtract with borrow: r/m32, r32 */
    fun sbb(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(25), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Subtract with borrow: r/m64, r64 */
    fun sbb(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(25), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Subtract with borrow: r8, r/m8 */
    fun sbb(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(26), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Subtract with borrow: r32, r/m32 */
    fun sbb(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(27), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Subtract with borrow: r64, r/m64 */
    fun sbb(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(27), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Signed multiply: r/m8 */
    fun imul(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1)
    }

    /** Signed multiply: r/m32 */
    fun imul(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1)
    }

    /** Signed multiply: r/m64 */
    fun imul(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1)
    }

    /** Signed multiply: r16, r/m16 */
    fun imul(r1: X86Register16, rm2: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 175), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Signed multiply: r32, r/m32 */
    fun imul(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 175), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Signed multiply: r64, r/m64 */
    fun imul(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 175), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Signed multiply: r16, r/m16, imm8 */
    fun imul(r1: X86Register16, rm2: X86Operand16, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(107), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2, imm)
    }

    /** Signed multiply: r32, r/m32, imm8 */
    fun imul(r1: X86Register32, rm2: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(107), modrmMode = ModrmMode.REG), r1, rm2, imm)
    }

    /** Signed multiply: r64, r/m64, imm8 */
    fun imul(r1: X86Register64, rm2: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(107), modrmMode = ModrmMode.REG, rexW = true), r1, rm2, imm)
    }

    /** Signed multiply: r16, r/m16, imm16 */
    fun imul(r1: X86Register16, rm2: X86Operand16, imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(105), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2, imm)
    }

    /** Signed multiply: r32, r/m32, imm32 */
    fun imul(r1: X86Register32, rm2: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(105), modrmMode = ModrmMode.REG), r1, rm2, imm)
    }

    /** Signed multiply: r64, r/m64, imm32 */
    fun imul(r1: X86Register64, rm2: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(105), modrmMode = ModrmMode.REG, rexW = true), r1, rm2, imm)
    }

    /** Unsigned multiply: r/m8 */
    fun mul(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1)
    }

    /** Unsigned multiply: r/m32 */
    fun mul(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1)
    }

    /** Unsigned multiply: r/m64 */
    fun mul(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1)
    }

    /** Unsigned divide: r/m8 */
    fun div(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1)
    }

    /** Unsigned divide: r/m32 */
    fun div(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1)
    }

    /** Unsigned divide: r/m64 */
    fun div(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 6, rexW = true), rm1)
    }

    /** Signed divide: r/m8 */
    fun idiv(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1)
    }

    /** Signed divide: r/m32 */
    fun idiv(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1)
    }

    /** Signed divide: r/m64 */
    fun idiv(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1)
    }

    /** Two's complement negation: r/m8 */
    fun neg(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1)
    }

    /** Two's complement negation: r/m16 */
    fun neg(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 3, mandatoryPrefix = 0x66), rm1)
    }

    /** Two's complement negation: r/m32 */
    fun neg(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1)
    }

    /** Two's complement negation: r/m64 */
    fun neg(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1)
    }

    /** Increment by 1: r/m8 */
    fun inc(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(254), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Increment by 1: r/m16 */
    fun inc(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1)
    }

    /** Increment by 1: r/m32 */
    fun inc(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Increment by 1: r/m64 */
    fun inc(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1)
    }

    /** Decrement by 1: r/m8 */
    fun dec(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(254), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1)
    }

    /** Decrement by 1: r/m16 */
    fun dec(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 1, mandatoryPrefix = 0x66), rm1)
    }

    /** Decrement by 1: r/m32 */
    fun dec(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1)
    }

    /** Decrement by 1: r/m64 */
    fun dec(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1)
    }

    /** Unsigned integer addition with carry flag: r32, r/m32 */
    fun adcx(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, rm2)
    }

    /** Unsigned integer addition with carry flag: r64, r/m64 */
    fun adcx(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, rexW = true), r1, rm2)
    }

    /** Unsigned integer addition with overflow flag: r32, r/m32 */
    fun adox(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, rm2)
    }

    /** Unsigned integer addition with overflow flag: r64, r/m64 */
    fun adox(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, rm2)
    }

    /** Logical AND: r/m8, imm8 */
    fun and_(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Logical AND: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun and_(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(36)), r1, imm)
        else and_(r1 as X86Operand8, imm)
    }

    /** Logical AND: r/m32, imm32 */
    fun and_(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Logical AND: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun and_(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(37)), r1, imm)
        else and_(r1 as X86Operand32, imm)
    }

    /** Logical AND: r/m64, imm32 */
    fun and_(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1, imm)
    }

    /** Logical AND: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun and_(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(37), rexW = true), r1, imm)
        else and_(r1 as X86Operand64, imm)
    }

    /** Logical AND: r/m32, imm8 */
    fun and_(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Logical AND: r/m64, imm8 */
    fun and_(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1, imm)
    }

    /** Logical AND: r/m8, r8 */
    fun and_(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(32), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical AND: r/m32, r32 */
    fun and_(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(33), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical AND: r/m64, r64 */
    fun and_(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(33), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Logical AND: r8, r/m8 */
    fun and_(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(34), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical AND: r32, r/m32 */
    fun and_(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(35), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical AND: r64, r/m64 */
    fun and_(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(35), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Logical OR: r/m8, imm8 */
    fun or_(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, imm)
    }

    /** Logical OR: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun or_(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(12)), r1, imm)
        else or_(r1 as X86Operand8, imm)
    }

    /** Logical OR: r/m32, imm32 */
    fun or_(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, imm)
    }

    /** Logical OR: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun or_(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(13)), r1, imm)
        else or_(r1 as X86Operand32, imm)
    }

    /** Logical OR: r/m64, imm32 */
    fun or_(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1, imm)
    }

    /** Logical OR: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun or_(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(13), rexW = true), r1, imm)
        else or_(r1 as X86Operand64, imm)
    }

    /** Logical OR: r/m32, imm8 */
    fun or_(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, imm)
    }

    /** Logical OR: r/m64, imm8 */
    fun or_(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1, imm)
    }

    /** Logical OR: r/m8, r8 */
    fun or_(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(8), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical OR: r/m32, r32 */
    fun or_(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(9), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical OR: r/m64, r64 */
    fun or_(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(9), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Logical OR: r8, r/m8 */
    fun or_(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(10), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical OR: r32, r/m32 */
    fun or_(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(11), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical OR: r64, r/m64 */
    fun or_(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(11), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Logical XOR: r/m8, imm8 */
    fun xor_(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1, imm)
    }

    /** Logical XOR: al, imm8 (accumulator short form) / r/m8, imm8 */
    fun xor_(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(52)), r1, imm)
        else xor_(r1 as X86Operand8, imm)
    }

    /** Logical XOR: r/m32, imm32 */
    fun xor_(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1, imm)
    }

    /** Logical XOR: eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun xor_(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(53)), r1, imm)
        else xor_(r1 as X86Operand32, imm)
    }

    /** Logical XOR: r/m64, imm32 */
    fun xor_(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 6, rexW = true), rm1, imm)
    }

    /** Logical XOR: rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun xor_(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(53), rexW = true), r1, imm)
        else xor_(r1 as X86Operand64, imm)
    }

    /** Logical XOR: r/m32, imm8 */
    fun xor_(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1, imm)
    }

    /** Logical XOR: r/m64, imm8 */
    fun xor_(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 6, rexW = true), rm1, imm)
    }

    /** Logical XOR: r/m8, r8 */
    fun xor_(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(48), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical XOR: r/m32, r32 */
    fun xor_(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(49), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Logical XOR: r/m64, r64 */
    fun xor_(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(49), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Logical XOR: r8, r/m8 */
    fun xor_(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(50), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical XOR: r32, r/m32 */
    fun xor_(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(51), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Logical XOR: r64, r/m64 */
    fun xor_(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(51), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** One's complement negation: r/m8 */
    fun not_(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1)
    }

    /** One's complement negation: r/m16 */
    fun not_(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 2, mandatoryPrefix = 0x66), rm1)
    }

    /** One's complement negation: r/m32 */
    fun not_(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1)
    }

    /** One's complement negation: r/m64 */
    fun not_(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1)
    }

    /** Logical compare (AND without storing result): r/m8, imm8 */
    fun test(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Logical compare (AND without storing result): al, imm8 (accumulator short form) / r/m8, imm8 */
    fun test(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(168)), r1, imm)
        else test(r1 as X86Operand8, imm)
    }

    /** Logical compare (AND without storing result): r/m32, imm32 */
    fun test(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Logical compare (AND without storing result): eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun test(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(169)), r1, imm)
        else test(r1 as X86Operand32, imm)
    }

    /** Logical compare (AND without storing result): r/m64, imm32 */
    fun test(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, imm)
    }

    /** Logical compare (AND without storing result): rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun test(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(169), rexW = true), r1, imm)
        else test(r1 as X86Operand64, imm)
    }

    /** Logical compare (AND without storing result): r/m8, r8 */
    fun test(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(132), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Logical compare (AND without storing result): r/m32, r32 */
    fun test(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(133), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Logical compare (AND without storing result): r/m64, r64 */
    fun test(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(133), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Shift left: r/m8, 1 */
    fun shl(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1)
    }

    /** Shift left: r/m8, cl */
    fun shl(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, r2)
    }

    /** Shift left: r/m8, imm8 */
    fun shl(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Shift left: r/m32, 1 */
    fun shl(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1)
    }

    /** Shift left: r/m32, cl */
    fun shl(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, r2)
    }

    /** Shift left: r/m32, imm8 */
    fun shl(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Shift left: r/m64, 1 */
    fun shl(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1)
    }

    /** Shift left: r/m64, cl */
    fun shl(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1, r2)
    }

    /** Shift left: r/m64, imm8 */
    fun shl(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1, imm)
    }

    /** Shift right (unsigned): r/m8, 1 */
    fun shr(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1)
    }

    /** Shift right (unsigned): r/m8, cl */
    fun shr(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, r2)
    }

    /** Shift right (unsigned): r/m8, imm8 */
    fun shr(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Shift right (unsigned): r/m32, 1 */
    fun shr(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1)
    }

    /** Shift right (unsigned): r/m32, cl */
    fun shr(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, r2)
    }

    /** Shift right (unsigned): r/m32, imm8 */
    fun shr(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Shift right (unsigned): r/m64, 1 */
    fun shr(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1)
    }

    /** Shift right (unsigned): r/m64, cl */
    fun shr(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1, r2)
    }

    /** Shift right (unsigned): r/m64, imm8 */
    fun shr(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1, imm)
    }

    /** Shift right (signed/arithmetic): r/m8, 1 */
    fun sar(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1)
    }

    /** Shift right (signed/arithmetic): r/m8, cl */
    fun sar(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, r2)
    }

    /** Shift right (signed/arithmetic): r/m8, imm8 */
    fun sar(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Shift right (signed/arithmetic): r/m32, 1 */
    fun sar(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1)
    }

    /** Shift right (signed/arithmetic): r/m32, cl */
    fun sar(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, r2)
    }

    /** Shift right (signed/arithmetic): r/m32, imm8 */
    fun sar(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Shift right (signed/arithmetic): r/m64, 1 */
    fun sar(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1)
    }

    /** Shift right (signed/arithmetic): r/m64, cl */
    fun sar(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1, r2)
    }

    /** Shift right (signed/arithmetic): r/m64, imm8 */
    fun sar(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1, imm)
    }

    /** Rotate left: r/m8, 1 */
    fun rol(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Rotate left: r/m8, cl */
    fun rol(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, r2)
    }

    /** Rotate left: r/m8, imm8 */
    fun rol(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Rotate left: r/m32, 1 */
    fun rol(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Rotate left: r/m32, cl */
    fun rol(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, r2)
    }

    /** Rotate left: r/m32, imm8 */
    fun rol(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1, imm)
    }

    /** Rotate left: r/m64, 1 */
    fun rol(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1)
    }

    /** Rotate left: r/m64, cl */
    fun rol(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, r2)
    }

    /** Rotate left: r/m64, imm8 */
    fun rol(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1, imm)
    }

    /** Rotate right: r/m8, 1 */
    fun ror(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1)
    }

    /** Rotate right: r/m8, cl */
    fun ror(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, r2)
    }

    /** Rotate right: r/m8, imm8 */
    fun ror(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, imm)
    }

    /** Rotate right: r/m32, 1 */
    fun ror(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1)
    }

    /** Rotate right: r/m32, cl */
    fun ror(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, r2)
    }

    /** Rotate right: r/m32, imm8 */
    fun ror(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 1), rm1, imm)
    }

    /** Rotate right: r/m64, 1 */
    fun ror(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1)
    }

    /** Rotate right: r/m64, cl */
    fun ror(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1, r2)
    }

    /** Rotate right: r/m64, imm8 */
    fun ror(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 1, rexW = true), rm1, imm)
    }

    /** Rotate left through carry: r/m8, 1 */
    fun rcl(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1)
    }

    /** Rotate left through carry: r/m8, cl */
    fun rcl(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, r2)
    }

    /** Rotate left through carry: r/m8, imm8 */
    fun rcl(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, imm)
    }

    /** Rotate left through carry: r/m32, 1 */
    fun rcl(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1)
    }

    /** Rotate left through carry: r/m32, cl */
    fun rcl(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, r2)
    }

    /** Rotate left through carry: r/m32, imm8 */
    fun rcl(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1, imm)
    }

    /** Rotate left through carry: r/m64, 1 */
    fun rcl(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1)
    }

    /** Rotate left through carry: r/m64, cl */
    fun rcl(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1, r2)
    }

    /** Rotate left through carry: r/m64, imm8 */
    fun rcl(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 2, rexW = true), rm1, imm)
    }

    /** Rotate right through carry: r/m8, 1 */
    fun rcr(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(208), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1)
    }

    /** Rotate right through carry: r/m8, cl */
    fun rcr(rm1: X86Operand8, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(210), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, r2)
    }

    /** Rotate right through carry: r/m8, imm8 */
    fun rcr(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(192), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, imm)
    }

    /** Rotate right through carry: r/m32, 1 */
    fun rcr(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1)
    }

    /** Rotate right through carry: r/m32, cl */
    fun rcr(rm1: X86Operand32, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, r2)
    }

    /** Rotate right through carry: r/m32, imm8 */
    fun rcr(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 3), rm1, imm)
    }

    /** Rotate right through carry: r/m64, 1 */
    fun rcr(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(209), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1)
    }

    /** Rotate right through carry: r/m64, cl */
    fun rcr(rm1: X86Operand64, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(211), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1, r2)
    }

    /** Rotate right through carry: r/m64, imm8 */
    fun rcr(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(193), modrmMode = ModrmMode.EXT, opcodeExt = 3, rexW = true), rm1, imm)
    }

    /** Double precision shift left: r/m32, r32, imm8 */
    fun shld(rm1: X86Operand32, r2: X86Register32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 164), modrmMode = ModrmMode.REG), r2, rm1, imm)
    }

    /** Double precision shift left: r/m32, r32, cl */
    fun shld(rm1: X86Operand32, r2: X86Register32, r3: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 165), modrmMode = ModrmMode.REG), r2, rm1, r3)
    }

    /** Double precision shift left: r/m64, r64, imm8 */
    fun shld(rm1: X86Operand64, r2: X86Register64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 164), modrmMode = ModrmMode.REG, rexW = true), r2, rm1, imm)
    }

    /** Double precision shift left: r/m64, r64, cl */
    fun shld(rm1: X86Operand64, r2: X86Register64, r3: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 165), modrmMode = ModrmMode.REG, rexW = true), r2, rm1, r3)
    }

    /** Double precision shift right: r/m32, r32, imm8 */
    fun shrd(rm1: X86Operand32, r2: X86Register32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 172), modrmMode = ModrmMode.REG), r2, rm1, imm)
    }

    /** Double precision shift right: r/m32, r32, cl */
    fun shrd(rm1: X86Operand32, r2: X86Register32, r3: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 173), modrmMode = ModrmMode.REG), r2, rm1, r3)
    }

    /** Double precision shift right: r/m64, r64, imm8 */
    fun shrd(rm1: X86Operand64, r2: X86Register64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 172), modrmMode = ModrmMode.REG, rexW = true), r2, rm1, imm)
    }

    /** Double precision shift right: r/m64, r64, cl */
    fun shrd(rm1: X86Operand64, r2: X86Register64, r3: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 173), modrmMode = ModrmMode.REG, rexW = true), r2, rm1, r3)
    }

    /** Compare (SUB without storing result): r/m8, imm8 */
    fun cmp(rm1: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(128), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Compare (SUB without storing result): al, imm8 (accumulator short form) / r/m8, imm8 */
    fun cmp(r1: X86Register8, imm: Byte) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(60)), r1, imm)
        else cmp(r1 as X86Operand8, imm)
    }

    /** Compare (SUB without storing result): r/m32, imm32 */
    fun cmp(rm1: X86Operand32, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Compare (SUB without storing result): eax, imm32 (accumulator short form) / r/m32, imm32 */
    fun cmp(r1: X86Register32, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(61)), r1, imm)
        else cmp(r1 as X86Operand32, imm)
    }

    /** Compare (SUB without storing result): r/m64, imm32 */
    fun cmp(rm1: X86Operand64, imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(129), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1, imm)
    }

    /** Compare (SUB without storing result): rax, imm32 (accumulator short form) / r/m64, imm32 */
    fun cmp(r1: X86Register64, imm: Int) {
        if (isAccumulator(r1)) encodeLegacy(X86EncodingInfo(opcode = intArrayOf(61), rexW = true), r1, imm)
        else cmp(r1 as X86Operand64, imm)
    }

    /** Compare (SUB without storing result): r/m32, imm8 */
    fun cmp(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Compare (SUB without storing result): r/m64, imm8 */
    fun cmp(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(131), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1, imm)
    }

    /** Compare (SUB without storing result): r/m8, r8 */
    fun cmp(mem: X86Memory, r2: X86Register8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(56), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Compare (SUB without storing result): r/m32, r32 */
    fun cmp(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(57), modrmMode = ModrmMode.REG), r2, mem)
    }

    /** Compare (SUB without storing result): r/m64, r64 */
    fun cmp(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(57), modrmMode = ModrmMode.REG, rexW = true), r2, mem)
    }

    /** Compare (SUB without storing result): r8, r/m8 */
    fun cmp(r1: X86Register8, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(58), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Compare (SUB without storing result): r32, r/m32 */
    fun cmp(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(59), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Compare (SUB without storing result): r64, r/m64 */
    fun cmp(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(59), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Set byte if above (CF=0, ZF=0): r/m8 */
    fun seta(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 151), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if above or equal (CF=0): r/m8 */
    fun setae(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 147), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if below (CF=1): r/m8 */
    fun setb(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 146), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if below or equal (CF=1 or ZF=1): r/m8 */
    fun setbe(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 150), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if equal (ZF=1): r/m8 */
    fun sete(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 148), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if greater (ZF=0, SF=OF): r/m8 */
    fun setg(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 159), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if greater or equal (SF=OF): r/m8 */
    fun setge(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 157), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if less (SF!=OF): r/m8 */
    fun setl(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 156), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if less or equal (ZF=1 or SF!=OF): r/m8 */
    fun setle(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 158), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if not equal (ZF=0): r/m8 */
    fun setne(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 149), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if not overflow (OF=0): r/m8 */
    fun setno(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 145), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if not parity (PF=0): r/m8 */
    fun setnp(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 155), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if not sign (SF=0): r/m8 */
    fun setns(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 153), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if overflow (OF=1): r/m8 */
    fun seto(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 144), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if parity (PF=1): r/m8 */
    fun setp(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 154), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Set byte if sign (SF=1): r/m8 */
    fun sets(rm1: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 152), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** Push onto stack: r16 */
    fun push(r1: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(80), mandatoryPrefix = 0x66, plusReg = true), r1)
    }

    /** Push onto stack: r64 */
    fun push(r1: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(80), plusReg = true, defaultSize = 64), r1)
    }

    /** Push onto stack: r/m16 */
    fun push(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 6, mandatoryPrefix = 0x66), rm1)
    }

    /** Push onto stack: r/m64 */
    fun push(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 6, defaultSize = 64), rm1)
    }

    /** Push onto stack: imm8 */
    fun push(imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(106)), imm)
    }

    /** Push onto stack: imm16 */
    fun push(imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(104), mandatoryPrefix = 0x66), imm)
    }

    /** Push onto stack: imm32 */
    fun push(imm: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(104)), imm)
    }

    /** Push onto stack: sreg */
    fun push(sreg: X86SegReg) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(6)), sreg)
    }

    /** Pop from stack: r16 */
    fun pop(r1: X86Register16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(88), mandatoryPrefix = 0x66, plusReg = true), r1)
    }

    /** Pop from stack: r64 */
    fun pop(r1: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(88), plusReg = true, defaultSize = 64), r1)
    }

    /** Pop from stack: r/m16 */
    fun pop(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(143), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1)
    }

    /** Pop from stack: r/m64 */
    fun pop(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(143), modrmMode = ModrmMode.EXT, opcodeExt = 0, defaultSize = 64), rm1)
    }

    /** Pop from stack: sreg */
    fun pop(sreg: X86SegReg) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(7)), sreg)
    }

    /** Create stack frame: imm16, imm8 */
    fun enter(imm1: Short, imm2: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(200)), imm1, imm2)
    }

    /** Destroy stack frame:  */
    fun leave() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(201)))
    }

    /** Push RFLAGS onto stack:  */
    fun pushfq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(156)))
    }

    /** Pop stack into RFLAGS:  */
    fun popfq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(157)))
    }

    /** Unconditional jump: rel8 */
    fun jmp(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(235)), rel)
    }

    /** Unconditional jump: rel32 */
    fun jmp(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(233)), rel)
    }

    /** Unconditional jump: r/m64 */
    fun jmp(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1)
    }

    /** Jump if above (CF=0, ZF=0): rel8 */
    fun ja(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(119)), rel)
    }

    /** Jump if above (CF=0, ZF=0): rel32 */
    fun ja(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 135)), rel)
    }

    /** Jump if above or equal (CF=0): rel8 */
    fun jae(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(115)), rel)
    }

    /** Jump if above or equal (CF=0): rel32 */
    fun jae(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 131)), rel)
    }

    /** Jump if below (CF=1): rel8 */
    fun jb(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(114)), rel)
    }

    /** Jump if below (CF=1): rel32 */
    fun jb(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 130)), rel)
    }

    /** Jump if below or equal (CF=1 or ZF=1): rel8 */
    fun jbe(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(118)), rel)
    }

    /** Jump if below or equal (CF=1 or ZF=1): rel32 */
    fun jbe(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 134)), rel)
    }

    /** Jump if equal (ZF=1): rel8 */
    fun je(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(116)), rel)
    }

    /** Jump if equal (ZF=1): rel32 */
    fun je(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 132)), rel)
    }

    /** Jump if greater (ZF=0, SF=OF): rel8 */
    fun jg(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(127)), rel)
    }

    /** Jump if greater (ZF=0, SF=OF): rel32 */
    fun jg(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 143)), rel)
    }

    /** Jump if greater or equal (SF=OF): rel8 */
    fun jge(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(125)), rel)
    }

    /** Jump if greater or equal (SF=OF): rel32 */
    fun jge(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 141)), rel)
    }

    /** Jump if less (SF!=OF): rel8 */
    fun jl(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(124)), rel)
    }

    /** Jump if less (SF!=OF): rel32 */
    fun jl(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 140)), rel)
    }

    /** Jump if less or equal (ZF=1 or SF!=OF): rel8 */
    fun jle(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(126)), rel)
    }

    /** Jump if less or equal (ZF=1 or SF!=OF): rel32 */
    fun jle(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 142)), rel)
    }

    /** Jump if not equal (ZF=0): rel8 */
    fun jne(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(117)), rel)
    }

    /** Jump if not equal (ZF=0): rel32 */
    fun jne(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 133)), rel)
    }

    /** Jump if not overflow (OF=0): rel8 */
    fun jno(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(113)), rel)
    }

    /** Jump if not overflow (OF=0): rel32 */
    fun jno(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 129)), rel)
    }

    /** Jump if not parity (PF=0): rel8 */
    fun jnp(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(123)), rel)
    }

    /** Jump if not parity (PF=0): rel32 */
    fun jnp(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 139)), rel)
    }

    /** Jump if not sign (SF=0): rel8 */
    fun jns(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(121)), rel)
    }

    /** Jump if not sign (SF=0): rel32 */
    fun jns(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 137)), rel)
    }

    /** Jump if overflow (OF=1): rel8 */
    fun jo(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(112)), rel)
    }

    /** Jump if overflow (OF=1): rel32 */
    fun jo(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 128)), rel)
    }

    /** Jump if parity (PF=1): rel8 */
    fun jp(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(122)), rel)
    }

    /** Jump if parity (PF=1): rel32 */
    fun jp(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 138)), rel)
    }

    /** Jump if sign (SF=1): rel8 */
    fun js(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(120)), rel)
    }

    /** Jump if sign (SF=1): rel32 */
    fun js(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 136)), rel)
    }

    /** Jump if RCX is zero: rel8 */
    fun jrcxz(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(227)), rel)
    }

    /** Call procedure: rel32 */
    fun call(rel: Int) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(232)), rel)
    }

    /** Call procedure: r/m64 */
    fun call(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(255), modrmMode = ModrmMode.EXT, opcodeExt = 2), rm1)
    }

    /** Return from procedure:  */
    fun ret() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(195)))
    }

    /** Return from procedure: imm16 */
    fun ret(imm: Short) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(194)), imm)
    }

    /** Loop (decrement RCX and jump if non-zero): rel8 */
    fun loop(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(226)), rel)
    }

    /** Loop if equal (decrement RCX, jump if non-zero and ZF=1): rel8 */
    fun loope(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(225)), rel)
    }

    /** Loop if not equal (decrement RCX, jump if non-zero and ZF=0): rel8 */
    fun loopne(rel: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(224)), rel)
    }

    /** Software interrupt: imm8 */
    fun int_(imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(205)), imm)
    }

    /** Breakpoint:  */
    fun int3() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(204)))
    }

    /** System call:  */
    fun syscall() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 5)))
    }

    /** Return from system call:  */
    fun sysret() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 7)))
    }

    /** No operation:  */
    fun nop() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(144)))
    }

    /** No operation: r/m16 */
    fun nop(rm1: X86Operand16) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 31), modrmMode = ModrmMode.EXT, opcodeExt = 0, mandatoryPrefix = 0x66), rm1)
    }

    /** No operation: r/m32 */
    fun nop(rm1: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 31), modrmMode = ModrmMode.EXT, opcodeExt = 0), rm1)
    }

    /** No operation: r/m64 */
    fun nop(rm1: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 31), modrmMode = ModrmMode.EXT, opcodeExt = 0, rexW = true), rm1)
    }

    /** Undefined instruction (guaranteed #UD):  */
    fun ud2() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 11)))
    }

    /** Halt:  */
    fun hlt() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(244)))
    }

    /** Move byte string:  */
    fun movsb() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(164)))
    }

    /** Move word string:  */
    fun movsw() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(165), mandatoryPrefix = 0x66))
    }

    /** Move doubleword string:  */
    fun movsd_str() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(165)))
    }

    /** Move quadword string:  */
    fun movsq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(165), rexW = true))
    }

    /** Compare byte strings:  */
    fun cmpsb() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(166)))
    }

    /** Compare doubleword strings:  */
    fun cmpsd_str() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(167)))
    }

    /** Compare quadword strings:  */
    fun cmpsq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(167), rexW = true))
    }

    /** Scan byte string:  */
    fun scasb() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(174)))
    }

    /** Scan doubleword string:  */
    fun scasd() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(175)))
    }

    /** Scan quadword string:  */
    fun scasq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(175), rexW = true))
    }

    /** Load byte string:  */
    fun lodsb() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(172)))
    }

    /** Load doubleword string:  */
    fun lodsd() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(173)))
    }

    /** Load quadword string:  */
    fun lodsq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(173), rexW = true))
    }

    /** Store byte string:  */
    fun stosb() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(170)))
    }

    /** Store doubleword string:  */
    fun stosd() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(171)))
    }

    /** Store quadword string:  */
    fun stosq() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(171), rexW = true))
    }

    /** Repeat prefix:  */
    fun rep() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(243)))
    }

    /** Repeat while equal prefix:  */
    fun repe() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(243)))
    }

    /** Repeat while not equal prefix:  */
    fun repne() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(242)))
    }

    /** Clear carry flag:  */
    fun clc() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(248)))
    }

    /** Set carry flag:  */
    fun stc() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(249)))
    }

    /** Complement carry flag:  */
    fun cmc() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(245)))
    }

    /** Clear direction flag:  */
    fun cld() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(252)))
    }

    /** Set direction flag:  */
    fun std() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(253)))
    }

    /** Load AH from flags:  */
    fun lahf() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(159)))
    }

    /** Store AH into flags:  */
    fun sahf() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(158)))
    }

    /** Bit test: r/m32, r32 */
    fun bt(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 163), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Bit test: r/m64, r64 */
    fun bt(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 163), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Bit test: r/m32, imm8 */
    fun bt(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 4), rm1, imm)
    }

    /** Bit test: r/m64, imm8 */
    fun bt(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 4, rexW = true), rm1, imm)
    }

    /** Bit test and set: r/m32, r32 */
    fun bts(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 171), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Bit test and set: r/m64, r64 */
    fun bts(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 171), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Bit test and set: r/m32, imm8 */
    fun bts(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 5), rm1, imm)
    }

    /** Bit test and set: r/m64, imm8 */
    fun bts(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 5, rexW = true), rm1, imm)
    }

    /** Bit test and reset: r/m32, r32 */
    fun btr(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 179), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Bit test and reset: r/m64, r64 */
    fun btr(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 179), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Bit test and reset: r/m32, imm8 */
    fun btr(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 6), rm1, imm)
    }

    /** Bit test and reset: r/m64, imm8 */
    fun btr(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 6, rexW = true), rm1, imm)
    }

    /** Bit test and complement: r/m32, r32 */
    fun btc(rm1: X86Operand32, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 187), modrmMode = ModrmMode.REG), r2, rm1)
    }

    /** Bit test and complement: r/m64, r64 */
    fun btc(rm1: X86Operand64, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 187), modrmMode = ModrmMode.REG, rexW = true), r2, rm1)
    }

    /** Bit test and complement: r/m32, imm8 */
    fun btc(rm1: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 7), rm1, imm)
    }

    /** Bit test and complement: r/m64, imm8 */
    fun btc(rm1: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 186), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), rm1, imm)
    }

    /** Bit scan forward: r32, r/m32 */
    fun bsf(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 188), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Bit scan forward: r64, r/m64 */
    fun bsf(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 188), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Bit scan reverse: r32, r/m32 */
    fun bsr(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 189), modrmMode = ModrmMode.REG), r1, rm2)
    }

    /** Bit scan reverse: r64, r/m64 */
    fun bsr(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 189), modrmMode = ModrmMode.REG, rexW = true), r1, rm2)
    }

    /** Population count (number of set bits): r32, r/m32 */
    fun popcnt(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, rm2)
    }

    /** Population count (number of set bits): r64, r/m64 */
    fun popcnt(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, rm2)
    }

    /** Count leading zero bits: r32, r/m32 */
    fun lzcnt(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 189), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, rm2)
    }

    /** Count leading zero bits: r64, r/m64 */
    fun lzcnt(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 189), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, rm2)
    }

    /** Count trailing zero bits: r32, r/m32 */
    fun tzcnt(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 188), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, rm2)
    }

    /** Count trailing zero bits: r64, r/m64 */
    fun tzcnt(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 188), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, rm2)
    }

    /** Logical AND NOT: r32, r32, r/m32 */
    fun andn(r1: X86Register32, r2: X86Register32, rm3: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(242), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, r2, rm3)
    }

    /** Logical AND NOT: r64, r64, r/m64 */
    fun andn(r1: X86Register64, r2: X86Register64, rm3: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(242), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, r2, rm3)
    }

    /** Bit field extract: r32, r/m32, r32 */
    fun bextr(r1: X86Register32, rm2: X86Operand32, r3: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2, r3)
    }

    /** Bit field extract: r64, r/m64, r64 */
    fun bextr(r1: X86Register64, rm2: X86Operand64, r3: X86Register64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2, r3)
    }

    /** Extract lowest set isolated bit: r32, r/m32 */
    fun blsi(r1: X86Register32, rm2: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2)
    }

    /** Extract lowest set isolated bit: r64, r/m64 */
    fun blsi(r1: X86Register64, rm2: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2)
    }

    /** Get mask up to lowest set bit: r32, r/m32 */
    fun blsmsk(r1: X86Register32, rm2: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2)
    }

    /** Get mask up to lowest set bit: r64, r/m64 */
    fun blsmsk(r1: X86Register64, rm2: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2)
    }

    /** Reset lowest set bit: r32, r/m32 */
    fun blsr(r1: X86Register32, rm2: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 1, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2)
    }

    /** Reset lowest set bit: r64, r/m64 */
    fun blsr(r1: X86Register64, rm2: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(243), modrmMode = ModrmMode.EXT, opcodeExt = 1, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2)
    }

    /** Zero high bits starting at specified bit position: r32, r/m32, r32 */
    fun bzhi(r1: X86Register32, rm2: X86Operand32, r3: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2, r3)
    }

    /** Zero high bits starting at specified bit position: r64, r/m64, r64 */
    fun bzhi(r1: X86Register64, rm2: X86Operand64, r3: X86Register64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2, r3)
    }

    /** Unsigned multiply without affecting flags: r32, r32, r/m32 */
    fun mulx(r1: X86Register32, r2: X86Register32, rm3: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, r2, rm3)
    }

    /** Unsigned multiply without affecting flags: r64, r64, r/m64 */
    fun mulx(r1: X86Register64, r2: X86Register64, rm3: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(246), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, r2, rm3)
    }

    /** Parallel bits deposit: r32, r32, r/m32 */
    fun pdep(r1: X86Register32, r2: X86Register32, rm3: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, r2, rm3)
    }

    /** Parallel bits deposit: r64, r64, r/m64 */
    fun pdep(r1: X86Register64, r2: X86Register64, rm3: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, r2, rm3)
    }

    /** Parallel bits extract: r32, r32, r/m32 */
    fun pext(r1: X86Register32, r2: X86Register32, rm3: X86Operand32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, r2, rm3)
    }

    /** Parallel bits extract: r64, r64, r/m64 */
    fun pext(r1: X86Register64, r2: X86Register64, rm3: X86Operand64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(245), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, r2, rm3)
    }

    /** Rotate right without affecting flags: r32, r/m32, imm8 */
    fun rorx(r1: X86Register32, rm2: X86Operand32, imm: Byte) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(240), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F3A, vexW = 0), r1, rm2, imm)
    }

    /** Rotate right without affecting flags: r64, r/m64, imm8 */
    fun rorx(r1: X86Register64, rm2: X86Operand64, imm: Byte) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(240), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F3A, vexW = 1), r1, rm2, imm)
    }

    /** Shift arithmetic right without affecting flags: r32, r/m32, r32 */
    fun sarx(r1: X86Register32, rm2: X86Operand32, r3: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2, r3)
    }

    /** Shift arithmetic right without affecting flags: r64, r/m64, r64 */
    fun sarx(r1: X86Register64, rm2: X86Operand64, r3: X86Register64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2, r3)
    }

    /** Shift logical right without affecting flags: r32, r/m32, r32 */
    fun shrx(r1: X86Register32, rm2: X86Operand32, r3: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2, r3)
    }

    /** Shift logical right without affecting flags: r64, r/m64, r64 */
    fun shrx(r1: X86Register64, rm2: X86Operand64, r3: X86Register64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2, r3)
    }

    /** Shift logical left without affecting flags: r32, r/m32, r32 */
    fun shlx(r1: X86Register32, rm2: X86Operand32, r3: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), r1, rm2, r3)
    }

    /** Shift logical left without affecting flags: r64, r/m64, r64 */
    fun shlx(r1: X86Register64, rm2: X86Operand64, r3: X86Register64) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), r1, rm2, r3)
    }

    /** Move aligned packed single-precision: xmm, xmm/m128 */
    fun movaps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 40), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Move unaligned packed single-precision: xmm, xmm/m128 */
    fun movups(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 16), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Move scalar single-precision: xmm, xmm/m32 */
    fun movss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 16), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Move low packed single-precision: xmm, m64 */
    fun movlps(xmm1: X86Xmm, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 18), modrmMode = ModrmMode.REG), xmm1, mem)
    }

    /** Move low packed single-precision: m64, xmm */
    fun movlps(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 19), modrmMode = ModrmMode.REG), mem, xmm2)
    }

    /** Move high packed single-precision: xmm, m64 */
    fun movhps(xmm1: X86Xmm, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 22), modrmMode = ModrmMode.REG), xmm1, mem)
    }

    /** Move high packed single-precision: m64, xmm */
    fun movhps(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 23), modrmMode = ModrmMode.REG), mem, xmm2)
    }

    /** Move low to high packed single: xmm, xmm */
    fun movlhps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 22), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Move high to low packed single: xmm, xmm */
    fun movhlps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 18), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Extract sign mask: r32, xmm */
    fun movmskps(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 80), modrmMode = ModrmMode.REG), r1, xmm2)
    }

    /** Add packed single-precision: xmm, xmm/m128 */
    fun addps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 88), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Add scalar single-precision: xmm, xmm/m32 */
    fun addss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Subtract packed single-precision: xmm, xmm/m128 */
    fun subps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 92), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Subtract scalar single-precision: xmm, xmm/m32 */
    fun subss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Multiply packed single-precision: xmm, xmm/m128 */
    fun mulps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 89), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Multiply scalar single-precision: xmm, xmm/m32 */
    fun mulss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Divide packed single-precision: xmm, xmm/m128 */
    fun divps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 94), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Divide scalar single-precision: xmm, xmm/m32 */
    fun divss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Square root packed single: xmm, xmm/m128 */
    fun sqrtps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 81), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Square root scalar single: xmm, xmm/m32 */
    fun sqrtss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 81), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Reciprocal packed single: xmm, xmm/m128 */
    fun rcpps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 83), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Reciprocal scalar single: xmm, xmm/m32 */
    fun rcpss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 83), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Reciprocal square root packed: xmm, xmm/m128 */
    fun rsqrtps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 82), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Reciprocal square root scalar: xmm, xmm/m32 */
    fun rsqrtss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 82), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Maximum packed single: xmm, xmm/m128 */
    fun maxps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 95), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Maximum scalar single: xmm, xmm/m32 */
    fun maxss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 95), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Minimum packed single: xmm, xmm/m128 */
    fun minps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 93), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Minimum scalar single: xmm, xmm/m32 */
    fun minss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 93), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Bitwise AND packed single: xmm, xmm/m128 */
    fun andps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 84), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Bitwise AND NOT packed single: xmm, xmm/m128 */
    fun andnps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 85), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Bitwise OR packed single: xmm, xmm/m128 */
    fun orps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 86), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Bitwise XOR packed single: xmm, xmm/m128 */
    fun xorps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 87), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Compare packed single: xmm, xmm/m128, imm8 */
    fun cmpps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 194), modrmMode = ModrmMode.REG), xmm1, xmm2, imm)
    }

    /** Compare scalar single: xmm, xmm/m32, imm8 */
    fun cmpss(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 194), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2, imm)
    }

    /** Compare ordered scalar single: xmm, xmm/m32 */
    fun comiss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 47), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Compare unordered scalar single: xmm, xmm/m32 */
    fun ucomiss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 46), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Shuffle packed single: xmm, xmm/m128, imm8 */
    fun shufps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 198), modrmMode = ModrmMode.REG), xmm1, xmm2, imm)
    }

    /** Unpack and interleave low packed single: xmm, xmm/m128 */
    fun unpcklps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 20), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Unpack and interleave high packed single: xmm, xmm/m128 */
    fun unpckhps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 21), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Convert integer to scalar single: xmm, r/m32 */
    fun cvtsi2ss(xmm1: X86Xmm, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 42), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, rm2)
    }

    /** Convert integer to scalar single: xmm, r/m64 */
    fun cvtsi2ss(xmm1: X86Xmm, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 42), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), xmm1, rm2)
    }

    /** Convert scalar single to integer: r32, xmm/m32 */
    fun cvtss2si(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 45), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, xmm2)
    }

    /** Convert scalar single to integer: r64, xmm/m32 */
    fun cvtss2si(r1: X86Register64, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 45), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, xmm2)
    }

    /** Convert truncated scalar single to integer: r32, xmm/m32 */
    fun cvttss2si(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 44), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), r1, xmm2)
    }

    /** Convert truncated scalar single to integer: r64, xmm/m32 */
    fun cvttss2si(r1: X86Register64, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 44), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, rexW = true), r1, xmm2)
    }

    /** Convert packed single to packed integer (MMX): mm, xmm/m64 */
    fun cvtps2pi(mm1: X86Mm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 45), modrmMode = ModrmMode.REG), mm1, xmm2)
    }

    /** Convert truncated packed single to packed integer (MMX): mm, xmm/m64 */
    fun cvttps2pi(mm1: X86Mm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 44), modrmMode = ModrmMode.REG), mm1, xmm2)
    }

    /** Convert packed integer (MMX) to packed single: xmm, mm/m64 */
    fun cvtpi2ps(xmm1: X86Xmm, mm2: X86Mm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 42), modrmMode = ModrmMode.REG), xmm1, mm2)
    }

    /** Load MXCSR register: m32 */
    fun ldmxcsr(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174), modrmMode = ModrmMode.EXT, opcodeExt = 2), mem)
    }

    /** Store MXCSR register: m32 */
    fun stmxcsr(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174), modrmMode = ModrmMode.EXT, opcodeExt = 3), mem)
    }

    /** Move aligned packed double-precision: xmm, xmm/m128 */
    fun movapd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 40), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Move unaligned packed double-precision: xmm, xmm/m128 */
    fun movupd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 16), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Move scalar double-precision: xmm, xmm/m64 */
    fun movsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 16), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Move aligned packed integers: xmm, xmm/m128 */
    fun movdqa(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 111), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Move unaligned packed integers: xmm, xmm/m128 */
    fun movdqu(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 111), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Move doubleword to/from XMM: xmm, r/m32 */
    fun movd(xmm1: X86Xmm, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 110), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, rm2)
    }

    /** Move doubleword to/from XMM: r/m32, xmm */
    fun movd(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 126), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm2, mem)
    }

    /** Move quadword to/from XMM: xmm, r/m64 */
    fun movq(xmm1: X86Xmm, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 110), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, rexW = true), xmm1, rm2)
    }

    /** Move quadword to/from XMM: r/m64, xmm */
    fun movq(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 126), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, rexW = true), xmm2, mem)
    }

    /** Move quadword to/from XMM: xmm, xmm/m64 */
    fun movq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 126), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Extract sign mask from packed double: r32, xmm */
    fun movmskpd(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 80), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, xmm2)
    }

    /** Add packed double-precision: xmm, xmm/m128 */
    fun addpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Add scalar double-precision: xmm, xmm/m64 */
    fun addsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Subtract packed double-precision: xmm, xmm/m128 */
    fun subpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Subtract scalar double-precision: xmm, xmm/m64 */
    fun subsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Multiply packed double-precision: xmm, xmm/m128 */
    fun mulpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply scalar double-precision: xmm, xmm/m64 */
    fun mulsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Divide packed double-precision: xmm, xmm/m128 */
    fun divpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Divide scalar double-precision: xmm, xmm/m64 */
    fun divsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Square root packed double: xmm, xmm/m128 */
    fun sqrtpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 81), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Square root scalar double: xmm, xmm/m64 */
    fun sqrtsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 81), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Maximum packed double: xmm, xmm/m128 */
    fun maxpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 95), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Maximum scalar double: xmm, xmm/m64 */
    fun maxsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 95), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Minimum packed double: xmm, xmm/m128 */
    fun minpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 93), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Minimum scalar double: xmm, xmm/m64 */
    fun minsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 93), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Bitwise AND packed double: xmm, xmm/m128 */
    fun andpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 84), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise AND NOT packed double: xmm, xmm/m128 */
    fun andnpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 85), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise OR packed double: xmm, xmm/m128 */
    fun orpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 86), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise XOR packed double: xmm, xmm/m128 */
    fun xorpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 87), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed double: xmm, xmm/m128, imm8 */
    fun cmppd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 194), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Compare scalar double: xmm, xmm/m64, imm8 */
    fun cmpsd_sse(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 194), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2, imm)
    }

    /** Compare ordered scalar double: xmm, xmm/m64 */
    fun comisd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 47), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare unordered scalar double: xmm, xmm/m64 */
    fun ucomisd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 46), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shuffle packed double: xmm, xmm/m128, imm8 */
    fun shufpd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 198), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Unpack low packed double: xmm, xmm/m128 */
    fun unpcklpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 20), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack high packed double: xmm, xmm/m128 */
    fun unpckhpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 21), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Convert integer to scalar double: xmm, r/m32 */
    fun cvtsi2sd(xmm1: X86Xmm, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 42), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, rm2)
    }

    /** Convert integer to scalar double: xmm, r/m64 */
    fun cvtsi2sd(xmm1: X86Xmm, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 42), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, rexW = true), xmm1, rm2)
    }

    /** Convert scalar double to integer: r32, xmm/m64 */
    fun cvtsd2si(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 45), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), r1, xmm2)
    }

    /** Convert scalar double to integer: r64, xmm/m64 */
    fun cvtsd2si(r1: X86Register64, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 45), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, rexW = true), r1, xmm2)
    }

    /** Convert truncated scalar double to integer: r32, xmm/m64 */
    fun cvttsd2si(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 44), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), r1, xmm2)
    }

    /** Convert truncated scalar double to integer: r64, xmm/m64 */
    fun cvttsd2si(r1: X86Register64, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 44), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, rexW = true), r1, xmm2)
    }

    /** Convert scalar double to scalar single: xmm, xmm/m64 */
    fun cvtsd2ss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 90), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Convert scalar single to scalar double: xmm, xmm/m32 */
    fun cvtss2sd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 90), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Convert packed double to packed single: xmm, xmm/m128 */
    fun cvtpd2ps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 90), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Convert packed single to packed double: xmm, xmm/m64 */
    fun cvtps2pd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 90), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Convert packed double to packed dword: xmm, xmm/m128 */
    fun cvtpd2dq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 230), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Convert truncated packed double to packed dword: xmm, xmm/m128 */
    fun cvttpd2dq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 230), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Convert packed dword to packed double: xmm, xmm/m64 */
    fun cvtdq2pd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 230), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Convert packed dword to packed single: xmm, xmm/m128 */
    fun cvtdq2ps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 91), modrmMode = ModrmMode.REG), xmm1, xmm2)
    }

    /** Convert packed single to packed dword: xmm, xmm/m128 */
    fun cvtps2dq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 91), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Convert truncated packed single to packed dword: xmm, xmm/m128 */
    fun cvttps2dq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 91), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Add packed byte integers: xmm, xmm/m128 */
    fun paddb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 252), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Add packed word integers: xmm, xmm/m128 */
    fun paddw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 253), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Add packed dword integers: xmm, xmm/m128 */
    fun paddd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 254), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Add packed qword integers: xmm, xmm/m128 */
    fun paddq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 212), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Subtract packed byte integers: xmm, xmm/m128 */
    fun psubb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 248), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Subtract packed word integers: xmm, xmm/m128 */
    fun psubw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 249), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Subtract packed dword integers: xmm, xmm/m128 */
    fun psubd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 250), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Subtract packed qword integers: xmm, xmm/m128 */
    fun psubq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 251), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply packed word integers (low): xmm, xmm/m128 */
    fun pmullw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 213), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply packed word integers (high): xmm, xmm/m128 */
    fun pmulhw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 229), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply packed unsigned dword integers: xmm, xmm/m128 */
    fun pmuludq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 244), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise AND packed integers: xmm, xmm/m128 */
    fun pand(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 219), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise AND NOT packed integers: xmm, xmm/m128 */
    fun pandn(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 223), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise OR packed integers: xmm, xmm/m128 */
    fun por(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 235), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Bitwise XOR packed integers: xmm, xmm/m128 */
    fun pxor(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 239), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed words left: xmm, xmm/m128 */
    fun psllw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed words left: xmm, imm8 */
    fun psllw(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 113), modrmMode = ModrmMode.EXT, opcodeExt = 6, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed dwords left: xmm, xmm/m128 */
    fun pslld(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 242), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed dwords left: xmm, imm8 */
    fun pslld(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 114), modrmMode = ModrmMode.EXT, opcodeExt = 6, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed qwords left: xmm, xmm/m128 */
    fun psllq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 243), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed qwords left: xmm, imm8 */
    fun psllq(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 115), modrmMode = ModrmMode.EXT, opcodeExt = 6, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed words right (logical): xmm, xmm/m128 */
    fun psrlw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 209), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed words right (logical): xmm, imm8 */
    fun psrlw(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 113), modrmMode = ModrmMode.EXT, opcodeExt = 2, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed dwords right (logical): xmm, xmm/m128 */
    fun psrld(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 210), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed dwords right (logical): xmm, imm8 */
    fun psrld(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 114), modrmMode = ModrmMode.EXT, opcodeExt = 2, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed qwords right (logical): xmm, xmm/m128 */
    fun psrlq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 211), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed qwords right (logical): xmm, imm8 */
    fun psrlq(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 115), modrmMode = ModrmMode.EXT, opcodeExt = 2, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed words right (arithmetic): xmm, xmm/m128 */
    fun psraw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 225), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed words right (arithmetic): xmm, imm8 */
    fun psraw(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 113), modrmMode = ModrmMode.EXT, opcodeExt = 4, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift packed dwords right (arithmetic): xmm, xmm/m128 */
    fun psrad(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 226), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shift packed dwords right (arithmetic): xmm, imm8 */
    fun psrad(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 114), modrmMode = ModrmMode.EXT, opcodeExt = 4, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift XMM left by bytes: xmm, imm8 */
    fun pslldq(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 115), modrmMode = ModrmMode.EXT, opcodeExt = 7, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Shift XMM right by bytes: xmm, imm8 */
    fun psrldq(xmm1: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 115), modrmMode = ModrmMode.EXT, opcodeExt = 3, mandatoryPrefix = 0x66), xmm1, imm)
    }

    /** Compare packed bytes for equality: xmm, xmm/m128 */
    fun pcmpeqb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 116), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed words for equality: xmm, xmm/m128 */
    fun pcmpeqw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 117), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed dwords for equality: xmm, xmm/m128 */
    fun pcmpeqd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 118), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed bytes for greater than: xmm, xmm/m128 */
    fun pcmpgtb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 100), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed words for greater than: xmm, xmm/m128 */
    fun pcmpgtw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 101), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed dwords for greater than: xmm, xmm/m128 */
    fun pcmpgtd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 102), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Pack words to bytes (signed saturation): xmm, xmm/m128 */
    fun packsswb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 99), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Pack dwords to words (signed saturation): xmm, xmm/m128 */
    fun packssdw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 107), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Pack words to bytes (unsigned saturation): xmm, xmm/m128 */
    fun packuswb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 103), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack low bytes: xmm, xmm/m128 */
    fun punpcklbw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 96), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack low words: xmm, xmm/m128 */
    fun punpcklwd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 97), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack low dwords: xmm, xmm/m128 */
    fun punpckldq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 98), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack low qwords: xmm, xmm/m128 */
    fun punpcklqdq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 108), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack high bytes: xmm, xmm/m128 */
    fun punpckhbw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 104), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack high words: xmm, xmm/m128 */
    fun punpckhwd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 105), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack high dwords: xmm, xmm/m128 */
    fun punpckhdq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 106), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Unpack high qwords: xmm, xmm/m128 */
    fun punpckhqdq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 109), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Shuffle packed dwords: xmm, xmm/m128, imm8 */
    fun pshufd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 112), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Shuffle packed high words: xmm, xmm/m128, imm8 */
    fun pshufhw(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 112), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2, imm)
    }

    /** Shuffle packed low words: xmm, xmm/m128, imm8 */
    fun pshuflw(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 112), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2, imm)
    }

    /** Move byte mask: r32, xmm */
    fun pmovmskb(r1: X86Register32, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 215), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), r1, xmm2)
    }

    /** Store selected bytes: xmm, xmm */
    fun maskmovdqu(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 247), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Store packed integers (non-temporal): m128, xmm */
    fun movntdq(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 231), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), mem, xmm2)
    }

    /** Store packed double (non-temporal): m128, xmm */
    fun movntpd(mem: X86Memory, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 43), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), mem, xmm2)
    }

    /** Store dword/qword (non-temporal): m32, r32 */
    fun movnti(mem: X86Memory, r2: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 195), modrmMode = ModrmMode.REG), mem, r2)
    }

    /** Store dword/qword (non-temporal): m64, r64 */
    fun movnti(mem: X86Memory, r2: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 195), modrmMode = ModrmMode.REG, rexW = true), mem, r2)
    }

    /** Packed single add/subtract: xmm, xmm/m128 */
    fun addsubps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 208), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Packed double add/subtract: xmm, xmm/m128 */
    fun addsubpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 208), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal add packed single: xmm, xmm/m128 */
    fun haddps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 124), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Horizontal add packed double: xmm, xmm/m128 */
    fun haddpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 124), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal subtract packed single: xmm, xmm/m128 */
    fun hsubps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 125), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Horizontal subtract packed double: xmm, xmm/m128 */
    fun hsubpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 125), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Move packed single high and duplicate: xmm, xmm/m128 */
    fun movshdup(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 22), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Move packed single low and duplicate: xmm, xmm/m128 */
    fun movsldup(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 18), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3), xmm1, xmm2)
    }

    /** Move packed double and duplicate: xmm, xmm/m64 */
    fun movddup(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 18), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, xmm2)
    }

    /** Load unaligned integer 128: xmm, m128 */
    fun lddqu(xmm1: X86Xmm, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 240), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), xmm1, mem)
    }

    /** Shuffle bytes: xmm, xmm/m128 */
    fun pshufb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 0), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal add packed words: xmm, xmm/m128 */
    fun phaddw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 1), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal add packed dwords: xmm, xmm/m128 */
    fun phaddd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 2), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal add packed words (saturating): xmm, xmm/m128 */
    fun phaddsw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 3), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal subtract packed words: xmm, xmm/m128 */
    fun phsubw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 5), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal subtract packed dwords: xmm, xmm/m128 */
    fun phsubd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 6), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Horizontal subtract packed words (saturating): xmm, xmm/m128 */
    fun phsubsw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 7), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply and add packed unsigned and signed bytes: xmm, xmm/m128 */
    fun pmaddubsw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 4), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed multiply high with round and scale: xmm, xmm/m128 */
    fun pmulhrsw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 11), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed sign byte: xmm, xmm/m128 */
    fun psignb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 8), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed sign word: xmm, xmm/m128 */
    fun psignw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 9), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed sign dword: xmm, xmm/m128 */
    fun psignd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 10), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed absolute value byte: xmm, xmm/m128 */
    fun pabsb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 28), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed absolute value word: xmm, xmm/m128 */
    fun pabsw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 29), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed absolute value dword: xmm, xmm/m128 */
    fun pabsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 30), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed align right: xmm, xmm/m128, imm8 */
    fun palignr(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 15), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Multiply packed dword integers (low): xmm, xmm/m128 */
    fun pmulld(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 64), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Multiply packed signed dword integers: xmm, xmm/m128 */
    fun pmuldq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 40), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Minimum packed signed dwords: xmm, xmm/m128 */
    fun pminsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 57), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Minimum packed unsigned dwords: xmm, xmm/m128 */
    fun pminud(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 59), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Minimum packed signed bytes: xmm, xmm/m128 */
    fun pminsb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 56), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Minimum packed unsigned words: xmm, xmm/m128 */
    fun pminuw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 58), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Maximum packed signed dwords: xmm, xmm/m128 */
    fun pmaxsd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 61), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Maximum packed unsigned dwords: xmm, xmm/m128 */
    fun pmaxud(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 63), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Maximum packed signed bytes: xmm, xmm/m128 */
    fun pmaxsb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 60), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Maximum packed unsigned words: xmm, xmm/m128 */
    fun pmaxuw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 62), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Round packed single-precision: xmm, xmm/m128, imm8 */
    fun roundps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 8), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Round packed double-precision: xmm, xmm/m128, imm8 */
    fun roundpd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 9), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Round scalar single-precision: xmm, xmm/m32, imm8 */
    fun roundss(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 10), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Round scalar double-precision: xmm, xmm/m64, imm8 */
    fun roundsd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 11), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Blend packed single-precision: xmm, xmm/m128, imm8 */
    fun blendps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 12), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Blend packed double-precision: xmm, xmm/m128, imm8 */
    fun blendpd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 13), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Variable blend packed single: xmm, xmm/m128 */
    fun blendvps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 20), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Variable blend packed double: xmm, xmm/m128 */
    fun blendvpd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 21), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Variable blend packed bytes: xmm, xmm/m128 */
    fun pblendvb(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 16), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Blend packed words: xmm, xmm/m128, imm8 */
    fun pblendw(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 14), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Dot product packed single: xmm, xmm/m128, imm8 */
    fun dpps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 64), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Dot product packed double: xmm, xmm/m128, imm8 */
    fun dppd(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 65), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Insert packed single: xmm, xmm/m32, imm8 */
    fun insertps(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 33), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Extract packed single: r/m32, xmm, imm8 */
    fun extractps(rm1: X86Operand32, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 23), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm2, rm1, imm)
    }

    /** Insert byte: xmm, r/m8, imm8 */
    fun pinsrb(xmm1: X86Xmm, rm2: X86Operand8, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 32), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, rm2, imm)
    }

    /** Insert dword: xmm, r/m32, imm8 */
    fun pinsrd(xmm1: X86Xmm, rm2: X86Operand32, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 34), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, rm2, imm)
    }

    /** Insert qword: xmm, r/m64, imm8 */
    fun pinsrq(xmm1: X86Xmm, rm2: X86Operand64, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 34), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, rexW = true), xmm1, rm2, imm)
    }

    /** Extract byte: r/m8, xmm, imm8 */
    fun pextrb(rm1: X86Operand8, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 20), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm2, rm1, imm)
    }

    /** Extract dword: r/m32, xmm, imm8 */
    fun pextrd(rm1: X86Operand32, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 22), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm2, rm1, imm)
    }

    /** Extract qword: r/m64, xmm, imm8 */
    fun pextrq(rm1: X86Operand64, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 22), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, rexW = true), xmm2, rm1, imm)
    }

    /** Packed move sign-extend bytes to words: xmm, xmm/m64 */
    fun pmovsxbw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 32), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move sign-extend bytes to dwords: xmm, xmm/m32 */
    fun pmovsxbd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 33), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move sign-extend bytes to qwords: xmm, xmm/m16 */
    fun pmovsxbq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 34), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move sign-extend words to dwords: xmm, xmm/m64 */
    fun pmovsxwd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 35), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move sign-extend words to qwords: xmm, xmm/m32 */
    fun pmovsxwq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 36), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move sign-extend dwords to qwords: xmm, xmm/m64 */
    fun pmovsxdq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 37), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend bytes to words: xmm, xmm/m64 */
    fun pmovzxbw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 48), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend bytes to dwords: xmm, xmm/m32 */
    fun pmovzxbd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 49), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend bytes to qwords: xmm, xmm/m16 */
    fun pmovzxbq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 50), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend words to dwords: xmm, xmm/m64 */
    fun pmovzxwd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 51), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend words to qwords: xmm, xmm/m32 */
    fun pmovzxwq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 52), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed move zero-extend dwords to qwords: xmm, xmm/m64 */
    fun pmovzxdq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 53), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Logical compare: xmm, xmm/m128 */
    fun ptest(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 23), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Compare packed qwords for equality: xmm, xmm/m128 */
    fun pcmpeqq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 41), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Pack dwords to words (unsigned saturation): xmm, xmm/m128 */
    fun packusdw(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 43), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Load packed integers (non-temporal): xmm, m128 */
    fun movntdqa(xmm1: X86Xmm, mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 42), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, mem)
    }

    /** Compute multiple packed sums of absolute difference: xmm, xmm/m128, imm8 */
    fun mpsadbw(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 66), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Compare packed qwords for greater than: xmm, xmm/m128 */
    fun pcmpgtq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 55), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** Packed compare explicit length strings (index): xmm, xmm/m128, imm8 */
    fun pcmpestri(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 97), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Packed compare explicit length strings (mask): xmm, xmm/m128, imm8 */
    fun pcmpestrm(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 96), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Packed compare implicit length strings (index): xmm, xmm/m128, imm8 */
    fun pcmpistri(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 99), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Packed compare implicit length strings (mask): xmm, xmm/m128, imm8 */
    fun pcmpistrm(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 98), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Accumulate CRC32 value: r32, r/m8 */
    fun crc32(r1: X86Register32, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), r1, rm2)
    }

    /** Accumulate CRC32 value: r32, r/m32 */
    fun crc32(r1: X86Register32, rm2: X86Operand32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2), r1, rm2)
    }

    /** Accumulate CRC32 value: r64, r/m8 */
    fun crc32(r1: X86Register64, rm2: X86Operand8) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, rexW = true), r1, rm2)
    }

    /** Accumulate CRC32 value: r64, r/m64 */
    fun crc32(r1: X86Register64, rm2: X86Operand64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 241), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, rexW = true), r1, rm2)
    }

    /** VEX move aligned packed single: xmm, xmm/m128 */
    fun vmovaps(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(40), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2)
    }

    /** VEX move aligned packed single: ymm, ymm/m256 */
    fun vmovaps(ymm1: X86Ymm, ymm2: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(40), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2)
    }

    /** VEX move unaligned packed single: xmm, xmm/m128 */
    fun vmovups(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(16), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2)
    }

    /** VEX move unaligned packed single: ymm, ymm/m256 */
    fun vmovups(ymm1: X86Ymm, ymm2: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(16), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2)
    }

    /** VEX move aligned packed double: xmm, xmm/m128 */
    fun vmovapd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(40), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2)
    }

    /** VEX move aligned packed double: ymm, ymm/m256 */
    fun vmovapd(ymm1: X86Ymm, ymm2: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(40), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2)
    }

    /** VEX add packed single: xmm, xmm, xmm/m128 */
    fun vaddps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX add packed single: ymm, ymm, ymm/m256 */
    fun vaddps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX add packed double: xmm, xmm, xmm/m128 */
    fun vaddpd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX add packed double: ymm, ymm, ymm/m256 */
    fun vaddpd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX add scalar single: xmm, xmm, xmm/m32 */
    fun vaddss(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF3, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX add scalar double: xmm, xmm, xmm/m64 */
    fun vaddsd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0xF2, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX subtract packed single: xmm, xmm, xmm/m128 */
    fun vsubps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX subtract packed single: ymm, ymm, ymm/m256 */
    fun vsubps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX subtract packed double: xmm, xmm, xmm/m128 */
    fun vsubpd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX subtract packed double: ymm, ymm, ymm/m256 */
    fun vsubpd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX multiply packed single: xmm, xmm, xmm/m128 */
    fun vmulps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX multiply packed single: ymm, ymm, ymm/m256 */
    fun vmulps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX multiply packed double: xmm, xmm, xmm/m128 */
    fun vmulpd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX multiply packed double: ymm, ymm, ymm/m256 */
    fun vmulpd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX divide packed single: xmm, xmm, xmm/m128 */
    fun vdivps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX divide packed single: ymm, ymm, ymm/m256 */
    fun vdivps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX divide packed double: xmm, xmm, xmm/m128 */
    fun vdivpd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX divide packed double: ymm, ymm, ymm/m256 */
    fun vdivpd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX bitwise XOR packed single: xmm, xmm, xmm/m128 */
    fun vxorps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(87), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX bitwise XOR packed single: ymm, ymm, ymm/m256 */
    fun vxorps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(87), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX bitwise XOR packed double: xmm, xmm, xmm/m128 */
    fun vxorpd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(87), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F), xmm1, xmm2, xmm3)
    }

    /** VEX bitwise XOR packed double: ymm, ymm, ymm/m256 */
    fun vxorpd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(87), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** Zero all YMM registers:  */
    fun vzeroall() {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(119), vexL = 1, vexMap = VexMap.MAP_0F))
    }

    /** Zero upper bits of all YMM registers:  */
    fun vzeroupper() {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(119), vexL = 0, vexMap = VexMap.MAP_0F))
    }

    /** Broadcast single-precision: xmm, xmm/m32 */
    fun vbroadcastss(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(24), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38), xmm1, xmm2)
    }

    /** Broadcast single-precision: ymm, xmm/m32 */
    fun vbroadcastss(ymm1: X86Ymm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(24), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38), ymm1, xmm2)
    }

    /** Broadcast double-precision: ymm, xmm/m64 */
    fun vbroadcastsd(ymm1: X86Ymm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(25), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38), ymm1, xmm2)
    }

    /** VEX add packed bytes: ymm, ymm, ymm/m256 */
    fun vpaddb(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(252), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX add packed words: ymm, ymm, ymm/m256 */
    fun vpaddw(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(253), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX add packed dwords: ymm, ymm, ymm/m256 */
    fun vpaddd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(254), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX add packed qwords: ymm, ymm, ymm/m256 */
    fun vpaddq(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(212), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX subtract packed bytes: ymm, ymm, ymm/m256 */
    fun vpsubb(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(248), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX subtract packed words: ymm, ymm, ymm/m256 */
    fun vpsubw(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(249), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX subtract packed dwords: ymm, ymm, ymm/m256 */
    fun vpsubd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(250), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX subtract packed qwords: ymm, ymm, ymm/m256 */
    fun vpsubq(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(251), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX multiply packed dwords (low): ymm, ymm, ymm/m256 */
    fun vpmulld(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(64), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38), ymm1, ymm2, ymm3)
    }

    /** VEX bitwise AND packed integers: ymm, ymm, ymm/m256 */
    fun vpand(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(219), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX bitwise OR packed integers: ymm, ymm, ymm/m256 */
    fun vpor(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(235), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX bitwise XOR packed integers: ymm, ymm, ymm/m256 */
    fun vpxor(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(239), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F), ymm1, ymm2, ymm3)
    }

    /** VEX broadcast dword: xmm, xmm/m32 */
    fun vpbroadcastd(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38), xmm1, xmm2)
    }

    /** VEX broadcast dword: ymm, xmm/m32 */
    fun vpbroadcastd(ymm1: X86Ymm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38), ymm1, xmm2)
    }

    /** VEX broadcast qword: xmm, xmm/m64 */
    fun vpbroadcastq(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38), xmm1, xmm2)
    }

    /** VEX broadcast qword: ymm, xmm/m64 */
    fun vpbroadcastq(ymm1: X86Ymm, xmm2: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38), ymm1, xmm2)
    }

    /** Permute 128-bit integer lanes: ymm, ymm, ymm/m256, imm8 */
    fun vperm2i128(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm, imm: Byte) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(70), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F3A, vexW = 0), ymm1, ymm2, ymm3, imm)
    }

    /** Permute packed dwords: ymm, ymm, ymm/m256 */
    fun vpermd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(54), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Permute packed qwords: ymm, ymm/m256, imm8 */
    fun vpermq(ymm1: X86Ymm, ymm2: X86Ymm, imm: Byte) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(0), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F3A, vexW = 1), ymm1, ymm2, imm)
    }

    /** Gather packed dwords with dword indices: xmm, m32, xmm */
    fun vpgatherdd(xmm1: X86Xmm, mem: X86Memory, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(144), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, mem, xmm3)
    }

    /** Gather packed dwords with dword indices: ymm, m32, ymm */
    fun vpgatherdd(ymm1: X86Ymm, mem: X86Memory, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(144), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, mem, ymm3)
    }

    /** Variable shift packed dwords left: xmm, xmm, xmm/m128 */
    fun vpsllvd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(71), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Variable shift packed dwords left: ymm, ymm, ymm/m256 */
    fun vpsllvd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(71), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Variable shift packed dwords right (logical): xmm, xmm, xmm/m128 */
    fun vpsrlvd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(69), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Variable shift packed dwords right (logical): ymm, ymm, ymm/m256 */
    fun vpsrlvd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(69), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Variable shift packed dwords right (arithmetic): xmm, xmm, xmm/m128 */
    fun vpsravd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(70), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Variable shift packed dwords right (arithmetic): ymm, ymm, ymm/m256 */
    fun vpsravd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(70), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** EVEX add packed single (512-bit): zmm, zmm, zmm/m512 */
    fun vaddps_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, evexL = 2, vexMap = VexMap.MAP_0F), zmm1, zmm2, zmm3)
    }

    /** EVEX add packed double (512-bit): zmm, zmm, zmm/m512 */
    fun vaddpd_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 1), zmm1, zmm2, zmm3)
    }

    /** EVEX subtract packed single (512-bit): zmm, zmm, zmm/m512 */
    fun vsubps_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, evexL = 2, vexMap = VexMap.MAP_0F), zmm1, zmm2, zmm3)
    }

    /** EVEX subtract packed double (512-bit): zmm, zmm, zmm/m512 */
    fun vsubpd_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(92), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 1), zmm1, zmm2, zmm3)
    }

    /** EVEX multiply packed single (512-bit): zmm, zmm, zmm/m512 */
    fun vmulps_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, evexL = 2, vexMap = VexMap.MAP_0F), zmm1, zmm2, zmm3)
    }

    /** EVEX multiply packed double (512-bit): zmm, zmm, zmm/m512 */
    fun vmulpd_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(89), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 1), zmm1, zmm2, zmm3)
    }

    /** EVEX divide packed single (512-bit): zmm, zmm, zmm/m512 */
    fun vdivps_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, evexL = 2, vexMap = VexMap.MAP_0F), zmm1, zmm2, zmm3)
    }

    /** EVEX divide packed double (512-bit): zmm, zmm, zmm/m512 */
    fun vdivpd_z(zmm1: X86Zmm, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(94), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 1), zmm1, zmm2, zmm3)
    }

    /** EVEX move aligned packed dwords: zmm, zmm/m512 */
    fun vmovdqa32(zmm1: X86Zmm, zmm2: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(111), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 0), zmm1, zmm2)
    }

    /** EVEX move aligned packed qwords: zmm, zmm/m512 */
    fun vmovdqa64(zmm1: X86Zmm, zmm2: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(111), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 1), zmm1, zmm2)
    }

    /** EVEX broadcast dword: zmm, r32 */
    fun vpbroadcastd_z(zmm1: X86Zmm, r2: X86Register32) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(124), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F38, vexW = 0), zmm1, r2)
    }

    /** EVEX broadcast dword: zmm, xmm/m32 */
    fun vpbroadcastd_z(zmm1: X86Zmm, xmm2: X86Xmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(88), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F38, vexW = 0), zmm1, xmm2)
    }

    /** EVEX compare packed dwords for equality (to mask): k, zmm, zmm/m512 */
    fun vpcmpeqd_z(k1: X86MaskReg, zmm2: X86Zmm, zmm3: X86Zmm) {
        encodeEvex(X86EncodingInfo(opcode = intArrayOf(118), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, evexL = 2, vexMap = VexMap.MAP_0F, vexW = 0), k1, zmm2, zmm3)
    }

    /** Move mask register: k, k/m16 */
    fun kmovw(k1: X86MaskReg, k2: X86MaskReg) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(144), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), k1, k2)
    }

    /** Move mask register: k, r32 */
    fun kmovw(k1: X86MaskReg, r2: X86Register32) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(146), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), k1, r2)
    }

    /** Move mask register: r32, k */
    fun kmovw(r1: X86Register32, k2: X86MaskReg) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(147), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), r1, k2)
    }

    /** AND mask registers: k, k, k */
    fun kandw(k1: X86MaskReg, k2: X86MaskReg, k3: X86MaskReg) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(65), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), k1, k2, k3)
    }

    /** OR mask registers: k, k, k */
    fun korw(k1: X86MaskReg, k2: X86MaskReg, k3: X86MaskReg) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(69), modrmMode = ModrmMode.REG, vexL = 1, vexMap = VexMap.MAP_0F), k1, k2, k3)
    }

    /** NOT mask register: k, k */
    fun knotw(k1: X86MaskReg, k2: X86MaskReg) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(68), modrmMode = ModrmMode.REG, vexL = 0, vexMap = VexMap.MAP_0F), k1, k2)
    }

    /** Fused multiply-add packed single (132 form): xmm, xmm, xmm/m128 */
    fun vfmadd132ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(152), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed single (132 form): ymm, ymm, ymm/m256 */
    fun vfmadd132ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(152), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add packed single (213 form): xmm, xmm, xmm/m128 */
    fun vfmadd213ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(168), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed single (213 form): ymm, ymm, ymm/m256 */
    fun vfmadd213ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(168), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add packed single (231 form): xmm, xmm, xmm/m128 */
    fun vfmadd231ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed single (231 form): ymm, ymm, ymm/m256 */
    fun vfmadd231ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add packed double (132 form): xmm, xmm, xmm/m128 */
    fun vfmadd132pd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(152), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed double (132 form): ymm, ymm, ymm/m256 */
    fun vfmadd132pd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(152), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 1), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add packed double (213 form): xmm, xmm, xmm/m128 */
    fun vfmadd213pd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(168), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed double (213 form): ymm, ymm, ymm/m256 */
    fun vfmadd213pd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(168), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 1), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add packed double (231 form): xmm, xmm, xmm/m128 */
    fun vfmadd231pd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add packed double (231 form): ymm, ymm, ymm/m256 */
    fun vfmadd231pd(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(184), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 1), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-add scalar single (132 form): xmm, xmm, xmm/m32 */
    fun vfmadd132ss(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(153), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add scalar single (213 form): xmm, xmm, xmm/m32 */
    fun vfmadd213ss(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(169), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add scalar single (231 form): xmm, xmm, xmm/m32 */
    fun vfmadd231ss(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(185), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add scalar double (132 form): xmm, xmm, xmm/m64 */
    fun vfmadd132sd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(153), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add scalar double (213 form): xmm, xmm, xmm/m64 */
    fun vfmadd213sd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(169), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-add scalar double (231 form): xmm, xmm, xmm/m64 */
    fun vfmadd231sd(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(185), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 1), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-subtract packed single (132 form): xmm, xmm, xmm/m128 */
    fun vfmsub132ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(154), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-subtract packed single (132 form): ymm, ymm, ymm/m256 */
    fun vfmsub132ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(154), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-subtract packed single (213 form): xmm, xmm, xmm/m128 */
    fun vfmsub213ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(170), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-subtract packed single (213 form): ymm, ymm, ymm/m256 */
    fun vfmsub213ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(170), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused multiply-subtract packed single (231 form): xmm, xmm, xmm/m128 */
    fun vfmsub231ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(186), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused multiply-subtract packed single (231 form): ymm, ymm, ymm/m256 */
    fun vfmsub231ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(186), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused negative multiply-add packed single (132 form): xmm, xmm, xmm/m128 */
    fun vfnmadd132ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(156), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused negative multiply-add packed single (132 form): ymm, ymm, ymm/m256 */
    fun vfnmadd132ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(156), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused negative multiply-add packed single (213 form): xmm, xmm, xmm/m128 */
    fun vfnmadd213ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(172), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused negative multiply-add packed single (213 form): ymm, ymm, ymm/m256 */
    fun vfnmadd213ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(172), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** Fused negative multiply-add packed single (231 form): xmm, xmm, xmm/m128 */
    fun vfnmadd231ps(xmm1: X86Xmm, xmm2: X86Xmm, xmm3: X86Xmm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(188), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 0, vexMap = VexMap.MAP_0F38, vexW = 0), xmm1, xmm2, xmm3)
    }

    /** Fused negative multiply-add packed single (231 form): ymm, ymm, ymm/m256 */
    fun vfnmadd231ps(ymm1: X86Ymm, ymm2: X86Ymm, ymm3: X86Ymm) {
        encodeVex(X86EncodingInfo(opcode = intArrayOf(188), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66, vexL = 1, vexMap = VexMap.MAP_0F38, vexW = 0), ymm1, ymm2, ymm3)
    }

    /** AES encrypt round: xmm, xmm/m128 */
    fun aesenc(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 220), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** AES encrypt last round: xmm, xmm/m128 */
    fun aesenclast(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 221), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** AES decrypt round: xmm, xmm/m128 */
    fun aesdec(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 222), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** AES decrypt last round: xmm, xmm/m128 */
    fun aesdeclast(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 223), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** AES inverse mix columns: xmm, xmm/m128 */
    fun aesimc(xmm1: X86Xmm, xmm2: X86Xmm) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 56, 219), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2)
    }

    /** AES key generation assist: xmm, xmm/m128, imm8 */
    fun aeskeygenassist(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 223), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** Carry-less multiplication: xmm, xmm/m128, imm8 */
    fun pclmulqdq(xmm1: X86Xmm, xmm2: X86Xmm, imm: Byte) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 58, 68), modrmMode = ModrmMode.REG, mandatoryPrefix = 0x66), xmm1, xmm2, imm)
    }

    /** End branch 64-bit (CET indirect branch tracking):  */
    fun endbr64() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 30, 250)))
    }

    /** End branch 32-bit (CET indirect branch tracking):  */
    fun endbr32() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 30, 251)))
    }

    /** CPU identification:  */
    fun cpuid() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 162)))
    }

    /** Read time-stamp counter:  */
    fun rdtsc() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 49)))
    }

    /** Read time-stamp counter and processor ID:  */
    fun rdtscp() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 1, 249)))
    }

    /** Read model-specific register:  */
    fun rdmsr() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 50)))
    }

    /** Write model-specific register:  */
    fun wrmsr() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 48)))
    }

    /** Read performance monitoring counter:  */
    fun rdpmc() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 51)))
    }

    /** Memory fence:  */
    fun mfence() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174, 240)))
    }

    /** Load fence:  */
    fun lfence() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174, 232)))
    }

    /** Store fence:  */
    fun sfence() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174, 248)))
    }

    /** Spin loop hint:  */
    fun pause() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(243, 144)))
    }

    /** Prefetch to all cache levels: m8 */
    fun prefetcht0(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 24), modrmMode = ModrmMode.EXT, opcodeExt = 1), mem)
    }

    /** Prefetch to L2+ cache: m8 */
    fun prefetcht1(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 24), modrmMode = ModrmMode.EXT, opcodeExt = 2), mem)
    }

    /** Prefetch to L3+ cache: m8 */
    fun prefetcht2(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 24), modrmMode = ModrmMode.EXT, opcodeExt = 3), mem)
    }

    /** Prefetch non-temporal: m8 */
    fun prefetchnta(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 24), modrmMode = ModrmMode.EXT, opcodeExt = 0), mem)
    }

    /** Flush cache line: m8 */
    fun clflush(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174), modrmMode = ModrmMode.EXT, opcodeExt = 7), mem)
    }

    /** Flush cache line (optimized): m8 */
    fun clflushopt(mem: X86Memory) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 174), modrmMode = ModrmMode.EXT, opcodeExt = 7, mandatoryPrefix = 0x66), mem)
    }

    /** Read random number: r32 */
    fun rdrand(r1: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 6), r1)
    }

    /** Read random number: r64 */
    fun rdrand(r1: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 6, rexW = true), r1)
    }

    /** Read random seed: r32 */
    fun rdseed(r1: X86Register32) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 7), r1)
    }

    /** Read random seed: r64 */
    fun rdseed(r1: X86Register64) {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 199), modrmMode = ModrmMode.EXT, opcodeExt = 7, rexW = true), r1)
    }

    /** Get extended control register:  */
    fun xgetbv() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 1, 208)))
    }

    /** Set extended control register:  */
    fun xsetbv() {
        encodeLegacy(X86EncodingInfo(opcode = intArrayOf(15, 1, 209)))
    }

}

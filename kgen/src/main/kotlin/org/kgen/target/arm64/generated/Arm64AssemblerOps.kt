// Generated — do not edit
package org.kgen.target.arm64

/**
 * Generated assembler dispatch methods for ARM64 (AArch64).
 *
 * Each method corresponds to an ARM64 instruction mnemonic.
 * Overloads select the correct encoding based on operand types.
 * The abstract [encodeArm64] method is implemented by the hand-written assembler.
 */
abstract class Arm64AssemblerOps {

    /** Encode and emit a single ARM64 instruction. Implemented by the assembler. */
    protected abstract fun encodeArm64(info: Arm64EncodingInfo, vararg operands: Any)

    /** Add: x, x, x */
    fun add(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x8B000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add: w, w, w */
    fun add(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x0B000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add: x, x, imm12 */
    fun add(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x91000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Add: w, w, imm12 */
    fun add(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x11000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Add and set flags: x, x, x */
    fun adds(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAB000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add and set flags: w, w, w */
    fun adds(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2B000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add and set flags: x, x, imm12 */
    fun adds(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB1000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Add and set flags: w, w, imm12 */
    fun adds(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x31000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Subtract: x, x, x */
    fun sub(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xCB000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract: w, w, w */
    fun sub(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x4B000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract: x, x, imm12 */
    fun sub(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xD1000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Subtract: w, w, imm12 */
    fun sub(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x51000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Subtract and set flags: x, x, x */
    fun subs(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEB000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract and set flags: w, w, w */
    fun subs(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6B000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract and set flags: x, x, imm12 */
    fun subs(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF1000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Subtract and set flags: w, w, imm12 */
    fun subs(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x71000000L, Arm64Format.REG2_IMM12), rd, rn, imm)
    }

    /** Add with carry: x, x, x */
    fun adc(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add with carry: w, w, w */
    fun adc(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add with carry and set flags: x, x, x */
    fun adcs(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xBA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Add with carry and set flags: w, w, w */
    fun adcs(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x3A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract with carry: x, x, x */
    fun sbc(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract with carry: w, w, w */
    fun sbc(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract with carry and set flags: x, x, x */
    fun sbcs(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xFA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Subtract with carry and set flags: w, w, w */
    fun sbcs(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x7A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Negate (alias: SUB Rd, ZR, Rm): x, x */
    fun neg(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xCB0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Negate (alias: SUB Rd, ZR, Rm): w, w */
    fun neg(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x4B0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Negate and set flags (alias: SUBS Rd, ZR, Rm): x, x */
    fun negs(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEB0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Negate and set flags (alias: SUBS Rd, ZR, Rm): w, w */
    fun negs(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6B0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Bitwise AND: x, x, x */
    fun and_(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x8A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise AND: w, w, w */
    fun and_(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x0A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise AND and set flags: x, x, x */
    fun ands(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise AND and set flags: w, w, w */
    fun ands(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise OR: x, x, x */
    fun orr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise OR: w, w, w */
    fun orr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise OR NOT: x, x, x */
    fun orn(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAA200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise OR NOT: w, w, w */
    fun orn(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2A200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise exclusive OR: x, x, x */
    fun eor(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xCA000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise exclusive OR: w, w, w */
    fun eor(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x4A000000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise exclusive OR NOT: x, x, x */
    fun eon(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xCA200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise exclusive OR NOT: w, w, w */
    fun eon(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x4A200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise bit clear (AND NOT): x, x, x */
    fun bic(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x8A200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise bit clear (AND NOT): w, w, w */
    fun bic(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x0A200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise bit clear and set flags: x, x, x */
    fun bics(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEA200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise bit clear and set flags: w, w, w */
    fun bics(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6A200000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Bitwise NOT (alias: ORN Rd, ZR, Rm): x, x */
    fun mvn(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAA2003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Bitwise NOT (alias: ORN Rd, ZR, Rm): w, w */
    fun mvn(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2A2003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Logical shift left (register): x, x, x */
    fun lsl(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC02000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Logical shift left (register): w, w, w */
    fun lsl(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC02000L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Logical shift right (register): x, x, x */
    fun lsr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC02400L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Logical shift right (register): w, w, w */
    fun lsr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC02400L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Arithmetic shift right (register): x, x, x */
    fun asr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC02800L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Arithmetic shift right (register): w, w, w */
    fun asr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC02800L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Rotate right (register): x, x, x */
    fun ror(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC02C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Rotate right (register): w, w, w */
    fun ror(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC02C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Multiply (alias: MADD Rd, Rn, Rm, ZR): x, x, x */
    fun mul(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B007C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Multiply (alias: MADD Rd, Rn, Rm, ZR): w, w, w */
    fun mul(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1B007C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Multiply-add (Rd = Ra + Rn * Rm): x, x, x, x */
    fun madd(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, ra: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B000000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Multiply-add (Rd = Ra + Rn * Rm): w, w, w, w */
    fun madd(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, ra: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1B000000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Multiply-subtract (Rd = Ra - Rn * Rm): x, x, x, x */
    fun msub(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, ra: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B008000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Multiply-subtract (Rd = Ra - Rn * Rm): w, w, w, w */
    fun msub(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, ra: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1B008000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Multiply-negate (alias: MSUB Rd, Rn, Rm, ZR): x, x, x */
    fun mneg(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B00FC00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Multiply-negate (alias: MSUB Rd, Rn, Rm, ZR): w, w, w */
    fun mneg(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1B00FC00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Signed multiply high (64x64→128, upper 64): x, x, x */
    fun smulh(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B407C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Unsigned multiply high (64x64→128, upper 64): x, x, x */
    fun umulh(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9BC07C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Signed multiply-add long (Xd = Xa + Wn * Wm): x, w, w, x */
    fun smaddl(rd: Arm64Register64, rn: Arm64Register32, rm: Arm64Register32, ra: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9B200000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Unsigned multiply-add long (Xd = Xa + Wn * Wm): x, w, w, x */
    fun umaddl(rd: Arm64Register64, rn: Arm64Register32, rm: Arm64Register32, ra: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9BA00000L, Arm64Format.REG4), rd, rn, rm, ra)
    }

    /** Signed divide: x, x, x */
    fun sdiv(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC00C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Signed divide: w, w, w */
    fun sdiv(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC00C00L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Unsigned divide: x, x, x */
    fun udiv(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9AC00800L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Unsigned divide: w, w, w */
    fun udiv(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1AC00800L, Arm64Format.REG3), rd, rn, rm)
    }

    /** Move register (alias: ORR Rd, ZR, Rm): x, x */
    fun mov(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAA0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Move register (alias: ORR Rd, ZR, Rm): w, w */
    fun mov(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2A0003E0L, Arm64Format.RD_RM), rd, rn)
    }

    /** Move wide with zero: x, imm16 */
    fun movz(rd: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xD2800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Move wide with zero: w, imm16 */
    fun movz(rd: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x52800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Move wide with keep: x, imm16 */
    fun movk(rd: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF2800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Move wide with keep: w, imm16 */
    fun movk(rd: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x72800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Move wide with NOT: x, imm16 */
    fun movn(rd: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x92800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Move wide with NOT: w, imm16 */
    fun movn(rd: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x12800000L, Arm64Format.MOVE_WIDE), rd, imm)
    }

    /** Sign-extend word to 64-bit (alias: SBFM Xd, Xn, #0, #31): x, w */
    fun sxtw(rd: Arm64Register64, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x93407C00L, Arm64Format.REG2), rd, rn)
    }

    /** Sign-extend halfword (alias: SBFM Rd, Rn, #0, #15): x, w */
    fun sxth(rd: Arm64Register64, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x93403C00L, Arm64Format.REG2), rd, rn)
    }

    /** Sign-extend halfword (alias: SBFM Rd, Rn, #0, #15): w, w */
    fun sxth(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x13003C00L, Arm64Format.REG2), rd, rn)
    }

    /** Sign-extend byte (alias: SBFM Rd, Rn, #0, #7): x, w */
    fun sxtb(rd: Arm64Register64, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x93401C00L, Arm64Format.REG2), rd, rn)
    }

    /** Sign-extend byte (alias: SBFM Rd, Rn, #0, #7): w, w */
    fun sxtb(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x13001C00L, Arm64Format.REG2), rd, rn)
    }

    /** Unsigned extend byte (alias: UBFM Wd, Wn, #0, #7): w, w */
    fun uxtb(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x53001C00L, Arm64Format.REG2), rd, rn)
    }

    /** Unsigned extend halfword (alias: UBFM Wd, Wn, #0, #15): w, w */
    fun uxth(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x53003C00L, Arm64Format.REG2), rd, rn)
    }

    /** Count leading sign bits: x, x */
    fun cls(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC01400L, Arm64Format.REG2), rd, rn)
    }

    /** Count leading sign bits: w, w */
    fun cls(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5AC01400L, Arm64Format.REG2), rd, rn)
    }

    /** Count leading zeros: x, x */
    fun clz(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC01000L, Arm64Format.REG2), rd, rn)
    }

    /** Count leading zeros: w, w */
    fun clz(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5AC01000L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bits: x, x */
    fun rbit(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC00000L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bits: w, w */
    fun rbit(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5AC00000L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bytes: x, x */
    fun rev(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC00C00L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bytes: w, w */
    fun rev(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5AC00800L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bytes in 16-bit halfwords: x, x */
    fun rev16(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC00400L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bytes in 16-bit halfwords: w, w */
    fun rev16(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x5AC00400L, Arm64Format.REG2), rd, rn)
    }

    /** Reverse bytes in 32-bit words: x, x */
    fun rev32(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xDAC00800L, Arm64Format.REG2), rd, rn)
    }

    /** Compare (alias: SUBS ZR, Rn, Rm/imm): x, x */
    fun cmp(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEB00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Compare (alias: SUBS ZR, Rn, Rm/imm): w, w */
    fun cmp(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6B00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Compare (alias: SUBS ZR, Rn, Rm/imm): x, imm12 */
    fun cmp(rd: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF100001FL, Arm64Format.RN_IMM12), rd, imm)
    }

    /** Compare (alias: SUBS ZR, Rn, Rm/imm): w, imm12 */
    fun cmp(rd: Arm64Register32, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x7100001FL, Arm64Format.RN_IMM12), rd, imm)
    }

    /** Compare negative (alias: ADDS ZR, Rn, Rm): x, x */
    fun cmn(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xAB00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Compare negative (alias: ADDS ZR, Rn, Rm): w, w */
    fun cmn(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x2B00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Test bits (alias: ANDS ZR, Rn, Rm): x, x */
    fun tst(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xEA00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Test bits (alias: ANDS ZR, Rn, Rm): w, w */
    fun tst(rd: Arm64Register32, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x6A00001FL, Arm64Format.RN_RM), rd, rn)
    }

    /** Conditional select: x, x, x, cond */
    fun csel(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x9A800000L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select: w, w, w, cond */
    fun csel(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x1A800000L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select increment: x, x, x, cond */
    fun csinc(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x9A800400L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select increment: w, w, w, cond */
    fun csinc(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x1A800400L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select invert: x, x, x, cond */
    fun csinv(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0xDA800000L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select invert: w, w, w, cond */
    fun csinv(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x5A800000L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select negate: x, x, x, cond */
    fun csneg(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0xDA800400L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional select negate: w, w, w, cond */
    fun csneg(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x5A800400L, Arm64Format.CSEL), rd, rn, rm, cond)
    }

    /** Conditional set (alias: CSINC Rd, ZR, ZR, invert(cond)): x, cond */
    fun cset(rd: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x9A9F07E0L, Arm64Format.RD_COND), rd, cond)
    }

    /** Conditional set (alias: CSINC Rd, ZR, ZR, invert(cond)): w, cond */
    fun cset(rd: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x1A9F07E0L, Arm64Format.RD_COND), rd, cond)
    }

    /** Conditional set mask (alias: CSINV Rd, ZR, ZR, invert(cond)): x, cond */
    fun csetm(rd: Arm64Register64, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0xDA9F03E0L, Arm64Format.RD_COND), rd, cond)
    }

    /** Conditional set mask (alias: CSINV Rd, ZR, ZR, invert(cond)): w, cond */
    fun csetm(rd: Arm64Register32, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x5A9F03E0L, Arm64Format.RD_COND), rd, cond)
    }

    /** Branch (unconditional, 26-bit offset): label */
    fun b(label: String) {
        encodeArm64(Arm64EncodingInfo(0x14000000L, Arm64Format.BRANCH26), label)
    }

    /** Branch with link (call, 26-bit offset): label */
    fun bl(label: String) {
        encodeArm64(Arm64EncodingInfo(0x94000000L, Arm64Format.BRANCH26), label)
    }

    /** Branch conditional (19-bit offset): cond, label */
    fun bCond(cond: Arm64Condition, label: String) {
        encodeArm64(Arm64EncodingInfo(0x54000000L, Arm64Format.BRANCH_COND), cond, label)
    }

    /** Compare and branch if zero: x, label */
    fun cbz(rd: Arm64Register64, label: String) {
        encodeArm64(Arm64EncodingInfo(0xB4000000L, Arm64Format.CBRANCH), rd, label)
    }

    /** Compare and branch if zero: w, label */
    fun cbz(rd: Arm64Register32, label: String) {
        encodeArm64(Arm64EncodingInfo(0x34000000L, Arm64Format.CBRANCH), rd, label)
    }

    /** Compare and branch if not zero: x, label */
    fun cbnz(rd: Arm64Register64, label: String) {
        encodeArm64(Arm64EncodingInfo(0xB5000000L, Arm64Format.CBRANCH), rd, label)
    }

    /** Compare and branch if not zero: w, label */
    fun cbnz(rd: Arm64Register32, label: String) {
        encodeArm64(Arm64EncodingInfo(0x35000000L, Arm64Format.CBRANCH), rd, label)
    }

    /** Test bit and branch if zero: x, imm, label */
    fun tbz(rd: Arm64Register64, imm: Int, label: String) {
        encodeArm64(Arm64EncodingInfo(0x36000000L, Arm64Format.TBRANCH), rd, imm, label)
    }

    /** Test bit and branch if zero: w, imm, label */
    fun tbz(rd: Arm64Register32, imm: Int, label: String) {
        encodeArm64(Arm64EncodingInfo(0x36000000L, Arm64Format.TBRANCH), rd, imm, label)
    }

    /** Test bit and branch if not zero: x, imm, label */
    fun tbnz(rd: Arm64Register64, imm: Int, label: String) {
        encodeArm64(Arm64EncodingInfo(0x37000000L, Arm64Format.TBRANCH), rd, imm, label)
    }

    /** Test bit and branch if not zero: w, imm, label */
    fun tbnz(rd: Arm64Register32, imm: Int, label: String) {
        encodeArm64(Arm64EncodingInfo(0x37000000L, Arm64Format.TBRANCH), rd, imm, label)
    }

    /** Branch to register: x */
    fun br(rd: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xD61F0000L, Arm64Format.REG1), rd)
    }

    /** Branch with link to register (indirect call): x */
    fun blr(rd: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xD63F0000L, Arm64Format.REG1), rd)
    }

    /** Return from subroutine: x */
    fun ret(rd: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xD65F0000L, Arm64Format.REG1), rd)
    }

    /** Load register: x, x, imm12 */
    fun ldr(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF9400000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register: w, x, imm12 */
    fun ldr(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB9400000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Store register: x, x, imm12 */
    fun str(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF9000000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Store register: w, x, imm12 */
    fun str(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB9000000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register (unscaled offset): x, x, imm */
    fun ldur(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF8400000L, Arm64Format.LDST_SIMM9), rd, rn, imm)
    }

    /** Load register (unscaled offset): w, x, imm */
    fun ldur(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB8400000L, Arm64Format.LDST_SIMM9), rd, rn, imm)
    }

    /** Store register (unscaled offset): x, x, imm */
    fun stur(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xF8000000L, Arm64Format.LDST_SIMM9), rd, rn, imm)
    }

    /** Store register (unscaled offset): w, x, imm */
    fun stur(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB8000000L, Arm64Format.LDST_SIMM9), rd, rn, imm)
    }

    /** Load register byte: w, x, imm12 */
    fun ldrb(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x39400000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Store register byte: w, x, imm12 */
    fun strb(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x39000000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register halfword: w, x, imm12 */
    fun ldrh(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x79400000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Store register halfword: w, x, imm12 */
    fun strh(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x79000000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register signed byte: x, x, imm12 */
    fun ldrsb(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x39800000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register signed byte: w, x, imm12 */
    fun ldrsb(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x39C00000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register signed halfword: x, x, imm12 */
    fun ldrsh(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x79800000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register signed halfword: w, x, imm12 */
    fun ldrsh(rd: Arm64Register32, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x79C00000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Load register signed word: x, x, imm12 */
    fun ldrsw(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xB9800000L, Arm64Format.LDST_UOFF), rd, rn, imm)
    }

    /** Store pair of registers: x, x, x, imm */
    fun stp(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xA9000000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Store pair of registers: w, w, x, imm */
    fun stp(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x29000000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Load pair of registers: x, x, x, imm */
    fun ldp(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xA9400000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Load pair of registers: w, w, x, imm */
    fun ldp(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x29400000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Store pair pre-index: x, x, x, imm */
    fun stpPre(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xA9800000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Load pair post-index: x, x, x, imm */
    fun ldpPost(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xA8C00000L, Arm64Format.LDST_PAIR), rd, rn, rm, imm)
    }

    /** Load-acquire register: x, x */
    fun ldar(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xC8DFFC00L, Arm64Format.LDST_ACQUIRE), rd, rn)
    }

    /** Load-acquire register: w, x */
    fun ldar(rd: Arm64Register32, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x88DFFC00L, Arm64Format.LDST_ACQUIRE), rd, rn)
    }

    /** Store-release register: x, x */
    fun stlr(rd: Arm64Register64, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0xC89FFC00L, Arm64Format.LDST_ACQUIRE), rd, rn)
    }

    /** Store-release register: w, x */
    fun stlr(rd: Arm64Register32, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x889FFC00L, Arm64Format.LDST_ACQUIRE), rd, rn)
    }

    /** Floating-point add: d, d, d */
    fun fadd(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E602800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point add: s, s, s */
    fun fadd(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E202800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point subtract: d, d, d */
    fun fsub(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E603800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point subtract: s, s, s */
    fun fsub(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E203800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point multiply: d, d, d */
    fun fmul(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E600800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point multiply: s, s, s */
    fun fmul(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E200800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point divide: d, d, d */
    fun fdiv(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E601800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point divide: s, s, s */
    fun fdiv(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E201800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point negate: d, d */
    fun fneg(fd: Arm64VecD, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E614000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point negate: s, s */
    fun fneg(fd: Arm64VecS, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E214000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point absolute value: d, d */
    fun fabs(fd: Arm64VecD, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E60C000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point absolute value: s, s */
    fun fabs(fd: Arm64VecS, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E20C000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point square root: d, d */
    fun fsqrt(fd: Arm64VecD, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E61C000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point square root: s, s */
    fun fsqrt(fd: Arm64VecS, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E21C000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point fused multiply-add (Fd = Fa + Fn * Fm): d, d, d, d */
    fun fmadd(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, fa: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1F400000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point fused multiply-add (Fd = Fa + Fn * Fm): s, s, s, s */
    fun fmadd(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, fa: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1F000000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point fused multiply-subtract: d, d, d, d */
    fun fmsub(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, fa: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1F408000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point fused multiply-subtract: s, s, s, s */
    fun fmsub(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, fa: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1F008000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point move: d, d */
    fun fmov(fd: Arm64VecD, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E604000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point move: s, s */
    fun fmov(fd: Arm64VecS, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E204000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point move: d, x */
    fun fmov(fd: Arm64VecD, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9E670000L, Arm64Format.REG2), fd, rn)
    }

    /** Floating-point move: s, w */
    fun fmov(fd: Arm64VecS, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1E270000L, Arm64Format.REG2), fd, rn)
    }

    /** Floating-point move: x, d */
    fun fmov(rd: Arm64Register64, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x9E660000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point move: w, s */
    fun fmov(rd: Arm64Register32, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E260000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point compare: d, d */
    fun fcmp(fd: Arm64VecD, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E602000L, Arm64Format.FP_CMP), fd, fn)
    }

    /** Floating-point compare: s, s */
    fun fcmp(fd: Arm64VecS, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E202000L, Arm64Format.FP_CMP), fd, fn)
    }

    /** Floating-point compare to zero: d */
    fun fcmpZero(fd: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E602008L, Arm64Format.FP_CMP_ZERO), fd)
    }

    /** Floating-point compare to zero: s */
    fun fcmpZero(fd: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E202008L, Arm64Format.FP_CMP_ZERO), fd)
    }

    /** Floating-point conditional select: d, d, d, cond */
    fun fcsel(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x1E600C00L, Arm64Format.CSEL), fd, fn, fm, cond)
    }

    /** Floating-point conditional select: s, s, s, cond */
    fun fcsel(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, cond: Arm64Condition) {
        encodeArm64(Arm64EncodingInfo(0x1E200C00L, Arm64Format.CSEL), fd, fn, fm, cond)
    }

    /** Signed integer to floating-point: d, x */
    fun scvtf(fd: Arm64VecD, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9E620000L, Arm64Format.REG2), fd, rn)
    }

    /** Signed integer to floating-point: d, w */
    fun scvtf(fd: Arm64VecD, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1E620000L, Arm64Format.REG2), fd, rn)
    }

    /** Signed integer to floating-point: s, w */
    fun scvtf(fd: Arm64VecS, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1E220000L, Arm64Format.REG2), fd, rn)
    }

    /** Signed integer to floating-point: s, x */
    fun scvtf(fd: Arm64VecS, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9E220000L, Arm64Format.REG2), fd, rn)
    }

    /** Unsigned integer to floating-point: d, x */
    fun ucvtf(fd: Arm64VecD, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9E630000L, Arm64Format.REG2), fd, rn)
    }

    /** Unsigned integer to floating-point: d, w */
    fun ucvtf(fd: Arm64VecD, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1E630000L, Arm64Format.REG2), fd, rn)
    }

    /** Unsigned integer to floating-point: s, w */
    fun ucvtf(fd: Arm64VecS, rn: Arm64Register32) {
        encodeArm64(Arm64EncodingInfo(0x1E230000L, Arm64Format.REG2), fd, rn)
    }

    /** Unsigned integer to floating-point: s, x */
    fun ucvtf(fd: Arm64VecS, rn: Arm64Register64) {
        encodeArm64(Arm64EncodingInfo(0x9E230000L, Arm64Format.REG2), fd, rn)
    }

    /** Floating-point to signed integer, round toward zero: x, d */
    fun fcvtzs(rd: Arm64Register64, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x9E780000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to signed integer, round toward zero: w, s */
    fun fcvtzs(rd: Arm64Register32, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E380000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to signed integer, round toward zero: w, d */
    fun fcvtzs(rd: Arm64Register32, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E780000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to signed integer, round toward zero: x, s */
    fun fcvtzs(rd: Arm64Register64, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x9E380000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to unsigned integer, round toward zero: x, d */
    fun fcvtzu(rd: Arm64Register64, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x9E790000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to unsigned integer, round toward zero: w, s */
    fun fcvtzu(rd: Arm64Register32, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E390000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to unsigned integer, round toward zero: w, d */
    fun fcvtzu(rd: Arm64Register32, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E790000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point to unsigned integer, round toward zero: x, s */
    fun fcvtzu(rd: Arm64Register64, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x9E390000L, Arm64Format.REG2), rd, fn)
    }

    /** Floating-point convert precision: d, s */
    fun fcvt(fd: Arm64VecD, fn: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E22C000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point convert precision: s, d */
    fun fcvt(fd: Arm64VecS, fn: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E624000L, Arm64Format.REG2), fd, fn)
    }

    /** Floating-point load register: d, x, imm12 */
    fun fldr(fd: Arm64VecD, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xFD400000L, Arm64Format.LDST_UOFF), fd, rn, imm)
    }

    /** Floating-point load register: s, x, imm12 */
    fun fldr(fd: Arm64VecS, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xBD400000L, Arm64Format.LDST_UOFF), fd, rn, imm)
    }

    /** Floating-point store register: d, x, imm12 */
    fun fstr(fd: Arm64VecD, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xFD000000L, Arm64Format.LDST_UOFF), fd, rn, imm)
    }

    /** Floating-point store register: s, x, imm12 */
    fun fstr(fd: Arm64VecS, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xBD000000L, Arm64Format.LDST_UOFF), fd, rn, imm)
    }

    /** Floating-point load register (unscaled offset): d, x, imm */
    fun fldur(fd: Arm64VecD, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xFC400000L, Arm64Format.LDST_SIMM9), fd, rn, imm)
    }

    /** Floating-point store register (unscaled offset): d, x, imm */
    fun fstur(fd: Arm64VecD, rn: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xFC000000L, Arm64Format.LDST_SIMM9), fd, rn, imm)
    }

    /** Floating-point store pair: d, d, x, imm */
    fun fstp(fd: Arm64VecD, fn: Arm64VecD, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x6D000000L, Arm64Format.LDST_PAIR), fd, fn, rm, imm)
    }

    /** Floating-point load pair: d, d, x, imm */
    fun fldp(fd: Arm64VecD, fn: Arm64VecD, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x6D400000L, Arm64Format.LDST_PAIR), fd, fn, rm, imm)
    }

    /** Floating-point store pair pre-index: d, d, x, imm */
    fun fstpPre(fd: Arm64VecD, fn: Arm64VecD, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x6D800000L, Arm64Format.LDST_PAIR), fd, fn, rm, imm)
    }

    /** Floating-point load pair post-index: d, d, x, imm */
    fun fldpPost(fd: Arm64VecD, fn: Arm64VecD, rm: Arm64Register64, imm: Int) {
        encodeArm64(Arm64EncodingInfo(0x6CC00000L, Arm64Format.LDST_PAIR), fd, fn, rm, imm)
    }

    /** Floating-point minimum: d, d, d */
    fun fmin(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E605800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point minimum: s, s, s */
    fun fmin(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E205800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point maximum: d, d, d */
    fun fmax(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1E604800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point maximum: s, s, s */
    fun fmax(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1E204800L, Arm64Format.REG3), fd, fn, fm)
    }

    /** Floating-point negated fused multiply-add: d, d, d, d */
    fun fnmadd(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, fa: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1F600000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point negated fused multiply-add: s, s, s, s */
    fun fnmadd(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, fa: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1F200000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point negated fused multiply-subtract: d, d, d, d */
    fun fnmsub(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, fa: Arm64VecD) {
        encodeArm64(Arm64EncodingInfo(0x1F608000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** Floating-point negated fused multiply-subtract: s, s, s, s */
    fun fnmsub(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, fa: Arm64VecS) {
        encodeArm64(Arm64EncodingInfo(0x1F208000L, Arm64Format.REG4), fd, fn, fm, fa)
    }

    /** No operation:  */
    fun nop() {
        encodeArm64(Arm64EncodingInfo(0xD503201FL, Arm64Format.NO_OPERAND))
    }

    /** Breakpoint: imm16 */
    fun brk(imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xD4200000L, Arm64Format.EXCEPTION), imm)
    }

    /** Supervisor call: imm16 */
    fun svc(imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xD4000001L, Arm64Format.EXCEPTION), imm)
    }

    /** Halt: imm16 */
    fun hlt(imm: Int) {
        encodeArm64(Arm64EncodingInfo(0xD4400000L, Arm64Format.EXCEPTION), imm)
    }

    /** Form PC-relative address (±1MB): x, label */
    fun adr(rd: Arm64Register64, label: String) {
        encodeArm64(Arm64EncodingInfo(0x10000000L, Arm64Format.ADR), rd, label)
    }

    /** Form PC-relative address to 4KB page (±4GB): x, label */
    fun adrp(rd: Arm64Register64, label: String) {
        encodeArm64(Arm64EncodingInfo(0x90000000L, Arm64Format.ADR), rd, label)
    }

    /** Clear exclusive monitor:  */
    fun clrex() {
        encodeArm64(Arm64EncodingInfo(0xD503305FL, Arm64Format.NO_OPERAND))
    }

    /** Instruction synchronization barrier:  */
    fun isb() {
        encodeArm64(Arm64EncodingInfo(0xD5033FDFL, Arm64Format.NO_OPERAND))
    }

    /** Data synchronization barrier:  */
    fun dsb() {
        encodeArm64(Arm64EncodingInfo(0xD503309FL, Arm64Format.NO_OPERAND))
    }

    /** Data memory barrier:  */
    fun dmb() {
        encodeArm64(Arm64EncodingInfo(0xD50330BFL, Arm64Format.NO_OPERAND))
    }

}

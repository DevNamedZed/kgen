package org.kgen.target.arm64

/**
 * AArch64 register. All registers are singleton instances discoverable via `Arm64Register.*`.
 *
 * ARM64 has 31 general-purpose registers (X0-X30 / W0-W30), SP, ZR (zero register),
 * and 32 SIMD/FP registers (V0-V31 / S0-S31 / D0-D31 / Q0-Q31).
 *
 * Companion fields are typed to the appropriate interface (e.g. X0 is [Arm64Register64],
 * W0 is [Arm64Register32], D0 is [Arm64VecD]) so assembler overloads resolve unambiguously.
 */
class Arm64Register private constructor(
    private val _name: String,
    private val _bits: Int,
    internal val encoding: Int,
) : Arm64Register32, Arm64Register64, Arm64VecS, Arm64VecD, Arm64VecQ {

    override fun name(): String = _name
    override fun bits(): Int = _bits
    override fun encoding(): Int = encoding

    override fun toString(): String = _name

    companion object {
        // 64-bit general purpose (X0-X30)
        @JvmField val X0:  Arm64Register64 = Arm64Register("x0",  64, 0)
        @JvmField val X1:  Arm64Register64 = Arm64Register("x1",  64, 1)
        @JvmField val X2:  Arm64Register64 = Arm64Register("x2",  64, 2)
        @JvmField val X3:  Arm64Register64 = Arm64Register("x3",  64, 3)
        @JvmField val X4:  Arm64Register64 = Arm64Register("x4",  64, 4)
        @JvmField val X5:  Arm64Register64 = Arm64Register("x5",  64, 5)
        @JvmField val X6:  Arm64Register64 = Arm64Register("x6",  64, 6)
        @JvmField val X7:  Arm64Register64 = Arm64Register("x7",  64, 7)
        @JvmField val X8:  Arm64Register64 = Arm64Register("x8",  64, 8)
        @JvmField val X9:  Arm64Register64 = Arm64Register("x9",  64, 9)
        @JvmField val X10: Arm64Register64 = Arm64Register("x10", 64, 10)
        @JvmField val X11: Arm64Register64 = Arm64Register("x11", 64, 11)
        @JvmField val X12: Arm64Register64 = Arm64Register("x12", 64, 12)
        @JvmField val X13: Arm64Register64 = Arm64Register("x13", 64, 13)
        @JvmField val X14: Arm64Register64 = Arm64Register("x14", 64, 14)
        @JvmField val X15: Arm64Register64 = Arm64Register("x15", 64, 15)
        @JvmField val X16: Arm64Register64 = Arm64Register("x16", 64, 16)
        @JvmField val X17: Arm64Register64 = Arm64Register("x17", 64, 17)
        @JvmField val X18: Arm64Register64 = Arm64Register("x18", 64, 18)
        @JvmField val X19: Arm64Register64 = Arm64Register("x19", 64, 19)
        @JvmField val X20: Arm64Register64 = Arm64Register("x20", 64, 20)
        @JvmField val X21: Arm64Register64 = Arm64Register("x21", 64, 21)
        @JvmField val X22: Arm64Register64 = Arm64Register("x22", 64, 22)
        @JvmField val X23: Arm64Register64 = Arm64Register("x23", 64, 23)
        @JvmField val X24: Arm64Register64 = Arm64Register("x24", 64, 24)
        @JvmField val X25: Arm64Register64 = Arm64Register("x25", 64, 25)
        @JvmField val X26: Arm64Register64 = Arm64Register("x26", 64, 26)
        @JvmField val X27: Arm64Register64 = Arm64Register("x27", 64, 27)
        @JvmField val X28: Arm64Register64 = Arm64Register("x28", 64, 28)
        @JvmField val X29: Arm64Register64 = Arm64Register("x29", 64, 29) // FP (frame pointer)
        @JvmField val X30: Arm64Register64 = Arm64Register("x30", 64, 30) // LR (link register)

        @JvmField val FP: Arm64Register64 = X29
        @JvmField val LR: Arm64Register64 = X30

        // SP and ZR share encoding 31, distinguished by context
        @JvmField val SP:  Arm64Register64 = Arm64Register("sp",  64, 31)
        @JvmField val XZR: Arm64Register64 = Arm64Register("xzr", 64, 31)

        // 32-bit general purpose (W0-W30)
        @JvmField val W0:  Arm64Register32 = Arm64Register("w0",  32, 0)
        @JvmField val W1:  Arm64Register32 = Arm64Register("w1",  32, 1)
        @JvmField val W2:  Arm64Register32 = Arm64Register("w2",  32, 2)
        @JvmField val W3:  Arm64Register32 = Arm64Register("w3",  32, 3)
        @JvmField val W4:  Arm64Register32 = Arm64Register("w4",  32, 4)
        @JvmField val W5:  Arm64Register32 = Arm64Register("w5",  32, 5)
        @JvmField val W6:  Arm64Register32 = Arm64Register("w6",  32, 6)
        @JvmField val W7:  Arm64Register32 = Arm64Register("w7",  32, 7)
        @JvmField val W8:  Arm64Register32 = Arm64Register("w8",  32, 8)
        @JvmField val W9:  Arm64Register32 = Arm64Register("w9",  32, 9)
        @JvmField val W10: Arm64Register32 = Arm64Register("w10", 32, 10)
        @JvmField val W11: Arm64Register32 = Arm64Register("w11", 32, 11)
        @JvmField val W12: Arm64Register32 = Arm64Register("w12", 32, 12)
        @JvmField val W13: Arm64Register32 = Arm64Register("w13", 32, 13)
        @JvmField val W14: Arm64Register32 = Arm64Register("w14", 32, 14)
        @JvmField val W15: Arm64Register32 = Arm64Register("w15", 32, 15)
        @JvmField val W16: Arm64Register32 = Arm64Register("w16", 32, 16)
        @JvmField val W17: Arm64Register32 = Arm64Register("w17", 32, 17)
        @JvmField val W18: Arm64Register32 = Arm64Register("w18", 32, 18)
        @JvmField val W19: Arm64Register32 = Arm64Register("w19", 32, 19)
        @JvmField val W20: Arm64Register32 = Arm64Register("w20", 32, 20)
        @JvmField val W21: Arm64Register32 = Arm64Register("w21", 32, 21)
        @JvmField val W22: Arm64Register32 = Arm64Register("w22", 32, 22)
        @JvmField val W23: Arm64Register32 = Arm64Register("w23", 32, 23)
        @JvmField val W24: Arm64Register32 = Arm64Register("w24", 32, 24)
        @JvmField val W25: Arm64Register32 = Arm64Register("w25", 32, 25)
        @JvmField val W26: Arm64Register32 = Arm64Register("w26", 32, 26)
        @JvmField val W27: Arm64Register32 = Arm64Register("w27", 32, 27)
        @JvmField val W28: Arm64Register32 = Arm64Register("w28", 32, 28)
        @JvmField val W29: Arm64Register32 = Arm64Register("w29", 32, 29)
        @JvmField val W30: Arm64Register32 = Arm64Register("w30", 32, 30)
        @JvmField val WZR: Arm64Register32 = Arm64Register("wzr", 32, 31)

        // SIMD/FP single-precision (S0-S31)
        @JvmField val S0:  Arm64VecS = Arm64Register("s0",  32, 0)
        @JvmField val S1:  Arm64VecS = Arm64Register("s1",  32, 1)
        @JvmField val S2:  Arm64VecS = Arm64Register("s2",  32, 2)
        @JvmField val S3:  Arm64VecS = Arm64Register("s3",  32, 3)
        @JvmField val S4:  Arm64VecS = Arm64Register("s4",  32, 4)
        @JvmField val S5:  Arm64VecS = Arm64Register("s5",  32, 5)
        @JvmField val S6:  Arm64VecS = Arm64Register("s6",  32, 6)
        @JvmField val S7:  Arm64VecS = Arm64Register("s7",  32, 7)
        @JvmField val S8:  Arm64VecS = Arm64Register("s8",  32, 8)
        @JvmField val S9:  Arm64VecS = Arm64Register("s9",  32, 9)
        @JvmField val S10: Arm64VecS = Arm64Register("s10", 32, 10)
        @JvmField val S11: Arm64VecS = Arm64Register("s11", 32, 11)
        @JvmField val S12: Arm64VecS = Arm64Register("s12", 32, 12)
        @JvmField val S13: Arm64VecS = Arm64Register("s13", 32, 13)
        @JvmField val S14: Arm64VecS = Arm64Register("s14", 32, 14)
        @JvmField val S15: Arm64VecS = Arm64Register("s15", 32, 15)
        @JvmField val S16: Arm64VecS = Arm64Register("s16", 32, 16)
        @JvmField val S17: Arm64VecS = Arm64Register("s17", 32, 17)
        @JvmField val S18: Arm64VecS = Arm64Register("s18", 32, 18)
        @JvmField val S19: Arm64VecS = Arm64Register("s19", 32, 19)
        @JvmField val S20: Arm64VecS = Arm64Register("s20", 32, 20)
        @JvmField val S21: Arm64VecS = Arm64Register("s21", 32, 21)
        @JvmField val S22: Arm64VecS = Arm64Register("s22", 32, 22)
        @JvmField val S23: Arm64VecS = Arm64Register("s23", 32, 23)
        @JvmField val S24: Arm64VecS = Arm64Register("s24", 32, 24)
        @JvmField val S25: Arm64VecS = Arm64Register("s25", 32, 25)
        @JvmField val S26: Arm64VecS = Arm64Register("s26", 32, 26)
        @JvmField val S27: Arm64VecS = Arm64Register("s27", 32, 27)
        @JvmField val S28: Arm64VecS = Arm64Register("s28", 32, 28)
        @JvmField val S29: Arm64VecS = Arm64Register("s29", 32, 29)
        @JvmField val S30: Arm64VecS = Arm64Register("s30", 32, 30)
        @JvmField val S31: Arm64VecS = Arm64Register("s31", 32, 31)

        // SIMD/FP double-precision (D0-D31)
        @JvmField val D0:  Arm64VecD = Arm64Register("d0",  64, 0)
        @JvmField val D1:  Arm64VecD = Arm64Register("d1",  64, 1)
        @JvmField val D2:  Arm64VecD = Arm64Register("d2",  64, 2)
        @JvmField val D3:  Arm64VecD = Arm64Register("d3",  64, 3)
        @JvmField val D4:  Arm64VecD = Arm64Register("d4",  64, 4)
        @JvmField val D5:  Arm64VecD = Arm64Register("d5",  64, 5)
        @JvmField val D6:  Arm64VecD = Arm64Register("d6",  64, 6)
        @JvmField val D7:  Arm64VecD = Arm64Register("d7",  64, 7)
        @JvmField val D8:  Arm64VecD = Arm64Register("d8",  64, 8)
        @JvmField val D9:  Arm64VecD = Arm64Register("d9",  64, 9)
        @JvmField val D10: Arm64VecD = Arm64Register("d10", 64, 10)
        @JvmField val D11: Arm64VecD = Arm64Register("d11", 64, 11)
        @JvmField val D12: Arm64VecD = Arm64Register("d12", 64, 12)
        @JvmField val D13: Arm64VecD = Arm64Register("d13", 64, 13)
        @JvmField val D14: Arm64VecD = Arm64Register("d14", 64, 14)
        @JvmField val D15: Arm64VecD = Arm64Register("d15", 64, 15)
        @JvmField val D16: Arm64VecD = Arm64Register("d16", 64, 16)
        @JvmField val D17: Arm64VecD = Arm64Register("d17", 64, 17)
        @JvmField val D18: Arm64VecD = Arm64Register("d18", 64, 18)
        @JvmField val D19: Arm64VecD = Arm64Register("d19", 64, 19)
        @JvmField val D20: Arm64VecD = Arm64Register("d20", 64, 20)
        @JvmField val D21: Arm64VecD = Arm64Register("d21", 64, 21)
        @JvmField val D22: Arm64VecD = Arm64Register("d22", 64, 22)
        @JvmField val D23: Arm64VecD = Arm64Register("d23", 64, 23)
        @JvmField val D24: Arm64VecD = Arm64Register("d24", 64, 24)
        @JvmField val D25: Arm64VecD = Arm64Register("d25", 64, 25)
        @JvmField val D26: Arm64VecD = Arm64Register("d26", 64, 26)
        @JvmField val D27: Arm64VecD = Arm64Register("d27", 64, 27)
        @JvmField val D28: Arm64VecD = Arm64Register("d28", 64, 28)
        @JvmField val D29: Arm64VecD = Arm64Register("d29", 64, 29)
        @JvmField val D30: Arm64VecD = Arm64Register("d30", 64, 30)
        @JvmField val D31: Arm64VecD = Arm64Register("d31", 64, 31)

        // SIMD 128-bit (Q0-Q31)
        @JvmField val Q0:  Arm64VecQ = Arm64Register("q0",  128, 0)
        @JvmField val Q1:  Arm64VecQ = Arm64Register("q1",  128, 1)
        @JvmField val Q2:  Arm64VecQ = Arm64Register("q2",  128, 2)
        @JvmField val Q3:  Arm64VecQ = Arm64Register("q3",  128, 3)
        @JvmField val Q4:  Arm64VecQ = Arm64Register("q4",  128, 4)
        @JvmField val Q5:  Arm64VecQ = Arm64Register("q5",  128, 5)
        @JvmField val Q6:  Arm64VecQ = Arm64Register("q6",  128, 6)
        @JvmField val Q7:  Arm64VecQ = Arm64Register("q7",  128, 7)
        @JvmField val Q8:  Arm64VecQ = Arm64Register("q8",  128, 8)
        @JvmField val Q9:  Arm64VecQ = Arm64Register("q9",  128, 9)
        @JvmField val Q10: Arm64VecQ = Arm64Register("q10", 128, 10)
        @JvmField val Q11: Arm64VecQ = Arm64Register("q11", 128, 11)
        @JvmField val Q12: Arm64VecQ = Arm64Register("q12", 128, 12)
        @JvmField val Q13: Arm64VecQ = Arm64Register("q13", 128, 13)
        @JvmField val Q14: Arm64VecQ = Arm64Register("q14", 128, 14)
        @JvmField val Q15: Arm64VecQ = Arm64Register("q15", 128, 15)
        @JvmField val Q16: Arm64VecQ = Arm64Register("q16", 128, 16)
        @JvmField val Q17: Arm64VecQ = Arm64Register("q17", 128, 17)
        @JvmField val Q18: Arm64VecQ = Arm64Register("q18", 128, 18)
        @JvmField val Q19: Arm64VecQ = Arm64Register("q19", 128, 19)
        @JvmField val Q20: Arm64VecQ = Arm64Register("q20", 128, 20)
        @JvmField val Q21: Arm64VecQ = Arm64Register("q21", 128, 21)
        @JvmField val Q22: Arm64VecQ = Arm64Register("q22", 128, 22)
        @JvmField val Q23: Arm64VecQ = Arm64Register("q23", 128, 23)
        @JvmField val Q24: Arm64VecQ = Arm64Register("q24", 128, 24)
        @JvmField val Q25: Arm64VecQ = Arm64Register("q25", 128, 25)
        @JvmField val Q26: Arm64VecQ = Arm64Register("q26", 128, 26)
        @JvmField val Q27: Arm64VecQ = Arm64Register("q27", 128, 27)
        @JvmField val Q28: Arm64VecQ = Arm64Register("q28", 128, 28)
        @JvmField val Q29: Arm64VecQ = Arm64Register("q29", 128, 29)
        @JvmField val Q30: Arm64VecQ = Arm64Register("q30", 128, 30)
        @JvmField val Q31: Arm64VecQ = Arm64Register("q31", 128, 31)

        @JvmStatic fun allX(): List<Arm64Register64> = (0..30).map { byEncoding64(it) }
        @JvmStatic fun allW(): List<Arm64Register32> = (0..30).map { byEncoding32(it) }
        @JvmStatic fun allD(): List<Arm64VecD> = (0..31).map { byEncodingD(it) }
        @JvmStatic fun allS(): List<Arm64VecS> = (0..31).map { byEncodingS(it) }
        @JvmStatic fun allQ(): List<Arm64VecQ> = (0..31).map { byEncodingQ(it) }

        @JvmStatic fun byEncoding64(enc: Int): Arm64Register64 = when (enc) {
            0 -> X0; 1 -> X1; 2 -> X2; 3 -> X3; 4 -> X4; 5 -> X5; 6 -> X6; 7 -> X7
            8 -> X8; 9 -> X9; 10 -> X10; 11 -> X11; 12 -> X12; 13 -> X13; 14 -> X14; 15 -> X15
            16 -> X16; 17 -> X17; 18 -> X18; 19 -> X19; 20 -> X20; 21 -> X21; 22 -> X22; 23 -> X23
            24 -> X24; 25 -> X25; 26 -> X26; 27 -> X27; 28 -> X28; 29 -> X29; 30 -> X30; 31 -> SP
            else -> error("Invalid ARM64 register encoding: $enc")
        }

        @JvmStatic fun byEncodingD(enc: Int): Arm64VecD = when (enc) {
            0 -> D0; 1 -> D1; 2 -> D2; 3 -> D3; 4 -> D4; 5 -> D5; 6 -> D6; 7 -> D7
            8 -> D8; 9 -> D9; 10 -> D10; 11 -> D11; 12 -> D12; 13 -> D13; 14 -> D14; 15 -> D15
            16 -> D16; 17 -> D17; 18 -> D18; 19 -> D19; 20 -> D20; 21 -> D21; 22 -> D22; 23 -> D23
            24 -> D24; 25 -> D25; 26 -> D26; 27 -> D27; 28 -> D28; 29 -> D29; 30 -> D30; 31 -> D31
            else -> error("Invalid ARM64 D register encoding: $enc")
        }

        @JvmStatic fun byEncodingS(enc: Int): Arm64VecS = when (enc) {
            0 -> S0; 1 -> S1; 2 -> S2; 3 -> S3; 4 -> S4; 5 -> S5; 6 -> S6; 7 -> S7
            8 -> S8; 9 -> S9; 10 -> S10; 11 -> S11; 12 -> S12; 13 -> S13; 14 -> S14; 15 -> S15
            16 -> S16; 17 -> S17; 18 -> S18; 19 -> S19; 20 -> S20; 21 -> S21; 22 -> S22; 23 -> S23
            24 -> S24; 25 -> S25; 26 -> S26; 27 -> S27; 28 -> S28; 29 -> S29; 30 -> S30; 31 -> S31
            else -> error("Invalid ARM64 S register encoding: $enc")
        }

        @JvmStatic fun byEncodingQ(enc: Int): Arm64VecQ = when (enc) {
            0 -> Q0; 1 -> Q1; 2 -> Q2; 3 -> Q3; 4 -> Q4; 5 -> Q5; 6 -> Q6; 7 -> Q7
            8 -> Q8; 9 -> Q9; 10 -> Q10; 11 -> Q11; 12 -> Q12; 13 -> Q13; 14 -> Q14; 15 -> Q15
            16 -> Q16; 17 -> Q17; 18 -> Q18; 19 -> Q19; 20 -> Q20; 21 -> Q21; 22 -> Q22; 23 -> Q23
            24 -> Q24; 25 -> Q25; 26 -> Q26; 27 -> Q27; 28 -> Q28; 29 -> Q29; 30 -> Q30; 31 -> Q31
            else -> error("Invalid ARM64 Q register encoding: $enc")
        }

        /** Look up a V register by encoding — returns the Q register (NEON uses V prefix in disassembly). */
        @JvmStatic fun byEncodingV(enc: Int): Arm64VecQ = byEncodingQ(enc)

        @JvmStatic fun byEncoding32(enc: Int): Arm64Register32 = when (enc) {
            0 -> W0; 1 -> W1; 2 -> W2; 3 -> W3; 4 -> W4; 5 -> W5; 6 -> W6; 7 -> W7
            8 -> W8; 9 -> W9; 10 -> W10; 11 -> W11; 12 -> W12; 13 -> W13; 14 -> W14; 15 -> W15
            16 -> W16; 17 -> W17; 18 -> W18; 19 -> W19; 20 -> W20; 21 -> W21; 22 -> W22; 23 -> W23
            24 -> W24; 25 -> W25; 26 -> W26; 27 -> W27; 28 -> W28; 29 -> W29; 30 -> W30; 31 -> WZR
            else -> error("Invalid ARM64 register encoding: $enc")
        }
    }
}

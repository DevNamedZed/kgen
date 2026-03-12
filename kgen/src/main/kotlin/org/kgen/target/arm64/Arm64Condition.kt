package org.kgen.target.arm64

enum class Arm64Condition(val code: Int) {
    EQ(0x0),  // Equal (Z=1)
    NE(0x1),  // Not equal (Z=0)
    CS(0x2),  // Carry set / unsigned higher or same (C=1)
    HS(0x2),  // Alias for CS
    CC(0x3),  // Carry clear / unsigned lower (C=0)
    LO(0x3),  // Alias for CC
    MI(0x4),  // Minus / negative (N=1)
    PL(0x5),  // Plus / positive or zero (N=0)
    VS(0x6),  // Overflow (V=1)
    VC(0x7),  // No overflow (V=0)
    HI(0x8),  // Unsigned higher (C=1 and Z=0)
    LS(0x9),  // Unsigned lower or same (C=0 or Z=1)
    GE(0xA),  // Signed greater than or equal (N=V)
    LT(0xB),  // Signed less than (N!=V)
    GT(0xC),  // Signed greater than (Z=0 and N=V)
    LE(0xD),  // Signed less than or equal (Z=1 or N!=V)
    AL(0xE),  // Always
    NV(0xF),  // Always (but architecturally reserved)
    ;

    fun invert(): Arm64Condition = when (this) {
        EQ -> NE; NE -> EQ
        CS, HS -> CC; CC, LO -> CS
        MI -> PL; PL -> MI
        VS -> VC; VC -> VS
        HI -> LS; LS -> HI
        GE -> LT; LT -> GE
        GT -> LE; LE -> GT
        AL -> NV; NV -> AL
    }
}

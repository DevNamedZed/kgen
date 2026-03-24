package org.kgen.target.riscv

enum class RiscVCondition {
    EQ,
    NE,
    LT,
    GE,
    LTU,
    GEU;

    fun invert(): RiscVCondition = when (this) {
        EQ -> NE
        NE -> EQ
        LT -> GE
        GE -> LT
        LTU -> GEU
        GEU -> LTU
    }
}

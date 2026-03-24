package org.kgen.target.clr

enum class CilCondition {
    BEQ,
    BGE,
    BGT,
    BLE,
    BLT,
    BNE_UN,
    BGE_UN,
    BGT_UN,
    BLE_UN,
    BLT_UN,
    BRTRUE,
    BRFALSE;

    fun invert(): CilCondition = when (this) {
        BEQ -> BNE_UN
        BNE_UN -> BEQ
        BGE -> BLT
        BLT -> BGE
        BGT -> BLE
        BLE -> BGT
        BGE_UN -> BLT_UN
        BLT_UN -> BGE_UN
        BGT_UN -> BLE_UN
        BLE_UN -> BGT_UN
        BRTRUE -> BRFALSE
        BRFALSE -> BRTRUE
    }
}

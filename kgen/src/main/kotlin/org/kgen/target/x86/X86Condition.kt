package org.kgen.target.x86

enum class X86Condition {
    OVERFLOW,
    NOT_OVERFLOW,
    BELOW,
    ABOVE_EQUAL,
    EQUAL,
    NOT_EQUAL,
    BELOW_EQUAL,
    ABOVE,
    SIGN,
    NOT_SIGN,
    PARITY,
    NOT_PARITY,
    LESS,
    GREATER_EQUAL,
    LESS_EQUAL,
    GREATER;

    fun invert(): X86Condition = entries[ordinal xor 1]
}

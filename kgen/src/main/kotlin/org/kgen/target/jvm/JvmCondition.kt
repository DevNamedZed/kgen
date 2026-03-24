package org.kgen.target.jvm

enum class JvmCondition {
    IFEQ,
    IFNE,
    IFLT,
    IFGE,
    IFGT,
    IFLE,
    IF_ICMPEQ,
    IF_ICMPNE,
    IF_ICMPLT,
    IF_ICMPGE,
    IF_ICMPGT,
    IF_ICMPLE,
    IF_ACMPEQ,
    IF_ACMPNE,
    IFNULL,
    IFNONNULL;

    fun invert(): JvmCondition = when (this) {
        IFEQ -> IFNE
        IFNE -> IFEQ
        IFLT -> IFGE
        IFGE -> IFLT
        IFGT -> IFLE
        IFLE -> IFGT
        IF_ICMPEQ -> IF_ICMPNE
        IF_ICMPNE -> IF_ICMPEQ
        IF_ICMPLT -> IF_ICMPGE
        IF_ICMPGE -> IF_ICMPLT
        IF_ICMPGT -> IF_ICMPLE
        IF_ICMPLE -> IF_ICMPGT
        IF_ACMPEQ -> IF_ACMPNE
        IF_ACMPNE -> IF_ACMPEQ
        IFNULL -> IFNONNULL
        IFNONNULL -> IFNULL
    }
}

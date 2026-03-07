package org.kgen.ir

/** Operations for [Instruction.AtomicRMW]. */
enum class AtomicRMWOp {
    XCHG, ADD, SUB, AND, NAND, OR, XOR, MAX, MIN, UMAX, UMIN, FADD, FSUB, FMAX, FMIN,
}

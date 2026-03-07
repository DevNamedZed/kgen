package org.kgen.ir

/** Reduction operations for [Instruction.VectorReduce]. */
enum class VectorReduceOp {
    ADD, MUL, AND, OR, XOR,
    SMIN, SMAX, UMIN, UMAX,
    FADD, FMUL, FMIN, FMAX,
}

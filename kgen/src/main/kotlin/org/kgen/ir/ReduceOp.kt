package org.kgen.ir

import org.kgen.ir.instructions.*

/** Reduction operations for [VectorReduce]. */
enum class VectorReduceOp {
    ADD, MUL, AND, OR, XOR,
    SMIN, SMAX, UMIN, UMAX,
    FADD, FMUL, FMIN, FMAX,
}

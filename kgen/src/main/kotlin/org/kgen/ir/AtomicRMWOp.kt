package org.kgen.ir

import org.kgen.ir.instructions.*

/** Operations for [AtomicRMW]. */
enum class AtomicRMWOp {
    XCHG, ADD, SUB, AND, NAND, OR, XOR, MAX, MIN, UMAX, UMIN, FADD, FSUB, FMAX, FMIN,
}

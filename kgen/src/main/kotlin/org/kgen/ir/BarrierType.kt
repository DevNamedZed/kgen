package org.kgen.ir

/**
 * Barrier precision tag for WriteBarrier instructions.
 *
 * Different barrier kinds have different costs — an array element barrier
 * may require a different card marking strategy than a field barrier.
 */
enum class BarrierType {
    FIELD,
    ARRAY,
    WEAK_FIELD,
    STATIC,
    UNKNOWN,
}

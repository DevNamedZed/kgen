package org.kgen.ir

/**
 * Reason for deoptimization, recorded in the speculation log.
 *
 * On recompilation, guards with previously-failed speculation IDs
 * are not emitted — the speculation is abandoned.
 */
enum class DeoptReason {
    NULL_CHECK,
    BOUNDS_CHECK,
    CLASS_CAST,
    ARRAY_STORE,
    ARITHMETIC_EXCEPTION,
    TYPE_CHECK_VIOLATED,
    UNREACHED_CODE,
    ALIASING,
    TRANSFER_TO_INTERPRETER,
    RUNTIME_CONSTRAINT,
    SPECULATIVE_INLINING,
    LOOP_LIMIT_CHECK,
}

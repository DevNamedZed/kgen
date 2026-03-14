package org.kgen.ir

/**
 * Stable identifier for a speculative assumption.
 *
 * Used by Guard, FixedGuard, and Deoptimize. When a guard fires,
 * the runtime records the failure keyed by this ID. On recompilation,
 * guards with previously-failed IDs are not emitted.
 */
@JvmInline
value class SpeculationId(val id: Long)

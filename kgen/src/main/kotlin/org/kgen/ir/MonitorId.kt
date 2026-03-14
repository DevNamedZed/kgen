package org.kgen.ir

/**
 * Opaque identifier for a monitor (object lock) held at a given FrameState point.
 *
 * The monitor is identified by the Reference value it is locked on.
 * A FrameState carries the list of monitors currently held so the
 * deopt handler can release them on fallback.
 */
@JvmInline
value class MonitorId(val objectRef: Value)

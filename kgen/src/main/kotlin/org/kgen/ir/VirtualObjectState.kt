package org.kgen.ir

/**
 * Represents an escape-analyzed object that exists only as IR values.
 *
 * Carried by FrameState so the deopt handler can materialize the object
 * in the heap when falling back to the interpreter.
 */
data class VirtualObjectState(
    val type: Type,
    val fields: List<FieldValue>,
) {
    data class FieldValue(
        val fieldName: String,
        val value: Value,
    )
}

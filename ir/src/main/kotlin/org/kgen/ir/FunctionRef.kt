package org.kgen.ir

/** A reference to a function. Carries the full [Type.Function] signature for call verification. */
data class FunctionRef(override val name: String, override val type: Type.Function) : Value

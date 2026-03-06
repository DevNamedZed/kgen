package org.kgen.ir

/** A reference to a global variable. Printed as `@name` in IR text. */
data class GlobalRef(override val name: String, override val type: Type) : Value

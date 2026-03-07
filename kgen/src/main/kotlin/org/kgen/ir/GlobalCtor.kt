package org.kgen.ir

/** Module constructor/destructor entry. Lower [priority] = runs first. */
data class GlobalCtor(
    val function: String,
    val priority: Int = 65535,
    val associatedData: String? = null,
)

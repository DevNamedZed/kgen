package org.kgen.ir

import org.kgen.ir.instructions.*

/** Bootstrap method descriptor for [DynamicCall] (invokedynamic). */
data class BootstrapMethod(
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val staticArgs: List<Constant> = emptyList(),
)

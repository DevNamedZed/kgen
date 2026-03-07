package org.kgen.ir

/** Bootstrap method descriptor for [Instruction.DynamicCall] (invokedynamic). */
data class BootstrapMethod(
    val className: String,
    val methodName: String,
    val methodType: Type.Function,
    val staticArgs: List<Constant> = emptyList(),
)

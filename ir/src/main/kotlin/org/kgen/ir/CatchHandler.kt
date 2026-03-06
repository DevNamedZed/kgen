package org.kgen.ir

/** Maps an exception type to a handler block label (for [Instruction.TryCatchRegion]). */
data class CatchHandler(
    val exceptionType: Type,
    val handlerBlock: String,
)

package org.kgen.ir

import org.kgen.ir.instructions.*

/** Maps an exception type to a handler block label (for [TryCatchRegion]). */
data class CatchHandler(
    val exceptionType: Type,
    val handlerBlock: String,
)

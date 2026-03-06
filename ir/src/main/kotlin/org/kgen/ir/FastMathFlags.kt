package org.kgen.ir

/** Fast-math flags that relax IEEE 754 semantics for optimization.
 *  Use [NONE] for strict IEEE compliance, [FAST] to enable all optimizations. */
data class FastMathFlags(
    val noNaNs: Boolean = false,
    val noInfs: Boolean = false,
    val noSignedZeros: Boolean = false,
    val allowReciprocal: Boolean = false,
    val allowContract: Boolean = false,
    val approxFunc: Boolean = false,
    val reassoc: Boolean = false,
) {
    companion object {
        val NONE = FastMathFlags()
        val FAST = FastMathFlags(true, true, true, true, true, true, true)
    }
}

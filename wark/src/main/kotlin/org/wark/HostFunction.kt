package org.wark

/**
 * A host function callable from WASM. Receives the instance context and
 * argument values, returns result values.
 *
 * Arguments and results are passed as long arrays — the WASM value encoding:
 * i32 values are zero-extended to long, i64 values are direct, f32/f64 are
 * bit-cast to long via Float/Double.toRawBits().
 */
fun interface HostFunction {
    fun call(instance: WarkInstance, args: LongArray): LongArray
}

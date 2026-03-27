package org.wark

/**
 * A WASM global variable — a single typed value, optionally mutable.
 *
 * ```java
 * var counter = WarkGlobal.mutableI32(0);
 * counter.setI32(42);
 * assertEquals(42, counter.getI32());
 * ```
 */
class WarkGlobal(
    val mutable: Boolean,
    private var value: Long,
) {
    fun getI32(): Int = value.toInt()
    fun getI64(): Long = value
    fun getF32(): Float = Float.fromBits(value.toInt())
    fun getF64(): Double = Double.fromBits(value)

    fun setI32(newValue: Int) {
        checkMutable()
        value = newValue.toLong() and 0xFFFFFFFFL
    }

    fun setI64(newValue: Long) {
        checkMutable()
        value = newValue
    }

    fun setF32(newValue: Float) {
        checkMutable()
        value = newValue.toRawBits().toLong() and 0xFFFFFFFFL
    }

    fun setF64(newValue: Double) {
        checkMutable()
        value = newValue.toRawBits()
    }

    fun rawValue(): Long = value

    private fun checkMutable() {
        if (!mutable) {
            throw WasmTrap("attempt to set immutable global")
        }
    }

    companion object {
        @JvmStatic fun mutableI32(initial: Int): WarkGlobal = WarkGlobal(true, initial.toLong() and 0xFFFFFFFFL)

        @JvmStatic fun mutableI64(initial: Long): WarkGlobal = WarkGlobal(true, initial)

        @JvmStatic fun immutableI32(value: Int): WarkGlobal = WarkGlobal(false, value.toLong() and 0xFFFFFFFFL)

        @JvmStatic fun immutableI64(value: Long): WarkGlobal = WarkGlobal(false, value)
    }
}

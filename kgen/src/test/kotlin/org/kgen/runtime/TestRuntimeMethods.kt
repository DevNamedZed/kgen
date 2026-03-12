package org.kgen.runtime

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenExport
import org.kgen.unmanaged.KgenRuntime

/**
 * Test runtime methods for subset validation and compilation tests.
 * These methods follow the Runtime Subset rules: static, primitives only.
 */
@KgenRuntime
class TestRuntimeMethods {
    companion object {
        @KgenExport @JvmStatic
        fun addInts(a: Int, b: Int): Int = a + b

        @KgenExport @JvmStatic
        fun addLongs(a: Long, b: Long): Long = a + b

        @KgenExport @JvmStatic
        fun multiply(a: Int, b: Int): Int = a * b

        @KgenExport @JvmStatic
        fun factorial(n: Int): Int {
            var result = 1
            var i = 2
            while (i <= n) {
                result *= i
                i++
            }
            return result
        }

        @KgenExport @JvmStatic
        fun max(a: Int, b: Int): Int {
            return if (a > b) a else b
        }

        @KgenExport @JvmStatic
        fun hash(ptr: Long, len: Int): Int {
            var h = 0
            var i = 0
            while (i < len) {
                h = 31 * h + Kgen.loadByte(Kgen.offset(ptr, i)).toInt()
                i++
            }
            return h
        }
    }
}

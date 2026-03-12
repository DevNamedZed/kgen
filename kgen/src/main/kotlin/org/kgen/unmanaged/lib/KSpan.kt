package org.kgen.unmanaged.lib

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenExport
import org.kgen.unmanaged.KgenRuntime

/**
 * Byte span — a pointer + length pair.
 *
 * Not a JVM object, just two values passed as arguments or stored in memory.
 * In JVM mode, the memory operations return dummy values (useful for testing
 * control flow). In native mode, they compile to real load/store instructions.
 *
 * ```java
 * // In JIT'd code, call the compiled native versions:
 * long ptr = ...;
 * int len = 10;
 * int h = KSpan.hash(ptr, len);
 * boolean eq = KSpan.equals(ptr1, len1, ptr2, len2);
 * ```
 */
@KgenRuntime
class KSpan {
    companion object {
        @KgenExport @JvmStatic
        fun length(ptr: Long, len: Int): Int = len

        @KgenExport @JvmStatic
        fun getByte(ptr: Long, len: Int, index: Int): Byte {
            if (index < 0 || index >= len) return 0
            return Kgen.loadByte(Kgen.offset(ptr, index))
        }

        @KgenExport @JvmStatic
        fun equals(aPtr: Long, aLen: Int, bPtr: Long, bLen: Int): Boolean {
            if (aLen != bLen) return false
            var i = 0
            while (i < aLen) {
                if (Kgen.loadByte(Kgen.offset(aPtr, i)) != Kgen.loadByte(Kgen.offset(bPtr, i))) {
                    return false
                }
                i++
            }
            return true
        }

        @KgenExport @JvmStatic
        fun indexOf(ptr: Long, len: Int, needle: Byte): Int {
            var i = 0
            while (i < len) {
                if (Kgen.loadByte(Kgen.offset(ptr, i)) == needle) return i
                i++
            }
            return -1
        }

        @KgenExport @JvmStatic
        fun hash(ptr: Long, len: Int): Int {
            var h = 0
            var i = 0
            while (i < len) {
                h = 31 * h + (Kgen.loadByte(Kgen.offset(ptr, i)).toInt() and 0xFF)
                i++
            }
            return h
        }

        @KgenExport @JvmStatic
        fun startsWith(ptr: Long, len: Int, prefixPtr: Long, prefixLen: Int): Boolean {
            if (prefixLen > len) return false
            var i = 0
            while (i < prefixLen) {
                if (Kgen.loadByte(Kgen.offset(ptr, i)) != Kgen.loadByte(Kgen.offset(prefixPtr, i))) {
                    return false
                }
                i++
            }
            return true
        }

        @KgenExport @JvmStatic
        fun copyTo(srcPtr: Long, srcLen: Int, dstPtr: Long, dstLen: Int): Int {
            val count = if (srcLen < dstLen) srcLen else dstLen
            var i = 0
            while (i < count) {
                Kgen.storeByte(Kgen.offset(dstPtr, i), Kgen.loadByte(Kgen.offset(srcPtr, i)))
                i++
            }
            return count
        }
    }
}

package org.kgen.unmanaged.lib

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenExport
import org.kgen.unmanaged.KgenNative

/**
 * Native implementations of `java.lang.System` methods.
 *
 * On the JVM these delegate to the standard library. In native mode, they
 * compile to platform-specific system calls (clock_gettime, gettimeofday, etc.).
 */
@KgenNative
object NativeSystem {

    @JavaMapping("java/lang/System", "currentTimeMillis", "()J")
    @KgenExport("kgen_currentTimeMillis")
    @JvmStatic
    fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    @JavaMapping("java/lang/System", "nanoTime", "()J")
    @KgenExport("kgen_nanoTime")
    @JvmStatic
    fun nanoTime(): Long {
        return System.nanoTime()
    }

    @JavaMapping("java/lang/System", "exit", "(I)V")
    @KgenExport("kgen_exit")
    @JvmStatic
    fun exit(status: Int) {
        System.exit(status)
    }

    @JavaMapping("java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I")
    @KgenExport("kgen_identityHashCode")
    @JvmStatic
    fun identityHashCode(objectAddress: Long): Int {
        val hash = (objectAddress xor (objectAddress ushr 32)).toInt()
        return hash xor (hash ushr 16)
    }

    @JavaMapping("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")
    @KgenExport("kgen_arraycopy")
    @JvmStatic
    fun arraycopy(source: Long, sourceOffset: Int, destination: Long, destinationOffset: Int, length: Int) {
        val elementSize = 8
        val sourceStart = source + sourceOffset.toLong() * elementSize
        val destinationStart = destination + destinationOffset.toLong() * elementSize

        if (source == destination && sourceOffset < destinationOffset) {
            var index = length - 1
            while (index >= 0) {
                val value = Kgen.loadLong(sourceStart + index.toLong() * elementSize)
                Kgen.storeLong(destinationStart + index.toLong() * elementSize, value)
                index--
            }
        } else {
            var index = 0
            while (index < length) {
                val value = Kgen.loadLong(sourceStart + index.toLong() * elementSize)
                Kgen.storeLong(destinationStart + index.toLong() * elementSize, value)
                index++
            }
        }
    }
}

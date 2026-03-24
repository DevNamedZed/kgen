package org.kgen.unmanaged.lib

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenDestructor
import org.kgen.unmanaged.KgenNative

/**
 * Native string builder. Builds null-terminated UTF-8 byte strings in native memory.
 *
 * ```kotlin
 * val builder = NativeStringBuilder()
 * builder.append(Kgen.stringConst("Hello"))
 * builder.appendByte(','.code.toByte())
 * builder.appendByte(' '.code.toByte())
 * builder.append(Kgen.stringConst("World"))
 * val result = builder.toAddress()  // pointer to "Hello, World\0"
 * ```
 */
@KgenNative
class NativeStringBuilder : AutoCloseable {
    var data: Long = 0
    var length: Int = 0
    var capacity: Int = 0

    init {
        capacity = INITIAL_CAPACITY
        data = Kgen.malloc(capacity.toLong())
        Kgen.storeByte(data, 0)
    }

    fun appendByte(byte: Byte) {
        ensureCapacity(length + 2)
        Kgen.storeByte(Kgen.offset(data, length), byte)
        length++
        Kgen.storeByte(Kgen.offset(data, length), 0)
    }

    fun append(stringAddress: Long) {
        var index = 0
        var byte = Kgen.loadByte(Kgen.offset(stringAddress, index))
        while (byte != 0.toByte()) {
            appendByte(byte)
            index++
            byte = Kgen.loadByte(Kgen.offset(stringAddress, index))
        }
    }

    fun appendInt(value: Int) {
        if (value == 0) {
            appendByte('0'.code.toByte())
            return
        }

        var number = value
        val negative = number < 0
        if (negative) {
            appendByte('-'.code.toByte())
            number = -number
        }

        val digits = Kgen.malloc(20)
        var digitCount = 0
        while (number > 0) {
            val digit = (number % 10)
            Kgen.storeByte(Kgen.offset(digits, digitCount), ('0'.code + digit).toByte())
            digitCount++
            number /= 10
        }

        var reverseIndex = digitCount - 1
        while (reverseIndex >= 0) {
            appendByte(Kgen.loadByte(Kgen.offset(digits, reverseIndex)))
            reverseIndex--
        }
        Kgen.free(digits)
    }

    fun appendLong(value: Long) {
        if (value == 0L) {
            appendByte('0'.code.toByte())
            return
        }

        var number = value
        val negative = number < 0L
        if (negative) {
            appendByte('-'.code.toByte())
            number = -number
        }

        val digits = Kgen.malloc(20)
        var digitCount = 0
        while (number > 0L) {
            val digit = (number % 10).toInt()
            Kgen.storeByte(Kgen.offset(digits, digitCount), ('0'.code + digit).toByte())
            digitCount++
            number /= 10
        }

        var reverseIndex = digitCount - 1
        while (reverseIndex >= 0) {
            appendByte(Kgen.loadByte(Kgen.offset(digits, reverseIndex)))
            reverseIndex--
        }
        Kgen.free(digits)
    }

    fun length(): Int = length

    fun isEmpty(): Boolean = length == 0

    fun byteAt(index: Int): Byte = Kgen.loadByte(Kgen.offset(data, index))

    fun clear() {
        length = 0
        Kgen.storeByte(data, 0)
    }

    fun toAddress(): Long {
        val result = Kgen.malloc(length.toLong() + 1)
        var index = 0
        while (index < length) {
            Kgen.storeByte(Kgen.offset(result, index), Kgen.loadByte(Kgen.offset(data, index)))
            index++
        }
        Kgen.storeByte(Kgen.offset(result, length), 0)
        return result
    }

    @KgenDestructor
    fun destroy() {
        if (data != 0L) {
            Kgen.free(data)
            data = 0
        }
        length = 0
        capacity = 0
    }

    override fun close() {
        destroy()
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= capacity) {
            return
        }
        var newCapacity = capacity * 2
        while (newCapacity < needed) {
            newCapacity *= 2
        }
        data = Kgen.realloc(data, newCapacity.toLong())
        capacity = newCapacity
    }

    companion object {
        private const val INITIAL_CAPACITY = 32
    }
}

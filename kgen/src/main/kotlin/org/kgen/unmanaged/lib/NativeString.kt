package org.kgen.unmanaged.lib

import org.kgen.unmanaged.Kgen
import org.kgen.unmanaged.KgenDestructor
import org.kgen.unmanaged.KgenNative

/**
 * Native string. Wraps a null-terminated UTF-8 byte buffer with cached length.
 *
 * Immutable after construction — transform methods return new instances.
 * Works on JVM (for testing) and compiles to native machine code.
 *
 * ```kotlin
 * val greeting = NativeString("Hello, World")
 * assertEquals(12, greeting.length())
 * assertTrue(greeting.startsWith(Kgen.stringConst("Hello")))
 *
 * val upper = greeting.toUpperCase()
 * upper.destroy()
 * greeting.destroy()
 * ```
 *
 * Implements [AutoCloseable] for explicit cleanup via `use {}`:
 * ```kotlin
 * NativeString("Hello").use { greeting ->
 *     println(greeting.length())
 * }
 * ```
 *
 * In native compilation, locals are cleaned up automatically at scope exit
 * (C++ RAII style) — no manual destroy or use block needed.
 */
@KgenNative
class NativeString : AutoCloseable {
    var data: Long = 0
    var length: Int = 0

    /**
     * Creates an empty native string.
     */
    constructor() {
        data = Kgen.malloc(1)
        Kgen.storeByte(data, 0)
    }

    /**
     * Creates a native string from a JVM string literal.
     *
     * On JVM, this allocates the string in the simulated heap via [Kgen.stringConst].
     * In native compilation, string literals in the constant pool are compiled
     * directly as constant data — no runtime allocation.
     */
    constructor(value: String) {
        val address = Kgen.stringConst(value)
        val sourceLength = strlen(address)
        data = Kgen.malloc(sourceLength.toLong() + 1)
        copyBytes(address, data, sourceLength)
        Kgen.storeByte(Kgen.offset(data, sourceLength), 0)
        length = sourceLength
    }

    /**
     * Creates a native string by copying from a raw null-terminated address.
     */
    constructor(sourceAddress: Long) {
        val sourceLength = strlen(sourceAddress)
        data = Kgen.malloc(sourceLength.toLong() + 1)
        copyBytes(sourceAddress, data, sourceLength)
        Kgen.storeByte(Kgen.offset(data, sourceLength), 0)
        length = sourceLength
    }

    // -- Query --

    fun length(): Int = length

    fun isEmpty(): Boolean = length == 0

    /**
     * Returns the byte value at [index] as an unsigned int (0-255).
     */
    fun charAt(index: Int): Int {
        return Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF
    }

    /**
     * Returns the raw pointer to the internal null-terminated buffer.
     * The caller must not free this pointer — use [destroy] instead.
     */
    fun toAddress(): Long = data

    // -- Hash --

    fun stringHashCode(): Int {
        var hash = 0
        var index = 0
        while (index < length) {
            hash = 31 * hash + (Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF)
            index++
        }
        return hash
    }

    // -- Comparison --

    /**
     * Returns true if this string has the same bytes as the null-terminated
     * string at [other].
     */
    fun contentEquals(other: Long): Boolean {
        val otherLength = strlen(other)
        if (length != otherLength) {
            return false
        }
        var index = 0
        while (index < length) {
            if (Kgen.loadByte(Kgen.offset(data, index)) != Kgen.loadByte(Kgen.offset(other, index))) {
                return false
            }
            index++
        }
        return true
    }

    /**
     * Lexicographic comparison with the null-terminated string at [other].
     * Returns negative, zero, or positive.
     */
    fun compareTo(other: Long): Int {
        var index = 0
        while (true) {
            val thisChar = Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF
            val otherChar = Kgen.loadByte(Kgen.offset(other, index)).toInt() and 0xFF
            if (thisChar != otherChar) {
                return thisChar - otherChar
            }
            if (thisChar == 0) {
                return 0
            }
            index++
        }
    }

    // -- Search --

    /**
     * Returns the index of the first occurrence of [character] (byte value),
     * or -1 if not found.
     */
    fun indexOf(character: Int): Int {
        var index = 0
        while (index < length) {
            if ((Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF) == character) {
                return index
            }
            index++
        }
        return -1
    }

    /**
     * Returns the index of the first occurrence of [character] starting from
     * [fromIndex], or -1 if not found.
     */
    fun indexOfFrom(character: Int, fromIndex: Int): Int {
        var index = fromIndex
        while (index < length) {
            if ((Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF) == character) {
                return index
            }
            index++
        }
        return -1
    }

    /**
     * Returns the index of the last occurrence of [character], or -1 if not found.
     */
    fun lastIndexOf(character: Int): Int {
        var index = length - 1
        while (index >= 0) {
            if ((Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF) == character) {
                return index
            }
            index--
        }
        return -1
    }

    /**
     * Returns the index of the first occurrence of the substring at [substring],
     * or -1 if not found.
     */
    fun indexOfString(substring: Long): Int {
        val substringLength = strlen(substring)
        if (substringLength > length) {
            return -1
        }
        if (substringLength == 0) {
            return 0
        }
        var index = 0
        val limit = length - substringLength
        while (index <= limit) {
            if (regionMatches(index, substring, substringLength)) {
                return index
            }
            index++
        }
        return -1
    }

    /**
     * Returns true if this string contains the substring at [substring].
     */
    fun contains(substring: Long): Boolean {
        return indexOfString(substring) >= 0
    }

    /**
     * Returns true if this string starts with the null-terminated string at [prefix].
     */
    fun startsWith(prefix: Long): Boolean {
        val prefixLength = strlen(prefix)
        if (prefixLength > length) {
            return false
        }
        return regionMatches(0, prefix, prefixLength)
    }

    /**
     * Returns true if this string ends with the null-terminated string at [suffix].
     */
    fun endsWith(suffix: Long): Boolean {
        val suffixLength = strlen(suffix)
        if (suffixLength > length) {
            return false
        }
        return regionMatches(length - suffixLength, suffix, suffixLength)
    }

    // -- Transform (return new NativeString) --

    /**
     * Returns a new NativeString containing bytes from [beginIndex] to [endIndex] (exclusive).
     */
    fun substring(beginIndex: Int, endIndex: Int): NativeString {
        val substringLength = endIndex - beginIndex
        val buffer = Kgen.malloc(substringLength.toLong() + 1)
        copyBytes(Kgen.offset(data, beginIndex), buffer, substringLength)
        Kgen.storeByte(Kgen.offset(buffer, substringLength), 0)
        return NativeString(buffer)
    }

    /**
     * Returns a new NativeString with leading and trailing ASCII whitespace removed.
     */
    fun trim(): NativeString {
        var start = 0
        while (start < length && isWhitespace(Kgen.loadByte(Kgen.offset(data, start)).toInt() and 0xFF)) {
            start++
        }
        var end = length
        while (end > start && isWhitespace(Kgen.loadByte(Kgen.offset(data, end - 1)).toInt() and 0xFF)) {
            end--
        }
        return substring(start, end)
    }

    /**
     * Returns a new NativeString with ASCII letters converted to upper case.
     */
    fun toUpperCase(): NativeString {
        val buffer = Kgen.malloc(length.toLong() + 1)
        var index = 0
        while (index < length) {
            val character = Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF
            val upper = if (character >= 0x61 && character <= 0x7A) { character - 32 } else { character }
            Kgen.storeByte(Kgen.offset(buffer, index), upper.toByte())
            index++
        }
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return NativeString(buffer)
    }

    /**
     * Returns a new NativeString with ASCII letters converted to lower case.
     */
    fun toLowerCase(): NativeString {
        val buffer = Kgen.malloc(length.toLong() + 1)
        var index = 0
        while (index < length) {
            val character = Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF
            val lower = if (character >= 0x41 && character <= 0x5A) { character + 32 } else { character }
            Kgen.storeByte(Kgen.offset(buffer, index), lower.toByte())
            index++
        }
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return NativeString(buffer)
    }

    /**
     * Returns a new NativeString that is the concatenation of this string
     * and the null-terminated string at [other].
     */
    fun concat(other: Long): NativeString {
        val otherLength = strlen(other)
        val totalLength = length + otherLength
        val buffer = Kgen.malloc(totalLength.toLong() + 1)
        copyBytes(data, buffer, length)
        copyBytes(other, Kgen.offset(buffer, length), otherLength)
        Kgen.storeByte(Kgen.offset(buffer, totalLength), 0)
        return NativeString(buffer)
    }

    /**
     * Returns a new NativeString with all occurrences of [oldCharacter] replaced
     * with [newCharacter].
     */
    fun replace(oldCharacter: Int, newCharacter: Int): NativeString {
        val buffer = Kgen.malloc(length.toLong() + 1)
        var index = 0
        while (index < length) {
            val character = Kgen.loadByte(Kgen.offset(data, index)).toInt() and 0xFF
            val replacement = if (character == oldCharacter) { newCharacter } else { character }
            Kgen.storeByte(Kgen.offset(buffer, index), replacement.toByte())
            index++
        }
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return NativeString(buffer)
    }

    // -- Cleanup --

    @KgenDestructor
    fun destroy() {
        if (data != 0L) {
            Kgen.free(data)
            data = 0
        }
        length = 0
    }

    override fun close() {
        destroy()
    }

    // -- Internal helpers --

    private fun strlen(address: Long): Int {
        var count = 0
        while (Kgen.loadByte(Kgen.offset(address, count)) != 0.toByte()) {
            count++
        }
        return count
    }

    private fun copyBytes(source: Long, destination: Long, count: Int) {
        var index = 0
        while (index < count) {
            Kgen.storeByte(Kgen.offset(destination, index), Kgen.loadByte(Kgen.offset(source, index)))
            index++
        }
    }

    private fun regionMatches(thisOffset: Int, other: Long, regionLength: Int): Boolean {
        var index = 0
        while (index < regionLength) {
            if (Kgen.loadByte(Kgen.offset(data, thisOffset + index)) != Kgen.loadByte(Kgen.offset(other, index))) {
                return false
            }
            index++
        }
        return true
    }

    private fun isWhitespace(character: Int): Boolean {
        return character == 0x20 || character == 0x09 || character == 0x0A ||
               character == 0x0D || character == 0x0B || character == 0x0C
    }
}

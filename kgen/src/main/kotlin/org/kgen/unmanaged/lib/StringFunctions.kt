package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

/**
 * Native implementations of `java.lang.String` methods and string concatenation helpers.
 *
 * Each `@JavaMapping` function replaces a Java String method call in native compilation.
 * Backed by libc string functions via `@KgenImport`.
 */
@KgenNative
object StringFunctions {

    @KgenImport external fun strlen(s: Long): Long
    @KgenImport external fun strcmp(a: Long, b: Long): Int
    @KgenImport external fun memcmp(a: Long, b: Long, n: Long): Int
    @KgenImport external fun strchr(s: Long, c: Int): Long
    @KgenImport external fun malloc(size: Long): Long
    @KgenImport external fun memcpy(dst: Long, src: Long, n: Long): Long
    @KgenImport("sprintf") external fun sprintfInt(buf: Long, fmt: Long, v: Int): Int
    @KgenImport("sprintf") external fun sprintfLong(buf: Long, fmt: Long, v: Long): Int

    // -- Core --

    @JavaMapping("java/lang/String", "length", "()I")
    @KgenExport("kgen_string_length")
    @JvmStatic fun stringLength(s: Long): Int = strlen(s).toInt()

    @JavaMapping("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
    @KgenExport("kgen_string_equals")
    @JvmStatic fun stringEquals(a: Long, b: Long): Int = if (strcmp(a, b) == 0) 1 else 0

    @JavaMapping("java/lang/String", "charAt", "(I)C")
    @KgenExport("kgen_string_charAt")
    @JvmStatic fun stringCharAt(s: Long, index: Int): Int =
        Kgen.loadByte(Kgen.offset(s, index)).toInt()

    @JavaMapping("java/lang/String", "isEmpty", "()Z")
    @KgenExport("kgen_string_isEmpty")
    @JvmStatic fun stringIsEmpty(s: Long): Int = if (strlen(s) == 0L) 1 else 0

    @JavaMapping("java/lang/String", "hashCode", "()I")
    @KgenExport("kgen_string_hashCode")
    @JvmStatic fun stringHashCode(s: Long): Int {
        var hash = 0
        var i = 0L
        val length = strlen(s)
        while (i < length) {
            hash = 31 * hash + (Kgen.loadByte(Kgen.offset(s, i.toInt())).toInt() and 0xFF)
            i++
        }
        return hash
    }

    // -- Search --

    @JavaMapping("java/lang/String", "indexOf", "(I)I")
    @KgenExport("kgen_string_indexOf")
    @JvmStatic fun stringIndexOf(s: Long, ch: Int): Int {
        val ptr = strchr(s, ch)
        return if (ptr == 0L) -1 else (ptr - s).toInt()
    }

    @JavaMapping("java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z")
    @KgenExport("kgen_string_contains")
    @JvmStatic fun stringContains(haystack: Long, needle: Long): Int {
        val haystackLen = strlen(haystack)
        val needleLen = strlen(needle)
        if (needleLen > haystackLen) { return 0 }
        if (needleLen == 0L) { return 1 }
        var i = 0L
        while (i <= haystackLen - needleLen) {
            if (memcmp(Kgen.offset(haystack, i.toInt()), needle, needleLen) == 0) { return 1 }
            i++
        }
        return 0
    }

    @JavaMapping("java/lang/String", "startsWith", "(Ljava/lang/String;)Z")
    @KgenExport("kgen_string_startsWith")
    @JvmStatic fun stringStartsWith(s: Long, prefix: Long): Int {
        val prefixLen = strlen(prefix)
        if (prefixLen > strlen(s)) { return 0 }
        return if (memcmp(s, prefix, prefixLen) == 0) 1 else 0
    }

    @JavaMapping("java/lang/String", "endsWith", "(Ljava/lang/String;)Z")
    @KgenExport("kgen_string_endsWith")
    @JvmStatic fun stringEndsWith(s: Long, suffix: Long): Int {
        val sLen = strlen(s)
        val suffixLen = strlen(suffix)
        if (suffixLen > sLen) { return 0 }
        return if (memcmp(Kgen.offset(s, (sLen - suffixLen).toInt()), suffix, suffixLen) == 0) 1 else 0
    }

    @JavaMapping("java/lang/String", "compareTo", "(Ljava/lang/String;)I")
    @KgenExport("kgen_string_compareTo")
    @JvmStatic fun stringCompareTo(a: Long, b: Long): Int {
        var i = 0L
        while (true) {
            val charA = Kgen.loadByte(Kgen.offset(a, i.toInt())).toInt() and 0xFF
            val charB = Kgen.loadByte(Kgen.offset(b, i.toInt())).toInt() and 0xFF
            if (charA != charB) { return charA - charB }
            if (charA == 0) { return 0 }
            i++
        }
    }

    // -- Transform --

    @JavaMapping("java/lang/String", "substring", "(II)Ljava/lang/String;")
    @KgenExport("kgen_string_substring")
    @JvmStatic fun stringSubstring(s: Long, beginIndex: Int, endIndex: Int): Long {
        val length = endIndex - beginIndex
        val buffer = malloc(length.toLong() + 1)
        memcpy(buffer, Kgen.offset(s, beginIndex), length.toLong())
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return buffer
    }

    @JavaMapping("java/lang/String", "trim", "()Ljava/lang/String;")
    @KgenExport("kgen_string_trim")
    @JvmStatic fun stringTrim(s: Long): Long {
        val length = strlen(s).toInt()
        var start = 0
        while (start < length && isWhitespace(Kgen.loadByte(Kgen.offset(s, start)).toInt())) {
            start++
        }
        var end = length
        while (end > start && isWhitespace(Kgen.loadByte(Kgen.offset(s, end - 1)).toInt())) {
            end--
        }
        val trimmedLength = end - start
        val buffer = malloc(trimmedLength.toLong() + 1)
        memcpy(buffer, Kgen.offset(s, start), trimmedLength.toLong())
        Kgen.storeByte(Kgen.offset(buffer, trimmedLength), 0)
        return buffer
    }

    @JavaMapping("java/lang/String", "toUpperCase", "()Ljava/lang/String;")
    @KgenExport("kgen_string_toUpperCase")
    @JvmStatic fun stringToUpperCase(s: Long): Long {
        val length = strlen(s).toInt()
        val buffer = malloc(length.toLong() + 1)
        var i = 0
        while (i < length) {
            val ch = Kgen.loadByte(Kgen.offset(s, i)).toInt() and 0xFF
            val upper = if (ch in 0x61..0x7A) ch - 32 else ch
            Kgen.storeByte(Kgen.offset(buffer, i), upper.toByte())
            i++
        }
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return buffer
    }

    @JavaMapping("java/lang/String", "toLowerCase", "()Ljava/lang/String;")
    @KgenExport("kgen_string_toLowerCase")
    @JvmStatic fun stringToLowerCase(s: Long): Long {
        val length = strlen(s).toInt()
        val buffer = malloc(length.toLong() + 1)
        var i = 0
        while (i < length) {
            val ch = Kgen.loadByte(Kgen.offset(s, i)).toInt() and 0xFF
            val lower = if (ch in 0x41..0x5A) ch + 32 else ch
            Kgen.storeByte(Kgen.offset(buffer, i), lower.toByte())
            i++
        }
        Kgen.storeByte(Kgen.offset(buffer, length), 0)
        return buffer
    }

    // -- Concat --

    @KgenExport("kgen_strconcat_begin")
    @JvmStatic fun strconcatBegin(): Long {
        val buffer = malloc(256)
        Kgen.storeByte(buffer, 0)
        return buffer
    }

    @KgenExport("kgen_strconcat_str")
    @JvmStatic fun strconcatStr(buffer: Long, s: Long): Long {
        val bufferLength = strlen(buffer)
        val sourceLength = strlen(s)
        memcpy(Kgen.offset(buffer, bufferLength.toInt()), s, sourceLength)
        Kgen.storeByte(Kgen.offset(buffer, (bufferLength + sourceLength).toInt()), 0)
        return buffer
    }

    @KgenExport("kgen_strconcat_int")
    @JvmStatic fun strconcatInt(buffer: Long, v: Int): Long {
        val bufferLength = strlen(buffer)
        sprintfInt(Kgen.offset(buffer, bufferLength.toInt()), Kgen.stringConst("%d"), v)
        return buffer
    }

    @KgenExport("kgen_strconcat_long")
    @JvmStatic fun strconcatLong(buffer: Long, v: Long): Long {
        val bufferLength = strlen(buffer)
        sprintfLong(Kgen.offset(buffer, bufferLength.toInt()), Kgen.stringConst("%ld"), v)
        return buffer
    }

    @KgenExport("kgen_strconcat_finish")
    @JvmStatic fun strconcatFinish(buffer: Long): Long = buffer

    private fun isWhitespace(ch: Int): Boolean =
        ch == 0x20 || ch == 0x09 || ch == 0x0A || ch == 0x0D || ch == 0x0B || ch == 0x0C
}

@file:JvmName("KgenStdlib")
package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

// ── C library imports ────────────────────────────────────────────

@KgenImport("_Unwind_RaiseException") external fun unwindRaiseException(exception: Long): Int
@KgenImport("abort") external fun abort()

@KgenImport external fun puts(s: Long): Int
@KgenImport external fun strlen(s: Long): Long
@KgenImport external fun strcmp(a: Long, b: Long): Int
@KgenImport external fun malloc(size: Long): Long
@KgenImport external fun free(ptr: Long)
@KgenImport external fun memcpy(dst: Long, src: Long, n: Long): Long
@KgenImport external fun sqrt(x: Double): Double
@KgenImport external fun pow(x: Double, y: Double): Double
@KgenImport external fun fabs(x: Double): Double

@KgenImport("printf") external fun printfI(fmt: Long, v: Int): Int
@KgenImport("printf") external fun printfL(fmt: Long, v: Long): Int
@KgenImport("printf") external fun printfD(fmt: Long, v: Double): Int
@KgenImport("printf") external fun printfS(fmt: Long, s: Long): Int
@KgenImport("printf") external fun printfC(fmt: Long, c: Int): Int
@KgenImport("sprintf") external fun sprintfI(buf: Long, fmt: Long, v: Int): Int
@KgenImport("sprintf") external fun sprintfL(buf: Long, fmt: Long, v: Long): Int
@KgenImport("sprintf") external fun sprintfD(buf: Long, fmt: Long, v: Double): Int
@KgenImport("ceil") external fun ceil(x: Double): Double
@KgenImport("floor") external fun floor(x: Double): Double
@KgenImport("round") external fun lround(x: Double): Long
@KgenImport("log") external fun log(x: Double): Double
@KgenImport("exp") external fun exp(x: Double): Double
@KgenImport("fmin") external fun fmin(a: Double, b: Double): Double
@KgenImport("fmax") external fun fmax(a: Double, b: Double): Double
@KgenImport("atoi") external fun atoi(s: Long): Int
@KgenImport("atol") external fun atol(s: Long): Long
@KgenImport("memcmp") external fun memcmp(a: Long, b: Long, n: Long): Int
@KgenImport("strchr") external fun strchr(s: Long, c: Int): Long

// ── I/O ──────────────────────────────────────────────────────────

@KgenExport("kgen_println_str")
fun printlnStr(s: Long) { puts(s) }

@KgenExport("kgen_println_int")
fun printlnInt(v: Int) { printfI(Kgen.stringConst("%d\n"), v) }

@KgenExport("kgen_println_long")
fun printlnLong(v: Long) { printfL(Kgen.stringConst("%ld\n"), v) }

@KgenExport("kgen_println_double")
fun printlnDouble(v: Double) { printfD(Kgen.stringConst("%f\n"), v) }

@KgenExport("kgen_println_void")
fun printlnVoid() { puts(Kgen.stringConst("\n")) }

@KgenExport("kgen_print_str")
fun printStr(s: Long) { printfS(Kgen.stringConst("%s"), s) }

@KgenExport("kgen_print_int")
fun printInt(v: Int) { printfI(Kgen.stringConst("%d"), v) }

@KgenExport("kgen_print_long")
fun printLong(v: Long) { printfL(Kgen.stringConst("%ld"), v) }

@KgenExport("kgen_println_float")
fun printlnFloat(v: Double) { printfD(Kgen.stringConst("%f\n"), v) }

@KgenExport("kgen_println_boolean")
fun printlnBoolean(v: Int) {
    if (v != 0) {
        puts(Kgen.stringConst("true"))
    } else {
        puts(Kgen.stringConst("false"))
    }
}

@KgenExport("kgen_println_char")
fun printlnChar(c: Int) { printfC(Kgen.stringConst("%c\n"), c) }

// ── String operations ────────────────────────────────────────────

@KgenExport("kgen_string_length")
fun stringLength(s: Long): Int = strlen(s).toInt()

@KgenExport("kgen_string_equals")
fun stringEquals(a: Long, b: Long): Int = if (strcmp(a, b) == 0) 1 else 0

@KgenExport("kgen_string_charAt")
fun stringCharAt(s: Long, idx: Int): Int = Kgen.loadByte(Kgen.offset(s, idx)).toInt()

@KgenExport("kgen_string_isEmpty")
fun stringIsEmpty(s: Long): Int = if (strlen(s) == 0L) 1 else 0

@KgenExport("kgen_string_indexOf")
fun stringIndexOf(s: Long, ch: Int): Int {
    val ptr = strchr(s, ch)
    return if (ptr == 0L) -1 else (ptr - s).toInt()
}

@KgenExport("kgen_string_substring")
fun stringSubstring(s: Long, beginIdx: Int, endIdx: Int): Long {
    val len = endIdx - beginIdx
    val buf = malloc(len.toLong() + 1)
    memcpy(buf, Kgen.offset(s, beginIdx.toLong()), len.toLong())
    Kgen.storeByte(Kgen.offset(buf, len.toLong()), 0)
    return buf
}

@KgenExport("kgen_string_contains")
fun stringContains(haystack: Long, needle: Long): Int {
    val haystackLen = strlen(haystack)
    val needleLen = strlen(needle)
    if (needleLen > haystackLen) return 0
    if (needleLen == 0L) return 1
    var i = 0L
    while (i <= haystackLen - needleLen) {
        if (memcmp(Kgen.offset(haystack, i), needle, needleLen) == 0) return 1
        i++
    }
    return 0
}

@KgenExport("kgen_string_startsWith")
fun stringStartsWith(s: Long, prefix: Long): Int {
    val prefixLen = strlen(prefix)
    if (prefixLen > strlen(s)) return 0
    return if (memcmp(s, prefix, prefixLen) == 0) 1 else 0
}

// ── Math operations ──────────────────────────────────────────────

@KgenExport("kgen_math_abs_int")
fun mathAbsInt(v: Int): Int = if (v < 0) -v else v

@KgenExport("kgen_math_abs_long")
fun mathAbsLong(v: Long): Long = if (v < 0) -v else v

@KgenExport("kgen_math_abs_double")
fun mathAbsDouble(v: Double): Double = fabs(v)

@KgenExport("kgen_math_min_int")
fun mathMinInt(a: Int, b: Int): Int = if (a < b) a else b

@KgenExport("kgen_math_max_int")
fun mathMaxInt(a: Int, b: Int): Int = if (a > b) a else b

@KgenExport("kgen_math_min_long")
fun mathMinLong(a: Long, b: Long): Long = if (a < b) a else b

@KgenExport("kgen_math_max_long")
fun mathMaxLong(a: Long, b: Long): Long = if (a > b) a else b

@KgenExport("kgen_math_sqrt")
fun mathSqrt(x: Double): Double = sqrt(x)

@KgenExport("kgen_math_pow")
fun mathPow(x: Double, y: Double): Double = pow(x, y)

@KgenExport("kgen_math_min_double")
fun mathMinDouble(a: Double, b: Double): Double = fmin(a, b)

@KgenExport("kgen_math_max_double")
fun mathMaxDouble(a: Double, b: Double): Double = fmax(a, b)

@KgenExport("kgen_math_ceil")
fun mathCeil(x: Double): Double = ceil(x)

@KgenExport("kgen_math_floor")
fun mathFloor(x: Double): Double = floor(x)

@KgenExport("kgen_math_round")
fun mathRound(x: Double): Long = lround(x)

@KgenExport("kgen_math_log")
fun mathLog(x: Double): Double = log(x)

@KgenExport("kgen_math_exp")
fun mathExp(x: Double): Double = exp(x)

// ── Numeric conversion ───────────────────────────────────────────

@KgenExport("kgen_int_to_string")
fun intToString(v: Int): Long {
    val buf = malloc(32)
    sprintfI(buf, Kgen.stringConst("%d"), v)
    return buf
}

@KgenExport("kgen_long_to_string")
fun longToString(v: Long): Long {
    val buf = malloc(32)
    sprintfL(buf, Kgen.stringConst("%ld"), v)
    return buf
}

@KgenExport("kgen_double_to_string")
fun doubleToString(v: Double): Long {
    val buf = malloc(64)
    sprintfD(buf, Kgen.stringConst("%g"), v)
    return buf
}

@KgenExport("kgen_int_parse")
fun intParse(s: Long): Int = atoi(s)

@KgenExport("kgen_long_parse")
fun longParse(s: Long): Long = atol(s)

// ── String concat helpers ────────────────────────────────────────

@KgenExport("kgen_strconcat_begin")
fun strconcatBegin(): Long {
    val buf = malloc(256)
    Kgen.storeByte(buf, 0)
    return buf
}

@KgenExport("kgen_strconcat_str")
fun strconcatStr(buf: Long, s: Long): Long {
    val bufLen = strlen(buf)
    val srcLen = strlen(s)
    memcpy(Kgen.offset(buf, bufLen), s, srcLen)
    Kgen.storeByte(Kgen.offset(buf, bufLen + srcLen), 0)
    return buf
}

@KgenExport("kgen_strconcat_int")
fun strconcatInt(buf: Long, v: Int): Long {
    val bufLen = strlen(buf)
    sprintfI(Kgen.offset(buf, bufLen), Kgen.stringConst("%d"), v)
    return buf
}

@KgenExport("kgen_strconcat_long")
fun strconcatLong(buf: Long, v: Long): Long {
    val bufLen = strlen(buf)
    sprintfL(Kgen.offset(buf, bufLen), Kgen.stringConst("%ld"), v)
    return buf
}

@KgenExport("kgen_strconcat_finish")
fun strconcatFinish(buf: Long): Long = buf

// ── Exception handling ──────────────────────────────────────────

@KgenExport("kgen_throw")
fun kgenThrow(exception: Long) {
    unwindRaiseException(exception)
    abort()
}

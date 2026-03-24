package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

@KgenNative
object NumericConvertFunctions {

    @KgenImport external fun malloc(size: Long): Long
    @KgenImport("sprintf") external fun sprintfInt(buf: Long, fmt: Long, v: Int): Int
    @KgenImport("sprintf") external fun sprintfLong(buf: Long, fmt: Long, v: Long): Int
    @KgenImport("sprintf") external fun sprintfDouble(buf: Long, fmt: Long, v: Double): Int
    @KgenImport external fun atoi(s: Long): Int
    @KgenImport external fun atol(s: Long): Long

    @JavaMapping("java/lang/Integer", "toString", "(I)Ljava/lang/String;")
    @KgenExport("kgen_int_to_string")
    @JvmStatic fun intToString(v: Int): Long {
        val buffer = malloc(32)
        sprintfInt(buffer, Kgen.stringConst("%d"), v)
        return buffer
    }

    @JavaMapping("java/lang/Long", "toString", "(J)Ljava/lang/String;")
    @KgenExport("kgen_long_to_string")
    @JvmStatic fun longToString(v: Long): Long {
        val buffer = malloc(32)
        sprintfLong(buffer, Kgen.stringConst("%ld"), v)
        return buffer
    }

    @JavaMapping("java/lang/Double", "toString", "(D)Ljava/lang/String;")
    @KgenExport("kgen_double_to_string")
    @JvmStatic fun doubleToString(v: Double): Long {
        val buffer = malloc(64)
        sprintfDouble(buffer, Kgen.stringConst("%g"), v)
        return buffer
    }

    @JavaMapping("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I")
    @KgenExport("kgen_int_parse")
    @JvmStatic fun parseInt(s: Long): Int = atoi(s)

    @JavaMapping("java/lang/Long", "parseLong", "(Ljava/lang/String;)J")
    @KgenExport("kgen_long_parse")
    @JvmStatic fun parseLong(s: Long): Long = atol(s)
}

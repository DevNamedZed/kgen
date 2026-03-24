package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

@KgenNative
object MathFunctions {

    @KgenImport external fun sqrt(x: Double): Double
    @KgenImport external fun pow(x: Double, y: Double): Double
    @KgenImport external fun fabs(x: Double): Double
    @KgenImport external fun fmin(a: Double, b: Double): Double
    @KgenImport external fun fmax(a: Double, b: Double): Double
    @KgenImport external fun sin(x: Double): Double
    @KgenImport external fun cos(x: Double): Double
    @KgenImport external fun tan(x: Double): Double
    @KgenImport external fun atan(x: Double): Double
    @KgenImport external fun atan2(y: Double, x: Double): Double
    @KgenImport external fun log(x: Double): Double
    @KgenImport external fun exp(x: Double): Double
    @KgenImport external fun ceil(x: Double): Double
    @KgenImport external fun floor(x: Double): Double
    @KgenImport("lround") external fun lround(x: Double): Long

    // -- Abs --

    @JavaMapping("java/lang/Math", "abs", "(I)I")
    @KgenExport("kgen_math_abs_int")
    @JvmStatic fun absInt(v: Int): Int = if (v < 0) -v else v

    @JavaMapping("java/lang/Math", "abs", "(J)J")
    @KgenExport("kgen_math_abs_long")
    @JvmStatic fun absLong(v: Long): Long = if (v < 0) -v else v

    @JavaMapping("java/lang/Math", "abs", "(D)D")
    @KgenExport("kgen_math_abs_double")
    @JvmStatic fun absDouble(v: Double): Double = fabs(v)

    // -- Min / Max --

    @JavaMapping("java/lang/Math", "min", "(II)I")
    @KgenExport("kgen_math_min_int")
    @JvmStatic fun minInt(a: Int, b: Int): Int = if (a < b) a else b

    @JavaMapping("java/lang/Math", "max", "(II)I")
    @KgenExport("kgen_math_max_int")
    @JvmStatic fun maxInt(a: Int, b: Int): Int = if (a > b) a else b

    @JavaMapping("java/lang/Math", "min", "(JJ)J")
    @KgenExport("kgen_math_min_long")
    @JvmStatic fun minLong(a: Long, b: Long): Long = if (a < b) a else b

    @JavaMapping("java/lang/Math", "max", "(JJ)J")
    @KgenExport("kgen_math_max_long")
    @JvmStatic fun maxLong(a: Long, b: Long): Long = if (a > b) a else b

    @JavaMapping("java/lang/Math", "min", "(DD)D")
    @KgenExport("kgen_math_min_double")
    @JvmStatic fun minDouble(a: Double, b: Double): Double = fmin(a, b)

    @JavaMapping("java/lang/Math", "max", "(DD)D")
    @KgenExport("kgen_math_max_double")
    @JvmStatic fun maxDouble(a: Double, b: Double): Double = fmax(a, b)

    // -- Core --

    @JavaMapping("java/lang/Math", "sqrt", "(D)D")
    @KgenExport("kgen_math_sqrt")
    @JvmStatic fun mathSqrt(x: Double): Double = sqrt(x)

    @JavaMapping("java/lang/Math", "pow", "(DD)D")
    @KgenExport("kgen_math_pow")
    @JvmStatic fun mathPow(x: Double, y: Double): Double = pow(x, y)

    @JavaMapping("java/lang/Math", "log", "(D)D")
    @KgenExport("kgen_math_log")
    @JvmStatic fun mathLog(x: Double): Double = log(x)

    @JavaMapping("java/lang/Math", "exp", "(D)D")
    @KgenExport("kgen_math_exp")
    @JvmStatic fun mathExp(x: Double): Double = exp(x)

    // -- Trig --

    @JavaMapping("java/lang/Math", "sin", "(D)D")
    @KgenExport("kgen_math_sin")
    @JvmStatic fun mathSin(x: Double): Double = sin(x)

    @JavaMapping("java/lang/Math", "cos", "(D)D")
    @KgenExport("kgen_math_cos")
    @JvmStatic fun mathCos(x: Double): Double = cos(x)

    @JavaMapping("java/lang/Math", "tan", "(D)D")
    @KgenExport("kgen_math_tan")
    @JvmStatic fun mathTan(x: Double): Double = tan(x)

    @JavaMapping("java/lang/Math", "atan", "(D)D")
    @KgenExport("kgen_math_atan")
    @JvmStatic fun mathAtan(x: Double): Double = atan(x)

    @JavaMapping("java/lang/Math", "atan2", "(DD)D")
    @KgenExport("kgen_math_atan2")
    @JvmStatic fun mathAtan2(y: Double, x: Double): Double = atan2(y, x)

    // -- Rounding --

    @JavaMapping("java/lang/Math", "ceil", "(D)D")
    @KgenExport("kgen_math_ceil")
    @JvmStatic fun mathCeil(x: Double): Double = ceil(x)

    @JavaMapping("java/lang/Math", "floor", "(D)D")
    @KgenExport("kgen_math_floor")
    @JvmStatic fun mathFloor(x: Double): Double = floor(x)

    @JavaMapping("java/lang/Math", "round", "(D)J")
    @KgenExport("kgen_math_round")
    @JvmStatic fun mathRound(x: Double): Long = lround(x)
}

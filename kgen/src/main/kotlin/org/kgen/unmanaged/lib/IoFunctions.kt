package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

@KgenNative
object IoFunctions {

    @KgenImport external fun puts(s: Long): Int
    @KgenImport("printf") external fun printfStr(fmt: Long, s: Long): Int
    @KgenImport("printf") external fun printfInt(fmt: Long, v: Int): Int
    @KgenImport("printf") external fun printfLong(fmt: Long, v: Long): Int
    @KgenImport("printf") external fun printfDouble(fmt: Long, v: Double): Int
    @KgenImport("printf") external fun printfChar(fmt: Long, c: Int): Int

    @JavaMapping("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
    @KgenExport("kgen_println_str")
    @JvmStatic fun printlnStr(s: Long) { puts(s) }

    @JavaMapping("java/io/PrintStream", "println", "(I)V")
    @KgenExport("kgen_println_int")
    @JvmStatic fun printlnInt(v: Int) { printfInt(Kgen.stringConst("%d\n"), v) }

    @JavaMapping("java/io/PrintStream", "println", "(J)V")
    @KgenExport("kgen_println_long")
    @JvmStatic fun printlnLong(v: Long) { printfLong(Kgen.stringConst("%ld\n"), v) }

    @JavaMapping("java/io/PrintStream", "println", "(D)V")
    @KgenExport("kgen_println_double")
    @JvmStatic fun printlnDouble(v: Double) { printfDouble(Kgen.stringConst("%f\n"), v) }

    @JavaMapping("java/io/PrintStream", "println", "(F)V")
    @KgenExport("kgen_println_float")
    @JvmStatic fun printlnFloat(v: Double) { printfDouble(Kgen.stringConst("%f\n"), v) }

    @JavaMapping("java/io/PrintStream", "println", "(Z)V")
    @KgenExport("kgen_println_boolean")
    @JvmStatic fun printlnBoolean(v: Int) {
        if (v != 0) { puts(Kgen.stringConst("true")) } else { puts(Kgen.stringConst("false")) }
    }

    @JavaMapping("java/io/PrintStream", "println", "(C)V")
    @KgenExport("kgen_println_char")
    @JvmStatic fun printlnChar(c: Int) { printfChar(Kgen.stringConst("%c\n"), c) }

    @JavaMapping("java/io/PrintStream", "println", "()V")
    @KgenExport("kgen_println_void")
    @JvmStatic fun printlnVoid() { puts(Kgen.stringConst("")) }

    @JavaMapping("java/io/PrintStream", "print", "(Ljava/lang/String;)V")
    @KgenExport("kgen_print_str")
    @JvmStatic fun printStr(s: Long) { printfStr(Kgen.stringConst("%s"), s) }

    @JavaMapping("java/io/PrintStream", "print", "(I)V")
    @KgenExport("kgen_print_int")
    @JvmStatic fun printInt(v: Int) { printfInt(Kgen.stringConst("%d"), v) }

    @JavaMapping("java/io/PrintStream", "print", "(J)V")
    @KgenExport("kgen_print_long")
    @JvmStatic fun printLong(v: Long) { printfLong(Kgen.stringConst("%ld"), v) }
}

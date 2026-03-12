package org.kgen.unmanaged.lib;

import org.kgen.unmanaged.*;

/**
 * Native stdlib for compiled Java code.
 *
 * Written as a normal {@code @KgenNative} class and compiled through
 * {@link org.kgen.runtime.compile.RuntimeCompiler} — NOT hand-built IR.
 * The lowering pipeline handles {@code @KgenImport}, {@code @KgenExport},
 * and {@code Kgen.stringConst()} intrinsics automatically.
 *
 * <pre>{@code
 * var stdlib = StdlibProvider.generate(Target.x86_64());
 * // merge with compiled module before linking
 * }</pre>
 */
@KgenNative
public class KgenStdlib {

    // ── C library imports ────────────────────────────────────────────

    @KgenImport
    static native int puts(long s);

    @KgenImport
    static native long strlen(long s);

    @KgenImport
    static native int strcmp(long a, long b);

    @KgenImport
    static native long malloc(long size);

    @KgenImport
    static native void free(long ptr);

    @KgenImport
    static native long memcpy(long dst, long src, long n);

    @KgenImport
    static native double sqrt(double x);

    @KgenImport
    static native double pow(double x, double y);

    @KgenImport
    static native double fabs(double x);

    // Printf/sprintf with fixed arity — each Java name maps to the same C symbol.
    // The lowering emits a call to the C name; vararg calling convention is handled
    // by the codegen (extra args in registers/stack per platform ABI).

    @KgenImport("printf")
    static native int printfI(long fmt, int v);

    @KgenImport("printf")
    static native int printfL(long fmt, long v);

    @KgenImport("printf")
    static native int printfD(long fmt, double v);

    @KgenImport("printf")
    static native int printfS(long fmt, long s);

    @KgenImport("sprintf")
    static native int sprintfI(long buf, long fmt, int v);

    @KgenImport("sprintf")
    static native int sprintfL(long buf, long fmt, long v);

    // ── I/O ──────────────────────────────────────────────────────────

    @KgenExport("kgen_println_str")
    public static void printlnStr(long s) {
        puts(s);
    }

    @KgenExport("kgen_println_int")
    public static void printlnInt(int v) {
        printfI(Kgen.stringConst("%d\n"), v);
    }

    @KgenExport("kgen_println_long")
    public static void printlnLong(long v) {
        printfL(Kgen.stringConst("%ld\n"), v);
    }

    @KgenExport("kgen_println_double")
    public static void printlnDouble(double v) {
        printfD(Kgen.stringConst("%f\n"), v);
    }

    @KgenExport("kgen_println_void")
    public static void printlnVoid() {
        puts(Kgen.stringConst("\n"));
    }

    @KgenExport("kgen_print_str")
    public static void printStr(long s) {
        printfS(Kgen.stringConst("%s"), s);
    }

    @KgenExport("kgen_print_int")
    public static void printInt(int v) {
        printfI(Kgen.stringConst("%d"), v);
    }

    @KgenExport("kgen_print_long")
    public static void printLong(long v) {
        printfL(Kgen.stringConst("%ld"), v);
    }

    // ── String operations ────────────────────────────────────────────

    @KgenExport("kgen_string_length")
    public static int stringLength(long s) {
        return (int) strlen(s);
    }

    @KgenExport("kgen_string_equals")
    public static int stringEquals(long a, long b) {
        return strcmp(a, b) == 0 ? 1 : 0;
    }

    @KgenExport("kgen_string_charAt")
    public static int stringCharAt(long s, int idx) {
        return Kgen.loadByte(Kgen.offset(s, idx));
    }

    // ── Math operations ──────────────────────────────────────────────

    @KgenExport("kgen_math_abs_int")
    public static int mathAbsInt(int v) {
        return v < 0 ? -v : v;
    }

    @KgenExport("kgen_math_abs_long")
    public static long mathAbsLong(long v) {
        return v < 0 ? -v : v;
    }

    @KgenExport("kgen_math_abs_double")
    public static double mathAbsDouble(double v) {
        return fabs(v);
    }

    @KgenExport("kgen_math_min_int")
    public static int mathMinInt(int a, int b) {
        return a < b ? a : b;
    }

    @KgenExport("kgen_math_max_int")
    public static int mathMaxInt(int a, int b) {
        return a > b ? a : b;
    }

    @KgenExport("kgen_math_min_long")
    public static long mathMinLong(long a, long b) {
        return a < b ? a : b;
    }

    @KgenExport("kgen_math_max_long")
    public static long mathMaxLong(long a, long b) {
        return a > b ? a : b;
    }

    @KgenExport("kgen_math_sqrt")
    public static double mathSqrt(double x) {
        return sqrt(x);
    }

    @KgenExport("kgen_math_pow")
    public static double mathPow(double x, double y) {
        return pow(x, y);
    }

    // ── Numeric conversion ───────────────────────────────────────────

    @KgenExport("kgen_int_to_string")
    public static long intToString(int v) {
        long buf = malloc(32);
        sprintfI(buf, Kgen.stringConst("%d"), v);
        return buf;
    }

    @KgenExport("kgen_long_to_string")
    public static long longToString(long v) {
        long buf = malloc(32);
        sprintfL(buf, Kgen.stringConst("%ld"), v);
        return buf;
    }

    // ── String concat helpers ────────────────────────────────────────

    @KgenExport("kgen_strconcat_begin")
    public static long strconcatBegin() {
        long buf = malloc(256);
        Kgen.storeByte(buf, (byte) 0);
        return buf;
    }

    @KgenExport("kgen_strconcat_str")
    public static long strconcatStr(long buf, long s) {
        long bufLen = strlen(buf);
        long srcLen = strlen(s);
        memcpy(Kgen.offset(buf, bufLen), s, srcLen);
        Kgen.storeByte(Kgen.offset(buf, bufLen + srcLen), (byte) 0);
        return buf;
    }

    @KgenExport("kgen_strconcat_int")
    public static long strconcatInt(long buf, int v) {
        long bufLen = strlen(buf);
        sprintfI(Kgen.offset(buf, bufLen), Kgen.stringConst("%d"), v);
        return buf;
    }

    @KgenExport("kgen_strconcat_long")
    public static long strconcatLong(long buf, long v) {
        long bufLen = strlen(buf);
        sprintfL(Kgen.offset(buf, bufLen), Kgen.stringConst("%ld"), v);
        return buf;
    }

    @KgenExport("kgen_strconcat_finish")
    public static long strconcatFinish(long buf) {
        return buf;
    }
}

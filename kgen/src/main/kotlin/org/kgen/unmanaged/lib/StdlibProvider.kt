package org.kgen.unmanaged.lib

import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.RuntimeCompiler

/**
 * Provides a minimal native stdlib for compiled Java code.
 *
 * Maps common Java standard library calls to native implementations
 * using libc functions (puts, printf, strlen, strcmp, memcpy, etc.).
 *
 * The stdlib is implemented as a `@KgenNative` Java class ([KgenStdlib])
 * and compiled through [RuntimeCompiler]. No hand-built IR.
 *
 * Supported APIs:
 * - `System.out.println(String)` -> kgen_println_str
 * - `System.out.println(int)` -> kgen_println_int
 * - `System.out.println(long)` -> kgen_println_long
 * - `System.out.println()` -> kgen_println_void
 * - `String.length()` -> kgen_string_length
 * - `String.equals(Object)` -> kgen_string_equals
 * - `String.charAt(int)` -> kgen_string_charAt
 * - `Math.abs(int)` / `Math.abs(long)` -> kgen_math_abs_int / kgen_math_abs_long
 * - `Math.min(int, int)` / `Math.max(int, int)` -> kgen_math_min_int / kgen_math_max_int
 * - `Math.sqrt(double)` -> kgen_math_sqrt
 * - String concat helpers (kgen_strconcat_*)
 *
 * ```java
 * var stdlib = StdlibProvider.generate(Target.x86_64());
 * // merge with compiled module before linking
 * ```
 */
object StdlibProvider {

    /** Set of method signatures that are handled by the stdlib. */
    val HANDLED_METHODS = setOf(
        "java/io/PrintStream.println:(Ljava/lang/String;)V",
        "java/io/PrintStream.println:(I)V",
        "java/io/PrintStream.println:(J)V",
        "java/io/PrintStream.println:(D)V",
        "java/io/PrintStream.println:()V",
        "java/io/PrintStream.print:(Ljava/lang/String;)V",
        "java/io/PrintStream.print:(I)V",
        "java/io/PrintStream.print:(J)V",
        "java/lang/String.length:()I",
        "java/lang/String.equals:(Ljava/lang/Object;)Z",
        "java/lang/String.charAt:(I)C",
        "java/lang/Math.abs:(I)I",
        "java/lang/Math.abs:(J)J",
        "java/lang/Math.abs:(D)D",
        "java/lang/Math.min:(II)I",
        "java/lang/Math.max:(II)I",
        "java/lang/Math.min:(JJ)J",
        "java/lang/Math.max:(JJ)J",
        "java/lang/Math.sqrt:(D)D",
        "java/lang/Math.pow:(DD)D",
        "java/lang/Integer.toString:(I)Ljava/lang/String;",
        "java/lang/Long.toString:(J)Ljava/lang/String;",
    )

    /**
     * Returns the native function name for a Java method, or null if not handled.
     */
    fun nativeName(className: String, methodName: String, descriptor: String): String? {
        return when ("$className.$methodName:$descriptor") {
            "java/io/PrintStream.println:(Ljava/lang/String;)V" -> "kgen_println_str"
            "java/io/PrintStream.println:(I)V" -> "kgen_println_int"
            "java/io/PrintStream.println:(J)V" -> "kgen_println_long"
            "java/io/PrintStream.println:(D)V" -> "kgen_println_double"
            "java/io/PrintStream.println:()V" -> "kgen_println_void"
            "java/io/PrintStream.print:(Ljava/lang/String;)V" -> "kgen_print_str"
            "java/io/PrintStream.print:(I)V" -> "kgen_print_int"
            "java/io/PrintStream.print:(J)V" -> "kgen_print_long"
            "java/lang/String.length:()I" -> "kgen_string_length"
            "java/lang/String.equals:(Ljava/lang/Object;)Z" -> "kgen_string_equals"
            "java/lang/String.charAt:(I)C" -> "kgen_string_charAt"
            "java/lang/Math.abs:(I)I" -> "kgen_math_abs_int"
            "java/lang/Math.abs:(J)J" -> "kgen_math_abs_long"
            "java/lang/Math.abs:(D)D" -> "kgen_math_abs_double"
            "java/lang/Math.min:(II)I" -> "kgen_math_min_int"
            "java/lang/Math.max:(II)I" -> "kgen_math_max_int"
            "java/lang/Math.min:(JJ)J" -> "kgen_math_min_long"
            "java/lang/Math.max:(JJ)J" -> "kgen_math_max_long"
            "java/lang/Math.sqrt:(D)D" -> "kgen_math_sqrt"
            "java/lang/Math.pow:(DD)D" -> "kgen_math_pow"
            "java/lang/Integer.toString:(I)Ljava/lang/String;" -> "kgen_int_to_string"
            "java/lang/Long.toString:(J)Ljava/lang/String;" -> "kgen_long_to_string"
            else -> null
        }
    }

    /**
     * Generate an IR module containing stdlib implementations.
     *
     * Loads the compiled [KgenStdlib] class file from the classpath and compiles
     * it through [RuntimeCompiler]. The result is a standard IR module with all
     * stdlib functions ready for codegen.
     */
    fun generate(target: Target): Module {
        val classBytes = KgenStdlib::class.java.classLoader
            .getResourceAsStream("org/kgen/unmanaged/lib/KgenStdlib.class")
            ?.readAllBytes()
            ?: error("KgenStdlib.class not found on classpath")

        return RuntimeCompiler(target).compile(classBytes)
    }
}

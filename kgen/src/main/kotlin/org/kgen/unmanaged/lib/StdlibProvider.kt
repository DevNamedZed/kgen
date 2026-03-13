package org.kgen.unmanaged.lib

import org.kgen.ir.*
import org.kgen.ir.instructions.*
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
 * - **I/O**: println/print for String, int, long, double, float, boolean, char
 * - **String**: length, equals, charAt, isEmpty, indexOf, substring, contains, startsWith
 * - **Math**: abs, min, max (int/long/double), sqrt, pow, ceil, floor, round, log, exp
 * - **Conversion**: Integer/Long/Double.toString, Integer.parseInt, Long.parseLong
 * - **String concat**: kgen_strconcat_* helpers
 *
 * ```java
 * var stdlib = StdlibProvider.generate(Target.x86_64());
 * // merge with compiled module before linking
 * ```
 */
object StdlibProvider {

    /** Set of method signatures that are handled by the stdlib. */
    val HANDLED_METHODS = setOf(
        // I/O
        "java/io/PrintStream.println:(Ljava/lang/String;)V",
        "java/io/PrintStream.println:(I)V",
        "java/io/PrintStream.println:(J)V",
        "java/io/PrintStream.println:(D)V",
        "java/io/PrintStream.println:(F)V",
        "java/io/PrintStream.println:(Z)V",
        "java/io/PrintStream.println:(C)V",
        "java/io/PrintStream.println:()V",
        "java/io/PrintStream.print:(Ljava/lang/String;)V",
        "java/io/PrintStream.print:(I)V",
        "java/io/PrintStream.print:(J)V",
        // String
        "java/lang/String.length:()I",
        "java/lang/String.equals:(Ljava/lang/Object;)Z",
        "java/lang/String.charAt:(I)C",
        "java/lang/String.isEmpty:()Z",
        "java/lang/String.indexOf:(I)I",
        "java/lang/String.substring:(II)Ljava/lang/String;",
        "java/lang/String.contains:(Ljava/lang/CharSequence;)Z",
        "java/lang/String.startsWith:(Ljava/lang/String;)Z",
        // Math
        "java/lang/Math.abs:(I)I",
        "java/lang/Math.abs:(J)J",
        "java/lang/Math.abs:(D)D",
        "java/lang/Math.min:(II)I",
        "java/lang/Math.max:(II)I",
        "java/lang/Math.min:(JJ)J",
        "java/lang/Math.max:(JJ)J",
        "java/lang/Math.min:(DD)D",
        "java/lang/Math.max:(DD)D",
        "java/lang/Math.sqrt:(D)D",
        "java/lang/Math.pow:(DD)D",
        "java/lang/Math.ceil:(D)D",
        "java/lang/Math.floor:(D)D",
        "java/lang/Math.round:(D)J",
        "java/lang/Math.log:(D)D",
        "java/lang/Math.exp:(D)D",
        // Numeric conversion
        "java/lang/Integer.toString:(I)Ljava/lang/String;",
        "java/lang/Long.toString:(J)Ljava/lang/String;",
        "java/lang/Double.toString:(D)Ljava/lang/String;",
        "java/lang/Integer.parseInt:(Ljava/lang/String;)I",
        "java/lang/Long.parseLong:(Ljava/lang/String;)J",
    )

    /**
     * Returns the native function name for a Java method, or null if not handled.
     */
    fun nativeName(className: String, methodName: String, descriptor: String): String? {
        return when ("$className.$methodName:$descriptor") {
            // I/O
            "java/io/PrintStream.println:(Ljava/lang/String;)V" -> "kgen_println_str"
            "java/io/PrintStream.println:(I)V" -> "kgen_println_int"
            "java/io/PrintStream.println:(J)V" -> "kgen_println_long"
            "java/io/PrintStream.println:(D)V" -> "kgen_println_double"
            "java/io/PrintStream.println:(F)V" -> "kgen_println_float"
            "java/io/PrintStream.println:(Z)V" -> "kgen_println_boolean"
            "java/io/PrintStream.println:(C)V" -> "kgen_println_char"
            "java/io/PrintStream.println:()V" -> "kgen_println_void"
            "java/io/PrintStream.print:(Ljava/lang/String;)V" -> "kgen_print_str"
            "java/io/PrintStream.print:(I)V" -> "kgen_print_int"
            "java/io/PrintStream.print:(J)V" -> "kgen_print_long"
            // String
            "java/lang/String.length:()I" -> "kgen_string_length"
            "java/lang/String.equals:(Ljava/lang/Object;)Z" -> "kgen_string_equals"
            "java/lang/String.charAt:(I)C" -> "kgen_string_charAt"
            "java/lang/String.isEmpty:()Z" -> "kgen_string_isEmpty"
            "java/lang/String.indexOf:(I)I" -> "kgen_string_indexOf"
            "java/lang/String.substring:(II)Ljava/lang/String;" -> "kgen_string_substring"
            "java/lang/String.contains:(Ljava/lang/CharSequence;)Z" -> "kgen_string_contains"
            "java/lang/String.startsWith:(Ljava/lang/String;)Z" -> "kgen_string_startsWith"
            // Math
            "java/lang/Math.abs:(I)I" -> "kgen_math_abs_int"
            "java/lang/Math.abs:(J)J" -> "kgen_math_abs_long"
            "java/lang/Math.abs:(D)D" -> "kgen_math_abs_double"
            "java/lang/Math.min:(II)I" -> "kgen_math_min_int"
            "java/lang/Math.max:(II)I" -> "kgen_math_max_int"
            "java/lang/Math.min:(JJ)J" -> "kgen_math_min_long"
            "java/lang/Math.max:(JJ)J" -> "kgen_math_max_long"
            "java/lang/Math.min:(DD)D" -> "kgen_math_min_double"
            "java/lang/Math.max:(DD)D" -> "kgen_math_max_double"
            "java/lang/Math.sqrt:(D)D" -> "kgen_math_sqrt"
            "java/lang/Math.pow:(DD)D" -> "kgen_math_pow"
            "java/lang/Math.ceil:(D)D" -> "kgen_math_ceil"
            "java/lang/Math.floor:(D)D" -> "kgen_math_floor"
            "java/lang/Math.round:(D)J" -> "kgen_math_round"
            "java/lang/Math.log:(D)D" -> "kgen_math_log"
            "java/lang/Math.exp:(D)D" -> "kgen_math_exp"
            // Numeric conversion
            "java/lang/Integer.toString:(I)Ljava/lang/String;" -> "kgen_int_to_string"
            "java/lang/Long.toString:(J)Ljava/lang/String;" -> "kgen_long_to_string"
            "java/lang/Double.toString:(D)Ljava/lang/String;" -> "kgen_double_to_string"
            "java/lang/Integer.parseInt:(Ljava/lang/String;)I" -> "kgen_int_parse"
            "java/lang/Long.parseLong:(Ljava/lang/String;)J" -> "kgen_long_parse"
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
        val classBytes = StdlibProvider::class.java.classLoader
            .getResourceAsStream("org/kgen/unmanaged/lib/KgenStdlib.class")
            ?.readAllBytes()
            ?: error("KgenStdlib.class not found on classpath")

        return RuntimeCompiler(target).compile(classBytes)
    }
}

package org.kgen.unmanaged.lib

/**
 * Maps a native function to the Java/Kotlin standard library method it replaces.
 *
 * When the compiler encounters a call to the specified Java method during
 * bytecode-to-IR lowering, it replaces it with a direct call to this native
 * function instead.
 *
 * The containing class must be `@KgenNative` to compile to native code.
 * The symbol name in the compiled output is derived from the Kotlin function name.
 *
 * ```kotlin
 * @KgenNative
 * object StringFunctions {
 *     @JavaMapping("java/lang/String", "length", "()I")
 *     @JvmStatic
 *     fun stringLength(s: Long): Int = strlen(s).toInt()
 * }
 * ```
 *
 * @param owner the Java class in internal form (e.g., `"java/lang/String"`)
 * @param name the Java method name (e.g., `"length"`)
 * @param descriptor the Java method descriptor (e.g., `"()I"`)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class JavaMapping(
    val owner: String,
    val name: String,
    val descriptor: String,
)

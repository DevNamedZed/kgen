package org.kgen.unmanaged.lib

import org.kgen.ir.Module
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.RuntimeCompiler
import java.lang.reflect.Method

/**
 * A resolved mapping from a Java method to its native implementation.
 *
 * @param owner Java class in internal form (e.g., "java/lang/String")
 * @param name Java method name (e.g., "length")
 * @param descriptor Java method descriptor (e.g., "()I")
 * @param nativeName the native symbol name (derived from the Kotlin function name)
 * @param implClass the class containing the implementation
 */
data class StdlibEntry(
    val owner: String,
    val name: String,
    val descriptor: String,
    val nativeName: String,
    val implClass: Class<*>,
)

/**
 * Registry for native stdlib functions. Discovers `@JavaMapping` annotated methods
 * and provides lookup for the bytecode-to-IR lowering pipeline.
 *
 * No manual registries. No hand-maintained method sets. Just annotate a function
 * with `@JavaMapping` and it's discoverable.
 *
 * ```kotlin
 * // Check if a Java method has a native implementation
 * val nativeName = NativeStdlib.resolve("java/lang/String", "length", "()I")
 *
 * // Compile all stdlib to an IR module
 * val stdlibModule = NativeStdlib.compile(Target.x86_64())
 * ```
 */
object NativeStdlib {

    private val entries: List<StdlibEntry> by lazy { discover() }
    private val lookupMap: Map<String, StdlibEntry> by lazy {
        entries.associateBy { "${it.owner}.${it.name}:${it.descriptor}" }
    }

    /**
     * Resolve a Java method to its native implementation name.
     * Returns null if the method is not handled by the stdlib.
     */
    @JvmStatic
    fun resolve(owner: String, name: String, descriptor: String): String? {
        return lookupMap["$owner.$name:$descriptor"]?.nativeName
    }

    /**
     * Check if a Java method has a native stdlib implementation.
     */
    @JvmStatic
    fun isHandled(owner: String, name: String, descriptor: String): Boolean {
        return "$owner.$name:$descriptor" in lookupMap
    }

    /**
     * Returns the set of all handled Java method signatures.
     */
    @JvmStatic
    fun handledMethods(): Set<String> = lookupMap.keys

    /**
     * Returns all discovered stdlib entries.
     */
    @JvmStatic
    fun allEntries(): List<StdlibEntry> = entries

    /**
     * Compile the stdlib to a single IR module.
     *
     * Compiles all stdlib function classes through [RuntimeCompiler] and
     * merges them into a single IR module.
     */
    @JvmStatic
    fun compile(target: Target): Module {
        val compiler = RuntimeCompiler(target)
        val classLoader = NativeStdlib::class.java.classLoader
        var merged: Module? = null
        for (cls in COMPILABLE_CLASSES) {
            val resourcePath = cls.name.replace('.', '/') + ".class"
            val classBytes = classLoader.getResourceAsStream(resourcePath)?.readAllBytes() ?: continue
            val module = compiler.compile(classBytes)
            merged = if (merged == null) { module } else { mergeModules(merged, module) }
        }
        return merged ?: error("No stdlib classes found on classpath")
    }

    private fun mergeModules(base: Module, other: Module): Module {
        val functions = base.functions.toMutableList()
        for (function in other.functions) {
            if (functions.none { it.name == function.name }) {
                functions.add(function)
            }
        }
        return base.copy(functions = functions)
    }

    /**
     * Scan classpath for all `@JavaMapping` annotated methods.
     */
    private fun discover(): List<StdlibEntry> {
        val result = mutableListOf<StdlibEntry>()

        for (cls in STDLIB_CLASSES) {
            scanClass(cls, result)
        }

        return result
    }

    private fun scanClass(cls: Class<*>, result: MutableList<StdlibEntry>) {
        for (method in cls.declaredMethods) {
            val mapping = method.getAnnotation(JavaMapping::class.java) ?: continue
            val nativeName = deriveNativeName(cls, method)
            result.add(StdlibEntry(
                owner = mapping.owner,
                name = mapping.name,
                descriptor = mapping.descriptor,
                nativeName = nativeName,
                implClass = cls,
            ))
        }
    }

    private fun deriveNativeName(cls: Class<*>, method: Method): String {
        val exportAnnotation = method.getAnnotation(org.kgen.unmanaged.KgenExport::class.java)
        if (exportAnnotation != null && exportAnnotation.value.isNotEmpty()) {
            return exportAnnotation.value
        }
        return "kgen_${method.name}"
    }

    /**
     * All classes to scan for @JavaMapping methods.
     */
    private val STDLIB_CLASSES: List<Class<*>> = listOf(
        IoFunctions::class.java,
        StringFunctions::class.java,
        MathFunctions::class.java,
        NumericConvertFunctions::class.java,
        ExceptionFunctions::class.java,
        Character::class.java,
        NativeSystem::class.java,
    )

    /**
     * Classes that can be compiled to native code through [RuntimeCompiler].
     * Excludes [NativeSystem] because its JVM fallback bodies call java.lang.System.
     */
    private val COMPILABLE_CLASSES: List<Class<*>> = listOf(
        IoFunctions::class.java,
        StringFunctions::class.java,
        MathFunctions::class.java,
        NumericConvertFunctions::class.java,
        ExceptionFunctions::class.java,
        Character::class.java,
    )
}

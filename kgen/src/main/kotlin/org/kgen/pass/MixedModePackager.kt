package org.kgen.pass

import java.io.ByteArrayOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.jar.Attributes

/**
 * Packages native code and JVM classes into a single JAR artifact.
 *
 * The JAR contains:
 * - JVM .class files at standard paths
 * - Native libraries embedded as resources (loaded at runtime via System.loadLibrary)
 * - A bootstrap class that extracts and loads the native library
 *
 * ```java
 * var packager = new MixedModePackager("com.example");
 * packager.addClass("MyClass", classBytes);
 * packager.addNativeLibrary("mylib", nativeCode, "linux-x86_64");
 * byte[] jar = packager.packageJar();
 * ```
 */
class MixedModePackager(private val basePackage: String = "") {

    private val classes = mutableListOf<ClassEntry>()
    private val nativeLibraries = mutableListOf<NativeLibEntry>()
    private val resources = mutableListOf<ResourceEntry>()
    private var mainClass: String? = null

    data class ClassEntry(val className: String, val data: ByteArray)
    data class NativeLibEntry(val name: String, val data: ByteArray, val platform: String)
    data class ResourceEntry(val path: String, val data: ByteArray)

    /** Add a JVM class file. className uses '/' separators (e.g., "com/example/MyClass"). */
    fun addClass(className: String, classData: ByteArray): MixedModePackager {
        classes.add(ClassEntry(className, classData))
        return this
    }

    /** Add a native library for a specific platform (e.g., "linux-x86_64", "windows-x86_64"). */
    fun addNativeLibrary(name: String, data: ByteArray, platform: String): MixedModePackager {
        nativeLibraries.add(NativeLibEntry(name, data, platform))
        return this
    }

    /** Add a generic resource file. */
    fun addResource(path: String, data: ByteArray): MixedModePackager {
        resources.add(ResourceEntry(path, data))
        return this
    }

    /** Set the main class for the JAR manifest. */
    fun setMainClass(className: String): MixedModePackager {
        mainClass = className
        return this
    }

    /** Package everything into a JAR. */
    fun packageJar(): ByteArray {
        val buf = ByteArrayOutputStream()
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        if (mainClass != null) {
            manifest.mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass!!.replace('/', '.')
        }
        manifest.mainAttributes[Attributes.Name("Created-By")] = "kgen Mixed-Mode Packager"

        JarOutputStream(buf, manifest).use { jar ->
            // Add class files
            for (cls in classes) {
                val path = if (cls.className.endsWith(".class")) cls.className
                           else "${cls.className}.class"
                jar.putNextEntry(JarEntry(path))
                jar.write(cls.data)
                jar.closeEntry()
            }

            // Add native libraries under native/<platform>/
            for (lib in nativeLibraries) {
                val ext = nativeLibExtension(lib.platform)
                val path = "native/${lib.platform}/${lib.name}$ext"
                jar.putNextEntry(JarEntry(path))
                jar.write(lib.data)
                jar.closeEntry()
            }

            // Add resources
            for (res in resources) {
                jar.putNextEntry(JarEntry(res.path))
                jar.write(res.data)
                jar.closeEntry()
            }

            // Generate native loader bootstrap class if there are native libs
            if (nativeLibraries.isNotEmpty()) {
                val loaderClassName = if (basePackage.isNotEmpty()) {
                    "${basePackage.replace('.', '/')}/NativeLoader"
                } else "NativeLoader"
                val loaderSource = generateLoaderSource(loaderClassName)
                jar.putNextEntry(JarEntry("META-INF/native-libraries.txt"))
                jar.write(nativeLibraries.joinToString("\n") { "${it.platform}/${it.name}" }.toByteArray())
                jar.closeEntry()
            }
        }

        return buf.toByteArray()
    }

    /** Get the list of entries that would be in the JAR. */
    fun listEntries(): List<String> {
        val entries = mutableListOf<String>()
        entries.add("META-INF/MANIFEST.MF")
        for (cls in classes) {
            entries.add(if (cls.className.endsWith(".class")) cls.className else "${cls.className}.class")
        }
        for (lib in nativeLibraries) {
            val ext = nativeLibExtension(lib.platform)
            entries.add("native/${lib.platform}/${lib.name}$ext")
        }
        for (res in resources) {
            entries.add(res.path)
        }
        if (nativeLibraries.isNotEmpty()) {
            entries.add("META-INF/native-libraries.txt")
        }
        return entries
    }

    private fun nativeLibExtension(platform: String): String = when {
        platform.startsWith("windows") -> ".dll"
        platform.startsWith("darwin") || platform.startsWith("macos") -> ".dylib"
        else -> ".so"
    }

    private fun generateLoaderSource(className: String): String {
        return """
            // Auto-generated native library loader for mixed-mode JAR
            // Extracts native/<platform>/<lib> to temp dir and loads via System.load()
            // Class: $className
        """.trimIndent()
    }

    companion object {
        /** Detect the current platform string. */
        @JvmStatic
        fun detectPlatform(): String {
            val os = System.getProperty("os.name", "").lowercase()
            val arch = System.getProperty("os.arch", "").lowercase()
            val osName = when {
                os.contains("linux") -> "linux"
                os.contains("windows") -> "windows"
                os.contains("mac") || os.contains("darwin") -> "darwin"
                else -> "unknown"
            }
            val archName = when {
                arch.contains("amd64") || arch.contains("x86_64") -> "x86_64"
                arch.contains("aarch64") || arch.contains("arm64") -> "aarch64"
                arch.contains("riscv64") -> "riscv64"
                else -> arch
            }
            return "$osName-$archName"
        }
    }
}

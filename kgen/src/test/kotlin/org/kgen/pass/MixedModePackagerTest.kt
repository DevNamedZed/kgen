package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.util.jar.JarInputStream

class MixedModePackagerTest {

    @Test
    fun packageEmptyJar() {
        val packager = MixedModePackager()
        val jar = packager.packageJar()
        assertTrue(jar.isNotEmpty())

        // Verify it's a valid JAR (ZIP format)
        val entries = readJarEntries(jar)
        assertTrue(entries.contains("META-INF/MANIFEST.MF"))
    }

    @Test
    fun packageWithClass() {
        val classData = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()) // fake class magic
        val packager = MixedModePackager()
        packager.addClass("com/example/MyClass", classData)
        val jar = packager.packageJar()

        val entries = readJarEntries(jar)
        assertTrue(entries.contains("com/example/MyClass.class"))
    }

    @Test
    fun packageWithNativeLibrary() {
        val nativeCode = byteArrayOf(0xC3.toByte()) // ret
        val packager = MixedModePackager()
        packager.addNativeLibrary("mylib", nativeCode, "linux-x86_64")
        val jar = packager.packageJar()

        val entries = readJarEntries(jar)
        assertTrue(entries.contains("native/linux-x86_64/mylib.so"))
        assertTrue(entries.contains("META-INF/native-libraries.txt"))
    }

    @Test
    fun nativeLibExtensionByPlatform() {
        val packager = MixedModePackager()
        packager.addNativeLibrary("lib", byteArrayOf(0), "linux-x86_64")
        packager.addNativeLibrary("lib", byteArrayOf(0), "windows-x86_64")
        packager.addNativeLibrary("lib", byteArrayOf(0), "darwin-aarch64")

        val entries = packager.listEntries()
        assertTrue(entries.any { it.endsWith(".so") })
        assertTrue(entries.any { it.endsWith(".dll") })
        assertTrue(entries.any { it.endsWith(".dylib") })
    }

    @Test
    fun packageWithMainClass() {
        val packager = MixedModePackager()
        packager.setMainClass("com/example/Main")
        packager.addClass("com/example/Main", byteArrayOf(0))
        val jar = packager.packageJar()

        val jis = JarInputStream(ByteArrayInputStream(jar))
        val manifest = jis.manifest
        assertEquals("com.example.Main", manifest.mainAttributes.getValue("Main-Class"))
        jis.close()
    }

    @Test
    fun packageWithResource() {
        val packager = MixedModePackager()
        packager.addResource("config/settings.json", """{"key": "value"}""".toByteArray())
        val jar = packager.packageJar()

        val entries = readJarEntries(jar)
        assertTrue(entries.contains("config/settings.json"))
    }

    @Test
    fun fluentApi() {
        val jar = MixedModePackager("com.example")
            .addClass("com/example/Main", byteArrayOf(0))
            .addNativeLibrary("compute", byteArrayOf(0xC3.toByte()), "linux-x86_64")
            .addResource("README.txt", "Hello".toByteArray())
            .setMainClass("com/example/Main")
            .packageJar()

        assertTrue(jar.isNotEmpty())
    }

    @Test
    fun listEntries() {
        val packager = MixedModePackager()
        packager.addClass("A", byteArrayOf(0))
        packager.addClass("B", byteArrayOf(0))
        packager.addNativeLibrary("lib", byteArrayOf(0), "linux-x86_64")

        val entries = packager.listEntries()
        assertTrue(entries.contains("A.class"))
        assertTrue(entries.contains("B.class"))
        assertTrue(entries.contains("native/linux-x86_64/lib.so"))
        assertTrue(entries.contains("META-INF/MANIFEST.MF"))
        assertTrue(entries.contains("META-INF/native-libraries.txt"))
    }

    @Test
    fun detectPlatform() {
        val platform = MixedModePackager.detectPlatform()
        assertNotNull(platform)
        assertTrue(platform.contains("-"), "Platform should be os-arch format")
    }

    @Test
    fun mixedModeFullPackage() {
        // Simulate full mixed-mode packaging: classes + native code
        val classData = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val nativeCode = ByteArray(64) // placeholder native code

        val packager = MixedModePackager("org.example")
        packager.addClass("org/example/ManagedClass", classData)
        packager.addClass("org/example/NativeBridge", classData)
        packager.addNativeLibrary("native_hotpath", nativeCode, "linux-x86_64")
        packager.addNativeLibrary("native_hotpath", nativeCode, "windows-x86_64")
        packager.addNativeLibrary("native_hotpath", nativeCode, "darwin-aarch64")
        packager.setMainClass("org/example/ManagedClass")

        val jar = packager.packageJar()
        val entries = readJarEntries(jar)

        // Verify all expected entries
        assertTrue(entries.contains("org/example/ManagedClass.class"))
        assertTrue(entries.contains("org/example/NativeBridge.class"))
        assertTrue(entries.contains("native/linux-x86_64/native_hotpath.so"))
        assertTrue(entries.contains("native/windows-x86_64/native_hotpath.dll"))
        assertTrue(entries.contains("native/darwin-aarch64/native_hotpath.dylib"))

        // Should be a reasonable size
        assertTrue(jar.size > 100)
    }

    @Test
    fun manifestContainsCreatedBy() {
        val packager = MixedModePackager()
        val jar = packager.packageJar()

        val jis = JarInputStream(ByteArrayInputStream(jar))
        val manifest = jis.manifest
        assertEquals("kgen Mixed-Mode Packager", manifest.mainAttributes.getValue("Created-By"))
        jis.close()
    }

    private fun readJarEntries(jarBytes: ByteArray): Set<String> {
        val entries = mutableSetOf<String>()
        val jis = JarInputStream(ByteArrayInputStream(jarBytes))
        var entry = jis.nextJarEntry
        while (entry != null) {
            entries.add(entry.name)
            jis.closeEntry()
            entry = jis.nextJarEntry
        }
        // Manifest is always present
        if (jis.manifest != null) entries.add("META-INF/MANIFEST.MF")
        jis.close()
        return entries
    }
}

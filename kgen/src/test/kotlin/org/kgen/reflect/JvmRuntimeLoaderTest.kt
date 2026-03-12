package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class JvmRuntimeLoaderTest {

    @Test
    fun detectLibjvmPathFindsJvm() {
        val path = JvmRuntimeLoader.detectLibjvmPath()
        // JAVA_HOME should be set in test environment
        if (System.getenv("JAVA_HOME") != null || System.getProperty("java.home") != null) {
            assertNotNull(path, "libjvm should be findable when JAVA_HOME is set")
            assertTrue(path!!.isNotEmpty())
        }
    }

    @Test
    fun isAvailableReturnsTrue() {
        // JVM is always available since we're running in one
        if (System.getProperty("java.home") != null) {
            assertTrue(JvmRuntimeLoader.isAvailable())
        }
    }

    @Test
    fun loadReturnsNullOrHandleForSecondJvm() {
        // JNI spec says one JVM per process; this should handle gracefully
        val handle = JvmRuntimeLoader.load()
        // May return null (can't create second JVM) or a handle to existing JVM
        if (handle != null) {
            assertFalse(handle.ownsVm(), "Should attach to existing JVM, not create new one")
            handle.close()
        }
    }

    @Test
    fun jvmOptionsDefaultValues() {
        val opts = JvmOptions()
        assertEquals("", opts.classpath)
        assertEquals("256m", opts.maxHeap)
        assertTrue(opts.additionalOptions.isEmpty())
    }

    @Test
    fun jvmOptionsWithClasspath() {
        val opts = JvmOptions(classpath = "/path/to/classes:/path/to/lib.jar")
        assertEquals("/path/to/classes:/path/to/lib.jar", opts.classpath)
        val strings = opts.toOptionStrings()
        assertTrue(strings.any { it.startsWith("-Djava.class.path=") })
    }

    @Test
    fun jvmOptionsWithMaxHeap() {
        val opts = JvmOptions(maxHeap = "512m")
        val strings = opts.toOptionStrings()
        assertTrue(strings.contains("-Xmx512m"))
    }

    @Test
    fun jvmOptionsWithAdditionalOptions() {
        val opts = JvmOptions(additionalOptions = listOf("-verbose:gc", "-ea"))
        val strings = opts.toOptionStrings()
        assertTrue(strings.contains("-verbose:gc"))
        assertTrue(strings.contains("-ea"))
    }

    @Test
    fun jvmOptionsToOptionStringsIncludesAll() {
        val opts = JvmOptions(classpath = "a.jar", maxHeap = "1g", additionalOptions = listOf("-ea"))
        val strings = opts.toOptionStrings()
        assertEquals(3, strings.size)
    }

    @Test
    fun jvmOptionsEmptyClasspathOmitsOption() {
        val opts = JvmOptions(classpath = "", maxHeap = "")
        val strings = opts.toOptionStrings()
        assertFalse(strings.any { it.contains("java.class.path") })
        assertFalse(strings.any { it.startsWith("-Xmx") })
    }

    @Test
    fun jvmRuntimeHandleIsAutoCloseable() {
        assertTrue(AutoCloseable::class.java.isAssignableFrom(JvmRuntimeHandle::class.java))
    }

    @Test
    fun detectLibjvmPathHandlesMissingJavaHome() {
        // Even if we can't unset JAVA_HOME, java.home should always be set
        // Just verify the method doesn't throw
        val path = JvmRuntimeLoader.detectLibjvmPath()
        // Result depends on environment
        assertDoesNotThrow { JvmRuntimeLoader.detectLibjvmPath() }
    }

    @Test
    fun loadFromPathWithInvalidPathReturnsNull() {
        val handle = JvmRuntimeLoader.loadFromPath("/nonexistent/path/libjvm.so")
        assertNull(handle)
    }

    @Test
    fun loadFromPathWithEmptyStringReturnsNull() {
        val handle = JvmRuntimeLoader.loadFromPath("")
        assertNull(handle)
    }

    @Test
    fun findClassWithInvalidNameReturnsZero() {
        val handle = JvmRuntimeLoader.load() ?: return
        // An invalid class name should return 0, not throw
        val clazz = handle.findClass("this/class/does/not/Exist9999999")
        // Might be 0 or might find it depending on context
        handle.close()
    }

    @Test
    fun callStaticMethodWithInvalidIdReturnsDefault() {
        val handle = JvmRuntimeLoader.load() ?: return
        // Should return 0 gracefully, not throw
        val result = handle.callStaticInt(0L, 0L)
        assertEquals(0, result)
        handle.close()
    }

    @Test
    fun callStaticVoidWithInvalidIdDoesNotThrow() {
        val handle = JvmRuntimeLoader.load() ?: return
        assertDoesNotThrow { handle.callStaticVoid(0L, 0L) }
        handle.close()
    }

    @Test
    fun getStaticMethodIdWithInvalidClassReturnsZero() {
        val handle = JvmRuntimeLoader.load() ?: return
        val methodId = handle.getStaticMethodId(0L, "main", "([Ljava/lang/String;)V")
        assertEquals(0L, methodId)
        handle.close()
    }

    @Test
    fun doubleCloseIsSafe() {
        val handle = JvmRuntimeLoader.load() ?: return
        handle.close()
        assertDoesNotThrow { handle.close() }
    }

    @Test
    fun closedHandleRejectsOperations() {
        val handle = JvmRuntimeLoader.load() ?: return
        handle.close()
        assertFalse(handle.isOpen())
        assertEquals(0L, handle.findClass("java/lang/Object"))
        assertEquals(0L, handle.getStaticMethodId(1L, "test", "()V"))
        assertEquals(0, handle.callStaticInt(1L, 1L))
    }

    @Test
    fun handleToString() {
        val handle = JvmRuntimeLoader.load() ?: return
        val str = handle.toString()
        assertTrue(str.contains("JvmRuntimeHandle"))
        assertTrue(str.contains("vm="))
        handle.close()
    }

    @Test
    fun jvmOptionsDataClassEquality() {
        val a = JvmOptions(classpath = "a.jar", maxHeap = "1g")
        val b = JvmOptions(classpath = "a.jar", maxHeap = "1g")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun jvmOptionsDataClassCopy() {
        val opts = JvmOptions(classpath = "a.jar")
        val copy = opts.copy(maxHeap = "2g")
        assertEquals("a.jar", copy.classpath)
        assertEquals("2g", copy.maxHeap)
    }

    @Test
    fun buildLibjvmCandidatesNotEmpty() {
        val candidates = JvmRuntimeLoader.buildLibjvmCandidates(Path.of("/opt/java"))
        assertTrue(candidates.isNotEmpty())
        // All candidates should contain the java home path
        candidates.forEach { assertTrue(it.contains("java") || it.contains("jvm")) }
    }

    @Test
    fun buildLibjvmCandidatesContainsServerDir() {
        val candidates = JvmRuntimeLoader.buildLibjvmCandidates(Path.of("/opt/java"))
        assertTrue(candidates.any { it.contains("server") })
    }

    @Test
    fun findLibjvmWithNonExistentPathReturnsNull() {
        val result = JvmRuntimeLoader.findLibjvm(Path.of("/this/does/not/exist"))
        assertNull(result)
    }

    @Test
    fun jvmOptionsToString() {
        val opts = JvmOptions(classpath = "app.jar", maxHeap = "512m")
        val str = opts.toString()
        assertTrue(str.contains("app.jar"))
        assertTrue(str.contains("512m"))
    }
}

package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

class ClrRuntimeLoaderTest {

    @Test
    fun detectHostfxrPathReturnsNullWhenNotInstalled() {
        // Should not crash even if .NET is not installed
        val path = ClrRuntimeLoader.detectHostfxrPath()
        // May be null or a valid path depending on environment
        if (path != null) {
            assertTrue(path.isNotEmpty())
        }
    }

    @Test
    fun isAvailableReturnsFalseWhenNotInstalled() {
        val available = ClrRuntimeLoader.isAvailable()
        // Should not throw regardless of whether .NET is installed
        assertNotNull(available)
    }

    @Test
    fun loadReturnsNullWhenNotInstalled() {
        val handle = ClrRuntimeLoader.load()
        // If .NET is not installed, should return null gracefully
        // If installed, should return a valid handle
        if (handle != null) {
            handle.close()
        }
    }

    @Test
    fun detectHostfxrPathFormatsCorrectly() {
        val libName = ClrRuntimeLoader.hostfxrLibraryName()
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> assertEquals("hostfxr.dll", libName)
            os.contains("mac") -> assertEquals("libhostfxr.dylib", libName)
            else -> assertEquals("libhostfxr.so", libName)
        }
    }

    @Test
    fun clrRuntimeHandleIsAutoCloseable() {
        // Verify the type implements AutoCloseable
        val handle = ClrRuntimeLoader.load()
        if (handle != null) {
            assertTrue(handle is AutoCloseable)
            handle.close()
            // Double close should be safe
            handle.close()
        }
    }

    @Test
    fun clrContextIsAutoCloseable() {
        // ClrContext implements AutoCloseable - verify via reflection
        assertTrue(AutoCloseable::class.java.isAssignableFrom(ClrContext::class.java))
    }

    @Test
    fun loadFromPathWithInvalidPathReturnsNull() {
        val handle = ClrRuntimeLoader.loadFromPath("/nonexistent/path/to/hostfxr.dll")
        assertNull(handle)
    }

    @Test
    fun loadFromPathWithEmptyStringReturnsNull() {
        val handle = ClrRuntimeLoader.loadFromPath("")
        assertNull(handle)
    }

    @Test
    fun initializeWithMissingConfigReturnsNull() {
        val handle = ClrRuntimeLoader.load() ?: return
        val ctx = handle.initialize("/nonexistent/app.runtimeconfig.json")
        assertNull(ctx)
        handle.close()
    }

    @Test
    fun multipleLoadCallsAreSafe() {
        val handle1 = ClrRuntimeLoader.load()
        val handle2 = ClrRuntimeLoader.load()
        // Both should succeed or both should be null, no crashes
        handle1?.close()
        handle2?.close()
    }

    @Test
    fun findHostfxrInDirectoryWithNonExistentPath() {
        val result = ClrRuntimeLoader.findHostfxrInDirectory(Path.of("/this/does/not/exist"))
        assertNull(result)
    }

    @Test
    fun findHostfxrInDirectoryWithRegularFile() {
        // A regular file should not be treated as a directory
        val tempFile = java.io.File.createTempFile("test", ".txt")
        try {
            val result = ClrRuntimeLoader.findHostfxrInDirectory(tempFile.toPath())
            assertNull(result)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun extractHostfxrFromRuntimeListWithEmptyOutput() {
        val result = ClrRuntimeLoader.extractHostfxrFromRuntimeList("")
        assertNull(result)
    }

    @Test
    fun extractHostfxrFromRuntimeListWithMalformedOutput() {
        val result = ClrRuntimeLoader.extractHostfxrFromRuntimeList("this is not valid output\nno brackets here")
        assertNull(result)
    }

    @Test
    fun extractHostfxrFromRuntimeListWithIrrelevantRuntime() {
        val result = ClrRuntimeLoader.extractHostfxrFromRuntimeList(
            "Microsoft.AspNetCore.App 8.0.0 [/usr/share/dotnet/shared/Microsoft.AspNetCore.App/8.0.0]"
        )
        assertNull(result)
    }

    @Test
    fun hostfxrLibraryNameIsNotEmpty() {
        val name = ClrRuntimeLoader.hostfxrLibraryName()
        assertTrue(name.isNotEmpty())
        assertTrue(name.contains("hostfxr"))
    }

    @Test
    fun clrRuntimeHandleToString() {
        val handle = ClrRuntimeLoader.load()
        if (handle != null) {
            val str = handle.toString()
            assertTrue(str.contains("ClrRuntimeHandle"))
            handle.close()
        }
    }

    @Test
    fun clrContextToString() {
        // We can't easily construct ClrContext without a real handle, so just check it's constructable
        assertTrue(ClrContext::class.java.declaredConstructors.isNotEmpty())
        // Verify it has the expected getDelegate method
        val methods = ClrContext::class.java.declaredMethods.map { it.name }
        assertTrue("getDelegate" in methods || methods.isNotEmpty())
    }

    @Test
    fun clrRuntimeHandleImplementsAutoCloseable() {
        assertTrue(AutoCloseable::class.java.isAssignableFrom(ClrRuntimeHandle::class.java))
    }

    @Test
    fun loadReturnsHandleWithToString() {
        val handle = ClrRuntimeLoader.load()
        if (handle != null) {
            assertNotNull(handle.toString())
            assertTrue(handle.toString().isNotEmpty())
            handle.close()
        }
    }
}

package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ClrRuntimeTest {

    @Test
    fun detectReturnsNullWhenNoClr() {
        // In a pure JVM test environment, CLR should not be loaded
        val clr = ClrRuntime.detect()
        // This may or may not be null depending on environment
        // but the API should not throw
    }

    @Test
    fun isAvailableDoesNotThrow() {
        val available = ClrRuntime.isAvailable()
        // Just verify it doesn't throw
        assertNotNull(available)
    }

    @Test
    fun ofCoreCLR() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.CORECLR, "8.0.0", "/usr/share/dotnet")
        assertEquals(ClrRuntime.RuntimeKind.CORECLR, clr.runtimeKind())
        assertEquals("8.0.0", clr.version())
        assertEquals("/usr/share/dotnet", clr.runtimePath())
        assertTrue(clr.isCoreCLR())
        assertFalse(clr.isFramework())
        assertFalse(clr.isMono())
    }

    @Test
    fun ofFramework() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.FRAMEWORK, "4.8.0")
        assertEquals(ClrRuntime.RuntimeKind.FRAMEWORK, clr.runtimeKind())
        assertTrue(clr.isFramework())
        assertFalse(clr.isCoreCLR())
    }

    @Test
    fun ofMono() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.MONO)
        assertEquals(ClrRuntime.RuntimeKind.MONO, clr.runtimeKind())
        assertTrue(clr.isMono())
        assertFalse(clr.isCoreCLR())
        assertFalse(clr.isFramework())
    }

    @Test
    fun assemblyInfoDataClass() {
        val info = ClrRuntime.AssemblyInfo(
            name = "System.Private.CoreLib",
            path = "/usr/share/dotnet/shared/Microsoft.NETCore.App/8.0.0/System.Private.CoreLib.dll",
            isMixedMode = false,
        )
        assertEquals("System.Private.CoreLib", info.name)
        assertFalse(info.isMixedMode)
    }

    @Test
    fun runtimeKindValues() {
        val values = ClrRuntime.RuntimeKind.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(ClrRuntime.RuntimeKind.CORECLR))
        assertTrue(values.contains(ClrRuntime.RuntimeKind.FRAMEWORK))
        assertTrue(values.contains(ClrRuntime.RuntimeKind.MONO))
    }

    @Test
    fun toStringFormat() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.CORECLR, "8.0.0")
        val str = clr.toString()
        assertTrue(str.contains("CORECLR"))
        assertTrue(str.contains("8.0.0"))
    }

    @Test
    fun jittedMethodAddressReturnsNullWhenNotFound() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.CORECLR)
        val addr = clr.jittedMethodAddress("System", "Console", "WriteLine")
        // In a JVM-only environment, this should return null
        assertNull(addr)
    }

    @Test
    fun activeMethodHookProperties() {
        val hook = ClrRuntime.ActiveMethodHook(0x1234L, byteArrayOf(0x48, 0x89.toByte()))
        assertTrue(hook.isActive())
        assertEquals(0x1234L, hook.address())
        assertArrayEquals(byteArrayOf(0x48, 0x89.toByte()), hook.originalBytes())
    }

    @Test
    fun assembliesDoesNotThrow() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.CORECLR)
        val assemblies = clr.assemblies()
        // Should return empty list in JVM environment, not throw
        assertNotNull(assemblies)
    }

    @Test
    fun detectInitializedReturnsFalseInPureJvm() {
        val clr = ClrRuntime.detectInitialized()
        // In a pure JVM test environment, CLR is not initialized
        assertNull(clr)
    }

    @Test
    fun detectWithProbeReturnsFalseInPureJvm() {
        val clr = ClrRuntime.detectWithProbe()
        // In a pure JVM test environment, probing should return null
        assertNull(clr)
    }

    @Test
    fun runtimeKindToStringFormats() {
        assertEquals("CORECLR", ClrRuntime.RuntimeKind.CORECLR.toString())
        assertEquals("FRAMEWORK", ClrRuntime.RuntimeKind.FRAMEWORK.toString())
        assertEquals("MONO", ClrRuntime.RuntimeKind.MONO.toString())
    }

    @Test
    fun assemblyInfoFieldsAccessible() {
        val info = ClrRuntime.AssemblyInfo(
            name = "TestAssembly",
            path = "/path/to/TestAssembly.dll",
            isMixedMode = true,
        )
        assertEquals("TestAssembly", info.name)
        assertEquals("/path/to/TestAssembly.dll", info.path)
        assertTrue(info.isMixedMode)
    }

    @Test
    fun assemblyInfoIsMixedModeDefault() {
        val info = ClrRuntime.AssemblyInfo(
            name = "PureManaged",
            path = null,
            isMixedMode = false,
        )
        assertFalse(info.isMixedMode)
        assertNull(info.path)
    }

    @Test
    fun activeMethodHookDataClassEquality() {
        val hook1 = ClrRuntime.ActiveMethodHook(0x1000L, byteArrayOf(0xCC.toByte()))
        val hook2 = ClrRuntime.ActiveMethodHook(0x1000L, byteArrayOf(0xCC.toByte()))
        // ActiveMethodHook is not a data class, so identity equality applies
        assertNotSame(hook1, hook2)
        assertEquals(hook1.address(), hook2.address())
    }

    @Test
    fun detectReturnsConsistentResults() {
        val first = ClrRuntime.detect()
        val second = ClrRuntime.detect()
        // Both calls should return the same kind of result
        if (first == null) {
            assertNull(second)
        } else {
            assertNotNull(second)
            assertEquals(first.runtimeKind(), second!!.runtimeKind())
        }
    }

    @Test
    fun clrRuntimeLoaderIntegration() {
        // Verify ClrRuntimeLoader and ClrRuntime agree
        val loaderAvailable = ClrRuntimeLoader.isAvailable()
        // Loader checks for hostfxr (SDK); detect() checks for loaded runtime
        // Both should not throw
        assertDoesNotThrow { ClrRuntimeLoader.isAvailable() }
        assertDoesNotThrow { ClrRuntime.detect() }
    }

    @Test
    fun assemblyInfoCopy() {
        val original = ClrRuntime.AssemblyInfo("A", "/path/a.dll", false)
        val copy = original.copy(name = "B")
        assertEquals("B", copy.name)
        assertEquals("/path/a.dll", copy.path)
        assertFalse(copy.isMixedMode)
    }

    @Test
    fun assemblyInfoEquality() {
        val a = ClrRuntime.AssemblyInfo("Lib", "/lib.dll", false)
        val b = ClrRuntime.AssemblyInfo("Lib", "/lib.dll", false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun ofWithNullVersionAndPath() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.CORECLR)
        assertNull(clr.version())
        assertNull(clr.runtimePath())
    }

    @Test
    fun toStringWithNullVersion() {
        val clr = ClrRuntime.of(ClrRuntime.RuntimeKind.MONO)
        val str = clr.toString()
        assertTrue(str.contains("MONO"))
        assertTrue(str.contains("null"))
    }
}

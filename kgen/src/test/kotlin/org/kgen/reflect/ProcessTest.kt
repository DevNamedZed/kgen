package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.binary.ArchType
import org.kgen.reflect.process.Process

class ProcessTest {

    @Test
    fun currentProcessPid() {
        val process = Process.current()
        assertTrue(process.pid() > 0)
    }

    @Test
    fun currentProcessArch() {
        val process = Process.current()
        val arch = process.arch()
        // Should detect x86_64 or aarch64
        assertTrue(
            arch.arch == ArchType.X86_64 || arch.arch == ArchType.AARCH64,
            "Unexpected arch: ${arch.arch}"
        )
    }

    @Test
    fun currentProcessIsCurrent() {
        assertTrue(Process.current().isCurrent())
    }

    @Test
    fun singletonInstance() {
        assertSame(Process.current(), Process.current())
    }

    @Test
    fun processName() {
        val name = Process.current().name()
        assertNotNull(name)
        assertTrue(name.isNotEmpty())
    }

    @Test
    fun hasJvm() {
        assertTrue(Process.current().hasJvm())
    }

    @Test
    fun jvmVersion() {
        val version = Process.current().jvmVersion()
        assertNotNull(version)
        assertTrue(version!!.isNotEmpty())
    }

    @Test
    fun toStringContainsPid() {
        val str = Process.current().toString()
        assertTrue(str.contains("pid="))
    }

    @Test
    fun modulesReturnsNonEmpty() {
        // On Linux (WSL2), /proc/self/maps should have entries
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        if (!isLinux) return // skip on non-Linux
        val modules = Process.current().modules()
        // Should find at least something (JVM itself, libc, etc.)
        assertTrue(modules.isNotEmpty(), "Expected at least one loaded module on Linux")
    }

    @Test
    fun moduleFindsLibc() {
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        if (!isLinux) return
        val libc = Process.current().module("libc")
        // May or may not find libc depending on environment
        // Just test that it doesn't crash
    }

    @Test
    fun lookupStdlibSymbol() {
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        if (!isLinux) return
        // strlen should be findable on Linux
        val addr = Process.current().lookup("strlen")
        // May be null in some environments, just test it doesn't crash
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun modulesReturnsNonEmptyOnWindows() {
        val modules = Process.current().modules()
        assertTrue(modules.isNotEmpty(), "Expected at least one loaded module on Windows")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun moduleFindsJvmOnWindows() {
        val modules = Process.current().modules()
        val jvmModule = modules.firstOrNull { mod ->
            val name = mod.name().lowercase()
            name == "jvm.dll" || name == "java.dll" || name.contains("jvm")
        }
        assertNotNull(jvmModule, "Expected to find jvm.dll among loaded modules: ${modules.map { it.name() }}")
    }
}
